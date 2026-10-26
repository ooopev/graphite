# Graphite R8 rules

# --- gomobile bound cores (go.Seq is used reflectively) ---
-dontwarn go.**
-keep class go.** { *; }
-keep class io.nekohasekai.libbox.** { *; }
-keep class io.nekohasekai.libXray.** { *; }
-keep class io.nekohasekai.oquqawg.** { *; }
-keep class io.github.xtls.libxray.** { *; }
-keep class com.wireguard.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.graphite.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
