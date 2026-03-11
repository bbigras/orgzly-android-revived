package com.orgzly.android.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
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

        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_QUERY = "query"

        const val DEFAULT_SEARCH_QUERY = "s.ge.today s.le.today"
    }
}
