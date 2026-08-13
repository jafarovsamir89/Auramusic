package az.simplesoft.aura.assistant

data class AssistantTrackContext(
    val id: String,
    val title: String,
    val artist: String
)

data class AssistantContext(
    val currentTrack: AssistantTrackContext? = null,
    val isPlaying: Boolean = false,
    val queue: List<AssistantTrackContext> = emptyList(),
    val currentIndex: Int = 0,
    val lastSearchQuery: String? = null,
    val lastSearchResults: List<AssistantTrackContext> = emptyList(),
    val playlists: List<String> = emptyList(),
    val favoriteCount: Int = 0,
    val currentPlaylist: String? = null,
    val currentTrackLiked: Boolean = false,
    val lastIntent: String? = null,
    val hourOfDay: Int = 12,
    val carMode: Boolean = false,
    val dialogue: LocalDialogueState = LocalDialogueState(),
    val memoryFacts: List<AssistantMemoryFact> = emptyList()
)

data class LocalDialogueState(
    val lastIntent: String? = null,
    val lastAction: String? = null,
    val lastEntities: List<AssistantEntity> = emptyList(),
    val lastMentionedTrack: AssistantTrackContext? = null,
    val lastMentionedArtist: String? = null,
    val currentTopic: String? = null,
    val pendingClarification: String? = null,
    val updatedAt: Long = 0L
) {
    fun isFresh(now: Long, ttlMs: Long = 15 * 60_000L): Boolean = updatedAt > 0L && now - updatedAt <= ttlMs
}

interface ReasoningProvider {
    val id: String
    val isAvailable: Boolean

    suspend fun reason(request: AssistantRequest, context: AssistantContext): AssistantDecision
}

data class AssistantRequest(
    val originalText: String,
    val normalizedText: String,
    val language: AssistantLanguage,
    val diagnostics: DecisionDiagnostics? = null,
    val recentTurns: List<Pair<String, String>> = emptyList()
)

/** Default future-facing provider seam. AURA 0.4 uses this local provider only. */
class LocalReasoningProvider(
    private val engine: LocalAssistantEngine
) : ReasoningProvider {
    override val id: String = "local"
    override val isAvailable: Boolean = true

    override suspend fun reason(request: AssistantRequest, context: AssistantContext): AssistantDecision =
        engine.decide(request.originalText, context)
}

class LocalAssistantEngine(
    private val legacy: LocalIntentEngine = LocalIntentEngine(),
    private val registry: IntentRegistry = IntentRegistry(),
    private val knowledge: LocalKnowledgeEngine = LocalKnowledgeEngine(),
    private val composer: ResponseComposer = ResponseComposer(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private var dialogue = LocalDialogueState()

    fun decide(raw: String, context: AssistantContext = AssistantContext()): AssistantDecision {
        val startedAt = now()
        val input = TextNormalizer.normalize(raw)
        val freshDialogue = context.dialogue.takeIf { it.isFresh(now()) } ?: dialogue.takeIf { it.isFresh(now()) }
        contextualDecision(input, context, freshDialogue)?.let { return remember(it, startedAt) }
        knowledgeDecision(input, context)?.let { return remember(it, startedAt) }
        conversationDecision(input, context)?.let { return remember(it, startedAt) }
        azTransliterationDecision(input)?.let { return remember(it, startedAt) }

        val registryCandidates = registry.candidates(input)
        val match = registryCandidates.firstOrNull()
        val registryReply = match?.let { registryAction(it, input.detectedLanguage) }
        if (registryReply != null) return remember(decisionFromReply(registryReply, input, match, registryCandidates), startedAt)

        val corrected = CommonSpeechCorrections.apply(input.normalizedText)
        val legacyReply = legacy.understand(if (corrected == input.normalizedText) input.searchSafeText else corrected)
        return remember(decisionFromReply(legacyReply, input, null), startedAt)
    }

    fun dialogueState(): LocalDialogueState = dialogue

    private fun knowledgeDecision(input: NormalizedAssistantInput, context: AssistantContext): AssistantDecision? {
        val answer = knowledge.answer(input, context) ?: return null
        return AssistantDecision(
            intentId = "LOCAL_KNOWLEDGE",
            confidence = 0.98,
            entities = answer.entities,
            language = answer.language,
            reply = answer.text,
            action = null,
            memoryInsights = answer.memoryInsights,
            diagnostics = DecisionDiagnostics(
                input.originalText, input.normalizedText, answer.language,
                selectedIntent = "LOCAL_KNOWLEDGE", confidence = 0.98,
                entities = answer.entities, reason = "local-knowledge"
            )
        )
    }

    private fun conversationDecision(input: NormalizedAssistantInput, context: AssistantContext): AssistantDecision? {
        val text = input.normalizedText
        val language = input.detectedLanguage
        if (text.matches(Regex("(?:привет|здравствуй|доброе утро|добрый вечер|salam|sabahın xeyir|hello|hi|hey)[!. ]*"))) {
            return localConversation(
                composer.greeting(language, context.hourOfDay, context.carMode, context.isPlaying),
                input,
                memoryInsights = preferredFacts(text, language)
            )
        }
        if (containsAny(text, "как дела", "как ты", "necəsən", "how are you")) {
            return localConversation(localized(language, "У меня всё отлично — я рядом и готова помочь. А ты как?", "Məndə hər şey əladır, yanındayam. Bəs sən necəsən?", "I'm great and ready to help. How are you?"), input)
        }
        if (containsAny(text, "спасибо", "благодарю", "təşəkkür", "sağ ol", "thank you", "thanks")) {
            return localConversation(localized(language, "Всегда пожалуйста ♥", "Həmişə məmnuniyyətlə ♥", "Always happy to help ♥"), input)
        }
        if (containsAny(text, "расскажи шутку", "пошути", "zarafat et", "tell me a joke")) {
            return localConversation(localized(language, "Почему музыканты не спорят с метрономом? Он всегда прав по такту.", "Musiqiçilər niyə metronomla mübahisə etmir? O, həmişə ritmdə haqlıdır.", "Why don't musicians argue with a metronome? It always has perfect timing."), input)
        }
        val insights = preferredFacts(text, language)
        if (insights.any { it.category == "identity" && it.key == "name" }) {
            val name = insights.first { it.category == "identity" && it.key == "name" }.value
            return localConversation(localized(language, "Приятно познакомиться, $name.", "Tanış olduğuma şadam, $name.", "Nice to meet you, $name."), input, insights)
        }
        if (insights.isNotEmpty() && isPreferenceStatement(text)) {
            val reply = localized(language, "Запомнила твоё предпочтение.", "Seçiminizi yadda saxladım.", "I’ll remember that preference.")
            return localConversation(reply, input, insights)
        }
        if (containsAny(text, "хорошая группа", "классная группа", "люблю эту группу", "yaxşı qrupdur", "good band", "great band")) {
            return localConversation(localized(language, "Согласна, отличный исполнитель.", "Razıyam, yaxşı ifaçıdır.", "Agreed, they’re a great artist."), input)
        }
        return null
    }

    private fun isPreferenceStatement(text: String): Boolean = containsAny(
        text, "я люблю", "мне нравится", "не включай", "xoşuma gəlir", "xoşlayıram", "xoşum gəlmir", "i like", "i love", "don't play"
    )

    private fun preferredFacts(text: String, language: AssistantLanguage): List<MemoryInsight> = buildList {
        val name = listOf(
            Regex("(?i)меня зовут\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)моё имя\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)mənim adım\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)my name is\\s+([\\p{L}\\-]{2,30})")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }
        if (!name.isNullOrBlank()) add(MemoryInsight("identity", "name", name.replaceFirstChar { it.titlecase() }))

        val genre = Regex("(?i)(?:я люблю|мне нравится|i love|i like|xoşlayıram|xoşuma gəlir)\\s+([\\p{L}][\\p{L} -]{1,30})")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
        if (!genre.isNullOrBlank()) add(MemoryInsight("preference", "genre", genre))
        val artist = Regex("(?i)(?:мне нравится|i like|i love|xoşuma gəlir)\\s+([\\p{L}0-9][\\p{L}0-9 .'-]{1,40})")
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
        if (!artist.isNullOrBlank() && artist != genre) add(MemoryInsight("preference", "artist", artist))
        val disliked = Regex("(?i)(?:не включай|don't play|xoşum gəlmir)\\s+(.+)").find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!disliked.isNullOrBlank()) add(MemoryInsight("dislike", "artist", disliked))
        if (isPreferenceStatement(text)) add(MemoryInsight("language", "preferred", language.tag))
    }

    private fun localConversation(text: String, input: NormalizedAssistantInput, memoryInsights: List<MemoryInsight> = emptyList()) = AssistantDecision(
        intentId = "CONVERSATION",
        confidence = 0.97,
        entities = emptyList(),
        language = input.detectedLanguage,
        reply = text,
        action = null,
        memoryInsights = memoryInsights,
        diagnostics = DecisionDiagnostics(input.originalText, input.normalizedText, input.detectedLanguage, selectedIntent = "CONVERSATION", confidence = 0.97, reason = "local-conversation")
    )

    private fun containsAny(text: String, vararg values: String): Boolean = values.any {
        val normalized = TextNormalizer.normalizeForMatching(it)
        text == normalized || text.contains(normalized)
    }

    private fun contextualDecision(
        input: NormalizedAssistantInput,
        context: AssistantContext,
        previous: LocalDialogueState?
    ): AssistantDecision? {
        val text = input.normalizedText
        val results = context.lastSearchResults
        val ordinal = when {
            text.matches(Regex(".*\\b(vtoruyu|vtoroi|вторую|второй|ikinci)\\b.*")) -> 1
            text.matches(Regex(".*\\b(tretyu|treti|третью|третий|ucuncu)\\b.*")) -> 2
            else -> null
        }
        if (ordinal != null) {
            val selected = results.getOrNull(ordinal)
            if (selected != null) {
                return localDecision(
                    action = MusicIntent.Search(selected.title, selected.artist),
                    reply = localized(input.detectedLanguage, "Включаю второй результат.", "İkinci nəticəni qoşuram.", "Playing the second result."),
                    input = input,
                    entities = listOf(AssistantEntity(AssistantEntityType.ORDINAL, (ordinal + 1).toString())),
                    references = listOf("lastSearchResults[$ordinal]")
                )
            }
            return unresolved(input, "Уточни, из какого списка выбрать результат.", listOf("ordinal_without_results"))
        }
        if (text.contains("dobav") && (text.contains("eto") || text.contains("bunu")) ||
            text.contains("dobav eto") || text.contains("добавь это")) {
            return if (context.currentTrack != null) localDecision(
                MusicIntent.Like,
                localized(input.detectedLanguage, "Добавляю текущий трек в любимые.", "Bu mahnını sevimlilərə əlavə edirəm.", "Adding the current track to favorites."),
                input,
                listOf(AssistantEntity(AssistantEntityType.TRACK, context.currentTrack.title)),
                listOf("currentTrack")
            ) else unresolved(input, "Сначала включи или выбери трек.", listOf("current_track_missing"))
        }
        if (text.contains("novbeti") && (text.contains("bu") || text.contains("eto") || text.contains("этот"))) {
            val track = context.currentTrack
            return if (track != null) localDecision(
                MusicIntent.QueueTrack("${track.artist} ${track.title}", true),
                localized(input.detectedLanguage, "Поставлю текущий трек следующим.", "Bu mahnını növbəti qoyuram.", "Putting this track next."),
                input,
                listOf(AssistantEntity(AssistantEntityType.TRACK, track.title)),
                listOf("currentTrack")
            ) else unresolved(input, "Какой трек поставить следующим?", listOf("current_track_missing"))
        }
        if (text.contains("druguyu") || text.contains("другую") || text.contains("basqasini")) {
            val selected = results.getOrNull(1)
            return if (selected != null) localDecision(
                MusicIntent.Search(selected.title, selected.artist),
                localized(input.detectedLanguage, "Попробую другой результат.", "Başqa nəticəni yoxlayıram.", "Trying another result."),
                input,
                listOf(AssistantEntity(AssistantEntityType.TRACK, selected.title)),
                listOf("lastSearchResults[1]")
            ) else unresolved(input, "Другой результат появится после поиска.", listOf("alternative_without_results"))
        }
        if (text.contains("eще spokoj") || text.contains("еще спокой") || text.contains("daha sakit")) {
            val query = context.lastSearchQuery ?: context.currentTrack?.artist
            if (!query.isNullOrBlank()) return localDecision(
                MusicIntent.Search(query = query, artist = context.currentTrack?.artist, mood = Mood.CALM),
                localized(input.detectedLanguage, "Ищу более спокойный вариант.", "Daha sakit variant axtarıram.", "Finding a calmer option."),
                input,
                listOf(AssistantEntity(AssistantEntityType.MOOD, Mood.CALM.name)),
                listOf("lastSearchQuery", "mood:calm")
            )
        }
        if (text.contains("eще") || text.contains("daha") || text.contains("yenə")) {
            val artist = context.currentTrack?.artist ?: previous?.lastMentionedArtist
            if (!artist.isNullOrBlank()) return localDecision(
                MusicIntent.Search(artist, artist),
                localized(input.detectedLanguage, "Ищу ещё песни этого исполнителя.", "Bu ifaçının başqa mahnılarını axtarıram.", "Finding more songs by this artist."),
                input,
                listOf(AssistantEntity(AssistantEntityType.ARTIST, artist)),
                listOf("currentArtist")
            )
        }
        return null
    }

    private fun azTransliterationDecision(input: NormalizedAssistantInput): AssistantDecision? {
        val text = input.normalizedText
        if (text.startsWith("pleylist yarat")) {
            val name = text.removePrefix("pleylist yarat").trim().ifBlank {
                localized(input.detectedLanguage, "Новый плейлист", "Yeni pleylist", "New playlist")
            }.replaceFirstChar { it.titlecase() }
            return localDecision(
                MusicIntent.CreatePlaylist(name, includeQueue = false),
                localized(input.detectedLanguage, "Создаю плейлист $name.", "$name pleylistini yaradıram.", "Creating playlist $name."),
                input,
                listOf(AssistantEntity(AssistantEntityType.PLAYLIST, name))
            )
        }
        if (text.contains("novbeye elave et")) {
            return unresolved(
                input,
                localized(input.detectedLanguage, "Какой трек добавить в очередь?", "Növbəyə hansı mahnını əlavə edim?", "Which track should I add to the queue?"),
                listOf("queue_track_missing")
            )
        }
        if (text.contains("sesi artir")) return localDecision(MusicIntent.Louder, localized(input.detectedLanguage, "Делаю громче.", "Səsi artırıram.", "Turning it up."), input)
        if (text.contains("sesi azalt")) return localDecision(MusicIntent.Quieter, localized(input.detectedLanguage, "Делаю тише.", "Səsi azaldıram.", "Turning it down."), input)
        if (text.contains("novbeti mahni")) return localDecision(MusicIntent.Next, localized(input.detectedLanguage, "Следующий трек.", "Növbəti mahnı.", "Next track."), input)
        if (text.matches(Regex(".*\\b(qos|gosh|cal)\\b.*"))) {
            val artist = input.searchSafeText
                .replace(Regex("(?i)(mahnisi|mahnini|mahnı|mahni|qoş|qos|gosh|çal|cal|nəsə|nese)"), " ")
                .replace(Regex("(?i)-dan\\b"), " ")
                .trim()
            return if (artist.isBlank()) localDecision(MusicIntent.Play, localized(input.detectedLanguage, "Продолжаю музыку.", "Musiqini davam etdirirəm.", "Resuming music."), input)
            else localDecision(
                MusicIntent.Search(query = artist, artist = artist),
                localized(input.detectedLanguage, "Ищу музыку от $artist.", "$artist üçün mahnı axtarıram.", "Finding music by $artist."),
                input,
                listOf(AssistantEntity(AssistantEntityType.ARTIST, artist))
            )
        }
        return null
    }

    private fun registryAction(match: RegistryIntentMatch, language: AssistantLanguage): AssistantReply? = when (match.intentId) {
        "open_queue" -> AssistantReply(MusicIntent.OpenQueue, localized(language, "Открываю очередь.", "Növbəni açıram.", "Opening the queue."), language)
        "open_playlists" -> AssistantReply(MusicIntent.OpenPlaylists, localized(language, "Открываю плейлисты.", "Pleylistlərini açıram.", "Opening your playlists."), language)
        "open_radio" -> AssistantReply(MusicIntent.OpenRadio, localized(language, "Открываю радио по странам.", "Radionu açıram.", "Opening radio stations."), language)
        "clear_queue" -> AssistantReply(MusicIntent.ClearQueue, localized(language, "Очищаю очередь.", "Növbəni təmizləyirəm.", "Clearing the queue."), language)
        "next" -> AssistantReply(MusicIntent.Next, localized(language, "Следующий трек.", "Növbəti mahnı.", "Next track."), language)
        "previous" -> AssistantReply(MusicIntent.Previous, localized(language, "Возвращаю предыдущий трек.", "Əvvəlki mahnıya qayıdıram.", "Going back one track."), language)
        "louder" -> AssistantReply(MusicIntent.Louder, localized(language, "Делаю громче.", "Səsi artırıram.", "Turning it up."), language)
        "quieter" -> AssistantReply(MusicIntent.Quieter, localized(language, "Делаю тише.", "Səsi azaldıram.", "Turning it down."), language)
        "pause" -> AssistantReply(MusicIntent.Pause, localized(language, "Ставлю на паузу.", "Pauza edirəm.", "Pausing."), language)
        "play" -> AssistantReply(MusicIntent.Play, localized(language, "Продолжаю.", "Davam edirəm.", "Resuming."), language)
        "mute" -> AssistantReply(MusicIntent.Mute, localized(language, "Выключаю звук.", "Səsi söndürürəm.", "Muting."), language)
        "unmute" -> AssistantReply(MusicIntent.Unmute, localized(language, "Включаю звук.", "Səsi açıram.", "Unmuting."), language)
        "like" -> AssistantReply(MusicIntent.Like, localized(language, "Добавляю в любимые.", "Sevimlilərə əlavə edirəm.", "Adding to favorites."), language)
        "unlike" -> AssistantReply(MusicIntent.Unlike, localized(language, "Убираю из любимых.", "Sevimlilərdən silirəm.", "Removing from favorites."), language)
        "repeat" -> AssistantReply(MusicIntent.Repeat, localized(language, "Переключаю повтор.", "Təkrar rejimini dəyişirəm.", "Changing repeat mode."), language)
        "shuffle" -> AssistantReply(MusicIntent.Shuffle, localized(language, "Перемешиваю очередь.", "Növbəni qarışdırıram.", "Shuffling the queue."), language)
        "similar" -> AssistantReply(MusicIntent.Similar, localized(language, "Подбираю похожее.", "Oxşar musiqi seçirəm.", "Finding similar music."), language)
        "my_mix" -> AssistantReply(MusicIntent.MyMix, localized(language, "Собираю твой микс.", "Sənin miksini hazırlayıram.", "Building your mix."), language)
        "continue_listening" -> AssistantReply(MusicIntent.ContinueListening, localized(language, "Продолжаю прослушивание.", "Musiqini davam etdirirəm.", "Continuing listening."), language)
        "car_mode" -> AssistantReply(MusicIntent.CarMode, localized(language, "Включаю автомобильный режим.", "Avtomobil rejimini açıram.", "Turning on car mode."), language)
        else -> null
    }

    private fun decisionFromReply(
        reply: AssistantReply,
        input: NormalizedAssistantInput,
        match: RegistryIntentMatch?,
        candidates: List<RegistryIntentMatch> = emptyList()
    ): AssistantDecision {
        val needsReasoning = reply.intent == MusicIntent.Unknown && reply.route == AssistantRoute.LOCAL_CONVERSATION
        val entities = entitiesFor(reply.intent)
        val confidence = when {
            needsReasoning -> 0.0
            match != null -> match.confidence
            reply.route == AssistantRoute.NEEDS_REASONING -> 0.0
            reply.intent is MusicIntent.Search -> 0.88
            else -> 0.96
        }
        return AssistantDecision(
            intentId = reply.intent::class.simpleName ?: reply.intent.toString(),
            confidence = confidence,
            entities = entities,
            language = input.detectedLanguage,
            reply = reply.text,
            action = reply.intent.takeIf { it != MusicIntent.Unknown },
            memoryInsights = reply.memoryInsights,
            diagnostics = DecisionDiagnostics(
                originalText = input.originalText,
                normalizedText = input.normalizedText,
                language = input.detectedLanguage,
                topIntents = candidates.map { "${it.intentId} ${"%.2f".format(it.confidence)}" },
                selectedIntent = match?.intentId,
                confidence = confidence,
                entities = entities,
                reason = when {
                    needsReasoning -> "local-unresolved"
                    reply.route == AssistantRoute.LOCAL_CONVERSATION -> "local-conversation"
                    match != null -> match.reason
                    else -> "legacy-compatibility-parser"
                }
            )
        )
    }

    private fun localDecision(
        action: MusicIntent,
        reply: String,
        input: NormalizedAssistantInput,
        entities: List<AssistantEntity> = entitiesFor(action),
        references: List<String> = emptyList()
    ) = AssistantDecision(
        intentId = action::class.simpleName ?: action.toString(),
        confidence = 0.95,
        entities = entities,
        language = input.detectedLanguage,
        reply = reply,
        action = action,
        diagnostics = DecisionDiagnostics(
            input.originalText, input.normalizedText, input.detectedLanguage,
            selectedIntent = action::class.simpleName,
            confidence = 0.95,
            entities = entities,
            contextReferences = references,
            reason = "local-context-rule"
        )
    )

    private fun unresolved(input: NormalizedAssistantInput, reply: String, references: List<String>) = AssistantDecision(
        intentId = "UNRESOLVED",
        confidence = 0.0,
        entities = emptyList(),
        language = input.detectedLanguage,
        reply = reply,
        action = null,
        needsClarification = true,
        clarification = reply,
        diagnostics = DecisionDiagnostics(input.originalText, input.normalizedText, input.detectedLanguage, contextReferences = references, reason = "local-unresolved")
    )

    private fun remember(decision: AssistantDecision, startedAt: Long): AssistantDecision {
        val updated = decision.copy(diagnostics = decision.diagnostics.copy(processingTimeMs = (now() - startedAt).coerceAtLeast(0L)))
        dialogue = LocalDialogueState(
            lastIntent = updated.intentId,
            lastAction = updated.action?.let { it::class.simpleName },
            lastEntities = updated.entities,
            lastMentionedTrack = updated.entities.firstOrNull { it.type == AssistantEntityType.TRACK }?.let {
                AssistantTrackContext("entity", it.value, "")
            },
            lastMentionedArtist = updated.entities.firstOrNull { it.type == AssistantEntityType.ARTIST }?.value,
            currentTopic = updated.action?.let { it::class.simpleName },
            updatedAt = now()
        )
        return updated
    }

    private fun entitiesFor(intent: MusicIntent): List<AssistantEntity> = when (intent) {
        is MusicIntent.Search -> buildList {
            intent.artist?.let { add(AssistantEntity(AssistantEntityType.ARTIST, it)) }
            intent.mood?.let { add(AssistantEntity(AssistantEntityType.MOOD, it.name)) }
            intent.decade?.let { add(AssistantEntity(AssistantEntityType.DECADE, it.toString())) }
            if (intent.query.isNotBlank()) add(AssistantEntity(AssistantEntityType.TRACK, intent.query))
        }
        is MusicIntent.PlayPlaylist -> listOf(AssistantEntity(AssistantEntityType.PLAYLIST, intent.name))
        is MusicIntent.CreatePlaylist -> listOf(AssistantEntity(AssistantEntityType.PLAYLIST, intent.name))
        else -> emptyList()
    }

    private fun localized(language: AssistantLanguage, ru: String, az: String, en: String) = when (language) {
        AssistantLanguage.RUSSIAN -> ru
        AssistantLanguage.AZERBAIJANI -> az
        AssistantLanguage.ENGLISH -> en
    }
}

private object CommonSpeechCorrections {
    private val replacements = mapOf(
        "пастав" to "поставь", "пастаф" to "поставь", "паставь" to "поставь",
        "вклычи" to "включи", "включ" to "включи", "следущую" to "следующую",
        "следюущую" to "следующую", "mahni" to "mahnı", "mahnini" to "mahnını",
        "gosh" to "qoş", "qos" to "qoş", "nobeti" to "növbəti",
        "novbeti" to "növbəti", "sesi" to "səsi", "artir" to "artır", "azalt" to "azalt"
    )

    fun apply(text: String): String = text.split(' ').joinToString(" ") { replacements[it] ?: it }
}
