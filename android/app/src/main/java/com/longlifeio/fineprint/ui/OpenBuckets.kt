package com.longlifeio.fineprint.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.longlifeio.fineprint.explain.BUCKETS

/** Lines a bucket shows under Where it goes before "See all". */
const val BUCKET_FIRST = 4

/**
 * Which buckets under Where it goes show all their lines, kept on this phone across launches like the
 * home's sections: closed at first, so each shows its first four and "See all N".
 */
class OpenBuckets(private val read: (String, Boolean) -> Boolean, private val write: (String, Boolean) -> Unit) {
    private val open = mutableStateMapOf<String, Boolean>().apply { BUCKETS.forEach { put(key(it), read(key(it), false)) } }

    fun isOpen(bucket: String): Boolean = open[key(bucket)] ?: false

    fun toggle(bucket: String) {
        val now = !isOpen(bucket)
        open[key(bucket)] = now
        write(key(bucket), now)
    }

    companion object {
        private fun key(bucket: String) = "all:$bucket"
        fun of(prefs: SharedPreferences) = OpenBuckets({ k, d -> prefs.getBoolean(k, d) }, { k, v -> prefs.edit().putBoolean(k, v).apply() })
        /** Every line shown and nothing kept: for tests and screenshots. */
        fun allOpen() = OpenBuckets({ _, _ -> true }, { _, _ -> })
    }
}

@Composable
fun rememberOpenBuckets(): OpenBuckets {
    val context = LocalContext.current
    return remember { OpenBuckets.of(context.getSharedPreferences("detail", Context.MODE_PRIVATE)) }
}
