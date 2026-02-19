"""
mitmproxy addon: network access rules and secret substitution.

Loaded via: mitmweb -s /scripts/mitmproxy_addon.py

On startup, reads /config/settings.json. Network rules (evaluated
top-to-bottom, first match wins, default deny) gate every request.
Secret placeholders are replaced with real values only for allowed hosts.
"""

import json
import logging
import os
from fnmatch import fnmatch

from mitmproxy import ctx, http

SETTINGS_PATH = "/config/settings.json"
SANDCAT_ENV_PATH = "/home/mitmproxy/.mitmproxy/sandcat.env"

logger = logging.getLogger(__name__)

class SandcatAddon:
    def __init__(self):
        self.secrets: dict[str, dict] = {}  # name -> {value, hosts, placeholder}
        self.network_rules: list[dict] = []
        self.env: dict[str, str] = {}  # non-secret env vars

    def load(self, loader):
        if not os.path.isfile(SETTINGS_PATH):
            logger.info("No settings.json found — addon disabled")
            return

        with open(SETTINGS_PATH) as f:
            raw = json.load(f)

        self.env = raw.get("env", {})
        self._load_secrets(raw.get("secrets", {}))
        self._load_network_rules(raw.get("network", []))

    def _load_secrets(self, raw_secrets: dict):
        for name, entry in raw_secrets.items():
            placeholder = f"SANDCAT_PLACEHOLDER_{name}"
            self.secrets[name] = {
                "value": entry["value"],
                "hosts": entry.get("hosts", []),
                "placeholder": placeholder,
            }

        self._write_placeholders_env()

        ctx.log.info(
            f"Loaded {len(self.env)} env var(s) and {len(self.secrets)} secret(s), wrote {SANDCAT_ENV_PATH}"
        )

    def _load_network_rules(self, raw_rules: list):
        self.network_rules = raw_rules
        ctx.log.info(f"Loaded {len(self.network_rules)} network rule(s)")

    @staticmethod
    def _shell_escape(value: str) -> str:
        """Escape a string for safe inclusion inside double quotes in shell."""
        return value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("`", "\\`")

    def _write_placeholders_env(self):
        lines = []
        # Non-secret env vars (e.g. git identity) — passed through as-is.
        for name, value in self.env.items():
            lines.append(f'export {name}="{self._shell_escape(value)}"')
        for name, entry in self.secrets.items():
            lines.append(f'export {name}="{self._shell_escape(entry["placeholder"])}"')
        with open(SANDCAT_ENV_PATH, "w") as f:
            f.write("\n".join(lines) + "\n")

    def _is_request_allowed(self, method: str, host: str) -> bool:
        for rule in self.network_rules:
            if not fnmatch(host, rule["host"]):
                continue
            rule_method = rule.get("method")
            if rule_method is not None and rule_method.upper() != method.upper():
                continue
            return rule["action"] == "allow"
        return False # default deny

    def _substitute_secrets(self, flow: http.HTTPFlow):
        host = flow.request.pretty_host

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
            if not any(fnmatch(host, pattern) for pattern in allowed_hosts):
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


addons = [SandcatAddon()]
