package com.graphite.app.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val protocol: Protocol,
    val server: String,
    val port: Int,
    /** Original URI — kept for re-parsing, editing and lossless backup/export. */
    val rawLink: String,
    /** ProfileParams serialized as JSON. */
    val params: String,
    val subscriptionId: Long? = null,
    val latencyMs: Int? = null,
    val lastTestedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)
