package com.example.soccergamemanager.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class CloudSyncClient {
    suspend fun pair(serviceUrl: String, code: String, deviceName: String): PairDeviceResponse = request(
        serviceUrl = serviceUrl,
        path = "/api/device/pair",
        method = "POST",
        body = appJson.encodeToString(PairDeviceRequest(code.replace(" ", "").uppercase(), deviceName)),
        token = null,
    )

    suspend fun pushMutations(
        connection: CloudConnectionSettings,
        token: String,
        mutations: List<MutationCommandDto>,
    ): MutationResultDto = request(
        connection.serviceUrl,
        "/api/device/teams/${connection.cloudTeamId}/mutations",
        "POST",
        appJson.encodeToString(MutationBatchRequest(mutations)),
        token,
    )

    suspend fun changes(connection: CloudConnectionSettings, token: String, afterRevision: Long): ChangesResponse = request(
        connection.serviceUrl,
        "/api/device/teams/${connection.cloudTeamId}/changes?after=$afterRevision",
        "GET",
        null,
        token,
    )

    suspend fun publishLineup(
        connection: CloudConnectionSettings,
        token: String,
        gameId: String,
        request: PublishLineupRequest,
    ): PublishedLineupResponse = request(
        connection.serviceUrl,
        "/api/device/teams/${connection.cloudTeamId}/games/$gameId/lineup/publish",
        "POST",
        appJson.encodeToString(request),
        token,
    )

    suspend fun claimController(
        connection: CloudConnectionSettings,
        token: String,
        gameId: String,
        holderName: String,
    ): ControllerLeaseResponse = request(
        connection.serviceUrl,
        "/api/device/teams/${connection.cloudTeamId}/games/$gameId/controller/claim",
        "POST",
        appJson.encodeToString(ClaimControllerRequest(holderName)),
        token,
    )

    private suspend inline fun <reified T> request(
        serviceUrl: String,
        path: String,
        method: String,
        body: String?,
        token: String?,
    ): T = withContext(Dispatchers.IO) {
        val base = serviceUrl.trim().trimEnd('/')
        val uri = URI.create("$base$path")
        require(uri.scheme == "https" || (uri.scheme == "http" && isLocalHost(uri.host))) {
            "Cloud service URL must use HTTPS."
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw IOException("Cloud request failed ($status): ${response.take(300)}")
            }
            appJson.decodeFromString<T>(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun isLocalHost(host: String?): Boolean = host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}
