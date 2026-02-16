package uni.dj.mavendepplugin

object DependencyManager {
    var allNodes: List<Dependency> = emptyList()
    var dependencyTree: Dependency? = null

    fun findWinnerDependency(currNodeName: String): Dependency? {
        try {
            val cleaned = currNodeName.replace(":jar:", ":").replace("?", "").trim()

            val parts = cleaned.split(":")
            if (parts.size < 3) {
                println("DEBUG: Ungültiges Format: $currNodeName")
                return null
            }

            val groupId = parts[0]
            val artifactId = parts[1]
            val version = parts.getOrNull(2)?.substringBefore(" ")?.trim() ?: ""

            return allNodes.find { node ->
                !node.isOmitted && node.groupId == groupId && node.artifactId == artifactId && node.version == version
            }
        } catch (e: Exception) {
            println("ERROR: Error in findWinnerDependency: ${e.message}")
            return null
        }
    }

    fun buildLabel(dependency: Dependency): String {
        var label = "${dependency.artifactId}:${dependency.version} (${dependency.scope})"
        if (dependency.isOmitted) {
            label += " -OMITTED"
        }
        return label
    }

    fun collectAllNodes(node: Dependency, list: MutableList<Dependency> = mutableListOf()): List<Dependency> {
        list.add(node)
        for (child in node.children) {
            collectAllNodes(child, list)
        }
        return list
    }

    fun findDirectParent(currNode: Dependency): Dependency? {
        return allNodes.find { node ->
            node.children.any { child ->
                child.groupId == currNode.groupId && child.artifactId == currNode.artifactId && child.version == currNode.version
            }
        }
    }

    fun findAllParents(currNode: Dependency, visited: MutableSet<String> = mutableSetOf()): List<Dependency> {
        val parents = mutableListOf<Dependency>()
        val nodeId = "${currNode.groupId}:${currNode.artifactId}:${currNode.version}"

        if (nodeId in visited) {
            return parents
        }
        visited.add(nodeId)

        val directParent = findDirectParent(currNode)
        if (directParent != null) {
            parents.add(directParent)
            parents.addAll(findAllParents(directParent, visited))
        }

        return parents
    }

    fun totalOmittedCount(node: Dependency?): Int {
        return countOmittedRecursive(node)
    }

    private fun countOmittedRecursive(node: Dependency?): Int {
        if (node == null) return 0
        var count = if (node.isOmitted) 1 else 0
        for (child in node.children) {
            count += countOmittedRecursive(child)
        }
        return count
    }
}
