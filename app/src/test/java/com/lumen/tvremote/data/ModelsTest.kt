package com.lumen.tvremote.data

import com.lumen.tvremote.protocol.TvKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class ModelsTest {

    @Test
    fun deviceRoundTrip() {
        val device = TvDevice(name = "Living room", host = "192.168.1.5", serviceName = "Living room", macAddress = "AA:BB:CC:DD:EE:FF", paired = true)
        assertEquals(device, TvDevice.fromJson(JSONObject(device.toJson().toString())))
    }

    @Test
    fun deviceWithoutOptionalFields() {
        val device = TvDevice(name = "TV", host = "10.0.0.2")
        val parsed = TvDevice.fromJson(JSONObject(device.toJson().toString()))
        assertNull(parsed.macAddress)
        assertNull(parsed.serviceName)
        assertEquals(device, parsed)
    }

    @Test
    fun macroRoundTripSkipsUnknownSteps() {
        val macro = Macro(
            name = "Test",
            steps = listOf(
                MacroStep.Key(TvKey.HOME, longPress = true),
                MacroStep.Text("hi"),
                MacroStep.App("market://launch?id=x.y"),
                MacroStep.Delay(250),
            ),
        )
        val json = macro.toJson()
        json.getJSONArray("steps").put(JSONObject().put("type", "teleport"))
        assertEquals(macro, Macro.fromJson(JSONObject(json.toString())))
    }

    @Test
    fun delayIsClamped() {
        val step = MacroStep.fromJson(JSONObject().put("type", "delay").put("ms", 999_999))
        assertEquals(MacroStep.Delay(MacroStep.MAX_DELAY_MS), step)
    }

    @Test
    fun packageShortcutsUseLaunchLink() {
        assertEquals("market://launch?id=com.netflix.ninja", AppShortcut(name = "N", target = "com.netflix.ninja").launchLink)
        assertEquals("https://www.youtube.com", AppShortcut(name = "Y", target = "https://www.youtube.com").launchLink)
    }

    @Test
    fun appLabelsAreReadable() {
        assertEquals("YouTube", AppShortcut.labelFor("com.google.android.youtube.tv"))
        assertEquals("Hotstar", AppShortcut.labelFor("in.startv.hotstar"))
        assertEquals("Twitch", AppShortcut.labelFor("tv.twitch.android.app"))
        assertEquals("Hbomax", AppShortcut.labelFor("com.hbo.hbomax.androidtv"))
    }
}
