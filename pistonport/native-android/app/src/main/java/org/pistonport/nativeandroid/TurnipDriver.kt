package org.pistonport.nativeandroid

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import org.json.JSONObject

internal object TurnipDriver {
    private const val LIBRARY = "libpiston_turnip.so"
    private const val LIMIT = 128L * 1024 * 1024

    data class Installed(val id: String, val name: String)

    fun directory(context: Context, id: String): File {
        require(id == "local" || Regex("[a-z0-9_-]{1,70}").matches(id))
        return if (id == "local") File(context.filesDir, "turnip")
        else File(File(context.filesDir, "drivers"), id)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("vulkan", Context.MODE_PRIVATE)

    fun selected(context: Context): String? {
        val prefs = preferences(context)
        val fallback = if (prefs.getBoolean("turnip", false)) "local" else ""
        val id = prefs.getString("driver_id", fallback).orEmpty()
        return id.takeIf { it.isNotEmpty() && File(directory(context, it), LIBRARY).isFile }
    }

    fun list(context: Context): List<Installed> = buildList {
        if (File(directory(context, "local"), LIBRARY).isFile) add(Installed("local", "Turnip importado"))
        File(context.filesDir, "drivers").listFiles().orEmpty().filter { it.isDirectory }
            .filter { Regex("[a-z0-9_-]{1,70}").matches(it.name) && File(it, LIBRARY).isFile }
            .sortedByDescending { it.lastModified() }.forEach {
                add(Installed(it.name, runCatching { File(it, "name.txt").readText() }.getOrDefault(it.name)))
            }
    }

    fun select(context: Context, id: String?) {
        preferences(context).edit().putString("driver_id", id.orEmpty()).apply()
    }

    fun install(context: Context, uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o arquivo do driver.")
        install(context, input, "local", "Turnip importado")
    }

    @Synchronized
    fun install(context: Context, input: InputStream, id: String, name: String) {
        val folder = directory(context, id).apply { mkdirs() }
        val destination = AtomicFile(File(folder, LIBRARY))
        input.buffered().use { source ->
            source.mark(4)
            val magic = ByteArray(4)
            DataInputStream(source).readFully(magic)
            source.reset()
            if (magic.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) {
                val archive = File.createTempFile("turnip-import-", ".zip", context.cacheDir)
                try {
                    archive.outputStream().use { copyLimited(source, it) }
                    ZipFile(archive).use { zip ->
                        val library = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                        val metadata = library.singleOrNull { it.name.substringAfterLast('/') == "meta.json" }
                        val declared = metadata?.let {
                            require(it.size in 1..64 * 1024L) { "O meta.json do driver é inválido." }
                            zip.getInputStream(it).bufferedReader().use { reader ->
                                val meta = JSONObject(reader.readText())
                                require(meta.optInt("minApi", 0) <= android.os.Build.VERSION.SDK_INT) {
                                    "O driver exige uma versão mais recente do Android."
                                }
                                meta.optString("libraryName").ifBlank { null }
                            }
                        }
                        val recognized = setOf("libvulkan_freedreno.so", "vulkan.freedreno.so",
                            "vulkan.turnip.so", "libvulkan_turnip.so", "vulkan.adreno.so")
                        val entry = library.singleOrNull {
                            val name = it.name.substringAfterLast('/')
                            if (declared != null) name == declared.substringAfterLast('/') else name in recognized
                        } ?: error("O ZIP precisa conter uma biblioteca Vulkan do Turnip.")
                        require(entry.size in 20..LIMIT) { "A biblioteca do Turnip tem tamanho inválido." }
                        zip.getInputStream(entry).use { installElf(it, destination) }
                    }
                } finally {
                    archive.delete()
                }
            } else {
                installElf(source, destination)
            }
        }
        if (id != "local") File(folder, "name.txt").writeText(name)
        select(context, id)
        GameActivity.vulkanLog(context, id).delete()
    }

    private fun installElf(input: InputStream, destination: AtomicFile) {
        val header = ByteArray(20)
        DataInputStream(input).readFully(header)
        require(header.copyOfRange(0, 7).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 2, 1, 1)) &&
            header[16] == 3.toByte() && header[17] == 0.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()) {
            "Selecione um Turnip ARM64 para Android em formato ZIP ou .so."
        }
        val output = destination.startWrite()
        try {
            output.write(header)
            copyLimited(input, output, LIMIT - header.size)
            destination.finishWrite(output)
        } catch (failure: Exception) {
            destination.failWrite(output)
            throw failure
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long = LIMIT) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return
            total += read
            require(total <= limit) { "O arquivo do driver excede 128 MiB." }
            output.write(buffer, 0, read)
        }
    }
}
