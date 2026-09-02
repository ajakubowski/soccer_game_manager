package com.example.soccergamemanager.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncModelsTest {
    @Test
    fun `publish request includes optional lineup name`() {
        val encoded = appJson.encodeToString(
            PublishLineupRequest(
                expectedTeamRevision = 12,
                payload = buildJsonObject {},
                lineupName = "Game-day final",
            ),
        )

        assertTrue(encoded.contains("\"lineupName\":\"Game-day final\""))
    }

    @Test
    fun `published lineup response includes user and device metadata`() {
        val response = appJson.decodeFromString<PublishedLineupResponse>(
            """{
                "gameId":"game-1",
                "publishedVersion":3,
                "teamRevision":12,
                "lineupName":"Game-day final",
                "publishedBy":"Coach",
                "publishedByUser":"coach@example.com",
                "publishedFromDeviceId":"tablet-1",
                "publishedFromDeviceName":"Sideline tablet",
                "publishedAt":123
            }""".trimIndent(),
        )

        assertEquals("Game-day final", response.lineupName)
        assertEquals("coach@example.com", response.publishedByUser)
        assertEquals("Sideline tablet", response.publishedFromDeviceName)
    }
}
