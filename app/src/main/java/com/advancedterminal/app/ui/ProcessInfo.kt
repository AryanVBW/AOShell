package com.advancedterminal.app.ui

/**
 * Data class representing information about a running process
 */
data class ProcessInfo(
    val processId: Int,
    val processName: String,
    val packageName: String,
    val memoryUsage: Float, // in MB
    val cpuUsage: Double, // in percentage
    val iconResourceId: Int
)
