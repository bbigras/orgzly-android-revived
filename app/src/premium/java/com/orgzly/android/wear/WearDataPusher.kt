package com.orgzly.android.wear

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WearDataPusher {
    fun notifyDataSetChanged(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            PhoneWearService.pushTaskCounts(context)
        }
    }
}
