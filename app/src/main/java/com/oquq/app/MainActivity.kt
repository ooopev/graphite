package com.oquq.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oquq.app.core.parser.LinkParser
import com.oquq.app.core.repo.ProfileRepository
import com.oquq.app.data.SettingsRepository
import com.oquq.app.ui.add.AddProfileScreen
import com.oquq.app.ui.apps.AppsRouteScreen
import com.oquq.app.ui.home.HomeScreen
import com.oquq.app.ui.settings.AboutScreen
import com.oquq.app.ui.settings.AppearanceSettingsScreen
import com.oquq.app.ui.settings.ConnectionSettingsScreen
import com.oquq.app.ui.settings.SettingsScreen
import com.oquq.app.ui.subs.SubscriptionsScreen
import com.oquq.app.ui.theme.OquqTheme
import com.oquq.app.vpn.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** vless://… link arriving via ACTION_VIEW (launch or onNewIntent). */
    private var pendingLink by mutableStateOf<String?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: Context) {
        // Per-app locale without AppCompat: quick SharedPreferences mirror of the
        // DataStore setting (written in onCreate), applied via configuration wrap.
        val lang =
            newBase
                .getSharedPreferences("oquq_locale", Context.MODE_PRIVATE)
                .getString("lang", null)
        super.attachBaseContext(lang?.let { applyLanguage(newBase, it) } ?: newBase)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        captureDeepLink(intent)

        val settingsRepo = SettingsRepository(this)
        lifecycleScope.launch {
            settingsRepo.settings.collect { s ->
                val tag =
                    when (s.language) {
                        com.oquq.app.data.AppLanguage.EN -> "en"
                        com.oquq.app.data.AppLanguage.RU -> "ru"
                        com.oquq.app.data.AppLanguage.SYSTEM -> null
                    }
                getSharedPreferences("oquq_locale", Context.MODE_PRIVATE)
                    .edit()
                    .putString("lang", tag)
                    .apply()
            }
        }

        setContent {
            val settings by settingsRepo.settings.collectAsState(
                initial =
                    com.oquq.app.data
                        .AppSettings(),
            )
            OquqTheme(theme = settings.theme) {
                OquqNavHost(
                    deepLink = pendingLink,
                    onDeepLinkConsumed = { pendingLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        val known =
            setOf(
                "vless",
                "vmess",
                "trojan",
                "ss",
                "hysteria2",
                "hy2",
                "tuic",
                "socks",
                "http",
                "vpn",
                "oquq",
            )
        if (scheme in known && LinkParser.parse(data.toString()) != null) {
            pendingLink = data.toString()
        }
    }

    private fun applyLanguage(
        base: Context,
        tag: String,
    ): Context {
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return base.createConfigurationContext(config)
    }
}

@androidx.compose.runtime.Composable
private fun OquqNavHost(
    deepLink: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            LinkParser.parse(deepLink)?.let { profile ->
                launch(Dispatchers.IO) {
                    ProfileRepository.get(context).upsert(profile)
                }
            }
            onDeepLinkConsumed()
            nav.navigate("add?link=${android.net.Uri.encode(deepLink)}")
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenSettings = { nav.navigate("settings") },
                onAddProfile = { nav.navigate("add") },
            )
        }
        composable(
            route = "add?link={link}",
            arguments = listOf(navArgument("link") { defaultValue = "" }),
        ) { entry ->
            val link = entry.arguments?.getString("link").orEmpty()
            AddProfileScreen(
                initialLink = link.ifBlank { null },
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAppearance = { nav.navigate("appearance") },
                onOpenConnection = { nav.navigate("connection") },
                onOpenApps = { nav.navigate("apps") },
                onOpenSubscriptions = { nav.navigate("subs") },
                onOpenAbout = { nav.navigate("about") },
            )
        }
        composable("appearance") { AppearanceSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("connection") { ConnectionSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("apps") { AppsRouteScreen(onBack = { nav.popBackStack() }) }
        composable("subs") {
            SubscriptionsScreen(
                onBack = { nav.popBackStack() },
                onAddSubscription = { nav.navigate("add") },
            )
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
