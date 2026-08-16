package az.simplesoft.aura.assistant

/**
 * Fast, deterministic dialogue layer. Its data set can grow without changing
 * callers: the UI only sees an AssistantReply.
 */
class LocalCompanionEngine {
    fun respond(
        input: String,
        context: AuraAiContext,
        memory: AssistantMemorySnapshot,
        language: AssistantLanguage
    ): AssistantReply {
        val text = input.lowercase().trim()
        val rememberedName = memory.facts.firstOrNull { it.category == "identity" && it.key == "name" }?.value
        val reply = when {
            text.containsAny("груст", "тяжело", "плохо", "одинок", "sad", "lonely") ->
                phrase(language, "Я рядом. Хочешь спокойную музыку или немного поговорить?", "Yanındayam. Sakit musiqi istəyirsən, yoxsa bir az danışaq?", "I'm here. Would you like calm music or to talk for a moment?")
            text.containsAny("устал", "устала", "усталость", "tired", "yorğun") ->
                phrase(language, "Понимаю. Могу включить что-то мягкое и не отвлекать тебя.", "Başa düşürəm. Sakit bir şey qoşa bilərəm.", "I understand. I can put on something gentle and give you some space.")
            text.containsAny("спасибо", "благодар", "thanks", "thank you", "təşəkkür") ->
                phrase(language, "Всегда рядом.", "Həmişə yanındayam.", "Always here.")
            text.containsAny("как тебя зовут", "кто ты", "who are you", "sən kimsən") ->
                phrase(language, "Я AURA, твой офлайн-помощник для музыки и повседневных команд.", "Mən AURAyam, musiqi və gündəlik əmrlər üçün oflayn köməkçinəm.", "I'm AURA, your offline assistant for music and everyday commands.")
            text.containsAny("что ты помнишь", "что ты знаешь обо мне", "what do you remember", "nə xatırlayırsan") -> memoryReply(language, memory, rememberedName)
            text.endsWith("?") -> questionReply(language, context, rememberedName)
            else -> phrase(language, "Я здесь. Могу включить музыку, помочь с плеером или просто поговорить.", "Buradayam. Musiqi qoşa, pleyerə kömək edə və ya sadəcə danışa bilərəm.", "I'm here. I can play music, help with the player, or simply keep you company.")
        }
        return AssistantReply(MusicIntent.Unknown, reply, language, AssistantRoute.LOCAL_CONVERSATION)
    }

    fun extractMemory(input: String): List<MemoryInsight> {
        val name = Regex("(?iu)(?:меня зовут|моё имя|my name is|mənim adım)\\s+([\\p{L}-]{2,30})")
            .find(input)?.groupValues?.getOrNull(1)?.replaceFirstChar { it.titlecase() }
        return name?.let { listOf(MemoryInsight("identity", "name", it)) }.orEmpty()
    }

    private fun memoryReply(language: AssistantLanguage, memory: AssistantMemorySnapshot, name: String?): String = when {
        name != null -> phrase(language, "Я помню, что тебя зовут $name. Остальное сохраняю только когда ты сам этим делишься.", "Adının $name olduğunu xatırlayıram. Qalanını yalnız özün paylaşanda saxlayıram.", "I remember that your name is $name. I only keep what you choose to share.")
        memory.facts.isNotEmpty() -> phrase(language, "Я помню несколько вещей, которыми ты со мной поделился. Можешь спросить точнее.", "Mənimlə paylaşdığın bir neçə şeyi xatırlayıram. Daha dəqiq soruşa bilərsən.", "I remember a few things you shared with me. You can ask more specifically.")
        else -> phrase(language, "Пока у меня нет личных заметок о тебе. Можешь сказать: «меня зовут ...».", "Hələ sənin haqqında şəxsi qeyd yoxdur. «Mənim adım ...» deyə bilərsən.", "I don't have personal notes about you yet. You can say: 'my name is ...'.")
    }

    private fun questionReply(language: AssistantLanguage, context: AuraAiContext, name: String?): String = when {
        context.isPlaying -> phrase(language, "Я слушаю. Сейчас играет ${context.currentTrack ?: "трек"}. Чем помочь?", "Dinləyirəm. İndi ${context.currentTrack ?: "musiqi"} səslənir. Necə kömək edim?", "I'm listening. ${context.currentTrack ?: "a track"} is playing now. How can I help?")
        name != null -> phrase(language, "$name, я могу помочь с музыкой и настройками плеера. Что выберем?", "$name, musiqi və pleyer ayarlarında kömək edə bilərəm. Nə edək?", "$name, I can help with music and player settings. What shall we do?")
        else -> phrase(language, "Я могу помочь с музыкой, очередью, плейлистами и настройками плеера.", "Musiqi, növbə, pleylistlər və pleyer ayarlarında kömək edə bilərəm.", "I can help with music, the queue, playlists, and player settings.")
    }

    private fun phrase(language: AssistantLanguage, ru: String, az: String, en: String) = when (language) {
        AssistantLanguage.RUSSIAN -> ru
        AssistantLanguage.AZERBAIJANI -> az
        AssistantLanguage.ENGLISH -> en
    }

    private fun String.containsAny(vararg values: String) = values.any(::contains)
}
