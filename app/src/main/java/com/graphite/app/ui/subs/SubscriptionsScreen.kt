package com.graphite.app.ui.subs

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.graphite.app.R
import com.graphite.app.core.model.Subscription
import com.graphite.app.core.parser.SubscriptionParser
import com.graphite.app.core.repo.ProfileRepository
import com.graphite.app.data.AppSettings
import com.graphite.app.data.SettingsRepository
import com.graphite.app.data.SubscriptionUpdateWorker
import com.graphite.app.ui.settings.SettingsScaffold
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.ErrorMuted
import com.graphite.app.ui.theme.Smoke
import com.graphite.app.ui.util.formatBytes
import com.graphite.app.ui.util.formatEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class SubscriptionsViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = ProfileRepository.get(app)
    private val settingsRepo = SettingsRepository(app)

    val settings =
        settingsRepo.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val subscriptions =
        repo
            .subscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updating = MutableStateFlow<Set<Long>>(emptySet())

    fun updateNow(sub: Subscription) {
        updating.value = updating.value + sub.id
        viewModelScope.launch {
            SubscriptionParser
                .fetch(sub.url)
                .onSuccess { update ->
                    repo.updateSubscription(SubscriptionParser.applyToSubscription(sub, update))
                    repo.replaceSubscriptionProfiles(sub.id, update.profiles)
                }
            updating.value = updating.value - sub.id
        }
    }

    fun delete(sub: Subscription) {
        viewModelScope.launch { repo.deleteSubscriptionCascade(sub) }
    }

    fun setAuto(
        sub: Subscription,
        auto: Boolean,
    ) {
        viewModelScope.launch { repo.updateSubscription(sub.copy(autoUpdate = auto)) }
    }

    fun setGlobalAuto(
        enabled: Boolean,
        hours: Int,
    ) {
        viewModelScope.launch {
            settingsRepo.setSubsAutoUpdate(enabled)
            SubscriptionUpdateWorker.schedule(getApplication(), enabled, hours)
        }
    }
}

@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onAddSubscription: () -> Unit,
    vm: SubscriptionsViewModel = viewModel(),
) {
    val subs by vm.subscriptions.collectAsState()
    val settings by vm.settings.collectAsState()
    val updating by vm.updating.collectAsState()
    var deleteTarget by remember { mutableStateOf<Subscription?>(null) }

    SettingsScaffold(title = stringResource(R.string.settings_subscriptions), onBack = onBack) {
        com.graphite.app.ui.components.SectionCard {
            com.graphite.app.ui.components.SwitchRow(
                stringResource(R.string.subs_auto_update),
                null,
                settings.subsAutoUpdate,
            ) { vm.setGlobalAuto(it, settings.subsIntervalHours) }
        }

        if (subs.isEmpty()) {
            Text(
                stringResource(R.string.no_servers_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Smoke,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp),
            )
            TextButton(onClick = onAddSubscription) { Text(stringResource(R.string.add), color = Bone) }
        }

        subs.forEach { sub ->
            SubscriptionCard(
                sub = sub,
                isUpdating = sub.id in updating,
                onUpdate = { vm.updateNow(sub) },
                onToggleAuto = { vm.setAuto(sub, it) },
                onDelete = { deleteTarget = sub },
            )
        }
    }

    deleteTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.sub_delete_q)) },
            text = { Text(sub.name) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(sub)
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

@Composable
private fun SubscriptionCard(
    sub: Subscription,
    isUpdating: Boolean,
    onUpdate: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small, // squared
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                sub.name,
                style = MaterialTheme.typography.titleMedium,
                color = Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub.url,
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub.lastUpdate?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                } ?: stringResource(R.string.subs_updated_never),
                style = MaterialTheme.typography.bodySmall,
                color = Smoke,
            )
            if (sub.total > 0) {
                Text(
                    stringResource(
                        R.string.subs_traffic,
                        formatBytes(sub.upload + sub.download),
                        formatBytes(sub.total),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                )
            }
            formatEpochSeconds(sub.expire)?.let {
                Text(
                    stringResource(R.string.subs_expires, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                )
            }
            Row {
                TextButton(onClick = onUpdate, enabled = !isUpdating) {
                    Text(
                        if (isUpdating) {
                            stringResource(R.string.status_connecting)
                        } else {
                            stringResource(R.string.subs_update_now)
                        },
                        color = Bone,
                    )
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = ErrorMuted)
                }
            }
        }
    }
}
