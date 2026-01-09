package com.tourmeta.app

import android.app.Application

class TurboMetaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TurboMetaApplication
            private set
    }
}
