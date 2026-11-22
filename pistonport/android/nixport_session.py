"""Resolve a local game source through Nixport, then start the ARM64 runtime."""

import argparse
import json
import os
import sys
from pathlib import Path

import nixport_host
import run_arm64
from prepare import check_game_folder


def select_source(config, prompted):
    if config.is_file() and not prompted:
        source = json.loads(config.read_text(encoding="utf-8"))
        game, image = Path(source["game"]), Path(source["image"])
        if game.is_dir() and image.is_file():
            return game, image

    if not nixport_host.available():
        raise ValueError("The Nixport file picker is unavailable in this session.")
    game = nixport_host.pick_directory()
    if not game:
        return None
    game = Path(game)
    check_game_folder(game)
    image = nixport_host.pick_file(initial=str(game.parent), extensions=("bin",))
    if not image:
        return None
    image = Path(image)
    with image.open("rb") as stream:
        if stream.read(8) != b"G3PROBE5":
            raise ValueError("Select the .bin image generated locally by pistonport/prepare.py.")
    config.parent.mkdir(parents=True, exist_ok=True)
    pending = config.with_name(f".{config.name}.{os.getpid()}.tmp")
    pending.write_text(json.dumps({"game": str(game), "image": str(image)}), encoding="utf-8")
    pending.replace(config)
    return game, image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("game", "select", "vulkan", "imports"))
    action = parser.parse_args().action
    runtime = Path(__file__).resolve().parent
    profile = Path(os.environ["SPARTAN_HOME"])
    args = ["--probe", str(runtime / "bin/spartanport-probe"), "--profile", str(profile)]
    if action == "vulkan":
        return run_arm64.main([*args, "--check-vulkan"])
    source = select_source(profile / "source.json", prompted=action == "select")
    if source is None:
        return 0
    game, image = source
    args.extend(("--game", str(game), "--image", str(image)))
    if action == "imports":
        args.append("--check-imports")
    return run_arm64.main(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        sys.exit(str(error))
