package az.simplesoft.aura.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicProvider(private val context: Context) {
    suspend fun load(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val tracks = mutableListOf<Track>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumId = cursor.getLong(albumColumn)
                    tracks += Track(
                        id = "local-$id",
                        title = cursor.getString(titleColumn).orEmpty().ifBlank { "Без названия" },
                        artist = cursor.getString(artistColumn).orEmpty()
                            .takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Неизвестный исполнитель" },
                        artworkUrl = "content://media/external/audio/albumart/$albumId",
                        durationMs = cursor.getLong(durationColumn),
                        sourceId = "local",
                        sourcePageUrl = uri.toString(),
                        playbackType = PlaybackType.LOCAL,
                        streamUrl = uri.toString(),
                        isPlayable = true,
                        year = cursor.getInt(yearColumn).takeIf { it > 0 }
                    )
                }
            }
        }
        tracks
    }
}
