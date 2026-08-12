package az.simplesoft.aura.assistant.gemini

import org.json.JSONArray
import org.json.JSONObject

object GeminiToolRegistry {
    private fun declaration(name: String, description: String, properties: JSONObject = JSONObject(), required: List<String> = emptyList()): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                if (required.isNotEmpty()) put("required", JSONArray(required))
            })
        }

    private fun string(description: String) = JSONObject().put("type", "STRING").put("description", description)
    private fun integer(description: String) = JSONObject().put("type", "INTEGER").put("description", description)
    private fun boolean(description: String) = JSONObject().put("type", "BOOLEAN").put("description", description)

    fun declarations(): JSONArray {
        val query = JSONObject().apply {
            put("query", string("Song, artist, genre, or free-form music request"))
            put("artist", string("Optional artist name"))
            put("mood", string("Optional mood such as calm, focus, drive, or energy"))
        }
        return JSONArray().apply {
            put(declaration("search_music", "Search AURA's music catalog. Do not claim a result before the tool response.", query, listOf("query")))
            put(declaration("play_track", "Play one selected track from the last search results.", JSONObject().put("index", integer("Zero-based result index")), listOf("index")))
            put(declaration("play_artist", "Find and play music by an artist.", JSONObject().put("artist", string("Artist name")), listOf("artist")))
            put(declaration("play_playlist", "Play a saved AURA playlist.", JSONObject().put("name", string("Playlist name")), listOf("name")))
            put(declaration("next_track", "Skip to the next track."))
            put(declaration("previous_track", "Return to the previous track."))
            put(declaration("pause_music", "Pause playback."))
            put(declaration("resume_music", "Resume playback."))
            put(declaration("volume_up", "Raise music volume."))
            put(declaration("volume_down", "Lower music volume."))
            put(declaration("set_volume", "Set music volume percentage from 0 to 100.", JSONObject().put("percent", integer("Volume percent, 0..100")), listOf("percent")))
            put(declaration("like_current_track", "Like the currently playing track."))
            put(declaration("unlike_current_track", "Remove the like from the currently playing track."))
            put(declaration("add_current_to_queue", "Add the current track to the end of the queue."))
            put(declaration("play_next", "Add a requested track to play next.", query, listOf("query")))
            put(declaration("get_now_playing", "Return the current track and playback state."))
            put(declaration("get_queue", "Return a compact queue summary."))
            put(declaration("get_recent_history", "Return recent listening history."))
            put(declaration("find_similar_music", "Find music similar to the current or named track.", JSONObject().put("mood", string("Optional mood adjustment"))))
            put(declaration("play_my_mix", "Play AURA's personal mix."))
            put(declaration("shuffle", "Toggle shuffle playback."))
            put(declaration("set_repeat", "Set repeat mode.", JSONObject().put("mode", string("One of off, one, all")), listOf("mode")))
            put(declaration("open_queue", "Open the queue screen."))
            put(declaration("open_playlists", "Open saved playlists."))
        }
    }
}

data class GeminiToolResult(val status: String, val message: String, val data: JSONObject = JSONObject())

fun interface GeminiToolExecutor {
    suspend fun execute(name: String, args: JSONObject): GeminiToolResult
}
