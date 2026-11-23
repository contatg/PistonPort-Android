"""Prepare a local game image and start the ARM64 runtime inside the APK."""

import argparse
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

import prepare
import run_arm64

RUNTIME = Path(__file__).resolve().parent


def image_for(game, profile, resources=RUNTIME, link_boot=None, resolution="540p"):
    if resolution not in ("360p", "480p", "540p", "720p", "900p", "native", "1440p"):
        raise ValueError("Resolução interna não suportada.")
    prepare.check_game_folder(game)
    manifest = json.loads((resources / "image.json").read_text(encoding="utf-8"))
    signature = hashlib.sha256()
    for name, expected in manifest["inputs"].items():
        with (game / name).open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != expected:
            raise ValueError(f"Arquivo incompatível com CUSA07027 v01.00: {name}")
        signature.update(bytes.fromhex(actual))
    signature.update(bytes.fromhex(manifest["image_sha256"]))
    image = profile / f"cars3-{resolution}-{signature.hexdigest()[:16]}.bin"
    if image.is_file():
        return image

    print(f"Preparando o jogo em {resolution}…", flush=True)
    with tempfile.TemporaryDirectory(dir=profile) as directory:
        working = Path(directory)
        linked = prepare.prepare(game, resources / "upstream", None, working, None,
                                 prebuilt=resources / "cars3-runtime.elf", link_boot=link_boot)
        with linked.open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != manifest["image_sha256"]:
            raise ValueError("A imagem gerada não corresponde à revisão esperada.")
        prepare.apply_graphics_patches(
            linked, image, resolution=resolution, shadow_resolution=512,
            skip_intro=True, bloom=False,
        )
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("game", "vulkan"))
    parser.add_argument("game", nargs="?", type=Path)
    args = parser.parse_args()
    profile = Path(os.environ["FXD_FILES"]) / "pistonport"
    launch = ["--probe", str(RUNTIME / "bin/spartanport-probe"), "--profile", str(profile)]
    if args.mode == "vulkan":
        result = run_arm64.main([*launch, "--check-vulkan"])
        log = profile / "vulkan-last.log"
    else:
        if args.game is None:
            parser.error("Selecione a pasta do jogo primeiro.")
        image = image_for(args.game.resolve(), profile)
        result = run_arm64.main([*launch, "--image", str(image), "--game", str(args.game)])
        log = profile / "native-last.log"
    if log.is_file():
        with log.open("rb") as source:
            source.seek(max(0, log.stat().st_size - 8192))
            print(source.read().decode("utf-8", errors="replace"))
    return result


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        sys.exit(str(error))
