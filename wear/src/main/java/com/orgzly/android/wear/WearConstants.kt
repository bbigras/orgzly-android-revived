package com.orgzly.android.wear

object WearConstants {
    const val PATH_TASK_COUNTS_REQUEST = "/orgzly/task_counts_request"
    const val PATH_TASK_COUNTS_RESPONSE = "/orgzly/task_counts_response"

    const val DATA_PATH_TASK_COUNTS = "/orgzly/task_counts"
    const val DATA_PATH_WATCH_CONFIG = "/orgzly/watch_config"
    const val DATA_KEY_TIMESTAMP = "timestamp"

    const val PREFS_NAME = "orgzly_wear_prefs"
    const val PREF_SEARCH_QUERY = "search_query"
    const val PREF_CACHED_DONE = "cached_done"
    const val PREF_CACHED_TOTAL = "cached_total"
    const val PREF_LAST_UPDATE = "last_update"

    const val DEFAULT_SEARCH_QUERY = "s.ge.today s.le.today"

    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_QUERY = "query"

    const val CAPABILITY_TASK_PROVIDER = "orgzly_task_provider"
}
