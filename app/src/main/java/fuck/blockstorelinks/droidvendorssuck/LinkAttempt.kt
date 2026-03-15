package fuck.blockstorelinks.droidvendorssuck

data class LinkAttempt(
    val url: String,
    val timestamp: Long,
    val outcome: LinkOutcome,
    val sourceHint: String? = null
)
