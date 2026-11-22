package ro.cobrabm.fexdroid

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object PistonSource {
    private const val EBOOT_SHA256 = "56d27cc83b7fe34921fb07413dd7c8fb255daf357a3f81c2d882969f001ff654"
    private fun selected(files: File) = AtomicFile(File(files, "piston-source.txt"))

    fun current(files: File): File? = runCatching {
        File(selected(files).readFully().toString(Charsets.UTF_8)).takeIf { File(it, "eboot.bin").isFile }
    }.getOrNull()

    fun importFolder(ctx: Context, uri: Uri): File {
        val folder = DocumentFile.fromTreeUri(ctx, uri) ?: error("Não consegui abrir essa pasta.")
        val documents = folder.listFiles()
        val eboot = documents.firstOrNull { it.name == "eboot.bin" && it.isFile }
            ?: error("Selecione a pasta que contém o eboot.bin.")
        val digest = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(eboot.uri)?.use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Não consegui ler o eboot.bin.")
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(hash == EBOOT_SHA256) { "O eboot.bin precisa ser da versão CUSA07027 v01.00." }
        for (name in listOf("assets", "bundles", "sce_module", "sce_sys")) {
            check(documents.any { it.isDirectory && it.name == name }) { "Falta a pasta $name na cópia selecionada." }
        }

        val storage = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "pistonport-imports")
        storage.mkdirs()
        val pending = File(storage, UUID.randomUUID().toString())
        val previous = current(ctx.filesDir)
        check(pending.mkdir()) { "Não consegui criar a pasta de importação." }
        try {
            copyFolder(ctx, documents, pending, 0)
            val pointer = selected(ctx.filesDir)
            val output = pointer.startWrite()
            try {
                output.write(pending.path.toByteArray(Charsets.UTF_8))
                pointer.finishWrite(output)
            } catch (error: IOException) {
                pointer.failWrite(output)
                throw error
            }
        } catch (error: Exception) {
            pending.deleteRecursively()
            throw error
        }
        previous?.takeIf { it.parentFile == storage }?.deleteRecursively()
        return pending
    }

    private fun copyFolder(ctx: Context, children: Array<DocumentFile>, target: File, depth: Int) {
        check(depth < 40) { "A pasta selecionada tem subpastas demais." }
        for (child in children) {
            val name = child.name ?: error("Um arquivo da pasta não tem nome.")
            check(name.isNotEmpty() && name !in listOf(".", "..") && '/' !in name && '\\' !in name) {
                "Nome de arquivo inválido."
            }
            val path = File(target, name)
            if (child.isDirectory) {
                check(path.mkdir()) { "Não consegui criar $name." }
                copyFolder(ctx, child.listFiles(), path, depth + 1)
            } else if (child.isFile) {
                val input = ctx.contentResolver.openInputStream(child.uri) ?: error("Não consegui ler $name.")
                input.use { source -> path.outputStream().buffered().use { source.copyTo(it) } }
            }
        }
    }
}

@Composable
internal fun PistonHome() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(PistonSource.current(ctx.filesDir)) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !importing) {
            importing = true
            message = null
            scope.launch {
                try {
                    source = withContext(Dispatchers.IO) { PistonSource.importFolder(ctx, uri) }
                } catch (error: Exception) {
                    message = error.message ?: "Falha ao importar a pasta."
                } finally {
                    importing = false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PistonPort Android", style = MaterialTheme.typography.headlineLarge)
        if (GameSession.active) {
            Button(onClick = { GameSession.playerVisible = true }) { Text("Voltar à sessão") }
            OutlinedButton(onClick = { GameSession.stop() }) { Text("Encerrar sessão") }
        } else {
            Text(if (source == null) "Selecione a pasta extraída CUSA07027 v01.00." else "Pasta do jogo importada.")
            Button(enabled = source != null && !importing, onClick = { GameSession.start(ctx, Game.PISTON) }) {
                Text("Jogar")
            }
            OutlinedButton(enabled = !importing, onClick = { picker.launch(null) }) {
                Text(if (importing) "Copiando os arquivos…" else "Selecionar pasta do jogo")
            }
            OutlinedButton(enabled = !importing, onClick = { GameSession.start(ctx, Game.PISTON_VULKAN) }) {
                Text("Testar Vulkan")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
