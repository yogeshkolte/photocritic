package com.yk.photocritic.platform.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yk.photocritic.core.providers.shared.Redact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** The Analytics and Crashlytics calls [Telemetry] makes, so tests can replace Firebase. */
interface TelemetryBackend {
    fun setCollection(on: Boolean)

    /** Null when the ID cannot be read. */
    suspend fun appInstanceId(): String?

    /** Null clears the user ID. */
    fun setUserId(id: String?)

    fun logEvent(
        name: String,
        params: Bundle,
    )
}

/**
 * Analytics and Crashlytics behind the "Share usage and crash data" switch
 * (§11, file 33). Collection is off in the manifest; [setSharing] turns it
 * on or off and sets or clears the instance-ID user ID. [log] sends only
 * the [PcEvent] list, drops everything while sharing is off, and redacts
 * every string value.
 */
class Telemetry(
    private val backend: TelemetryBackend,
) {
    @Volatile
    var sharing: Boolean = false
        private set

    suspend fun setSharing(on: Boolean) {
        sharing = on
        backend.setCollection(on)
        backend.setUserId(if (on) backend.appInstanceId() else null)
    }

    /**
     * The Analytics app instance ID, for reports and the Settings support
     * ID. Phase 0 found the SDK returns it with collection off too; callers
     * decide whether to use it while sharing is off.
     */
    suspend fun instanceIdOrNull(): String? = backend.appInstanceId()

    /** Drops the event while sharing is off. A missing, extra or mistyped parameter is a bug and throws. */
    fun log(
        event: PcEvent,
        params: Map<String, Any?>,
    ) {
        require(params.keys == event.params.keys) { "${event.wire}: parameters ${params.keys} != ${event.params.keys}" }
        if (!sharing) return
        val bundle = Bundle()
        for ((name, type) in event.params) {
            val value = params[name] ?: continue
            when (type) {
                ParamType.STRING -> bundle.putString(name, Redact.text(value.toString()).take(MAX_STRING_PARAM))
                ParamType.INTEGER -> bundle.putLong(name, (value as? Number)?.toLong() ?: error("${event.wire}.$name is not a number"))
            }
        }
        backend.logEvent(event.wire, bundle)
    }

    companion object {
        /** Analytics' limit for a string parameter value. */
        const val MAX_STRING_PARAM = 100
    }
}

class FirebaseTelemetryBackend(
    context: Context,
) : TelemetryBackend {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun setCollection(on: Boolean) {
        analytics.setAnalyticsCollectionEnabled(on)
        crashlytics.isCrashlyticsCollectionEnabled = on
    }

    @Suppress("TooGenericExceptionCaught") // The Task fails with any exception type.
    override suspend fun appInstanceId(): String? =
        try {
            analytics.appInstanceId.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    override fun setUserId(id: String?) {
        analytics.setUserId(id)
        crashlytics.setUserId(id.orEmpty())
    }

    override fun logEvent(
        name: String,
        params: Bundle,
    ) = analytics.logEvent(name, params)
}

/**
 * Passes uncaught exceptions to the previous handler (Crashlytics) with
 * every message redacted, keeping the class names and stack traces (file 33).
 */
class RedactingExceptionHandler(
    private val next: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        error: Throwable,
    ) {
        next?.uncaughtException(thread, redacted(error))
    }

    /** A copy of the error chain whose messages pass through [Redact]. */
    class RedactedThrowable(
        original: Throwable,
        cause: Throwable?,
    ) : RuntimeException("${original.javaClass.name}: ${original.message?.let { Redact.text(it) }.orEmpty()}", cause) {
        init {
            stackTrace = original.stackTrace
        }
    }

    companion object {
        private const val MAX_DEPTH = 8

        fun redacted(
            error: Throwable,
            depth: Int = 0,
        ): Throwable = RedactedThrowable(error, error.cause?.takeIf { depth < MAX_DEPTH }?.let { redacted(it, depth + 1) })

        /** Installs the handler in front of the current default one. */
        fun install() {
            Thread.setDefaultUncaughtExceptionHandler(RedactingExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()))
        }
    }
}
