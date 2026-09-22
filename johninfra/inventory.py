from lib.storedhost import read_hosts

production = [
    (
        host.host,
        {
            k: v for k, v in {
            "ssh_user": host.ssh.user,
            "ssh_key": host.ssh.key_dir,
            "ssh_key_password": host.secrets.ssh_key_password,
            "_sudo": host.sudo,
            "_sudo_password": host.secrets.sudo_password
        }.items() if v is not None
        }
    ) for host in read_hosts()
]