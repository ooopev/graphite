package com.oquq.app

import android.app.Application
import androidx.work.Configuration

class OquqApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(android.util.Log.WARN)
                .build()
}
