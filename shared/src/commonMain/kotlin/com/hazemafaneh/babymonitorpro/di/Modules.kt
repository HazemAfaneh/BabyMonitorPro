package com.hazemafaneh.babymonitorpro.di

import com.hazemafaneh.babymonitorpro.store.AppSettings
import com.hazemafaneh.babymonitorpro.store.KeyValueStore
import com.hazemafaneh.babymonitorpro.store.createKeyValueStore
import org.koin.dsl.module

val appModule = module {
    single<KeyValueStore> { createKeyValueStore() }
    single { AppSettings(get()) }
}
