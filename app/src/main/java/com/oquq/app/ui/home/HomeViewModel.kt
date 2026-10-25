package com.oquq.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oquq.app.core.model.Profile
import com.oquq.app.core.net.LatencyTester
import com.oquq.app.core.repo.ProfileRepository
import com.oquq.app.data.AppSettings
import com.oquq.app.data.SettingsRepository
import com.oquq.app.vpn.VpnController
import com.oquq.app.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = ProfileRepository.get(app)
    private val settingsRepo = SettingsRepository(app)

    val profiles: StateFlow<List<Profile>> =
        repo
            .profiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val vpnState: StateFlow<VpnState> = VpnController.state

    val selected: StateFlow<Profile?> =
        combine(repo.profiles(), settingsRepo.settings) { list, s ->
            list.firstOrNull { it.id == s.selectedProfileId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---- live speed: 1 Hz deltas of the cumulative counters ----
    private val _speed = MutableStateFlow(0L to 0L) // up, down bytes/s
    val speed: StateFlow<Pair<Long, Long>> = _speed.asStateFlow()
    private var lastTick = 0L
    private var lastUp = 0L
    private var lastDown = 0L

    val testing: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())

    init {
        viewModelScope.launch {
            vpnState.collect { s ->
                val now = System.currentTimeMillis()
                if (s.status == com.oquq.app.vpn.VpnStatus.CONNECTED && lastTick > 0) {
                    val dt = (now - lastTick) / 1000.0
                    // Ignore emission bursts (multiple state writes within one
                    // stats tick) — their tiny dt produces wild spikes.
                    if (dt >= 0.2) {
                        val du = s.upBytes - lastUp
                        val dd = s.downBytes - lastDown
                        // The core resets its cumulative counters on reload;
                        // a negative delta means "counters restarted", not
                        // negative traffic.
                        _speed.value =
                            (if (du > 0) (du / dt).toLong() else 0L) to
                                (if (dd > 0) (dd / dt).toLong() else 0L)
                    }
                } else {
                    _speed.value = 0L to 0L
                }
                lastTick = now
                lastUp = s.upBytes
                lastDown = s.downBytes
            }
        }
    }

    fun toggle() {
        VpnController.toggle(getApplication(), selected.value)
    }

    fun select(profile: Profile) {
        viewModelScope.launch { settingsRepo.setSelectedProfile(profile.id) }
    }

    fun selectAndConnect(profile: Profile) {
        viewModelScope.launch {
            settingsRepo.setSelectedProfile(profile.id)
            VpnController.connect(getApplication(), profile)
        }
    }

    fun delete(profile: Profile) {
        viewModelScope.launch { repo.delete(profile) }
    }

    fun rename(
        profile: Profile,
        name: String,
    ) {
        viewModelScope.launch { repo.update(profile.copy(name = name.ifBlank { profile.name })) }
    }

    fun replaceLink(
        profile: Profile,
        newLink: String,
        onSuccess: () -> Unit = {},
    ) {
        val parsed =
            com.oquq.app.core.parser.LinkParser
                .parse(newLink) ?: return
        viewModelScope.launch {
            repo.update(parsed.copy(id = profile.id, name = parsed.name.ifBlank { profile.name }))
            onSuccess()
        }
    }

    fun test(profile: Profile) {
        testing.value = testing.value + profile.id
        viewModelScope.launch {
            val ms = LatencyTester.test(profile)
            repo.update(
                profile.copy(
                    latencyMs = ms,
                    lastTestedAt = System.currentTimeMillis(),
                ),
            )
            testing.value = testing.value - profile.id
        }
    }

    fun testAll() {
        val all = profiles.value
        testing.value = all.map { it.id }.toSet()
        viewModelScope.launch {
            LatencyTester.testAll(repo, all)
            testing.value = emptySet()
        }
    }
}
