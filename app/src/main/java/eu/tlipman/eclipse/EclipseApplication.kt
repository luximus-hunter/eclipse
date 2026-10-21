package eu.tlipman.eclipse

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import eu.tlipman.eclipse.shizuku.ShizukuShell
import eu.tlipman.eclipse.system.EclipseManager
import eu.tlipman.eclipse.system.EclipseStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class EclipseApplication : Application() {
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Could not apply Battery Saver protection on Shizuku startup.", error)
        }
    )

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        applicationScope.launch {
            applyBatterySaverProtectionIfEnabled()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    private suspend fun applyBatterySaverProtectionIfEnabled() {
        val store = EclipseStore(this)
        if (!store.overrideEnabled) return
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Skipping startup apply because Shizuku disconnected.")
            return
        }

        val authorised = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not check Shizuku permission during startup.", e)
            return
        }
        if (!authorised) {
            Log.i(TAG, "Skipping startup apply because Shizuku permission is not granted.")
            return
        }

        val result = EclipseManager(this, ShizukuShell(), store).maintainOverride()
        if (result.success) {
            Log.i(TAG, result.message)
        } else {
            Log.e(TAG, result.message)
        }
    }

    private companion object {
        const val TAG = "EclipseApplication"
    }
}
