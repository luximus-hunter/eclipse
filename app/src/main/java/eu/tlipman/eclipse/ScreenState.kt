package eu.tlipman.eclipse

internal data class ScreenState(
    val shizukuInstalled: Boolean = false,
    val binderConnected: Boolean = false,
    val authorised: Boolean = false,
    val uid: Int? = null,
    val batterySaver: Boolean = false,
    val darkTheme: Boolean = false,
    val overrideEnabled: Boolean = false,
    val scheduleEnabled: Boolean = false,
    val configConflictDetected: Boolean = false,
    val busy: Boolean = false,
    val overrideStatus: String = "Checking",
    val scheduleStatus: String = "Checking",
    val lastResult: String = "Connect Shizuku to inspect system settings.",
    val diagnostics: String = "Not checked"
)
