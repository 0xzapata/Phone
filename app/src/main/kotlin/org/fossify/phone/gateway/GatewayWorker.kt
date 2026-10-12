package org.fossify.phone.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.extensions.getCallDuration
import org.fossify.phone.extensions.isOutgoing
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

const val CALL_STARTED = "call_started"
const val CALL_ENDED = "call_ended"

private const val KEY_EVENT_JSON = "event_json"
private const val CHANNEL_ID = "gateway"
private const val BACKOFF_SECONDS = 30L
private const val MAX_RETRIES = 10
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val MAX_NUMBER_CHARS = 32
private const val MAX_NAME_CHARS = 120

fun isMissed(kind: String, outgoing: Boolean, connectTimeMillis: Long) =
    kind == CALL_ENDED && !outgoing && connectTimeMillis == 0L

data class GatewayEvent(
    val id: String,
    val kind: String,
    val occurredAtMs: Long,
    val number: String,
    val displayName: String,
    val direction: String = "",
    val durationSeconds: Int = 0,
    val missed: Boolean = false
)

fun eventJson(event: GatewayEvent): String = JSONObject()
    .put("id", event.id)
    .put("kind", event.kind)
    .put("occurred_at", Instant.ofEpochMilli(event.occurredAtMs).truncatedTo(ChronoUnit.SECONDS).toString())
    .put("number", event.number)
    .put("display_name", event.displayName)
    .put("direction", event.direction)
    .put("duration_s", event.durationSeconds)
    .put("missed", event.missed)
    .toString()

class GatewayWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val config = GatewayConfig(applicationContext)
        val code = try {
            post(config.gatewayUrl, config.deviceToken, inputData.getString(KEY_EVENT_JSON).orEmpty())
        } catch (_: IOException) {
            null
        }
        return when (code) {
            HTTP_CREATED, HTTP_OK -> Result.success()
            HTTP_UNAUTHORIZED, HTTP_BAD_REQUEST -> fail(code)
            else -> if (runAttemptCount >= MAX_RETRIES) fail(code) else Result.retry()
        }
    }

    private fun post(url: String, token: String, body: String): Int {
        val connection = URL(url.trimEnd('/') + "/v1/events").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    // The text holds the status code only, never the event content.
    private fun fail(code: Int?): Result {
        val context = applicationContext
        val title = context.getString(R.string.gateway)
        val text = context.getString(R.string.gateway_failed, code?.toString() ?: "no response")
        context.notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        context.notificationManager.notify(id.hashCode(), notification)
        return Result.failure()
    }

    companion object {
        // Blocks until WorkManager stores the request: the in-call service process can die after the call ends.
        fun enqueue(context: Context, event: GatewayEvent) {
            val request = OneTimeWorkRequestBuilder<GatewayWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_EVENT_JSON to eventJson(event)))
                .build()
            WorkManager.getInstance(context).enqueue(request).result.get()
        }

        fun enqueueCall(context: Context, call: Call, kind: String) {
            val details = call.details
            val outgoing = call.isOutgoing()
            val number = if (details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                details.handle?.schemeSpecificPart?.normalizePhoneNumber().orEmpty().take(MAX_NUMBER_CHARS)
            } else {
                ""
            }
            val event = GatewayEvent(
                id = UUID.randomUUID().toString(),
                kind = kind,
                occurredAtMs = System.currentTimeMillis(),
                number = number,
                displayName = details.callerDisplayName.orEmpty().take(MAX_NAME_CHARS),
                direction = if (outgoing) "outgoing" else "incoming",
                durationSeconds = if (kind == CALL_ENDED) call.getCallDuration() else 0,
                missed = isMissed(kind, outgoing, details.connectTimeMillis)
            )
            ensureBackgroundThread {
                val config = GatewayConfig(context)
                if (config.enabled && isAllowed(number, config.allowlist)) enqueue(context, event)
            }
        }
    }
}
