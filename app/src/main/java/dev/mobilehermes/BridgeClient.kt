package dev.mobilehermes

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BridgeClient(
    private val baseUrl: String = "http://127.0.0.1:8765"
) {
    suspend fun health(): Result<String> = runCatching {
        withContext(Dispatchers.IO) { get("/health") }
    }

    suspend fun adbDevices(): Result<String> = runCatching {
        withContext(Dispatchers.IO) { get("/adb/devices") }
    }

    suspend fun command(text: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            post("/command", """{"text":${jsonString(text)}}""")
        }
    }

    suspend fun chat(text: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            post("/chat", """{"text":${jsonString(text)}}""")
        }
    }

    suspend fun providers(): Result<String> = runCatching {
        withContext(Dispatchers.IO) { get("/providers") }
    }

    private fun get(path: String): String {
        val connection = open(path, "GET")
        return connection.useResponse()
    }

    private fun post(path: String, body: String): String {
        val connection = open(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return connection.useResponse()
    }

    private fun open(path: String, method: String): HttpURLConnection {
        return (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 1500
            readTimeout = 4000
        }
    }

    private fun HttpURLConnection.useResponse(): String {
        return try {
            val stream = if (responseCode in 200..299) inputStream else errorStream
            val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (responseCode !in 200..299) {
                error("HTTP $responseCode: $response")
            }
            response
        } finally {
            disconnect()
        }
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
