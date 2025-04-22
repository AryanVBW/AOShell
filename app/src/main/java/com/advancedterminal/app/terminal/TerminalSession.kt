package com.advancedterminal.app.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.advancedterminal.app.AdvancedTerminalApp
import com.jrummyapps.android.shell.Shell
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 * Uses JRummy's Android Shell library for command execution.
 */
class TerminalSession(
    val executable: String,
    val cwd: String,
    val args: Array<String>,
    val env: Array<String>
) {
    companion object {
        private val TAG = TerminalSession::class.java.simpleName
    }
    
    // Terminal session callback interface
    interface SessionCallback {
        fun onSessionExit(session: TerminalSession)
        fun onDataReceived(session: TerminalSession, data: ByteArray)
    }
    
    // Shell command execution
    private var shellProcess: Process? = null
    private var processId = -1
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    
    // Terminal emulation variables
    private val buffer = StringBuilder()
    private val mEmulatorLock = Any()
    
    // Streams for reading/writing to terminal
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val mWriteLock = Any()
    
    // Session state
    var sessionCallback: SessionCallback? = null
    private var isRunning = false
    
    init {
        // Start the process and initiate terminal emulation
        try {
            initializeSession()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize terminal session", e)
        }
    }
    
    /**
     * Initializes the terminal session by starting the process and setting up I/O.
     */
    private fun initializeSession() {
        try {
            // Prepare command
            val command = mutableListOf(executable)
            command.addAll(args)
            
            // Create environment variables string for the shell command
            val envString = StringBuilder()
            for (envEntry in env) {
                envString.append(envEntry).append(" ")
            }
            
            // Start the process with ProcessBuilder for better control
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(cwd))
            
            // Set environment variables
            val environment = processBuilder.environment()
            for (envEntry in env) {
                val splitIndex = envEntry.indexOf('=')
                if (splitIndex != -1) {
                    val key = envEntry.substring(0, splitIndex)
                    val value = envEntry.substring(splitIndex + 1)
                    environment[key] = value
                }
            }
            
            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true)
            
            // Start the process
            shellProcess = processBuilder.start()
            
            // Get the streams for I/O
            inputStream = shellProcess?.inputStream
            outputStream = shellProcess?.outputStream
            
            // Start reading output
            startReaderThread(inputStream!!)
            
            isRunning = true
            Log.i(TAG, "Terminal session initialized with command: ${command.joinToString(" ")}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start shell process", e)
            throw e
        }
    }
    
    /**
     * Start a thread to read output from the process.
     */
    private fun startReaderThread(processInputStream: InputStream) {
        Thread {
            val buffer = ByteArray(4096)
            var bytesRead: Int = 0
            
            try {
                while (isRunning && processInputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        val data = buffer.copyOfRange(0, bytesRead)
                        processSessionOutput(data)
                    }
                }
            } catch (e: IOException) {
                if (isRunning) {
                    Log.e(TAG, "Error reading from process", e)
                }
            } finally {
                // Process exited or reading failed
                mainThreadHandler.post {
                    isRunning = false
                    sessionCallback?.onSessionExit(this)
                }
            }
        }.start()
    }
    
    /**
     * Process output from the terminal session.
     */
    private fun processSessionOutput(data: ByteArray) {
        synchronized(mEmulatorLock) {
            // Process the data (in a real implementation, this would handle terminal escape sequences)
            val str = String(data)
            buffer.append(str)
            
            // Notify callback on main thread
            mainThreadHandler.post {
                sessionCallback?.onDataReceived(this, data)
            }
        }
    }
    
    /**
     * Execute a command directly using the Shell library as an alternative approach.
     */
    fun executeCommand(command: String): String {
        try {
            val result = Shell.SH.run(command)
            val output = result.getStdout()
            
            // Also process this through our normal channels to update UI
            processSessionOutput(output.toByteArray())
            
            return output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $command", e)
            return "Error: ${e.message}"
        }
    }
    
    /**
     * Write data to the terminal session.
     */
    fun write(data: ByteArray): Boolean {
        if (!isRunning) return false
        
        synchronized(mWriteLock) {
            try {
                outputStream?.write(data)
                outputStream?.flush()
                return true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write to terminal", e)
                return false
            }
        }
    }
    
    /**
     * Write a single byte to the terminal session.
     */
    fun write(byte: Int): Boolean {
        return write(byteArrayOf(byte.toByte()))
    }
    
    /**
     * Write a string to the terminal session.
     */
    fun write(string: String): Boolean {
        return write(string.toByteArray())
    }
    
    /**
     * Finish this terminal session.
     */
    fun finish() {
        if (!isRunning) return
        
        isRunning = false
        
        shellProcess?.let { proc ->
            try {
                proc.destroy()
                proc.waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy process", e)
            }
        }
        
        shellProcess = null
        
        try {
            outputStream?.close()
            inputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close terminal streams", e)
        }
        
        Log.i(TAG, "Terminal session finished")
    }
    
    /**
     * Check if this terminal session is still running.
     */
    fun isRunning(): Boolean {
        return isRunning
    }
    
    /**
     * Get the current buffer content as a string.
     */
    fun getBufferText(): String {
        return buffer.toString()
    }
}
