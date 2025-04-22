package com.advancedterminal.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.advancedterminal.app.AdvancedTerminalApp
import com.advancedterminal.app.R
import com.advancedterminal.app.terminal.TerminalSession
import java.io.File

/**
 * Service to manage terminal sessions in the background.
 * Allows terminals to continue running when the app is not in the foreground.
 */
class TerminalService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "terminal_service_channel"
    }

    private val binder = LocalBinder()
    
    // List of active terminal sessions
    val sessions = mutableListOf<TerminalSession>()
    
    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Android 14+ requires specifying foreground service type
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        Log.i(AdvancedTerminalApp.TAG, "Terminal service started")
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        // Close all terminal sessions
        sessions.forEach { it.finish() }
        sessions.clear()
        
        Log.i(AdvancedTerminalApp.TAG, "Terminal service destroyed")
        super.onDestroy()
    }

    /**
     * Creates a new terminal session with the default shell.
     */
    fun createSession(): TerminalSession {
        val homeDir = getHomeDirectory()
        val session = TerminalSession(
            executable = getShellPath(),
            cwd = homeDir.path,
            args = arrayOf(),
            env = createEnvironment(homeDir)
        )
        sessions.add(session)
        
        // Update notification with session count
        updateNotification()
        
        return session
    }

    /**
     * Removes a terminal session.
     */
    fun removeSession(session: TerminalSession) {
        session.finish()
        sessions.remove(session)
        
        // Update notification with session count
        updateNotification()
        
        // If no sessions remain, consider stopping the service
        if (sessions.isEmpty()) {
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val title = "Advanced Terminal"
        val content = if (sessions.isEmpty()) {
            "No active sessions"
        } else {
            "${sessions.size} active session(s)"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Terminal Service"
            val descriptionText = "Keeps terminal sessions running in background"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun getHomeDirectory(): File {
        val dir = File(filesDir, "home")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getShellPath(): String {
        // Try to use system shell if available, otherwise use fallback
        val systemShell = "/system/bin/sh"
        return if (File(systemShell).exists()) {
            systemShell
        } else {
            // Fallback to bundled shell if we include one
            "${applicationInfo.nativeLibraryDir}/libshell.so"
        }
    }
    
    private fun createEnvironment(homeDir: File): Array<String> {
        return arrayOf(
            "HOME=${homeDir.path}",
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "TMPDIR=${cacheDir.path}",
            "PATH=/system/bin:/system/xbin",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "EXTERNAL_STORAGE=/sdcard",
            "LANG=en_US.UTF-8"
        )
    }
}
