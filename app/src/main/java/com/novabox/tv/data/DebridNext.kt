package com.novabox.tv.data

/** Finds the episode after the one playing, among the user's Real-Debrid files. */
object DebridNext {
    /** The (season, episode) that count as next: the next in this season, else the first of the next. */
    private fun targets(r: ReleaseName): List<Pair<Int, Int>> {
        val s = r.season ?: return emptyList()
        val e = r.episode ?: return emptyList()
        return listOf(s to e + 1, s + 1 to 1)
    }

    private fun sameShow(a: ReleaseName, b: ReleaseName) = a.title.isNotBlank() && a.title.equals(b.title, ignoreCase = true)

    /** The item that is the next episode after [current], or null. */
    fun <T> pick(current: String, items: List<T>, name: (T) -> String): T? {
        val c = ReleaseNames.parse(current)
        val parsed = items.map { it to ReleaseNames.parse(name(it)) }.filter { sameShow(c, it.second) }
        for ((s, e) in targets(c)) parsed.firstOrNull { it.second.season == s && it.second.episode == e }?.let { return it.first }
        return null
    }

    /** Torrents of the same show that could hold the next episode: that episode alone, or a season pack. */
    fun <T> holders(current: String, items: List<T>, name: (T) -> String): List<T> {
        val c = ReleaseNames.parse(current)
        val t = targets(c)
        if (t.isEmpty()) return emptyList()
        return items.filter { item ->
            val r = ReleaseNames.parse(name(item))
            sameShow(c, r) && (t.any { (s, e) -> r.season == s && (r.episode == null || r.episode == e) })
        }
    }

    /** True when [name] is an episode, so there can be a next one. */
    fun isEpisode(name: String) = ReleaseNames.parse(name).let { it.season != null && it.episode != null }
}
