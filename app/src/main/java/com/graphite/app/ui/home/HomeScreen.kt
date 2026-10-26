package com.graphite.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.graphite.app.R
import com.graphite.app.core.model.Profile
import com.graphite.app.ui.components.BoilingPixels
import com.graphite.app.ui.components.OmarchySpiral
import com.graphite.app.ui.components.GraphiteTextField
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.BorderStrong
import com.graphite.app.ui.theme.ErrorMuted
import com.graphite.app.ui.theme.OutlineDark
import com.graphite.app.ui.theme.Smoke
import com.graphite.app.ui.theme.SuccessMuted
import com.graphite.app.ui.theme.Surface01
import com.graphite.app.ui.theme.TextSecondary
import com.graphite.app.ui.util.formatSpeed
import com.graphite.app.vpn.VpnStatus

/**
 * Home in the omarchy.org language: hairline-divided sections, mono
 * eyebrows, squared everything. Top — wordmark + settings; center — the
 * spiral square; bottom — a grid of server squares.
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
    val context = androidx.compose.ui.platform.LocalContext.current

    var menuProfile by remember { mutableStateOf<Profile?>(null) }
    var renameTarget by remember { mutableStateOf<Profile?>(null) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---- header band: owns the status-bar zone, hairline at the bottom ----
        Box(Modifier.fillMaxWidth().background(Surface01)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            OmarchySpiral(color = Bone, sizeDp = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = Bone,
            )
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, null, tint = Smoke)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Add, null, tint = TextSecondary) },
                        text = { Text(stringResource(R.string.add_profile)) },
                        onClick = {
                            menuOpen = false
                            onAddProfile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.test_all)) },
                        onClick = {
                            menuOpen = false
                            vm.testAll()
                        },
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Smoke)
            }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineDark))
        }

        // ---- power zone ----
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val statusText =
                when (vpnState.status) {
                    VpnStatus.IDLE -> vpnState.error ?: stringResource(R.string.status_idle)
                    VpnStatus.CONNECTING -> stringResource(R.string.status_connecting)
                    VpnStatus.CONNECTED -> vpnState.profileName ?: "VPN"
                    VpnStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                }.uppercase()
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color =
                    when (vpnState.status) {
                        VpnStatus.CONNECTED -> Bone
                        VpnStatus.IDLE -> if (vpnState.error != null) ErrorMuted else Smoke
                        else -> Smoke
                    },
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(26.dp))
            PowerButton(status = vpnState.status, onToggle = { vm.toggle() })
            Spacer(Modifier.height(26.dp))
            if (vpnState.status == VpnStatus.CONNECTED) {
                Row {
                    Text(
                        "▼ " + formatSpeed(speed.second),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        "▲ " + formatSpeed(speed.first),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.press_to_connect),
                    style = MaterialTheme.typography.labelLarge,
                    color = Smoke,
                )
            }
        }

        // ---- servers: mono eyebrow + hairline, like omarchy section heads ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.servers).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Smoke,
                letterSpacing = 1.5.sp,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineDark))

        if (profiles.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OmarchySpiral(color = Smoke.copy(alpha = 0.4f), sizeDp = 64.dp)
                Spacer(Modifier.height(6.dp))
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
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, BorderStrong),
                ) {
                    Text(stringResource(R.string.add_profile), color = Bone)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 14.dp,
                            bottom = 24.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ServerSquare(
                            profile = profile,
                            isSelected = profile.id == selected?.id,
                            isLive =
                                vpnState.status == VpnStatus.CONNECTED &&
                                    vpnState.profileId == profile.id,
                            isTesting = profile.id in testing,
                            onClick = {
                                vm.select(profile)
                                if (vpnState.status == VpnStatus.CONNECTED &&
                                    vpnState.profileId != profile.id
                                ) {
                                    vm.selectAndConnect(profile)
                                }
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
                onClick = {
                    vm.test(p)
                    dismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    renameTarget = p
                    dismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_link)) },
                onClick = {
                    editTarget = p
                    dismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = {
                    val send =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
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
                onClick = {
                    deleteTarget = p
                    dismiss()
                },
            )
        }
    }

    renameTarget?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                GraphiteTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.field_name))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(p, name)
                    renameTarget = null
                }) {
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
                GraphiteTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = stringResource(R.string.field_link),
                    singleLine = false,
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.replaceLink(p, link)
                    editTarget = null
                }) {
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
                TextButton(onClick = {
                    vm.delete(p)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = ErrorMuted)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A server as an omarchy square: hairline frame, 1:1 aspect, mono meta.
 *  The live one boils: its lower half is water with fast square bubbles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerSquare(
    profile: Profile,
    isSelected: Boolean,
    isLive: Boolean,
    isTesting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small, // 0dp
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Bone else OutlineDark,
            ),
        modifier =
            Modifier
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- upper half: identity ----
            Column(Modifier.fillMaxWidth().weight(1f).padding(14.dp)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Bone else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ---- lower half: meta, or boiling water when live ----
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (isLive) {
                    BoilingPixels(
                        modifier = Modifier.fillMaxSize(),
                        slotCount = 26,
                        durationMs = 800,
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                ) {
                    Text(
                        "${profile.server}:${profile.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLive) Bone else Smoke,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.protocol.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLive) TextSecondary else TextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        latencyText(profile.latencyMs, isTesting),
                        style = MaterialTheme.typography.labelSmall,
                        color = latencyColor(profile.latencyMs, isTesting),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun latencyText(
    latencyMs: Int?,
    isTesting: Boolean,
): String =
    when {
        isTesting -> "…"
        latencyMs == null -> "—"
        else -> "${latencyMs}ms"
    }

@Composable
private fun latencyColor(
    latencyMs: Int?,
    isTesting: Boolean,
) = when {
    isTesting -> Smoke
    latencyMs == null -> Smoke
    latencyMs < 150 -> SuccessMuted
    latencyMs < 400 -> Smoke
    else -> ErrorMuted
}
