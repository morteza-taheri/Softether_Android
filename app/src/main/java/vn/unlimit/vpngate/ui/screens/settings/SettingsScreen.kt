package vn.unlimit.vpngate.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.InetAddresses
import android.os.Build
import android.text.InputFilter
import android.text.Spanned
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.blinkt.openvpn.core.OpenVPNService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.unlimit.vpngate.App
import vn.unlimit.vpngate.R
import vn.unlimit.vpngate.activities.MainActivity
import vn.unlimit.vpngate.activities.DetailActivity
import vn.unlimit.vpngate.ui.screens.home.OperatorDropdown
import vn.unlimit.vpngate.utils.AppConfig
import vn.unlimit.vpngate.utils.DataUtil
import vn.unlimit.vpngate.utils.ExcludeAppsManager
import java.text.DateFormat

/**
 * Settings: every preference from the old SettingFragment, grouped into
 * tonal section cards. Handlers are ported 1:1 (DataUtil + OscPrefKey).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateHomeRequested: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val dataUtil = remember { app.dataUtil!! }
    val prefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val excludeAppsManager = remember { ExcludeAppsManager(context) }

    // ---- Setting states
    var notifySpeed by remember { mutableStateOf(dataUtil.getBooleanSetting(DataUtil.SETTING_NOTIFY_SPEED, true)) }
    var blockAds by remember { mutableStateOf(dataUtil.getBooleanSetting(DataUtil.SETTING_BLOCK_ADS, false)) }
    var includeUdp by remember { mutableStateOf(dataUtil.getBooleanSetting(DataUtil.INCLUDE_UDP_SERVER, true)) }
    var useCustomDns by remember { mutableStateOf(dataUtil.getBooleanSetting(DataUtil.USE_CUSTOM_DNS, false)) }
    var dns1 by remember { mutableStateOf(dataUtil.getStringSetting(DataUtil.CUSTOM_DNS_IP_1, "8.8.8.8") ?: "") }
    var dns2 by remember { mutableStateOf(dataUtil.getStringSetting(DataUtil.CUSTOM_DNS_IP_2, "") ?: "") }
    var useDomain by remember { mutableStateOf(dataUtil.getBooleanSetting(DataUtil.USE_DOMAIN_TO_CONNECT, false)) }
    var cacheTimeIndex by remember { mutableStateOf(dataUtil.getIntSetting(DataUtil.SETTING_CACHE_TIME_KEY, DataUtil.DEFAULT_CACHE_TIME_INDEX)) }
    var startupScreenIndex by remember { mutableStateOf(dataUtil.getIntSetting(DataUtil.SETTING_STARTUP_SCREEN, 0)) }
    var themeIndex by remember { mutableStateOf(dataUtil.getIntSetting(DataUtil.SETTING_THEME, 0)) }
    var languageIndex by remember {
        mutableStateOf(
            when (androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
                "fa" -> 2
                "en" -> 1
                else -> 0
            },
        )
    }
    var developerMode by remember { mutableStateOf(dataUtil.getDeveloperMode()) }
    var excludedAppsCount by remember { mutableStateOf(excludeAppsManager.getExcludedAppsCount()) }
    var showExcludedApps by remember { mutableStateOf(false) }
    var autoProtocol by remember {
        mutableStateOf(
            vn.unlimit.vpngate.automode.AutoModeProtocol.fromId(
                dataUtil.getStringSetting(DataUtil.SETTING_DEFAULT_VPN_PROTOCOL, null),
            ),
        )
    }
    var autoTimeout by remember { mutableStateOf(dataUtil.getAutoModeTimeoutSeconds()) }
    var softetherMaxConnections by remember { mutableStateOf(dataUtil.getSoftEtherMaxConnections()) }
    var cacheExpires by remember { mutableStateOf(dataUtil.connectionCacheExpires) }
    var showAutoProtocolPicker by remember { mutableStateOf(false) }
    var showAutoTimeoutPicker by remember { mutableStateOf(false) }
    var showSoftetherConnectionsPicker by remember { mutableStateOf(false) }
    val applyNextConnectionNote = stringResource(R.string.setting_apply_on_next_connection_time)
    val hasLastConnection = remember { dataUtil.lastVPNConnection != null }
    val showImportOptions = remember { App.isImportToOpenVPN }

    fun clearListServerCache(showToast: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { dataUtil.clearConnectionCache() }
            if (ok) {
                cacheExpires = null
                if (showToast) {
                    Toast.makeText(context, context.getString(R.string.setting_clear_cache_success), Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) {
                    context.sendBroadcast(Intent(vn.unlimit.vpngate.provider.BaseProvider.ACTION.ACTION_CLEAR_CACHE))
                }
            } else if (showToast) {
                Toast.makeText(context, context.getString(R.string.setting_clear_cache_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val cacheTimes = cacheTimeLabels()
        val startupScreens = startupScreenLabels()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- Connection section
            item {
                SectionCard(title = stringResource(R.string.status)) {
                    SettingSwitch(
                        title = stringResource(R.string.enable_notification_speed),
                        subtitle = stringResource(R.string.enable_notification_speed_hint),
                        checked = notifySpeed,
                        onChecked = {
                            notifySpeed = it
                            Toast.makeText(context, applyNextConnectionNote, Toast.LENGTH_SHORT).show()
                            dataUtil.setBooleanSetting(DataUtil.SETTING_NOTIFY_SPEED, it)
                        },
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        title = stringResource(R.string.udp_setting_label),
                        subtitle = stringResource(R.string.udp_setting_hint),
                        checked = includeUdp,
                        onChecked = {
                            includeUdp = it
                            dataUtil.setBooleanSetting(DataUtil.INCLUDE_UDP_SERVER, it)
                            clearListServerCache(false)
                        },
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        title = stringResource(R.string.use_domain_label),
                        subtitle = stringResource(R.string.use_domain_hint),
                        checked = useDomain,
                        onChecked = {
                            useDomain = it
                            dataUtil.setBooleanSetting(DataUtil.USE_DOMAIN_TO_CONNECT, it)
                        },
                    )
                    if (!showImportOptions) {
                        HorizontalDivider()
                        SettingSwitch(
                            title = stringResource(R.string.block_ads_setting_label),
                            subtitle = stringResource(R.string.block_ads_setting_hint),
                            checked = blockAds,
                            onChecked = {
                                blockAds = it
                                Toast.makeText(context, applyNextConnectionNote, Toast.LENGTH_SHORT).show()
                                dataUtil.setBooleanSetting(DataUtil.SETTING_BLOCK_ADS, it)
                                if (it && useCustomDns) {
                                    useCustomDns = false
                                    dataUtil.setBooleanSetting(DataUtil.USE_CUSTOM_DNS, false)
                                }
                                prefs.edit().apply {
                                    if (it) {
                                        putBoolean(kittoku.osc.preference.OscPrefKey.DNS_DO_USE_CUSTOM_SERVER.toString(), true)
                                        putString(
                                            kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS.toString(),
                                            AppConfig.getString("vpn_dns_block_ads_primary"),
                                        )
                                        putString(
                                            kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS_SECONDARY.toString(),
                                            AppConfig.getString("vpn_dns_block_ads_alternative"),
                                        )
                                    } else {
                                        putBoolean(kittoku.osc.preference.OscPrefKey.DNS_DO_USE_CUSTOM_SERVER.toString(), false)
                                        remove(kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS.toString())
                                        remove(kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS_SECONDARY.toString())
                                    }
                                }
                            },
                        )
                    }
                }
            }
            // ---------------- DNS section
            if (!showImportOptions) {
                item {
                    SectionCard(title = stringResource(R.string.dns_setting_label)) {
                        SettingSwitch(
                            title = stringResource(R.string.dns_setting_label),
                            subtitle = stringResource(R.string.dns_setting_hint),
                            checked = useCustomDns,
                            onChecked = {
                                useCustomDns = it
                                dataUtil.setBooleanSetting(DataUtil.USE_CUSTOM_DNS, it)
                                if (it) {
                                    if (blockAds) {
                                        blockAds = false
                                        dataUtil.setBooleanSetting(DataUtil.SETTING_BLOCK_ADS, false)
                                    }
                                }
                                prefs.edit().apply {
                                    if (it) {
                                        putBoolean(kittoku.osc.preference.OscPrefKey.DNS_DO_USE_CUSTOM_SERVER.toString(), true)
                                        putString(
                                            kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS.toString(),
                                            dns1.ifEmpty { "8.8.8.8" },
                                        )
                                        putString(
                                            kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS_SECONDARY.toString(),
                                            dns2.ifEmpty { "8.8.4.4" },
                                        )
                                    } else {
                                        remove(kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS.toString())
                                        remove(kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS_SECONDARY.toString())
                                        putBoolean(kittoku.osc.preference.OscPrefKey.DNS_DO_USE_CUSTOM_SERVER.toString(), false)
                                    }
                                }
                            },
                        )
                        if (useCustomDns) {
                            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                                OutlinedTextField(
                                    value = dns1,
                                    onValueChange = { dns1 = it },
                                    label = { Text(stringResource(R.string.dns_ip_1)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                )
                                OutlinedTextField(
                                    value = dns2,
                                    onValueChange = { dns2 = it },
                                    label = { Text(stringResource(R.string.dns_ip_2)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                )
                                Button(onClick = {
                                    fun isValidIp(ip: String): Boolean =
                                        if (ip.isEmpty()) true else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            InetAddresses.isNumericAddress(ip)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            Patterns.IP_ADDRESS.matcher(ip).matches()
                                        }
                                    if (isValidIp(dns1) && isValidIp(dns2)) {
                                        dataUtil.setStringSetting(DataUtil.CUSTOM_DNS_IP_1, dns1)
                                        dataUtil.setStringSetting(DataUtil.CUSTOM_DNS_IP_2, dns2)
                                        prefs.edit()
                                            .putString(kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS.toString(), dns1)
                                            .putString(
                                                kittoku.osc.preference.OscPrefKey.DNS_CUSTOM_ADDRESS_SECONDARY.toString(),
                                                dns2.ifEmpty { "8.8.4.4" },
                                            )
                                            .apply()
                                    } else {
                                        Toast.makeText(context, "Invalid IP address", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text(stringResource(R.string.apply))
                                }
                            }
                        }
                    }
                }
            }
            // ---------------- Auto Mode section
            item {
                SectionCard(title = stringResource(R.string.auto_mode)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_auto_protocol_label)) },
                        trailingContent = {
                            Text(
                                autoProtocol.id.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickableRow {
                            showAutoProtocolPicker = true
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_auto_timeout_label)) },
                        trailingContent = {
                            Text(
                                "$autoTimeout seconds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickableRow {
                            showAutoTimeoutPicker = true
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_softether_max_connections_label)) },
                        trailingContent = {
                            Text(
                                stringResource(
                                    R.string.setting_softether_max_connections_value,
                                    softetherMaxConnections,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickableRow {
                            showSoftetherConnectionsPicker = true
                        },
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_excluded_apps_label)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.exclude_apps_text, excludedAppsCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickableRow { showExcludedApps = true },
                    )
                }
            }
            // ---------------- Cache section
            item {
                SectionCard(title = stringResource(R.string.setting_cache_label)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_cache_label)) },
                        supportingContent = { Text(stringResource(R.string.setting_cache_subtitle)) },
                        trailingContent = {
                            OperatorDropdown(
                                value = cacheTimes[cacheTimeIndex],
                                labels = cacheTimes,
                                onSelect = { index ->
                                    cacheTimeIndex = index
                                    dataUtil.setIntSetting(DataUtil.SETTING_CACHE_TIME_KEY, index)
                                },
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        },
                    )
                    if (cacheExpires != null) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.setting_cache_auto_clear_label))
                            },
                            supportingContent = {
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                                        .format(cacheExpires),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                Button(onClick = { clearListServerCache(true) }) {
                                    Text(stringResource(R.string.setting_cache_clear))
                                }
                            },
                        )
                    }
                }
            }
            // ---------------- Startup + appearance
            if (hasLastConnection) {
                item {
                    SectionCard(title = stringResource(R.string.setting_startup_screen)) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_startup_screen)) },
                            supportingContent = { Text(stringResource(R.string.setting_startup_screen_subtitle)) },
                            trailingContent = {
                                OperatorDropdown(
                                    value = startupScreens[startupScreenIndex],
                                    labels = startupScreens,
                                    onSelect = { index ->
                                        startupScreenIndex = index
                                        dataUtil.setIntSetting(DataUtil.SETTING_STARTUP_SCREEN, index)
                                        OpenVPNService.setNotificationActivityClass(
                                            if (index == 0) DetailActivity::class.java
                                            else MainActivity::class.java,
                                        )
                                    },
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            },
                        )
                    }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.setting_theme_label)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_language_label)) },
                        trailingContent = {
                            OperatorDropdown(
                                value = languageNames[languageIndex],
                                labels = languageNames,
                                onSelect = { index ->
                                    languageIndex = index
                                    val locales = when (index) {
                                        1 -> androidx.core.os.LocaleListCompat.forLanguageTags("en")
                                        2 -> androidx.core.os.LocaleListCompat.forLanguageTags("fa")
                                        else -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                    }
                                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                                },
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        },
                    )
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.setting_theme_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            themeNames.forEachIndexed { index, name ->
                                SegmentedButton(
                                    selected = themeIndex == index,
                                    onClick = {
                                        if (themeIndex != index) {
                                            themeIndex = index
                                            dataUtil.setIntSetting(DataUtil.SETTING_THEME, index)
                                            val mode = when (index) {
                                                1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                                                2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                            }
                                            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                                            (context as? Activity)?.recreate()
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                                ) {
                                    Text(name)
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.setting_theme_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    HorizontalDivider()
                    SettingSwitch(
                        title = stringResource(R.string.setting_developer_mode_label),
                        subtitle = stringResource(R.string.setting_developer_mode_summary),
                        checked = developerMode,
                        onChecked = {
                            developerMode = it
                            dataUtil.setDeveloperMode(it)
                            vn.unlimit.vpngate.data.model.CollectorLog.enabled = it
                            vn.unlimit.vpngate.automode.AutoModeLogStore.setPaused(!it)
                        },
                    )
                }
            }
        }
    }

    // ---- pickers
    if (showAutoProtocolPicker) {
        SingleChoiceDialog(
            title = stringResource(R.string.setting_auto_protocol_label),
            options = vn.unlimit.vpngate.automode.AutoModeProtocol.entries
                .map { it.id.lowercase().replace('_', ' ') },
            selectedIndex = vn.unlimit.vpngate.automode.AutoModeProtocol.entries.indexOf(autoProtocol),
            onSelect = { index ->
                autoProtocol = vn.unlimit.vpngate.automode.AutoModeProtocol.entries[index]
                dataUtil.setStringSetting(
                    DataUtil.SETTING_DEFAULT_VPN_PROTOCOL,
                    autoProtocol.id,
                )
            },
            onDismiss = { showAutoProtocolPicker = false },
        )
    }
    if (showAutoTimeoutPicker) {
        val values = (5..60).toList()
        SingleChoiceDialog(
            title = stringResource(R.string.setting_auto_timeout_label),
            options = values.map { "$it seconds" },
            selectedIndex = values.indexOf(autoTimeout).let { if (it < 0) values.indexOf(12) else it },
            onSelect = { index ->
                autoTimeout = values[index]
                dataUtil.setAutoModeTimeoutSeconds(values[index])
            },
            onDismiss = { showAutoTimeoutPicker = false },
        )
    }
    if (showSoftetherConnectionsPicker) {
        val values = listOf(1, 2, 3, 4, 5, 6, 8)
        SingleChoiceDialog(
            title = stringResource(R.string.setting_softether_max_connections_label),
            options = values.map { context.getString(R.string.setting_softether_max_connections_value, it) },
            selectedIndex = values.indexOf(softetherMaxConnections).let { if (it < 0) values.indexOf(4) else it },
            onSelect = { index ->
                softetherMaxConnections = values[index]
                dataUtil.setSoftEtherMaxConnections(values[index])
            },
            onDismiss = { showSoftetherConnectionsPicker = false },
        )
    }
    if (showExcludedApps) {
        ExcludedAppsSheet(
            manager = excludeAppsManager,
            onDismiss = {
                showExcludedApps = false
                excludedAppsCount = excludeAppsManager.getExcludedAppsCount()
            },
        )
    }
}

@Composable
private fun cacheTimeLabels(): List<String> = listOf(
    stringResource(R.string.cache_time_15m),
    stringResource(R.string.cache_time_30m),
    stringResource(R.string.cache_time_1h),
    stringResource(R.string.cache_time_2h),
    stringResource(R.string.cache_time_4h),
    stringResource(R.string.cache_time_6h),
    stringResource(R.string.cache_time_12h),
    stringResource(R.string.cache_time_24h),
    stringResource(R.string.cache_time_never),
)

@Composable
private fun startupScreenLabels(): List<String> = listOf(
    stringResource(R.string.startup_screen_list),
    stringResource(R.string.startup_screen_status),
)

private val themeNames = listOf("System default", "Light", "Dark")
private val languageNames = listOf("System default", "English", "فارسی")

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
