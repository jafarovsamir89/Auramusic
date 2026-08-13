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
            put(declaration("play_mood_mix", "Build and play a mood-specific queue. Prefer this over a generic search when the user asks for music by mood. Lullaby means a real nursery/bedtime song, not generic chill or lo-fi.", JSONObject().put("mood", string("One of calm, drive, focus, energy, night, sad, happy, lullaby")), listOf("mood")))
            put(declaration("play_track", "Play one selected track from the last search results.", JSONObject().put("index", integer("Zero-based result index")), listOf("index")))
            put(declaration("play_artist", "Find and play music by an artist.", JSONObject().put("artist", string("Artist name")), listOf("artist")))
            put(declaration("play_playlist", "Play a saved AURA playlist.", JSONObject().put("name", string("Playlist name")), listOf("name")))
            put(declaration("next_track", "Skip to the next track."))
            put(declaration("previous_track", "Return to the previous track."))
            put(declaration("seek_relative", "Seek forward or backward by seconds.", JSONObject().put("seconds", integer("Positive or negative seconds")), listOf("seconds")))
            put(declaration("pause_music", "Pause playback."))
            put(declaration("resume_music", "Resume playback."))
            put(declaration("open_radio", "Open the radio catalog and immediately start the first playable station. Use for requests such as 'включи радио' or 'play radio'."))
            put(declaration("clear_queue", "Remove all tracks from the current queue."))
            put(declaration("remove_last_queue_track", "Remove the last track from the current queue."))
            put(declaration("set_sleep_timer", "Stop playback after the requested number of minutes.", JSONObject().put("minutes", integer("Minutes from 1 to 240")), listOf("minutes")))
            put(declaration("cancel_sleep_timer", "Cancel the active sleep timer."))
            put(declaration("stop_after_track", "Stop playback when the current track finishes."))
            put(declaration("volume_up", "Raise Android media/music volume and confirm the resulting percentage."))
            put(declaration("volume_down", "Lower Android media/music volume and confirm the resulting percentage."))
            put(declaration("set_volume", "Set Android media/music volume percentage from 0 to 100.", JSONObject().put("percent", integer("Volume percent, 0..100")), listOf("percent")))
            put(declaration("set_equalizer", "Apply a music equalizer preset: flat, bass, vocal, rock, or acoustic.", JSONObject().put("preset", string("One of flat, bass, vocal, rock, acoustic")), listOf("preset")))
            put(declaration("disable_equalizer", "Turn off the music equalizer."))
            put(declaration("mute_music", "Mute media playback without changing the voice session."))
            put(declaration("unmute_music", "Unmute media playback."))
            put(declaration("set_car_mode", "Turn AURA driving mode on or off.", JSONObject().put("enabled", boolean("Whether driving mode should be enabled")), listOf("enabled")))
            put(declaration("disable_voice_mode", "Stop AURA's voice session. Use for 'Аура, отключись', 'замолчи', 'останови голос', or 'выключи голосовой режим'; never use pause_music for these requests."))
            put(declaration("like_current_track", "Like the currently playing track."))
            put(declaration("unlike_current_track", "Remove the like from the currently playing track."))
            put(declaration("add_current_to_queue", "Add the current track to the end of the queue."))
            put(declaration("save_queue_as_playlist", "Save the current queue as a named playlist.", JSONObject().put("name", string("Playlist name")), listOf("name")))
            put(declaration("play_next", "Add a requested track to play next.", query, listOf("query")))
            put(declaration("get_now_playing", "Return the current track and playback state."))
            put(declaration("get_queue", "Return a compact queue summary."))
            put(declaration("get_recent_history", "Return recent listening history."))
            put(declaration("find_similar_music", "Find music similar to the current or named track.", JSONObject().put("mood", string("Optional mood adjustment"))))
            put(declaration("more_like_this", "Find and play more music with the same style as the current track."))
            put(declaration("reject_current_track", "Skip the current track and record that this recommendation was not wanted."))
            put(declaration("clear_memory", "Delete AURA's locally saved user preferences and conversation memory."))
            put(declaration("play_my_mix", "Build and play AURA's personal mix for requests such as 'поставь микс песен', 'включи микс', or 'мой микс'."))
            put(declaration("shuffle", "Toggle shuffle playback."))
            put(declaration("set_repeat", "Set repeat mode.", JSONObject().put("mode", string("One of off, one, all")), listOf("mode")))
            put(declaration("open_queue", "Open the queue screen."))
            put(declaration("open_playlists", "Open saved playlists."))
            put(declaration("open_history", "Open listening history."))
            put(declaration("set_auto_continue", "Enable or disable automatic queue continuation.", JSONObject().put("enabled", boolean("Whether auto continue is enabled")), listOf("enabled")))
        }
    }
}

data class GeminiToolResult(val status: String, val message: String, val data: JSONObject = JSONObject())

fun interface GeminiToolExecutor {
    suspend fun execute(name: String, args: JSONObject): GeminiToolResult
}
