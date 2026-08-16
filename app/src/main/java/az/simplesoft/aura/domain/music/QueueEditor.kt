package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track

data class QueueEditResult(
    val tracks: List<Track>,
    val currentIndex: Int
)

enum class AuraRepeatMode {
    OFF,
    ONE,
    ALL;

    fun next(): AuraRepeatMode = entries[(ordinal + 1) % entries.size]
}

/** Pure queue transformations shared by UI actions and playback synchronization. */
object QueueEditor {
    fun playNext(queue: List<Track>, currentIndex: Int, track: Track): QueueEditResult =
        insert(queue, currentIndex, track, afterCurrent = true)

    fun addToEnd(queue: List<Track>, currentIndex: Int, track: Track): QueueEditResult =
        insert(queue, currentIndex, track, afterCurrent = false)

    fun remove(queue: List<Track>, currentIndex: Int, trackId: String): QueueEditResult {
        val current = queue.getOrNull(currentIndex) ?: return normalized(queue, currentIndex)
        if (current.id == trackId || queue.size <= 1) return normalized(queue, currentIndex)
        val updated = queue.filterNot { it.id == trackId }
        return QueueEditResult(updated, updated.indexOfFirst { it.id == current.id }.coerceAtLeast(0))
    }

    fun move(queue: List<Track>, currentIndex: Int, from: Int, to: Int): QueueEditResult {
        val current = queue.getOrNull(currentIndex) ?: return normalized(queue, currentIndex)
        if (from !in queue.indices || to !in queue.indices || from == to) return normalized(queue, currentIndex)
        val updated = queue.toMutableList().apply { add(to, removeAt(from)) }
        return QueueEditResult(updated, updated.indexOfFirst { it.id == current.id }.coerceAtLeast(0))
    }

    fun clear(queue: List<Track>, currentIndex: Int): QueueEditResult {
        val current = queue.getOrNull(currentIndex) ?: return QueueEditResult(emptyList(), 0)
        return QueueEditResult(listOf(current), 0)
    }

    fun removeLast(queue: List<Track>, currentIndex: Int): QueueEditResult {
        if (queue.size <= 1) return normalized(queue, currentIndex)
        val current = queue.getOrNull(currentIndex)
        val updated = queue.dropLast(1)
        return QueueEditResult(
            tracks = updated,
            currentIndex = current?.let { updated.indexOfFirst { track -> track.id == it.id } }
                ?.coerceAtLeast(0)
                ?: currentIndex.coerceIn(0, updated.lastIndex)
        )
    }

    private fun insert(
        queue: List<Track>,
        currentIndex: Int,
        track: Track,
        afterCurrent: Boolean
    ): QueueEditResult {
        val current = queue.getOrNull(currentIndex)
        if (current?.id == track.id) return normalized(queue, currentIndex)
        val withoutDuplicate = queue.filterNot { it.id == track.id }.toMutableList()
        val currentInUpdated = current?.let { playing ->
            withoutDuplicate.indexOfFirst { it.id == playing.id }.takeIf { it >= 0 }
        } ?: -1
        val insertAt = if (afterCurrent && currentInUpdated >= 0) {
            (currentInUpdated + 1).coerceAtMost(withoutDuplicate.size)
        } else {
            withoutDuplicate.size
        }
        withoutDuplicate.add(insertAt, track)
        return QueueEditResult(
            tracks = withoutDuplicate,
            currentIndex = current?.let { playing ->
                withoutDuplicate.indexOfFirst { it.id == playing.id }.coerceAtLeast(0)
            } ?: 0
        )
    }

    private fun normalized(queue: List<Track>, currentIndex: Int) = QueueEditResult(
        tracks = queue,
        currentIndex = currentIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
    )
}
