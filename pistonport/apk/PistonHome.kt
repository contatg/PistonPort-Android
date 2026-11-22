package ro.cobrabm.fexdroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
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
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@Composable
internal fun PistonHome() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(PistonSource.current(ctx.filesDir)) }
    var checkingFolder by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !checkingFolder) {
            checkingFolder = true
            message = null
            scope.launch {
                try {
                    source = withContext(Dispatchers.IO) { PistonSource.selectFolder(ctx, uri) }
                } catch (error: Exception) {
                    message = error.message ?: "Falha ao abrir a pasta."
                } finally {
                    checkingFolder = false
                }
            }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) picker.launch(null)
        else message = "Permita a leitura do armazenamento para abrir a pasta do jogo."
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PistonPort Android", style = MaterialTheme.typography.headlineLarge)
        if (GameSession.active) {
            Button(onClick = { GameSession.playerVisible = true }) { Text("Voltar à sessão") }
            OutlinedButton(onClick = { GameSession.stop() }) { Text("Encerrar sessão") }
        } else {
            Text(if (source == null) "Selecione a pasta extraída CUSA07027 v01.00." else "Pasta do jogo selecionada.")
            Button(enabled = source != null && !checkingFolder, onClick = { GameSession.start(ctx, Game.PISTON) }) {
                Text("Jogar")
            }
            OutlinedButton(enabled = !checkingFolder, onClick = {
                if (ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    picker.launch(null)
                } else {
                    storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }) {
                Text(if (checkingFolder) "Verificando a pasta…" else "Selecionar pasta do jogo")
            }
            OutlinedButton(enabled = !checkingFolder, onClick = { GameSession.start(ctx, Game.PISTON_VULKAN) }) {
                Text("Testar Vulkan")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
