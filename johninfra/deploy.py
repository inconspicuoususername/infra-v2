"""pyinfra deploy — layer 1: OS baseline for a k0s node (Fedora Server).

Scope is deliberately narrow: get the host into a known, hardened state with the
prerequisites k0s needs, then stop. Cluster install is k0sctl's job; everything
above that is Flux's job.

Run:  pyinfra inventory.py deploy.py
"""
import io

from pyinfra.operations import dnf, files, server, systemd

packageman = dnf
pm_str = "dnf"

unattended_upgrades_pkg = ""

if pm_str == "dnf":
    unattended_upgrades_pkg = "dnf-automatic"
elif pm_str == "apt":
    unattended_upgrades_pkg = "unattended-upgrades"
else:
    raise ValueError("Unknown package manager %s" % pm_str)

# ── Base packages ─────────────────────────────────────────────────────────
packageman.update(name="Update packageman cache")

packageman.packages(
    name="Install base packages",
    packages=[
        "curl",
        "gnupg",
        "ca-certificates",
        "firewalld",
        "chrony",            # time sync — k8s certs/tokens hate clock skew
        unattended_upgrades_pkg,
        "htop",
    ],
)

# ── Kernel prerequisites for k0s/kube-router ──────────────────────────────
files.put(
    name="Load required kernel modules at boot",
    src=io.StringIO("overlay\nbr_netfilter\n"),
    dest="/etc/modules-load.d/k0s.conf",
    create_remote_dir=True,
    # br_netfilter + overlay are the standard k8s node modules.
)

server.shell(
    name="Load kernel modules now",
    commands=["modprobe overlay", "modprobe br_netfilter"],
)

files.put(
    name="Sysctl tuning for kubernetes networking",
    src=io.StringIO((
        "net.bridge.bridge-nf-call-iptables  = 1\n"
        "net.bridge.bridge-nf-call-ip6tables = 1\n"
        "net.ipv4.ip_forward                 = 1\n"
        "fs.inotify.max_user_instances       = 1280\n"
        "fs.inotify.max_user_watches         = 655360\n"
    )),
    dest="/etc/sysctl.d/99-k0s.conf",
    create_remote_dir=True,
)

server.shell(name="Apply sysctl", commands=["sysctl --system"])

# ── Time sync ─────────────────────────────────────────────────────────────
systemd.service(
    name="Enable chrony",
    service="chronyd",
    running=True,
    enabled=True,
)

# ── Firewall ──────────────────────────────────────────────────────────────
# Fedora Server is firewalld-managed; don't fight it with a raw nftables
# ruleset (nftables.service reads /etc/sysconfig/nftables.conf here, and the
# two clobber each other's rules anyway).
#
# Philosophy: be permissive toward the cluster's own traffic — the pod/service
# CIDRs go in the trusted zone so the CNI, konnectivity (8132) and kubelet
# (10250) keep working — and only gate the PUBLIC ingress ports. If pods lose
# connectivity, widen the trusted sources; do not just disable the firewall.
systemd.service(
    name="Enable firewalld",
    service="firewalld",
    running=True,
    enabled=True,
)

server.shell(
    name="Configure firewalld",
    commands=[
        # cluster-internal traffic (CNI, konnectivity, kubelet, webhooks)
        "firewall-cmd --permanent --zone=trusted --add-source=10.244.0.0/16",  # pod CIDR
        "firewall-cmd --permanent --zone=trusted --add-source=10.96.0.0/12",   # service CIDR
        # tailscale — the management plane. Traffic on tailscale0 is already
        # identity-gated by the tailnet ACL; 41641/udp lets peers reach us
        # directly instead of falling back to DERP relays.
        "firewall-cmd --permanent --zone=trusted --add-interface=tailscale0",
        "firewall-cmd --permanent --add-port=41641/udp",
        # public services (ssh is already allowed by the default zone)
        "firewall-cmd --permanent --add-port=80/tcp",    # traefik http (ACME http-01 + redirect)
        "firewall-cmd --permanent --add-port=443/tcp",   # traefik https
        "firewall-cmd --permanent --add-port=1666/tcp",  # perforce (p4d)
        "firewall-cmd --permanent --add-port=6443/tcp",  # kube API (remove if you only use kubectl over tailscale)
        "firewall-cmd --reload",
    ],
)

# ── Data disk (OPT-IN, DESTRUCTIVE — review before enabling) ───────────────
# Stateful workloads (p4d db.*, Postgres) want fast LOCAL disk. If you have a
# dedicated SSD/NVMe, mount it at /var/openebs/local so the openebs-hostpath
# StorageClass lands there. mkfs WILL destroy data — uncomment deliberately.
#
# DATA_DISK = "/dev/nvme1n1"
# server.shell(
#     name="Format data disk (DESTRUCTIVE)",
#     commands=[f"blkid {DATA_DISK} || mkfs.ext4 {DATA_DISK}"],
# )
# files.line(
#     name="Mount data disk via fstab",
#     path="/etc/fstab",
#     line=f"{DATA_DISK} /var/openebs/local ext4 defaults,noatime 0 2",
# )
# server.shell(name="Mount all", commands=["mkdir -p /var/openebs/local", "mount -a"])
