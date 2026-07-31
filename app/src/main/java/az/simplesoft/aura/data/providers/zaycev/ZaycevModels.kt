package az.simplesoft.aura.data.providers.zaycev

data class ZaycevFileMeta(
    val trackId: String,
    val streamingToken: String
)

data class ZaycevPlaybackInfo(
    val url: String,
    val durationSeconds: Long? = null
)

data class ZaycevDiagnostics(
    val stage: String,
    val httpCode: Int? = null,
    val detail: String? = null
) {
    fun asMap(): Map<String, String> = buildMap {
        put("stage", stage)
        httpCode?.let { put("httpCode", it.toString()) }
        detail?.let { put("detail", it.take(240)) }
    }
}
