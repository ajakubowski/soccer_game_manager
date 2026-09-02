package com.example.soccergamemanager.data

import android.content.Context
import androidx.room.withTransaction
import com.example.soccergamemanager.domain.GameStatus
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CloudSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val credentials: SecureCredentialStore,
    private val client: CloudSyncClient = CloudSyncClient(),
) {
    private val seasonDao = database.seasonDao()
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val availabilityDao = database.availabilityDao()
    private val assignmentDao = database.assignmentDao()
    private val goalDao = database.goalDao()
    private val syncDao = database.syncDao()

    fun observeState(localTeamId: String): Flow<TeamSyncStateEntity?> = syncDao.observeState(localTeamId)

    fun observeConnection(localTeamId: String): Flow<CloudConnectionSettings?> = settingsStore.cloudConnection(localTeamId)

    fun observeConflicts(localTeamId: String): Flow<List<SyncConflictEntity>> = syncDao.observeConflicts(localTeamId)

    fun scheduleSync() {
        CloudSyncWorker.enqueue(context)
    }

    suspend fun pairTeam(
        localTeamId: String,
        serviceUrl: String,
        pairingCode: String,
        deviceName: String,
    ): String {
        require(pairingCode.isNotBlank()) { "Pairing code is required." }
        require(deviceName.isNotBlank()) { "Device name is required." }
        createInternalBackup()
        val normalizedUrl = serviceUrl.trim().trimEnd('/')
        val response = client.pair(normalizedUrl, pairingCode, deviceName.trim())
        val connection = CloudConnectionSettings(
            localTeamId = localTeamId,
            serviceUrl = normalizedUrl,
            cloudTeamId = response.teamId,
            deviceId = response.deviceId,
            deviceName = deviceName.trim(),
        )
        credentials.saveDeviceToken(localTeamId, response.token)
        settingsStore.saveCloudConnection(connection)
        syncDao.upsertState(
            TeamSyncStateEntity(
                localTeamId = localTeamId,
                cloudTeamId = response.teamId,
                status = "PENDING",
            ),
        )
        syncTeam(localTeamId)
        return response.teamId
    }

    suspend fun downloadCloudTeam(
        serviceUrl: String,
        pairingCode: String,
        deviceName: String,
    ): String {
        require(pairingCode.isNotBlank()) { "Pairing code is required." }
        require(deviceName.isNotBlank()) { "Device name is required." }
        createInternalBackup()
        val normalizedUrl = serviceUrl.trim().trimEnd('/')
        val response = client.pair(normalizedUrl, pairingCode, deviceName.trim())
        val localTeamId = response.teamId
        val connection = CloudConnectionSettings(
            localTeamId = localTeamId,
            serviceUrl = normalizedUrl,
            cloudTeamId = response.teamId,
            deviceId = response.deviceId,
            deviceName = deviceName.trim(),
        )
        credentials.saveDeviceToken(localTeamId, response.token)
        settingsStore.saveCloudConnection(connection)
        syncDao.upsertState(
            TeamSyncStateEntity(
                localTeamId = localTeamId,
                cloudTeamId = response.teamId,
                status = "SYNCING",
            ),
        )

        runCatching {
            val remote = client.changes(connection, response.token, 0)
            applyRemoteChanges(localTeamId, remote.changes)
            checkNotNull(seasonDao.getSeason(localTeamId)) {
                "The cloud team is missing its team profile. Open it once in the web app, then generate a new pairing code."
            }
            syncDao.upsertState(
                TeamSyncStateEntity(
                    localTeamId = localTeamId,
                    cloudTeamId = response.teamId,
                    lastPulledRevision = remote.teamRevision,
                    lastSyncAt = System.currentTimeMillis(),
                    status = "SYNCED",
                ),
            )
        }.onFailure { throwable ->
            val state = syncDao.getState(localTeamId) ?: TeamSyncStateEntity(localTeamId, response.teamId)
            syncDao.upsertState(state.copy(status = "OFFLINE", lastError = throwable.message))
        }.getOrThrow()
        return localTeamId
    }

    suspend fun disconnect(localTeamId: String) {
        settingsStore.clearCloudConnection(localTeamId)
        credentials.clearDeviceToken(localTeamId)
        database.withTransaction {
            syncDao.deletePendingByTeam(localTeamId)
            syncDao.deleteConflictsByTeam(localTeamId)
            syncDao.deleteVersionsByTeam(localTeamId)
            syncDao.deleteControllerLeasesByTeam(localTeamId)
            syncDao.deleteState(localTeamId)
        }
    }

    suspend fun syncAllPairedTeams(): Boolean {
        var allSucceeded = true
        syncDao.getAllStates().forEach { state ->
            runCatching { syncTeam(state.localTeamId) }
                .onFailure { allSucceeded = false }
        }
        return allSucceeded
    }

    suspend fun syncTeam(localTeamId: String) {
        val connection = settingsStore.cloudConnection(localTeamId).first()
            ?: error("This team is not connected to the cloud.")
        val token = credentials.deviceToken(localTeamId)
            ?: error("The device credential is missing. Pair the tablet again.")
        val previousState = syncDao.getState(localTeamId)
            ?: TeamSyncStateEntity(localTeamId, connection.cloudTeamId)
        syncDao.upsertState(previousState.copy(status = "SYNCING", lastError = null))

        runCatching {
            preparePendingMutations(localTeamId, connection)
            val pending = syncDao.getPending(localTeamId)
            val mutationResult = if (pending.isNotEmpty()) {
                client.pushMutations(connection, token, pending.map { it.toDto(connection.deviceId) })
            } else {
                MutationResultDto(previousState.lastPulledRevision, emptyList(), emptyList(), emptyList())
            }
            applyMutationResult(localTeamId, mutationResult)

            val refreshedState = syncDao.getState(localTeamId) ?: previousState
            val remote = client.changes(connection, token, refreshedState.lastPulledRevision)
            applyRemoteChanges(localTeamId, remote.changes)
            val pendingCount = syncDao.getPending(localTeamId).size
            val conflictCount = syncDao.getConflicts(localTeamId).size
            syncDao.upsertState(
                (syncDao.getState(localTeamId) ?: previousState).copy(
                    lastPulledRevision = remote.teamRevision,
                    lastSyncAt = System.currentTimeMillis(),
                    status = when {
                        conflictCount > 0 -> "CONFLICT"
                        pendingCount > 0 -> "PENDING"
                        else -> "SYNCED"
                    },
                    pendingCount = pendingCount,
                    conflictCount = conflictCount,
                    lastError = null,
                ),
            )
        }.onFailure { throwable ->
            val state = syncDao.getState(localTeamId) ?: previousState
            syncDao.upsertState(
                state.copy(
                    status = if (state.pendingCount > 0) "PENDING" else "OFFLINE",
                    lastError = throwable.message ?: "Cloud synchronization failed.",
                ),
            )
            throw throwable
        }.getOrThrow()
    }

    suspend fun publishLineup(localTeamId: String, detail: GameDetail): PublishedLineupResponse {
        syncTeam(localTeamId)
        val connection = settingsStore.cloudConnection(localTeamId).first() ?: error("Team is not connected.")
        val token = credentials.deviceToken(localTeamId) ?: error("Device credential is missing.")
        val state = syncDao.getState(localTeamId) ?: error("Sync state is unavailable.")
        require(state.conflictCount == 0 && state.pendingCount == 0) { "Resolve cloud conflicts before publishing." }
        val payload = PublishedLineupPayload(
            game = detail.game,
            availability = detail.availability,
            assignments = detail.assignments,
            publishedFromRevision = state.lastPulledRevision,
        )
        val response = client.publishLineup(
            connection,
            token,
            detail.game.gameId,
            PublishLineupRequest(state.lastPulledRevision, appJson.encodeToJsonElement(PublishedLineupPayload.serializer(), payload)),
        )
        syncDao.upsertState(state.copy(lastPublishedLineupVersion = response.publishedVersion, lastSyncAt = System.currentTimeMillis()))
        return response
    }

    suspend fun downloadForMatch(localTeamId: String, game: GameEntity): ControllerLeaseResponse {
        syncTeam(localTeamId)
        val connection = settingsStore.cloudConnection(localTeamId).first() ?: error("Team is not connected.")
        val token = credentials.deviceToken(localTeamId) ?: error("Device credential is missing.")
        val response = client.claimController(connection, token, game.gameId, connection.deviceName)
        credentials.saveControllerToken(game.gameId, response.leaseToken)
        syncDao.upsertControllerLease(
            GameControllerLeaseEntity(
                gameId = response.gameId,
                localTeamId = localTeamId,
                deviceId = response.deviceId,
                holderName = response.holderName,
                expiresAt = response.expiresAt,
                claimedAt = response.claimedAt,
            ),
        )
        return response
    }

    suspend fun keepLocalConflict(localTeamId: String, mutationId: String) {
        val conflict = syncDao.getConflict(mutationId) ?: return
        val pending = syncDao.getPendingById(mutationId) ?: return
        val replacement = pending.copy(
            mutationId = UUID.randomUUID().toString(),
            expectedVersion = conflict.actualVersion,
            attemptCount = 0,
            createdAt = System.currentTimeMillis(),
        )
        database.withTransaction {
            syncDao.deletePending(listOf(mutationId))
            syncDao.deleteConflict(mutationId)
            syncDao.upsertPending(listOf(replacement))
            refreshSyncCounts(localTeamId)
        }
        syncTeam(localTeamId)
    }

    suspend fun useCloudConflict(localTeamId: String, mutationId: String) {
        val conflict = syncDao.getConflict(mutationId) ?: return
        val serverEntity = conflict.serverEntityJson?.let { appJson.decodeFromString<CloudEntityDto>(it) }
        database.withTransaction {
            if (serverEntity != null) applyResolvedCloudEntity(localTeamId, serverEntity)
            syncDao.deletePending(listOf(mutationId))
            syncDao.deleteConflict(mutationId)
            refreshSyncCounts(localTeamId)
        }
        syncTeam(localTeamId)
    }

    suspend fun keepAllLocalConflicts(localTeamId: String) {
        val conflicts = syncDao.getConflicts(localTeamId).associateBy { it.mutationId }
        if (conflicts.isEmpty()) return
        val replacements = syncDao.getPending(localTeamId).mapNotNull { pending ->
            val conflict = conflicts[pending.mutationId] ?: return@mapNotNull null
            pending.copy(
                mutationId = UUID.randomUUID().toString(),
                expectedVersion = conflict.actualVersion,
                attemptCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        }
        database.withTransaction {
            syncDao.deletePending(conflicts.keys.toList())
            syncDao.deleteConflictsByTeam(localTeamId)
            if (replacements.isNotEmpty()) syncDao.upsertPending(replacements)
            refreshSyncCounts(localTeamId)
        }
        syncTeam(localTeamId)
    }

    suspend fun useAllCloudConflicts(localTeamId: String) {
        val conflicts = syncDao.getConflicts(localTeamId)
        if (conflicts.isEmpty()) return
        database.withTransaction {
            conflicts.forEach { conflict ->
                conflict.serverEntityJson
                    ?.let { appJson.decodeFromString<CloudEntityDto>(it) }
                    ?.let { applyResolvedCloudEntity(localTeamId, it) }
            }
            syncDao.deletePending(conflicts.map { it.mutationId })
            syncDao.deleteConflictsByTeam(localTeamId)
            refreshSyncCounts(localTeamId)
        }
        syncTeam(localTeamId)
    }

    private suspend fun refreshSyncCounts(localTeamId: String) {
        val state = syncDao.getState(localTeamId) ?: return
        val pendingCount = syncDao.getPending(localTeamId).size
        val conflictCount = syncDao.getConflicts(localTeamId).size
        syncDao.upsertState(
            state.copy(
                status = when {
                    conflictCount > 0 -> "CONFLICT"
                    pendingCount > 0 -> "PENDING"
                    else -> "SYNCED"
                },
                pendingCount = pendingCount,
                conflictCount = conflictCount,
                lastError = null,
            ),
        )
    }

    private suspend fun applyResolvedCloudEntity(localTeamId: String, entity: CloudEntityDto) {
        applyRemoteEntity(localTeamId, entity)
        if (entity.deletedAt != null || entity.payload == null) {
            syncDao.deleteVersion(localTeamId, entity.entityType, entity.entityId)
        } else {
            syncDao.upsertVersions(
                listOf(
                    EntitySyncVersionEntity(
                        localTeamId = localTeamId,
                        entityType = entity.entityType,
                        entityId = entity.entityId,
                        serverVersion = entity.version,
                        syncedPayloadHash = hash(entity.payload.toString()),
                    ),
                ),
            )
        }
    }

    private suspend fun preparePendingMutations(localTeamId: String, connection: CloudConnectionSettings) {
        val localEntities = localEntities(localTeamId)
        val localByKey = localEntities.associateBy { it.entityType to it.entityId }
        val versions = syncDao.getVersions(localTeamId).associateBy { it.entityType to it.entityId }
        val conflicts = syncDao.getConflicts(localTeamId).map { it.entityType to it.entityId }.toSet()
        val existingPending = syncDao.getPending(localTeamId).associateBy { it.entityType to it.entityId }
        val mutations = mutableListOf<PendingMutationEntity>()

        localEntities.forEach { entity ->
            val key = entity.entityType to entity.entityId
            if (key in conflicts) return@forEach
            val version = versions[key]
            if (version?.syncedPayloadHash == entity.hash) return@forEach
            val existing = existingPending[key]
            mutations += PendingMutationEntity(
                mutationId = existing?.mutationId ?: UUID.randomUUID().toString(),
                localTeamId = localTeamId,
                cloudTeamId = connection.cloudTeamId,
                entityType = entity.entityType,
                entityId = entity.entityId,
                operation = "UPSERT_ENTITY",
                expectedVersion = version?.serverVersion ?: 0,
                payloadJson = entity.payloadJson,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                attemptCount = existing?.attemptCount ?: 0,
            )
        }
        versions.forEach { (key, version) ->
            if (key in localByKey || key in conflicts) return@forEach
            val existing = existingPending[key]
            mutations += PendingMutationEntity(
                mutationId = existing?.mutationId ?: UUID.randomUUID().toString(),
                localTeamId = localTeamId,
                cloudTeamId = connection.cloudTeamId,
                entityType = key.first,
                entityId = key.second,
                operation = "DELETE_ENTITY",
                expectedVersion = version.serverVersion,
                payloadJson = null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                attemptCount = existing?.attemptCount ?: 0,
            )
        }
        if (mutations.isNotEmpty()) syncDao.upsertPending(mutations)
    }

    private suspend fun applyMutationResult(localTeamId: String, result: MutationResultDto) {
        database.withTransaction {
            val accepted = result.acceptedMutationIds.toSet()
            val pending = syncDao.getPending(localTeamId).associateBy { it.mutationId }
            val versions = result.changes.map { change ->
                EntitySyncVersionEntity(
                    localTeamId,
                    change.entityType,
                    change.entityId,
                    change.version,
                    hash(change.payload?.toString().orEmpty()),
                )
            }
            if (versions.isNotEmpty()) syncDao.upsertVersions(versions)
            if (accepted.isNotEmpty()) syncDao.deletePending(accepted.toList())
            if (result.conflicts.isNotEmpty()) {
                syncDao.upsertConflicts(
                    result.conflicts.map { conflict ->
                        SyncConflictEntity(
                            mutationId = conflict.mutationId,
                            localTeamId = localTeamId,
                            entityType = conflict.entityType,
                            entityId = conflict.entityId,
                            reason = conflict.reason,
                            expectedVersion = conflict.expectedVersion,
                            actualVersion = conflict.actualVersion,
                            serverEntityJson = conflict.serverEntity?.let { appJson.encodeToString(it) },
                        )
                    },
                )
            }
            pending.keys.filter { it in accepted }.forEach { mutationId -> syncDao.deleteConflict(mutationId) }
        }
    }

    private suspend fun applyRemoteChanges(localTeamId: String, changes: List<CloudEntityDto>) {
        if (changes.isEmpty()) return
        database.withTransaction {
            val blocked = (syncDao.getPending(localTeamId).map { it.entityType to it.entityId } +
                syncDao.getConflicts(localTeamId).map { it.entityType to it.entityId }).toSet()
            val appliedVersions = mutableListOf<EntitySyncVersionEntity>()
            changes.forEach { change ->
                if ((change.entityType to change.entityId) in blocked) return@forEach
                if (applyRemoteEntity(localTeamId, change)) {
                    appliedVersions += EntitySyncVersionEntity(
                        localTeamId,
                        change.entityType,
                        change.entityId,
                        change.version,
                        hash(change.payload?.toString().orEmpty()),
                    )
                }
            }
            if (appliedVersions.isNotEmpty()) syncDao.upsertVersions(appliedVersions)
        }
    }

    private suspend fun applyRemoteEntity(localTeamId: String, entity: CloudEntityDto): Boolean {
        if (entity.entityType == "team_profile") return false
        if (entity.deletedAt != null || entity.payload == null) {
            when (entity.entityType) {
                "season" -> if (entity.entityId == localTeamId) {
                    seasonDao.getSeason(localTeamId)?.let { season -> seasonDao.deleteSeason(season) }
                }
                "player" -> playerDao.deleteById(entity.entityId)
                "game" -> gameDao.deleteById(entity.entityId)
                "availability" -> entity.entityId.split(":", limit = 2).takeIf { it.size == 2 }?.let { availabilityDao.deleteById(it[0], it[1]) }
                "assignment" -> assignmentDao.deleteById(entity.entityId)
                "goal" -> goalDao.deleteById(entity.entityId)
                else -> return false
            }
            return true
        }
        val payload = entity.payload.toString()
        when (entity.entityType) {
            "season" -> {
                val season = appJson.decodeFromString<SeasonEntity>(payload)
                if (season.seasonId != localTeamId) return false
                seasonDao.insertSeason(season)
            }
            "player" -> playerDao.insertPlayer(appJson.decodeFromString(payload))
            "game" -> gameDao.insertGame(appJson.decodeFromString(payload))
            "availability" -> availabilityDao.upsertAll(listOf(appJson.decodeFromString(payload)))
            "assignment" -> assignmentDao.insertAll(listOf(appJson.decodeFromString(payload)))
            "goal" -> goalDao.insertGoal(appJson.decodeFromString(payload))
            else -> return false
        }
        return true
    }

    private suspend fun localEntities(localTeamId: String): List<LocalCloudEntity> {
        val team = seasonDao.getSeason(localTeamId) ?: return emptyList()
        val players = playerDao.getPlayersBySeason(localTeamId)
        val games = gameDao.getAllGames().filter { it.seasonId == localTeamId }
        val gameIds = games.map { it.gameId }.toSet()
        return buildList {
            add(localEntity("season", team.seasonId, appJson.encodeToString(team)))
            players.forEach { add(localEntity("player", it.playerId, appJson.encodeToString(it))) }
            games.forEach { add(localEntity("game", it.gameId, appJson.encodeToString(it))) }
            availabilityDao.getAll().filter { it.gameId in gameIds }.forEach {
                add(localEntity("availability", "${it.gameId}:${it.playerId}", appJson.encodeToString(it)))
            }
            assignmentDao.getAll().filter { it.gameId in gameIds }.forEach {
                add(localEntity("assignment", it.assignmentId, appJson.encodeToString(it)))
            }
            goalDao.getAll().filter { it.gameId in gameIds }.forEach {
                add(localEntity("goal", it.goalEventId, appJson.encodeToString(it)))
            }
        }
    }

    private suspend fun createInternalBackup(): File {
        val backup = SoccerDataBackup(
            seasons = seasonDao.getAllSeasons(),
            players = playerDao.getAllPlayers(),
            games = gameDao.getAllGames(),
            availability = availabilityDao.getAll(),
            assignments = assignmentDao.getAll(),
            goals = goalDao.getAll(),
        )
        return File(context.filesDir, "pre-cloud-backup-${System.currentTimeMillis()}.json").apply {
            writeText(appJson.encodeToString(backup))
        }
    }

    private fun localEntity(type: String, id: String, json: String) = LocalCloudEntity(type, id, json, hash(json))

    private fun PendingMutationEntity.toDto(deviceId: String): MutationCommandDto {
        val payload = payloadJson?.let(appJson::parseToJsonElement)
        val gameId = when (entityType) {
            "game" -> entityId
            else -> payload?.jsonObject?.get("gameId")?.jsonPrimitive?.content
        }
        return MutationCommandDto(
            mutationId = mutationId,
            deviceId = deviceId,
            teamId = cloudTeamId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            expectedVersion = expectedVersion,
            payload = payload,
            createdAt = createdAt,
            cell = cellJson?.let { appJson.decodeFromString(it) },
            controllerLeaseToken = gameId?.let(credentials::controllerToken),
        )
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class LocalCloudEntity(
        val entityType: String,
        val entityId: String,
        val payloadJson: String,
        val hash: String,
    )
}
