#!/bin/sh
# Lets borgmatic run as any non-root UID on a read-only root filesystem, which
# is what restricted Pod Security requires.
set -eu

if [ -z "${HOME:-}" ] || [ "$HOME" = "/" ] || ! mkdir -p "$HOME" 2>/dev/null; then
    export HOME=/tmp/home
    mkdir -p "$HOME"
fi

# ssh and borg both refuse to run as a UID without a passwd entry.
if ! getent passwd "$(id -u)" >/dev/null 2>&1; then
    cp /etc/passwd /tmp/nss-passwd
    echo "borg:x:$(id -u):$(id -g):borg:$HOME:/sbin/nologin" >> /tmp/nss-passwd
    export NSS_WRAPPER_PASSWD=/tmp/nss-passwd NSS_WRAPPER_GROUP=/etc/group
    export LD_PRELOAD=/usr/lib/libnss_wrapper.so
fi

# The key arrives via env rather than a secret volume: ssh rejects key files
# that are group-readable, which is what fsGroup makes them.
if [ -n "${BORG_SSH_KEY:-}" ] && [ -z "${BORG_RSH:-}" ]; then
    mkdir -p "$HOME/.ssh"
    (umask 077 && printf '%s\n' "$BORG_SSH_KEY" > "$HOME/.ssh/id_borg")
    unset BORG_SSH_KEY
    if [ -n "${BORG_KNOWN_HOSTS_FILE:-}" ]; then
        hosts="-o UserKnownHostsFile=$BORG_KNOWN_HOSTS_FILE -o StrictHostKeyChecking=yes"
    else
        hosts="-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=accept-new"
    fi
    export BORG_RSH="ssh -i $HOME/.ssh/id_borg -o IdentitiesOnly=yes -o BatchMode=yes $hosts"
fi

exec "$@"
