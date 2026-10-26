package com.graphite.app.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.graphite.app.core.model.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): Profile?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<Profile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<Profile>): List<Long>

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: Long)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subscriptionId")
    suspend fun removeSubscriptionLink(subscriptionId: Long)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}
