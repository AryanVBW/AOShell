package com.advancedterminal.app.linux

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.advancedterminal.app.databinding.ActivityLinuxEnvironmentBinding
import com.advancedterminal.app.service.TerminalService
import com.advancedterminal.app.terminal.TerminalSession
import com.advancedterminal.app.ui.MainActivity

/**
 * Activity for managing Linux distributions
 */
class LinuxEnvironmentActivity : AppCompatActivity(), 
    LinuxDistributionAdapter.DistributionActionListener,
    LinuxDistributionManager.InstallationListener,
    LinuxDistributionManager.UninstallListener {

    private lateinit var binding: ActivityLinuxEnvironmentBinding
    private lateinit var distributionManager: LinuxDistributionManager
    private lateinit var adapter: LinuxDistributionAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinuxEnvironmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize distribution manager
        distributionManager = LinuxDistributionManager.getInstance(applicationContext)
        
        // Set up RecyclerView
        binding.rvDistributions.layoutManager = LinearLayoutManager(this)
        adapter = LinuxDistributionAdapter(this)
        binding.rvDistributions.adapter = adapter
        
        // Observe installation progress
        distributionManager.installProgress.observe(this, Observer { (distribution, progress) ->
            adapter.updateProgress(distribution, progress)
        })
        
        // Observe installation status
        distributionManager.installStatus.observe(this, Observer { (distribution, status) ->
            adapter.updateStatus(distribution, status)
        })
        
        // Load distributions
        loadDistributions()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun loadDistributions() {
        binding.progressLoading.visibility = View.VISIBLE
        
        // Load available distributions in background
        Thread {
            val distributions = distributionManager.getAvailableDistributions()
            runOnUiThread {
                binding.progressLoading.visibility = View.GONE
                adapter.setDistributions(distributions)
            }
        }.start()
    }
    
    override fun onInstallClicked(distribution: LinuxDistribution) {
        distributionManager.installDistribution(distribution, this)
    }
    
    override fun onLaunchClicked(distribution: LinuxDistribution) {
        // Create a terminal session factory
        val terminalSessionFactory = object : LinuxDistributionManager.TerminalSessionFactory {
            override fun createSession(
                executable: String,
                cwd: String,
                args: Array<String>,
                env: Array<String>
            ): TerminalSession {
                return TerminalSession(executable, cwd, args, env)
            }
        }
        
        // Launch the distribution
        val session = distributionManager.launchDistribution(distribution, terminalSessionFactory)
        
        if (session != null) {
            // Pass the session back to MainActivity
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("LAUNCH_LINUX_SESSION", true)
                // We can't directly pass the session, so we'll retrieve it in MainActivity
                // from the LinuxDistributionManager
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Failed to launch ${distribution.name}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onUninstallClicked(distribution: LinuxDistribution) {
        distributionManager.uninstallDistribution(distribution, this)
    }
    
    // InstallationListener implementation
    override fun onInstallationProgress(
        distribution: LinuxDistribution,
        progress: Int,
        message: String
    ) {
        runOnUiThread {
            adapter.updateProgress(distribution, progress)
            adapter.updateProgressMessage(distribution, message)
        }
    }
    
    override fun onInstallationComplete(distribution: LinuxDistribution) {
        runOnUiThread {
            Toast.makeText(
                this,
                "${distribution.name} ${distribution.version} installed successfully",
                Toast.LENGTH_SHORT
            ).show()
            adapter.updateStatus(distribution, LinuxDistribution.InstallationStatus.INSTALLED)
        }
    }
    
    override fun onInstallationError(distribution: LinuxDistribution, error: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                "Installation failed: $error",
                Toast.LENGTH_LONG
            ).show()
            adapter.updateStatus(distribution, LinuxDistribution.InstallationStatus.ERROR)
        }
    }
    
    // UninstallListener implementation
    override fun onUninstallComplete(distribution: LinuxDistribution) {
        runOnUiThread {
            Toast.makeText(
                this,
                "${distribution.name} ${distribution.version} uninstalled successfully",
                Toast.LENGTH_SHORT
            ).show()
            adapter.updateStatus(distribution, LinuxDistribution.InstallationStatus.NOT_INSTALLED)
        }
    }
    
    override fun onUninstallError(distribution: LinuxDistribution, error: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                "Uninstallation failed: $error",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
