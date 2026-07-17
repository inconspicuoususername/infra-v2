import os
import shutil
import subprocess
from typing import Optional, TypedDict, Literal
import yaml
import constants
from pydantic import BaseModel, TypeAdapter, ValidationError

from lib import sops
from log import get_logger

logger = get_logger(__name__)

class StoredHostSSH(BaseModel):
    user: str
    key_dir: str

class StoredHostSecrets(BaseModel):
    ssh_key_password: str | None
    sudo_password: Optional[str] = None

class StoredHostK8(BaseModel):
    role: Literal["controller", "worker", "controller+worker"]

class StoredHost(BaseModel):
    name: str
    hostname: str
    host: str
    sudo: bool
    k8: StoredHostK8 | None
    ssh: StoredHostSSH
    secrets: StoredHostSecrets



def read_hosts() -> list[StoredHost]:
    try:
        if not os.path.exists(constants.HOSTS_FILE):
            return []

        raw_hosts = sops.decrypt(str(constants.HOSTS_FILE))
        return TypeAdapter(list[StoredHost]).validate_python(raw_hosts["hosts"])
    except FileNotFoundError:
        logger.error("Hosts file not found")
        raise

def write_hosts(hosts: list[StoredHost]):
    with open(constants.HOSTS_FILE, 'w') as ymlfile:
        dict_list = [hosts.model_dump() for hosts in hosts]

        yaml.dump({"hosts": dict_list}, ymlfile, sort_keys=False)
        logger.info("Wrote hosts file to {}".format(constants.HOSTS_FILE))
    sops.encrypt(constants.HOSTS_FILE, True)
    logger.info("Encrypted hosts file to {}".format(constants.HOSTS_FILE))

def append_host(host: StoredHost):
    rh = read_hosts()
    rh.append(host)
    write_hosts(rh)


from cryptography.hazmat.primitives.serialization import (
    load_ssh_private_key, load_pem_private_key
)

def key_needs_password(path: str) -> bool:
    full_path = os.path.expanduser(path)

    with open(full_path, "rb") as f:
        data = f.read()
    for loader in (load_ssh_private_key, load_pem_private_key):
        try:
            loader(data, password=None)
            return False
        except TypeError:
            return True   # PEM: encrypted, no password given
        except ValueError as e:
            if "password-protected" in str(e):
                return True   # OpenSSH: encrypted
            continue  # wrong format for this loader, try next
    raise ValueError("Not a recognized private key")

if __name__ == "__main__":
    __hosts = read_hosts()