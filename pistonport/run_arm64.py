"""Run an experimental PistonPort image with a SpartanPort ARM64 runtime."""

import argparse
import fcntl
import os
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--probe", required=True, type=Path, help="ARM64 spartanport-probe executable")
    parser.add_argument("--image", required=True, type=Path, help="image from pistonport/prepare.py")
    parser.add_argument("--game", required=True, type=Path, help="extracted CUSA07027 v01.00 folder")
    parser.add_argument("--profile", required=True, type=Path, help="writable save and log directory")
    args = parser.parse_args()
    probe = args.probe.resolve()
    image = args.image.resolve()
    game = args.game.resolve()
    profile = args.profile.resolve()

    with probe.open("rb") as stream:
        header = stream.read(20)
    if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\xb7\x00":
        parser.error("--probe must be a little-endian ARM64 ELF executable")
    with image.open("rb") as stream:
        if stream.read(8) != b"G3PROBE5":
            parser.error("--image must be a G3PROBE5 image")
    if not (game / "eboot.bin").is_file():
        parser.error("--game must point to the extracted folder containing eboot.bin")

    profile.mkdir(parents=True, exist_ok=True)
    log = profile / "native-last.log"
    env = os.environ.copy()
    for name, value in {
        "SPARTAN_CONFIG": str(profile / "pistonport.ini"),
        "SPARTAN_GPU_USER_DIR": str(profile),
        "SPARTAN_FPS_LIMIT": "30",
        "SPARTAN_ANISOTROPY": "0",
        "SPARTAN_FRAME_STATS": "1",
    }.items():
        env.setdefault(name, value)
    command = [probe, image, "--app0", game, "--user", profile, "--timeout", "0"]
    with (profile / ".pistonport-session.lock").open("a+b") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error("--profile is already in use")
        with log.open("wb") as stream:
            result = subprocess.run(
                command, cwd=profile, env=env, stdin=subprocess.DEVNULL,
                stdout=stream, stderr=subprocess.STDOUT, check=False,
            )
    print(f"Runtime log: {log}", file=sys.stderr)
    return result.returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except OSError as error:
        sys.exit(str(error))
