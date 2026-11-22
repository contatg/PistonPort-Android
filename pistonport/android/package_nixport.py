"""Stage and optionally package the experimental Nixport build without game data."""

import argparse
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
APPIMAGE = "PistonPort-Android-aarch64.AppImage"


def touch_layout():
    def control(kind, text, x, y, bindings, scale=1):
        return {
            "type": kind, "shape": "CIRCLE", "text": text, "x": x, "y": y,
            "bindings": bindings, "scale": scale, "toggleSwitch": False, "iconId": 0,
        }

    elements = [
        control("STICK", "", x, 0.70, [f"GAMEPAD_{side}_THUMB_{d}" for d in ("UP", "RIGHT", "DOWN", "LEFT")])
        for side, x in (("LEFT", 0.13), ("RIGHT", 0.69))
    ]
    for key, label, x, y in (
        ("R2", "Acelerar", 0.90, 0.80), ("L2", "Frear", 0.29, 0.79),
        ("X", "Turbo", 0.86, 0.56), ("B", "Drift", 0.95, 0.56),
        ("A", "Pular", 0.86, 0.34), ("Y", "Item", 0.95, 0.34),
        ("L1", "L1", 0.12, 0.17), ("R1", "R1", 0.90, 0.17),
        ("START", "Pausa", 0.50, 0.13),
    ):
        elements.append(control("BUTTON", label, x, y, [f"GAMEPAD_BUTTON_{key}", "NONE", "NONE", "NONE"], 0.85))
    return {
        "schema": "nixport.virtualkeys.v1", "name": "PistonPort", "cursorSpeed": 1,
        "hasTouchpad": False, "elements": elements,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path, help="ARM64 AppDir built with SPARTAN_APPDIR_ONLY=1")
    parser.add_argument("--out", required=True, type=Path, help="new directory for the PistonPort package")
    parser.add_argument("--appimage-builder", type=Path, help="optional builder taking AppDir and output AppImage paths")
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    out = args.out.resolve()
    with (runtime / "bin/spartanport-probe").open("rb") as stream:
        header = stream.read(20)
    if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\xb7\x00":
        parser.error("--runtime must contain an ARM64 probe")

    out.mkdir(parents=True)
    appdir = out / "PistonPort.AppDir"
    (appdir / "bin/gpu").mkdir(parents=True)
    for name in ("bin/spartanport-probe", "bin/gpu/libgportcpu.so", "bin/gpu/libgportgpu.so"):
        shutil.copy2(runtime / name, appdir / name)
    shutil.copytree(runtime / "lib", appdir / "lib", symlinks=True)
    shutil.copy2(runtime / "scripts/nixport_host.py", appdir)
    for name in ("prepare.py", "run_arm64.py"):
        shutil.copy2(HERE.parent / name, appdir)
    shutil.copy2(HERE / "nixport_session.py", appdir)
    shutil.copy2(HERE / "AppRun", appdir)
    (appdir / "AppRun").chmod(0o755)
    shutil.copy2(HERE.parent / "launcher/pistonport.png", appdir / "pistonport.png")
    (appdir / ".DirIcon").symlink_to("pistonport.png")
    (appdir / "pistonport.desktop").write_text(
        "[Desktop Entry]\nType=Application\nName=PistonPort Android\n"
        "Exec=AppRun game\nIcon=pistonport\nCategories=Game;\n", encoding="utf-8",
    )

    manifest = {
        "schema": "nixport.port.v1", "id": "pistonport-android", "name": "PistonPort Android",
        "version": "0.1.0", "appimage": APPIMAGE, "storage": {"home": "home"},
        "entries": [
            {"id": action, "name": name, "kind": "direct", "command": f"AppRun {action}", "display": "x11"}
            for action, name in (
                ("game", "Jogar"), ("select", "Trocar arquivos e jogar"),
                ("vulkan", "Testar Vulkan"), ("imports", "Verificar imports"),
            )
        ],
        "virtualKeys": "virtualkeys.json",
        "logs": [f"home/pistonport/{name}-last.log" for name in ("native", "vulkan", "imports")],
    }
    for name, data in (("port.json", manifest), ("virtualkeys.json", touch_layout())):
        (out / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if args.appimage_builder is None:
        print(appdir)
        return
    appimage = out / APPIMAGE
    subprocess.run([args.appimage_builder.resolve(), appdir, appimage], check=True)
    with appimage.open("rb") as stream:
        header = stream.read(20)
    if header[:6] != b"\x7fELF\x02\x01" or header[8:11] != b"AI\x02" or header[18:20] != b"\xb7\x00":
        raise ValueError("The builder did not produce an ARM64 Type 2 AppImage.")
    target = out / "PistonPort-Android-nixport.zip"
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for name in ("port.json", APPIMAGE, "virtualkeys.json"):
            archive.write(out / name, name)
    print(target)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
