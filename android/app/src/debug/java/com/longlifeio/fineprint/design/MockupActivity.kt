package com.longlifeio.fineprint.design

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import com.longlifeio.fineprint.FinePrintApp
import com.longlifeio.fineprint.bundle.Bundle as KnowledgeBundle
import com.longlifeio.fineprint.egress.InstalledApp
import com.longlifeio.fineprint.egress.TrackerScanResult
import com.longlifeio.fineprint.egress.TrackerSignature
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug only (G5 step 1): renders one mockup from this phone's real apps, scans and bundle, then
 * saves the whole of it, taller than the screen if need be, as a PNG in the app's external files.
 *
 *   adb shell am start -n com.longlifeio.fineprint/.design.MockupActivity \
 *     --es direction ledger|dashboard|label --es screen home|detail --es mode light|dark --ef scale 1.5 --es out <name>
 *
 * --es direction app draws the app's real screen instead (RealScreens.kt): the home, for G5 step 2.
 */
class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val direction = intent.getStringExtra("direction") ?: "ledger"
        val screen = intent.getStringExtra("screen") ?: "detail"
        val dark = intent.getStringExtra("mode") == "dark"
        val scale = intent.getFloatExtra("scale", 1f)
        val out = intent.getStringExtra("out") ?: "g5-$direction-$screen-${if (dark) "dark" else "light"}"
        val app = application as FinePrintApp
        app.session.refresh()
        app.bundle.refreshOnce()
        setContent {
            val apps by app.session.apps.collectAsState()
            val results by app.session.results.collectAsState()
            val progress by app.session.progress.collectAsState()
            val signatures by app.session.signatures.collectAsState()
            val state by app.bundle.state.collectAsState()
            val bundle = state.bundle
            // The list's default view: the apps you installed (system apps stay hidden, as in the real list).
            val visible = apps.orEmpty().filterNot { it.isSystem }
            val ready = bundle != null && apps != null && !progress.running && visible.all { results.containsKey(it.scanKey) }
            val byId = signatures?.trackers.orEmpty().associateBy { it.id }
            val data = remember(ready, bundle, results) {
                if (!ready) null else mockupData(visible, results, bundle!!, byId) { pkg ->
                    runCatching { packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull()
                }
            }
            if (direction == "app") {
                // The real screen: drawn as the phone shows it, once every app is scanned.
                if (!ready) {
                    Text("Loading… ${progress.done}/${progress.total}")
                } else {
                    CompositionLocalProvider(
                        LocalConfiguration provides LocalConfiguration.current.withNight(dark),
                        LocalDensity provides Density(LocalDensity.current.density, scale),
                    ) { Capture(out, fixedHeight = REAL_HEIGHT) { RealHome(app) } }
                }
            } else if (data == null) {
                val missing = visible.filterNot { results.containsKey(it.scanKey) }.map { it.packageName }
                Text("Loading… apps=${apps?.size} results=${results.size} running=${progress.running} ${progress.done}/${progress.total} bundle=${bundle != null} missing=$missing")
            } else {
                LaunchedEffect(Unit) { saveFixture(visible, results, data, bundle!!, byId) }
                val (palette, fonts) = look(direction, dark)
                Capture(out) {
                    MockupTheme(palette, fonts, scale) { MockupScreen(direction, screen, data) }
                }
            }
        }
    }

    /**
     * Draws [content] at its full height, then writes it to <external files>/<name>.png and a .done
     * marker. Tall content is recorded in tiles: a GPU layer can't be taller than its texture limit
     * (8,192px here), so each tile stays under it and the tiles are joined on a CPU bitmap.
     */
    @Composable
    private fun Capture(name: String, fixedHeight: Dp? = null, content: @Composable () -> Unit) {
        val tiles = List(4) { rememberGraphicsLayer() }
        var height = 0f
        var width = 0
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).verticalScroll(rememberScrollState())) {
            Box(Modifier.drawWithContent {
                height = size.height
                width = size.width.toInt()
                tiles.forEachIndexed { i, tile ->
                    val top = i * TILE
                    if (top < size.height) {
                        tile.record(size = IntSize(width, minOf(TILE, size.height - top).toInt())) {
                            translate(top = -top) { this@drawWithContent.drawContent() }
                        }
                    }
                }
                drawContent()
            }) {
                // A lazy list needs a bounded height: give it room for everything, then trim the empty end.
                if (fixedHeight == null) content() else Box(Modifier.fillMaxWidth().height(fixedHeight)) { content() }
            }
        }
        LaunchedEffect(name) {
            delay(2500) // fonts, icons and layout settle
            val bitmap = Bitmap.createBitmap(width, height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            tiles.forEachIndexed { i, tile ->
                if (i * TILE < height) {
                    val part = tile.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                    canvas.drawBitmap(part, 0f, i * TILE, null)
                }
            }
            val dir = getExternalFilesDir(null)!!
            val out = if (fixedHeight == null) bitmap else trimBottom(bitmap)
            File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "$name.done").writeText("${out.width}x${out.height}")
            Log.i(TAG, "saved $name.png ${out.width}x${out.height}")
            finish() // so the next render starts a fresh instance
        }
    }

    /**
     * Saves this phone's apps and scans as the @Preview fixture (scan-fixture.json, beside the PNGs),
     * then checks that the fixture rebuilds the same explanations as the live scan.
     */
    private fun saveFixture(apps: List<InstalledApp>, results: Map<String, TrackerScanResult>, live: MockupData, bundle: KnowledgeBundle, signatures: Map<String, TrackerSignature>) {
        val json = fixtureJson(apps, results)
        File(getExternalFilesDir(null), FIXTURE_ASSET).writeText(json)
        val (fixed, scans) = parseFixture(json)
        val strip = { d: MockupData? -> d?.apps?.map { it.copy(icon = null, app = it.app.copy(apkPaths = emptyList(), lastUpdateTime = 0)) } }
        Log.i(TAG, "fixture: ${apps.size} apps; rebuilds the same explanations: ${strip(mockupData(fixed, scans, bundle, signatures) { null }) == strip(live)}")
    }

    /** Cuts the run of rows at the bottom that are all the background colour, keeping a 48px margin. */
    private fun trimBottom(b: Bitmap): Bitmap {
        val background = b.getPixel(0, b.height - 1)
        val row = IntArray(b.width)
        var last = b.height - 1
        while (last > 0) {
            b.getPixels(row, 0, b.width, 0, last, b.width, 1)
            if (row.any { it != background }) break
            last--
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, minOf(b.height, last + 48))
    }

    private companion object {
        const val TAG = "FinePrintMockup"
        const val TILE = 6000f
        val REAL_HEIGHT = 4200.dp
    }
}

/** One mockup screen by name, as MockupActivity and the @Previews draw it. */
@Composable
fun MockupScreen(direction: String, screen: String, data: MockupData) = when (direction to screen) {
    "ledger" to "home" -> LedgerHome(data)
    "dashboard" to "home" -> DashboardHome(data)
    "dashboard" to "detail" -> DashboardDetail(data)
    "label" to "home" -> LabelHome(data)
    "label" to "detail" -> LabelDetail(data)
    else -> LedgerDetail(data)
}

/** Ledger: palette A, Atkinson + Fraunces. Dashboard: palette B, Plex + Literata. Label-first: palette B, Atkinson + Fraunces. */
fun look(direction: String, dark: Boolean): Pair<Palette, FontPair> = when (direction) {
    "dashboard" -> (if (dark) FieldDark else FieldLight) to PlexLiterata
    "label" -> (if (dark) FieldDark else FieldLight) to AtkinsonFraunces
    else -> (if (dark) HarbourDark else HarbourLight) to AtkinsonFraunces
}
