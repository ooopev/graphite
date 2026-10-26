package com.graphite.app

import android.app.Application
import androidx.work.Configuration

class GraphiteApp :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(android.util.Log.WARN)
                .build()
}
