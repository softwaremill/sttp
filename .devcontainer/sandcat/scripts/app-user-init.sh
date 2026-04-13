#!/bin/bash
#
# vscode-user tasks run via su from app-init.sh.
# /etc/profile.d/ is sourced by the login shell, providing
# GIT_USER_NAME, GIT_USER_EMAIL, and NODE_EXTRA_CA_CERTS.
#
set -e

if [ -n "${GIT_USER_NAME:-}" ]; then
    git config --global user.name "$GIT_USER_NAME"
fi
if [ -n "${GIT_USER_EMAIL:-}" ]; then
    git config --global user.email "$GIT_USER_EMAIL"
fi

# GPG keys are not forwarded into the container (credential isolation),
# so commit signing would always fail. Disable it via global config as a
# baseline; app-init.sh also sets GIT_CONFIG env vars which override even
# repo-level config.
git config --global commit.gpgsign false

# SSH keys are not available in the container (SSH_AUTH_SOCK is cleared
# and credential sockets are removed), so rewrite git SSH URLs to HTTPS.
# Sandcat's secret substitution handles GitHub token authentication over
# HTTPS transparently.
git config --global --replace-all url."https://github.com/".insteadOf "git@github.com:" "git@github.com:"
git config --global --replace-all url."https://github.com/".insteadOf "ssh://git@github.com/" "ssh://git@github.com/"

# If Java is installed (via mise), import the mitmproxy CA into Java's trust
# store. Java uses its own cacerts and ignores the system CA store.
CA_CERT="/mitmproxy-config/mitmproxy-ca-cert.pem"

# Ensure mise is on PATH. `su - vscode` resets the environment and sources
# only the first of ~/.bash_profile, ~/.bash_login, ~/.profile.  If
# ~/.bash_profile exists (e.g. created by VS Code on a persistent volume),
# ~/.profile — where the Dockerfile adds mise — is never read.
if ! command -v mise >/dev/null 2>&1; then
    export PATH="/home/vscode/.local/bin:/home/vscode/.local/share/mise/shims:$PATH"
fi

MISE_JAVA_HOME="$(mise where java 2>/dev/null || true)"
if [ -n "$MISE_JAVA_HOME" ] && [ -f "$CA_CERT" ]; then
    # Create a version-independent symlink so JAVA_HOME doesn't depend
    # on the mise Java version.
    SANDCAT_DIR="$HOME/.local/share/sandcat"
    mkdir -p "$SANDCAT_DIR"
    ln -sfn "$MISE_JAVA_HOME" "$SANDCAT_DIR/java-home"

    JAVA_CACERTS="$MISE_JAVA_HOME/lib/security/cacerts"
    SANDCAT_CACERTS="$SANDCAT_DIR/cacerts"
    if [ -f "$JAVA_CACERTS" ]; then
        # Import on first start; on restart the alias already exists (harmless failure).
        if keytool -importcert -trustcacerts -noprompt \
            -alias mitmproxy \
            -file "$CA_CERT" \
            -keystore "$JAVA_CACERTS" \
            -storepass changeit >/dev/null 2>&1; then
            echo "Imported mitmproxy CA into Java trust store"
        fi

        # Create/update a standalone copy of the trust store (with the mitmproxy
        # CA) so JAVA_TOOL_OPTIONS can point all JVMs to it — including
        # ones downloaded later by tools like Coursier (Scala Metals).
        cp "$JAVA_CACERTS" "$SANDCAT_CACERTS"

        # scala-cli is a GraalVM native binary that ignores JAVA_TOOL_OPTIONS
        # and JAVA_HOME for trust store resolution. Pre-create its config
        # so the trust store is used even if scala-cli isn't installed yet
        # (e.g. when Metals downloads it later).
        SCALACLI_CONFIG="$HOME/.local/share/scalacli/secrets/config.json"
        mkdir -p "$(dirname "$SCALACLI_CONFIG")"
        cat > "$SCALACLI_CONFIG" << EOFJSON
{
  "java": {
    "properties": ["javax.net.ssl.trustStore=$SANDCAT_CACERTS","javax.net.ssl.trustStorePassword=changeit"]
  }
}
EOFJSON

    fi
fi

# Seed the onboarding flag so Claude Code uses the API key without interactive
# setup. Only written when the user configured an ANTHROPIC_API_KEY secret.
if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
    echo '{"hasCompletedOnboarding":true}' > "$HOME/.claude.json"
fi

# Claude Code is installed at build time (Dockerfile.app).
# Background update so it doesn't block startup.
(claude install >/dev/null 2>&1 &)

