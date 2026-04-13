#!/bin/bash
#
# Entrypoint for containers that share the wg-client's network namespace.
# Installs the mitmproxy CA cert, disables commit signing, loads env vars
# and secret placeholders from sandcat.env, runs vscode-user setup (git
# identity, Java trust store, Claude Code update), then drops to vscode
# and exec's the container's main command.
#
set -e

CA_CERT="/mitmproxy-config/mitmproxy-ca-cert.pem"

# The CA cert is guaranteed to exist: app depends_on wg-client (healthy),
# which depends_on mitmproxy (healthy), whose healthcheck requires the
# WireGuard config — generated after the CA.
if [ ! -f "$CA_CERT" ]; then
    echo "mitmproxy CA cert not found at $CA_CERT" >&2
    exit 1
fi

cp "$CA_CERT" /usr/local/share/ca-certificates/mitmproxy.crt
update-ca-certificates

# Node.js ignores the system trust store and bundles its own CA certs.
# Point it at the mitmproxy CA so TLS verification works for Node-based
# tools (e.g. Anthropic SDK).
export NODE_EXTRA_CA_CERTS="$CA_CERT"
echo "export NODE_EXTRA_CA_CERTS=\"$CA_CERT\"" > /etc/profile.d/sandcat-node-ca.sh

# GPG keys are not forwarded into the container (credential isolation),
# so commit signing would always fail.  Git env vars have the highest
# precedence, overriding system/global/local/worktree config files.
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0="commit.gpgsign"
export GIT_CONFIG_VALUE_0="false"
cat > /etc/profile.d/sandcat-git.sh << 'GITEOF'
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0="commit.gpgsign"
export GIT_CONFIG_VALUE_0="false"
GITEOF

# Source env vars and secret placeholders (if available)
SANDCAT_ENV="/mitmproxy-config/sandcat.env"
if [ -f "$SANDCAT_ENV" ]; then
    . "$SANDCAT_ENV"
    # Make vars available to new shells (e.g. VS Code terminals in dev
    # containers) that won't inherit the entrypoint's environment.
    cp "$SANDCAT_ENV" /etc/profile.d/sandcat-env.sh
    count=$(grep -c '^export ' "$SANDCAT_ENV" 2>/dev/null || echo 0)
    echo "Loaded $count env var(s) from $SANDCAT_ENV"
    grep '^export ' "$SANDCAT_ENV" | sed 's/=.*//' | sed 's/^export /  /'
else
    echo "No $SANDCAT_ENV found — env vars and secret substitution disabled"
fi

# Run vscode-user tasks: git identity, Java trust store, Claude Code update.
su - vscode -c /usr/local/bin/app-user-init.sh

# Source all sandcat profile.d scripts from /etc/bash.bashrc so env vars
# are available in non-login shells (e.g. VS Code integrated terminals).
# Guard with a marker to avoid duplicating on container restart.
BASHRC_MARKER="# sandcat-profile-source"
if ! grep -q "$BASHRC_MARKER" /etc/bash.bashrc 2>/dev/null; then
    cat >> /etc/bash.bashrc << 'BASHRC_EOF'

# sandcat-profile-source
for _f in /etc/profile.d/sandcat-*.sh; do
    [ -r "$_f" ] && . "$_f"
done
unset _f
BASHRC_EOF
fi

exec gosu vscode "$@"
