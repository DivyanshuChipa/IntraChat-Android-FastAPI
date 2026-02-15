package com.example.intra

import android.app.Application
// 👇 Ye imports zaroori hain
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

// 👇 Yahan 'ImageLoaderFactory' interface add kiya
class MyApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    // 🔥 YE FUNCTION ZAROORI HAI: Ye Coil ko Video Decoder deta hai
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory()) // 👈 Video support enabled!
            }
            .crossfade(true)
            .build()
    }
    object AppState {
        var isForeground = false
        var pendingCallOffer: String? = null
        var pendingCallSender: String? = null // Sender का नाम भी सेव कर लो
    }
}