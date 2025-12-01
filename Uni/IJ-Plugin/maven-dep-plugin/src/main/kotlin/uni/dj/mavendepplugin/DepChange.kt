package uni.dj.mavendepplugin

import kotlinx.serialization.Serializable

@Serializable
data class DepChange(
    val omittedGroupID: String,
    val omittedArtifactID: String,
    val omittedName: String,
    val omittedVersion: String,
    val operationTye: String,
    val winnerGroupID: String,
    val winnerArtifactID: String,
    val winnerName: String,
    val winnerVersion: String,
    val date: String
)