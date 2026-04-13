"""
mitmproxy addon: network access rules and secret substitution.

Loaded via: mitmweb -s /scripts/mitmproxy_addon.py

On startup, reads settings from up to three layers (lowest to highest
precedence): user (~/.config/sandcat/settings.json), project
(.sandcat/settings.json), and local (.sandcat/settings.local.json).
Env vars and secrets are merged (higher precedence wins on conflict).
Network rules are concatenated (highest precedence first).

Network rules are evaluated top-to-bottom, first match wins, default deny.
Secret placeholders are replaced with real values only for allowed hosts.
"""

import json
import logging
import os
import re
import subprocess
import sys
from fnmatch import fnmatch

from mitmproxy import ctx, http, dns

_VALID_ENV_NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")

# Settings layers, lowest to highest precedence.
SETTINGS_PATHS = [
    "/config/settings.json",                # user:    ~/.config/sandcat/settings.json
    "/config/project/settings.json",        # project: .sandcat/settings.json
    "/config/project/settings.local.json",  # local:   .sandcat/settings.local.json
]
SANDCAT_ENV_PATH = "/home/mitmproxy/.mitmproxy/sandcat.env"

logger = logging.getLogger(__name__)

class SandcatAddon:
    def __init__(self):
        self.secrets: dict[str, dict] = {}  # name -> {value, hosts, placeholder}
        self.network_rules: list[dict] = []
        self.env: dict[str, str] = {}  # non-secret env vars

    def load(self, loader):
        layers = []
        for path in SETTINGS_PATHS:
            if os.path.isfile(path):
                with open(path) as f:
                    layers.append(json.load(f))

        if not layers:
            logger.info("No settings files found — addon disabled")
            return

        merged = self._merge_settings(layers)

        self._configure_op_token(merged.get("op_service_account_token"))
        self.env = merged["env"]
        self._load_secrets(merged["secrets"])
        self._load_network_rules(merged["network"])
        self._write_placeholders_env()

        ctx.log.info(
            f"Loaded {len(self.env)} env var(s) and {len(self.secrets)} secret(s), wrote {SANDCAT_ENV_PATH}"
        )

    @staticmethod
    def _configure_op_token(token: str | None):
        """Set OP_SERVICE_ACCOUNT_TOKEN from settings if not already in the environment."""
        if token and "OP_SERVICE_ACCOUNT_TOKEN" not in os.environ:
            os.environ["OP_SERVICE_ACCOUNT_TOKEN"] = token

    @staticmethod
    def _merge_settings(layers: list[dict]) -> dict:
        """Merge settings from multiple layers (lowest to highest precedence).

        - env: dict merge, higher precedence overwrites.
        - secrets: dict merge, higher precedence overwrites.
        - network: concatenated, highest precedence first.
        - op_service_account_token: highest precedence non-empty value wins.
        """
        env: dict[str, str] = {}
        secrets: dict[str, dict] = {}
        network: list[dict] = []
        op_token: str | None = None

        for layer in layers:
            env.update(layer.get("env", {}))
            secrets.update(layer.get("secrets", {}))
            layer_token = layer.get("op_service_account_token")
            if layer_token:
                op_token = layer_token

        # Network rules: highest-precedence layer's rules come first.
        for layer in reversed(layers):
            network.extend(layer.get("network", []))

        return {"env": env, "secrets": secrets, "network": network,
                "op_service_account_token": op_token}

    def _load_secrets(self, raw_secrets: dict):
        for name, entry in raw_secrets.items():
            placeholder = f"SANDCAT_PLACEHOLDER_{name}"
            try:
                value = self._resolve_secret_value(name, entry)
            except (RuntimeError, ValueError) as e:
                ctx.log.warn(str(e))
                print(f"WARNING: {e}", file=sys.stderr)
                value = ""
            self.secrets[name] = {
                "value": value,
                "hosts": entry.get("hosts", []),
                "placeholder": placeholder,
            }

    @staticmethod
    def _resolve_secret_value(name: str, entry: dict) -> str:
        """Resolve a secret value from either a plain 'value' or a 1Password 'op' reference."""
        has_value = "value" in entry
        has_op = "op" in entry

        if has_value and has_op:
            raise ValueError(
                f"Secret {name!r}: specify either 'value' or 'op', not both"
            )
        if not has_value and not has_op:
            raise ValueError(
                f"Secret {name!r}: must specify either 'value' or 'op'"
            )

        if has_value:
            return entry["value"]

        op_ref = entry["op"]
        if not op_ref.startswith("op://"):
            raise ValueError(
                f"Secret {name!r}: 'op' value must start with 'op://', got {op_ref!r}"
            )

        try:
            result = subprocess.run(
                ["op", "read", op_ref],
                capture_output=True, text=True, timeout=30,
            )
        except FileNotFoundError:
            raise RuntimeError(
                f"Secret {name!r}: 'op' CLI not found. "
                "Install 1Password CLI to use op:// references."
            ) from None

        if result.returncode != 0:
            stderr = result.stderr.strip()
            raise RuntimeError(
                f"Secret {name!r}: 'op read' failed: {stderr}"
            )

        return result.stdout.strip()

    def _load_network_rules(self, raw_rules: list):
        self.network_rules = raw_rules
        ctx.log.info(f"Loaded {len(self.network_rules)} network rule(s)")

    @staticmethod
    def _shell_escape(value: str) -> str:
        """Escape a string for safe inclusion inside double quotes in shell."""
        return (value
                .replace("\\", "\\\\")
                .replace('"', '\\"')
                .replace("$", "\\$")
                .replace("`", "\\`")
                .replace("\n", "\\n"))

    @staticmethod
    def _validate_env_name(name: str):
        """Raise ValueError if name is not a valid shell variable name."""
        if not _VALID_ENV_NAME.match(name):
            raise ValueError(f"Invalid env var name: {name!r}")

    def _write_placeholders_env(self):
        lines = []
        # Non-secret env vars (e.g. git identity) — passed through as-is.
        for name, value in self.env.items():
            self._validate_env_name(name)
            lines.append(f'export {name}="{self._shell_escape(value)}"')
        for name, entry in self.secrets.items():
            self._validate_env_name(name)
            lines.append(f'export {name}="{self._shell_escape(entry["placeholder"])}"')
        with open(SANDCAT_ENV_PATH, "w") as f:
            f.write("\n".join(lines) + "\n")

    def _is_request_allowed(self, method: str | None, host: str) -> bool:
        host = host.lower().rstrip(".")
        for rule in self.network_rules:
            if not fnmatch(host, rule["host"].lower()):
                continue
            rule_method = rule.get("method")
            if rule_method is not None and method is not None and rule_method.upper() != method.upper():
                continue
            return rule["action"] == "allow"
        return False # default deny

    def _substitute_secrets(self, flow: http.HTTPFlow):
        host = flow.request.pretty_host.lower()

        for name, entry in self.secrets.items():
            placeholder = entry["placeholder"]
            value = entry["value"]
            allowed_hosts = entry["hosts"]

            present = (
                placeholder in flow.request.url
                or placeholder in str(flow.request.headers)
                or (
                    flow.request.content
                    and placeholder.encode() in flow.request.content
                )
            )

            if not present:
                continue

            # Leak detection: block if secret going to disallowed host
            if not any(fnmatch(host, pattern.lower()) for pattern in allowed_hosts):
                flow.response = http.Response.make(
                    403,
                    f"Blocked: secret {name!r} not allowed for host {host!r}\n".encode(),
                    {"Content-Type": "text/plain"},
                )
                ctx.log.warn(
                    f"Blocked secret {name!r} leak to disallowed host {host!r}"
                )
                return

            if placeholder in flow.request.url:
                flow.request.url = flow.request.url.replace(placeholder, value)
            for k, v in flow.request.headers.items():
                if placeholder in v:
                    flow.request.headers[k] = v.replace(placeholder, value)
            if flow.request.content and placeholder.encode() in flow.request.content:
                flow.request.content = flow.request.content.replace(
                    placeholder.encode(), value.encode()
                )

    def request(self, flow: http.HTTPFlow):
        method = flow.request.method
        host = flow.request.pretty_host

        if not self._is_request_allowed(method, host):
            flow.response = http.Response.make(
                403,
                f"Blocked by network policy: {method} {host}\n".encode(),
                {"Content-Type": "text/plain"},
            )
            ctx.log.warn(f"Network deny: {method} {host}")
            return

        self._substitute_secrets(flow)

    def dns_request(self, flow: dns.DNSFlow):
        question = flow.request.question
        if question is None:
            flow.response = flow.request.fail(dns.response_codes.REFUSED)
            return

        host = question.name
        if not self._is_request_allowed(None, host):
            flow.response = flow.request.fail(dns.response_codes.REFUSED)
            ctx.log.warn(f"DNS deny: {host}")


addons = [SandcatAddon()]
