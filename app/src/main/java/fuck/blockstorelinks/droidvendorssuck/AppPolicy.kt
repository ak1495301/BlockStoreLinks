package fuck.blockstorelinks.droidvendorssuck

data class AppPolicy(
    val packageName: String,
    var attemptCount: Int,
    var linkAction: LinkAction,
    var redirectPackage: String?,
    var allowlistPatterns: List<String>,
    var redirectRules: List<String>
)
