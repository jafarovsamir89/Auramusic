package az.simplesoft.aura.domain.music

import kotlin.math.roundToInt

object VolumeMath {
    fun levelForPercent(maxLevel: Int, percent: Int): Int {
        if (maxLevel <= 0) return 0
        return (maxLevel * percent.coerceIn(0, 100) / 100.0).roundToInt().coerceIn(0, maxLevel)
    }

    fun percentForLevel(maxLevel: Int, level: Int): Int {
        if (maxLevel <= 0) return 0
        return (level.coerceIn(0, maxLevel) * 100.0 / maxLevel).roundToInt().coerceIn(0, 100)
    }
}
