package com.yk.photocritic.core.net

import okhttp3.HttpUrl

/** The provider hosts PhotoCritic may call (design §4, decision 23). */
object Allowlist {
    val PROVIDER_HOSTS: Set<String> =
        setOf(
            "api.openai.com",
            "api.anthropic.com",
            "generativelanguage.googleapis.com",
        )

    /** Base URLs as Remote Config must give them (`providers.<p>.baseUrl`). */
    val BASE_URLS: Map<String, String> =
        mapOf(
            "openai" to "https://api.openai.com",
            "anthropic" to "https://api.anthropic.com",
            "gemini" to "https://generativelanguage.googleapis.com",
        )
}

/** Which hosts a [ProviderHttp] may reach. Tests use a local policy for a mock server. */
data class HostPolicy(
    val hosts: Set<String>,
    val requireHttps: Boolean,
) {
    fun allows(url: HttpUrl): Boolean = url.host in hosts && (!requireHttps || url.isHttps)

    companion object {
        val PRODUCTION = HostPolicy(Allowlist.PROVIDER_HOSTS, requireHttps = true)
    }
}
