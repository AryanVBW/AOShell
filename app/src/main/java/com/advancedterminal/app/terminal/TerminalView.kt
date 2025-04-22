package com.advancedterminal.app.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
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
    private var lastScrollY = 0
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
                requestFocus()
                showKeyboard()
                return true
            }
        }
    )

    init {
        // Make view focusable to receive key events
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Add welcome message
        lines.add("AdvancedTerminal - Android Terminal Emulator")
        lines.add("Type 'help' for available commands")
        lines.add("$ ")
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
    private var currentDirectory = "/storage/emulated/0"
    
    /**
     * Execute the current command in the terminal
     */
    private fun executeCommand() {
        val command = currentCommand.toString()
        lines.add("$ $command")
        
        // Split command to handle arguments
        val parts = command.trim().split("\\s+".toRegex())
        val cmd = parts.firstOrNull() ?: ""
        val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()
        
        when {
            cmd == "clear" -> {
                // Clear the terminal
                lines.clear()
                lines.add("$ ")
            }
            cmd == "help" -> {
                // Show help information
                lines.add("Available commands:")
                lines.add("  clear - Clear the terminal")
                lines.add("  exit - Close the current session")
                lines.add("  help - Show this help")
                lines.add("  info - Show system information")
                lines.add("  ls - List directory contents")
                lines.add("  cd - Change directory")
                lines.add("  pwd - Print working directory")
                lines.add("  Any other commands will be executed in the shell")
                lines.add("$ ")
            }
            command == "exit" -> {
                lines.add("Closing session...")
                terminalSession?.finish()
                lines.add("Session closed")
                lines.add("$ ")
            }
            cmd == "pwd" -> {
                // Print working directory
                lines.add(currentDirectory)
                lines.add("$ ")
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
                                if (currentDirectory.endsWith("/")) {
                                    "$currentDirectory$path"
                                } else {
                                    "$currentDirectory/$path"
                                }
                            }
                        } else {
                            currentDirectory
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
                                lines.add("$ ")
                                invalidate()
                            }
                        } else {
                            post {
                                lines.add("ls: cannot access '$directory': No such directory")
                                lines.add("$ ")
                                invalidate()
                            }
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
            
            cmd == "cd" -> {
                // Change directory
                if (args.isEmpty()) {
                    // Default to home directory
                    currentDirectory = "/storage/emulated/0"
                    lines.add("$ ")
                } else {
                    val targetDir = args[0]
                    Thread {
                        try {
                            val newPath = if (targetDir.startsWith("/")) {
                                // Absolute path
                                targetDir
                            } else if (targetDir == ".."){ 
                                // Go up one directory
                                val parent = File(currentDirectory).parent
                                parent ?: "/"
                            } else {
                                // Relative path
                                if (currentDirectory.endsWith("/")) {
                                    "$currentDirectory$targetDir"
                                } else {
                                    "$currentDirectory/$targetDir"
                                }
                            }
                            
                            val directory = File(newPath)
                            if (directory.exists() && directory.isDirectory) {
                                currentDirectory = newPath
                                post {
                                    lines.add("$ ")
                                    invalidate()
                                }
                            } else {
                                post {
                                    lines.add("cd: cannot change directory to '$targetDir': No such directory")
                                    lines.add("$ ")
                                    invalidate()
                                }
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
            
            cmd == "info" -> {
                // Get system info
                Thread {
                    try {
                        val deviceInfo = StringBuilder()
                        deviceInfo.append("Android Version: ${android.os.Build.VERSION.RELEASE}\n")
                        deviceInfo.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                        
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
                            lines.add("$ ")
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
                        val fullCommand = "cd \"$currentDirectory\" && $command"
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
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        
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
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
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
}
