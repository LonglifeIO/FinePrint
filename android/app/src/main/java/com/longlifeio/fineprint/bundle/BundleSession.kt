package com.longlifeio.fineprint.bundle

import android.content.Context
import com.longlifeio.fineprint.BuildConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The knowledge bundle for this process: the cached copy at once, a fresh download once per launch. */
class BundleSession(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = BundleClient(File(context.filesDir, "bundle"), BuildConfig.BUNDLE_BASE_URL, BuildConfig.DEBUG)
    private val _state = MutableStateFlow(client.cached())
    val state: StateFlow<BundleState> = _state.asStateFlow()
    private var job: Job? = null // main thread only

    val baseUrl: String get() = BuildConfig.BUNDLE_BASE_URL

    /** Downloads the bundle unless that already happened (or is happening) in this process. */
    fun refreshOnce() {
        if (job != null) return
        job = scope.launch { download() }
    }

    /** Downloads now, e.g. from the About screen. */
    fun refreshNow() {
        if (job?.isActive == true) return
        job = scope.launch { download() }
    }

    private fun download() {
        _state.update { it.copy(refreshing = true, error = null) }
        _state.value = client.refresh()
    }
}
