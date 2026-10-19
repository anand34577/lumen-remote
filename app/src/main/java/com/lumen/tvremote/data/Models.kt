package com.lumen.tvremote.data

import com.lumen.tvremote.connection.PairingSession
import com.lumen.tvremote.connection.RemoteSession
import com.lumen.tvremote.protocol.TvKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TvDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = RemoteSession.DEFAULT_REMOTE_PORT,
    val pairingPort: Int = PairingSession.DEFAULT_PAIRING_PORT,
    /** mDNS service name; stays stable when the TV's IP address changes. */
    val serviceName: String? = null,
    val macAddress: String? = null,
    val model: String? = null,
    val paired: Boolean = false,
    val lastConnected: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("host", host)
        .put("port", port).put("pairingPort", pairingPort)
        .putOpt("serviceName", serviceName).putOpt("mac", macAddress).putOpt("model", model)
        .put("paired", paired).put("lastConnected", lastConnected)

    companion object {
        fun fromJson(o: JSONObject) = TvDevice(
            id = o.getString("id"),
            name = o.getString("name"),
            host = o.getString("host"),
            port = o.optInt("port", RemoteSession.DEFAULT_REMOTE_PORT),
            pairingPort = o.optInt("pairingPort", PairingSession.DEFAULT_PAIRING_PORT),
            serviceName = o.optStringOrNull("serviceName"),
            macAddress = o.optStringOrNull("mac"),
            model = o.optStringOrNull("model"),
            paired = o.optBoolean("paired"),
            lastConnected = o.optLong("lastConnected"),
        )
    }
}

data class AppShortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** A deep link URL or an Android package name. */
    val target: String,
) {
    /** Package names are launched through the Play Store launch link, which every Android TV handles. */
    val launchLink: String
        get() = if (target.contains("://")) target else "market://launch?id=$target"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("target", target)

    companion object {
        fun fromJson(o: JSONObject) = AppShortcut(o.getString("id"), o.getString("name"), o.getString("target"))

        val defaults = listOf(
            AppShortcut(name = "YouTube", target = "com.google.android.youtube.tv"),
            AppShortcut(name = "Netflix", target = "com.netflix.ninja"),
            AppShortcut(name = "Prime Video", target = "com.amazon.amazonvideo.livingroom"),
            AppShortcut(name = "Disney+", target = "com.disney.disneyplus"),
            AppShortcut(name = "Spotify", target = "com.spotify.tv.android"),
            AppShortcut(name = "YouTube Music", target = "com.google.android.youtube.tvmusic"),
            AppShortcut(name = "Plex", target = "com.plexapp.android"),
            AppShortcut(name = "Kodi", target = "org.xbmc.kodi"),
            AppShortcut(name = "Play Store", target = "com.android.vending"),
        )

        private val genericSegments = setOf("tv", "android", "androidtv", "app", "apps", "leanback", "livingroom", "ninja", "atv", "player", "mobile")

        /**
         * Human-friendly name for a package the TV reports, e.g. `com.google.android.youtube.tv` -> "YouTube".
         * Known apps use their real name; otherwise the most specific non-generic segment is used.
         */
        fun labelFor(packageName: String): String {
            defaults.find { it.target == packageName }?.let { return it.name }
            val segment = packageName.split('.')
                .drop(1)
                .lastOrNull { it.isNotBlank() && it.lowercase() !in genericSegments }
                ?: packageName.substringAfterLast('.')
            return segment.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}

sealed interface MacroStep {
    data class Key(val key: TvKey, val longPress: Boolean = false) : MacroStep
    data class Text(val text: String) : MacroStep
    data class App(val link: String) : MacroStep
    data class Delay(val millis: Long) : MacroStep

    fun toJson(): JSONObject = when (this) {
        is Key -> JSONObject().put("type", "key").put("key", key.name).put("long", longPress)
        is Text -> JSONObject().put("type", "text").put("text", text)
        is App -> JSONObject().put("type", "app").put("link", link)
        is Delay -> JSONObject().put("type", "delay").put("ms", millis)
    }

    companion object {
        fun fromJson(o: JSONObject): MacroStep? = when (o.optString("type")) {
            "key" -> TvKey.fromName(o.optString("key"))?.let { Key(it, o.optBoolean("long")) }
            "text" -> Text(o.optString("text"))
            "app" -> App(o.optString("link"))
            "delay" -> Delay(o.optLong("ms").coerceIn(0, MAX_DELAY_MS))
            else -> null
        }

        const val MAX_DELAY_MS = 30_000L
    }
}

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Macro {
            val arr = o.optJSONArray("steps") ?: JSONArray()
            val steps = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MacroStep::fromJson) }
            return Macro(o.getString("id"), o.getString("name"), steps)
        }

        val defaults = listOf(
            Macro(
                name = "Pause & mute",
                steps = listOf(MacroStep.Key(TvKey.PAUSE), MacroStep.Delay(200), MacroStep.Key(TvKey.MUTE)),
            ),
            Macro(
                name = "Sleep TV",
                steps = listOf(MacroStep.Key(TvKey.SLEEP)),
            ),
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class HapticStrength { OFF, LIGHT, MEDIUM, STRONG }
/** The main control surface shown in the middle of the remote. */
enum class PadMode(val label: String) { DPAD("D-pad"), TOUCHPAD("Touch"), NUMPAD("Numbers"), MEDIA("Media") }

/** Sections of the remote screen the user can show or hide. */
enum class RemoteSection(val label: String) {
    APPS("Quick launch apps"),
    MACROS("Macros"),
    COLOR_KEYS("Color keys"),
    EXTRA_KEYS("TV keys (guide, inputs, captions)"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val amoledBlack: Boolean = false,
    val accentIndex: Int = 0,
    val haptics: HapticStrength = HapticStrength.MEDIUM,
    val volumeKeysControlTv: Boolean = true,
    val keepScreenOn: Boolean = false,
    val padMode: PadMode = PadMode.DPAD,
    val touchpadSensitivity: Float = 1f,
    val autoKeyboard: Boolean = true,
    val persistentNotification: Boolean = false,
    val autoConnect: Boolean = true,
    val leftHanded: Boolean = false,
    val repeatIntervalMs: Int = 110,
    val sections: Set<RemoteSection> = setOf(RemoteSection.APPS, RemoteSection.MACROS),
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
