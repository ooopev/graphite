package com.oquq.app.core.repo

import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.Subscription
import com.oquq.app.core.parser.LinkParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Backup format: a tiny JSON document of raw links + subscription URLs.
 * Raw links are the canonical, lossless form — re-import re-parses everything.
 */
object BackupManager {

    @Serializable
    data class BackupV1(
        val version: Int = 1,
        val profiles: List<BackupProfile>,
        val subscriptions: List<BackupSubscription>,
    )

    @Serializable
    data class BackupProfile(val name: String, val rawLink: String)

    @Serializable
    data class BackupSubscription(val name: String, val url: String, val autoUpdate: Boolean)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun export(profiles: List<Profile>, subscriptions: List<Subscription>): String =
        json.encodeToString(
            BackupV1(
                profiles = profiles.map { BackupProfile(it.name, it.rawLink) },
                subscriptions = subscriptions.map { BackupSubscription(it.name, it.url, it.autoUpdate) },
            ),
            serializer = BackupV1.serializer(),
        )

    /** Returns parsed profiles + subscriptions to add. */
    fun import(content: String): Pair<List<Profile>, List<Subscription>>? = runCatching {
        val doc = json.decodeFromString(BackupV1.serializer(), content)
        val profiles = doc.profiles.mapNotNull { bp ->
            LinkParser.parse(bp.rawLink)?.copy(name = bp.name.ifBlank { it.name })
        }
        val subs = doc.subscriptions.map { Subscription(name = it.name, url = it.url, autoUpdate = it.autoUpdate) }
        profiles to subs
    }.getOrNull()
}
