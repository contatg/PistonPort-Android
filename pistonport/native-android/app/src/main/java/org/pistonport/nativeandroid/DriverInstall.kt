package org.pistonport.nativeandroid

import android.os.Handler
import android.os.Looper
import android.util.Log

// Shared by launcher instances so rotating the screen does not start a second installation.
internal object DriverInstall {
    data class Status(val running: Boolean, val message: String, val failed: Boolean = false)

    private val main = Handler(Looper.getMainLooper())
    var status: Status? = null
        private set
    var onChange: (() -> Unit)? = null

    fun start(name: String, initial: String, work: ((String) -> Unit) -> Unit) {
        if (status?.running == true) return
        update(Status(true, initial))
        Thread({
            val result = runCatching {
                work { message -> main.post { update(Status(true, message)) } }
            }
            main.post {
                val error = result.exceptionOrNull()
                if (error == null) {
                    update(Status(false, "$name instalado e selecionado."))
                } else {
                    Log.w("PistonPort", "Falha ao instalar $name", error)
                    val reason = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "arquivo ou conexão inválida"
                    update(Status(false, "Falha ao instalar $name. $reason", failed = true))
                }
            }
        }, "driver-install").start()
    }

    fun clearResult() {
        if (status?.running != true) status = null
    }

    private fun update(current: Status) {
        status = current
        onChange?.invoke()
    }
}
