# PistonPort

[![Stars](https://img.shields.io/github/stars/afkarxyz/PistonPort?color=ffcb47&labelColor=black&logo=github&label=Stars)](https://github.com/afkarxyz/PistonPort/stargazers)
[![Downloads](https://img.shields.io/github/downloads/afkarxyz/PistonPort/total?color=22c55e&labelColor=black&logo=github&label=Downloads)](https://github.com/afkarxyz/PistonPort/releases)
[![Release](https://img.shields.io/github/v/release/afkarxyz/PistonPort?color=8b5cf6&labelColor=black&logo=github&label=Release)](https://github.com/afkarxyz/PistonPort/releases/latest)

An unofficial native Windows x64 port of *Cars 3: Driven to Win* (PS4).

[**Download for Windows (Latest)**](https://github.com/afkarxyz/PistonPort/releases/latest/download/PistonPort-Windows-x64.zip)

> **No game files are included.** You need a dump from your own PS4 (tested: CUSA07027, version 01.00). This project is not affiliated with or endorsed by Disney/Pixar, Warner Bros. Interactive Entertainment, Avalanche Software, or Sony Interactive Entertainment. *Cars 3: Driven to Win* is a trademark of Disney/Pixar.

![PistonPort launcher showing the Garage](docs/images/pistonport/PistonPort-Launcher.png)

## Getting Started

You need a Vulkan-capable graphics driver and a playable PKG or extracted game folder. Extract the entire ZIP, open `PistonPort.exe`, select your game source, and click **Play**.

[Player Guide](pistonport/launcher/QUICKSTART.md) · [Technical Notes & Build](pistonport/README.md) · [Settings Screenshot](docs/images/pistonport/PistonPort-Settings.png) · [Gameplay Screenshot](docs/images/pistonport/PistonPort-Gameplay.jpg) · [Ultrawide Screenshot](docs/images/pistonport/PistonPort-Ultrawide.jpg)

## Adaptação experimental para Android

O primeiro alvo é iniciar o jogo no runtime ARM64 do SpartanPort dentro de uma sessão compatível com o Nixport. O processo usa uma cópia própria de CUSA07027 v01.00, extraída localmente. O suporte ao menu e às corridas ainda precisa ser validado em um aparelho.

No Linux, use o Python com pyelftools e os checkouts de referência descritos nas [instruções de build](pistonport/README.md#building). Indique o diretório que contém `clang`, `clang++` e `ld.lld` ao preparar a imagem.

```sh
python3 pistonport/prepare.py /caminho/jogo /caminho/spartanport /caminho/llvm/bin /caminho/out /caminho/shadps4 --arm64-low
```

A opção gera `cars3-arm64-low.bin` em 960×540, com sombras de 512 px, sem bloom nem motion blur. Com o `spartanport-probe` ARM64 e suas bibliotecas já disponíveis na sessão de teste, o iniciador aplica limite inicial de 30 FPS e grava o log em `native-last.log` dentro do perfil.

```sh
python3 pistonport/run_arm64.py --probe /caminho/spartanport-probe --image /caminho/out/cars3-arm64-low.bin --game /caminho/jogo --profile /caminho/perfil
```

## Credits

PistonPort uses SpartanPort’s loader, shadPS4’s Vulkan renderer, and Rune for the launcher. [SpartanPort](https://github.com/FelipePrado0/SpartanPort) · [shadPS4](https://github.com/shadps4-emu/shadPS4) · [Rune](https://github.com/anomalyco/rune) · [License](LICENSE)
