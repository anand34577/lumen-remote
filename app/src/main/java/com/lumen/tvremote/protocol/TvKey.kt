package com.lumen.tvremote.protocol

import android.view.KeyEvent

/** Remote key codes. The TV accepts standard Android [KeyEvent] key codes. */
enum class TvKey(val code: Int, val label: String) {
    POWER(KeyEvent.KEYCODE_POWER, "Power"),
    SLEEP(KeyEvent.KEYCODE_SLEEP, "Sleep"),
    WAKEUP(KeyEvent.KEYCODE_WAKEUP, "Wake up"),
    UP(KeyEvent.KEYCODE_DPAD_UP, "Up"),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "Down"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "Left"),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "Right"),
    CENTER(KeyEvent.KEYCODE_DPAD_CENTER, "OK"),
    BACK(KeyEvent.KEYCODE_BACK, "Back"),
    HOME(KeyEvent.KEYCODE_HOME, "Home"),
    MENU(KeyEvent.KEYCODE_MENU, "Menu"),
    SETTINGS(KeyEvent.KEYCODE_SETTINGS, "Settings"),
    SEARCH(KeyEvent.KEYCODE_SEARCH, "Search"),
    ASSIST(KeyEvent.KEYCODE_ASSIST, "Assistant"),
    ALL_APPS(284, "All apps"), // KeyEvent.KEYCODE_ALL_APPS; the constant is API 28+ but the TV understands it regardless
    INPUT(KeyEvent.KEYCODE_TV_INPUT, "Input"),
    HDMI_1(KeyEvent.KEYCODE_TV_INPUT_HDMI_1, "HDMI 1"),
    HDMI_2(KeyEvent.KEYCODE_TV_INPUT_HDMI_2, "HDMI 2"),
    HDMI_3(KeyEvent.KEYCODE_TV_INPUT_HDMI_3, "HDMI 3"),
    HDMI_4(KeyEvent.KEYCODE_TV_INPUT_HDMI_4, "HDMI 4"),
    GUIDE(KeyEvent.KEYCODE_GUIDE, "Guide"),
    INFO(KeyEvent.KEYCODE_INFO, "Info"),
    CAPTIONS(KeyEvent.KEYCODE_CAPTIONS, "Captions"),
    AUDIO_TRACK(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, "Audio track"),
    VOLUME_UP(KeyEvent.KEYCODE_VOLUME_UP, "Volume up"),
    VOLUME_DOWN(KeyEvent.KEYCODE_VOLUME_DOWN, "Volume down"),
    MUTE(KeyEvent.KEYCODE_VOLUME_MUTE, "Mute"),
    CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP, "Channel up"),
    CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN, "Channel down"),
    LAST_CHANNEL(KeyEvent.KEYCODE_LAST_CHANNEL, "Last channel"),
    PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause"),
    PLAY(KeyEvent.KEYCODE_MEDIA_PLAY, "Play"),
    PAUSE(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause"),
    STOP(KeyEvent.KEYCODE_MEDIA_STOP, "Stop"),
    NEXT(KeyEvent.KEYCODE_MEDIA_NEXT, "Next"),
    PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous"),
    REWIND(KeyEvent.KEYCODE_MEDIA_REWIND, "Rewind"),
    FAST_FORWARD(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "Fast forward"),
    RECORD(KeyEvent.KEYCODE_MEDIA_RECORD, "Record"),
    RED(KeyEvent.KEYCODE_PROG_RED, "Red"),
    GREEN(KeyEvent.KEYCODE_PROG_GREEN, "Green"),
    YELLOW(KeyEvent.KEYCODE_PROG_YELLOW, "Yellow"),
    BLUE(KeyEvent.KEYCODE_PROG_BLUE, "Blue"),
    DEL(KeyEvent.KEYCODE_DEL, "Backspace"),
    ENTER(KeyEvent.KEYCODE_ENTER, "Enter"),
    NUM_0(KeyEvent.KEYCODE_0, "0"),
    NUM_1(KeyEvent.KEYCODE_1, "1"),
    NUM_2(KeyEvent.KEYCODE_2, "2"),
    NUM_3(KeyEvent.KEYCODE_3, "3"),
    NUM_4(KeyEvent.KEYCODE_4, "4"),
    NUM_5(KeyEvent.KEYCODE_5, "5"),
    NUM_6(KeyEvent.KEYCODE_6, "6"),
    NUM_7(KeyEvent.KEYCODE_7, "7"),
    NUM_8(KeyEvent.KEYCODE_8, "8"),
    NUM_9(KeyEvent.KEYCODE_9, "9");

    companion object {
        private val byName = entries.associateBy { it.name }
        fun fromName(name: String): TvKey? = byName[name]
        val digits = listOf(NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9)
    }
}
