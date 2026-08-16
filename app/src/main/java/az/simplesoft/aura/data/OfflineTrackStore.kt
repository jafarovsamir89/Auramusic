package az.simplesoft.aura.data

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private on-device music library. It intentionally accepts only direct, authorized
 * audio streams; YouTube/watch-page URLs are never downloaded here.
 */
class OfflineTrackStore(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "offline-music")
    private val catalogFile = File(root, "catalog.json")
    private val fileMutex = Mutex()

    init {
        // A process kill during a download must not leave unusable partial files
        // that are mistaken for a completed library item later.
        root.listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun load(): List<Track> = withContext(Dispatchers.IO) {
        readRecords().mapNotNull { record ->
            val file = File(record.filePath)
            if (!file.isFile || file.length() <= 0L) null else record.toTrack()
        }
    }

    suspend fun download(
        track: Track,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Track = withContext(Dispatchers.IO) {
        fileMutex.withLock {
        require(track.sourceId != "youtube") { "YouTube tracks cannot be downloaded" }
        require(track.sourceId != "radio_browser") { "Radio stations cannot be downloaded" }
        require(track.sourceId in DOWNLOADABLE_SOURCES) {
            "Only authorized music catalog streams can be saved offline"
        }
        val sourceUrl = track.streamUrl?.takeIf { it.startsWith("https://") }
            ?: error("This track has no downloadable audio stream")
        val sourceKey = track.id
        readRecords().firstOrNull { it.sourceTrackId == sourceKey }?.let { existing ->
            if (File(existing.filePath).isFile) return@withContext existing.toTrack()
        }
        if (!root.exists()) check(root.mkdirs()) { "Cannot create offline music directory" }
        val stableId = "offline-${sha256(sourceKey).take(16)}"
        val target = File(root, "$stableId.mp3")
        val part = File(root, "$stableId.part")
        val request = Request.Builder()
            .url(sourceUrl)
            .header("User-Agent", USER_AGENT)
            .apply { track.requestHeaders.forEach { (name, value) -> header(name, value) } }
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Audio download failed: HTTP ${response.code}" }
                val body = checkNotNull(response.body) { "Audio download returned no body" }
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            check(availableBytes() >= MIN_FREE_BYTES) { "Недостаточно свободного места" }
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(downloadedBytes, totalBytes)
                            ensureActive()
                        }
                    }
                }
            }
            check(part.length() > 0L) { "Audio download returned an empty file" }
            check(part.renameTo(target)) { "Cannot finalize offline audio file" }
            val saved = OfflineTrackRecord(
                sourceTrackId = sourceKey,
                id = stableId,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                year = track.year,
                filePath = target.absolutePath,
                addedAt = System.currentTimeMillis()
            )
            writeRecords((readRecords().filterNot { it.sourceTrackId == sourceKey } + saved).distinctBy { it.id })
            saved.toTrack()
        } catch (error: Throwable) {
            part.delete()
            target.delete()
            throw error
        }
        }
    }

    suspend fun remove(track: Track): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
        val records = readRecords()
        val record = records.firstOrNull { it.id == track.id || it.sourceTrackId == track.id } ?: return@withContext false
        File(record.filePath).delete()
        writeRecords(records.filterNot { it.id == record.id })
        true
        }
    }

    suspend fun removeAll(): Int = withContext(Dispatchers.IO) {
        fileMutex.withLock {
        val records = readRecords()
        records.forEach { File(it.filePath).delete() }
        writeRecords(emptyList())
        records.size
        }
    }

    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        readRecords().sumOf { record -> File(record.filePath).takeIf(File::isFile)?.length() ?: 0L }
    }

    suspend fun downloadedSourceTrackIds(): Set<String> = withContext(Dispatchers.IO) {
        readRecords().mapTo(mutableSetOf(), OfflineTrackRecord::sourceTrackId)
    }

    private fun readRecords(): List<OfflineTrackRecord> {
        if (!catalogFile.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(catalogFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(OfflineTrackRecord.fromJson(item))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecords(records: List<OfflineTrackRecord>) {
        if (!root.exists()) check(root.mkdirs()) { "Cannot create offline music directory" }
        val temp = File(root, "catalog.json.part")
        val json = JSONArray().apply { records.forEach { put(it.toJson()) } }
        temp.writeText(json.toString())
        check(temp.renameTo(catalogFile)) { "Cannot finalize offline catalog" }
    }

    private fun availableBytes(): Long = runCatching {
        val storage = appContext.getSystemService(StorageManager::class.java)
        storage.getAllocatableBytes(storage.getUuidForPath(root))
    }.getOrElse { StatFs(root.path).availableBytes }

    private data class OfflineTrackRecord(
        val sourceTrackId: String,
        val id: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val durationMs: Long?,
        val year: Int?,
        val filePath: String,
        val addedAt: Long = 0L
    ) {
        fun toTrack() = Track(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            sourceId = "local",
            sourcePageUrl = "file://$filePath",
            playbackType = PlaybackType.LOCAL,
            streamUrl = "file://$filePath",
            isPlayable = true,
            year = year,
            addedAt = addedAt.takeIf { it > 0L }
        )

        fun toJson() = JSONObject().apply {
            put("sourceTrackId", sourceTrackId)
            put("id", id)
            put("title", title)
            put("artist", artist)
            put("artworkUrl", artworkUrl ?: JSONObject.NULL)
            put("durationMs", durationMs ?: JSONObject.NULL)
            put("year", year ?: JSONObject.NULL)
            put("filePath", filePath)
            put("addedAt", addedAt)
        }

        companion object {
            fun fromJson(json: JSONObject) = OfflineTrackRecord(
                sourceTrackId = json.optString("sourceTrackId"),
                id = json.optString("id"),
                title = json.optString("title", "Без названия"),
                artist = json.optString("artist", "Неизвестный исполнитель"),
                artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                durationMs = json.optLong("durationMs").takeIf { it > 0L },
                year = json.optInt("year").takeIf { it > 0 },
                filePath = json.optString("filePath"),
                addedAt = json.optLong("addedAt")
            )
        }
    }

    private companion object {
        const val USER_AGENT = "AuraMusic/0.7 (offline music library)"
        val DOWNLOADABLE_SOURCES = setOf("muzofond", "vol")
        const val MIN_FREE_BYTES = 8L * 1024L * 1024L

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
