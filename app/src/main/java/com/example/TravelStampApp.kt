package com.example

import android.app.Application
import com.example.data.AppContainer
import com.example.data.DefaultAppContainer

class TravelStampApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
