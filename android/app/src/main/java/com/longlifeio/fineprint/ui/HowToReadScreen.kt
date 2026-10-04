package com.longlifeio.fineprint.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.longlifeio.fineprint.R
import com.longlifeio.fineprint.explain.MethodBlock
import com.longlifeio.fineprint.explain.parseMethod

/** docs/METHOD.md, shipped as an asset and shown as written (MethodDocTest keeps the two identical). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToReadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val blocks = remember { parseMethod(context.assets.open("METHOD.md").bufferedReader().use { it.readText() }) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to read this") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(TOUCH)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().testTag("howto")) {
            items(blocks) { MethodBlockView(it) }
            item { LinkRow("Report an error", R.drawable.ic_open_in_new) { uriHandler.openUri(REPORT_ERROR_URL) } }
        }
    }
}

@Composable
private fun MethodBlockView(block: MethodBlock) {
    val pad = Modifier.padding(horizontal = 16.dp)
    when (block.kind) {
        MethodBlock.Kind.TITLE -> Text(
            block.text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = pad.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
        )
        MethodBlock.Kind.HEADING -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = pad.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
        )
        MethodBlock.Kind.PARAGRAPH -> Text(block.text, style = MaterialTheme.typography.bodyMedium, modifier = pad.padding(vertical = 4.dp))
        MethodBlock.Kind.BULLET -> Row(pad.padding(vertical = 3.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            // "Term: definition" bullets show the term in bold; the words are unchanged.
            val split = block.text.indexOf(": ").takeIf { it in 1..40 }
            Text(
                buildAnnotatedString {
                    if (split == null) {
                        append(block.text)
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(block.text.substring(0, split + 1)) }
                        append(block.text.substring(split + 1))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
