package fuck.blockstorelinks.droidvendorssuck

enum class LinkOutcome {
    ALLOWED,
    DENIED,
    REDIRECTED,
    ASKED,
    ALLOWLIST_BLOCKED;

    companion object {
        fun fromStored(value: String): LinkOutcome {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALLOWED
        }
    }
}
