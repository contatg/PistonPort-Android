# Licenças e avisos de terceiros / Third-party notices

O PistonPort-Android é mantido por [contatg](https://github.com/contatg), a partir do [PistonPort original de afkarxyz](https://github.com/afkarxyz/PistonPort). Os créditos não transferem autoria nem alteram as licenças originais.

A licença principal é a [GNU GPL v2](../LICENSE). O arquivo [pistonport/LICENSE.txt](../pistonport/LICENSE.txt) também permanece intacto. Cada componente mantém seus próprios avisos de copyright, licença e condições; consulte o texto original na revisão efetivamente utilizada. GPL-2.0 nas tabelas indica a identificação geral do repositório, não uma conclusão sobre a cláusula “or later” de cada arquivo.

## Base e runtime

| Projeto / autores | Uso | Licença ou referência |
| --- | --- | --- |
| [PistonPort — afkarxyz e colaboradores](https://github.com/afkarxyz/PistonPort) | Port original, preparador e launcher Windows | GPL v2 |
| [SpartanPort — FelipePrado0 e colaboradores](https://github.com/FelipePrado0/SpartanPort) | Loader e runtime | GPL v2 |
| [gow3_android — zFitness](https://github.com/zFitness/gow3_android/tree/56e804d1ef3e108257b94c51b6f7fb204930a244) | Backend ARM64 e adaptação Android | GPL v2 |
| [gow3_android — niilo](https://github.com/niilo/gow3_android/tree/046791de0fd4639b0b479afe8fc7ec67e538925d) | Dependências Android/Bionic | GPL v2 |
| [shadPS4 — equipe e colaboradores](https://github.com/shadps4-emu/shadPS4) | Renderizador Vulkan e emulação | [Licença preservada](../gpu/shadps4/LICENSE) |
| [FEX-Emu — equipe FEX](https://github.com/FEX-Emu/FEX) | Tradução x86-64 para ARM64 | MIT; componentes internos podem ter outros termos |
| [FEXDroid — cobrabm12](https://github.com/cobrabm12/fexdroid/tree/410bba28cf2f55e08fd9588266524106387ea67c) | Host do APK alternativo | MIT |
| Nixport | Alvo experimental de empacotamento; ver README principal | Ver distribuição correspondente |
| [Bloodborne-Android — ducvd89 e colaboradores](https://github.com/ducvd89/Bloodborne-Android/tree/1600cc0) | Interface, controles, editor e integração gráfica adaptados | GPL v2 |
| [Bloodframe — ducvd89](https://github.com/ducvd89/bloodframe/tree/f4ac713aef58d9403a136e179b3b0d163d85eb66) | Base do Piston Frame | MIT |
| [lsfg-vk — PancakeTAS](https://github.com/PancakeTAS/lsfg-vk) e Ragnarok93/GameNative | Origem do Bloodframe e adaptação Android | Ver avisos da revisão do Bloodframe |
| [Fractal — dfdx047](https://github.com/dfdx047/Fractal) | Referência do medidor KGSL | Ver licença upstream |
| [libadrenotools — bylaws e colaboradores](https://github.com/bylaws/libadrenotools/tree/8fae8ce254dfc1344527e05301e43f37dea2df80) | Carregamento de drivers Adreno | BSD-2-Clause; subcomponentes mantêm termos próprios |
| [SDL3 — SDL contributors](https://github.com/libsdl-org/SDL/tree/release-3.2.10) | Interface nativa, vídeo e entrada | zlib |
| [Vulkan Memory Allocator — AMD](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/tree/v3.3.0) | Alocação de memória Vulkan | MIT |
| [Vulkan / SPIR-V — Khronos Group](https://github.com/KhronosGroup/Vulkan-Headers) | APIs e cabeçalhos gráficos | Ver avisos upstream e [SPIRV-Headers](../gpu/third_party/sirit/externals/SPIRV-Headers/LICENSE) |
| [FSR-Vulkan — FireBurn](https://github.com/FireBurn/FSR-Vulkan) / [AMD GPUOpen FidelityFX](https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK) | Upscaling e tecnologias FSR; patches locais | Ver licenças no submódulo e SDK |
| [Snapdragon GSR 2 — Qualcomm Innovation Center](https://github.com/SnapdragonStudios/snapdragon-gsr) | Shaders de upscaling | [BSD-3-Clause](../pistonport/native-android/licenses/Snapdragon-GSR2.txt) |
| [MAKO-FG — Eugenio Segala](https://huggingface.co/eugeniosegala/MAKO-FG) | Modelo opcional baixado pelo usuário | CC BY-NC 4.0 e condições de pesquisa/educação indicadas pelo upstream |
| Lossless Scaling / Lossless.dll | Arquivo externo importado pelo usuário | Proprietário; não autorizado à redistribuição por este projeto |

## Bibliotecas, ferramentas e recursos

| Componente | Aviso / referência |
| --- | --- |
| [Dear ImGui — Omar Cornut](https://github.com/ocornut/imgui) | MIT |
| [LibAtrac9 — Thealexbarney](https://github.com/Thealexbarney/LibAtrac9) | MIT |
| [magic_enum — Neargye](https://github.com/Neargye/magic_enum) | MIT |
| [miniz — Rich Geldreich e colaboradores](https://github.com/richgel999/miniz) | MIT |
| [Xbyak — herumi](https://github.com/herumi/xbyak) | BSD-3-Clause |
| half — Christian Rau | [Licença original](../gpu/third_party/half/LICENSE.txt) |
| sirit | [Licença original](../gpu/third_party/sirit/LICENSE.txt) |
| SMAA | [Licença original](../gpu/shadps4/video_core/host_shaders/smaa/LICENSE.txt) |
| Intel XeSS | Avisos nos cabeçalhos [xess.h](../gpu/third_party/xess/xess.h) e [xess_vk.h](../gpu/third_party/xess/xess_vk.h); não presume redistribuição do SDK |
| [fmt](https://github.com/fmtlib/fmt), [Boost](https://www.boost.org/), [robin-map](https://github.com/Tessil/robin-map), [Zydis](https://github.com/zyantific/zydis), [FFmpeg](https://ffmpeg.org/) | Dependências CMake e/ou testes; conferir licença e configuração exatas |
| [Rune — anomalyco](https://github.com/anomalyco/rune/tree/f05046ac10fc124f9314c3ee6dc2434e2e6e2752) | Launcher Windows; MIT, dependências Rust com avisos próprios |
| [Lucide](https://lucide.dev/) | [Licença original](../pistonport/launcher/lucide/LICENSE) |
| [Kenney Input Prompts](https://kenney.nl/assets/input-prompts) | [CC0 1.0](../launcher-rust/assets/pads/LICENSE.txt) |
| DejaVu Sans | [Licença original](../gpu/third_party/fonts/LICENSE-DejaVu.txt) |
| [Chaquopy](https://chaquo.com/chaquopy/), [Python](https://www.python.org/), [pyelftools](https://github.com/eliben/pyelftools), [Pillow](https://github.com/python-pillow/Pillow) | Preparação de imagens e runtime Python; termos das versões usadas |
| [Android SDK/NDK](https://developer.android.com/ndk), [LLVM/libc++](https://llvm.org/), [Kotlin](https://kotlinlang.org/), [Gradle](https://gradle.org/), [Rust](https://www.rust-lang.org/) | Ferramentas de build e bibliotecas de runtime; termos próprios |
| orbis-pkg/orbis-pfs, pkgextract | Ferramentas do fluxo Windows; consultar instruções de build e licenças da origem utilizada |

## Dependências adicionais identificadas nos manifestos

| Componente / autores | Uso e referência de licença |
| --- | --- |
| [xxHash — Yann Collet e colaboradores](https://github.com/Cyan4973/xxHash) | Hashing no backend GPU; BSD-2-Clause para a biblioteca, conferir revisão |
| [X.Org / libX11](https://gitlab.freedesktop.org/xorg/lib/libx11) | Backend não Windows conforme CMake; avisos em COPYING upstream |
| [glslang — Khronos Group e colaboradores](https://github.com/KhronosGroup/glslang) | Compilação de shaders; termos no LICENSE upstream |
| [CMake — Kitware e colaboradores](https://cmake.org/) / [pkg-config](https://www.freedesktop.org/wiki/Software/pkg-config/) | Ferramentas de configuração de build; termos próprios |
| [fs2](https://crates.io/crates/fs2), [image](https://crates.io/crates/image), [rfd](https://crates.io/crates/rfd), [tempfile](https://crates.io/crates/tempfile) | Dependências diretas do launcher Rust original; licenças nas versões resolvidas |
| [windows-sys](https://crates.io/crates/windows-sys), [winresource](https://crates.io/crates/winresource) | Integração e build Windows; não presumir inclusão no APK |
| [Rune Kit](https://github.com/anomalyco/rune), [wgpu-hal](https://crates.io/crates/wgpu-hal), [winit](https://crates.io/crates/winit), [sctk-adwaita](https://crates.io/crates/sctk-adwaita), [parley_engine](https://github.com/anomalyco/rune), [AccessKit](https://github.com/AccessKit/accesskit) | Dependências e patches do launcher Rune na revisão indicada acima; preservar avisos de cada projeto |
| [aes](https://crates.io/crates/aes), [cbc](https://crates.io/crates/cbc), [anyhow](https://crates.io/crates/anyhow), [memmap2](https://crates.io/crates/memmap2), [roxmltree](https://crates.io/crates/roxmltree), [self_cell](https://crates.io/crates/self_cell) | Dependências diretas do leitor PKG Rust; consultar licenças das versões resolvidas |
| [orbis-pkg](https://crates.io/crates/orbis-pkg), [orbis-pfs](https://crates.io/crates/orbis-pfs) | Leitor PKG original, versão 0.1.0; consultar avisos dos pacotes |

Fontes deste inventário: [CMake GPU](../gpu/CMakeLists.txt), [manifesto do launcher](../launcher-rust/pistonport/Cargo.toml), [manifesto PKG](../pistonport/pkg/Cargo.toml), [submódulos](../.gitmodules) e [Gradle Android](../pistonport/native-android/app/build.gradle.kts). Os manifestos Rust declaram GPL-2.0-or-later para os próprios pacotes. Isso não altera as licenças de terceiros.

Antes de cada release, conferir dependências transitivas resolvidas, avisos dos binários efetivamente empacotados, licenças dos submódulos e disponibilidade do código-fonte correspondente aos destinatários. Um link upstream não substitui a inclusão do texto de licença quando exigida.

## Drivers opcionais

O Hub consulta distribuições de Mesa Turnip e PanVK. Créditos à [comunidade Mesa](https://mesa3d.org/) e aos mantenedores dos pacotes: [StevenMXZ](https://github.com/StevenMXZ/Adreno-Tools-Drivers), [MrPurple666](https://github.com/MrPurple666/purple-turnip), [K11MCH1](https://github.com/K11MCH1/AdrenoToolsDrivers), [The412Banner](https://github.com/The412Banner/Banners-Turnip), [zenithblue-oss](https://github.com/zenithblue-oss/panvk-kbase-android), [JimVulkan](https://github.com/JimVulkan/mali-panvk), [MineGraph-bilibili](https://github.com/MineGraph-bilibili/panvk-Android), [soulexit](https://github.com/soulexit/panvk-bifrost-soulexit) e [FristOneRR](https://github.com/FristOneRR/FristOneRR-Panvk-Driver). Os pacotes são baixados separadamente e sujeitos aos termos e avisos de cada publicação.

## Textos de licença preservados

- [PistonPort](upstream/PistonPort.txt) — [origem](https://github.com/afkarxyz/PistonPort/blob/main/LICENSE)
- [SpartanPort](upstream/SpartanPort.txt) — [origem](https://github.com/FelipePrado0/SpartanPort/blob/6c97aa8bf38a049558879a6f1ca9ba4e01b9b80c/LICENSE)
- [gow3-zFitness](upstream/gow3-zFitness.txt) — [origem](https://github.com/zFitness/gow3_android/blob/56e804d1ef3e108257b94c51b6f7fb204930a244/LICENSE)
- [gow3-niilo](upstream/gow3-niilo.txt) — [origem](https://github.com/niilo/gow3_android/blob/046791de0fd4639b0b479afe8fc7ec67e538925d/LICENSE)
- [Bloodborne-Android](upstream/Bloodborne-Android.txt) — [origem](https://github.com/ducvd89/Bloodborne-Android/blob/1600cc0/LICENSE)

- [libadrenotools](upstream/libadrenotools.txt) — [origem](https://github.com/bylaws/libadrenotools/blob/8fae8ce254dfc1344527e05301e43f37dea2df80/LICENSE)
- [SDL3](upstream/SDL3.txt) — [origem](https://github.com/libsdl-org/SDL/blob/release-3.2.10/LICENSE.txt)
- [VulkanMemoryAllocator](upstream/VulkanMemoryAllocator.txt) — [origem](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/blob/v3.3.0/LICENSE.txt)
- [Rune](upstream/Rune.txt) — [origem](https://github.com/anomalyco/rune/blob/f05046ac10fc124f9314c3ee6dc2434e2e6e2752/LICENSE)
- [FEXDroid](upstream/FEXDroid.txt) — [origem](https://github.com/cobrabm12/fexdroid/blob/410bba28cf2f55e08fd9588266524106387ea67c/LICENSE)

- [Bloodframe](upstream/Bloodframe.txt) — [origem](https://github.com/ducvd89/bloodframe/blob/f4ac713aef58d9403a136e179b3b0d163d85eb66/LICENSE.md)
- [Bloodframe-framegen](upstream/Bloodframe-framegen.txt) — [origem](https://github.com/ducvd89/bloodframe/blob/f4ac713aef58d9403a136e179b3b0d163d85eb66/framegen/LICENSE.md)
- [Bloodframe-dxbc](upstream/Bloodframe-dxbc.txt) — [origem](https://github.com/ducvd89/bloodframe/blob/f4ac713aef58d9403a136e179b3b0d163d85eb66/thirdparty/dxbc/LICENSE.md)
- [Dear-ImGui](upstream/Dear-ImGui.txt) — [origem](https://github.com/ocornut/imgui/blob/f1cc2ae15e53a861a874c3034aae6798fde194ab/LICENSE.txt)
- [magic_enum](upstream/magic_enum.txt) — [origem](https://github.com/Neargye/magic_enum/blob/e046b69a3736d314fad813e159b1c192eaef92cd/LICENSE)
- [miniz](upstream/miniz.txt) — [origem](https://github.com/richgel999/miniz/blob/2948eebafc5b9f1c69bebed80333e403a09bd62d/LICENSE)
- [Xbyak](upstream/Xbyak.txt) — [origem](https://github.com/herumi/xbyak/blob/cd6bad3cad2e07a3e277c23d2b1f065320024717/COPYRIGHT)
- [LibAtrac9](upstream/LibAtrac9.txt) — [origem](https://github.com/Thealexbarney/LibAtrac9/blob/efca2e3af35562a09a9bb6deed90e45b4b824dc4/LICENSE)
- [FEX](upstream/FEX.txt) — [origem](https://github.com/FEX-Emu/FEX/blob/fb925d6fc5cc8576bf0f9ae6978fc6d912485593/LICENSE)

Os textos já existentes na árvore permanecem nos caminhos indicados nas tabelas. As licenças de dependências transitivas, de bibliotecas baixadas durante o build e dos binários externos devem ser verificadas na revisão e na configuração efetivamente distribuídas; este inventário não constitui certificação de conformidade. Antes de distribuir APKs derivados de GPL, disponibilize o código-fonte correspondente completo e os scripts de build nos termos aplicáveis, inclusive para componentes derivados. A distribuição do APK não autoriza distribuir arquivos do jogo ou modelos proprietários.

*Cars 3: Driven to Win* e seus recursos pertencem aos respectivos titulares, incluindo Disney/Pixar, Warner Bros., Avalanche Software e Sony Interactive Entertainment conforme aplicável. Projeto não oficial, sem afiliação. O usuário deve fornecer a própria cópia do jogo.
