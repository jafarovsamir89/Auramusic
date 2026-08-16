package az.simplesoft.aura.ui

/** Limits used by the interactive search screen. Providers currently expose up to 50 items. */
internal object SearchPaging {
    const val INITIAL_LIMIT = 20
    const val PAGE_SIZE = 20
    const val MAX_LIMIT = 50

    fun nextLimit(current: Int): Int? {
        if (current >= MAX_LIMIT) return null
        return (current + PAGE_SIZE).coerceAtMost(MAX_LIMIT)
    }
}
