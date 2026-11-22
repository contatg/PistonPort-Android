"""Prepare the CUSA07027 SpartanPort image from an existing local game dump."""

import argparse
import hashlib
import math
import os
import struct
import subprocess
import sys
from pathlib import Path

TARGET_HASH = "56d27cc83b7fe34921fb07413dd7c8fb255daf357a3f81c2d882969f001ff654"
RESOLUTIONS = {
    "540p": (960, 540),
    "720p": (1280, 720),
    "1280x800": (1280, 800),
    "900p": (1600, 900),
    "native": (1920, 1080),
    "2560x1080": (2560, 1080),
    "1440p": (2560, 1440),
    "3440x1440": (3440, 1440),
    "3840x1600": (3840, 1600),
    "5120x1440": (5120, 1440),
    "4K": (3840, 2160),
}
_RESOLUTION_ALIASES = {
    "540p": "540p",
    "960x540": "540p",
    "720p": "720p",
    "1280x720": "720p",
    "1280x800": "1280x800",
    "800p": "1280x800",
    "900p": "900p",
    "1600x900": "900p",
    "native": "native",
    "1080p": "native",
    "1920x1080": "native",
    "2560x1080": "2560x1080",
    "1440p": "1440p",
    "2560x1440": "1440p",
    "3440x1440": "3440x1440",
    "3840x1600": "3840x1600",
    "5120x1440": "5120x1440",
    "4k": "4K",
    "2160p": "4K",
    "3840x2160": "4K",
}
SHADOW_RESOLUTIONS = (512, 1024, 2048, 3072, 4096)
FOV_SCALES = (100, 110, 120, 130)
BASE_DMEM_MB = 5056
# The toolkit and public NetCtl callbacks use the same PS4 calling convention.
# Both use the offline network provider in this build.
ALIASES = {
    "wIsKy+TfeLs#libSceNetCtlForNpToolkit": "UJ+Z7Q+4ck0#libSceNetCtl",
    "TywrFKCoLGY#libSceSaveData": "ZkZhskCPXFw#libSceSaveData",
    "JELHf4xPufo#libSceNpManagerForToolkit": "3Zl8BePTh9Y#libSceNpManager",
    "u5oqtlIP+Fw#libSceNetCtlForNpToolkit": "iQw3iQPhvUQ#libSceNetCtl",
    "2oUqKR5odGc#libSceNetCtlForNpToolkit": "Rqm2OnZMCz0#libSceNetCtl",
}
EXPORTS = {
    "NWtTN10cJzE#libSceLibcInternalExt": "sceLibcHeapGetTraceInfo",
    "uWyW3v98sU4#libkernel": "sceKernelCheckReachability",
    "clVvL4ZDntw#libScePad": "scePadSetMotionSensorState",
    "WslcK1FQcGI#libkernel": "sceKernelIsNeoMode",
    "0z45PIH+SNI#libSceSaveData": "cars3_mount2",
    "j2AIqSqJP0w#libkernel": "cars3_getdents",
    "-o5uEDpN+oY#libkernel": "cars3_utc_to_local",
    "0NTHN1NKONI#libkernel": "cars3_local_to_utc",
    "XgvSuIdnMlw#libSceSaveData": "cars3_get_param",
    "65VH0Qaaz6s#libSceSaveData": "cars3_mount_info",
    "cGjO3wM3V28#libSceSaveData": "cars3_load_icon",
    "FIs3-UQT9sg#libScePosix": "cars3_get_sched",
    "Xs9hdiD7sAA#libScePosix": "cars3_set_sched",
    "isruqthpYcw#libSceSharePlay": "cars3_share_initialize",
    "UaLjloJinow#libSceSharePlay": "cars3_share_terminate",
    "kvYEw2lBndk#libSceGameLiveStreaming": "cars3_stream_initialize",
    "9yK6Fk8mKOQ#libSceGameLiveStreaming": "cars3_stream_terminate",
    "wuI7c7UNk0A#libSceUserServiceForNpToolkit": "cars3_user_subscribe",
    "spW--yoLQ9o#libSceUserServiceForNpToolkit": "cars3_user_unsubscribe",
    "0c7HbXRKUt4#libSceNpManagerForToolkit": "cars3_np_subscribe",
    "YIvqqvJyjEc#libSceNpManagerForToolkit": "cars3_np_unsubscribe",
    "v6EZ-YWRdMs#libScePlayGo": "scePlayGoGetEta",
    "3OMbYZBaa50#libScePlayGo": "scePlayGoGetLanguageMask",
    "Nn7zKwnA5q0#libScePlayGo": "scePlayGoGetToDoList",
    "-Q1-u1a7p0g#libScePlayGo": "scePlayGoPrefetch",
    "LosLlHOpNqQ#libScePlayGo": "scePlayGoSetLanguageMask",
    "BpFoboUJoZU#libkernel": "sceKernelCreateEventFlag",
    "8mql9OcQnd4#libkernel": "sceKernelDeleteEventFlag",
    "IOnSvHzqu6A#libkernel": "sceKernelSetEventFlag",
    "7uhBFWRAS60#libkernel": "sceKernelClearEventFlag",
    "JTvBflhYazQ#libkernel": "sceKernelWaitEventFlag",
    "9lvj5DjHZiA#libkernel": "sceKernelPollEventFlag",
    "hVYD7Ou2pCQ#libSceFiber": "sceFiberInitialize",
    "JeNX5F-NzQU#libSceFiber": "sceFiberFinalize",
    "p+zLIOg27zU#libSceFiber": "sceFiberGetSelf",
    "B0ZX2hx9DMw#libSceFiber": "sceFiberReturnToThread",
    "a0LLrZWac0M#libSceFiber": "sceFiberRun",
    "PFT2S-tJ7Uk#libSceFiber": "sceFiberSwitch",
}
IMPORTS = {
    "IWIBBdTHit4#libkernel": "cars3_map_flag_page",
    "eV9wAD2riIA#libkernel": "cars3_kernel_stat",
    "geDaqgH9lTg#libkernel": "cars3_key_create",
    "gjP9-KQzoUk#libScePad": "cars3_pad_info",
    "32HQAQdwM2o#libSceSaveData": "cars3_mount",
    "1G3lF1Gg1k8#libkernel": "cars3_open",
    "Cg4srZ6TKbU#libkernel": "cars3_read",
    "UK2Tl2DWUns#libkernel": "cars3_close",
    "oib76F-12fk#libkernel": "cars3_seek",
    "taRWhTJFTgE#libkernel": "cars3_get_direntries",
    "kOcnerypnQA#libkernel": "cars3_zone",
    "uWIYLFkkwqk#libScePlayGo": "cars3_chunk_locus",
    "188x57JYp0g#libkernel": "cars3_sem_create",
    "R1Jvn8bSCW8#libkernel": "cars3_sem_delete",
    "Zxa0VhQVTsk#libkernel": "cars3_sem_wait",
    "4czppHBiriw#libkernel": "cars3_sem_signal",
    "1tKyG7RlMJo#libkernel": "cars3_thread_priority",
    "aI+OeCz8xrQ#libkernel": "cars3_thread_self",
    "W0Hpm2X0uPE#libkernel": "cars3_set_priority",
    "+BzXYkqYeLE#libkernel": "cars3_key_set",
    "eoht7mQOCmo#libkernel": "cars3_key_get",
}


def set_60fps_presentation(image):
    # CUSA07027 01.00 requests two vblanks in the base-PS4 mode. Select one
    # both when opening VideoOut (zero-based rate) and when restoring the
    # renderer's setting after a transition (one-based interval).
    changes = [
        (0x5A8C5, "0f b6 f1 41 89 74 24 50", "31 f6 90 41 89 74 24 50"),
        (0x58D333, "be 02 00 00 00 55", "be 01 00 00 00 55"),
        # If a pixel counter's valid bit is still clear, Octane already has
        # a timeout path. Poll for at most 500 us instead of 20 ms. Leave the
        # separate frame-ready wait, the result checks and the simulation
        # timestep untouched.
        (0x78037, "48 05 20 4e 00 00", "48 05 f4 01 00 00"),
    ]
    for offset, original, replacement in changes:
        expected = bytes.fromhex(original)
        if image[offset : offset + len(expected)] != expected:
            raise ValueError(f"Unexpected presentation code at {offset:#x}")
        image[offset : offset + len(expected)] = bytes.fromhex(replacement)


def parse_resolution(value):
    key = _RESOLUTION_ALIASES.get(str(value or "").strip().lower(), "native")
    return key, RESOLUTIONS[key]


def parse_shadow_resolution(value):
    try:
        size = int(str(value).strip())
    except (TypeError, ValueError):
        return 2048
    return size if size in SHADOW_RESOLUTIONS else 2048


def parse_fov_scale(value):
    try:
        scale = int(str(value).strip())
    except (TypeError, ValueError):
        return 100
    return scale if scale in FOV_SCALES else 100


def required_heap_spec(resolution="native", shadow_resolution=2048):
    _, (width, height) = parse_resolution(resolution)
    shadow = parse_shadow_resolution(shadow_resolution)
    pixels = width * height
    if pixels > 5_000_000:
        return b'size="3.6G"', 8192
    if pixels > 2_200_000 or shadow > 2048:
        return b'size="2.8G"', 6144
    return None, BASE_DMEM_MB


def required_dmem_mb(resolution="native", shadow_resolution=2048):
    return required_heap_spec(resolution, shadow_resolution)[1]


def _probe_image_offset(data):
    header = struct.Struct("<8s6Q")
    if len(data) < 104:
        raise ValueError("Truncated SpartanPort image")
    magic, size, _entry, ns, nr, ni, _flags = header.unpack_from(data)
    if magic != b"G3PROBE5":
        raise ValueError("Unsupported SpartanPort image version")
    nmods = struct.unpack_from("<Q", data, 96)[0]
    binding_start = 104 + 56 * nmods
    if binding_start + 8 > len(data):
        raise ValueError("Unexpected probe image layout")
    nb = struct.unpack_from("<Q", data, binding_start)[0]
    segment_start = binding_start + 8 + nb * 24
    name_start = segment_start + ns * 24
    relocation_start = name_start + ni * 128
    image_start = relocation_start + nr * 32
    if image_start + size != len(data):
        raise ValueError("Unexpected probe image layout")
    return image_start, size


def _replace_bytes(image, offset, expected, replacement, label):
    end = offset + len(expected)
    if image[offset:end] != expected:
        raise ValueError(f"Unexpected {label} bytes at {offset:#x}")
    image[offset:end] = replacement


def patch_graphics(
    image,
    resolution="native",
    shadow_resolution=2048,
    motion_blur=False,
    fov_scale=100,
    skip_intro=False,
    high_lod=False,
    bloom=True,
):
    """Patch CUSA07027 01.00 guest image for resolution, ultrawide, shadows, FOV, LOD, intro skip, bloom, and motion blur."""
    res_key, (width, height) = parse_resolution(resolution)
    shadow = parse_shadow_resolution(shadow_resolution)
    fov = parse_fov_scale(fov_scale)

    if (width, height) != (1920, 1080):
        w0 = struct.pack("<I", 1920)
        h0 = struct.pack("<I", 1080)
        wh0 = w0 + h0
        w1 = struct.pack("<I", width)
        h1 = struct.pack("<I", height)
        wh1 = w1 + h1
        w0_16 = struct.pack("<H", 1920)
        h0_16 = struct.pack("<H", 1080)
        w1_16 = struct.pack("<H", width)
        h1_16 = struct.pack("<H", height)
        # Update all internal render-target, VideoOut, initial viewport, scissor,
        # and viewport-builder sites while leaving the 1920x1080 UI layout intact.
        for offset, expected, replacement in (
            (0x4467EB, wh0, wh1),
            (0x5649C, wh0, wh1),
            (0x56DB8, w0, w1),
            (0x56DBF, h0, h1),
            (0xDE0370, wh0, wh1),
            (0xDE0380, wh0, wh1),
            (0x5A970, w0, w1),
            (0x5A976, h0, h1),
            (0x5A98B, w0, w1),
            (0x5A991, h0, h1),
            (0x8BD17, w0_16, w1_16),
            (0x8BD1D, h0_16, h1_16),
            (0x8BD3D, w0_16, w1_16),
            (0x8BD58, h0_16, h1_16),
        ):
            _replace_bytes(image, offset, expected, replacement, f"resolution ({res_key})")

        aspect = width / height
        if abs(aspect - (16.0 / 9.0)) > 1e-4:
            ar0_f = struct.pack("<f", 16.0 / 9.0)
            iar0_f = struct.pack("<f", 9.0 / 16.0)
            ar1_f = struct.pack("<f", aspect)
            iar1_f = struct.pack("<f", height / width)
            ar0_d = bytes.fromhex("00 00 00 20 c7 71 fc 3f")
            ar1_d = struct.pack("<d", aspect)
            for offset, expected, replacement in (
                (0x564E2, ar0_f, ar1_f),
                (0xDE0958, iar0_f + ar0_f, iar1_f + ar1_f),
                (0xDE0968, iar0_f + ar0_f, iar1_f + ar1_f),
                (0xE84630, ar0_d, ar1_d),
            ):
                _replace_bytes(image, offset, expected, replacement, f"aspect ratio ({res_key})")
            if aspect > (16.0 / 9.0) + 1e-4:
                # Switch ultrawide FOV handling from Vert- cropping to Hor+ so wider
                # displays expand horizontal view instead of zooming vertically.
                _replace_bytes(
                    image,
                    0x2EA99A,
                    bytes.fromhex("76 48"),
                    bytes.fromhex("eb 48"),
                    f"ultrawide Hor+ ({res_key})",
                )
                # Widen Scaleform's centered 2D orthographic canvas width from 1920.0f
                # to 1080.0f * aspect so 16:9 menus, HUD, and button prompts stay
                # 1:1 proportional and centered instead of stretching horizontally.
                ui_width = struct.pack("<f", 1080.0 * aspect)
                _replace_bytes(
                    image,
                    0x36A487,
                    struct.pack("<f", 1920.0),
                    ui_width,
                    f"ultrawide HUD width ({res_key})",
                )

    if shadow != 2048:
        # Octane reads a single base shadow-map float (2048.0f) and multiplies it
        # by the splitscreen scale factor (1.0 / 0.75 / 0.5) for the 3-cascade map.
        _replace_bytes(
            image,
            0xE32710,
            struct.pack("<f", 2048.0),
            struct.pack("<f", float(shadow)),
            f"shadow resolution ({shadow})",
        )

    if fov != 100:
        # Redirect camera FieldOfView deg->rad load to a 4-byte aligned padding
        # slot right after the function's tail jump, holding (pi/180) * fov_scale.
        scaled_deg2rad = struct.pack("<f", (math.pi / 180.0) * (fov / 100.0))
        _replace_bytes(
            image,
            0x2EA3A7,
            bytes.fromhex("48 8d 05 7a 32 af 00"),
            bytes.fromhex("48 8d 05 2e 00 00 00"),
            f"FOV load ({fov}%)",
        )
        _replace_bytes(
            image,
            0x2EA3DC,
            bytes.fromhex("90 90 90 90"),
            scaled_deg2rad,
            f"FOV constant ({fov}%)",
        )

    if skip_intro:
        # Skip the boot logo video loop (AvLogo / WBG_Logo / Pixar_Logo / DIS / CESA)
        # while keeping Bink setup/teardown intact, and zero the static splash timer.
        _replace_bytes(
            image,
            0x8B0562,
            bytes.fromhex("45 31 e4 4c 8d 7d 90"),
            bytes.fromhex("e9 da 01 00 00 90 90"),
            "skip intro videos",
        )
        _replace_bytes(
            image,
            0x69F3E7,
            bytes.fromhex("81 fb b7 0b 00 00"),
            bytes.fromhex("81 fb 00 00 00 00"),
            "skip logo splash timer",
        )

    if high_lod:
        # Enable Cars 3's built-in ForceHighLOD global flag so cars and track
        # props keep full-detail geometry instead of popping to low-LOD meshes.
        _replace_bytes(
            image,
            0x8C7BF1,
            bytes.fromhex("e8 6a b6 fe ff"),
            bytes.fromhex("b0 01 90 90 90"),
            "force high LOD",
        )

    # Fix high-FPS car smear: Octane scales per-car stencil reprojection blur by
    # (42.0 / dt_ms) / 14.0, which quadruples blur intensity at 120 FPS. Always
    # branch to the 33.0 ms reference divisor so shutter blur scales cleanly.
    _replace_bytes(
        image,
        0x7B1C9D,
        bytes.fromhex("74 0e"),
        bytes.fromhex("eb 0e"),
        "motion blur shutter",
    )
    if not motion_blur:
        # Skip MotionBlurPrepare + per-frame callback + MotionBlur composite.
        _replace_bytes(
            image,
            0x7BE789,
            bytes.fromhex("0f 84 7c 01 00 00"),
            bytes.fromhex("e9 7d 01 00 00 90"),
            "motion blur toggle",
        )

    if not bloom:
        # Zero g_LowWeight and g_HighWeight in Octane's Bloom UpscaleAccum setup
        # so bloomBuffer still clears cleanly to 0.0 each frame without glow.
        for offset, label in (
            (0x7CA4F4, "bloom low weight"),
            (0x7CA52D, "bloom high weight"),
        ):
            _replace_bytes(
                image,
                offset,
                bytes.fromhex("41 8b 4c 24 04"),
                bytes.fromhex("31 c9 90 90 90"),
                label,
            )

    xml_size, _ = required_heap_spec(res_key, shadow)
    if xml_size is not None:
        # Expand Octane's embedded GPU Data direct-memory heap XML entry (11 bytes)
        # so 1440p / ultrawide / 4K render targets and 3072 / 4096 shadows fit cleanly.
        _replace_bytes(image, 0xE3C4AE, b'size="2.2G"', xml_size, "GPU Data heap XML")


def apply_graphics_patches(
    base_path,
    output_path,
    resolution="native",
    shadow_resolution=2048,
    motion_blur=False,
    fov_scale=100,
    skip_intro=False,
    high_lod=False,
    bloom=True,
):
    base_path = Path(base_path)
    output_path = Path(output_path)
    data = base_path.read_bytes()
    image_start, _ = _probe_image_offset(data)
    image = bytearray(data[image_start:])
    patch_graphics(
        image,
        resolution=resolution,
        shadow_resolution=shadow_resolution,
        motion_blur=motion_blur,
        fov_scale=fov_scale,
        skip_intro=skip_intro,
        high_lod=high_lod,
        bloom=bloom,
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = output_path.with_name(f"{output_path.name}.{os.getpid()}.tmp")
    with tmp.open("wb") as handle:
        handle.write(data[:image_start])
        handle.write(image)
    tmp.replace(output_path)
    return output_path


def prepare(game, reference, llvm, out, shad, prebuilt=None):
    from elftools.elf.elffile import ELFFile

    if hashlib.sha256((game / "eboot.bin").read_bytes()).hexdigest() != TARGET_HASH:
        raise ValueError("Unsupported eboot.bin. This profile targets CUSA07027 01.00.")
    out.mkdir(parents=True, exist_ok=True)
    for tool in ("prepare.py", "link_modules.py"):
        command = [sys.executable, reference / "scripts" / tool, game, "--out", out]
        if tool == "link_modules.py":
            command += [
                "--modules",
                "libc.prx",
                "libSceFios2.prx",
                "libSceNpToolkit2.prx",
            ]
        subprocess.run(command, check=True)
    source = Path(__file__).resolve().parent
    original = (out / "boot-linked.bin").read_bytes()
    header = struct.Struct("<8s6Q")
    magic, size, entry, ns, nr, ni, flags = header.unpack_from(original)
    if magic != b"G3PROBE5":
        raise ValueError("Unsupported SpartanPort image version")
    nmods = struct.unpack_from("<Q", original, 96)[0]
    binding_start = 104 + 56 * nmods
    nb = struct.unpack_from("<Q", original, binding_start)[0]
    segment_start = binding_start + 8 + nb * 24
    name_start = segment_start + ns * 24
    relocation_start = name_start + ni * 128
    image_start = relocation_start + nr * 32
    if image_start + size != len(original):
        raise ValueError("Unexpected probe image layout")

    base = (size + 0xFFFF) & ~0xFFFF
    if prebuilt is not None:
        elf = prebuilt
    else:
        from fibers import compile_fibers

        tool_suffix = ".exe" if os.name == "nt" else ""
        clang = llvm / f"clang{tool_suffix}"
        linker = llvm / f"ld.lld{tool_suffix}"
        obj = out / "cars3-runtime.o"
        elf = out / "cars3-runtime.elf"
        subprocess.run(
            [
                clang,
                "-target",
                "x86_64-none-elf",
                "-O2",
                "-ffreestanding",
                "-fPIC",
                "-fno-stack-protector",
                "-c",
                source / "cars3_runtime.c",
                "-o",
                obj,
            ],
            check=True,
        )
        fibers = compile_fibers(shad, source, llvm, out)
        events = out / "cars3-events.o"
        saves = out / "cars3-save.o"
        subprocess.run(
            [
                clang,
                "-target",
                "x86_64-none-elf",
                "-O2",
                "-ffreestanding",
                "-fPIC",
                "-fno-stack-protector",
                "-c",
                source / "save_info.c",
                "-o",
                saves,
            ],
            check=True,
        )
        subprocess.run(
            [
                clang,
                "-target",
                "x86_64-none-elf",
                "-O2",
                "-ffreestanding",
                "-fPIC",
                "-fno-stack-protector",
                "-c",
                source / "event_flags.c",
                "-o",
                events,
            ],
            check=True,
        )
        subprocess.run(
            [
                linker,
                "-T",
                source / "runtime.ld",
                f"--defsym=CARS3_BASE={base}",
                "-e",
                "cars3_module_start",
                obj,
                fibers,
                events,
                saves,
                "-o",
                elf,
            ],
            check=True,
        )
    image = bytearray(original[image_start:])
    set_60fps_presentation(image)
    segments = []
    bindings = []
    relocations = []
    names = [
        original[pos : pos + 128].split(b"\0", 1)[0].decode("ascii")
        for pos in range(name_start, relocation_start, 128)
    ]
    names.extend(name for name in IMPORTS if name not in names)
    bound = {
        struct.unpack_from("<Q", original, pos)[0]
        for pos in range(binding_start + 8, segment_start, 24)
    }
    with elf.open("rb") as f:
        module = ELFFile(f)
        for seg in module.iter_segments("PT_LOAD"):
            addr, length, perms = seg["p_vaddr"], seg["p_memsz"], seg["p_flags"]
            if not length or addr < len(image) or addr + length > 512 * 1024**2:
                raise ValueError("Invalid native runtime segment")
            data = seg.data()
            image.extend(bytes(addr + length - len(image)))
            image[addr : addr + len(data)] = data
            segments.append((addr, length, perms))
        symbols = module.get_section_by_name(".symtab")
        initializer = symbols.get_symbol_by_name("cars3_module_start")[0]["st_value"]
        for nid, name in EXPORTS.items():
            index = names.index(nid)
            address = symbols.get_symbol_by_name(name)[0]["st_value"]
            if index in bound or not any(
                p & 1 and a <= address < a + n for a, n, p in segments
            ):
                raise ValueError(f"Invalid binding for {name}")
            bindings.append((index, address, 1))
        for nid, name in IMPORTS.items():
            slot = symbols.get_symbol_by_name(name)[0]["st_value"]
            if not any(
                p & 2 and a <= slot and slot + 8 <= a + n for a, n, p in segments
            ):
                raise ValueError(f"Invalid runtime import slot {name}")
            relocations.append((slot, 1, names.index(nid), 0))
    path = out / "cars3-native.bin"
    tmp = path.with_suffix(".tmp")
    with tmp.open("wb") as f:
        f.write(
            header.pack(
                magic,
                len(image),
                entry,
                ns + len(segments),
                nr + len(relocations),
                len(names),
                flags,
            )
        )
        f.write(original[header.size : 96])
        f.write(struct.pack("<Q", nmods + 1))
        f.write(original[104:binding_start])
        f.write(struct.pack("<7Q", base, len(image) - base, initializer, 0, 0, 0, 0))
        f.write(struct.pack("<Q", nb + len(bindings)))
        f.write(original[binding_start + 8 : segment_start])
        for row in bindings:
            f.write(struct.pack("<3Q", *row))
        f.write(original[segment_start:name_start])
        for row in segments:
            f.write(struct.pack("<3Q", *row))
        for name in names:
            f.write(ALIASES.get(name, name).encode("ascii").ljust(128, b"\0"))
        f.write(original[relocation_start:image_start])
        for row in relocations:
            f.write(struct.pack("<4Q", *row))
        f.write(image)
    tmp.replace(path)
    print(path)
    return path


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("game", "reference", "llvm", "out", "shad"):
        parser.add_argument(arg, type=Path)
    parser.add_argument(
        "--arm64-low",
        action="store_true",
        help="also prepare a 540p image for the experimental ARM64 runtime",
    )
    args = parser.parse_args()
    image = prepare(
        *(
            path.resolve()
            for path in (args.game, args.reference, args.llvm, args.out, args.shad)
        )
    )
    if args.arm64_low:
        mobile_image = apply_graphics_patches(
            image,
            image.with_name("cars3-arm64-low.bin"),
            resolution="540p",
            shadow_resolution=512,
            skip_intro=True,
            bloom=False,
        )
        print(mobile_image)
