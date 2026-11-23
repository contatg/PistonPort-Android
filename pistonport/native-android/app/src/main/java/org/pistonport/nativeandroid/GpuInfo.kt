package org.pistonport.nativeandroid

internal data class GpuInfo(val name: String, val version: String, val stockReady: Boolean) {
    // A family match is only a catalog filter. Each driver must pass the native test.
    val family = Regex("""Adreno\D*([678])\d{2}""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.get(1)?.toInt()
    val label: String
        get() = "$name · Vulkan $version\n" + when {
            stockReady -> "O driver do sistema atende aos requisitos do Vulkan."
            family != null -> "O driver do sistema é incompatível. Escolha um Turnip e execute o teste."
            else -> "O driver do sistema é incompatível e este Hub não tem drivers para esta GPU."
        }

    companion object {
        fun detect(): GpuInfo {
            System.loadLibrary("piston_gpu_info")
            val data = readStockVulkan().split('\t', limit = 3)
            return GpuInfo(data[0], data.getOrElse(1) { "indisponível" }, data.getOrNull(2) == "1")
        }

    }
}

private external fun readStockVulkan(): String
