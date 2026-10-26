package com.graphite.app.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.graphite.app.BuildConfig
import com.graphite.app.R
import com.graphite.app.core.repo.BackupManager
import com.graphite.app.core.repo.ProfileRepository
import com.graphite.app.data.AppLanguage
import com.graphite.app.data.AppSettings
import com.graphite.app.data.ProxyMode
import com.graphite.app.data.SettingsRepository
import com.graphite.app.data.SubscriptionUpdateWorker
import com.graphite.app.data.ThemeMode
import com.graphite.app.data.TunStack
import com.graphite.app.ui.components.ClickRow
import com.graphite.app.ui.components.Label
import com.graphite.app.ui.components.GraphiteTextField
import com.graphite.app.ui.components.SectionCard
import com.graphite.app.ui.components.SwitchRow
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.Smoke
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    app: Application,
) : AndroidViewModel(app) {
    val repo = SettingsRepository(app)
    private val profileRepo = ProfileRepository.get(app)

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun set(transform: suspend (SettingsRepository) -> Unit) {
        viewModelScope.launch { transform(repo) }
    }

    fun applySubsWork(
        enabled: Boolean,
        hours: Int,
    ) {
        SubscriptionUpdateWorker.schedule(getApplication(), enabled, hours)
    }

    fun exportBackup(write: (String) -> Unit) {
        viewModelScope.launch {
            val profiles = profileRepo.profiles().first()
            val subs = profileRepo.subscriptions().first()
            write(BackupManager.export(profiles, subs))
        }
    }

    fun importBackup(content: String): Int {
        val parsed = BackupManager.import(content) ?: return -1
        viewModelScope.launch {
            parsed.first.takeIf { it.isNotEmpty() }?.let { profileRepo.insertParsed(it) }
            parsed.second.forEach { sub -> profileRepo.addSubscription(sub) }
        }
        return parsed.first.size
    }
}

// ---------------------------------------------------------------- hub

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        SectionCard {
            ClickRow(
                stringResource(R.string.settings_connection),
                stringResource(R.string.settings_connection_desc),
                onClick = onOpenConnection,
                icon = Icons.Outlined.Bolt,
            )
            DividerRow()
            ClickRow(
                stringResource(R.string.settings_apps),
                stringResource(R.string.settings_apps_desc),
                onClick = onOpenApps,
                icon = Icons.Outlined.Apps,
            )
            DividerRow()
            ClickRow(
                stringResource(R.string.settings_subscriptions),
                stringResource(R.string.settings_subscriptions_desc),
                onClick = onOpenSubscriptions,
                icon = Icons.Outlined.CloudSync,
            )
            DividerRow()
            ClickRow(
                stringResource(R.string.settings_appearance),
                stringResource(R.string.settings_appearance_desc),
                onClick = onOpenAppearance,
                icon = Icons.Outlined.DarkMode,
            )
            DividerRow()
            BackupSection(vm)
            DividerRow()
            ClickRow(
                stringResource(R.string.settings_about),
                stringResource(R.string.settings_about_desc),
                onClick = onOpenAbout,
                icon = Icons.Outlined.Info,
            )
        }
    }
}

@Composable
private fun BackupSection(vm: SettingsViewModel) {
    val context = LocalContext.current
    var importResult by remember { mutableStateOf<Int?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let { u ->
                vm.exportBackup { json ->
                    context.contentResolver.openOutputStream(u)?.use { os ->
                        os.write(json.toByteArray())
                    }
                }
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { u ->
                val text =
                    context.contentResolver
                        .openInputStream(u)
                        ?.bufferedReader()
                        ?.readText()
                text?.let { importResult = vm.importBackup(it) }
            }
        }

    Column {
        ClickRow(
            stringResource(R.string.backup_export),
            stringResource(R.string.settings_backup_desc),
            onClick = { exportLauncher.launch("graphite-backup.json") },
            icon = Icons.Outlined.Archive,
        )
        ClickRow(
            stringResource(R.string.backup_import),
            null,
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            icon = Icons.Outlined.Restore,
        )
    }
}

// ---------------------------------------------------------------- appearance

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    SettingsScaffold(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        Label(stringResource(R.string.theme))
        SectionCard {
            RadioRow(stringResource(R.string.theme_black), settings.theme == ThemeMode.BLACK) {
                vm.set { it.setTheme(ThemeMode.BLACK) }
            }
            RadioRow(stringResource(R.string.theme_gray), settings.theme == ThemeMode.GRAY) {
                vm.set { it.setTheme(ThemeMode.GRAY) }
            }
            RadioRow(stringResource(R.string.theme_dynamic), settings.theme == ThemeMode.DYNAMIC) {
                vm.set { it.setTheme(ThemeMode.DYNAMIC) }
            }
        }

        Label(stringResource(R.string.language))
        SectionCard {
            RadioRow(stringResource(R.string.lang_system), settings.language == AppLanguage.SYSTEM) {
                vm.set { it.setLanguage(AppLanguage.SYSTEM) }
                (context as? android.app.Activity)?.recreate()
            }
            RadioRow(stringResource(R.string.lang_en), settings.language == AppLanguage.EN) {
                vm.set { it.setLanguage(AppLanguage.EN) }
                (context as? android.app.Activity)?.recreate()
            }
            RadioRow(stringResource(R.string.lang_ru), settings.language == AppLanguage.RU) {
                vm.set { it.setLanguage(AppLanguage.RU) }
                (context as? android.app.Activity)?.recreate()
            }
        }
    }
}

// ---------------------------------------------------------------- connection

@Composable
fun ConnectionSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var stackDialog by remember { mutableStateOf(false) }
    var modeDialog by remember { mutableStateOf(false) }
    var mtu by remember(settings.mtu) { mutableStateOf(settings.mtu.toString()) }
    var testUrl by remember(settings.urlTestUrl) { mutableStateOf(settings.urlTestUrl) }
    var socksPort by remember(settings.socksSharePort) { mutableStateOf(settings.socksSharePort.toString()) }

    SettingsScaffold(title = stringResource(R.string.settings_connection), onBack = onBack) {
        Label(stringResource(R.string.proxy_mode))
        SectionCard {
            ClickRow(
                stringResource(R.string.proxy_mode),
                when (settings.proxyMode) {
                    ProxyMode.GLOBAL -> stringResource(R.string.mode_global)
                    ProxyMode.RULE -> stringResource(R.string.mode_rule)
                },
                onClick = { modeDialog = true },
            )
            SwitchRow(stringResource(R.string.block_ads), null, settings.blockAds) {
                vm.set { s -> s.setBlockAds(it) }
            }
        }

        Label("TUN")
        SectionCard {
            ClickRow(
                stringResource(R.string.tun_stack),
                when (settings.tunStack) {
                    TunStack.MIXED -> stringResource(R.string.stack_mixed)
                    TunStack.SYSTEM -> stringResource(R.string.stack_system)
                    TunStack.GVISOR -> stringResource(R.string.stack_gvisor)
                },
                onClick = { stackDialog = true },
            )
            GraphiteTextField(
                value = mtu,
                onValueChange = { v ->
                    mtu = v.filter { it.isDigit() }.take(5)
                },
                label = stringResource(R.string.mtu),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            TextButton(onClick = { vm.set { s -> s.setMtu(mtu.toIntOrNull() ?: 9000) } }) {
                Text(stringResource(R.string.save), color = Bone)
            }
            SwitchRow(
                stringResource(R.string.sniffer),
                stringResource(R.string.sniffer_desc),
                settings.sniffer,
            ) { vm.set { s -> s.setSniffer(it) } }
        }

        Label(stringResource(R.string.kill_switch))
        SectionCard {
            ClickRow(
                stringResource(R.string.kill_switch),
                stringResource(R.string.kill_switch_hint),
                onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            )
        }

        Label(stringResource(R.string.socks_share))
        SectionCard {
            SwitchRow(
                stringResource(R.string.socks_share),
                stringResource(R.string.socks_share_desc),
                settings.socksShareEnabled,
            ) { enabled ->
                vm.set { s -> s.setSocksShareEnabled(enabled) }
            }
            if (settings.socksShareEnabled) {
                GraphiteTextField(
                    value = socksPort,
                    onValueChange = { v -> socksPort = v.filter { it.isDigit() }.take(5) },
                    label = stringResource(R.string.socks_share_port),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
                TextButton(onClick = {
                    vm.set { s -> s.setSocksPort(socksPort.toIntOrNull() ?: 10808) }
                }) { Text(stringResource(R.string.save), color = Bone) }
            }
        }

        Label(stringResource(R.string.urltest_url))
        SectionCard {
            GraphiteTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                label = stringResource(R.string.urltest_url),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                singleLine = false,
            )
            TextButton(onClick = { vm.set { s -> s.setUrlTestUrl(testUrl) } }) {
                Text(stringResource(R.string.save), color = Bone)
            }
        }
    }

    if (stackDialog) {
        SelectDialog(
            title = stringResource(R.string.tun_stack),
            options =
                listOf(
                    stringResource(R.string.stack_mixed) to TunStack.MIXED,
                    stringResource(R.string.stack_system) to TunStack.SYSTEM,
                    stringResource(R.string.stack_gvisor) to TunStack.GVISOR,
                ),
            selected = settings.tunStack,
            onDismiss = { stackDialog = false },
        ) { chosen -> vm.set { s -> s.setTunStack(chosen) } }
    }
    if (modeDialog) {
        SelectDialog(
            title = stringResource(R.string.proxy_mode),
            options =
                listOf(
                    stringResource(R.string.mode_rule) to ProxyMode.RULE,
                    stringResource(R.string.mode_global) to ProxyMode.GLOBAL,
                ),
            selected = settings.proxyMode,
            onDismiss = { modeDialog = false },
        ) { chosen -> vm.set { s -> s.setProxyMode(chosen) } }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Bone)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Bone)
        }
        Column(
            Modifier
                .fillMaxSize()
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
fun DividerRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp),
    ) {
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
    }
}

@Composable
fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Bone)
    }
}

@Composable
fun <T> SelectDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onDismiss: () -> Unit,
    onChoose: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    RadioRow(label, value == selected) {
                        onChoose(value)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_about), onBack = onBack) {
        SectionCard {
            Column(Modifier.padding(18.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Bone,
                )
                Text(
                    stringResource(R.string.about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Smoke,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Label(stringResource(R.string.about_version))
        SectionCard {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "${stringResource(R.string.about_version)}: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Bone,
                )
            }
        }
        Label(stringResource(R.string.about_cores))
        SectionCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("sing-box 1.14.x — libbox.aar", style = MaterialTheme.typography.bodySmall, color = Smoke)
                Text("Xray-core 26.x — libxray.aar", style = MaterialTheme.typography.bodySmall, color = Smoke)
                Text("AmneziaWG — libawg.aar", style = MaterialTheme.typography.bodySmall, color = Smoke)
            }
        }
    }
}
