package ro.cobrabm.fexdroid

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal object PistonSource {
    private const val EBOOT_SHA256 = "56d27cc83b7fe34921fb07413dd7c8fb255daf357a3f81c2d882969f001ff654"
    private const val STORAGE_PROVIDER = "com.android.externalstorage.documents"
    private val volumeId = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
    private fun selected(files: File) = AtomicFile(File(files, "piston-source.txt"))

    fun current(files: File): File? = runCatching {
        val folder = File(selected(files).readFully().toString(Charsets.UTF_8))
        val oldImport = generateSequence(folder) { it.parentFile }
            .any { it.name == "pistonport-imports" }
        folder.takeIf { !oldImport && File(it, "eboot.bin").isFile }
    }.getOrNull()

    fun selectFolder(ctx: Context, uri: Uri): File {
        check(uri.authority == STORAGE_PROVIDER) {
            "Escolha a pasta no armazenamento interno ou no cartão SD."
        }
        val id = DocumentsContract.getTreeDocumentId(uri).split(':', limit = 2)
        check(id.size == 2) { "Não consegui identificar o caminho dessa pasta." }
        val root = when {
            id[0] == "primary" -> Environment.getExternalStorageDirectory()
            volumeId.matches(id[0]) -> File("/storage", id[0])
            else -> error("Esse volume de armazenamento não tem um caminho local compatível.")
        }.canonicalFile
        val folder = File(root, id[1]).canonicalFile
        check(folder.toPath().startsWith(root.toPath()) && folder.isDirectory && folder.canRead()) {
            "Não consegui ler essa pasta. Verifique a permissão de armazenamento."
        }

        val eboot = File(folder, "eboot.bin")
        check(eboot.isFile && eboot.canRead()) { "Selecione a pasta que contém o eboot.bin." }
        for (name in listOf("assets", "bundles", "sce_module", "sce_sys")) {
            val directory = File(folder, name)
            check(directory.isDirectory && directory.canRead()) { "Falta acesso à pasta $name do jogo." }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        eboot.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(hash == EBOOT_SHA256) { "O eboot.bin precisa ser da versão CUSA07027 v01.00." }

        val pointer = selected(ctx.filesDir)
        val output = pointer.startWrite()
        try {
            output.write(folder.path.toByteArray(Charsets.UTF_8))
            pointer.finishWrite(output)
        } catch (error: IOException) {
            pointer.failWrite(output)
            throw error
        }
        return folder
    }
}
