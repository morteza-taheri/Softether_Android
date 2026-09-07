package vn.unlimit.vpngate.ui.screens.automode

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import vn.unlimit.vpngate.App
import vn.unlimit.vpngate.R
import vn.unlimit.vpngate.automode.AutoModeController
import vn.unlimit.vpngate.automode.AutoModeLogStore
import vn.unlimit.vpngate.automode.AutoModeState
import vn.unlimit.vpngate.ui.components.FlagImage
import vn.unlimit.vpngate.ui.theme.ExtendedTheme
import vn.unlimit.vpngate.ui.theme.MonospaceStyle
import vn.unlimit.vpngate.viewmodels.AutoModeViewModel

/**
 * Auto Mode screen: a single dynamic button across the four states plus
 * live server info, attempt progress, the live module log window and the
 * Try-next-server button. Logic stays in AutoModeEngine via the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoModeScreen() {
    val context = LocalContext.current
    val viewModel: AutoModeViewModel = viewModel()
    val state by viewModel.state.observeAsState(AutoModeState.Disconnected)

    // Developer-mode gating for the log panel (re-checked on every render,
    // matching renderLogPanelVisibility()).
    var developerMode by remember {
        mutableStateOf(viewModel.dataUtil.getDeveloperMode())
    }
    // Live log lines filtered by the active protocol (mirrors renderLog()).
    var logText by remember { mutableStateOf(formatLog(state, context)) }
    LaunchedEffect(Unit) {
        AutoModeLogStore.lines.collect {
            logText = formatLog(state, context)
            developerMode = viewModel.dataUtil.getDeveloperMode()
        }
    }
    LaunchedEffect(state) {
        logText = formatLog(state, context)
        developerMode = viewModel.dataUtil.getDeveloperMode()
    }

    // ---- Permission gates (§12): tunnel, notifications, battery ----
    // Stage machine: 1 = check VPN tunnel, 2 = check notifications,
    // 3 = check battery exemption. Launchers bump the stage on result;
    // the LaunchedEffect below drives the chain (no forward references).
    var gateStage by remember { mutableStateOf(0) }
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        gateStage = if (result.resultCode == Activity.RESULT_OK) 2 else 0
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                context,
                context.getString(R.string.auto_mode_error_vpn_permission),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Re-run the notification stage: with the grant it advances to 3.
        gateStage = if (granted) 2 else 0
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.auto_mode_error_notification_permission),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    LaunchedEffect(gateStage) {
        when (gateStage) {
            1 -> {
                // 1) OS VPN tunnel permission
                try {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent == null) {
                        gateStage = 2
                    } else {
                        vpnPermissionLauncher.launch(prepareIntent)
                    }
                } catch (e: ActivityNotFoundException) {
                    gateStage = 0
                    Toast.makeText(
                        context,
                        context.getString(R.string.auto_mode_error_vpn_permission),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            2 -> {
                // 2) Notifications (Android 13+): the foreground service needs
                //    them to keep the tunnel alive in the background.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    gateStage = 3
                }
            }
            3 -> {
                gateStage = 0
                // 3) Battery optimization exemption (one shot): open the
                //    exemption page once; the user re-presses the button
                //    afterwards and all gates pass instantly.
                try {
                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE)
                            as android.os.PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        val batteryIntent = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + context.packageName),
                        )
                        if (batteryIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(batteryIntent)
                            return@LaunchedEffect
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // All gates passed — start the run.
                viewModel.onButtonPressed()
            }
        }
    }

    fun startAutoModeOrRequestPermission() {
        val current = viewModel.state.value
        val isStart = current is AutoModeState.Disconnected || current is AutoModeState.Error
        if (!isStart) {
            viewModel.onButtonPressed()
            return
        }
        gateStage = 1
    }

    // ---- State → visuals ----
    val (color, colorDeep, labelRes, subLabel, icon, connecting) = when (val s = state) {
        is AutoModeState.Disconnected -> ButtonVisuals(
            color = MaterialTheme.colorScheme.primary,
            colorDeep = vn.unlimit.vpngate.ui.theme.ExtendedTheme.primaryDark,
            labelRes = R.string.auto_mode_state_disconnected,
            subLabel = null,
            icon = Icons.Filled.PowerSettingsNew,
            connecting = false,
        )
        is AutoModeState.Connecting -> ButtonVisuals(
            color = ExtendedTheme.autoConnecting,
            colorDeep = ExtendedTheme.autoConnecting.copy(alpha = 0.65f),
            labelRes = R.string.auto_mode_state_connecting,
            subLabel = context.getString(
                R.string.auto_mode_attempt_progress, s.attempt, s.total,
            ),
            icon = null,
            connecting = true,
        )
        is AutoModeState.Connected -> ButtonVisuals(
            color = ExtendedTheme.autoConnected,
            colorDeep = ExtendedTheme.autoConnected.copy(alpha = 0.65f),
            labelRes = R.string.auto_mode_state_connected,
            subLabel = s.hostname,
            icon = Icons.Filled.ThumbUp,
            connecting = false,
        )
        is AutoModeState.Error -> ButtonVisuals(
            color = ExtendedTheme.autoDisconnected,
            colorDeep = ExtendedTheme.autoDisconnected.copy(alpha = 0.65f),
            labelRes = R.string.auto_mode_state_error,
            subLabel = null,
            icon = Icons.Filled.Close,
            connecting = false,
        )
        else -> ButtonVisuals(
            color = MaterialTheme.colorScheme.primary,
            colorDeep = vn.unlimit.vpngate.ui.theme.ExtendedTheme.primaryDark,
            labelRes = R.string.auto_mode_state_disconnected,
            subLabel = null,
            icon = Icons.Filled.PowerSettingsNew,
            connecting = false,
        )
    }
    val animatedColor by animateColorAsState(color, label = "autoBtnColor")
    val animatedDeep by animateColorAsState(colorDeep, label = "autoBtnDeep")
    val canSkip = state is AutoModeState.Connecting ||
            state is AutoModeState.Connected ||
            state is AutoModeState.Error

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_mode)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AutoModePowerButton(
                stateLabel = stringResource(labelRes),
                subLabel = subLabel,
                color = animatedColor,
                colorDeep = animatedDeep,
                icon = icon,
                connecting = connecting,
                onClick = { startAutoModeOrRequestPermission() },
                modifier = Modifier.padding(top = 12.dp),
            )
            TryNextServerButton(
                label = stringResource(R.string.auto_mode_try_next_server),
                enabled = canSkip,
                onClick = { viewModel.tryNextServer() },
                modifier = Modifier.padding(top = 10.dp),
            )
            if (state is AutoModeState.Error) {
                val error = state as AutoModeState.Error
                Text(
                    text = when (error.message) {
                        AutoModeController.ERROR_NO_SERVER ->
                            stringResource(R.string.auto_mode_error_no_server)
                        AutoModeController.ERROR_VPN_PERMISSION ->
                            stringResource(R.string.auto_mode_error_vpn_permission)
                        else -> stringResource(R.string.auto_mode_error_generic)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            when (val s = state) {
                is AutoModeState.Connecting -> {
                    ServerInfoCard(
                        hostname = s.hostname,
                        ip = s.ip,
                        protocol = s.protocol.id.lowercase().replace('_', ' '),
                        speed = s.speed,
                        ping = s.ping,
                        attempt = s.attempt,
                        total = s.total,
                    )
                }
                is AutoModeState.Connected -> {
                    ServerInfoCard(
                        hostname = s.hostname,
                        ip = s.ip,
                        protocol = s.protocol.id.lowercase().replace('_', ' '),
                        speed = s.speed,
                        ping = s.ping,
                        attempt = null,
                        total = null,
                    )
                }
                else -> Unit
            }
            if (developerMode) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.auto_mode_log_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                val text = AutoModeLogStore.asText()
                                if (text.isNotEmpty()) {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("AutoModeLog", text),
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.auto_mode_log_copied),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }) {
                                Text(stringResource(R.string.auto_mode_log_copy))
                            }
                            androidx.compose.material3.TextButton(onClick = {
                                AutoModeLogStore.clear()
                            }) {
                                Text(stringResource(R.string.auto_mode_log_clear))
                            }
                        }
                        val logListState = rememberLazyListState()
                        LaunchedEffect(logText) {
                            if (logListState.layoutInfo.totalItemsCount > 0) {
                                logListState.animateScrollToItem(logListState.layoutInfo.totalItemsCount - 1)
                            }
                        }
                        LaunchedEffect(Unit) {
                            AutoModeLogStore.lines.collect {
                                developerMode = viewModel.dataUtil.getDeveloperMode()
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        ) {
                            LazyColumn(
                                state = logListState,
                                contentPadding = PaddingValues(6.dp),
                            ) {
                                items(logText.lines()) { line ->
                                    SelectionContainer {
                                        Text(
                                            line,
                                            style = MonospaceStyle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerInfoCard(
    hostname: String?,
    ip: String?,
    protocol: String,
    speed: Long,
    ping: Int,
    attempt: Int?,
    total: Int?,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.auto_mode_server_name, hostname ?: "-"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.auto_mode_server_ip, ip ?: "-"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.auto_mode_server_protocol, protocol),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.auto_mode_server_speed, speed / 1_000_000),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.auto_mode_server_ping, ping),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (attempt != null && total != null) {
                LinearProgressIndicator(
                    progress = { attempt.toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.auto_mode_attempt_n_of_m, attempt, total),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private data class ButtonVisuals(
    val color: Color,
    val colorDeep: Color,
    val labelRes: Int,
    val subLabel: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val connecting: Boolean,
)

private fun formatLog(state: AutoModeState, context: Context): String {
    val protocol = when (state) {
        is AutoModeState.Connecting -> state.protocol
        is AutoModeState.Connected -> state.protocol
        else -> null
    }
    val filtered = AutoModeLogStore.filterFor(protocol)
    return if (filtered.isEmpty()) {
        context.getString(R.string.auto_mode_log_empty)
    } else {
        filtered.joinToString("\n") { AutoModeLogStore.format(it) }
    }
}
