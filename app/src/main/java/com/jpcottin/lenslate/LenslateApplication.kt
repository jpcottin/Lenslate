package com.jpcottin.lenslate

import android.app.Application
import android.content.Context
import com.jpcottin.lenslate.di.AppContainer

class LenslateApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ensureCacheDirectory()
        container = AppContainer(this)
    }
}

/**
 * Clearing the app's storage removes the cache directory itself, and the system only recreates
 * it when it is asked for. ML Kit never asks: it creates its temporary directories under that
 * path, and fails on a thread of its own where nothing can catch it, so the first translation
 * after "Clear storage" crashed the app.
 */
internal fun Context.ensureCacheDirectory() {
    cacheDir
}

/** Reaches the app's [AppContainer] from any context, including projected (glasses) contexts. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LenslateApplication).container
