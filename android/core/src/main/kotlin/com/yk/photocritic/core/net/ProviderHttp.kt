package com.yk.photocritic.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import okhttp3.CookieJar
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A request a provider client asks [ProviderHttp] to send. The key travels only in [headers]. */
class ProviderRequest(
    val url: String,
    val headers: Map<String, String>,
    val jsonBody: String?,
    /** The response header that holds the provider's request ID, if it has one (§3a). */
    val requestIdHeader: String?,
)

/** What [ProviderHttp.exchange] emits: the response head once, then the body line by line. */
sealed interface HttpChunk {
    /** [errorBody] is set, and no lines follow, when [status] is not 2xx. A 3xx is never followed. */
    data class Head(
        val status: Int,
        val requestId: String?,
        val errorBody: String?,
    ) : HttpChunk

    data class Line(
        val text: String,
    ) : HttpChunk
}

/** Thrown before any data is sent when a request's host is not allowed. */
class HostNotAllowedException(
    host: String,
) : IOException("host not allowed: $host")

/**
 * The one HTTP client for provider calls (design §4). It refuses hosts outside
 * the allowlist, never follows redirects, never retries, applies the connect
 * and total timeouts, and keeps no cache or cookies. Cancelling the collecting
 * coroutine cancels the call (Stop).
 */
class ProviderHttp(
    policy: HostPolicy = HostPolicy.PRODUCTION,
    connectTimeoutMs: Long,
    requestTimeoutMs: Long,
) {
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(HostPolicyInterceptor(policy))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cache(null)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    fun exchange(request: ProviderRequest): Flow<HttpChunk> =
        flow {
            val call = client.newCall(request.toOkHttp())
            val cancelOnStop = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                runInterruptible { call.execute() }.use { response ->
                    val requestId = request.requestIdHeader?.let { response.header(it) }
                    if (!response.isSuccessful) {
                        emit(HttpChunk.Head(response.code, requestId, response.errorBody()))
                        return@use
                    }
                    emit(HttpChunk.Head(response.code, requestId, errorBody = null))
                    val source = response.body.source()
                    while (true) {
                        val line = runInterruptible { source.readUtf8Line() } ?: break
                        emit(HttpChunk.Line(line))
                    }
                }
            } finally {
                cancelOnStop.dispose()
            }
        }.flowOn(Dispatchers.IO)

    private fun ProviderRequest.toOkHttp(): Request {
        val builder = Request.Builder().url(url.toHttpUrl()).headers(headers.toHeaders())
        return if (jsonBody == null) {
            builder.get().build()
        } else {
            builder.post(jsonBody.toRequestBody(JSON)).build()
        }
    }

    private fun Response.errorBody(): String = peekBody(ERROR_BODY_LIMIT).string()

    private class HostPolicyInterceptor(
        private val policy: HostPolicy,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            if (!policy.allows(url)) throw HostNotAllowedException(url.host)
            return chain.proceed(chain.request())
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val ERROR_BODY_LIMIT = 64L * 1024
    }
}
