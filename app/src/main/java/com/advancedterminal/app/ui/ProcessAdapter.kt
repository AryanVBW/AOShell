package com.advancedterminal.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.advancedterminal.app.R
import java.text.DecimalFormat

class ProcessAdapter(private val processList: List<ProcessInfo>) :
    RecyclerView.Adapter<ProcessAdapter.ProcessViewHolder>() {

    class ProcessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvProcessName: TextView = itemView.findViewById(R.id.tvProcessName)
        val tvProcessId: TextView = itemView.findViewById(R.id.tvProcessId)
        val tvMemoryUsage: TextView = itemView.findViewById(R.id.tvMemoryUsage)
        val tvCpuUsage: TextView = itemView.findViewById(R.id.tvCpuUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_process, parent, false)
        return ProcessViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProcessViewHolder, position: Int) {
        val process = processList[position]
        
        // Set process name and ID
        holder.tvProcessName.text = process.processName
        holder.tvProcessId.text = "PID: ${process.processId}"
        
        // Set app icon
        holder.ivAppIcon.setImageResource(process.iconResourceId)
        
        // Set memory usage
        holder.tvMemoryUsage.text = formatMemory(process.memoryUsage)
        
        // Set CPU usage
        holder.tvCpuUsage.text = "${DecimalFormat("#.#").format(process.cpuUsage)}% CPU"
    }

    override fun getItemCount() = processList.size

    private fun formatMemory(memoryMB: Float): String {
        return if (memoryMB < 1024) {
            "${DecimalFormat("#.#").format(memoryMB)} MB"
        } else {
            "${DecimalFormat("#.##").format(memoryMB / 1024)} GB"
        }
    }
}
