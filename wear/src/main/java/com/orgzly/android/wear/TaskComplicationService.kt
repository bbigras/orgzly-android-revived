package com.orgzly.android.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.orgzly.wear.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class TaskComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> buildShortText(3, 7)
            ComplicationType.RANGED_VALUE -> buildRangedValue(3, 7)
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val prefs = getSharedPreferences(WearConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val query = prefs.getString(WearConstants.PREF_SEARCH_QUERY, WearConstants.DEFAULT_SEARCH_QUERY)
            ?: WearConstants.DEFAULT_SEARCH_QUERY

        // Try to get fresh data from phone
        val result = withTimeoutOrNull(8000L) {
            requestTaskCountsFromPhone(query)
        }

        val done: Int
        val total: Int

        if (result != null) {
            done = result.first
            total = result.second
            // Cache the result
            prefs.edit()
                .putInt(WearConstants.PREF_CACHED_DONE, done)
                .putInt(WearConstants.PREF_CACHED_TOTAL, total)
                .putLong(WearConstants.PREF_LAST_UPDATE, System.currentTimeMillis())
                .apply()
        } else {
            // Use cached data
            done = prefs.getInt(WearConstants.PREF_CACHED_DONE, -1)
            total = prefs.getInt(WearConstants.PREF_CACHED_TOTAL, -1)
        }

        if (done < 0 || total < 0) {
            return buildNoData(request.complicationType)
        }

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> buildShortText(done, total)
            ComplicationType.RANGED_VALUE -> buildRangedValue(done, total)
            else -> null
        }
    }

    private suspend fun requestTaskCountsFromPhone(query: String): Pair<Int, Int>? {
        return try {
            val capabilityClient = Wearable.getCapabilityClient(this)
            val capabilityInfo = capabilityClient.getCapability(
                WearConstants.CAPABILITY_TASK_PROVIDER,
                com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE
            ).await()

            val nodes = capabilityInfo.nodes

            if (nodes.isEmpty()) {
                // Fallback to all connected nodes
                val allNodes = Wearable.getNodeClient(this).connectedNodes.await()
                if (allNodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes found")
                    return null
                }
                return sendRequestToNodes(allNodes.map { it.id to it.displayName }, query)
            }

            return sendRequestToNodes(nodes.map { it.id to it.displayName }, query)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request task counts from phone", e)
            null
        }
    }

    private suspend fun sendRequestToNodes(nodes: List<Pair<String, String>>, query: String): Pair<Int, Int>? {
        return try {
            val messageClient = Wearable.getMessageClient(this)
            val requestData = Gson().toJson(mapOf(WearConstants.KEY_QUERY to query)).toByteArray()

            // Register a temporary listener for the response
            val result = suspendCancellableCoroutine { cont ->
                val listener = object : MessageClient.OnMessageReceivedListener {
                    override fun onMessageReceived(event: MessageEvent) {
                        if (event.path == WearConstants.PATH_TASK_COUNTS_RESPONSE) {
                            messageClient.removeListener(this)
                            val json = String(event.data)
                            val map = Gson().fromJson(json, Map::class.java)
                            val done = (map[WearConstants.KEY_DONE] as? Double)?.toInt() ?: 0
                            val total = (map[WearConstants.KEY_TOTAL] as? Double)?.toInt() ?: 0
                            if (cont.isActive) {
                                cont.resume(Pair(done, total))
                            }
                        }
                    }
                }

                messageClient.addListener(listener)

                cont.invokeOnCancellation {
                    messageClient.removeListener(listener)
                }

                // Send request to all nodes
                for ((nodeId, _) in nodes) {
                    messageClient.sendMessage(
                        nodeId,
                        WearConstants.PATH_TASK_COUNTS_REQUEST,
                        requestData
                    ).addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send message to node $nodeId", e)
                    }
                }
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send request to nodes", e)
            null
        }
    }

    private fun createTapAction(): PendingIntent {
        val intent = Intent(this, ConfigActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShortText(done: Int, total: Int): ShortTextComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("$done/$total").build(),
            contentDescription = PlainComplicationText.Builder("$done of $total tasks done").build()
        )
            .setTapAction(createTapAction())
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication_icon)
                ).build()
            )
            .build()
    }

    private fun buildRangedValue(done: Int, total: Int): RangedValueComplicationData {
        return RangedValueComplicationData.Builder(
            value = done.toFloat(),
            min = 0f,
            max = total.toFloat().coerceAtLeast(1f),
            contentDescription = PlainComplicationText.Builder("$done of $total tasks done").build()
        )
            .setTapAction(createTapAction())
            .setText(PlainComplicationText.Builder("$done/$total").build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication_icon)
                ).build()
            )
            .build()
    }

    private fun buildNoData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("--").build(),
                contentDescription = PlainComplicationText.Builder("No data available").build()
            )
                .setTapAction(createTapAction())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication_icon)
                    ).build()
                )
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 0f,
                min = 0f,
                max = 1f,
                contentDescription = PlainComplicationText.Builder("No data available").build()
            )
                .setTapAction(createTapAction())
                .setText(PlainComplicationText.Builder("--").build())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication_icon)
                    ).build()
                )
                .build()
            else -> null
        }
    }

    companion object {
        private const val TAG = "TaskComplication"

        fun requestUpdate(context: Context) {
            val requester = ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, TaskComplicationService::class.java)
            )
            requester.requestUpdateAll()
        }
    }
}
