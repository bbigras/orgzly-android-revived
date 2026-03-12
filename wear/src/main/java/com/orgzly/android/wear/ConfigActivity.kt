package com.orgzly.android.wear

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.orgzly.wear.R

class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(WearConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val currentQuery = prefs.getString(WearConstants.PREF_SEARCH_QUERY, WearConstants.DEFAULT_SEARCH_QUERY)
            ?: WearConstants.DEFAULT_SEARCH_QUERY

        setContent {
            WearConfigScreen(
                initialQuery = currentQuery,
                onSave = { query ->
                    prefs.edit()
                        .putString(WearConstants.PREF_SEARCH_QUERY, query)
                        .apply()

                    // Push query to DataClient so phone can listen for config changes
                    val putRequest = PutDataMapRequest.create(WearConstants.DATA_PATH_WATCH_CONFIG).apply {
                        dataMap.putString(WearConstants.KEY_QUERY, query)
                        dataMap.putLong(WearConstants.DATA_KEY_TIMESTAMP, System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()
                    Wearable.getDataClient(this@ConfigActivity).putDataItem(putRequest)

                    // Trigger complication update with new query
                    TaskComplicationService.requestUpdate(this)

                    setResult(Activity.RESULT_OK)
                    finish()
                }
            )
        }
    }
}

@Composable
fun WearConfigScreen(initialQuery: String, onSave: (String) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Search Query",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                        .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSave(query) },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
