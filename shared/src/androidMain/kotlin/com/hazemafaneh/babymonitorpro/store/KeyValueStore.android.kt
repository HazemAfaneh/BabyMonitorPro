package com.hazemafaneh.babymonitorpro.store

import android.content.Context
import com.hazemafaneh.babymonitorpro.core.AndroidPlatformContext

private class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("babymonitorpro", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

actual fun createKeyValueStore(): KeyValueStore = SharedPrefsStore(AndroidPlatformContext.require())
