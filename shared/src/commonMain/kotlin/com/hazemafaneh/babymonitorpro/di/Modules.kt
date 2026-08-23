package com.hazemafaneh.babymonitorpro.di

import com.hazemafaneh.babymonitorpro.server.Broadcaster
import com.hazemafaneh.babymonitorpro.server.createBroadcaster
import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.store.KeyValueStore
import com.hazemafaneh.babymonitorpro.store.createKeyValueStore
import org.koin.dsl.module

val appModule = module {
    single<KeyValueStore> { createKeyValueStore() }
    single { AppSettings(get()) }

    // One broadcaster for the whole app, deliberately.
    //
    // createBroadcaster() builds a new KtorBroadcaster every call, so a per-screen
    // `remember { createBroadcaster() }` gave the camera screen and its settings screen two
    // different instances: the settings screen would then drive a broadcaster that owns no
    // camera and no socket, and restarting it would collide with the real one on port 8080.
    // Named so the null on web stays expressible.
    single<BroadcasterHolder> { BroadcasterHolder(createBroadcaster()) }
}

/**
 * Wrapper so Koin can hold the null the browser returns — `single<Broadcaster?>` is not a
 * resolvable type, and the web build genuinely has no broadcaster to give.
 */
class BroadcasterHolder(val broadcaster: Broadcaster?)
