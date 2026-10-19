package com.lumen.tvremote.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumen")

/** Persistent storage for devices, shortcuts, macros and settings. */
class AppStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.w(TAG, "Preferences unreadable, using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    val devices: Flow<List<TvDevice>> = data.map { prefs ->
        prefs[K_DEVICES].decodeList(TvDevice::fromJson).sortedByDescending { it.lastConnected }
    }.distinctUntilChanged()

    val lastDeviceId: Flow<String?> = data.map { it[K_LAST_DEVICE] }.distinctUntilChanged()

    val shortcuts: Flow<List<AppShortcut>> = data.map { prefs ->
        prefs[K_SHORTCUTS]?.let { it.decodeList(AppShortcut::fromJson) } ?: AppShortcut.defaults
    }.distinctUntilChanged()

    val macros: Flow<List<Macro>> = data.map { prefs ->
        prefs[K_MACROS]?.let { it.decodeList(Macro::fromJson) } ?: Macro.defaults
    }.distinctUntilChanged()

    val settings: Flow<AppSettings> = data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun upsertDevice(device: TvDevice) = store.edit { prefs ->
        val list = prefs[K_DEVICES].decodeList(TvDevice::fromJson).toMutableList()
        val index = list.indexOfFirst { it.id == device.id }
        if (index >= 0) list[index] = device else list += device
        prefs[K_DEVICES] = list.encode { it.toJson() }
    }

    suspend fun updateDevice(id: String, transform: (TvDevice) -> TvDevice) = store.edit { prefs ->
        val list = prefs[K_DEVICES].decodeList(TvDevice::fromJson)
        prefs[K_DEVICES] = list.map { if (it.id == id) transform(it) else it }.encode { it.toJson() }
    }

    suspend fun removeDevice(id: String) = store.edit { prefs ->
        val list = prefs[K_DEVICES].decodeList(TvDevice::fromJson).filterNot { it.id == id }
        prefs[K_DEVICES] = list.encode { it.toJson() }
        if (prefs[K_LAST_DEVICE] == id) prefs.remove(K_LAST_DEVICE)
    }

    suspend fun setLastDevice(id: String) = store.edit { it[K_LAST_DEVICE] = id }

    suspend fun saveShortcuts(list: List<AppShortcut>) = store.edit { it[K_SHORTCUTS] = list.encode { s -> s.toJson() } }

    suspend fun saveMacros(list: List<Macro>) = store.edit { it[K_MACROS] = list.encode { m -> m.toJson() } }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = store.edit { prefs ->
        val updated = transform(prefs.toSettings())
        prefs.writeSettings(updated)
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(this[K_THEME], d.themeMode),
            dynamicColor = this[K_DYNAMIC] ?: d.dynamicColor,
            amoledBlack = this[K_AMOLED] ?: d.amoledBlack,
            accentIndex = this[K_ACCENT] ?: d.accentIndex,
            haptics = enumOrDefault(this[K_HAPTICS], d.haptics),
            volumeKeysControlTv = this[K_VOLUME_KEYS] ?: d.volumeKeysControlTv,
            keepScreenOn = this[K_KEEP_SCREEN] ?: d.keepScreenOn,
            padMode = enumOrDefault(this[K_PAD], d.padMode),
            touchpadSensitivity = this[K_SENSITIVITY] ?: d.touchpadSensitivity,
            autoKeyboard = this[K_AUTO_KEYBOARD] ?: d.autoKeyboard,
            persistentNotification = this[K_NOTIFICATION] ?: d.persistentNotification,
            autoConnect = this[K_AUTO_CONNECT] ?: d.autoConnect,
            leftHanded = this[K_LEFT_HANDED] ?: d.leftHanded,
            repeatIntervalMs = this[K_REPEAT] ?: d.repeatIntervalMs,
            sections = this[K_SECTIONS]?.mapNotNull { name -> RemoteSection.entries.find { it.name == name } }?.toSet()
                ?: d.sections,
        )
    }

    private fun MutablePreferences.writeSettings(s: AppSettings) {
        this[K_THEME] = s.themeMode.name
        this[K_DYNAMIC] = s.dynamicColor
        this[K_AMOLED] = s.amoledBlack
        this[K_ACCENT] = s.accentIndex
        this[K_HAPTICS] = s.haptics.name
        this[K_VOLUME_KEYS] = s.volumeKeysControlTv
        this[K_KEEP_SCREEN] = s.keepScreenOn
        this[K_PAD] = s.padMode.name
        this[K_SENSITIVITY] = s.touchpadSensitivity
        this[K_AUTO_KEYBOARD] = s.autoKeyboard
        this[K_NOTIFICATION] = s.persistentNotification
        this[K_AUTO_CONNECT] = s.autoConnect
        this[K_LEFT_HANDED] = s.leftHanded
        this[K_REPEAT] = s.repeatIntervalMs
        this[K_SECTIONS] = s.sections.map { it.name }.toSet()
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().find { it.name == n } } ?: default

    private fun <T> String?.decodeList(parse: (JSONObject) -> T): List<T> {
        if (this.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(this)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { parse(it) }.getOrNull() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt list in preferences", e)
            emptyList()
        }
    }

    private fun <T> List<T>.encode(toJson: (T) -> JSONObject): String =
        JSONArray().apply { this@encode.forEach { put(toJson(it)) } }.toString()

    private companion object {
        const val TAG = "AppStore"
        val K_DEVICES = stringPreferencesKey("devices")
        val K_LAST_DEVICE = stringPreferencesKey("last_device")
        val K_SHORTCUTS = stringPreferencesKey("shortcuts")
        val K_MACROS = stringPreferencesKey("macros")
        val K_THEME = stringPreferencesKey("theme")
        val K_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val K_AMOLED = booleanPreferencesKey("amoled")
        val K_ACCENT = intPreferencesKey("accent")
        val K_HAPTICS = stringPreferencesKey("haptics")
        val K_VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val K_KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        val K_PAD = stringPreferencesKey("pad_mode")
        val K_SENSITIVITY = floatPreferencesKey("touchpad_sensitivity")
        val K_AUTO_KEYBOARD = booleanPreferencesKey("auto_keyboard")
        val K_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val K_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val K_LEFT_HANDED = booleanPreferencesKey("left_handed")
        val K_REPEAT = intPreferencesKey("repeat_interval")
        val K_SECTIONS = stringSetPreferencesKey("sections")
    }
}
