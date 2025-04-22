package com.advancedterminal.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.advancedterminal.app.R
import com.advancedterminal.app.databinding.ActivityMainBinding
import com.advancedterminal.app.service.TerminalService
import com.advancedterminal.app.terminal.TerminalSession
import com.advancedterminal.app.terminal.TerminalView
import com.advancedterminal.app.ui.SystemMonitorActivity

/**
 * Main activity for the Advanced Terminal application.
 * Hosts the terminal interface and manages user interactions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var terminalView: TerminalView
    private var terminalService: TerminalService? = null
    private var currentSession: TerminalSession? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalService.LocalBinder
            terminalService = binder.getService()
            setupTerminal()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up window and status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Inflate layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        
        // Ensure the window allows the keyboard to appear
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        // Initialize views
        terminalView = binding.terminalView
        
        // Bind to terminal service
        val intent = Intent(this, TerminalService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Set up UI listeners
        setupListeners()
    }
    
    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_session -> {
                createNewSession()
                true
            }
            R.id.action_system_monitor -> {
                // Launch System Monitor activity
                val intent = Intent(this, SystemMonitorActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_network_tools -> {
                Toast.makeText(this, "Network Tools feature coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_plugins -> {
                Toast.makeText(this, "Plugins feature coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_settings -> {
                Toast.makeText(this, "Settings feature coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle special key combinations here
        return super.onKeyDown(keyCode, event)
    }
    
    private fun setupTerminal() {
        terminalService?.let { service ->
            if (service.sessions.isEmpty()) {
                createNewSession()
            } else {
                // Attach to existing session
                currentSession = service.sessions[0]
                terminalView.attachSession(currentSession!!)
            }
        }
    }
    
    private fun createNewSession() {
        terminalService?.let { service ->
            currentSession = service.createSession()
            terminalView.attachSession(currentSession!!)
            Toast.makeText(this, "New terminal session created", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupListeners() {
        // Set up keyboard shortcuts and gesture handlers
        binding.fab.setOnClickListener {
            terminalView.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun showQuickActionsMenu() {
        // TODO: Implement quick actions menu for common terminal commands
        Toast.makeText(this, "Quick actions menu (coming soon)", Toast.LENGTH_SHORT).show()
    }
}
