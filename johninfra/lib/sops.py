import shutil
import subprocess

import yaml

from log import get_logger

logger = get_logger(__name__)


# def decrypt(file: str) -> str:
#     try:
#         ensure_sops()
#         result = subprocess.run(
#             ["sops", "decrypt", file],
#             capture_output=True, text=True, check=True
#         )
#         return result.stdout.strip()
#     except FileNotFoundError:
#         logger.error("Hosts file not found")
#         raise

class SopsError(RuntimeError):
    pass


def _run_sops(args: list[str]) -> subprocess.CompletedProcess:
    if shutil.which("sops") is None:
        raise SopsError("sops binary not found on PATH")
    return subprocess.run(["sops", *args], capture_output=True, text=True)


def decrypt(path: str) -> dict:
    result = _run_sops(["decrypt", path])

    if result.returncode != 0:
        raise SopsError(
            f"sops failed decrypting {path!r} "
            f"(exit code {result.returncode}):\n{result.stderr.strip()}"
        )

    try:
        return yaml.safe_load(result.stdout)
    except yaml.YAMLError as e:
        raise SopsError(f"sops output for {path!r} is not valid YAML: {e}") from e

def encrypt(path: str, in_place: bool = True) -> str | None:
    args = ["encrypt", "--in-place", path] if in_place else ["encrypt", path]
    result = _run_sops(args)

    if result.returncode != 0:
        stderr = result.stderr.strip()

        if "already encrypted" in stderr.lower():
            raise SopsError(f"{path!r} is already encrypted — refusing to double-encrypt")

        raise SopsError(
            f"sops failed encrypting {path!r} "
            f"(exit code {result.returncode}):\n{stderr}"
        )

    return None if in_place else result.stdout
