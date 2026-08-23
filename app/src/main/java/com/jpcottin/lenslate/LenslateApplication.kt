package com.jpcottin.lenslate

import android.app.Application
import android.content.Context
import com.jpcottin.lenslate.di.AppContainer

class LenslateApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Reaches the app's [AppContainer] from any context, including projected (glasses) contexts. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LenslateApplication).container
