package dev.nikhil.cmfsocialremote.data

import android.content.Context
import dev.nikhil.cmfsocialremote.model.AppConfig
import dev.nikhil.cmfsocialremote.model.ContentSurface
import dev.nikhil.cmfsocialremote.model.LikeStrategy
import dev.nikhil.cmfsocialremote.model.SocialApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val activeApp: SocialApp = SocialApp.INSTAGRAM,
    /** 0 = relaxed (forgiving timing), 1 = quick. */
    val sensitivity: Float = 0.5f,
    val phoneHaptics: Boolean = true,
    val autoReconnect: Boolean = true,
    /** Open a direct BLE (GATT) session for battery / command channel when the remote starts. Off by default. */
    val directBleSession: Boolean = false,
    val autoOpenApp: Boolean = true,
    val preferredWatchAddress: String? = null,
    val preferredWatchName: String? = null,
    val onboardingDone: Boolean = false,
    val appConfigs: Map<SocialApp, AppConfig> = emptyMap(),
) {
    fun configFor(app: SocialApp): AppConfig = appConfigs[app] ?: AppConfig()

    /** Max gap between two presses to count as a double tap. */
    val doublePressWindowMs: Long get() = (650 - 370 * sensitivity).toLong()

    /** Minimum gap between two scroll commands. */
    val scrollCooldownMs: Long get() = (500 - 300 * sensitivity).toLong()
}

/** SharedPreferences-backed settings, exposed as a StateFlow. Stores no credentials or account data. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(block: (Settings) -> Settings) {
        val next = block(_settings.value)
        _settings.value = next
        save(next)
    }

    fun updateApp(app: SocialApp, block: (AppConfig) -> AppConfig) {
        update { s -> s.copy(appConfigs = s.appConfigs + (app to block(s.configFor(app)))) }
    }

    private fun load(): Settings {
        val configs = SocialApp.entries.associateWith { app ->
            val k = "cfg.${app.name}"
            AppConfig(
                invertScroll = prefs.getBoolean("$k.invert", false),
                likeStrategy = runCatching { LikeStrategy.valueOf(prefs.getString("$k.like", null) ?: "") }
                    .getOrDefault(LikeStrategy.BUTTON_THEN_DOUBLE_TAP),
                allowUnlike = prefs.getBoolean("$k.unlike", true),
                surface = runCatching { ContentSurface.valueOf(prefs.getString("$k.surface", null) ?: "") }
                    .getOrDefault(ContentSurface.REELS),
            )
        }
        return Settings(
            activeApp = SocialApp.fromName(prefs.getString("activeApp", null)),
            sensitivity = prefs.getFloat("sensitivity", 0.5f),
            phoneHaptics = prefs.getBoolean("phoneHaptics", true),
            autoReconnect = prefs.getBoolean("autoReconnect", true),
            directBleSession = prefs.getBoolean("directBleSession", false),
            autoOpenApp = prefs.getBoolean("autoOpenApp", true),
            preferredWatchAddress = prefs.getString("watchAddress", null),
            preferredWatchName = prefs.getString("watchName", null),
            onboardingDone = prefs.getBoolean("onboardingDone", false),
            appConfigs = configs,
        )
    }

    private fun save(s: Settings) {
        prefs.edit().apply {
            putString("activeApp", s.activeApp.name)
            putFloat("sensitivity", s.sensitivity)
            putBoolean("phoneHaptics", s.phoneHaptics)
            putBoolean("autoReconnect", s.autoReconnect)
            putBoolean("directBleSession", s.directBleSession)
            putBoolean("autoOpenApp", s.autoOpenApp)
            putString("watchAddress", s.preferredWatchAddress)
            putString("watchName", s.preferredWatchName)
            putBoolean("onboardingDone", s.onboardingDone)
            SocialApp.entries.forEach { app ->
                val c = s.configFor(app)
                val k = "cfg.${app.name}"
                putBoolean("$k.invert", c.invertScroll)
                putString("$k.like", c.likeStrategy.name)
                putBoolean("$k.unlike", c.allowUnlike)
                putString("$k.surface", c.surface.name)
            }
        }.apply()
    }
}
