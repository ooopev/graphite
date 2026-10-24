package com.oquq.app.ui.add

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oquq.app.R
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.core.parser.SubscriptionParser
import com.oquq.app.core.repo.ProfileRepository
import com.oquq.app.data.SubscriptionUpdateWorker
import com.oquq.app.ui.components.OquqTextField
import com.oquq.app.ui.theme.Bone
import com.oquq.app.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddProfileViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = ProfileRepository.get(app)

    /** 0 = ok(n), -1 = fail */
    val lastImport = MutableStateFlow<Int?>(null)
    val busy = MutableStateFlow(false)

    fun importLinks(text: String) {
        viewModelScope.launch {
            busy.value = true
            val parsed =
                text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .mapNotNull { LinkParser.parse(it) }
                    .distinctBy { it.rawLink }
                    .toList()
            lastImport.value = if (parsed.isEmpty()) -1 else repo.insertParsed(parsed)
            busy.value = false
        }
    }

    fun addSubscription(
        name: String,
        url: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            busy.value = true
            val sub =
                com.oquq.app.core.model.Subscription(
                    name = name.ifBlank { url.substringAfter("//").take(24) },
                    url = url,
                )
            val id = repo.addSubscription(sub)
            val update = SubscriptionParser.fetch(url).getOrNull()
            if (update != null) {
                repo.updateSubscription(SubscriptionParser.applyToSubscription(sub.copy(id = id), update))
                repo.replaceSubscriptionProfiles(id, update.profiles)
            }
            SubscriptionUpdateWorker.schedule(getApplication(), enabled = true, intervalHours = 12)
            busy.value = false
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun buildVlessManual(
        name: String,
        server: String,
        port: String,
        uuid: String,
        sni: String,
        fingerprint: String,
        pbk: String,
        sid: String,
        transport: String,
        path: String,
        host: String,
        flow: String,
        allowInsecure: Boolean,
    ) {
        val link =
            buildVlessUri(
                name,
                server,
                port,
                uuid,
                sni,
                fingerprint,
                pbk,
                sid,
                transport,
                path,
                host,
                flow,
                allowInsecure,
            )
        link?.let { importLinks(it) }
    }

    private fun buildVlessUri(
        name: String,
        server: String,
        port: String,
        uuid: String,
        sni: String,
        fingerprint: String,
        pbk: String,
        sid: String,
        transport: String,
        path: String,
        host: String,
        flow: String,
        allowInsecure: Boolean,
    ): String? {
        if (server.isBlank() || uuid.isBlank() || port.toIntOrNull() == null) return null
        val q =
            buildList {
                add("type=${encode(transport.ifBlank { "tcp" })}")
                if (pbk.isNotBlank()) {
                    add("security=reality")
                    add("pbk=${encode(pbk)}")
                    if (sid.isNotBlank()) add("sid=${encode(sid)}")
                    if (sni.isNotBlank()) add("sni=${encode(sni)}")
                    if (fingerprint.isNotBlank()) add("fp=${encode(fingerprint)}")
                    if (flow.isNotBlank()) add("flow=${encode(flow)}")
                } else {
                    add("security=none")
                    if (sni.isNotBlank()) add("sni=${encode(sni)}")
                    if (fingerprint.isNotBlank()) add("fp=${encode(fingerprint)}")
                }
                if (path.isNotBlank()) add("path=${encode(path)}")
                if (host.isNotBlank()) add("host=${encode(host)}")
                if (allowInsecure) add("allowInsecure=1")
            }.joinToString("&")
        return "vless://${encode(uuid)}@${encode(server)}:${port.trim()}?$q#${encode(name.ifBlank { server })}"
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun AddProfileScreen(
    initialLink: String? = null,
    onBack: () -> Unit,
    vm: AddProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lastImport by vm.lastImport.collectAsState()
    val busy by vm.busy.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf(initialLink ?: "") }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }

    // Deep-link import (vless://… opened from browser/messenger)
    LaunchedEffect(initialLink) {
        if (!initialLink.isNullOrBlank()) vm.importLinks(initialLink)
    }

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { vm.importLinks(it) }
        }
    val cameraPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                        setPrompt("")
                    },
                )
            }
        }

    val importMessage =
        when (lastImport) {
            null -> null
            -1 -> stringResource(R.string.import_fail)
            else -> stringResource(R.string.import_n, lastImport ?: 0)
        }

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
            Text(
                stringResource(R.string.add_profile),
                style = MaterialTheme.typography.titleLarge,
                color = Bone,
            )
        }

        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Bone,
            divider = {},
        ) {
            listOf(
                R.string.tab_link,
                R.string.tab_subscription,
                R.string.tab_manual,
                R.string.tab_qr,
            ).forEachIndexed { i, res ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(stringResource(res)) },
                    selectedContentColor = Bone,
                    unselectedContentColor = Smoke,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    OquqTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = stringResource(R.string.field_link),
                        placeholder = stringResource(R.string.field_link_hint),
                        singleLine = false,
                        minLines = 4,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButtonCompact(stringResource(R.string.paste_from_clipboard)) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            link = cm.primaryClip
                                ?.getItemAt(0)
                                ?.text
                                ?.toString() ?: link
                        }
                        Spacer(Modifier.width(6.dp))
                        ActionButton(stringResource(R.string.add), busy) { vm.importLinks(link) }
                    }
                }

                1 -> {
                    OquqTextField(subName, { subName = it }, stringResource(R.string.field_sub_name))
                    OquqTextField(subUrl, { subUrl = it }, stringResource(R.string.field_sub_url))
                    ActionButton(stringResource(R.string.add), busy) {
                        vm.addSubscription(subName, subUrl) { onBack() }
                    }
                }

                2 -> {
                    ManualVlessForm(vm, busy)
                }

                3 -> {
                    Text(
                        stringResource(R.string.qr_scan),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Bone,
                    )
                    ActionButton(stringResource(R.string.qr_scan), busy) {
                        cameraPermission.launch(android.Manifest.permission.CAMERA)
                    }
                    Text(
                        stringResource(R.string.paste_from_clipboard),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Smoke,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (importMessage != null) {
                Text(
                    importMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lastImport == -1) com.oquq.app.ui.theme.ErrorMuted else Bone,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ManualVlessForm(
    vm: AddProfileViewModel,
    busy: Boolean,
) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var uuid by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }
    var fp by remember { mutableStateOf("chrome") }
    var pbk by remember { mutableStateOf("") }
    var sid by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("tcp") }
    var path by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("xtls-rprx-vision") }
    var insecure by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OquqTextField(name, { name = it }, stringResource(R.string.field_name))
        OquqTextField(server, { server = it }, stringResource(R.string.field_server))
        OquqTextField(port, { port = it }, stringResource(R.string.field_port))
        OquqTextField(uuid, { uuid = it }, stringResource(R.string.field_id))
        LabelSmall(stringResource(R.string.field_transport))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("tcp", "ws", "grpc", "xhttp").forEach { t ->
                if (t == transport) {
                    Button(onClick = {}) { Text(t) }
                } else {
                    TextButton(onClick = {
                        transport = t
                        flow = ""
                    }) { Text(t, color = Smoke) }
                }
            }
        }
        if (transport == "ws" || transport == "xhttp") {
            OquqTextField(path, { path = it }, stringResource(R.string.field_path))
            OquqTextField(host, { host = it }, stringResource(R.string.field_host))
        }
        if (transport == "grpc") {
            OquqTextField(path, { path = it }, stringResource(R.string.field_path))
        }
        OquqTextField(sni, { sni = it }, stringResource(R.string.field_sni))
        OquqTextField(fp, { fp = it }, stringResource(R.string.field_fingerprint))
        OquqTextField(pbk, { pbk = it }, stringResource(R.string.field_pbk))
        OquqTextField(sid, { sid = it }, stringResource(R.string.field_sid))
        com.oquq.app.ui.components.SwitchRow(
            title = stringResource(R.string.allow_insecure),
            checked = insecure,
            onCheckedChange = { insecure = it },
        )
        ActionButton(stringResource(R.string.add), busy) {
            vm.buildVlessManual(
                name,
                server,
                port,
                uuid,
                sni,
                fp,
                pbk,
                sid,
                transport,
                path,
                host,
                flow,
                insecure,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        colors =
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Bone,
                contentColor = com.oquq.app.ui.theme.Black,
            ),
    ) { Text(text) }
}

@Composable
private fun OutlinedButtonCompact(
    text: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.OutlinedButton(onClick = onClick) { Text(text, color = Bone) }
}

@Composable
private fun LabelSmall(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Smoke,
        modifier = Modifier.padding(top = 4.dp),
    )
}
