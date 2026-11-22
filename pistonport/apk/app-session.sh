#!/bin/sh
set -eu

runtime="$FXD_ROOT/opt/pistonport"
profile="$FXD_FILES/pistonport"
mkdir -p "$profile"
export LD_LIBRARY_PATH="$runtime/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export LD_PRELOAD="$FXD_ROOT/usr/lib/fexdroid/libfxpath.so"
export SDL_VIDEODRIVER=x11
export PYTHONPATH="$runtime/lib/python"
exec "$FXD_ROOT/usr/bin/python3" -u "$runtime/app_session.py" "$@"
