import yaml

import constants
import log
from lib.storedhost import StoredHost, read_hosts

logger = log.get_logger(__name__)

def regen_k0sctl(host_list: list[StoredHost]):
    with open(constants.K0SCTL_BASE_FILE, 'r') as base, open(constants.K0SCTL_FILE, 'w') as dst:
        ym = yaml.full_load(base)
        converted_hosts = [
            {
                "role": host.k8.role,
                "noTaints": host.k8.role == "controller+worker",
                "ssh": {
                    "address": host.host,
                    "user": host.ssh.user,
                    "keyPath": host.ssh.key_dir
                }
            } for host in host_list
            if host.k8 is not None
        ]
        ym["spec"]["hosts"] = converted_hosts
        dst.write(yaml.dump(ym, default_flow_style=False))

if __name__ == "__main__":
    hosts = read_hosts()
    regen_k0sctl(hosts)