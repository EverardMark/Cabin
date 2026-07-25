package com.cabin.app

import android.app.Application
import com.cabin.app.data.ServiceLocator

class CabinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
