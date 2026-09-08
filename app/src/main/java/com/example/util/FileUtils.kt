package com.example.util

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(java.util.Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(java.util.Locale.US, mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.1f GB".format(java.util.Locale.US, gb)
    val tb = gb / 1024.0
    return "%.1f TB".format(java.util.Locale.US, tb)
}
