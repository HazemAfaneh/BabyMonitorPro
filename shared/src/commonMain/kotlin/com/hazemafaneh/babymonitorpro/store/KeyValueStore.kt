package com.hazemafaneh.babymonitorpro.store

/** Tiny persistence seam — SharedPreferences / NSUserDefaults / Preferences / localStorage. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

expect fun createKeyValueStore(): KeyValueStore

fun KeyValueStore.getInt(key: String, fallback: Int): Int =
    getString(key)?.toIntOrNull() ?: fallback

fun KeyValueStore.putInt(key: String, value: Int) = putString(key, value.toString())

fun KeyValueStore.getBoolean(key: String, fallback: Boolean): Boolean =
    getString(key)?.toBooleanStrictOrNull() ?: fallback

fun KeyValueStore.putBoolean(key: String, value: Boolean) = putString(key, value.toString())
