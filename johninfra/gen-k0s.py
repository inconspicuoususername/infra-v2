import subprocess

import yaml

import constants
import log
from lib.storedhost import StoredHost, read_hosts

logger = log.get_logger(__name__)

def _tailscale_ip(name: str) -> str:
    out = subprocess.run(
        ["tailscale", "ip", "-4", name],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    if not out:
        raise RuntimeError(f"no tailscale IPv4 for {name!r}")
    return out.splitlines()[0].strip()


def regen_k0sctl(host_list: list[StoredHost]):
    with open(constants.K0SCTL_BASE_FILE, 'r') as base, open(constants.K0SCTL_FILE, 'w') as dst:
        ym = yaml.full_load(base)

        k8s_hosts = [host for host in host_list if host.k8 is not None]

        converted_hosts = []
        sans: list[str] = []
        api_address: str | None = None

        for host in k8s_hosts:
            private_address = _tailscale_ip(host.host)
            sans.extend((private_address, host.host))
            if api_address is None and "controller" in host.k8.role:
                api_address = private_address
            converted_hosts.append({
                "role": host.k8.role,
                "noTaints": host.k8.role == "controller+worker",
                "installFlags": [
                    "--profile=relaxed-disk"
                ],
                "privateAddress": private_address,
                "ssh": {
                    "address": host.host,
                    "user": host.ssh.user,
                    "keyPath": host.ssh.key_dir
                }
            })

        ym["spec"]["hosts"] = converted_hosts
        api = ym["spec"]["k0s"]["config"]["spec"].setdefault("api", {})

        if api_address is not None:
            api["address"] = api_address
        # The serving cert must also be valid for every tailscale address that
        # kubectl and the peer controllers connect through.
        api["sans"] = sans

        print(f"writing to {constants.K0SCTL_FILE}")
        dst.write(yaml.dump(ym, default_flow_style=False))


if __name__ == "__main__":
    hosts = read_hosts()
    regen_k0sctl(hosts)
