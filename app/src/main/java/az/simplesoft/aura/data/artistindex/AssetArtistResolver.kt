package az.simplesoft.aura.data.artistindex

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import az.simplesoft.aura.domain.artist.ArtistIndexEntry
import az.simplesoft.aura.domain.artist.ArtistResolveContext
import az.simplesoft.aura.domain.artist.ArtistResolveResult
import az.simplesoft.aura.domain.artist.ArtistResolver
import az.simplesoft.aura.domain.artist.ArtistNameNormalizer
import az.simplesoft.aura.domain.artist.AzerbaijaniArtistMorphologyNormalizer
import az.simplesoft.aura.domain.artist.InMemoryArtistResolver
import java.io.File

data class ArtistIndexMetadata(
    val version: String,
    val snapshotDate: String,
    val artistCount: Long,
    val aliasCount: Long,
    val checksum: String?
)

/** Reads the generated DB without loading the millions of rows into Kotlin memory. */
class AssetArtistResolver(
    private val context: Context,
    private val assetName: String = "aura_artists.db"
) : ArtistResolver, AutoCloseable {
    private val lock = Any()
    @Volatile private var database: SQLiteDatabase? = null
    @Volatile private var available: Boolean? = null

    fun isAvailable(): Boolean = available ?: synchronized(lock) {
        available ?: runCatching { context.assets.open(assetName).use { true } }.getOrDefault(false).also { available = it }
    }

    fun metadata(): ArtistIndexMetadata? = withDatabase { db ->
        db.rawQuery("SELECT version, snapshot_date, artist_count, alias_count, checksum FROM artist_index_metadata LIMIT 1", null).use { cursor ->
            if (!cursor.moveToFirst()) return@withDatabase null
            ArtistIndexMetadata(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), cursor.getString(4))
        }
    }

    override suspend fun resolve(rawArtistName: String, context: ArtistResolveContext): ArtistResolveResult {
        val entries = withDatabase { db -> queryEntries(db, rawArtistName) }.orEmpty()
        if (entries.isEmpty()) return InMemoryArtistResolver(emptyList()).resolve(rawArtistName, context)
        return InMemoryArtistResolver(entries).resolve(rawArtistName, context)
    }

    override fun close() {
        synchronized(lock) {
            database?.close()
            database = null
        }
    }

    private fun queryEntries(db: SQLiteDatabase, raw: String): List<ArtistIndexEntry> {
        val normalized = ArtistNameNormalizer.normalize(raw)
        val folded = ArtistNameNormalizer.folded(raw)
        val rows = linkedMapOf<Long, MutableEntry>()
        val sql = """
            SELECT a.id, a.mbid, a.canonical_name, a.sort_name, a.country, a.type, a.disambiguation,
                   NULL AS alias FROM artists a
            WHERE a.normalized_name = ? OR a.folded_name = ? OR a.normalized_name LIKE ? OR a.folded_name LIKE ?
            UNION ALL
            SELECT a.id, a.mbid, a.canonical_name, a.sort_name, a.country, a.type, a.disambiguation,
                   al.alias FROM artist_aliases al JOIN artists a ON a.id = al.artist_id
            WHERE al.normalized_alias = ? OR al.folded_alias = ? OR al.normalized_alias LIKE ? OR al.folded_alias LIKE ?
            LIMIT 100
        """.trimIndent()
        val args = arrayOf(normalized, folded, "$normalized%", "$folded%", normalized, folded, "$normalized%", "$folded%")
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val item = rows.getOrPut(id) {
                    MutableEntry(id, cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6))
                }
                if (!cursor.isNull(7)) item.aliases += cursor.getString(7)
            }
        }
        if (rows.isEmpty()) {
            val base = AzerbaijaniArtistMorphologyNormalizer.baseForm(raw)
            if (base != normalized) return queryEntries(db, base)
        }
        return rows.values.map { it.toModel() }
    }

    private data class MutableEntry(
        val id: Long,
        val mbid: String?,
        val canonical: String,
        val sort: String?,
        val country: String?,
        val type: String?,
        val disambiguation: String?,
        val aliases: MutableList<String> = mutableListOf()
    ) {
        fun toModel() = ArtistIndexEntry(id, mbid, canonical, sort, country, type, disambiguation, aliases)
    }

    private fun <T> withDatabase(block: (SQLiteDatabase) -> T): T? = runCatching {
        val db = synchronized(lock) { database ?: openDatabase().also { database = it } }
        block(db)
    }.getOrNull()

    private fun openDatabase(): SQLiteDatabase {
        val target = File(context.noBackupFilesDir, assetName)
        if (!target.exists() || target.length() == 0L) {
            val temporary = File(target.parentFile, "$assetName.tmp")
            context.assets.open(assetName).use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                error("Unable to install artist index")
            }
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }
}

class CompositeArtistResolver(context: Context) : ArtistResolver, AutoCloseable {
    private val asset = AssetArtistResolver(context)
    private val fallback = InMemoryArtistResolver.default

    override suspend fun resolve(rawArtistName: String, context: ArtistResolveContext): ArtistResolveResult {
        if (asset.isAvailable()) {
            val result = asset.resolve(rawArtistName, context)
            if (result !is ArtistResolveResult.NotFound) return result
        }
        return fallback.resolve(rawArtistName, context)
    }

    override fun close() = asset.close()
}
