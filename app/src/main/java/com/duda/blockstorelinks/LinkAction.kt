package com.duda.blockstorelinks

enum class LinkAction {
    ALLOW,
    ASK,
    DENY;

    companion object {
        fun fromStored(value: String): LinkAction {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALLOW
        }
    }
}
