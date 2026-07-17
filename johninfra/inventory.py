from lib.storedhost import read_hosts

production = [
    (
        host.host,
        {
            "ssh_user": host.ssh.user,
            "ssh_key": host.ssh.key_dir,
            "ssh_key_password": host.secrets.ssh_key_password,
            "_sudo": host.sudo,
            "_sudo_password": host.secrets.sudo_password
        }
    ) for host in read_hosts()
]