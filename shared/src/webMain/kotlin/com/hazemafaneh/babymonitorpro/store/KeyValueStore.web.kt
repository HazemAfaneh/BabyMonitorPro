package com.hazemafaneh.babymonitorpro.store

/**
 * Hand-written externals rather than a browser-wrapper dependency: this is the whole of
 * the localStorage surface the app needs, and it compiles for both js and wasmJs.
 */
private external interface WebStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}

private external val localStorage: WebStorage

private class LocalStorageStore : KeyValueStore {
    override fun getString(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()
    override fun putString(key: String, value: String) {
        runCatching { localStorage.setItem(key, value) }
    }
    override fun remove(key: String) {
        runCatching { localStorage.removeItem(key) }
    }
}

actual fun createKeyValueStore(): KeyValueStore = LocalStorageStore()
