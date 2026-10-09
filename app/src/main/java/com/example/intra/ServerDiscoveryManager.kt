package com.example.intra

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit

object ServerDiscoveryManager {
    private const val TAG = "ServerDiscovery"

    // Ultra-light OkHttpClient for instant ping checks
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Finds the device's local IPv4 address across Wi-Fi or active network interfaces.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val connectionInfo = wm.connectionInfo
            val ipAddress = connectionInfo.ipAddress
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP via WifiManager", e)
        }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP via NetworkInterface", ex)
        }
        return null
    }

    /**
     * Checks if a target IP has the given TCP port open with a tight timeout.
     */
    private suspend fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 180): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies that the endpoint responding on port is indeed the Intra FastAPI server.
     */
    suspend fun verifyIntraServer(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        // First try /api/ping
        try {
            val pingRequest = Request.Builder()
                .url("http://$ip:$port/api/ping")
                .get()
                .build()
            probeClient.newCall(pingRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("intra") || body.contains("Intra Server")) {
                        return@withContext true
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: try /users or root
        try {
            val fallbackRequest = Request.Builder()
                .url("http://$ip:$port/users")
                .get()
                .build()
            probeClient.newCall(fallbackRequest).execute().use { response ->
                return@withContext response.isSuccessful || response.code == 401 || response.code == 403
            }
        } catch (_: Exception) {}

        false
    }

    /**
     * Scans the local network subnet for the Intra Chat Server.
     * 1. Checks currently saved IP first (instant cache hit).
     * 2. If not reachable, scans 1..254 in parallel coroutines like SmbHelper.
     * 3. Verifies candidate hosts and returns discovered IP.
     */
    suspend fun scanForServer(
        context: Context,
        port: Int = 8000,
        currentSavedIp: String? = null
    ): String? = withContext(Dispatchers.IO) {
        // Step 1: Quick check if currently saved IP is already up and running
        if (!currentSavedIp.isNullOrBlank() && currentSavedIp != "127.0.0.1") {
            if (isPortOpen(currentSavedIp, port, timeoutMs = 250) && verifyIntraServer(currentSavedIp, port)) {
                Log.d(TAG, "Current saved IP $currentSavedIp is active and verified.")
                return@withContext currentSavedIp
            }
        }

        // Step 2: Determine local subnet
        val localIp = getLocalIpAddress(context) ?: return@withContext null
        Log.d(TAG, "Starting subnet scan from device IP: $localIp (Port: $port)")
        val parts = localIp.split(".")
        if (parts.size != 4) return@withContext null

        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"

        // Step 3: Fast parallel port probe
        val openIps = coroutineScope {
            (1..254).map { host ->
                val candidateIp = "$subnet.$host"
                async(Dispatchers.IO) {
                    if (isPortOpen(candidateIp, port, timeoutMs = 180)) candidateIp else null
                }
            }.awaitAll().filterNotNull()
        }

        Log.d(TAG, "Open port $port candidates: $openIps")

        // Step 4: Verify candidate IPs
        for (ip in openIps) {
            if (verifyIntraServer(ip, port)) {
                Log.d(TAG, "Found & verified Intra server at: $ip:$port")
                return@withContext ip
            }
        }

        // If only 1 host has port open on LAN, return it as the most likely server
        if (openIps.size == 1) {
            return@withContext openIps.first()
        }

        null
    }
}
