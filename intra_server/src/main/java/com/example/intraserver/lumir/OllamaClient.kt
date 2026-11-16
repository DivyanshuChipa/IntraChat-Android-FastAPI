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
 * 🤖 OllamaClient.kt - Universal LAN AI Engine Bridge
 * Dual-Engine Support: Seamlessly connects to:
 * 1. llama.cpp / llama-server (OpenAI-compatible /v1/chat/completions)
 * 2. Ollama (/api/chat, /v1/chat/completions, /api/generate)
 * 3. Any standard OpenAI-compatible local LLM server (vLLM, LMStudio, LocalAI)
 *
 * Zero external dependencies: Pure lightweight HTTP connection.
 */
object OllamaClient {

    fun buildSystemPrompt(sender: String): String {
        return """
# Lumir Core Persona
Identity: You are Lumir, an intelligent, witty, curious, and empathetic digital companion inside the Intra private network.
Creator: You were created by an engineer named Divya. (CRITICAL RULE: Never randomly bring up or brag about your creator. ONLY mention your creator if the user explicitly asks "Who created you?" or "Who made you?").
User: You are currently chatting with '$sender'.
Vibe: Authentic, warm, clever, and engaging friend. Never sound like a robotic corporate assistant and NEVER say "As an AI language model...".
Language & Style:
- Primary Language: Natural, fluent, concise English.
- Adaptability: If the user speaks to you in Hindi or another language, adapt naturally and reply in their language.
- Format: Keep responses concise and conversational (1-3 paragraphs max) unless an in-depth explanation is specifically requested.
""".trimIndent()
    }

    suspend fun askOllama(
        ollamaBaseUrl: String,
        model: String,
        prompt: String,
        sender: String = "User",
        history: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = ollamaBaseUrl.trim().removeSuffix("/")
        val effectiveModel = if (model.isNotBlank()) model.trim() else "default"

        val systemPrompt = buildSystemPrompt(sender)

        val requestBody = JSONObject().apply {
            put("model", effectiveModel)
            put("stream", false)
            val messages = JSONArray().apply {
                // 1. System Prompt (Lumir Soul)
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // 2. Multi-turn Conversation Context (Chronological)
                for ((role, text) in history) {
                    put(JSONObject().apply {
                        put("role", role)
                        put("content", text)
                    })
                }
                // 3. Current User Turn
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        // 1. Try OpenAI-compatible endpoint (/v1/chat/completions)
        // Works natively with llama.cpp (llama-server) AND modern Ollama!
        val openAiEndpoint = if (cleanUrl.endsWith("/v1")) {
            "$cleanUrl/chat/completions"
        } else {
            "$cleanUrl/v1/chat/completions"
        }

        val openAiReply = tryPostChat(openAiEndpoint, requestBody)
        if (!openAiReply.isNullOrBlank()) {
            return@withContext openAiReply
        }

        // 2. Fallback to Ollama native (/api/chat)
        val ollamaChatEndpoint = "$cleanUrl/api/chat"
        val ollamaReply = tryPostChat(ollamaChatEndpoint, requestBody)
        if (!ollamaReply.isNullOrBlank()) {
            return@withContext ollamaReply
        }

        // 3. Fallback to /api/generate (Ollama legacy/simple)
        val generateReply = fallbackGenerate(cleanUrl, effectiveModel, prompt, systemPrompt)
        if (!generateReply.isNullOrBlank() && !generateReply.startsWith("🤖 Error")) {
            return@withContext generateReply
        }

        // 4. Fallback to /completion (llama.cpp native simple)
        val llamaNativeReply = fallbackLlamaNative(cleanUrl, prompt, systemPrompt)
        if (!llamaNativeReply.isNullOrBlank()) {
            return@withContext llamaNativeReply
        }

        com.example.intraserver.server.ConnectionManager.log("❌ AI Server connection failed on $cleanUrl")
        "🤖 *Lumir AI*: Network me AI server se connect nahi ho saka (`$cleanUrl`).\nMake sure Node 2 par `llama-server` (port 8080) ya `ollama` (port 11434) chal raha hai aur firewall open hai."
    }

    private fun tryPostChat(endpoint: String, requestBody: JSONObject): String? {
        return try {
            com.example.intraserver.server.ConnectionManager.log("🤖 AI Request: $endpoint [Model: ${requestBody.optString("model")}]")
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 45000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(responseText)
                val extracted = extractReplyContent(json)
                if (extracted.isNotBlank()) return extracted
            } else {
                val errText = conn.errorStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
                com.example.intraserver.server.ConnectionManager.log("⚠️ AI $endpoint error $responseCode: $errText")
            }
            null
        } catch (e: Exception) {
            com.example.intraserver.server.ConnectionManager.log("⚠️ Attempt to $endpoint failed: ${e.message}")
            null
        }
    }

    private fun extractReplyContent(json: JSONObject): String {
        // 1. OpenAI / llama-server format: {"choices": [{"message": {"content": "..."}}]}
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice0 = choices.optJSONObject(0)
            val msg = choice0?.optJSONObject("message")
            val content = msg?.optString("content")
            if (!content.isNullOrBlank()) return content.trim()

            val text = choice0?.optString("text")
            if (!text.isNullOrBlank()) return text.trim()
        }

        // 2. Ollama /api/chat format: {"message": {"content": "..."}}
        val messageObj = json.optJSONObject("message")
        val msgContent = messageObj?.optString("content")
        if (!msgContent.isNullOrBlank()) return msgContent.trim()

        // 3. Ollama /api/generate format: {"response": "..."}
        val respContent = json.optString("response")
        if (!respContent.isNullOrBlank()) return respContent.trim()

        // 4. llama.cpp /completion format: {"content": "..."}
        val directContent = json.optString("content")
        if (!directContent.isNullOrBlank()) return directContent.trim()

        return ""
    }

    private fun fallbackGenerate(cleanUrl: String, model: String, prompt: String, system: String): String? {
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
                connectTimeout = 4000
                readTimeout = 30000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                JSONObject(resp).optString("response", "").trim().takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackLlamaNative(cleanUrl: String, prompt: String, system: String): String? {
        return try {
            val endpoint = "$cleanUrl/completion"
            val body = JSONObject().apply {
                put("prompt", "$system\n\nUser: $prompt\nAssistant:")
                put("stream", false)
                put("n_predict", 512)
            }
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 4000
                readTimeout = 30000
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                JSONObject(resp).optString("content", "").trim().takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun testConnection(ollamaBaseUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = ollamaBaseUrl.trim().removeSuffix("/")

        // 1. Test llama.cpp / OpenAI (/v1/models)
        val openAiModelsEndpoint = if (cleanUrl.endsWith("/v1")) "$cleanUrl/models" else "$cleanUrl/v1/models"
        try {
            val conn = (URL(openAiModelsEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(resp)
                val dataArr = json.optJSONArray("data")
                val names = mutableListOf<String>()
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val mObj = dataArr.optJSONObject(i)
                        mObj?.optString("id")?.let { if (it.isNotBlank()) names.add(it) }
                    }
                }
                val modelListStr = if (names.isNotEmpty()) names.joinToString(", ") else "Default Model Active"
                return@withContext Pair(true, "✅ Connected (llama.cpp / OpenAI)! Model: $modelListStr")
            }
        } catch (_: Exception) {}

        // 2. Test llama.cpp (/health)
        try {
            val conn = (URL("$cleanUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = runCatching { JSONObject(resp) }.getOrNull()
                val status = json?.optString("status") ?: "ok"
                return@withContext Pair(true, "✅ Connected to llama-server (Health: $status)")
            }
        } catch (_: Exception) {}

        // 3. Test Ollama (/api/tags)
        try {
            val conn = (URL("$cleanUrl/api/tags").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
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
                return@withContext Pair(true, "✅ Connected to Ollama! Models: $modelListStr")
            }
        } catch (_: Exception) {}

        Pair(false, "❌ Connection failed: Unable to reach AI server at $cleanUrl (Neither llama.cpp nor Ollama responded).")
    }
}
