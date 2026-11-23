package org.pistonport.nativeandroid

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
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
                        val entry = findLibrary(zip)
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
    }

    private fun findLibrary(zip: ZipFile): ZipEntry {
        val files = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
        val metadata = files.firstOrNull { it.name == "meta.json" }
            ?: files.singleOrNull { it.name.substringAfterLast('/') == "meta.json" }
        val declared = metadata?.let {
            require(it.size in 1..64 * 1024L) { "O meta.json do driver é inválido." }
            zip.getInputStream(it).bufferedReader().use { reader ->
                val meta = JSONObject(reader.readText())
                require(meta.optInt("minApi", 0) <= android.os.Build.VERSION.SDK_INT) {
                    "O driver exige uma versão mais recente do Android."
                }
                meta.optString("libraryName").trim().takeIf { name -> name.isNotEmpty() }
            }
        }
        val candidates = files.filter { file ->
            val name = file.name.substringAfterLast('/')
            if (declared != null) name == declared.substringAfterLast('/')
            else name.endsWith(".so") && (name.startsWith("libvulkan") || name.startsWith("vulkan."))
        }.filter { it.size in 20..LIMIT && zip.getInputStream(it).use { stream ->
            isArm64Elf(stream.readNBytes(20))
        } }
        if (declared != null) {
            val relativePath = metadata?.name.orEmpty().substringBeforeLast('/', "") + "/" + declared
            candidates.firstOrNull { it.name == relativePath.trimStart('/') || it.name == declared }?.let {
                return it
            }
        }
        return candidates.singleOrNull() ?: error(
            if (candidates.isEmpty()) "O pacote não contém uma biblioteca Vulkan ARM64 compatível."
            else "O pacote contém mais de uma biblioteca Vulkan ARM64 sem uma seleção clara."
        )
    }

    private fun isArm64Elf(header: ByteArray): Boolean =
        header.size == 20 &&
            header.copyOfRange(0, 7).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 2, 1, 1)) &&
            header[16] == 3.toByte() && header[17] == 0.toByte() &&
            header[18] == 0xb7.toByte() && header[19] == 0.toByte()

    private fun installElf(input: InputStream, destination: AtomicFile) {
        val header = input.readNBytes(20)
        require(isArm64Elf(header)) {
            "Selecione um Turnip ARM64 para Android em formato ZIP, ADPKG ou .so."
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
