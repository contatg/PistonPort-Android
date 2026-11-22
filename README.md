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

O primeiro alvo é iniciar o jogo no runtime ARM64 do SpartanPort dentro do Nixport. Use a **pasta extraída** da sua cópia PS4 **CUSA07027 v01.00**, com `eboot.bin` na raiz. O APK do Nixport é o aplicativo que importa o port, o ZIP do port contém o runtime e a pasta do jogo fica no celular. Se a sua cópia estiver em um PKG de PS4, extraia-a antes. O preparador verifica a revisão pelo SHA-256 do executável.

```sh
python3 pistonport/run_arm64.py --check-source --game /caminho/jogo
```

No Linux, use o Python com pyelftools e os checkouts de referência descritos nas [instruções de build](pistonport/README.md#building). Indique o diretório que contém `clang`, `clang++` e `ld.lld` ao preparar a imagem.

```sh
python3 pistonport/prepare.py /caminho/jogo /caminho/spartanport /caminho/llvm/bin /caminho/out /caminho/shadps4 --arm64-low
```

A opção gera `cars3-arm64-low.bin` em 960×540, com sombras de 512 px, sem bloom nem motion blur. Copie o arquivo gerado para o aparelho junto com a pasta original do jogo. Com o `spartanport-probe` ARM64 e suas bibliotecas já disponíveis na sessão de teste, o iniciador aplica limite inicial de 30 FPS e grava o log em `native-last.log` dentro do perfil. As opções `--check-vulkan` e `--check-imports` geram logs separados para verificar o driver ativo e a resolução de imports.

```sh
python3 pistonport/run_arm64.py --probe /caminho/spartanport-probe --image /caminho/out/cars3-arm64-low.bin --game /caminho/jogo --profile /caminho/perfil
```

### Build do pacote Nixport

Use o [SpartanPort ARM64](https://github.com/zFitness/gow3_android/tree/56e804d1ef3e108257b94c51b6f7fb204930a244), inicialize os submódulos e instale as dependências descritas no repositório dele. O patch usa `armv8-a` no FEXCore quando solicitado para testar CPUs como o Snapdragon 685.

```sh
git -C /caminho/spartanport-arm apply "$PWD/pistonport/android/spartanport-armv8.patch"
SPARTAN_FEX_TUNE_ARCH=armv8-a SPARTAN_APPDIR_ONLY=1 bash /caminho/spartanport-arm/packaging/arm-linux/build.sh
python3 pistonport/android/package_nixport.py --runtime /caminho/spartanport-arm/out/arm64-linux/pkg/gport --out /caminho/pacote --appimage-builder /caminho/build-port-appimage.sh
```

O empacotador produz `PistonPort-Android-nixport.zip` quando recebe o construtor de AppImage compatível com Nixport. Esse ZIP é o arquivo para importar no Nixport. Use **Testar Vulkan** para verificar o Turnip ativo e **Jogar** para selecionar a pasta e a imagem no primeiro uso. A integração, os controles por toque e o desempenho ainda precisam ser testados num aparelho.

## Credits

PistonPort uses SpartanPort’s loader, shadPS4’s Vulkan renderer, and Rune for the launcher. [SpartanPort](https://github.com/FelipePrado0/SpartanPort) · [shadPS4](https://github.com/shadps4-emu/shadPS4) · [Rune](https://github.com/anomalyco/rune) · [License](LICENSE)
