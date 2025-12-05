package uni.dj.mavendepplugin

import kotlinx.serialization.Serializable

/***
Data class for Change logging
 ***/

@Serializable
data class DepChange(
    val newGroupID: String,
    val newArtifactID: String,
    val newName: String,
    val newVersion: String,
    val operationTye: String,
    val oldGroupID: String,
    val oldArtifactID: String,
    val oldName: String,
    val oldVersion: String,
    val date: String
)