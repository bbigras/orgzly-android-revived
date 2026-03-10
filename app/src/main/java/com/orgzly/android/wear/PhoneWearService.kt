package com.orgzly.android.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles task count requests from the Wear OS companion app.
 * Runs the requested search query and sends back done/total counts.
 *
 * This service works via manifest-based WearableListenerService (same-package only)
 * AND via a programmatic listener registered in App.onCreate() (cross-package).
 */
class PhoneWearService : WearableListenerService() {

    @Inject
    lateinit var dataRepository: DataRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        App.appComponent.inject(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "WearableListenerService received message on path: ${messageEvent.path}")
        if (messageEvent.path == PATH_TASK_COUNTS_REQUEST) {
            scope.launch {
                handleTaskCountRequest(this@PhoneWearService, dataRepository, messageEvent)
            }
        }
    }

    companion object {
        private const val TAG = "PhoneWearService"

        const val PATH_TASK_COUNTS_REQUEST = "/orgzly/task_counts_request"
        const val PATH_TASK_COUNTS_RESPONSE = "/orgzly/task_counts_response"

        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_QUERY = "query"

        const val DEFAULT_SEARCH_QUERY = "ad.3"

        private var programmaticListener: MessageClient.OnMessageReceivedListener? = null

        /**
         * Register a programmatic MessageClient listener so we can receive
         * messages from Wear apps with a different package name.
         */
        fun registerProgrammaticListener(context: Context, dataRepository: DataRepository) {
            if (programmaticListener != null) return

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val appContext = context.applicationContext

            programmaticListener = MessageClient.OnMessageReceivedListener { messageEvent ->
                Log.d(TAG, "Programmatic listener received message on path: ${messageEvent.path}")
                if (messageEvent.path == PATH_TASK_COUNTS_REQUEST) {
                    scope.launch {
                        handleTaskCountRequest(appContext, dataRepository, messageEvent)
                    }
                }
            }

            Wearable.getMessageClient(context).addListener(programmaticListener!!)
            Log.d(TAG, "Registered programmatic MessageClient listener")
        }

        private fun handleTaskCountRequest(
            context: Context,
            dataRepository: DataRepository,
            messageEvent: MessageEvent
        ) {
            try {
                val json = String(messageEvent.data)
                Log.d(TAG, "Received request: $json")
                val request = Gson().fromJson(json, Map::class.java)
                val queryString = request[KEY_QUERY] as? String ?: DEFAULT_SEARCH_QUERY

                val parser = InternalQueryParser()
                val query = parser.parse(queryString)
                val notes = dataRepository.selectNotesFromQuery(query)

                val doneKeywords = AppPreferences.doneKeywordsSet(context)
                val total = notes.size
                val done = notes.count { note ->
                    note.note.state != null && doneKeywords.contains(note.note.state)
                }

                val response = Gson().toJson(mapOf(
                    KEY_DONE to done,
                    KEY_TOTAL to total
                ))

                Log.d(TAG, "Sending response: $done/$total for query '$queryString'")

                val messageClient = Wearable.getMessageClient(context)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    PATH_TASK_COUNTS_RESPONSE,
                    response.toByteArray()
                ).addOnSuccessListener {
                    Log.d(TAG, "Response sent successfully to ${messageEvent.sourceNodeId}")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send response", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle task count request", e)
            }
        }
    }
}
