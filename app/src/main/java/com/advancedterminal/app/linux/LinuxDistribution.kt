package com.advancedterminal.app.linux

import android.content.Context
import java.io.File

/**
 * Represents a Linux distribution that can be installed and used in the terminal.
 */
data class LinuxDistribution(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val archiveUrl: String,
    val archiveSha256: String,
    val sizeInMb: Int,
    val requiredCommands: List<String> = listOf("proot", "tar"),
    var installationStatus: InstallationStatus = InstallationStatus.NOT_INSTALLED
) {
    enum class InstallationStatus {
        NOT_INSTALLED,
        DOWNLOADING,
        EXTRACTING,
        CONFIGURING,
        INSTALLED,
        ERROR
    }
    
    /**
     * Get the installation directory for this distribution
     */
    fun getInstallDir(context: Context): File {
        return File(context.filesDir, "linux_distros/$id")
    }
    
    /**
     * Get the root filesystem directory for this distribution
     */
    fun getRootfsDir(context: Context): File {
        return File(getInstallDir(context), "rootfs")
    }
    
    /**
     * Check if this distribution is installed
     */
    fun isInstalled(context: Context): Boolean {
        val rootfsDir = getRootfsDir(context)
        return rootfsDir.exists() && 
               File(rootfsDir, "bin").exists() && 
               File(rootfsDir, "usr").exists() && 
               File(rootfsDir, "etc").exists()
    }
    
    companion object {
        // Define available distributions
        val DEBIAN_BOOKWORM = LinuxDistribution(
            id = "debian_bookworm",
            name = "Debian",
            version = "12 (Bookworm)",
            description = "Debian is a free operating system that comes with over 59000 packages. " +
                          "It's known for its stability and security.",
            archiveUrl = "https://github.com/termux/proot-distro/releases/download/v3.10.0/debian-bookworm-aarch64-pd-v3.10.0.tar.xz",
            archiveSha256 = "2a298d5caef1365653e6ec5d33c963c60c7ce74628f05157c45211b3c9b56cf0",
            sizeInMb = 140
        )
        
        val UBUNTU_JAMMY = LinuxDistribution(
            id = "ubuntu_jammy",
            name = "Ubuntu",
            version = "22.04 LTS (Jammy Jellyfish)",
            description = "Ubuntu is a popular Linux distribution based on Debian. " +
                          "It provides regular releases with long-term support (LTS) options.",
            archiveUrl = "https://github.com/termux/proot-distro/releases/download/v3.10.0/ubuntu-jammy-aarch64-pd-v3.10.0.tar.xz",
            archiveSha256 = "6023d9c330baa4e44ec1d20f26a8c66c7000be44737314be12f0096c7968dec5",
            sizeInMb = 230
        )
        
        val ALPINE = LinuxDistribution(
            id = "alpine",
            name = "Alpine Linux",
            version = "3.18",
            description = "Alpine Linux is a security-oriented, lightweight Linux distribution. " +
                          "It uses musl libc and busybox to keep the system small and efficient.",
            archiveUrl = "https://github.com/termux/proot-distro/releases/download/v3.10.0/alpine-3.18-aarch64-pd-v3.10.0.tar.xz",
            archiveSha256 = "fa7df00d407c273a02a51db9e6952eb3bdf163c7244236f1033c86ff7c82c483",
            sizeInMb = 12
        )
        
        // Get all available distributions
        fun getAllDistributions(): List<LinuxDistribution> {
            return listOf(DEBIAN_BOOKWORM, UBUNTU_JAMMY, ALPINE)
        }
    }
}
