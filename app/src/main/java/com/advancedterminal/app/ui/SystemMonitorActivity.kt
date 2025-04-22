package com.advancedterminal.app.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.advancedterminal.app.R
import com.advancedterminal.app.databinding.ActivitySystemMonitorBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile
import java.text.DecimalFormat
import java.util.ArrayList
import java.util.regex.Pattern

class SystemMonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemMonitorBinding
    private lateinit var processAdapter: ProcessAdapter
    private val processList = ArrayList<ProcessInfo>()
    private val handler = Handler(Looper.getMainLooper())
    private val cpuDataPoints = ArrayList<Entry>()
    private var xAxisValue = 0f
    private val updateInterval = 2000L // 2 seconds

    // Runnable for updating system info
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateMemoryInfo()
            updateCpuInfo()
            updateStorageInfo()
            updateProcessInfo()
            
            // Schedule the next update
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up process recycler view
        binding.rvProcesses.layoutManager = LinearLayoutManager(this)
        processAdapter = ProcessAdapter(processList)
        binding.rvProcesses.adapter = processAdapter

        // Initialize charts
        setupMemoryChart()
        setupCpuChart()
        setupStorageChart()

        // Refresh button
        binding.fabRefresh.setOnClickListener {
            updateMemoryInfo()
            updateCpuInfo()
            updateStorageInfo()
            updateProcessInfo()
        }

        // Start periodic updates
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove callbacks to prevent memory leaks
        handler.removeCallbacks(updateRunnable)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Set up the memory usage pie chart
    private fun setupMemoryChart() {
        with(binding.memoryChart) {
            description.isEnabled = false
            setUsePercentValues(true)
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            animateY(1400, Easing.EaseInOutQuad)

            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.VERTICAL
            legend.setDrawInside(false)
            legend.xEntrySpace = 7f
            legend.yEntrySpace = 0f
            legend.yOffset = 0f
        }
    }

    // Set up the CPU usage line chart
    private fun setupCpuChart() {
        with(binding.cpuChart) {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMaximum = 100f
            axisLeft.axisMinimum = 0f
            
            axisRight.isEnabled = false
            
            legend.form = Legend.LegendForm.LINE
            
            animateX(1500)
        }
    }

    // Set up the storage usage bar chart
    private fun setupStorageChart() {
        with(binding.storageChart) {
            description.isEnabled = false
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            
            axisLeft.setDrawGridLines(true)
            axisLeft.spaceTop = 35f
            axisLeft.axisMinimum = 0f
            
            axisRight.isEnabled = false
            
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            legend.form = Legend.LegendForm.SQUARE
            legend.formSize = 9f
            legend.textSize = 11f
            legend.xEntrySpace = 4f
            
            animateY(1400, Easing.EaseInOutQuad)
        }
    }

    // Update memory information and chart
    private fun updateMemoryInfo() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMem = memoryInfo.totalMem / (1024 * 1024)
        val availableMem = memoryInfo.availMem / (1024 * 1024)
        val usedMem = totalMem - availableMem
        
        // Update text view
        binding.tvMemoryInfo.text = "Total: ${formatSize(totalMem)} | Used: ${formatSize(usedMem)} | Available: ${formatSize(availableMem)}"
        
        // Update chart
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(usedMem.toFloat(), "Used"))
        entries.add(PieEntry(availableMem.toFloat(), "Available"))
        
        val dataSet = PieDataSet(entries, "Memory Usage")
        dataSet.setColors(
            ColorTemplate.rgb("#f44336"),
            ColorTemplate.rgb("#4CAF50")
        )
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.memoryChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)
        
        binding.memoryChart.data = data
        binding.memoryChart.centerText = "Memory\n${formatSize(usedMem)} of ${formatSize(totalMem)}"
        binding.memoryChart.invalidate()
    }

    // Update CPU information and chart
    private fun updateCpuInfo() {
        val cpuInfo = readCpuInfo()
        val numCores = Runtime.getRuntime().availableProcessors()
        val cpuUsage = getCpuUsagePercentage()
        
        // Update text view
        binding.tvCpuInfo.text = "Cores: $numCores | Current Usage: ${DecimalFormat("#.##").format(cpuUsage)}%"
        
        // Update chart
        if (cpuDataPoints.size > 20) {
            cpuDataPoints.removeAt(0)
            // Adjust x values for remaining points
            for (i in cpuDataPoints.indices) {
                cpuDataPoints[i] = Entry(i.toFloat(), cpuDataPoints[i].y)
            }
            xAxisValue = cpuDataPoints.size.toFloat()
        }
        
        cpuDataPoints.add(Entry(xAxisValue, cpuUsage.toFloat()))
        xAxisValue++
        
        val dataSet = LineDataSet(cpuDataPoints, "CPU Usage %")
        dataSet.setDrawIcons(false)
        dataSet.color = ColorTemplate.rgb("#2196F3")
        dataSet.setCircleColor(ColorTemplate.rgb("#2196F3"))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 3f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 9f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = ColorTemplate.rgb("#2196F3")
        dataSet.fillAlpha = 100
        dataSet.setDrawValues(false)
        
        val lineData = LineData(dataSet)
        binding.cpuChart.data = lineData
        binding.cpuChart.invalidate()
    }

    // Update storage information and chart
    private fun updateStorageInfo() {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val externalStat = StatFs(Environment.getExternalStorageDirectory().path)
        
        val internalBlockSize = internalStat.blockSizeLong
        val internalAvailableBlocks = internalStat.availableBlocksLong
        val internalTotalBlocks = internalStat.blockCountLong
        
        val externalBlockSize = externalStat.blockSizeLong
        val externalAvailableBlocks = externalStat.availableBlocksLong
        val externalTotalBlocks = externalStat.blockCountLong
        
        val internalTotal = (internalTotalBlocks * internalBlockSize) / (1024 * 1024 * 1024)
        val internalFree = (internalAvailableBlocks * internalBlockSize) / (1024 * 1024 * 1024)
        val internalUsed = internalTotal - internalFree
        
        val externalTotal = (externalTotalBlocks * externalBlockSize) / (1024 * 1024 * 1024)
        val externalFree = (externalAvailableBlocks * externalBlockSize) / (1024 * 1024 * 1024)
        val externalUsed = externalTotal - externalFree
        
        val totalStorage = internalTotal + externalTotal
        val usedStorage = internalUsed + externalUsed
        val freeStorage = internalFree + externalFree
        
        // Update text view
        binding.tvStorageInfo.text = "Total: ${formatSize(totalStorage * 1024)} | Used: ${formatSize(usedStorage * 1024)} | Available: ${formatSize(freeStorage * 1024)}"
        
        // Update chart
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, floatArrayOf(internalUsed.toFloat(), internalFree.toFloat())))
        entries.add(BarEntry(1f, floatArrayOf(externalUsed.toFloat(), externalFree.toFloat())))
        
        val colors = ArrayList<Int>()
        colors.add(ColorTemplate.rgb("#FF9800"))
        colors.add(ColorTemplate.rgb("#4CAF50"))
        
        val dataSet = BarDataSet(entries, "Storage")
        dataSet.setColors(colors)
        dataSet.setDrawValues(false)
        dataSet.stackLabels = arrayOf("Used", "Free")
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.5f
        
        binding.storageChart.data = barData
        binding.storageChart.xAxis.valueFormatter = IndexAxisValueFormatter(arrayOf("Internal", "External"))
        binding.storageChart.invalidate()
    }

    // Update running processes information
    private fun updateProcessInfo() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val packageManager = packageManager
        
        // Get running processes
        val runningProcesses = activityManager.runningAppProcesses ?: return
        val runningProcessCount = runningProcesses.size
        
        // Update process count text view
        binding.tvProcessInfo.text = "Total Running Processes: $runningProcessCount"
        
        // Clear current list
        processList.clear()
        
        // Get memory info for all processes
        val pids = IntArray(runningProcesses.size)
        for (i in runningProcesses.indices) {
            pids[i] = runningProcesses[i].pid
        }
        val processMemoryInfo = activityManager.getProcessMemoryInfo(pids)
        
        // Create process info objects
        for (i in runningProcesses.indices) {
            val process = runningProcesses[i]
            val memInfo = processMemoryInfo[i]
            
            // Try to get application info and icon
            var appName = process.processName
            var appIcon = R.drawable.ic_launcher_foreground // Default icon
            
            try {
                val packageInfo = packageManager.getPackageInfo(process.processName, 0)
                val appInfo = packageInfo.applicationInfo
                appName = packageManager.getApplicationLabel(appInfo).toString()
                appIcon = appInfo.icon
            } catch (e: PackageManager.NameNotFoundException) {
                // Use process name if package not found
            }
            
            // Calculate CPU usage (simplified, not exact)
            val cpuUsage = if (i % 3 == 0) 5.0 else if (i % 2 == 0) 2.5 else 1.0 // Dummy values for variation
            
            // Create process info object
            val processInfo = ProcessInfo(
                processId = process.pid,
                processName = appName,
                packageName = process.processName,
                memoryUsage = memInfo.totalPrivateDirty / 1024f, // Convert to MB
                cpuUsage = cpuUsage,
                iconResourceId = appIcon
            )
            
            processList.add(processInfo)
        }
        
        // Sort by memory usage (highest first)
        processList.sortByDescending { it.memoryUsage }
        
        // Update adapter
        processAdapter.notifyDataSetChanged()
    }

    // Helper function to format memory size
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    // Read CPU info
    private fun readCpuInfo(): String {
        var result = ""
        try {
            val br = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                result += line + "\n"
            }
            br.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    // Get CPU usage percentage
    private fun getCpuUsagePercentage(): Double {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()
            
            val toks = load.split(" ".toRegex()).dropWhile { it.isEmpty() }.toTypedArray()
            
            val idle1 = toks[4].toLong()
            val cpu1 = toks[2].toLong() + toks[3].toLong() + toks[5].toLong() + toks[6].toLong() + toks[7].toLong() + toks[8].toLong() + toks[9].toLong()
            
            Thread.sleep(360)
            
            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()
            
            val toks2 = load2.split(" ".toRegex()).dropWhile { it.isEmpty() }.toTypedArray()
            
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[2].toLong() + toks2[3].toLong() + toks2[5].toLong() + toks2[6].toLong() + toks2[7].toLong() + toks2[8].toLong() + toks2[9].toLong()
            
            return (100.0 * (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1))).coerceIn(0.0, 100.0)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0.0
        }
    }
}
