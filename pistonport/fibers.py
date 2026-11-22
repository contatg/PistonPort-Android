"""Compile upstream's fiber logic against SpartanPort's per-thread keys."""

import os
import re
import subprocess


def compile_fibers(shad, source, llvm, out):
    directory = shad / "src/core/libraries/fiber"

    def read(name):
        contents = (directory / name).read_text(encoding="utf-8")
        return re.sub(r"^\s*#include[^\n]*|^#pragma once", "", contents, flags=re.M)

    header = read("fiber.h").replace("std::atomic", "GuestAtomic")
    body = read("fiber.cpp").split("void RegisterLib(")[0] + "}\n"
    body = body.replace("std::atomic", "GuestAtomic")
    body = body.replace(
        "return Core::GetTcbBase()->tcb_fiber;",
        "return static_cast<OrbisFiberContext*>(cars3_key_get(cars3_fiber_key));",
    )
    body = body.replace("Core::Tcb* tcb = Core::GetTcbBase();", "")
    body = body.replace("if (tcb->tcb_fiber)", "if (GetFiberContext())")
    body = re.sub(
        r"tcb->tcb_fiber = ([^;]+);",
        r"if (cars3_key_set(cars3_fiber_key, \1)) __builtin_trap();",
        body,
    )
    body = body.replace(
        "s32 PS4_SYSV_ABI _sceFiberSetJmp(",
        "__attribute__((returns_twice)) s32 PS4_SYSV_ABI _sceFiberSetJmp(",
    )
    # Export stable C names while retaining the original public types and contracts.
    header = header.replace("s32 PS4_SYSV_ABI sce", 'extern "C" s32 PS4_SYSV_ABI sce')
    unit = out / "cars3_fibers.cpp"
    unit.write_text(
        '#include "fiber_host.hpp"\n'
        + header
        + read("fiber_error.h")
        + read("fiber_context.cpp")
        + body,
        encoding="utf-8",
    )
    obj = out / "cars3-fibers.o"
    tool_suffix = ".exe" if os.name == "nt" else ""
    clang = llvm / f"clang++{tool_suffix}"
    subprocess.run(
        [
            clang,
            "-target",
            "x86_64-none-elf",
            "-std=c++20",
            "-O2",
            "-ffreestanding",
            "-fPIC",
            "-fvisibility=hidden",
            "-mno-red-zone",
            "-fno-exceptions",
            "-fno-rtti",
            "-fno-stack-protector",
            "-I",
            source,
            "-c",
            unit,
            "-o",
            obj,
        ],
        check=True,
    )
    return obj
