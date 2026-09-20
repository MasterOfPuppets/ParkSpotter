package com.masterofpuppets.parkspotter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    showText: Boolean = false,
    messages: List<String> = emptyList(),
    messageIntervalMillis: Long = 2_500L,
) {
    require(messageIntervalMillis > 0)
    var messageIndex by remember(messages) { mutableIntStateOf(0) }

    LaunchedEffect(showText, messages, messageIntervalMillis) {
        if (showText && messages.size > 1) {
            while (true) {
                delay(messageIntervalMillis)
                messageIndex = (messageIndex + 1) % messages.size
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        if (showText && messages.isNotEmpty()) {
            Text(
                text = messages[messageIndex.coerceIn(messages.indices)],
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
