package eu.tlipman.eclipse.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val successful: Boolean get() = exitCode == 0 && !timedOut
    val summary: String
        get() = when {
            timedOut -> "Command timed out."
            successful -> (stdout.ifBlank { stderr }).ifBlank { "Command completed." }.trim()
            else -> (stderr.ifBlank { stdout }).ifBlank { "Command failed (exit $exitCode)." }.trim()
        }
}

class ShizukuShell(private val timeoutMillis: Long = 8_000) {
    suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        check(Shizuku.pingBinder()) { "Shizuku is not connected." }
        check(Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            "Eclipse does not have Shizuku permission."
        }
        val process: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val outThread = streamTo(process.inputStream, stdout)
        val errThread = streamTo(process.errorStream, stderr)
        val finished = process.waitForTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) process.destroy()
        outThread.join(1_000)
        errThread.join(1_000)
        ShellResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.toString(Charsets.UTF_8.name()),
            stderr = stderr.toString(Charsets.UTF_8.name()),
            timedOut = !finished
        )
    }

    private fun streamTo(input: InputStream, output: ByteArrayOutputStream) = thread(
        isDaemon = true
    ) {
        input.use { it.copyTo(output) }
    }

    companion object {
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
