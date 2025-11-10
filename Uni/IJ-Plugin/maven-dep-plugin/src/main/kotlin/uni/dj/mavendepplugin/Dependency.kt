package uni.dj.mavendepplugin

import com.jetbrains.rhizomedb.Schema

data class Dependency (
    val groupId: String,
    val artifactId: String,
    val name: String,
    val version: String,
    val scope : String,
    val isOmmited : Boolean,
    val winner : Boolean? = null,
    val isLeaf : Boolean,
    val parent : String? = null,
    val children: List<Dependency>,
)