package com.example.ilyphoto

import android.app.Application
import com.example.ilyphoto.data.AzureProvider

class ILYPhotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AzureProvider.init(this)
    }
}
