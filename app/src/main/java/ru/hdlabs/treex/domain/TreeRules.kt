package ru.hdlabs.treex.domain

object TreeRules {
    fun createsCycle(
        parents: Map<String, Pair<String?, String?>>,
        personId: String,
        fatherId: String?,
        motherId: String?,
    ): Boolean {
        if (fatherId == personId || motherId == personId) return true
        if (fatherId != null && reaches(parents, personId, fatherId)) return true
        if (motherId != null && reaches(parents, personId, motherId)) return true
        return false
    }

    private fun reaches(
        parents: Map<String, Pair<String?, String?>>,
        start: String,
        target: String,
    ): Boolean {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            if (id == target && seen.size > 1) return true
            parents.forEach { (child, pair) ->
                if (pair.first == id || pair.second == id) queue.add(child)
            }
        }
        return false
    }
}
