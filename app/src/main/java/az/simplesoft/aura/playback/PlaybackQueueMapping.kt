package az.simplesoft.aura.playback

/** Maps AURA track ids to Media3 indices after non-playable placeholders are removed. */
internal object PlaybackQueueMapping {
    fun mediaIndexForTrack(mediaIds: List<String>, trackId: String): Int = mediaIds.indexOf(trackId)
}
