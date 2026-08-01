package az.simplesoft.aura.data.plugins.zaycev

import az.simplesoft.aura.data.plugins.core.LegacyMusicPluginAdapter
import az.simplesoft.aura.data.plugins.core.MusicPlugin
import az.simplesoft.aura.data.providers.zaycev.ZaycevProvider

/**
 * Compatibility boundary for the proven Zaycev implementation.
 * The provider remains unchanged until Plugin Core reaches physical parity.
 */
class ZaycevMusicPlugin(
    provider: ZaycevProvider
) : MusicPlugin by LegacyMusicPluginAdapter(provider)
