package org.pistonport.nativeandroid

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.net.URL

internal object FrameGenAssets {
    private const val makoRevision = "18d7d690515a658c993f84029b245cb710cb83f2"
    private const val modelBase = "https://huggingface.co/eugeniosegala/MAKO-FG/resolve/"
    private val files = mapOf(
        "LICENSE" to "e990112837dcd5af880065d292f22f394f87c69912f957eabc54dc0285c6a7fc",
        "model/weights.bin" to "08ed3acfefa71a6d29a208e9a64c73d6593bfc2e83eb0230fe4f8d62ff7ea781",
        "model/transport.spv" to "d2285a08b046fcb40ef63254589df3e1defa43e11e8f1026dd9a10c922db992d",
        "model/ops.spv" to "431ec6d0224111435a62e9e1235d844cc9180083e9e5c79461b47d46a2b950ea",
        "model/mixture.spv" to "f5395a370e03783273c68295c1b11215e8c0c7c978e07299b68d7e843d6c0e8e")

    fun lossless(profile: File) = File(profile, "Lossless.dll")
    private fun makoRoot(profile: File) = File(profile, "mako-$makoRevision")
    fun mako(profile: File) = File(makoRoot(profile), "model")
    fun makoInstalled(profile: File) = files.keys.all { File(makoRoot(profile), it).isFile }

    fun importDll(context: Context, profile: File, uri: Uri) {
        profile.mkdirs()
        val temporary = File.createTempFile("lossless-", ".part", profile)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Falha ao abrir o arquivo.")
            input.buffered().use {
                require(it.read() == 'M'.code && it.read() == 'Z'.code) { "Selecione o arquivo Lossless.dll." }
                temporary.outputStream().use { output ->
                    output.write(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte()))
                    val buffer = ByteArray(65536)
                    var size = 2L
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= 64L * 1024 * 1024) { "Arquivo Lossless.dll grande demais." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.renameTo(lossless(profile))) { "Falha ao guardar a Lossless.dll." }
        } finally {
            temporary.delete()
        }
    }

    fun installMako(profile: File) {
        if (makoInstalled(profile)) return
        profile.mkdirs()
        val staging = java.nio.file.Files.createTempDirectory(profile.toPath(), "mako-").toFile()
        try {
            for ((name, expected) in files) {
                val connection = URL("$modelBase$makoRevision/$name").openConnection().apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                }
                val bytes = connection.getInputStream().use { it.readNBytes(512 * 1024 + 1) }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                require(hash == expected) { "A integridade do modelo MAKO-FG não confere." }
                File(staging, name).apply { parentFile?.mkdirs(); writeBytes(bytes) }
            }
            check(staging.renameTo(makoRoot(profile))) { "Falha ao guardar o modelo MAKO-FG." }
        } finally {
            staging.deleteRecursively()
        }
    }
}
