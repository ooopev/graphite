package com.graphite.app.core.repo

import android.content.Context
import com.graphite.app.core.db.AppDatabase
import com.graphite.app.core.model.Profile
import com.graphite.app.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Single facade over Room for profiles & subscriptions. */
class ProfileRepository(
    context: Context,
) {
    private val db = AppDatabase.get(context)
    private val profileDao = db.profileDao()
    private val subscriptionDao = db.subscriptionDao()

    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun profiles(): Flow<List<Profile>> = profileDao.observeAll()

    fun subscriptions(): Flow<List<Subscription>> = subscriptionDao.observeAll()

    fun profile(id: Long): Flow<Profile?> = profileDao.observeById(id)

    suspend fun profileOnce(id: Long): Profile? = profileDao.byId(id)

    suspend fun upsert(profile: Profile): Long = profileDao.insert(profile)

    /** Insert parsed profiles, deduped inside the batch by raw link. */
    suspend fun insertParsed(parsed: List<Profile>): Int {
        val unique = parsed.distinctBy { it.rawLink }
        profileDao.insertAll(unique)
        return unique.size
    }

    suspend fun update(profile: Profile) = profileDao.update(profile)

    suspend fun delete(profile: Profile) = profileDao.delete(profile)

    suspend fun deleteSubscriptionCascade(sub: Subscription) {
        profileDao.deleteBySubscription(sub.id)
        subscriptionDao.delete(sub)
    }

    /** Replace all servers of a subscription with the fresh list. */
    suspend fun replaceSubscriptionProfiles(
        subscriptionId: Long,
        fresh: List<Profile>,
    ) {
        profileDao.deleteBySubscription(subscriptionId)
        profileDao.insertAll(fresh.map { it.copy(subscriptionId = subscriptionId) })
    }

    suspend fun updateSubscription(sub: Subscription) = subscriptionDao.update(sub)

    suspend fun addSubscription(sub: Subscription): Long = subscriptionDao.insert(sub)

    suspend fun subscription(id: Long): Subscription? = subscriptionDao.byId(id)

    companion object {
        @Volatile private var instance: ProfileRepository? = null

        fun get(context: Context): ProfileRepository =
            instance ?: synchronized(this) {
                instance ?: ProfileRepository(context.applicationContext).also { instance = it }
            }
    }
}
