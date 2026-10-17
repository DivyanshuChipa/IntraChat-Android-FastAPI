package com.example.intraserver.lumir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🤖 OllamaClient.kt - Lightweight LAN Ollama Bridge
 * Zero dependencies: pure HTTP connection to talk to Ollama running on any PC in the LAN.
 */
object OllamaClient {

    suspend fun askOllama(
        ollamaBaseUrl: String,
        model: String,
        prompt: String,
        sender: String = "User"
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = ollamaBaseUrl.trim().removeSuffix("/")
        val endpoint = "$cleanUrl/api/chat"

        val systemPrompt = "You are Lumir, a helpful, intelligent, friendly and witty AI assistant inside the Intra LAN network. You were created by an engineer named Divya. The user chatting with you is named '$sender'. Always reply in a concise and friendly manner."

        val requestBody = JSONObject().apply {
            put("model", model)
            put("stream", false)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        try {
            com.example.intraserver.server.ConnectionManager.log("🤖 Ollama request: $endpoint [Model: $model]")
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 45000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseText)
                val messageObj = json.optJSONObject("message")
                val reply = messageObj?.optString("content") ?: json.optString("response", "")
                if (reply.isNotBlank()) return@withContext reply.trim()
            } else {
                val errText = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                com.example.intraserver.server.ConnectionManager.log("⚠️ Ollama /api/chat error $responseCode: $errText")
            }

            // Fallback to /api/generate if /api/chat fails
            return@withContext fallbackGenerate(cleanUrl, model, prompt, systemPrompt)
        } catch (e: Exception) {
            com.example.intraserver.server.ConnectionManager.log("❌ Ollama connection failed: ${e.message}")
            "🤖 *Lumir AI*: Network me Ollama server se connect nahi ho saka (`$cleanUrl`). Error: ${e.message}\nMake sure PC par `OLLAMA_HOST=0.0.0.0` chal raha hai aur firewall port 11434 open hai."
        }
    }

    private fun fallbackGenerate(cleanUrl: String, model: String, prompt: String, system: String): String {
        return try {
            val endpoint = "$cleanUrl/api/generate"
            val body = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("system", system)
                put("stream", false)
            }
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 30000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                JSONObject(resp).optString("response", "No response from AI")
            } else {
                val errText = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                com.example.intraserver.server.ConnectionManager.log("⚠️ Ollama /api/generate error ${conn.responseCode}: $errText")
                "🤖 AI Server responded with code ${conn.responseCode}: $errText"
            }
        } catch (e: Exception) {
            "🤖 Error reaching Ollama fallback: ${e.message}"
        }
    }

    suspend fun testConnection(ollamaBaseUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = ollamaBaseUrl.trim().removeSuffix("/")
        try {
            val conn = (URL("$cleanUrl/api/tags").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = conn.responseCode
            if (code == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(resp)
                val modelsArr = json.optJSONArray("models")
                val names = mutableListOf<String>()
                if (modelsArr != null) {
                    for (i in 0 until modelsArr.length()) {
                        val mObj = modelsArr.optJSONObject(i)
                        mObj?.optString("name")?.let { if (it.isNotBlank()) names.add(it) }
                    }
                }
                val modelListStr = if (names.isNotEmpty()) names.joinToString(", ") else "No models pulled yet"
                Pair(true, "✅ Connected! Models: $modelListStr")
            } else {
                Pair(false, "❌ Ollama returned code $code")
            }
        } catch (e: Exception) {
            Pair(false, "❌ Connection failed: ${e.message}")
        }
    }
}
