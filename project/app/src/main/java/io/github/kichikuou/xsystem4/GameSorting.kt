package io.github.kichikuou.xsystem4

import java.text.Collator
import java.util.Locale

/** Persist stable keys, never enum ordinals. Default retains the catalog order within each group. */
enum class GameSortMode(val key: String) {
    CATALOG("catalog"), NAME("name"), POPULARITY("popularity"), RELEASE_DATE("release_date");

    companion object {
        fun fromKey(key: String?): GameSortMode = values().firstOrNull { it.key == key } ?: CATALOG
    }
}

object GameSorting {
    fun sorted(
        entries: List<GameSelectEntry>,
        mode: GameSortMode,
        locale: Locale
    ): List<GameSelectEntry> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        // sortedWith is stable: equal keys (including missing keys) retain catalog/import order.
        return entries.sortedWith(Comparator { a, b ->
            // Use the same scanned installation presence as the launcher badge, not dates or downloads.
            val installed = (b.primaryItem != null).compareTo(a.primaryItem != null)
            if (installed != 0) installed else when (mode) {
                GameSortMode.CATALOG -> 0
                GameSortMode.NAME -> collator.compare(a.descriptor.title, b.descriptor.title)
                GameSortMode.POPULARITY -> descendingKnownFirst(
                    GameSortMetadata.forGame(a.descriptor.id)?.voteCount,
                    GameSortMetadata.forGame(b.descriptor.id)?.voteCount
                )
                GameSortMode.RELEASE_DATE -> descendingKnownFirst(
                    GameSortMetadata.forGame(a.descriptor.id)?.releaseDate,
                    GameSortMetadata.forGame(b.descriptor.id)?.releaseDate
                )
            }
        })
    }

    internal fun <T : Comparable<T>> descendingKnownFirst(a: T?, b: T?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> b.compareTo(a)
    }
}
