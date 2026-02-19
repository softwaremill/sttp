#!/bin/bash
#
# Post-start hook for VS Code dev containers.
# Runs after VS Code connects and sets up its remote server.
#
# VS Code forwards host credential sockets into the container
# (SSH agent, git credential helper).  Clearing the env vars via
# remoteEnv hides the paths, but the socket files still exist in
# /tmp and can be discovered by scanning.  Remove them as a
# best-effort hardening measure.
#
# This is not bulletproof — VS Code could recreate sockets on
# reconnect, or change the naming pattern in future versions.
#
set -e

remove_sockets() {
    local pattern="$1"
    local label="$2"
    local found=0

    for sock in $pattern; do
        [ -e "$sock" ] || continue
        if rm -f "$sock" 2>/dev/null; then
            echo "sandcat: removed forwarded $label socket: $sock"
        else
            echo "sandcat: warning: could not remove $sock (owned by root?)" >&2
        fi
        found=1
    done

    if [ "$found" -eq 0 ]; then
        echo "sandcat: no $label socket found in /tmp (pattern: $pattern)" >&2
    fi
}

remove_sockets "/tmp/vscode-ssh-auth-*.sock" "SSH agent"
remove_sockets "/tmp/vscode-git-*.sock" "git credential"
