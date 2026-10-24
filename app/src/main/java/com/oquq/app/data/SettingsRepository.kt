package com.oquq.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oquq_settings")

enum class ThemeMode { BLACK, GRAY, DYNAMIC }

enum class AppLanguage { SYSTEM, EN, RU }

enum class ProxyMode { GLOBAL, RULE }

enum class TunStack { MIXED, SYSTEM, GVISOR }

enum class PerAppMode { INCLUDE, EXCLUDE }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.BLACK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val proxyMode: ProxyMode = ProxyMode.RULE,
    val blockAds: Boolean = false,
    val tunStack: TunStack = TunStack.MIXED,
    val mtu: Int = 9000,
    val sniffer: Boolean = true,
    val urlTestUrl: String = "https://www.gstatic.com/generate_204",
    val socksShareEnabled: Boolean = false,
    val socksSharePort: Int = 10808,
    val socksShareAllowLan: Boolean = false,
    val perAppEnabled: Boolean = false,
    val perAppMode: PerAppMode = PerAppMode.EXCLUDE,
    val perAppPackages: Set<String> = emptySet(),
    val autoStartOnBoot: Boolean = false,
    val subsAutoUpdate: Boolean = true,
    val subsIntervalHours: Int = 12,
    val selectedProfileId: Long = -1L,
)

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val PROXY_MODE = stringPreferencesKey("proxy_mode")
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val TUN_STACK = stringPreferencesKey("tun_stack")
        val MTU = intPreferencesKey("mtu")
        val SNIFFER = booleanPreferencesKey("sniffer")
        val URL_TEST = stringPreferencesKey("url_test")
        val SOCKS_SHARE = booleanPreferencesKey("socks_share")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val SOCKS_LAN = booleanPreferencesKey("socks_lan")
        val PER_APP = booleanPreferencesKey("per_app")
        val PER_APP_MODE = stringPreferencesKey("per_app_mode")
        val PER_APP_PKGS = stringSetPreferencesKey("per_app_pkgs")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val SUBS_AUTO = booleanPreferencesKey("subs_auto")
        val SUBS_HOURS = intPreferencesKey("subs_hours")
        val SELECTED = longPreferencesKey("selected_profile")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings =
        context.dataStore.data
            .first()
            .toSettings()

    private fun Preferences.toSettings() =
        AppSettings(
            theme = enum(ThemeMode.BLACK, Keys.THEME),
            language = enum(AppLanguage.SYSTEM, Keys.LANGUAGE),
            proxyMode = enum(ProxyMode.RULE, Keys.PROXY_MODE),
            blockAds = this[Keys.BLOCK_ADS] ?: false,
            tunStack = enum(TunStack.MIXED, Keys.TUN_STACK),
            mtu = this[Keys.MTU] ?: 9000,
            sniffer = this[Keys.SNIFFER] ?: true,
            urlTestUrl = this[Keys.URL_TEST] ?: "https://www.gstatic.com/generate_204",
            socksShareEnabled = this[Keys.SOCKS_SHARE] ?: false,
            socksSharePort = this[Keys.SOCKS_PORT] ?: 10808,
            socksShareAllowLan = this[Keys.SOCKS_LAN] ?: false,
            perAppEnabled = this[Keys.PER_APP] ?: false,
            perAppMode = enum(PerAppMode.EXCLUDE, Keys.PER_APP_MODE),
            perAppPackages = this[Keys.PER_APP_PKGS] ?: emptySet(),
            autoStartOnBoot = this[Keys.AUTO_START] ?: false,
            subsAutoUpdate = this[Keys.SUBS_AUTO] ?: true,
            subsIntervalHours = this[Keys.SUBS_HOURS] ?: 12,
            selectedProfileId = this[Keys.SELECTED] ?: -1L,
        )

    private inline fun <reified T : Enum<T>> Preferences.enum(
        default: T,
        key: Preferences.Key<String>,
    ): T {
        val stored = this[key] ?: return default
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(default)
    }

    suspend fun setTheme(v: ThemeMode) = edit { it[Keys.THEME] = v.name }

    suspend fun setLanguage(v: AppLanguage) = edit { it[Keys.LANGUAGE] = v.name }

    suspend fun setProxyMode(v: ProxyMode) = edit { it[Keys.PROXY_MODE] = v.name }

    suspend fun setBlockAds(v: Boolean) = edit { it[Keys.BLOCK_ADS] = v }

    suspend fun setTunStack(v: TunStack) = edit { it[Keys.TUN_STACK] = v.name }

    suspend fun setMtu(v: Int) = edit { it[Keys.MTU] = v }

    suspend fun setSniffer(v: Boolean) = edit { it[Keys.SNIFFER] = v }

    suspend fun setUrlTestUrl(v: String) = edit { it[Keys.URL_TEST] = v }

    suspend fun setSocksShareEnabled(v: Boolean) = edit { it[Keys.SOCKS_SHARE] = v }

    suspend fun setSocksPort(v: Int) = edit { it[Keys.SOCKS_PORT] = v }

    suspend fun setSocksAllowLan(v: Boolean) = edit { it[Keys.SOCKS_LAN] = v }

    suspend fun setPerAppEnabled(v: Boolean) = edit { it[Keys.PER_APP] = v }

    suspend fun setPerAppMode(v: PerAppMode) = edit { it[Keys.PER_APP_MODE] = v.name }

    suspend fun setPerAppPackages(v: Set<String>) = edit { it[Keys.PER_APP_PKGS] = v }

    suspend fun setAutoStart(v: Boolean) = edit { it[Keys.AUTO_START] = v }

    suspend fun setSubsAutoUpdate(v: Boolean) = edit { it[Keys.SUBS_AUTO] = v }

    suspend fun setSubsIntervalHours(v: Int) = edit { it[Keys.SUBS_HOURS] = v }

    suspend fun setSelectedProfile(v: Long) = edit { it[Keys.SELECTED] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
