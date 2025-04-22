package com.advancedterminal.app.terminal

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import com.jrummyapps.android.shell.CommandResult
import com.jrummyapps.android.shell.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom view that renders terminal content and handles user input.
 * This integrates with jrummyapps Android Shell for command execution.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), TerminalSession.SessionCallback {

    companion object {
        private val TAG = TerminalView::class.java.simpleName
        private const val DEFAULT_FONT_SIZE = 14
    }

    // Terminal rendering properties
    private var fontSize = DEFAULT_FONT_SIZE
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textSize = fontSize * resources.displayMetrics.density
    }
    private val bgPaint = Paint().apply {
        color = Color.BLACK
    }

    // Session handling
    private var terminalSession: TerminalSession? = null
    
    // Terminal output storage
    private val lines = LinkedList<String>()
    private var currentCommand = StringBuilder()
    
    // For scrolling and gestures
    private val scroller = OverScroller(context)
    // Touch and scrolling variables
    private var lastScrollY = 0
    private var lastY = 0f
    private var startY = 0f
    private var startTime = 0L
    private var activePointerId = 0
    private var velocityTracker: VelocityTracker? = null
    private var isScrolling = false
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                lastScrollY = (lastScrollY + distanceY).toInt()
                lastScrollY = lastScrollY.coerceIn(0, maxScrollY())
                ViewCompat.postInvalidateOnAnimation(this@TerminalView)
                return true
            }
            
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                scroller.fling(0, lastScrollY, 0, -velocityY.toInt(), 0, 0, 0, maxScrollY())
                ViewCompat.postInvalidateOnAnimation(this@TerminalView)
                return true
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Request focus and show keyboard on tap
                if (!hasFocus()) {
                    requestFocus()
                }
                showKeyboard()
                return true
            }
        }
    )

    init {
        // Make view focusable to receive key events
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Set text handling properties
        textPaint.apply {
            isAntiAlias = true
            color = Color.WHITE
            typeface = Typeface.MONOSPACE
            textSize = fontSize * resources.displayMetrics.density
        }
        
        // Initialize device name and username
        initDeviceInfo()
        
        // Add welcome message
        lines.add("┌────────────────────────────────────────┐")
        lines.add("│ AOShell ${ Build.VERSION.RELEASE} - Advanced Terminal │")
        lines.add("│ Type 'help' for available commands    │")
        lines.add("└────────────────────────────────────────┘")
        lines.add(getPrompt())
        
        // Ensure this view handles clicks and can get focus for keyboard input
        isClickable = true
    }
    
    /**
     * Initialize device name and username for the prompt
     */
    private fun initDeviceInfo() {
        try {
            // Get username
            val userResult = Shell.SH.run("whoami")
            if (userResult.isSuccessful && userResult.stdout.isNotEmpty()) {
                username = userResult.stdout[0].trim()
            }
            
            // Get device name
            deviceName = Build.MODEL.replace(" ", "_")
            
            // If unable to get username, use static value
            if (username.isBlank()) {
                username = "aoshell"
            }
        } catch (e: Exception) {
            username = "aoshell"
            deviceName = "android"
        }
    }

    /**
     * Attach this view to a terminal session
     */
    fun attachSession(session: TerminalSession) {
        // Detach from current session if any
        terminalSession?.sessionCallback = null
        
        // Attach to new session
        terminalSession = session
        session.sessionCallback = this
        
        // Add session connected message
        lines.add("Terminal session started")
        lines.add("$ ")
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Calculate character dimensions
        val charWidth = textPaint.measureText("M")
        val fontMetrics = textPaint.fontMetrics
        val charHeight = fontMetrics.descent - fontMetrics.ascent
        
        // Apply scroll offset
        canvas.save()
        canvas.translate(0f, -lastScrollY.toFloat())
        
        // Draw lines of text
        var y = fontMetrics.leading - fontMetrics.ascent + paddingTop
        for (line in lines) {
            canvas.drawText(line, paddingLeft.toFloat(), y, textPaint)
            y += charHeight
        }
        
        // Draw current command line with cursor
        val prompt = "$ "
        val commandLineY = y
        canvas.drawText(prompt + currentCommand.toString(), paddingLeft.toFloat(), commandLineY, textPaint)
        
        // Draw cursor
        if (hasFocus() && System.currentTimeMillis() % 1000 < 500) {
            val cursorX = paddingLeft + textPaint.measureText(prompt + currentCommand.toString())
            canvas.drawRect(cursorX, commandLineY + fontMetrics.ascent, 
                            cursorX + 2, commandLineY + fontMetrics.descent, textPaint)
        }
        
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle tap to focus and show keyboard
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!hasFocus()) {
                requestFocus()
            }
        }
        
        // Let the gesture detector handle touch events
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    // Handle backspace
                    if (currentCommand.isNotEmpty()) {
                        currentCommand.deleteCharAt(currentCommand.length - 1)
                        invalidate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    // Execute command
                    executeCommand()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Execute the current command in the terminal
     */
    // Keep track of current directory
    private var currentDirectory: String? = "/storage/emulated/0"
    
    // For Kali-style prompt
    private var username = "user"
    private var deviceName = "android"
    
    // ANSI color codes for terminal
    private var promptColor = "\u001B[1;32m" // Green
    private var pathColor = "\u001B[1;34m" // Blue
    private var resetColor = "\u001B[0m" // Reset
    private val redColor = "\u001B[31m"
    private val greenColor = "\u001B[32m"
    private val yellowColor = "\u001B[33m"
    private val blueColor = "\u001B[34m"
    private val magentaColor = "\u001B[35m"
    private val cyanColor = "\u001B[36m"
    private val whiteColor = "\u001B[37m"
    private val boldText = "\u001B[1m"
    private val purpleColor = "\u001B[38;5;93m"
    private val backgroundBlack = "\u001B[40m"
    
    /**
     * Get Kali Linux style prompt
     */
    private fun getPrompt(): String {
        // Initialize currentDirectory if it's null
        if (currentDirectory == null) {
            currentDirectory = "/storage/emulated/0"
        }
        
        val dir = if (currentDirectory == "/storage/emulated/0") {
            "~" // Show home directory as ~
        } else {
            // Extract last part of path
            val parts = currentDirectory?.split("/") ?: listOf()
            parts.lastOrNull() ?: currentDirectory ?: "~"
        }
        return "$promptColor$username@$deviceName$resetColor:$pathColor$dir$resetColor$ "
    }
    
    /**
     * Execute the current command in the terminal
     */
    private fun executeCommand() {
        val command = currentCommand.toString()
        
        // Add prompt with command to history
        val promptText = getPrompt().replace("$ ", "")
        lines.add("$promptText$ $command")
        
        // Split command to handle arguments
        val parts = command.trim().split("\\s+".toRegex())
        val cmd = parts.firstOrNull() ?: ""
        val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()
        
        when {
            cmd == "clear" -> {
                // Clear the terminal
                lines.clear()
                lines.add(getPrompt())
            }
            cmd == "help" -> {
                // Display available commands
                lines.add("AOShell Commands:")
                lines.add("  clear           - Clear the terminal screen")
                lines.add("  exit/quit       - Exit the application")
                lines.add("  help            - Display this help message")
                lines.add("  pwd             - Print working directory")
                lines.add("  ls [path]       - List files in directory")
                lines.add("  cd [path]       - Change directory")
                lines.add("  neofetch        - Display system information")
                lines.add("")
                lines.add("System Tools:")
                lines.add("  sysmon          - Show detailed system monitor")
                lines.add("  netmon          - Show network traffic monitor")
                lines.add("  settings        - Configure terminal settings")
                lines.add("  plugins         - Manage plugins (coming soon)")
                lines.add(getPrompt())
            }
            cmd == "exit" || cmd == "quit" -> {
                // Show exit message
                lines.add("Exiting AOShell...")
                
                // Schedule app exit after showing the message
                Handler(Looper.getMainLooper()).postDelayed({
                    // Exit the application using activity context
                    val activity = context as? Activity
                    activity?.finishAndRemoveTask()
                    
                    // If we can't get activity context, try alternative exit
                    if (activity == null) {
                        try {
                            // Force exit as fallback
                            Process.killProcess(Process.myPid())
                        } catch (e: Exception) {
                            // Last resort
                            System.exit(0)
                        }
                    }
                }, 500)
                lines.add(getPrompt())
            }
            cmd == "pwd" -> {
                // Print working directory
                lines.add(currentDirectory ?: "/storage/emulated/0")
                lines.add(getPrompt())
            }
            cmd == "sysmon" -> {
                // System Monitor functionality
                showSystemMonitor()
                lines.add(getPrompt())
            }
            cmd == "netmon" -> {
                // Network Monitor functionality
                showNetworkMonitor()
                lines.add(getPrompt())
            }
            cmd == "settings" -> {
                // Settings functionality
                showSettings()
                lines.add(getPrompt())
            }
            
            cmd == "ls" -> {
                // List files in current directory
                Thread {
                    try {
                        val directory = if (args.isNotEmpty()) {
                            val path = args[0]
                            if (path.startsWith("/")) {
                                // Absolute path
                                path
                            } else {
                                // Relative path
                                val curDir = currentDirectory ?: "/storage/emulated/0"
                                if (curDir.endsWith("/")) {
                                    "$curDir$path"
                                } else {
                                    "$curDir/$path"
                                }
                            }
                        } else {
                            currentDirectory ?: "/storage/emulated/0"
                        }
                        
                        val file = File(directory)
                        if (file.exists() && file.isDirectory) {
                            val files = file.listFiles()
                            val output = StringBuilder()
                            
                            files?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { f ->
                                val lastModified = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(f.lastModified()))
                                val size = if (f.isDirectory) "<DIR>" else humanReadableByteCount(f.length())
                                val permissions = if (f.canRead()) "r" else "-"
                                val permissions2 = if (f.canWrite()) "w" else "-"
                                val permissions3 = if (f.canExecute()) "x" else "-"
                                val name = if (f.isDirectory) "${f.name}/" else f.name
                                output.append("$permissions$permissions2$permissions3 $lastModified $size  $name\n")
                            }
                            
                            post {
                                processTerminalText(output.toString())
                                lines.add(getPrompt())
                                invalidate()
                            }
                        } else {
                            post {
                                lines.add("ls: cannot access '$directory': No such directory")
                                lines.add(getPrompt())
                                invalidate()
                            }
                        }
                    } catch (e: Exception) {
                        post {
                            lines.add("Error: ${e.message}")
                            lines.add(getPrompt())
                            invalidate()
                        }
                    }
                }.start()
            }
            
            cmd == "cd" -> {
                // Change directory
                if (args.isEmpty()) {
                    // Default to home directory
                    currentDirectory = "/storage/emulated/0"
                    lines.add(getPrompt())
                } else {
                    val targetDir = args[0]
                    Thread {
                        try {
                            val newPath = if (targetDir.startsWith("/")) {
                                // Absolute path
                                targetDir
                            } else if (targetDir == ".."){ 
                                // Go up one directory
                                val parent = File(currentDirectory ?: "/storage/emulated/0").parent
                                parent ?: "/"
                            } else {
                                // Relative path
                                val curDir = currentDirectory ?: "/storage/emulated/0"
                                if (curDir.endsWith("/")) {
                                    "$curDir$targetDir"
                                } else {
                                    "$curDir/$targetDir"
                                }
                            }
                            
                            val directory = File(newPath)
                            if (directory.exists() && directory.isDirectory) {
                                currentDirectory = newPath
                                post {
                                    lines.add(getPrompt())
                                    invalidate()
                                }
                            } else {
                                post {
                                    lines.add("cd: cannot change directory to '$targetDir': No such directory")
                                    lines.add(getPrompt())
                                    invalidate()
                                }
                            }
                        } catch (e: Exception) {
                            post {
                                lines.add("Error: ${e.message}")
                                lines.add(getPrompt())
                                invalidate()
                            }
                        }
                    }.start()
                }
            }
            
            cmd == "neofetch" -> {
                // Show system info with ASCII art logo
                Thread {
                    try {
                        val logo = StringBuilder()
                        logo.append(getAsciiLogo())
                        
                        // System info
                        val deviceInfo = StringBuilder()
                        deviceInfo.append("${pathColor}---------------------------------------${resetColor}\n")
                        deviceInfo.append("${promptColor}OS: ${resetColor}Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                        deviceInfo.append("${promptColor}Model: ${resetColor}${Build.MANUFACTURER} ${Build.MODEL}\n")
                        deviceInfo.append("${promptColor}Hardware: ${resetColor}${Build.HARDWARE}\n")
                        if (Build.SUPPORTED_ABIS.isNotEmpty()) {
                            deviceInfo.append("${promptColor}Architecture: ${resetColor}${Build.SUPPORTED_ABIS[0]}\n")
                        }
                        deviceInfo.append("${promptColor}User: ${resetColor}$username@$deviceName\n")
                        deviceInfo.append("${promptColor}Storage: ${resetColor}/storage/emulated/0\n")
                        deviceInfo.append("${pathColor}---------------------------------------${resetColor}\n")
                        
                        // Get additional info
                        val memInfo = Shell.SH.run("cat /proc/meminfo | grep MemTotal")
                        if (memInfo.isSuccessful && memInfo.stdout.isNotEmpty()) {
                            deviceInfo.append("${promptColor}Memory: ${resetColor}${memInfo.stdout.joinToString("").trim()}\n")
                        }
                        
                        val uptime = Shell.SH.run("uptime")
                        if (uptime.isSuccessful && uptime.stdout.isNotEmpty()) {
                            deviceInfo.append("${promptColor}Uptime: ${resetColor}${uptime.stdout.joinToString("").trim()}\n")
                        }
                        
                        post {
                            processTerminalText(logo.toString() + deviceInfo.toString())
                            lines.add(getPrompt())
                            invalidate()
                        }
                    } catch (e: Exception) {
                        post {
                            lines.add("Error getting system info: ${e.message}")
                            lines.add(getPrompt())
                            invalidate()
                        }
                    }
                }.start()
            }
            
            cmd == "info" -> {
                // Get system info
                Thread {
                    try {
                        val deviceInfo = StringBuilder()
                        deviceInfo.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                        deviceInfo.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                        deviceInfo.append("\n")
                        // Use Shell utility to get additional info
                        val memInfo = Shell.SH.run("cat /proc/meminfo | grep MemTotal")
                        val cpuInfo = Shell.SH.run("cat /proc/cpuinfo | grep Processor | head -1")
                        
                        if (memInfo.isSuccessful) {
                            deviceInfo.append("${memInfo.stdout.joinToString("").trim()}\n")
                        }
                        
                        if (cpuInfo.isSuccessful) {
                            deviceInfo.append("${cpuInfo.stdout.joinToString("").trim()}\n")
                        }
                        
                        post {
                            processTerminalText(deviceInfo.toString())
                            lines.add(getPrompt())
                            invalidate()
                        }
                    } catch (e: Exception) {
                        post {
                            lines.add("Error getting system info: ${e.message}")
                            lines.add("$ ")
                            invalidate()
                        }
                    }
                }.start()
            }
            else -> {
                // Try to execute the command through our TerminalSession or directly
                Thread {
                    try {
                        // Prepend cd to current directory to ensure commands run in proper context
                        val fullCommand = "cd \"${currentDirectory ?: "/storage/emulated/0"}\" && $command"
                        val result: CommandResult = Shell.SH.run(fullCommand)
                        // Convert output to string since result.stdout is a List<String>
                        val outputText = if (result.isSuccessful) {
                            result.stdout.joinToString("\n")
                        } else {
                            "${result.stderr.joinToString("\n")}\nExit code: ${result.exitCode}"
                        }
                        
                        post {
                            if (outputText.isNotBlank()) {
                                processTerminalText(outputText)
                            }
                            lines.add("$ ")
                            invalidate()
                        }
                    } catch (e: Exception) {
                        post {
                            lines.add("Error: ${e.message}")
                            lines.add("$ ")
                            invalidate()
                        }
                    }
                }.start()
            }
        }
        
        // Clear current command
        currentCommand.clear()
        invalidate()
        
        // Scroll to bottom
        post {
            lastScrollY = maxScrollY()
            invalidate()
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.let {
                    // Add text to current command
                    currentCommand.append(it)
                    invalidate()
                }
                return true
            }
            
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (currentCommand.isNotEmpty()) {
                    if (beforeLength > 0) {
                        val start = currentCommand.length - beforeLength
                        if (start >= 0) {
                            currentCommand.delete(start, currentCommand.length)
                            invalidate()
                        }
                    }
                }
                return true
            }
        }
    }

    /**
     * Show the soft keyboard
     */
    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
        post {
            // Sometimes the keyboard doesn't show on the first attempt
            // Try again after a short delay
            postDelayed({
                imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
            }, 100)
        }
    }

    /**
     * Calculate maximum scroll value
     */
    private fun maxScrollY(): Int {
        val fontMetrics = textPaint.fontMetrics
        val charHeight = fontMetrics.descent - fontMetrics.ascent
        val totalTextHeight = (lines.size + 1) * charHeight + paddingTop + paddingBottom
        return (totalTextHeight - height).coerceAtLeast(0f).toInt()
    }

    /**
     * Process text received from terminal session
     */
    private fun processTerminalText(text: String) {
        // For simplicity, split by newlines and add to lines
        val newLines = text.split("\n", "\r\n")
        lines.addAll(newLines.filter { it.isNotEmpty() })
        
        // Limit number of lines to prevent memory issues
        while (lines.size > 5000) {
            lines.removeFirst()
        }
        
        // Scroll to bottom to show new output
        post {
            lastScrollY = maxScrollY()
            invalidate()
        }
    }

    // TerminalSession.SessionCallback implementation
    override fun onSessionExit(session: TerminalSession) {
        post {
            lines.add("[Terminal session exited]")
            lines.add("$ ")
            invalidate()
        }
    }

    override fun onDataReceived(session: TerminalSession, data: ByteArray) {
        val text = String(data)
        post {
            processTerminalText(text)
        }
    }
    
    /**
     * Utility function to convert bytes to human-readable format
     */
    private fun humanReadableByteCount(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }
    
    /**
     * Set the font size for the terminal
     */
    fun setFontSize(size: Int) {
        fontSize = size.coerceIn(8, 32)  // Limit size to reasonable range
        textPaint.textSize = fontSize * resources.displayMetrics.density
        invalidate()
    }
    

    
    /**
     * Get the ASCII art logo for neofetch
     */
    private fun getAsciiLogo(): String {
        return """
            ${greenColor}╔════════════════════════════════════╗${resetColor}
            ${greenColor}║  ${boldText}${blueColor}  ▄▄▄      ▒█████    ▄████  ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor} ▒████▄   ▒██▒  ██▒ ██▒ ▀█▒ ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor} ▒██  ▀█▄ ▒██░  ██▒▒██░▄▄▄░ ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor} ░██▄▄▄▄██▒██   ██░░▓█  ██▓ ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor}  ▓█   ▓██░ ████▓▒░░▒▓███▀▒ ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor}  ▒▒   ▓▒█░ ▒░▒░▒░  ░▒   ▒  ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor}   ▒   ▒▒ ░ ░ ▒ ▒░   ░   ░  ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}║  ${boldText}${blueColor}   ░   ▒  ░ ░ ░ ▒  ░ ░   ░  ${resetColor}${greenColor} ║${resetColor}
            ${greenColor}╚════════════════════════════════════╝${resetColor}
        """.trimIndent()
    }
    
    /**
     * Display system monitor with CPU, memory, and storage details
     */
    private fun showSystemMonitor() {
        // Header
        lines.add("${greenColor}╔═══════════════ System Monitor ═══════════════╗${resetColor}")
        
        // Get CPU info
        lines.add("${greenColor}║ ${yellowColor}CPU Information:${resetColor}")
        try {
            val cpuInfo = Shell.SH.run("cat /proc/cpuinfo")
            if (cpuInfo.isSuccessful) {
                val processors = cpuInfo.stdout.filter { it.contains("processor") }.size
                val model = cpuInfo.stdout.firstOrNull { it.contains("model name") }?.replace("model name", "Model")?.trim() ?: "Unknown"
                lines.add("${greenColor}║${resetColor} Processors: $processors")
                lines.add("${greenColor}║${resetColor} $model")
                
                // Get CPU usage
                val topOutput = Shell.SH.run("top -n 1 | grep CPU")
                if (topOutput.isSuccessful && topOutput.stdout.isNotEmpty()) {
                    lines.add("${greenColor}║${resetColor} ${topOutput.stdout[0].trim()}")
                }
            }
        } catch (e: Exception) {
            lines.add("${greenColor}║${resetColor} Failed to get CPU information")
        }
        
        // Get Memory info
        lines.add("${greenColor}║")
        lines.add("${greenColor}║ ${yellowColor}Memory Information:${resetColor}")
        try {
            val memInfo = Shell.SH.run("cat /proc/meminfo")
            if (memInfo.isSuccessful) {
                val totalMem = memInfo.stdout.firstOrNull { it.contains("MemTotal") }?.trim()
                val freeMem = memInfo.stdout.firstOrNull { it.contains("MemFree") }?.trim()
                val availableMem = memInfo.stdout.firstOrNull { it.contains("MemAvailable") }?.trim()
                
                lines.add("${greenColor}║${resetColor} $totalMem")
                lines.add("${greenColor}║${resetColor} $freeMem")
                lines.add("${greenColor}║${resetColor} $availableMem")
            }
        } catch (e: Exception) {
            lines.add("${greenColor}║${resetColor} Failed to get memory information")
        }
        
        // Get Storage info
        lines.add("${greenColor}║")
        lines.add("${greenColor}║ ${yellowColor}Storage Information:${resetColor}")
        try {
            val dfOutput = Shell.SH.run("df -h /storage/emulated/0")
            if (dfOutput.isSuccessful && dfOutput.stdout.size > 1) {
                // Get the line with actual storage info (skip header)
                val storageInfo = dfOutput.stdout[1].trim().split("\\s+")
                if (storageInfo.size >= 5) {
                    lines.add("${greenColor}║${resetColor} Total: ${storageInfo[1]}")
                    lines.add("${greenColor}║${resetColor} Used: ${storageInfo[2]} (${storageInfo[4]})")
                    lines.add("${greenColor}║${resetColor} Free: ${storageInfo[3]}")
                }
            }
        } catch (e: Exception) {
            lines.add("${greenColor}║${resetColor} Failed to get storage information")
        }
        
        // Get battery info
        lines.add("${greenColor}║")
        lines.add("${greenColor}║ ${yellowColor}Battery Information:${resetColor}")
        try {
            val batteryInfo = Shell.SH.run("dumpsys battery | grep level")
            if (batteryInfo.isSuccessful && batteryInfo.stdout.isNotEmpty()) {
                lines.add("${greenColor}║${resetColor} ${batteryInfo.stdout[0].trim()}")
            }
            
            val batteryTemp = Shell.SH.run("dumpsys battery | grep temperature")
            if (batteryTemp.isSuccessful && batteryTemp.stdout.isNotEmpty()) {
                // Convert temperature to Celsius (value is in tenths of a degree)
                val tempLine = batteryTemp.stdout[0].trim()
                val tempValue = tempLine.split(":").getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val tempCelsius = tempValue / 10.0
                lines.add("${greenColor}║${resetColor} Temperature: $tempCelsius°C")
            }
        } catch (e: Exception) {
            lines.add("${greenColor}║${resetColor} Failed to get battery information")
        }
        
        // Footer
        lines.add("${greenColor}╚═══════════════════════════════════════════╝${resetColor}")
        lines.add("")
        lines.add("Type 'sysmon -r' to refresh the system monitor")
    }
    
    /**
     * Display network traffic monitor
     */
    private fun showNetworkMonitor() {
        // Header
        lines.add("${blueColor}╔═══════════════ Network Monitor ═══════════════╗${resetColor}")
        
        // Show network interfaces
        lines.add("${blueColor}║ ${yellowColor}Network Interfaces:${resetColor}")
        try {
            val netInterfaces = Shell.SH.run("ip addr show")
            if (netInterfaces.isSuccessful) {
                var currentInterface = ""
                for (line in netInterfaces.stdout) {
                    if (line.contains(": ")) {
                        // This is an interface line
                        currentInterface = line.trim()
                        if (currentInterface.contains("wlan") || currentInterface.contains("rmnet") || currentInterface.contains("eth")) {
                            lines.add("${blueColor}║${resetColor} $currentInterface")
                        }
                    } else if (line.contains("inet ") && (currentInterface.contains("wlan") || currentInterface.contains("rmnet") || currentInterface.contains("eth"))) {
                        // This is an IPv4 address line
                        lines.add("${blueColor}║${resetColor}   ${line.trim()}")
                    }
                }
            }
        } catch (e: Exception) {
            lines.add("${blueColor}║${resetColor} Failed to get network interfaces")
        }
        
        // Show network statistics
        lines.add("${blueColor}║")
        lines.add("${blueColor}║ ${yellowColor}Network Traffic:${resetColor}")
        try {
            val netStats = Shell.SH.run("cat /proc/net/dev")
            if (netStats.isSuccessful && netStats.stdout.size > 2) {
                // Skip the first two header lines
                for (i in 2 until netStats.stdout.size) {
                    val line = netStats.stdout[i].trim()
                    val parts = line.split(":").map { it.trim() }
                    if (parts.size >= 2) {
                        val interfaceName = parts[0]
                        // Only show relevant interfaces (wlan, rmnet, eth)
                        if (interfaceName.contains("wlan") || interfaceName.contains("rmnet") || interfaceName.contains("eth")) {
                            val stats = parts[1].split("\\s+")
                            if (stats.size >= 9) {
                                // Parse stats: [rx_bytes, rx_packets, rx_errs, ...]
                                val rxBytes = formatBytes(stats[0].toLongOrNull() ?: 0)
                                val txBytes = formatBytes(stats[8].toLongOrNull() ?: 0)
                                
                                lines.add("${blueColor}║${resetColor} $interfaceName:")
                                lines.add("${blueColor}║${resetColor}   Received: $rxBytes")
                                lines.add("${blueColor}║${resetColor}   Transmitted: $txBytes")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            lines.add("${blueColor}║${resetColor} Failed to get network statistics")
        }
        
        // Show active connections
        lines.add("${blueColor}║")
        lines.add("${blueColor}║ ${yellowColor}Active Connections:${resetColor}")
        try {
            val connections = Shell.SH.run("netstat -tn | grep ESTABLISHED | head -5")
            if (connections.isSuccessful && connections.stdout.isNotEmpty()) {
                lines.add("${blueColor}║${resetColor} ESTABLISHED connections:")
                for (conn in connections.stdout) {
                    lines.add("${blueColor}║${resetColor}   ${conn.trim()}")
                }
            } else {
                lines.add("${blueColor}║${resetColor} No active connections found")
            }
        } catch (e: Exception) {
            lines.add("${blueColor}║${resetColor} Failed to get active connections")
        }
        
        // Footer
        lines.add("${blueColor}╚═══════════════════════════════════════════╝${resetColor}")
        lines.add("")
        lines.add("Type 'netmon -r' to refresh network statistics")
    }
    
    /**
     * Format bytes to human-readable format
     */
    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
    
    /**
     * Display terminal settings
     */
    private fun showSettings() {
        // Header
        lines.add("${purpleColor}╔═══════════════ Terminal Settings ═══════════════╗${resetColor}")
        
        // Settings menu
        lines.add("${purpleColor}║ ${yellowColor}Terminal Configuration:${resetColor}")
        lines.add("${purpleColor}║${resetColor} 1. Change Font Size (current: ${fontSize}px)")
        lines.add("${purpleColor}║${resetColor} 2. Change Text Color")
        lines.add("${purpleColor}║${resetColor} 3. Change Background Color")
        lines.add("${purpleColor}║${resetColor} 4. Change Prompt Style")
        lines.add("${purpleColor}║")
        
        // Appearance settings
        lines.add("${purpleColor}║ ${yellowColor}Appearance:${resetColor}")
        lines.add("${purpleColor}║${resetColor} 5. Toggle Dark Mode")
        lines.add("${purpleColor}║${resetColor} 6. Toggle Bold Text")
        lines.add("${purpleColor}║")
        
        // System settings
        lines.add("${purpleColor}║ ${yellowColor}System:${resetColor}")
        lines.add("${purpleColor}║${resetColor} 7. Reset All Settings")
        lines.add("${purpleColor}║${resetColor} 8. About AOShell")
        
        // Footer
        lines.add("${purpleColor}╚═══════════════════════════════════════════╝${resetColor}")
        lines.add("")
        lines.add("To change a setting, use 'settings <number>'")
        lines.add("Example: 'settings 1 14' to set font size to 14px")
    }
}
