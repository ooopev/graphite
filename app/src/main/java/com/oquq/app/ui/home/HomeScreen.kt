package com.oquq.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oquq.app.R
import com.oquq.app.core.model.Profile
import com.oquq.app.ui.components.OquqTextField
import com.oquq.app.ui.theme.Bone
import com.oquq.app.ui.theme.ErrorMuted
import com.oquq.app.ui.theme.Smoke
import com.oquq.app.ui.theme.SuccessMuted
import com.oquq.app.ui.util.formatSpeed
import com.oquq.app.vpn.VpnStatus

/**
 * Home, exactly as specified:
 * top — logo and the ONLY settings entry; center — the big power button;
 * bottom — every added server.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddProfile: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val profiles by vm.profiles.collectAsState()
    val selected by vm.selected.collectAsState()
    val vpnState by vm.vpnState.collectAsState()
    val speed by vm.speed.collectAsState()
    val testing by vm.testing.collectAsState()
    val context = LocalContext.current

    var menuProfile by remember { mutableStateOf<Profile?>(null) }
    var renameTarget by remember { mutableStateOf<Profile?>(null) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
        // ---- top bar: logo + settings, nothing else ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = Bone,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Smoke)
            }
        }

        // ---- power zone ----
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val statusText = when (vpnState.status) {
                VpnStatus.IDLE -> vpnState.error ?: stringResource(R.string.status_idle)
                VpnStatus.CONNECTING -> stringResource(R.string.status_connecting)
                VpnStatus.CONNECTED -> stringResource(
                    R.string.connected_to, vpnState.profileName ?: "VPN",
                )
                VpnStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
            }
            Text(
                statusText,
                style = MaterialTheme.typography.titleMedium,
                color = when (vpnState.status) {
                    VpnStatus.CONNECTED -> Bone
                    VpnStatus.IDLE -> if (vpnState.error != null) ErrorMuted else Smoke
                    else -> Smoke
                },
            )
            Spacer(Modifier.height(20.dp))
            PowerButton(status = vpnState.status, onToggle = { vm.toggle() })
            Spacer(Modifier.height(18.dp))
            if (vpnState.status == VpnStatus.CONNECTED) {
                Text(
                    formatSpeed(speed.second) + " ▼   " + formatSpeed(speed.first) + " ▲",
                    style = MaterialTheme.typography.labelLarge,
                    color = Smoke,
                )
            } else {
                Text(
                    stringResource(R.string.press_to_connect),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        // ---- servers ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.servers).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Smoke,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.testAll() }) {
                Text(stringResource(R.string.test_all), color = Smoke)
            }
            IconButton(onClick = onAddProfile) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_profile), tint = Bone)
            }
        }

        if (profiles.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.no_servers), style = MaterialTheme.typography.titleMedium, color = Bone)
                Text(
                    stringResource(R.string.no_servers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onAddProfile,
                    modifier = Modifier.padding(top = 12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(stringResource(R.string.add_profile), color = Bone)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ServerCard(
                        profile = profile,
                        isSelected = profile.id == selected?.id,
                        isTesting = profile.id in testing,
                        onClick = {
                            vm.select(profile)
                            if (vpnState.status == VpnStatus.CONNECTED &&
                                vpnState.profileId != profile.id
                            ) vm.selectAndConnect(profile)
                        },
                        onLongClick = { menuProfile = profile },
                    )
                }
            }
        }
    }

    // ---- profile action menu ----
    menuProfile?.let { p ->
        val dismiss = { menuProfile = null }
        DropdownMenu(expanded = true, onDismissRequest = dismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.test)) },
                onClick = { vm.test(p); dismiss() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = { renameTarget = p; dismiss() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_link)) },
                onClick = { editTarget = p; dismiss() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, p.rawLink)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(send, null),
                    )
                    dismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = ErrorMuted) },
                onClick = { deleteTarget = p; dismiss() },
            )
        }
    }

    renameTarget?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OquqTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.field_name))
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(p, name); renameTarget = null }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    editTarget?.let { p ->
        var link by remember { mutableStateOf(p.rawLink) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.edit_link)) },
            text = {
                OquqTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = stringResource(R.string.field_link),
                    singleLine = false,
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.replaceLink(p, link); editTarget = null }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_profile_q)) },
            text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = { vm.delete(p); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = ErrorMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    } // Box root
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    profile: Profile,
    isSelected: Boolean,
    isTesting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Bone else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Bone else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${profile.server}:${profile.port}  ·  ${profile.protocol.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            LatencyBadge(profile.latencyMs, isTesting)
        }
    }
}

@Composable
private fun LatencyBadge(latencyMs: Int?, isTesting: Boolean) {
    val (text, color) = when {
        isTesting -> stringResource(R.string.latency_testing) to Smoke
        latencyMs == null -> "—" to Smoke
        latencyMs < 150 -> stringResource(R.string.latency_ms, latencyMs) to SuccessMuted
        latencyMs < 400 -> stringResource(R.string.latency_ms, latencyMs) to Smoke
        else -> stringResource(R.string.latency_ms, latencyMs) to ErrorMuted
    }
    Surface(shape = CircleShape, color = Color.Transparent) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(4.dp),
        )
    }
}
