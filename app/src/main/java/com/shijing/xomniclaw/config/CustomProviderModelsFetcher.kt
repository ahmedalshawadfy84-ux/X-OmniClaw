package com.shijing.xomniclaw.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches model IDs from OpenAI-compatible providers.
 *
 * Custom providers such as NVIDIA NIM, OpenRouter-compatible gateways, and many
 * sk-* compatible endpoints expose GET /models with an OpenAI-style response:
 * { "data": [{ "id": "model-id" }] }.
 */
object CustomProviderModelsFetcher {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModelIds(apiKey: String, baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val endpoint = modelsEndpoint(baseUrl)

        if (trimmedKey.isBlank()) {
            throw IllegalArgumentException("API Key is required")
        }

        val request = Request.Builder()
            .url(endpoint)
            .get()
            .addHeader("Authorization", "Bearer $trimmedKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = bodyText.take(300).ifBlank { response.message }
                throw IOException("Fetch failed (${response.code}): $detail")
            }

            parseModelIds(bodyText).ifEmpty {
                throw IOException("No models were returned by $endpoint")
            }
        }
    }

    fun modelsEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Base URL is required")
        }
        return "$trimmed/models"
    }

    private fun parseModelIds(bodyText: String): List<String> {
        val json = JSONObject(bodyText)
        val candidates = when {
            json.has("data") -> json.optJSONArray("data")
            json.has("models") -> json.optJSONArray("models")
            else -> null
        } ?: JSONArray()

        val ids = mutableListOf<String>()
        for (i in 0 until candidates.length()) {
            val item = candidates.opt(i)
            val id = when (item) {
                is JSONObject -> item.optString("id").takeIf { it.isNotBlank() }
                    ?: item.optString("name").takeIf { it.isNotBlank() }
                is String -> item.takeIf { it.isNotBlank() }
                else -> null
            }
            if (!id.isNullOrBlank()) {
                ids.add(id)
            }
        }
        return ids.distinct().sorted()
    }
}
