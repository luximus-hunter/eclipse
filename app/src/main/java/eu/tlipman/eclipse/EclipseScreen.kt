package eu.tlipman.eclipse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime

private enum class AppPage(
    val navigationLabel: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    Status("Status", "Eclipse", "Your system appearance at a glance", Icons.Outlined.Home),
    Schedule("Schedule", "Schedule", "Android's native Light/Dark schedule", Icons.Outlined.Schedule),
    Settings("Settings", "Settings", "Access and maintenance", Icons.Outlined.Settings)
}

@Composable
internal fun EclipseScreen(
    state: ScreenState,
    darkStart: LocalTime,
    lightStart: LocalTime,
    onOverrideChange: (Boolean) -> Unit,
    onScheduleChange: (Boolean) -> Unit,
    onDarkTime: (LocalTime) -> Unit,
    onLightTime: (LocalTime) -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onMergeRestore: () -> Unit,
    onPermission: () -> Unit,
    onDiagnostics: () -> Unit
) {
    var selectedPage by rememberSaveable { mutableStateOf(AppPage.Status) }
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedPage) {
        scrollState.scrollTo(0)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = { selectedPage = page },
                        icon = { Icon(page.icon, contentDescription = page.navigationLabel) },
                        label = { Text(page.navigationLabel) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(selectedPage)
            when (selectedPage) {
                AppPage.Status -> StatusPage(
                    state = state,
                    onOverrideChange = onOverrideChange,
                    onMergeRestore = onMergeRestore
                )
                AppPage.Settings -> SettingsPage(
                    state = state,
                    onPermission = onPermission,
                    onApply = onApply,
                    onRestore = onRestore,
                    onMergeRestore = onMergeRestore,
                    onDiagnostics = onDiagnostics
                )
                AppPage.Schedule -> SchedulePage(
                    state = state,
                    darkStart = darkStart,
                    lightStart = lightStart,
                    onScheduleChange = onScheduleChange,
                    onDarkTime = onDarkTime,
                    onLightTime = onLightTime
                )
            }
        }
    }
}

@Composable
private fun PageHeader(page: AppPage) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            page.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            page.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
