package com.graphite.app.ui.apps

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.graphite.app.R
import com.graphite.app.data.AppSettings
import com.graphite.app.data.PerAppMode
import com.graphite.app.data.SettingsRepository
import com.graphite.app.ui.components.GraphiteTextField
import com.graphite.app.ui.components.SectionCard
import com.graphite.app.ui.components.SwitchRow
import com.graphite.app.ui.settings.SettingsScaffold
import com.graphite.app.ui.theme.Bone
import com.graphite.app.ui.theme.Smoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
)

class AppsRouteViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val entries =
                withContext(Dispatchers.IO) {
                    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    pm
                        .queryIntentActivities(launcher, 0)
                        .asSequence()
                        .map { it.activityInfo.applicationInfo }
                        .distinctBy { it.packageName }
                        .map { info ->
                            AppEntry(
                                packageName = info.packageName,
                                label = info.loadLabel(pm).toString(),
                                icon = info.loadIcon(pm),
                                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            )
                        }.sortedBy { it.label.lowercase() }
                        .toList()
                }
            apps.value = entries
            loaded.value = true
        }
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setPerAppEnabled(v) }

    fun setMode(m: PerAppMode) = viewModelScope.launch { repo.setPerAppMode(m) }

    fun togglePackage(pkg: String) =
        viewModelScope.launch {
            val current = repo.current().perAppPackages
            repo.setPerAppPackages(if (pkg in current) current - pkg else current + pkg)
        }
}

@Composable
fun AppsRouteScreen(
    onBack: () -> Unit,
    vm: AppsRouteViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()
    var query by remember { mutableStateOf("") }

    SettingsScaffold(title = stringResource(R.string.settings_apps), onBack = onBack, scroll = false) {
        SectionCard {
            SwitchRow(
                stringResource(R.string.per_app_enable),
                null,
                settings.perAppEnabled,
            ) { vm.setEnabled(it) }
        }

        if (settings.perAppEnabled) {
            SectionCard {
                com.graphite.app.ui.settings.RadioRow(
                    stringResource(R.string.mode_include),
                    settings.perAppMode == PerAppMode.INCLUDE,
                ) { vm.setMode(PerAppMode.INCLUDE) }
                com.graphite.app.ui.settings.RadioRow(
                    stringResource(R.string.mode_exclude),
                    settings.perAppMode == PerAppMode.EXCLUDE,
                ) { vm.setMode(PerAppMode.EXCLUDE) }
            }
            Text(
                stringResource(R.string.selected_n, settings.perAppPackages.size),
                style = MaterialTheme.typography.labelMedium,
                color = Smoke,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            )
            GraphiteTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.apps_search_hint),
                modifier = Modifier.padding(top = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                val visible =
                    apps.filter {
                        query.isBlank() || it.label.contains(query, true) ||
                            it.packageName.contains(query, true)
                    }
                items(visible, key = { it.packageName }) { entry ->
                    AppRow(
                        entry = entry,
                        checked = entry.packageName in settings.perAppPackages,
                        onToggle = { vm.togglePackage(entry.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small, // squared
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(entry.icon, Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Smoke,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

/** Drawable → ImageBitmap for any icon type (incl. adaptive icons). */
@Composable
private fun AppIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier,
) {
    val bitmap =
        remember(drawable) {
            val size = 96
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}
