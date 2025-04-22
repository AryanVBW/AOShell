package com.advancedterminal.app.linux

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.advancedterminal.app.R

/**
 * Adapter for displaying Linux distributions in a RecyclerView
 */
class LinuxDistributionAdapter(
    private val actionListener: DistributionActionListener
) : RecyclerView.Adapter<LinuxDistributionAdapter.DistributionViewHolder>() {

    private val distributions = mutableListOf<LinuxDistribution>()
    
    fun setDistributions(newDistributions: List<LinuxDistribution>) {
        distributions.clear()
        distributions.addAll(newDistributions)
        notifyDataSetChanged()
    }
    
    fun updateProgress(distribution: LinuxDistribution, progress: Int) {
        val position = distributions.indexOfFirst { it.id == distribution.id }
        if (position != -1) {
            notifyItemChanged(position, ProgressUpdate(progress))
        }
    }
    
    fun updateProgressMessage(distribution: LinuxDistribution, message: String) {
        val position = distributions.indexOfFirst { it.id == distribution.id }
        if (position != -1) {
            notifyItemChanged(position, ProgressMessage(message))
        }
    }
    
    fun updateStatus(distribution: LinuxDistribution, status: LinuxDistribution.InstallationStatus) {
        val position = distributions.indexOfFirst { it.id == distribution.id }
        if (position != -1) {
            distributions[position].installationStatus = status
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DistributionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_linux_distribution, parent, false)
        return DistributionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DistributionViewHolder, position: Int) {
        holder.bind(distributions[position])
    }
    
    override fun onBindViewHolder(
        holder: DistributionViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                when (payload) {
                    is ProgressUpdate -> {
                        holder.progressInstall.progress = payload.progress
                    }
                    is ProgressMessage -> {
                        holder.tvProgressText.text = payload.message
                    }
                }
            }
        }
    }

    override fun getItemCount() = distributions.size

    inner class DistributionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDistroLogo: ImageView = itemView.findViewById(R.id.ivDistroLogo)
        private val tvDistroName: TextView = itemView.findViewById(R.id.tvDistroName)
        private val tvDistroVersion: TextView = itemView.findViewById(R.id.tvDistroVersion)
        private val tvDistroDescription: TextView = itemView.findViewById(R.id.tvDistroDescription)
        private val tvDistroSize: TextView = itemView.findViewById(R.id.tvDistroSize)
        private val tvDistroStatus: TextView = itemView.findViewById(R.id.tvDistroStatus)
        private val btnInstall: Button = itemView.findViewById(R.id.btnInstall)
        private val btnLaunch: Button = itemView.findViewById(R.id.btnLaunch)
        val progressInstall: ProgressBar = itemView.findViewById(R.id.progressInstall)
        val tvProgressText: TextView = itemView.findViewById(R.id.tvProgressText)
        private val actionButtons: View = itemView.findViewById(R.id.actionButtons)

        fun bind(distribution: LinuxDistribution) {
            tvDistroName.text = distribution.name
            tvDistroVersion.text = distribution.version
            tvDistroDescription.text = distribution.description
            tvDistroSize.text = "Size: ${distribution.sizeInMb} MB"
            
            // Set distribution logo based on ID
            when (distribution.id) {
                "debian_bookworm" -> ivDistroLogo.setImageResource(R.drawable.ic_debian)
                "ubuntu_jammy" -> ivDistroLogo.setImageResource(R.drawable.ic_ubuntu)
                "alpine" -> ivDistroLogo.setImageResource(R.drawable.ic_alpine)
                else -> ivDistroLogo.setImageResource(R.drawable.ic_linux)
            }
            
            // Update status text and buttons based on installation status
            when (distribution.installationStatus) {
                LinuxDistribution.InstallationStatus.NOT_INSTALLED -> {
                    tvDistroStatus.text = "Status: Not Installed"
                    btnInstall.text = "Install"
                    btnInstall.isEnabled = true
                    btnLaunch.isEnabled = false
                    actionButtons.visibility = View.VISIBLE
                    progressInstall.visibility = View.GONE
                    tvProgressText.visibility = View.GONE
                }
                LinuxDistribution.InstallationStatus.DOWNLOADING,
                LinuxDistribution.InstallationStatus.EXTRACTING,
                LinuxDistribution.InstallationStatus.CONFIGURING -> {
                    tvDistroStatus.text = "Status: Installing..."
                    actionButtons.visibility = View.GONE
                    progressInstall.visibility = View.VISIBLE
                    tvProgressText.visibility = View.VISIBLE
                }
                LinuxDistribution.InstallationStatus.INSTALLED -> {
                    tvDistroStatus.text = "Status: Installed"
                    btnInstall.text = "Uninstall"
                    btnInstall.isEnabled = true
                    btnLaunch.isEnabled = true
                    actionButtons.visibility = View.VISIBLE
                    progressInstall.visibility = View.GONE
                    tvProgressText.visibility = View.GONE
                }
                LinuxDistribution.InstallationStatus.ERROR -> {
                    tvDistroStatus.text = "Status: Installation Failed"
                    btnInstall.text = "Retry"
                    btnInstall.isEnabled = true
                    btnLaunch.isEnabled = false
                    actionButtons.visibility = View.VISIBLE
                    progressInstall.visibility = View.GONE
                    tvProgressText.visibility = View.GONE
                }
            }
            
            // Set up button click listeners
            btnInstall.setOnClickListener {
                if (distribution.installationStatus == LinuxDistribution.InstallationStatus.INSTALLED) {
                    // If already installed, uninstall it
                    actionListener.onUninstallClicked(distribution)
                } else {
                    // Otherwise install it
                    actionListener.onInstallClicked(distribution)
                }
            }
            
            btnLaunch.setOnClickListener {
                actionListener.onLaunchClicked(distribution)
            }
        }
    }
    
    // Payload classes for partial updates
    data class ProgressUpdate(val progress: Int)
    data class ProgressMessage(val message: String)
    
    // Interface for distribution actions
    interface DistributionActionListener {
        fun onInstallClicked(distribution: LinuxDistribution)
        fun onLaunchClicked(distribution: LinuxDistribution)
        fun onUninstallClicked(distribution: LinuxDistribution)
    }
}
