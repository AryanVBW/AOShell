package com.advancedterminal.app.linux

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.advancedterminal.app.AdvancedTerminalApp
import com.advancedterminal.app.terminal.TerminalSession
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages Linux distributions for the terminal app
 */
class LinuxDistributionManager(private val context: Context) {

    companion object {
        private const val TAG = "LinuxDistroManager"
        private const val PROOT_BINARY = "proot"
        private const val BUSYBOX_BINARY = "busybox"
        private const val BOOTSTRAP_ARCHIVE = "bootstrap.tar.xz"
        
        // Singleton instance
        @Volatile
        private var INSTANCE: LinuxDistributionManager? = null
        
        fun getInstance(context: Context): LinuxDistributionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LinuxDistributionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // HTTP client for downloading distributions
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Thread pool for distribution operations
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // LiveData for installation progress
    private val _installProgress = MutableLiveData<Pair<LinuxDistribution, Int>>()
    val installProgress: LiveData<Pair<LinuxDistribution, Int>> = _installProgress
    
    // LiveData for installation status
    private val _installStatus = MutableLiveData<Pair<LinuxDistribution, LinuxDistribution.InstallationStatus>>()
    val installStatus: LiveData<Pair<LinuxDistribution, LinuxDistribution.InstallationStatus>> = _installStatus
    
    // LiveData for current active distribution
    private val _activeDistribution = MutableLiveData<LinuxDistribution?>()
    val activeDistribution: LiveData<LinuxDistribution?> = _activeDistribution
    
    // Current active session for a Linux distribution
    private var activeLinuxSession: TerminalSession? = null
    
    // Get list of all available distributions with their installation status
    fun getAvailableDistributions(): List<LinuxDistribution> {
        val distributions = LinuxDistribution.getAllDistributions()
        
        // Update installation status of each distribution
        distributions.forEach { distribution ->
            distribution.installationStatus = if (distribution.isInstalled(context)) {
                LinuxDistribution.InstallationStatus.INSTALLED
            } else {
                LinuxDistribution.InstallationStatus.NOT_INSTALLED
            }
        }
        
        return distributions
    }
    
    /**
     * Install a Linux distribution
     */
    fun installDistribution(distribution: LinuxDistribution, listener: InstallationListener? = null) {
        if (distribution.installationStatus == LinuxDistribution.InstallationStatus.INSTALLED) {
            listener?.onInstallationComplete(distribution)
            return
        }
        
        // Create necessary directories
        val installDir = distribution.getInstallDir(context)
        val rootfsDir = distribution.getRootfsDir(context)
        
        if (!installDir.exists()) {
            installDir.mkdirs()
        }
        
        // Check for PRoot and Busybox binaries, extract if needed
        installSupportBinaries()
        
        // Update status
        distribution.installationStatus = LinuxDistribution.InstallationStatus.DOWNLOADING
        _installStatus.postValue(Pair(distribution, distribution.installationStatus))
        
        // Start installation in background
        executor.execute {
            try {
                // Step 1: Download distribution
                listener?.onInstallationProgress(distribution, 0, "Starting download...")
                val downloadFile = File(installDir, BOOTSTRAP_ARCHIVE)
                
                downloadDistribution(distribution, downloadFile) { progress ->
                    _installProgress.postValue(Pair(distribution, progress))
                    listener?.onInstallationProgress(distribution, progress, "Downloading... $progress%")
                }
                
                // Step 2: Verify hash
                listener?.onInstallationProgress(distribution, 50, "Verifying download...")
                if (!verifyDownload(downloadFile, distribution.archiveSha256)) {
                    throw IOException("Archive checksum verification failed")
                }
                
                // Step 3: Extract distribution
                distribution.installationStatus = LinuxDistribution.InstallationStatus.EXTRACTING
                _installStatus.postValue(Pair(distribution, distribution.installationStatus))
                listener?.onInstallationProgress(distribution, 60, "Extracting files...")
                
                if (!rootfsDir.exists()) {
                    rootfsDir.mkdirs()
                }
                
                extractDistribution(downloadFile, rootfsDir) { progress ->
                    val extractProgress = 60 + progress * 0.3 // Scale to 60-90%
                    _installProgress.postValue(Pair(distribution, extractProgress.toInt()))
                    listener?.onInstallationProgress(
                        distribution, 
                        extractProgress.toInt(), 
                        "Extracting files... ${extractProgress.toInt()}%"
                    )
                }
                
                // Step 4: Configure distribution
                distribution.installationStatus = LinuxDistribution.InstallationStatus.CONFIGURING
                _installStatus.postValue(Pair(distribution, distribution.installationStatus))
                listener?.onInstallationProgress(distribution, 90, "Configuring distribution...")
                
                configureDistribution(distribution)
                
                // Step 5: Cleanup
                downloadFile.delete()
                
                // Update status
                distribution.installationStatus = LinuxDistribution.InstallationStatus.INSTALLED
                _installStatus.postValue(Pair(distribution, distribution.installationStatus))
                listener?.onInstallationProgress(distribution, 100, "Installation complete!")
                listener?.onInstallationComplete(distribution)
                
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed for ${distribution.name}: ${e.message}", e)
                distribution.installationStatus = LinuxDistribution.InstallationStatus.ERROR
                _installStatus.postValue(Pair(distribution, distribution.installationStatus))
                listener?.onInstallationError(distribution, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Launch a Linux distribution in a terminal session
     */
    fun launchDistribution(
        distribution: LinuxDistribution, 
        terminalSessionFactory: TerminalSessionFactory
    ): TerminalSession? {
        if (!distribution.isInstalled(context)) {
            Log.e(TAG, "Cannot launch ${distribution.name}: not installed")
            return null
        }
        
        try {
            val installDir = distribution.getInstallDir(context)
            val rootfsDir = distribution.getRootfsDir(context)
            
            // Path to support binaries
            val prootPath = File(context.applicationInfo.nativeLibraryDir, PROOT_BINARY).absolutePath
            
            // Build the command to launch the distribution with PRoot
            val command = StringBuilder(prootPath)
            
            // Add PRoot options
            command.append(" -r ${rootfsDir.absolutePath}")
            command.append(" -w /root")
            command.append(" -b /dev")
            command.append(" -b /proc")
            command.append(" -b /sys")
            command.append(" -b /sdcard:/sdcard")
            
            // Define command to run inside the chroot
            command.append(" /bin/login -f root")
            
            // Create environment variables
            val env = arrayOf(
                "HOME=/root",
                "TERM=xterm-256color",
                "USER=root",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "LANG=C.UTF-8",
                "SHELL=/bin/bash"
            )
            
            // Create the session using the provided factory
            activeLinuxSession = terminalSessionFactory.createSession(
                "/system/bin/sh",
                installDir.absolutePath,
                arrayOf("-c", command.toString()),
                env
            )
            
            _activeDistribution.postValue(distribution)
            return activeLinuxSession
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${distribution.name}: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Uninstall a Linux distribution
     */
    fun uninstallDistribution(distribution: LinuxDistribution, listener: UninstallListener? = null) {
        if (!distribution.isInstalled(context)) {
            listener?.onUninstallComplete(distribution)
            return
        }
        
        executor.execute {
            try {
                val installDir = distribution.getInstallDir(context)
                
                // Ensure no active session is using this distribution
                if (activeDistribution.value == distribution && activeLinuxSession?.isRunning() == true) {
                    activeLinuxSession?.finish()
                    activeLinuxSession = null
                    _activeDistribution.postValue(null)
                }
                
                // Delete the distribution directory
                deleteRecursive(installDir)
                
                distribution.installationStatus = LinuxDistribution.InstallationStatus.NOT_INSTALLED
                _installStatus.postValue(Pair(distribution, distribution.installationStatus))
                
                listener?.onUninstallComplete(distribution)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall ${distribution.name}: ${e.message}", e)
                listener?.onUninstallError(distribution, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Install support binaries needed for distribution operations
     */
    private fun installSupportBinaries() {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        
        val prootFile = File(libDir, PROOT_BINARY)
        val busyboxFile = File(libDir, BUSYBOX_BINARY)
        
        // TODO: Extract binaries from assets if they don't exist
        // This should be implemented later with proper binary packaging
    }
    
    /**
     * Download a distribution archive
     */
    private fun downloadDistribution(
        distribution: LinuxDistribution,
        targetFile: File,
        progressCallback: (Int) -> Unit
    ) {
        val request = Request.Builder()
            .url(distribution.archiveUrl)
            .build()
            
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download: ${response.code}")
            }
            
            val contentLength = response.body?.contentLength() ?: -1L
            
            response.body?.let { body ->
                val inputStream = BufferedInputStream(body.byteStream())
                val outputStream = FileOutputStream(targetFile)
                
                try {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgressUpdate = 0
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            if (progress > lastProgressUpdate) {
                                progressCallback(progress)
                                lastProgressUpdate = progress
                            }
                        }
                    }
                    
                    progressCallback(100)
                } finally {
                    outputStream.close()
                    inputStream.close()
                }
            }
        }
    }
    
    /**
     * Verify the downloaded archive against its checksum
     */
    private fun verifyDownload(file: File, expectedHash: String): Boolean {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = file.inputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            
            inputStream.close()
            
            val hashBytes = digest.digest()
            val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
            
            return hashHex == expectedHash
        } catch (e: Exception) {
            Log.e(TAG, "Hash verification failed: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Extract a distribution archive
     */
    private fun extractDistribution(
        archiveFile: File,
        targetDir: File,
        progressCallback: (Int) -> Unit
    ) {
        // Use shell command to extract using tar
        val process = ProcessBuilder(
            "tar", "-xf", archiveFile.absolutePath, "-C", targetDir.absolutePath
        ).redirectErrorStream(true).start()
        
        // Start a thread to monitor extraction progress
        Thread {
            var progress = 0
            while (process.isAlive) {
                if (progress < 90) {
                    progress += 5
                    progressCallback(progress)
                }
                Thread.sleep(1000)
            }
            progressCallback(100)
        }.start()
        
        // Wait for extraction to complete
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("Extraction failed with code $exitCode")
        }
    }
    
    /**
     * Configure the distribution after extraction
     */
    private fun configureDistribution(distribution: LinuxDistribution) {
        val rootfsDir = distribution.getRootfsDir(context)
        
        // Create configuration script
        val configScript = """
            #!/bin/sh
            # Set up essential configuration

            # Configure hostname
            echo "${distribution.id}" > /etc/hostname

            # Configure DNS
            echo "nameserver 8.8.8.8" > /etc/resolv.conf
            echo "nameserver 8.8.4.4" >> /etc/resolv.conf

            # Set up basic profile
            cat > /root/.profile << 'EOF'
            export LANG=C.UTF-8
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export TERM=xterm-256color
            export PS1='\[\e[0;32m\]\u@\h\[\e[m\] \[\e[1;34m\]\w\[\e[m\] \[\e[1;32m\]\$\[\e[m\] '
            alias ll='ls -la'
            EOF

            # Create .bashrc
            cat > /root/.bashrc << 'EOF'
            [ -f /root/.profile ] && . /root/.profile
            EOF

            # Make login work without password
            sed -i 's/nullok_secure/nullok/' /etc/pam.d/common-auth 2>/dev/null || true

            # Set root password to empty for convenience
            if command -v passwd > /dev/null; then
                echo "root::0:0:root:/root:/bin/bash" > /etc/passwd
                passwd -d root > /dev/null 2>&1 || true
            fi

            # Create the /sdcard symlink if it doesn't exist
            if [ ! -d /sdcard ]; then
                mkdir -p /sdcard
            fi

            # Create startup script
            cat > /etc/profile.d/welcome.sh << 'EOF'
            #!/bin/sh
            echo ""
            echo "Welcome to ${distribution.name} ${distribution.version}"
            echo "Type 'apt update && apt upgrade' to update the system packages."
            echo ""
            uname -a
            echo ""
            EOF
            chmod +x /etc/profile.d/welcome.sh

            exit 0
        """.trimIndent()

        // Write the script to a temporary file
        val scriptFile = File(context.cacheDir, "configure_${distribution.id}.sh")
        scriptFile.writeText(configScript)
        scriptFile.setExecutable(true)
        
        // Get path to proot binary
        val prootPath = File(context.applicationInfo.nativeLibraryDir, PROOT_BINARY).absolutePath
        
        // Execute the configuration script inside the chroot
        val process = ProcessBuilder(
            prootPath,
            "-r", rootfsDir.absolutePath,
            "-w", "/root",
            "-b", "/proc",
            "-b", "/sys",
            "/bin/sh", scriptFile.absolutePath
        ).redirectErrorStream(true).start()
        
        // Wait for configuration to complete
        val exitCode = process.waitFor()
        scriptFile.delete()
        
        if (exitCode != 0) {
            throw IOException("Configuration failed with code $exitCode")
        }
    }
    
    /**
     * Recursively delete a directory and its contents
     */
    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            val children = fileOrDirectory.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursive(child)
                }
            }
        }
        fileOrDirectory.delete()
    }
    
    /**
     * Interface for creating terminal sessions
     */
    interface TerminalSessionFactory {
        fun createSession(
            executable: String,
            cwd: String,
            args: Array<String>,
            env: Array<String>
        ): TerminalSession
    }
    
    /**
     * Interface for installation callbacks
     */
    interface InstallationListener {
        fun onInstallationProgress(distribution: LinuxDistribution, progress: Int, message: String)
        fun onInstallationComplete(distribution: LinuxDistribution)
        fun onInstallationError(distribution: LinuxDistribution, error: String)
    }
    
    /**
     * Interface for uninstallation callbacks
     */
    interface UninstallListener {
        fun onUninstallComplete(distribution: LinuxDistribution)
        fun onUninstallError(distribution: LinuxDistribution, error: String)
    }
}
