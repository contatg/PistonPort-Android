"""Bundle the ARM64 executable and upstream image preparation tools in the APK rootfs."""

import argparse
import hashlib
import shutil
import struct
import subprocess
import tarfile
import tempfile
from pathlib import Path
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
PORT = "PistonPort-Android-nixport.zip"
APPIMAGE = "PistonPort-Android-aarch64.AppImage"
WINDOWS_RUNTIME = "PistonPort/PistonPort.Runtime/"
REFERENCE_FILES = {
    "image.json": "image.json",
    "tools/cars3-runtime.elf": "cars3-runtime.elf",
    "upstream/scripts/prepare.py": "upstream/scripts/prepare.py",
    "upstream/scripts/link_modules.py": "upstream/scripts/link_modules.py",
    "upstream/scripts/link_libc.py": "upstream/scripts/link_libc.py",
}


def copy_member(archive, info, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with archive.open(info) as source, destination.open("wb") as target:
        shutil.copyfileobj(source, target)


def extract_runtime(port, work):
    appimage = work / APPIMAGE
    checksum = port.with_name(PORT + ".sha256").read_text().split()[0]
    with port.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != checksum:
            raise ValueError("ARM64 artifact checksum mismatch")
    with ZipFile(port) as archive:
        copy_member(archive, APPIMAGE, appimage)
    with appimage.open("rb") as source:
        header = source.read(64)
        if header[:6] != b"\x7fELF\x02\x01" or header[8:11] != b"AI\x02" or header[18:20] != b"\xb7\0":
            raise ValueError("Expected an ARM64 Type 2 AppImage")
        offset = struct.unpack_from("<Q", header, 40)[0]
        entry_size, count = struct.unpack_from("<HH", header, 58)
        offset += entry_size * count
        source.seek(offset)
        if source.read(4) != b"hsqs":
            raise ValueError("Could not locate the AppImage squashfs payload")
    runtime = work / "runtime"
    subprocess.run(
        ["unsquashfs", "-no-progress", "-o", str(offset), "-d", runtime, appimage], check=True
    )
    return runtime


def stage_preparer(windows, destination):
    with ZipFile(windows) as archive:
        for source, target in REFERENCE_FILES.items():
            copy_member(archive, WINDOWS_RUNTIME + source, destination / target)
        elf = destination / "cars3-runtime.elf"
        with elf.open("rb") as source:
            header = source.read(20)
        if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\x3e\0":
            raise ValueError("The guest bridge must be an x86-64 ELF")
        library = WINDOWS_RUNTIME + "lib/elftools/"
        for item in archive.infolist():
            if item.is_dir() or not item.filename.startswith(library):
                continue
            path = Path(item.filename[len(library):])
            if path.is_absolute() or ".." in path.parts:
                raise ValueError(f"Invalid Python wheel path: {path}")
            copy_member(archive, item, destination / "lib/python/elftools" / path)
        if not (destination / "lib/python/elftools/elf/elffile.py").is_file():
            raise ValueError("Missing pyelftools in the upstream release")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=Path, required=True)
    parser.add_argument("--windows-tools", type=Path, required=True)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--interpreter", required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        runtime = extract_runtime(args.port, root)
        destination = root / "pistonport"
        destination.mkdir()
        for name in ("bin", "lib"):
            shutil.copytree(runtime / name, destination / name, symlinks=True)
        for filename in ("prepare.py", "run_arm64.py"):
            shutil.copy2(HERE.parent / filename, destination)
        for filename in ("app-session.sh", "app_session.py"):
            shutil.copy2(HERE / filename, destination)
        shutil.copy2(HERE.parents[1] / "LICENSE", destination / "LICENSE-PistonPort")
        stage_preparer(args.windows_tools, destination)
        probe = destination / "bin/spartanport-probe"
        subprocess.run(["patchelf", "--set-interpreter", args.interpreter, probe], check=True)
        with tarfile.open(args.payload / "rootfs-arm64.tar", "a") as archive:
            archive.add(destination, arcname="./opt/pistonport")
    with (args.payload / "rootfs-arm64.tar").open("rb") as source:
        version = hashlib.file_digest(source, "sha256").hexdigest()[:16]
    (args.payload / "payload.version").write_text(version + "\n")


if __name__ == "__main__":
    main()
