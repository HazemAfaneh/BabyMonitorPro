package com.hazemafaneh.babymonitorpro.store

import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.core.defaultDeviceName

/** Everything the app remembers between launches. Nothing here leaves the device. */
class AppSettings(private val store: KeyValueStore) {

    var lastRole: Role?
        get() = store.getString(KEY_ROLE)?.let { name -> Role.entries.firstOrNull { it.name == name } }
        set(value) {
            if (value == null) store.remove(KEY_ROLE) else store.putString(KEY_ROLE, value.name)
        }

    var deviceName: String
        get() = store.getString(KEY_DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
        set(value) = store.putString(KEY_DEVICE_NAME, value)

    var pinEnabled: Boolean
        get() = store.getBoolean(KEY_PIN_ENABLED, false)
        set(value) = store.putBoolean(KEY_PIN_ENABLED, value)

    var pin: String
        get() = store.getString(KEY_PIN).orEmpty()
        set(value) = store.putString(KEY_PIN, value)

    var motionSensitivity: Int
        get() = store.getInt(KEY_MOTION, 50)
        set(value) = store.putInt(KEY_MOTION, value.coerceIn(0, 100))

    var soundSensitivity: Int
        get() = store.getInt(KEY_SOUND, 50)
        set(value) = store.putInt(KEY_SOUND, value.coerceIn(0, 100))

    var nightMode: Boolean
        get() = store.getBoolean(KEY_NIGHT_MODE, false)
        set(value) = store.putBoolean(KEY_NIGHT_MODE, value)

    var lastManualHost: String
        get() = store.getString(KEY_LAST_HOST).orEmpty()
        set(value) = store.putString(KEY_LAST_HOST, value)

    private companion object {
        const val KEY_ROLE = "role"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_PIN_ENABLED = "pin_enabled"
        const val KEY_PIN = "pin"
        const val KEY_MOTION = "motion_sensitivity"
        const val KEY_SOUND = "sound_sensitivity"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_LAST_HOST = "last_manual_host"
    }
}
