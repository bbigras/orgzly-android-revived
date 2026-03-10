package com.orgzly.android.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Listens for task count responses from the phone app.
 * When a response arrives, it triggers a complication update so fresh data is shown.
 */
class WearDataListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearConstants.PATH_TASK_COUNTS_RESPONSE) {
            // Trigger complication update with fresh data
            TaskComplicationService.requestUpdate(this)
        }
    }
}
