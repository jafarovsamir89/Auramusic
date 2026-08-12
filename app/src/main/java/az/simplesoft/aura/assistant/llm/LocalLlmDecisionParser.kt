package az.simplesoft.aura.assistant.llm

import az.simplesoft.aura.assistant.Mood
import org.json.JSONObject

/** Strict parser: malformed or unknown model output is never executable. */
class LocalLlmDecisionParser {
    fun parse(raw: String): LocalLlmDecision {
        val json = runCatching {
            JSONObject(raw.toJsonCandidate())
        }.getOrNull() ?: return LocalLlmDecision.Unresolved
        if (!keysAre(json, setOf("type", "action", "parameters", "reply", "question"))) {
            return LocalLlmDecision.Unresolved
        }
        return when (json.optString("type")) {
            "action" -> parseAction(json)
            "conversation" -> json.optString("reply").boundedReply()?.let(LocalLlmDecision::Conversation)
                ?: LocalLlmDecision.Unresolved
            "clarification" -> json.optString("question").boundedReply()?.let(LocalLlmDecision::Clarification)
                ?: LocalLlmDecision.Unresolved
            "unresolved" -> LocalLlmDecision.Unresolved
            else -> LocalLlmDecision.Unresolved
        }
    }

    private fun parseAction(json: JSONObject): LocalLlmDecision {
        val action = json.optString("action").takeIf { it.isNotBlank() } ?: return LocalLlmDecision.Unresolved
        val params = json.optJSONObject("parameters") ?: JSONObject()
        val allowedParameters = when (action) {
            "SEARCH_MUSIC" -> setOf("query", "artist", "mood")
            "QUEUE_NEXT", "QUEUE_ADD" -> setOf("query")
            "PLAY_PLAYLIST" -> setOf("name", "shuffled")
            "CREATE_PLAYLIST" -> setOf("name", "includeQueue")
            else -> emptySet()
        }
        if (!keysAre(params, allowedParameters)) return LocalLlmDecision.Unresolved
        val parsed = when (action) {
            "PLAY" -> LocalLlmAction.Play
            "PAUSE" -> LocalLlmAction.Pause
            "NEXT" -> LocalLlmAction.Next
            "PREVIOUS" -> LocalLlmAction.Previous
            "SEARCH_MUSIC" -> search(params) ?: return LocalLlmDecision.Unresolved
            "PLAY_SIMILAR" -> LocalLlmAction.PlaySimilar
            "MY_MIX" -> LocalLlmAction.MyMix
            "LIKE" -> LocalLlmAction.Like
            "UNLIKE" -> LocalLlmAction.Unlike
            "QUEUE_NEXT" -> params.required("query")?.let(LocalLlmAction::QueueNext) ?: return LocalLlmDecision.Unresolved
            "QUEUE_ADD" -> params.required("query")?.let(LocalLlmAction::QueueAdd) ?: return LocalLlmDecision.Unresolved
            "OPEN_QUEUE" -> LocalLlmAction.OpenQueue
            "PLAY_PLAYLIST" -> params.required("name")?.let {
                LocalLlmAction.PlayPlaylist(it, params.optBoolean("shuffled", false))
            } ?: return LocalLlmDecision.Unresolved
            "CREATE_PLAYLIST" -> params.required("name")?.let {
                LocalLlmAction.CreatePlaylist(it, params.optBoolean("includeQueue", false))
            } ?: return LocalLlmDecision.Unresolved
            "VOLUME_UP" -> LocalLlmAction.VolumeUp
            "VOLUME_DOWN" -> LocalLlmAction.VolumeDown
            "REPEAT" -> LocalLlmAction.Repeat
            "SHUFFLE" -> LocalLlmAction.Shuffle
            "NOW_PLAYING" -> LocalLlmAction.NowPlaying
            else -> return LocalLlmDecision.Unresolved
        }
        return LocalLlmDecision.Action(parsed, json.optString("reply").boundedReply())
    }

    private fun search(params: JSONObject): LocalLlmAction.SearchMusic? {
        val query = params.optString("query").trim().takeIf { it.isNotBlank() }
        val artist = params.optString("artist").trim().takeIf { it.isNotBlank() }
        if (query == null && artist == null) return null
        val mood = params.optString("mood").uppercase().takeIf { it.isNotBlank() }
            ?.let { value -> runCatching { Mood.valueOf(value) }.getOrNull() }
        return LocalLlmAction.SearchMusic(query ?: artist.orEmpty(), artist, mood)
    }

    private fun keysAre(json: JSONObject, allowed: Set<String>): Boolean {
        val iterator = json.keys()
        while (iterator.hasNext()) if (iterator.next() !in allowed) return false
        return true
    }

    private fun JSONObject.required(key: String): String? = optString(key).trim()
        .takeIf { it.isNotBlank() && it.length <= 240 }

    private fun String?.boundedReply(): String? = this?.trim()?.takeIf { it.isNotBlank() }?.take(500)
}

private fun String.toJsonCandidate(): String {
    val withoutThinking = replace(Regex("(?s)<think>.*?</think>"), "")
        .replace("```json", "", ignoreCase = true)
        .replace("```", "")
        .trim()
    val start = withoutThinking.indexOf('{')
    val end = withoutThinking.lastIndexOf('}')
    return if (start >= 0 && end >= start) withoutThinking.substring(start, end + 1) else withoutThinking
}
