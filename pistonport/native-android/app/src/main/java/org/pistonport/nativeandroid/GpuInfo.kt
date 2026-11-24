package org.pistonport.nativeandroid

import android.opengl.EGL14
import android.opengl.GLES20

internal enum class DriverKind(val label: String, val library: String) {
    TURNIP("Turnip", "libpiston_turnip.so"),
    PANVK("PanVK", "libvulkan_panfrost.so")
}

internal data class GpuInfo(
    val name: String,
    val version: String,
    val stockReady: Boolean,
    val vendor: String = ""
) {
    // Model matching narrows the catalog. The renderer checks the actual driver on startup.
    val adrenoFamily = Regex("""Adreno\D*([678])\d{2}""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.get(1)?.toInt()
    val maliModel = Regex("""(?:Mali|Immortalis)[\s-]+G\s*(\d+)""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.get(1)?.toInt()
    val driverKind = when {
        vendor == "13b5" || name.contains("Mali", true) || name.contains("Immortalis", true) ->
            DriverKind.PANVK
        adrenoFamily != null -> DriverKind.TURNIP
        else -> null
    }
    val label: String
        get() = "$name · Vulkan $version\n" + when {
            stockReady -> "O driver do sistema atende aos requisitos do Vulkan."
            driverKind == DriverKind.TURNIP ->
                "O driver do sistema não atende aos requisitos. Escolha um Turnip pelo Hub."
            driverKind == DriverKind.PANVK ->
                "O driver do sistema não atende aos requisitos. Consulte os pacotes PanVK para sua Mali no Hub."
            else -> "O driver do sistema não atende aos requisitos e o Hub não tem pacotes para esta GPU."
        }

    companion object {
        fun detect(): GpuInfo {
            val data = runCatching {
                System.loadLibrary("piston_gpu_info")
                readStockVulkan().split('\t')
            }.getOrDefault(emptyList())
            val info = GpuInfo(data.getOrElse(0) { "GPU não detectada" },
                data.getOrElse(1) { "indisponível" }, data.getOrNull(2) == "1",
                data.getOrElse(3) { "" })
            if (info.driverKind != null && (info.driverKind != DriverKind.PANVK || info.maliModel != null))
                return info
            // Some stock Vulkan loaders expose no device. Read the real GLES renderer in that case.
            val renderer = readGlRenderer() ?: return info
            return info.copy(name = renderer)
        }

        private fun readGlRenderer(): String? {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, null, 0, null, 0))
                return null
            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE
            try {
                val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
                val count = IntArray(1)
                val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
                if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) ||
                    count[0] == 0) return null
                context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
                if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE ||
                    !EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                return GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() }
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()
            }
        }
    }
}

private external fun readStockVulkan(): String
