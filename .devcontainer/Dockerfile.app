FROM mcr.microsoft.com/devcontainers/base:debian

# gosu is used by the entrypoint to drop privileges.
# ca-certificates, curl, git are already in the devcontainers base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends gh gosu jq vim \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 sandcat/scripts/app-init.sh /usr/local/bin/app-init.sh
COPY --chmod=755 sandcat/scripts/app-user-init.sh /usr/local/bin/app-user-init.sh

USER vscode

# Install mise (SDK manager), then use it to install Node.js and Claude Code.
RUN curl https://mise.run | sh
# Make mise available in login shells (su - vscode) and Docker CMD/RUN.
RUN echo 'export PATH="/home/vscode/.local/bin:/home/vscode/.local/share/mise/shims:$PATH"' >> /home/vscode/.profile
ENV PATH="/home/vscode/.local/bin:/home/vscode/.local/share/mise/shims:$PATH"
RUN mise use -g node@lts \
    && npm install -g @anthropic-ai/claude-code

RUN mise use -g java@21
RUN mise use -g sbt@1.12

# If Java was installed above, bake JAVA_HOME and trust-store paths into the
# image.  VS Code may probe the environment before the entrypoint finishes
# importing the mitmproxy CA; having these ready avoids a race where Metals
# (or other JVM tooling) starts without JAVA_HOME or JAVA_TOOL_OPTIONS.
# The entrypoint will import the mitmproxy CA into the cacerts copy at runtime.
RUN if MISE_JAVA=$(mise where java 2>/dev/null); then \
    dir="$HOME/.local/share/sandcat"; mkdir -p "$dir"; \
    ln -sfn "$MISE_JAVA" "$dir/java-home"; \
    cp "$MISE_JAVA/lib/security/cacerts" "$dir/cacerts" 2>/dev/null || true; \
    { echo ''; \
    echo '# sandcat-java-env'; \
    echo '_sc_java="$HOME/.local/share/sandcat/java-home"'; \
    echo '_sc_cacerts="$HOME/.local/share/sandcat/cacerts"'; \
    echo '[ -L "$_sc_java" ] && export JAVA_HOME="$_sc_java"'; \
    echo '[ -f "$_sc_cacerts" ] && export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$_sc_cacerts -Djavax.net.ssl.trustStorePassword=changeit"'; \
    echo 'unset _sc_java _sc_cacerts'; \
    } >> "$HOME/.bashrc"; \
    fi

# Pre-create the Claude config directory and seed onboarding flag so Claude
# Code can use an API key from the environment without interactive setup.
RUN mkdir -p /home/vscode/.claude \
    && echo '{"hasCompletedOnboarding":true}' > /home/vscode/.claude.json

RUN echo 'alias claude-yolo="claude --dangerously-skip-permissions"' >> /home/vscode/.bashrc

USER root
ENTRYPOINT ["/usr/local/bin/app-init.sh"]
