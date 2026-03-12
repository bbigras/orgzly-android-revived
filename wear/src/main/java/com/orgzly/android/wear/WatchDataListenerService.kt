package com.orgzly.android.wear

import android.content.Context
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class WatchDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path == WearConstants.DATA_PATH_TASK_COUNTS) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val done = dataMap.getInt(WearConstants.KEY_DONE, -1)
                val total = dataMap.getInt(WearConstants.KEY_TOTAL, -1)

                if (done >= 0 && total >= 0) {
                    getSharedPreferences(WearConstants.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(WearConstants.PREF_CACHED_DONE, done)
                        .putInt(WearConstants.PREF_CACHED_TOTAL, total)
                        .putLong(WearConstants.PREF_LAST_UPDATE, System.currentTimeMillis())
                        .apply()

                    TaskComplicationService.requestUpdate(this)
                }
            }
        }
    }
}
