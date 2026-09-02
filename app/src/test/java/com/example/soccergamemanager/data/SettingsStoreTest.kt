package com.example.soccergamemanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun `legacy worker url uses branded domain`() {
        assertEquals(
            DEFAULT_CLOUD_SERVICE_URL,
            currentCloudServiceUrl("https://soccer-game-manager-collab.jakubowski-andy.workers.dev/"),
        )
    }

    @Test
    fun `custom service url remains unchanged`() {
        val customUrl = "https://staging.example.com"

        assertEquals(customUrl, currentCloudServiceUrl(customUrl))
    }
}
