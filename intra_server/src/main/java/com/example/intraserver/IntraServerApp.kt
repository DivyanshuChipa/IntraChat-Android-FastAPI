package com.example.intraserver

import android.app.Application
import com.example.intraserver.db.ServerDatabase

class IntraServerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize SQLite Database
        ServerDatabase.getInstance(this)
    }
}
