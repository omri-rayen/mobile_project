package com.studyhelper.network

import com.studyhelper.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OpenAiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"

    suspend fun chat(prompt: String, maxTokens: Int = 1024): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val messages = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    })
                }

                val body = JsonObject().apply {
                    addProperty("model", MODEL)
                    add("messages", messages)
                    addProperty("max_tokens", maxTokens)
                }

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))

                if (!response.isSuccessful) {
                    val msg = when (response.code) {
                        401 -> "Invalid API key. Check GROQ_API_KEY in local.properties and rebuild."
                        429 -> "Rate limit reached. Please wait a moment and try again."
                        500, 502, 503 -> "Groq server error. Please try again later."
                        else -> try {
                            val err = gson.fromJson(responseBody, JsonObject::class.java)
                            err.getAsJsonObject("error")?.get("message")?.asString
                                ?: "API error ${response.code}"
                        } catch (_: Exception) {
                            "API error ${response.code}"
                        }
                    }
                    return@withContext Result.failure(Exception(msg))
                }

                val json = gson.fromJson(responseBody, JsonObject::class.java)
                val text = json
                    .getAsJsonArray("choices")
                    .get(0).asJsonObject
                    .getAsJsonObject("message")
                    .get("content").asString
                    .trim()

                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
