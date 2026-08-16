package az.simplesoft.aura.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Representative speech/ASR corpus. Variants exercise case, punctuation and spacing normalization. */
class LocalAssistantCorpusTest {
    private val engine = LocalAssistantEngine()

    @Test
    fun russianCommandCorpusStaysActionable() {
        val base = listOf(
            "пауза", "продолжи", "следующая", "предыдущая", "громче", "тише", "очисти очередь",
            "открой очередь", "открой плейлисты", "открой радио", "добавь в любимые", "убери из любимых",
            "повтор", "перемешай", "что играет", "включи мой микс", "похожее", "режим машины",
            "продолжить прослушивание", "включи Numb", "найди музыку для дороги", "поставь спокойную музыку",
            "сыграй рок", "включи Linkin Park", "найди грустную песню", "поставь следующим Numb",
            "добавь Numb в очередь", "включи плейлист Дорога", "создай плейлист Вечер", "выключи автопродолжение"
        )
        val corpus = base.flatMap { phrase -> listOf(phrase, phrase.uppercase(), "$phrase!", "  $phrase  ", phrase.replace(" ", "  ")) }
        assertTrue(corpus.size >= 150)
        corpus.forEach { phrase ->
            val decision = engine.decide(phrase)
            assertNotNull("No decision for $phrase", decision)
            assertFalse("Search should not be absent for command: $phrase", decision.action == null && decision.isUnresolved)
        }
    }

    @Test
    fun azerbaijaniCommandCorpusRecognizesTransliterationAndNativeForms() {
        val base = listOf(
            "salam", "salam necəsən", "səsi artır", "səsi azalt", "mahni qos", "mahnı qoş",
            "mahnını qoş", "nobeti mahni", "növbəti mahnı", "növbəni göstər", "növbəni təmizlə",
            "əvvəlki mahnı", "radionu aç", "pleylistlərimi göstər", "mənim miksim", "musiqini davam etdir",
            "pauza", "davam et", "qarışdır", "təkrar et", "oxşar musiqi", "maşın rejimi",
            "xoşuma gəlir", "xoşum gəlmir", "Roya mahnisi qos", "Aygun Kazimova qos",
            "Linkin Park-dan Numb qoş", "sakit musiqi qoş", "növbəyə əlavə et", "pleylist yarat"
        )
        val corpus = base.flatMap { phrase -> listOf(phrase, phrase.uppercase(), "$phrase!", "  $phrase  ", phrase.replace(" ", "  ")) }
        assertTrue(corpus.size >= 150)
        corpus.forEach { phrase ->
            val decision = engine.decide(phrase)
            assertNotNull("No decision for $phrase", decision)
            if (phrase.lowercase() !in setOf("salam", "salam necəsən")) {
                assertFalse("Unexpected unresolved AZ phrase: $phrase", decision.isUnresolved)
            }
        }
    }

    @Test
    fun mixedAndNegativeCorpusNeverTurnsConversationIntoSearch() {
        val mixed = listOf(
            "Включи Roya mahni", "Salam, включи Numb", "поставь Linkin Park qoş", "play спокойную музыку",
            "növbəti track", "сделай volume up", "найди Aygun mahnisi", "open плейлисты", "включи my mix",
            "qoş mahni для дороги", "поставь sad песню", "add Numb to queue", "сделай тише please", "Roya qoş", "Numb çal"
        )
        mixed.forEach { phrase -> assertFalse("Mixed phrase became unresolved: $phrase", engine.decide(phrase).isUnresolved) }

        val negative = listOf(
            "Привет", "Как дела?", "Какой сегодня день?", "Я люблю рок", "Мне нравится Linkin Park",
            "Linkin Park хорошая группа", "Я сегодня устал", "Расскажи шутку", "Спасибо", "Кто ты?",
            "Что ты умеешь?", "Сколько времени?", "Salam necəsən", "Mən roka qulaq asıram", "What can you do?",
            "I am happy", "это просто мысль", "не ищи ничего", "почему небо синее", "какая погода"
        )
        negative.forEach { phrase ->
            val decision = engine.decide(phrase)
            assertTrue("Conversation became a music action: $phrase -> ${decision.action}", decision.action !is MusicIntent.Search)
        }
    }
}
