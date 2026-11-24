# Créditos do PistonPort para Android

**Manutenção e adaptação Android:** [contatg](https://github.com/contatg). **Projeto original:** [afkarxyz/PistonPort](https://github.com/afkarxyz/PistonPort). Créditos à equipe SpartanPort (FelipePrado0), shadPS4, zFitness, niilo, FEX-Emu, cobrabm12, bylaws, SDL, AMD GPUOpen, Khronos, Chaquopy e demais colaboradores. Veja o [inventário de licenças e dependências](../../licenses/README.md) no repositório. A licença GPL v2 original e os termos de cada componente são preservados.

- [Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android), de ducvd89 e colaboradores, revisão `1600cc0`. O controle PlayStation multitouch e seu editor foram adaptados para o Carros 3. A integração de AHardwareBuffer do Piston Frame veio do renderizador Android desse projeto. As opções de gráficos e o medidor de FPS e tempo de quadro seguem sua organização e foram ligados ao runtime do PistonPort. Consulte a [licença do Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android/blob/1600cc0/LICENSE).
- O Piston Frame usa [Bloodframe 2.0](https://github.com/ducvd89/bloodframe/tree/f4ac713aef58d9403a136e179b3b0d163d85eb66), de ducvd89 e colaboradores. Baseado no lsfg-vk 1.0 de PancakeTAS e no fork Android do GameNative por Ragnarok93, sob licença MIT. As licenças das bibliotecas vinculadas acompanham o APK.
- [MAKO-FG](https://huggingface.co/eugeniosegala/MAKO-FG), de Eugenio Segala. Os arquivos do modelo têm licença CC BY-NC 4.0 e condições de pesquisa e educação não comerciais provenientes dos dados de treinamento. O download opcional instala os arquivos originais e a licença.
- [Snapdragon GSR 2](https://github.com/SnapdragonStudios/snapdragon-gsr), de Qualcomm Innovation Center. Shaders BSD-3-Clause adaptados pelo Bloodborne-Android. [Licença](licenses/Snapdragon-GSR2.txt).
- [Fractal](https://github.com/dfdx047/Fractal), de dfdx047 e colaboradores. Referência para a leitura dos sensores KGSL do medidor de uso da Adreno.
- Fontes de drivers opcionais no Hub [StevenMXZ](https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases), [Mesa Purple](https://github.com/MrPurple666/purple-turnip/releases), [AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers/releases) e [Banners Turnip](https://github.com/The412Banner/Banners-Turnip/releases).
- Fontes de pacotes PanVK no Hub Mali [Zenithblue](https://github.com/zenithblue-oss/panvk-kbase-android/releases), [JimVulkan](https://github.com/JimVulkan/mali-panvk/releases), [MineGraph](https://github.com/MineGraph-bilibili/panvk-Android/releases), [soulexit](https://github.com/soulexit/panvk-bifrost-soulexit/releases) e [FristOneRR](https://github.com/FristOneRR/FristOneRR-Panvk-Driver/releases). Cada pacote é baixado do projeto que o publica.

## Projeto e componentes de base

- [contatg](https://github.com/contatg) — manutenção e adaptação Android do PistonPort.
- [afkarxyz/PistonPort](https://github.com/afkarxyz/PistonPort) — port original e runtime do jogo.
- [SpartanPort](https://github.com/FelipePrado0/SpartanPort) — loader e infraestrutura de execução; [zFitness/gow3_android](https://github.com/zFitness/gow3_android) e [niilo/gow3_android](https://github.com/niilo/gow3_android) — referências e dependências ARM64/Bionic.
- [FEX](https://github.com/FEX-Emu/FEX) — tradução de código x86-64 para ARM64.
- [shadPS4](https://github.com/shadps4-emu/shadPS4) — renderizador Vulkan, recompilador de shaders e componentes PS4; consulte gpu/VENDOR.txt e gpu/shadps4/LICENSE.
- [FexDroid](https://github.com/cobrabm12/fexdroid) — base do pacote Android alternativo.
- [SDL](https://github.com/libsdl-org/SDL) — SDL3, integração de janela, entrada e plataforma.
- [libadrenotools](https://github.com/bylaws/libadrenotools) — carregamento de drivers Adreno.
- [Mesa](https://mesa3d.org/) — Turnip e PanVK; os distribuidores dos pacotes opcionais estão listados acima.
- [Vulkan Memory Allocator](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator) — gerenciamento de memória Vulkan.
- [FSR-Vulkan](https://github.com/FireBurn/FSR-Vulkan) e [AMD GPUOpen](https://gpuopen.com/fidelityfx-superresolution/) — componentes e tecnologia de upscaling.
- [Sirit](https://github.com/ReinUsesLisp/sirit), [SPIRV-Headers](https://github.com/KhronosGroup/SPIRV-Headers), half (Christian Rau), GCN e SMAA (Jorge Jimenez e colaboradores) — componentes de shaders e renderização; consulte as licenças em gpu/.
- [Dear ImGui](https://github.com/ocornut/imgui), [magic_enum](https://github.com/Neargye/magic_enum), [miniz](https://github.com/richgel999/miniz), [Xbyak](https://github.com/herumi/xbyak), [LibAtrac9](https://github.com/Thealexbarney/LibAtrac9) — dependências declaradas no repositório.
- [Chaquopy](https://chaquo.com/chaquopy/), [Python](https://www.python.org/), [pyelftools](https://github.com/eliben/pyelftools), [LLVM](https://llvm.org/), Kotlin e Android SDK/NDK — integração e ferramentas de execução e build.
- [Rune](https://github.com/anomalyco/rune), [Lucide](https://lucide.dev/), [Kenney Input Prompts](https://kenney.nl/assets/input-prompts) (CC0), Rust e Pillow — componentes e ferramentas do launcher original.

Cars 3: Driven to Win pertence aos respectivos titulares, incluindo Disney/Pixar, Warner Bros. Interactive Entertainment e Avalanche Software. PlayStation é marca da Sony Interactive Entertainment. Projeto independente, não oficial, sem arquivos do jogo incluídos.

As atribuições acima distinguem componentes do APK, dependências do repositório, ferramentas de build e referências. Não substituem os textos de licença originais nem implicam endosso dos autores.
