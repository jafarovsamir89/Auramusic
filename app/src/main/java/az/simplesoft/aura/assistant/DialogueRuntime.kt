package az.simplesoft.aura.assistant

import android.content.Context
import az.simplesoft.aura.data.database.AssistantConversationStateEntity
import az.simplesoft.aura.data.database.AssistantDialogueNodeEntity
import az.simplesoft.aura.data.database.AssistantDialogueVariantEntity
import az.simplesoft.aura.data.database.AssistantIntentPatternEntity
import az.simplesoft.aura.data.database.AssistantResponseStatEntity
import az.simplesoft.aura.data.database.AssistantUnknownUtteranceEntity
import az.simplesoft.aura.data.database.AuraDatabase
import az.simplesoft.aura.data.database.AuraStateDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

enum class AssistantIntent {
    GREETING, FAREWELL, THANKS, INTRODUCTION, NAME, MOOD, FATIGUE, SADNESS, JOY,
    WORK, REST, MUSIC, CAPABILITIES, MEMORY, UNKNOWN, CLARIFICATION, CONFIRMATION,
    REFUSAL, CONTINUATION
}

data class IntentMatch(
    val intent: AssistantIntent,
    val confidence: Float,
    val slots: Map<String, String> = emptyMap(),
    val negated: Boolean = false,
    val matchedPatternId: String? = null,
    val reasons: List<String> = emptyList()
)

data class DialogueState(
    val nodeId: String? = null,
    val topic: String? = null,
    val emotion: String? = null,
    val expectedIntent: AssistantIntent? = null,
    val failureCount: Int = 0,
    val lastBranch: String? = null
)

object AssistantTextNormalizer {
    private val punctuation = Regex("[^\\p{L}\\p{N}']+")

    fun normalize(text: String): String = text
        .lowercase()
        .replace('ё', 'е')
        .replace('ә', 'ə')
        .replace(punctuation, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter(String::isNotBlank)

    fun isNegated(text: String): Boolean {
        val words = tokens(text)
        val negations = setOf("не", "нет", "никогда", "нехочу", "don't", "not", "no", "yox", "deyil")
        return words.any { it in negations }
    }
}

class DialogueRepository(private val dao: AuraStateDao) {
    constructor(context: Context) : this(AuraDatabase.get(context).stateDao())

    suspend fun state(): DialogueState = dao.conversationState()?.let {
        DialogueState(
            nodeId = it.nodeId,
            topic = it.topic,
            emotion = it.emotion,
            expectedIntent = it.expectedIntent?.let { value -> runCatching { AssistantIntent.valueOf(value) }.getOrNull() },
            failureCount = it.failureCount,
            lastBranch = it.lastBranch
        )
    } ?: DialogueState()

    suspend fun saveState(state: DialogueState) {
        dao.upsertConversationState(
            AssistantConversationStateEntity(
                nodeId = state.nodeId,
                topic = state.topic,
                emotion = state.emotion,
                expectedIntent = state.expectedIntent?.name,
                failureCount = state.failureCount,
                lastBranch = state.lastBranch,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun patterns(language: AssistantLanguage): List<AssistantIntentPatternEntity> =
        dao.intentPatterns(language.tag.substringBefore('-'))

    suspend fun nodes(language: AssistantLanguage): List<AssistantDialogueNodeEntity> =
        dao.dialogueNodes(language.tag.substringBefore('-'))

    suspend fun nodeForPattern(patternId: String): AssistantDialogueNodeEntity? = dao.dialogueNodeForPattern(patternId)

    suspend fun variants(nodeId: String, language: AssistantLanguage): List<AssistantDialogueVariantEntity> =
        dao.dialogueVariants(nodeId, language.tag.substringBefore('-'))

    suspend fun stats(ids: List<String>): Map<String, AssistantResponseStatEntity> =
        if (ids.isEmpty()) emptyMap() else dao.responseStats(ids).associateBy { it.variantId }

    suspend fun recordVariant(variant: AssistantDialogueVariantEntity) {
        val current = dao.responseStats(listOf(variant.id)).firstOrNull()
        dao.upsertResponseStat(
            AssistantResponseStatEntity(
                variantId = variant.id,
                usedCount = (current?.usedCount ?: 0) + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordUnknown(text: String, language: AssistantLanguage, topic: String?, result: String) {
        val normalized = AssistantTextNormalizer.normalize(text).take(320)
        if (normalized.isBlank()) return
        val now = System.currentTimeMillis()
        val current = dao.unknownUtterance(normalized)
        dao.upsertUnknownUtterance(
            AssistantUnknownUtteranceEntity(
                id = current?.id ?: "unknown:${UUID.randomUUID()}",
                normalizedText = normalized,
                language = language.tag,
                topic = topic,
                result = result.take(32),
                frequency = (current?.frequency ?: 0) + 1,
                firstSeenAt = current?.firstSeenAt ?: now,
                lastSeenAt = now
            )
        )
    }
}

class DialogueSeedImporter(private val context: Context, private val repository: DialogueRepository) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val manifest = context.assets.open("assistant/dialogue_manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val version = manifest.optString("version", "1")
        val current = AuraDatabase.get(context).stateDao().preference("assistant_dialogue_version")
        if (current == version) return@withContext

        val nodes = mutableListOf<AssistantDialogueNodeEntity>()
        val variants = mutableListOf<AssistantDialogueVariantEntity>()
        val patterns = mutableListOf<AssistantIntentPatternEntity>()
        val languages = manifest.optJSONArray("languages") ?: JSONArray()
        for (index in 0 until languages.length()) {
            val language = languages.optString(index)
            val file = "assistant/dialogue_${language}.json"
            val root = context.assets.open(file).bufferedReader().use { JSONObject(it.readText()) }
            val jsonNodes = root.optJSONArray("nodes") ?: JSONArray()
            for (nodeIndex in 0 until jsonNodes.length()) {
                val node = jsonNodes.getJSONObject(nodeIndex)
                val nodeId = node.getString("id")
                nodes += AssistantDialogueNodeEntity(
                    id = nodeId,
                    topic = node.optString("topic", "general"),
                    language = language,
                    nextNodeId = node.optString("nextNodeId").takeIf(String::isNotBlank),
                    priority = node.optInt("priority", 0)
                )
                val jsonVariants = node.optJSONArray("variants") ?: JSONArray()
                for (variantIndex in 0 until jsonVariants.length()) {
                    val variant = jsonVariants.getJSONObject(variantIndex)
                    variants += AssistantDialogueVariantEntity(
                        id = variant.getString("id"),
                        nodeId = nodeId,
                        language = language,
                        tone = variant.optString("tone", "warm"),
                        text = variant.getString("text"),
                        weight = max(1, variant.optInt("weight", 1)),
                        cooldownKey = variant.optString("cooldownKey").takeIf(String::isNotBlank)
                    )
                }
                val jsonPatterns = node.optJSONArray("patterns") ?: JSONArray()
                for (patternIndex in 0 until jsonPatterns.length()) {
                    val pattern = jsonPatterns.getJSONObject(patternIndex)
                    patterns += AssistantIntentPatternEntity(
                        id = pattern.getString("id"),
                        nodeId = nodeId,
                        intent = pattern.getString("intent"),
                        language = language,
                        pattern = AssistantTextNormalizer.normalize(pattern.getString("pattern")),
                        emotion = pattern.optString("emotion").takeIf(String::isNotBlank),
                        priority = pattern.optInt("priority", 0)
                    )
                }
            }
        }
        val ids = nodes.map { it.id }.toSet()
        require(nodes.all { it.nextNodeId == null || it.nextNodeId in ids }) { "Dialogue seed has a broken nextNodeId" }
        require(variants.all { it.nodeId in ids }) { "Dialogue seed has a broken variant link" }
        require(patterns.all { it.nodeId in ids }) { "Dialogue seed has a broken pattern link" }
        require(patterns.all { it.intent in AssistantIntent.entries.map(AssistantIntent::name) }) { "Dialogue seed has an unknown intent" }
        AuraDatabase.get(context).stateDao().importDialoguePackage(nodes, variants, patterns, version, System.currentTimeMillis())
    }
}

class LocalDialogueMatcher(private val repository: DialogueRepository) {
    suspend fun match(text: String, language: AssistantLanguage, state: DialogueState): IntentMatch {
        val normalized = AssistantTextNormalizer.normalize(text)
        if (normalized.isBlank()) return IntentMatch(AssistantIntent.UNKNOWN, 0f, reasons = listOf("empty"))
        val tokens = AssistantTextNormalizer.tokens(normalized).toSet()
        return repository.patterns(language).asSequence()
            .mapNotNull { pattern ->
                val phraseTokens = AssistantTextNormalizer.tokens(pattern.pattern)
                val exact = normalized == pattern.pattern
                val overlap = phraseTokens.count { it in tokens }.toFloat() / phraseTokens.size.coerceAtLeast(1)
                val contextBoost = if (state.expectedIntent?.name == pattern.intent) 0.08f else 0f
                val score = (if (exact) 1f else overlap * 0.84f) + contextBoost + pattern.priority / 1000f
                if (score < 0.38f) null else {
                    val negated = AssistantTextNormalizer.isNegated(normalized) && pattern.intent in setOf("PAUSE", "STOP", "NEXT")
                    IntentMatch(
                        intent = runCatching { AssistantIntent.valueOf(pattern.intent) }.getOrDefault(AssistantIntent.UNKNOWN),
                        confidence = score.coerceIn(0f, 1f),
                        negated = negated,
                        matchedPatternId = pattern.id,
                        reasons = listOf(if (exact) "exact phrase" else "token overlap", "priority=${pattern.priority}")
                    )
                }
            }
            .maxByOrNull { it.confidence }
            ?: IntentMatch(AssistantIntent.UNKNOWN, 0f, reasons = listOf("no pattern"))
    }
}

class DialogueStateMachine(private val repository: DialogueRepository) {
    suspend fun advance(match: IntentMatch, topic: String?, nodeId: String?, previous: DialogueState = DialogueState()) {
        val failureCount = if (match.confidence >= .65f) 0 else previous.failureCount + 1
        repository.saveState(
            DialogueState(
                nodeId = nodeId,
                topic = topic,
                emotion = match.intent.name,
                expectedIntent = when {
                    match.intent == AssistantIntent.CLARIFICATION -> AssistantIntent.CONFIRMATION
                    failureCount >= 3 -> null
                    match.intent == AssistantIntent.UNKNOWN -> previous.expectedIntent
                    else -> null
                },
                failureCount = failureCount,
                lastBranch = match.matchedPatternId
            )
        )
    }
}

class DialogueCooldownManager {
    private val lastUsed = mutableMapOf<String, Long>()
    fun available(key: String?, now: Long = System.currentTimeMillis()): Boolean =
        key == null || now - (lastUsed[key] ?: 0L) >= COOLDOWN_MS
    fun mark(key: String?, now: Long = System.currentTimeMillis()) { if (key != null) lastUsed[key] = now }
    companion object { private const val COOLDOWN_MS = 15_000L }
}

class ResponseVariantSelector(
    private val repository: DialogueRepository,
    private val cooldowns: DialogueCooldownManager = DialogueCooldownManager(),
    private val randomIndex: (Int) -> Int = { Random.nextInt(it) }
) {
    private var lastVariantId: String? = null

    suspend fun choose(variants: List<AssistantDialogueVariantEntity>, language: AssistantLanguage): AssistantDialogueVariantEntity? {
        if (variants.isEmpty()) return null
        val stats = repository.stats(variants.map { it.id })
        val available = variants.filter { cooldowns.available(it.cooldownKey) }
        val withoutImmediateRepeat = available.filterNot { it.id == lastVariantId }
        val pool = when {
            withoutImmediateRepeat.isNotEmpty() -> withoutImmediateRepeat
            available.isNotEmpty() -> available
            else -> variants.filterNot { it.id == lastVariantId }.ifEmpty { variants }
        }
        val weighted = pool.flatMap { variant ->
            val used = stats[variant.id]?.usedCount ?: 0
            val adjustedWeight = (max(1, variant.weight) / (1f + used * 0.25f)).toInt().coerceAtLeast(1)
            List(adjustedWeight) { variant }
        }
        val choice = weighted[randomIndex(weighted.size)]
        lastVariantId = choice.id
        cooldowns.mark(choice.cooldownKey)
        repository.recordVariant(choice)
        return choice
    }
}

class DataBackedCompanionEngine(context: Context) {
    private val repository = DialogueRepository(context)
    private val importer = DialogueSeedImporter(context, repository)
    private val matcher = LocalDialogueMatcher(repository)
    private val stateMachine = DialogueStateMachine(repository)
    private val selector = ResponseVariantSelector(repository)
    @Volatile private var imported = false

    suspend fun respond(input: String, language: AssistantLanguage): AssistantReply {
        if (!imported) runCatching { importer.importIfNeeded() }.onSuccess { imported = true }
        val state = repository.state()
        val match = matcher.match(input, language, state)
        val nodes = repository.nodes(language)
        val matchedNode = if (match.matchedPatternId != null) {
            repository.nodeForPattern(match.matchedPatternId)
        } else {
            null
        }
        val previousNode = nodes.firstOrNull { it.id == state.nodeId }
        var node = when {
            matchedNode != null -> matchedNode
            match.intent in setOf(AssistantIntent.CONFIRMATION, AssistantIntent.REFUSAL, AssistantIntent.CONTINUATION) ->
                previousNode?.nextNodeId?.let { next -> nodes.firstOrNull { it.id == next } } ?: previousNode
            else -> null
        }
        if (node == null) {
            for (candidate in nodes) {
                if (repository.variants(candidate.id, language).isNotEmpty()) {
                    node = candidate
                    break
                }
            }
        }
        node = node ?: nodes.firstOrNull()
        val variant = node?.let { selector.choose(repository.variants(it.id, language), language) }
        stateMachine.advance(match, node?.topic, node?.nextNodeId ?: node?.id, state)
        if (match.intent == AssistantIntent.UNKNOWN || variant == null) repository.recordUnknown(input, language, state.topic, "unknown")
        return AssistantReply(MusicIntent.Unknown, variant?.text ?: fallback(language), language, AssistantRoute.LOCAL_CONVERSATION)
    }

    private fun fallback(language: AssistantLanguage): String = when (language) {
        AssistantLanguage.RUSSIAN -> "Я рядом. Могу помочь с музыкой или просто поговорить."
        AssistantLanguage.AZERBAIJANI -> "Buradayam. Musiqi və söhbətdə kömək edə bilərəm."
        AssistantLanguage.ENGLISH -> "I am here. I can help with music or keep you company."
    }
}
