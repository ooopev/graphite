package com.oquq.app.ui.util

import java.util.Locale

/** Human-readable byte sizes: 942 B · 1.2 MB · 3.4 GB … */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb)
    return String.format(Locale.US, "%.2f TB", gb / 1024.0)
}

/** Human-readable speed from a bytes-per-second value. */
fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

fun formatEpochSeconds(seconds: Long?): String? {
    if (seconds == null || seconds <= 0) return null
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        .format(java.util.Date(seconds * 1000))
}
