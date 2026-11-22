package ro.cobrabm.fexdroid

import android.Manifest
import android.content.pm.PackageManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
