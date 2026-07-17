#!/usr/bin/env python3
import argparse
import getpass
import logging
import os
import shlex
import sys

from pydantic_settings.sources.providers import secrets
from pyinfra.api import Config, Inventory, State, BaseStateCallback, StringCommand
from pyinfra.api.connect import connect_all
from pyinfra.api.exceptions import PyinfraError
from pyinfra.api.facts import get_facts
from pyinfra.api.operation import add_op
from pyinfra.api.operations import run_ops
from pyinfra.facts.server import Command
from pyinfra.operations import server, systemd, dnf
from pyinfra_cli.log import setup_logging
from pyinfra_cli.prints import print_results

import constants
import log
from lib.storedhost import key_needs_password, StoredHost, StoredHostK8, StoredHostSSH, StoredHostSecrets, append_host

from typedef.tailscale import TailscaleStatus


logger = log.get_logger(__name__)

def parse_args():
    parser = argparse.ArgumentParser(
        description="Enroll a fresh machine into the tailnet over its LAN address.",
    )
    parser.add_argument("target", help="host to enroll, as [user@]address")
    parser.add_argument(
        "--user",
        default="root",
        help="ssh user (default: root; a user@ prefix in TARGET wins)",
    )
    parser.add_argument(
        "--key",
        help="ssh private key path (default: ssh-agent / your default key)",
    )
    parser.add_argument(
        "--ts-hostname",
        help="device name on the tailnet (default: the machine's own hostname)",
    )
    parser.add_argument(
        "--kubernetes",
        help="k0ctl role. if set, enrolls the device into the inventory as part of the kube cluster",
        choices=[None, "controller+worker", "worker", "controller"],
        default=None
    )
    return parser.parse_args()


def check_sudo(inventory: Inventory) -> str | None:
    """One throwaway sudo command per host. True only if all succeed."""
    password = ""
    for     host in inventory:
        logger.info(f"{host.name}: checking sudo")
        password = getpass.getpass(prompt="Enter password for sudo: ")
        success, output = host.run_shell_command(
            StringCommand("true"),
            _sudo=True,
            _sudo_password=password,
        )
        if not success:
            lines = [l.line for l in output.combined_lines]
            logger.error(f"{host.name}: sudo check failed -> {lines}")
            return None
        else:
            host.connector_data["prompted_sudo_password"] = password
    return password


def main():
    args = parse_args()

    authkey = os.environ.get("TS_AUTHKEY")
    if not authkey:
        sys.exit("TS_AUTHKEY is not set — generate a pre-auth key in the tailscale admin console")

    user, _, address = args.target.rpartition("@")
    user = user or args.user
    sudo_required = user != "root"
    has_sudo_password = False

    host_data = {"ssh_user": user}
    if args.key:
        host_data["ssh_key"] = args.key

    _password = None
    if args.key:
        host_data["ssh_key"] = args.key
        if key_needs_password(args.key):
            # Key passphrases use "ssh_key_password" in pyinfra
            host_data["ssh_key_password"] = getpass.getpass(prompt="Enter password for ssh key: ")

    # 2. Handle Plain Text SSH Password (if no key is provided, or as a fallback)
    else:
        # Plain password authentication uses "ssh_password"
        host_data["ssh_password"] = getpass.getpass(prompt="Enter SSH password: ").rstrip("\r\n")
    #
    # if sudo_required:
    #     host_data["_sudo"] = True
    #     if input("Sudo password? (y/n): ").lower() == 'y':
    #         host_data["_sudo_password"] = getpass.getpass(prompt="Enter password for sudo: ")
    #         has_sudo_password = True
    #     else:
    #         print("Aborting.")


    # ── The pyinfra CLI in miniature: inventory → state → connect → ops ──
    inventory = Inventory(([(address, host_data)], {}))
    state = State(inventory, Config())
    setup_logging(logging.INFO)
    # logging.getLogger("pyinfra").setLevel(logging.CRITICAL)
    # state.add_callback_handler(PrettyOutput())

    try:
        connect_all(state)
    except PyinfraError as e:
        sys.exit(f"connect failed: {e}")

    # add_op(
    #     state,
    #     server.shell,
    #     name="Install tailscale",
    #     commands=["command -v tailscale >/dev/null || curl -fsSL https://tailscale.com/install.sh | sh"],
    #     _sudo=sudo,
    # )

    sudo_pass = check_sudo(inventory)
    if sudo_pass is None:
        raise ValueError("Sudo check failed")

    add_op(
        state,
        dnf.repo,
        name="Add Tailscale repository",
        src="https://pkgs.tailscale.com/stable/fedora/tailscale.repo",
        present=True,
        _sudo=sudo_required,
    )

    add_op(
        state,
        dnf.packages,
        name="Install tailscaled",
        packages=["tailscale"],
        update=True,
        _sudo=sudo_required,
    )

    add_op(
        state,
        systemd.service,
        name="Enable tailscaled",
        service="tailscaled",
        running=True,
        enabled=True,
        _sudo=sudo_required,
    )

    up = f"tailscale up --auth-key={shlex.quote(authkey)}"
    if args.ts_hostname:
        up += f" --hostname={shlex.quote(args.ts_hostname)}"

    add_op(
        state,
        server.shell,
        name="Enroll in tailnet",
        commands=[up],
        _sudo=sudo_required,
    )

    run_ops(state)
    print_results(state)

    if state.failed_hosts:
        sys.exit(1)

    # Echo the identity to put in inventory.py, so the LAN address can be forgotten.
    # (first DNSName in the status JSON is Self's; grep is fine for a one-liner)
    identity_cmd = "tailscale status --json 2>/dev/null || true"
    for host, ts_info_json in get_facts(state, Command, kwargs={"command": identity_cmd}).items():
        ts_info = TailscaleStatus.model_validate_json(ts_info_json)
        logger.info(f"\n{host} is enrolled:\n{ts_info.Self.DNSName or '(could not read tailscale status — check manually)'}")
        hostfull = StoredHost(
            name=ts_info.Self.HostName,
            hostname=ts_info.Self.HostName,
            host=ts_info.Self.DNSName,
            sudo=sudo_required,
            k8=None if args.kubernetes is None else StoredHostK8(
                role=args.kubernetes
            ),
            ssh=StoredHostSSH(
                user=user,
                key_dir=host_data["ssh_key"],
            ),
            secrets=StoredHostSecrets(
                ssh_key_password=host_data["ssh_key_password"],
                sudo_password=sudo_pass if len(sudo_pass) > 0 else None,
            )
        )
        append_host(hostfull)
        logger.info("Successfully saved enrolled host to {}".format(constants.HOSTS_FILE))






if __name__ == "__main__":
    main()
