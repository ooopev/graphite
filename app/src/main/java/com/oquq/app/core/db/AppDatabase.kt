package com.oquq.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.oquq.app.core.model.Profile
import com.oquq.app.core.model.Protocol
import com.oquq.app.core.model.Subscription

class Converters {
    @TypeConverter
    fun protocolToString(p: Protocol): String = p.name

    @TypeConverter
    fun stringToProtocol(s: String): Protocol = runCatching { Protocol.valueOf(s) }.getOrDefault(Protocol.VLESS)
}

@Database(
    entities = [Profile::class, Subscription::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "oquq.db",
                    ).build()
                    .also { instance = it }
            }
    }
}
