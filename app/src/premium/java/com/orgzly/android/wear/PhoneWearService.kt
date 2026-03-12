package com.orgzly.android.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles task count requests from the Wear OS companion app.
 * Runs the requested search query and sends back done/total counts.
 */
class PhoneWearService : WearableListenerService() {

    private val dataRepository by lazy { App.appComponent.dataRepository() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_TASK_COUNTS_REQUEST) {
            scope.launch {
                handleTaskCountRequest(messageEvent)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path == DATA_PATH_WATCH_CONFIG) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val query = dataMap.getString(KEY_QUERY) ?: DEFAULT_SEARCH_QUERY

                // Store the watch query locally
                getSharedPreferences(PHONE_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_WATCH_QUERY, query)
                    .apply()

                // Immediately compute and push fresh counts
                scope.launch {
                    pushTaskCounts(this@PhoneWearService, query)
                }
            }
        }
    }

    private fun handleTaskCountRequest(messageEvent: MessageEvent) {
        try {
            val json = String(messageEvent.data)
            val request = Gson().fromJson(json, Map::class.java)
            val queryString = request[KEY_QUERY] as? String ?: DEFAULT_SEARCH_QUERY

            val parser = InternalQueryParser()
            val query = parser.parse(queryString)
            val notes = dataRepository.selectNotesFromQuery(query)

            val doneKeywords = AppPreferences.doneKeywordsSet(this)
            val total = notes.size
            val done = notes.count { note ->
                note.note.state != null && doneKeywords.contains(note.note.state)
            }

            val response = Gson().toJson(mapOf(
                KEY_DONE to done,
                KEY_TOTAL to total
            ))

            val messageClient = Wearable.getMessageClient(this)
            messageClient.sendMessage(
                messageEvent.sourceNodeId,
                PATH_TASK_COUNTS_RESPONSE,
                response.toByteArray()
            ).addOnFailureListener { e ->
                Log.e(TAG, "Failed to send response", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle task count request", e)
        }
    }

    companion object {
        private const val TAG = "PhoneWearService"

        const val PATH_TASK_COUNTS_REQUEST = "/orgzly/task_counts_request"
        const val PATH_TASK_COUNTS_RESPONSE = "/orgzly/task_counts_response"
        const val DATA_PATH_TASK_COUNTS = "/orgzly/task_counts"
        const val DATA_PATH_WATCH_CONFIG = "/orgzly/watch_config"
        const val DATA_KEY_TIMESTAMP = "timestamp"

        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_QUERY = "query"

        const val DEFAULT_SEARCH_QUERY = "s.ge.today s.le.today"

        const val PHONE_PREFS_NAME = "orgzly_wear_phone_prefs"
        const val PREF_WATCH_QUERY = "watch_query"

        fun pushTaskCounts(context: Context, query: String? = null) {
            try {
                val dataRepository = App.appComponent.dataRepository()
                val queryString = query
                    ?: context.getSharedPreferences(PHONE_PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_WATCH_QUERY, null)
                    ?: DEFAULT_SEARCH_QUERY

                val parser = InternalQueryParser()
                val parsed = parser.parse(queryString)
                val notes = dataRepository.selectNotesFromQuery(parsed)

                val doneKeywords = AppPreferences.doneKeywordsSet(context)
                val total = notes.size
                val done = notes.count { note ->
                    note.note.state != null && doneKeywords.contains(note.note.state)
                }

                val putRequest = PutDataMapRequest.create(DATA_PATH_TASK_COUNTS).apply {
                    dataMap.putInt(KEY_DONE, done)
                    dataMap.putInt(KEY_TOTAL, total)
                    dataMap.putLong(DATA_KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Wearable.getDataClient(context).putDataItem(putRequest)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to push task counts to watch", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute/push task counts", e)
            }
        }
    }
}
