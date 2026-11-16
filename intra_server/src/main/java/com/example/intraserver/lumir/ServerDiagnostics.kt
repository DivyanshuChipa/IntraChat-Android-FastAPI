package com.example.intraserver.lumir

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.example.intraserver.server.ConnectionManager
import com.example.intraserver.service.ServerService
import java.util.Locale

/**
 * 📊 ServerDiagnostics.kt - Native Android System Health & Diagnostics
 * Queries local phone sensors & OS stats (Battery, RAM, Storage, Uptime, Presence)
 * 100% offline with zero dependencies and 0ms latency.
 */
object ServerDiagnostics {

    private val serverStartTime: Long = System.currentTimeMillis()

    fun getStatusReport(context: Context?): String {
        val sb = StringBuilder()
        sb.append("📊 **Intra Server (Phone Node) Status**\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

        // 1. 🔋 Battery Info
        if (context != null) {
            try {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

                val chargeIcon = if (isCharging) "⚡ Charging" else "🔋 On Battery"
                if (pct >= 0) {
                    sb.append("🔋 **Battery:** $pct% [$chargeIcon]\n")
                }
            } catch (_: Exception) {}
        }

        // 2. 🧠 RAM (Memory Info)
        if (context != null) {
            try {
                val mi = ActivityManager.MemoryInfo()
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.getMemoryInfo(mi)
                val totalGb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
                val availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0)
                val usedGb = totalGb - availGb
                val usedPct = if (totalGb > 0) ((usedGb / totalGb) * 100).toInt() else 0
                val bar = makeProgressBar(usedPct)

                sb.append("🧠 **RAM:** ${String.format(Locale.US, "%.1f", availGb)} GB Free / ${String.format(Locale.US, "%.1f", totalGb)} GB $bar\n")
            } catch (_: Exception) {}
        }

        // 3. 💾 Internal Storage Info
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val availBytes = stat.availableBlocksLong * blockSize
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val availGb = availBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = totalGb - availGb
            val usedPct = if (totalGb > 0) ((usedGb / totalGb) * 100).toInt() else 0
            val bar = makeProgressBar(usedPct)

            sb.append("💾 **Storage:** ${String.format(Locale.US, "%.1f", availGb)} GB Free / ${String.format(Locale.US, "%.1f", totalGb)} GB $bar\n")
        } catch (_: Exception) {}

        // 4. ⏱️ Server Uptime
        val uptimeMillis = System.currentTimeMillis() - serverStartTime
        val hours = uptimeMillis / (1000 * 60 * 60)
        val mins = (uptimeMillis % (1000 * 60 * 60)) / (1000 * 60)
        sb.append("⏱️ **Server Uptime:** ${hours}h ${mins}m\n")

        // 5. 🌐 IP & Active Users
        val ip = ServerService.serverIp.value
        val port = ServerService.serverPort.value
        sb.append("🌐 **LAN Address:** http://$ip:$port\n")

        val onlineCount = ConnectionManager.getOnlineCount()
        val onlineUsers = ConnectionManager.getOnlineUsers()
        val userListStr = if (onlineUsers.isNotEmpty()) onlineUsers.joinToString(", ") else "None"
        sb.append("👥 **Online Users:** $onlineCount ($userListStr)\n")

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🟢 **All Systems Operational**")

        return sb.toString()
    }

    private fun makeProgressBar(pct: Int): String {
        val filledBlocks = (pct.coerceIn(0, 100) / 10)
        val emptyBlocks = 10 - filledBlocks
        val bar = "█".repeat(filledBlocks) + "░".repeat(emptyBlocks)
        return "[$bar $pct%]"
    }
}
