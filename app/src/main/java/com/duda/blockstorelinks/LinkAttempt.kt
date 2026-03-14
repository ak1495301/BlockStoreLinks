package com.duda.blockstorelinks

data class LinkAttempt(
    val url: String,
    val timestamp: Long,
    val outcome: LinkOutcome
)
