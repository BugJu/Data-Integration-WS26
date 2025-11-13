package uni.dj.mavendepplugin

data class Dependency(
    val groupId: String,
    val artifactId: String,
    val name: String,
    val version: String,
    val scope: String,
    val isOmitted: Boolean,
    val winner: Boolean? = null,
    val isLeaf: Boolean,
    val parent: String? = null,
    val winnerNodeName: String? = null,
    val children: List<Dependency>,
)