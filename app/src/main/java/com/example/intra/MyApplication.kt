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
    object AppState {
        var isForeground = false
        var pendingCallOffer: String? = null
        var pendingCallSender: String? = null // Sender का नाम भी सेव कर लो
    }
}