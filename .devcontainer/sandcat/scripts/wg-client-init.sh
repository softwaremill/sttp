#!/bin/bash
#
# Entrypoint for the wg-client container. Sets up a WireGuard tunnel to the
# mitmproxy container and configures iptables to enforce it. Other containers
# share this network namespace via network_mode: "service:wg-client" and
# inherit the tunnel without needing NET_ADMIN themselves.
#
# Expects:
#   - /mitmproxy-config volume mounted (shared with the mitmproxy container)
#   - NET_ADMIN capability (for creating the WireGuard interface and iptables)
#   - net.ipv4.conf.all.src_valid_mark=1 sysctl (set via docker-compose)
#
set -e

WG_JSON="/mitmproxy-config/wireguard.conf"

# ── Wait for mitmproxy to be ready ──────────────────────────────────────────
# The mitmproxy container generates wireguard.conf (key pairs) on startup.
# The docker-compose healthcheck gates us, but wait just in case.
elapsed=0
while [ ! -f "$WG_JSON" ]; do
    if [ "$elapsed" -ge 60 ]; then
        echo "Timed out waiting for mitmproxy wireguard.conf" >&2
        exit 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
done

# ── Derive WireGuard keys ───────────────────────────────────────────────────
# mitmproxy stores its WireGuard key pairs as JSON:
#   {"server_key": "<server-private>", "client_key": "<client-private>"}
# We need the client's private key and the server's public key (derived from
# the server's private key).
client_private_key=$(jq -r .client_key "$WG_JSON")
server_private_key=$(jq -r .server_key "$WG_JSON")
server_public_key=$(echo "$server_private_key" | wg pubkey)

# Resolve the mitmproxy endpoint IP before setting up the tunnel, since DNS
# won't be available through the normal path after routing is configured.
mitmproxy_ip=$(getent hosts mitmproxy | awk '{print $1}')

# ── Create WireGuard interface ──────────────────────────────────────────────
# We set up WireGuard manually instead of using wg-quick because wg-quick
# calls `sysctl -w net.ipv4.conf.all.src_valid_mark=1` which fails in Docker
# (/proc/sys is read-only). The equivalent sysctl is pre-set via the
# docker-compose `sysctls` option.
ip link add wg0 type wireguard

wg set wg0 \
    private-key <(echo "$client_private_key") \
    fwmark 51820 \
    peer "$server_public_key" \
        endpoint mitmproxy:51820 \
        allowed-ips 0.0.0.0/0,::/0

ip address add 10.0.0.1/32 dev wg0
ip link set mtu 1420 up dev wg0

# ── Policy routing ──────────────────────────────────────────────────────────
# All traffic is routed through wg0 via a custom routing table (51820).
# WireGuard marks its own encapsulated UDP packets with fwmark 51820, which
# exempts them from the custom table — so they use the normal default route to
# reach the mitmproxy endpoint. The suppress_prefixlength rule ensures that
# local/link-local routes in the main table still work (e.g. Docker DNS,
# container-to-container traffic).
ip -4 route add 0.0.0.0/0 dev wg0 table 51820
ip -4 rule add not fwmark 51820 table 51820
ip -4 rule add table main suppress_prefixlength 0
ip -6 route add ::/0 dev wg0 table 51820
ip -6 rule add not fwmark 51820 table 51820
ip -6 rule add table main suppress_prefixlength 0

# ── Firewall kill switch ────────────────────────────────────────────────────
# iptables rules that enforce the tunnel. Without these, traffic could leak
# via eth0 if the WireGuard interface goes down, or a process could reach
# arbitrary IPs on the Docker network subnet directly (bypassing mitmproxy).
#
# The rules allow:
#   - All traffic on the WireGuard interface (wg0) and loopback
#   - WireGuard's own UDP encapsulation to the mitmproxy endpoint (fwmark 51820)
#   - Established/related return traffic on eth0 (for the WG handshake)
# Everything else outbound on eth0 is dropped.
#
# These rules cannot be modified by containers sharing this network namespace
# via network_mode, because those containers don't have NET_ADMIN.
iptables -I OUTPUT -o wg0 -j ACCEPT
iptables -I OUTPUT -o lo -j ACCEPT
iptables -I OUTPUT -d "$mitmproxy_ip" -p udp --dport 51820 -m mark --mark 51820 -j ACCEPT
iptables -I OUTPUT -o eth0 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -o eth0 -j DROP

ip6tables -I OUTPUT -o wg0 -j ACCEPT
ip6tables -I OUTPUT -o lo -j ACCEPT
ip6tables -I OUTPUT -o eth0 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
ip6tables -A OUTPUT -o eth0 -j DROP

# ── DNS ─────────────────────────────────────────────────────────────────────
# Point DNS at mitmproxy's built-in resolver (10.0.0.53) so DNS queries also
# go through the tunnel.
resolvconf -a wg0 -m 0 -x <<EOF
nameserver 10.0.0.53
EOF

# Signal readiness to containers waiting on the healthcheck.
touch /tmp/wg-ready

# Hand off to the container's main command (e.g. "sleep infinity").
exec "$@"
