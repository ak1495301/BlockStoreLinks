package com.duda.blockstorelinks

data class AppPolicy(
    val packageName: String,
    var attemptCount: Int,
    var linkAction: LinkAction,
    var redirectPackage: String?,
    var allowlistPatterns: List<String>
)
