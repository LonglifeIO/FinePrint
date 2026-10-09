package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.longlifeio.fineprint.explain.ASK_FOR_REVIEW
import com.longlifeio.fineprint.explain.OPEN_GITHUB
import com.longlifeio.fineprint.explain.REVIEW_DISCLOSURE

/**
 * "Ask FinePrint to review this app": it says first what will happen ([REVIEW_DISCLOSURE]) and only on Open GitHub
 * calls [onOpen], the browser. With [onOpen] null (the preview), Open GitHub is off and [unavailable] says why.
 */
@Composable
fun AskForReview(onOpen: (() -> Unit)?, unavailable: String?) {
    var asking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { asking = true }, modifier = Modifier.heightIn(min = TOUCH)) { Text(ASK_FOR_REVIEW) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(ASK_FOR_REVIEW) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(REVIEW_DISCLOSURE)
                    unavailable?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = { asking = false; onOpen?.invoke() }, enabled = onOpen != null, modifier = Modifier.heightIn(min = TOUCH)) { Text(OPEN_GITHUB) }
            },
            dismissButton = { TextButton(onClick = { asking = false }, modifier = Modifier.heightIn(min = TOUCH)) { Text("Cancel") } },
        )
    }
}
