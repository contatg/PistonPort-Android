"""Run an experimental PistonPort image with a SpartanPort ARM64 runtime."""

import argparse
import os
import subprocess
import sys
from pathlib import Path

from prepare import check_game_folder


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check-source", action="store_true", help="validate only the local game folder")
    modes.add_argument("--check-vulkan", action="store_true", help="probe the active Vulkan driver")
    modes.add_argument("--check-imports", action="store_true", help="resolve imports without starting the game")
    parser.add_argument("--probe", type=Path, help="ARM64 spartanport-probe executable")
    parser.add_argument("--image", type=Path, help="image from pistonport/prepare.py")
    parser.add_argument("--game", type=Path, help="extracted CUSA07027 v01.00 folder")
    parser.add_argument("--profile", type=Path, help="writable save and log directory")
    args = parser.parse_args(argv)
    if args.check_source:
        if args.game is None:
            parser.error("--game is required with --check-source")
        check_game_folder(args.game)
        print("Game folder verified: CUSA07027 v01.00")
        return 0
    if args.probe is None or args.profile is None:
        parser.error("--probe and --profile are required to run the ARM64 runtime")
    if not args.check_vulkan and (args.image is None or args.game is None):
        parser.error("--image and --game are required to run a game image")

    import fcntl

    probe = args.probe.resolve()
    profile = args.profile.resolve()

    with probe.open("rb") as stream:
        header = stream.read(20)
    if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\xb7\x00":
        parser.error("--probe must be a little-endian ARM64 ELF executable")
    if args.check_vulkan:
        command = [probe, "--vulkan-only"]
        log_name = "vulkan-last.log"
    else:
        game = args.game.resolve()
        image = args.image.resolve()
        check_game_folder(game)
        with image.open("rb") as stream:
            if stream.read(8) != b"G3PROBE5":
                parser.error("--image must be a G3PROBE5 image")
        command = [probe, image, "--app0", game, "--user", profile, "--timeout", "0"]
        log_name = "native-last.log"
        if args.check_imports:
            command.append("--check-imports")
            log_name = "imports-last.log"
    profile.mkdir(parents=True, exist_ok=True)
    log = profile / log_name
    env = os.environ.copy()
    for name, value in {
        "SPARTAN_CONFIG": str(profile / "pistonport.ini"),
        "SPARTAN_GPU_USER_DIR": str(profile),
        "SPARTAN_FPS_LIMIT": "30",
        "SPARTAN_ANISOTROPY": "0",
        "SPARTAN_FRAME_STATS": "1",
    }.items():
        env.setdefault(name, value)
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
    except (OSError, ValueError) as error:
        sys.exit(str(error))
