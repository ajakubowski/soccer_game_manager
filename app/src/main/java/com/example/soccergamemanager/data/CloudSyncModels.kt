package com.example.soccergamemanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PairDeviceRequest(
    val code: String,
    val deviceName: String,
)

@Serializable
data class PairDeviceResponse(
    val deviceId: String,
    val teamId: String,
    val token: String,
)

@Serializable
data class CloudEntityDto(
    val entityType: String,
    val entityId: String,
    val version: Long,
    val payload: JsonElement? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long,
    val updatedBy: String,
)

@Serializable
data class LineupCellDto(
    val gameId: String,
    val halfNumber: Int,
    val roundIndex: Int,
    val slotKey: String,
)

@Serializable
data class MutationCommandDto(
    val mutationId: String,
    val deviceId: String,
    val teamId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val expectedVersion: Long,
    val payload: JsonElement? = null,
    val createdAt: Long,
    val cell: LineupCellDto? = null,
    val controllerLeaseToken: String? = null,
)

@Serializable
data class MutationBatchRequest(val commands: List<MutationCommandDto>)

@Serializable
data class CloudConflictDto(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val reason: String,
    val expectedVersion: Long,
    val actualVersion: Long,
    val serverEntity: CloudEntityDto? = null,
)

@Serializable
data class MutationResultDto(
    val teamRevision: Long,
    val acceptedMutationIds: List<String>,
    val conflicts: List<CloudConflictDto>,
    val changes: List<CloudEntityDto>,
)

@Serializable
data class ChangesResponse(
    val teamRevision: Long,
    val changes: List<CloudEntityDto>,
)

@Serializable
data class PublishLineupRequest(
    val expectedTeamRevision: Long,
    val payload: JsonElement,
    val lineupName: String? = null,
)

@Serializable
data class PublishedLineupResponse(
    val gameId: String,
    val publishedVersion: Int,
    val teamRevision: Long,
    val lineupName: String? = null,
    val publishedBy: String,
    val publishedByUser: String = "",
    val publishedFromDeviceId: String = "",
    val publishedFromDeviceName: String = "",
    val publishedAt: Long,
)

@Serializable
data class ClaimControllerRequest(
    val holderName: String,
    val durationHours: Int = 24,
)

@Serializable
data class ControllerLeaseResponse(
    val gameId: String,
    val deviceId: String,
    val holderName: String,
    val expiresAt: Long,
    val claimedAt: Long,
    val leaseToken: String,
)

@Serializable
data class PublishedLineupPayload(
    val game: GameEntity,
    val availability: List<PlayerAvailabilityEntity>,
    val assignments: List<AssignmentEntity>,
    val publishedFromRevision: Long,
)
