package com.duda.blockstorelinks

data class AppPolicy(
    val packageName: String,
    var attemptCount: Int,
    var blocked: Boolean,
    var redirectPackage: String?,
    var allowlistPatterns: List<String>
)
