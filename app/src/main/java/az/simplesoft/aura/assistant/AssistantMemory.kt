package az.simplesoft.aura.assistant

import az.simplesoft.aura.data.database.AuraStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class AssistantMemoryFact(
    val category: String,
    val key: String,
    val value: String,
    val updatedAt: Long
)

data class AssistantMemorySnapshot(
    val facts: List<AssistantMemoryFact> = emptyList(),
    val recentMessages: List<AssistantMessage> = emptyList()
) {
    fun promptSummary(): String {
        if (facts.isEmpty() && recentMessages.isEmpty()) return "No durable user facts saved."
        val factsText = facts.joinToString(separator = "\n") { "- ${it.category}.${it.key}: ${it.value}" }
        val recentText = recentMessages.takeLast(4).joinToString(separator = "\n") {
            "- ${if (it.role == AssistantRole.USER) "user" else "AURA"}: ${it.text}"
        }
        return buildString {
            append(factsText)
            if (recentText.isNotBlank()) append("\nRecent context:\n").append(recentText)
        }.take(2_400)
    }
}

interface AssistantMemoryPersistence {
    suspend fun read(): String?
    suspend fun write(value: String)
}

class RoomAssistantMemoryPersistence(
    private val repository: AuraStateRepository
) : AssistantMemoryPersistence {
    override suspend fun read(): String? = repository.loadPreference(KEY)
    override suspend fun write(value: String) = repository.savePreference(KEY, value)

    companion object {
        const val KEY = "assistant_compact_memory_v1"
    }
}

/** Keeps a bounded summary instead of complete transcripts. */
class CompactAssistantMemory(
    private val persistence: AssistantMemoryPersistence,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var cached: AssistantMemorySnapshot? = null

    suspend fun snapshot(): AssistantMemorySnapshot = mutex.withLock { loadLocked() }

    suspend fun record(
        userText: String,
        reply: AssistantReply
    ): AssistantMemorySnapshot = mutex.withLock {
        val current = loadLocked()
        val timestamp = now()
        val messages = (current.recentMessages + listOf(
            AssistantMessage("u:$timestamp", AssistantRole.USER, userText.take(MAX_MESSAGE_CHARS), reply.language, timestamp),
            AssistantMessage("a:$timestamp", AssistantRole.AURA, reply.text.take(MAX_MESSAGE_CHARS), reply.language, timestamp + 1)
        )).takeLast(MAX_RECENT_MESSAGES)
        val factsByKey = current.facts.associateByTo(linkedMapOf()) { "${it.category}:${it.key}" }
        reply.memoryInsights.forEach { insight ->
            val safeValue = insight.value.trim().replace(Regex("\\s+"), " ").take(MAX_FACT_CHARS)
            if (safeValue.isNotBlank()) {
                factsByKey["${insight.category}:${insight.key}"] = AssistantMemoryFact(
                    insight.category.take(32), insight.key.take(48), safeValue, timestamp
                )
            }
        }
        var next = AssistantMemorySnapshot(
            facts = factsByKey.values.sortedByDescending(AssistantMemoryFact::updatedAt).take(MAX_FACTS),
            recentMessages = messages
        )
        var encoded = encode(next)
        while (encoded.length > MAX_PERSISTED_CHARS && next.recentMessages.size > 2) {
            next = next.copy(recentMessages = next.recentMessages.drop(2))
            encoded = encode(next)
        }
        if (encoded.length > MAX_PERSISTED_CHARS) {
            next = next.copy(facts = next.facts.take(MAX_FACTS / 2))
            encoded = encode(next)
        }
        persistence.write(encoded.take(MAX_PERSISTED_CHARS))
        cached = next
        next
    }

    suspend fun clear() = mutex.withLock {
        cached = AssistantMemorySnapshot()
        persistence.write(encode(AssistantMemorySnapshot()))
    }

    suspend fun remember(facts: List<MemoryInsight>) = mutex.withLock {
        val current = loadLocked()
        val timestamp = now()
        val factsByKey = current.facts.associateByTo(linkedMapOf()) { "${it.category}:${it.key}" }
        facts.forEach { insight ->
            val safeValue = insight.value.trim().replace(Regex("\\s+"), " ").take(MAX_FACT_CHARS)
            if (safeValue.isNotBlank()) {
                factsByKey["${insight.category}:${insight.key}"] = AssistantMemoryFact(
                    insight.category.take(32), insight.key.take(48), safeValue, timestamp
                )
            }
        }
        val next = current.copy(facts = factsByKey.values.sortedByDescending(AssistantMemoryFact::updatedAt).take(MAX_FACTS))
        persistence.write(encode(next).take(MAX_PERSISTED_CHARS))
        cached = next
    }

    private suspend fun loadLocked(): AssistantMemorySnapshot {
        cached?.let { return it }
        val loaded = persistence.read()?.let(::decode) ?: AssistantMemorySnapshot()
        cached = loaded
        return loaded
    }

    private fun encode(value: AssistantMemorySnapshot): String = JSONObject().apply {
        put("version", 1)
        put("facts", JSONArray().apply {
            value.facts.forEach { fact ->
                put(JSONObject().apply {
                    put("category", fact.category)
                    put("key", fact.key)
                    put("value", fact.value)
                    put("updatedAt", fact.updatedAt)
                })
            }
        })
        put("recent", JSONArray().apply {
            value.recentMessages.forEach { message ->
                put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                    put("language", message.language.name)
                    put("createdAt", message.createdAt)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): AssistantMemorySnapshot = runCatching {
        val root = JSONObject(raw)
        val factsJson = root.optJSONArray("facts") ?: JSONArray()
        val facts = buildList {
            for (index in 0 until factsJson.length()) {
                val item = factsJson.optJSONObject(index) ?: continue
                add(AssistantMemoryFact(
                    category = item.optString("category").take(32),
                    key = item.optString("key").take(48),
                    value = item.optString("value").take(MAX_FACT_CHARS),
                    updatedAt = item.optLong("updatedAt")
                ))
            }
        }
        val recentJson = root.optJSONArray("recent") ?: JSONArray()
        val recent = buildList {
            for (index in 0 until recentJson.length()) {
                val item = recentJson.optJSONObject(index) ?: continue
                val role = runCatching { AssistantRole.valueOf(item.optString("role")) }.getOrNull() ?: continue
                val language = runCatching { AssistantLanguage.valueOf(item.optString("language")) }.getOrDefault(AssistantLanguage.RUSSIAN)
                add(AssistantMessage(
                    id = item.optString("id", "memory:$index"),
                    role = role,
                    text = item.optString("text").take(MAX_MESSAGE_CHARS),
                    language = language,
                    createdAt = item.optLong("createdAt")
                ))
            }
        }
        AssistantMemorySnapshot(facts.take(MAX_FACTS), recent.takeLast(MAX_RECENT_MESSAGES))
    }.getOrDefault(AssistantMemorySnapshot())

    companion object {
        const val MAX_PERSISTED_CHARS = 16_000
        const val MAX_RECENT_MESSAGES = 10
        const val MAX_MESSAGE_CHARS = 320
        const val MAX_FACT_CHARS = 180
        const val MAX_FACTS = 64
    }
}
