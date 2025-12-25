package com.example.intra

import android.app.Application

class MyApplication : Application() {
    companion object {
        // 💡 lateinit var की जगह val use करें, यह बेहतर है।
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}