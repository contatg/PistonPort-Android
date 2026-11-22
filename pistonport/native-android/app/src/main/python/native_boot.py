"""Build the Carros 3 boot image on Android from the original game directory."""

from pathlib import Path

import prepare
from app_session import image_for
from spartanprep import link_modules
from spartanprep import prepare as spartan_prepare


def link_boot(game, output):
    spartan_prepare.prepare(game, output)
    link_modules.link(game, output, prepare.LINK_MODULES)


def build(game, profile, resources):
    return str(image_for(Path(game), Path(profile), Path(resources), link_boot))
