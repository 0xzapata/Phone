package org.fossify.phone.agent

import android.content.Context
import android.telecom.Call
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.extensions.getCallDuration
import org.fossify.phone.extensions.isOutgoing

const val CALL_STARTED = "call_started"
const val CALL_ENDED = "call_ended"

fun isMissed(kind: String, outgoing: Boolean, connectTimeMillis: Long) =
    kind == CALL_ENDED && !outgoing && connectTimeMillis == 0L

data class AgentEvent(
    val kind: String,
    val number: String,
    val displayName: String,
    val direction: String,
    val durationSeconds: Int,
    val missed: Boolean,
    val timestampMs: Long
) {
    fun toData() = workDataOf(
        "kind" to kind, "number" to number, "displayName" to displayName, "direction" to direction,
        "durationSeconds" to durationSeconds, "missed" to missed, "timestampMs" to timestampMs
    )

    companion object {
        fun from(data: Data) = AgentEvent(
            kind = data.getString("kind").orEmpty(),
            number = data.getString("number").orEmpty(),
            displayName = data.getString("displayName").orEmpty(),
            direction = data.getString("direction").orEmpty(),
            durationSeconds = data.getInt("durationSeconds", 0),
            missed = data.getBoolean("missed", false),
            timestampMs = data.getLong("timestampMs", 0L)
        )
    }
}

class AgentWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()

    companion object {
        fun enqueue(context: Context, name: String, event: AgentEvent) {
            val request = OneTimeWorkRequestBuilder<AgentWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(event.toData())
                .build()
            // Wait until the request is persisted: the in-call service process can die after the call ends.
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get()
        }

        fun enqueueCall(context: Context, call: Call, kind: String) {
            val details = call.details
            val number = details.handle?.schemeSpecificPart?.normalizePhoneNumber() ?: return
            val outgoing = call.isOutgoing()
            val now = System.currentTimeMillis()
            val event = AgentEvent(
                kind = kind,
                number = number,
                displayName = details.callerDisplayName.orEmpty(),
                direction = if (outgoing) "outgoing" else "incoming",
                durationSeconds = if (kind == CALL_ENDED) call.getCallDuration() else 0,
                missed = isMissed(kind, outgoing, details.connectTimeMillis),
                timestampMs = now
            )
            val name = "agent-$kind-${System.identityHashCode(call)}-$now"
            ensureBackgroundThread {
                val config = AgentConfig(context)
                if (config.enabled && number in config.allowlist) enqueue(context, name, event)
            }
        }
    }
}
