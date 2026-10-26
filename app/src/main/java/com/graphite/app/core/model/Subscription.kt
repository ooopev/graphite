package com.graphite.app.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val url: String,
    val autoUpdate: Boolean = true,
    val lastUpdate: Long? = null,
    // Parsed from the "subscription-userinfo" response header, bytes:
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expire: Long? = null,
)
