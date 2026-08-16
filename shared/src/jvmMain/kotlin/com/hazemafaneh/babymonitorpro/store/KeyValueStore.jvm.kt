package com.hazemafaneh.babymonitorpro.store

import java.util.prefs.Preferences

private class PreferencesStore : KeyValueStore {
    private val prefs = Preferences.userRoot().node("com/hazemafaneh/babymonitorpro")

    override fun getString(key: String): String? = prefs.get(key, null)
    override fun putString(key: String, value: String) = prefs.put(key, value)
    override fun remove(key: String) = prefs.remove(key)
}

actual fun createKeyValueStore(): KeyValueStore = PreferencesStore()
