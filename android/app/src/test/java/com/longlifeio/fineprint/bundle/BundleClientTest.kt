package com.longlifeio.fineprint.bundle

import com.longlifeio.fineprint.BuildConfig
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.InetSocketAddress

/**
 * The bundle download, against a local server shaped like GitHub's: each file's address redirects to a second one
 * that serves it. Every request names FinePrint and its version, and nothing about the phone.
 */
@RunWith(RobolectricTestRunner::class)
class BundleClientTest {

    @get:Rule val folder = TemporaryFolder()

    private val files = mapOf(
        BUNDLE_FILE to File("src/test/resources/bundle-fixture.json").readText(),
        TRACKERS_FILE to File("src/main/assets/trackers.json").readText(),
        JURISDICTIONS_FILE to File("src/test/resources/jurisdictions-fixture.json").readText(),
    )
    private val requests = mutableListOf<Pair<String, List<String>?>>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            synchronized(requests) { requests += path to exchange.requestHeaders["User-Agent"] }
            val name = path.substringAfterLast('/')
            when {
                path.startsWith("/latest/download/") -> {
                    exchange.responseHeaders.add("Location", "/assets/$name")
                    exchange.sendResponseHeaders(302, -1)
                }
                path.startsWith("/assets/") && name in files -> {
                    val body = files.getValue(name).toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                else -> exchange.sendResponseHeaders(404, -1)
            }
            exchange.close()
        }
        start()
    }

    @After fun stop() = server.stop(0)

    @Test
    fun everyRequestNamesFinePrintAndItsVersionAndNothingElse() {
        val client = BundleClient(folder.root, "http://127.0.0.1:${server.address.port}/latest/download/", logRequests = false)

        val state = client.refresh()

        assertNull(state.error)
        assertNotNull(state.bundle)
        assertNotNull(state.signatures)
        assertEquals(
            files.keys.flatMap { listOf("/latest/download/$it", "/assets/$it") },
            requests.map { it.first },
        )
        assertEquals("FinePrint/${BuildConfig.VERSION_NAME}", BUNDLE_USER_AGENT)
        requests.forEach { (path, agent) -> assertEquals(path, listOf(BUNDLE_USER_AGENT), agent) }
    }
}
