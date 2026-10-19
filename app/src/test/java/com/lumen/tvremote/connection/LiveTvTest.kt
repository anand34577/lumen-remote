package com.lumen.tvremote.connection

import com.lumen.tvremote.protocol.RemoteEvent
import com.lumen.tvremote.protocol.RemoteProtocol
import com.lumen.tvremote.protocol.TvKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * End-to-end checks against a real TV. Skipped unless TV_HOST is set, e.g.
 *   TV_HOST=192.168.1.20 TV_STAGE=probe ./gradlew testDebugUnitTest --tests '*LiveTvTest*'
 *
 * Stages: probe (fresh identity must be rejected), pair (reads the TV code from the file in
 * TV_CODE_FILE), control (connects with the paired identity and sends a harmless key).
 */
class LiveTvTest {
    private val host: String? = System.getenv("TV_HOST")
    private val stage: String? = System.getenv("TV_STAGE")
    private val identityFile = File(System.getenv("TV_IDENTITY") ?: "build/live-tv-identity.p12")

    private fun session(identity: ClientIdentity) =
        RemoteSession(identity, host!!, RemoteSession.DEFAULT_REMOTE_PORT, "JVM test", "Lumen", "test")

    @Test
    fun probeRejectsUnpairedIdentity() = runBlocking {
        assumeTrue(host != null && stage == "probe")
        val fresh = File.createTempFile("probe", ".p12").apply { delete() }
        val end = session(ClientIdentity(fresh)).run(onReady = {}, onEvent = {})
        println("Probe result: $end")
        assertEquals(SessionEnd.NotPaired, end)
    }

    @Test
    fun pairWithCodeFromFile() = runBlocking {
        assumeTrue(host != null && stage == "pair")
        val codeFile = File(System.getenv("TV_CODE_FILE")!!).apply { delete() }
        val pairing = PairingSession(ClientIdentity(identityFile), host!!, clientName = "Lumen Remote (test)")
        pairing.start()
        println("Code requested; waiting for ${codeFile.absolutePath}")
        val code = withTimeout(600_000) {
            while (!codeFile.exists()) delay(250)
            codeFile.readText().trim()
        }
        val result = pairing.submitCode(code)
        println("Pairing result: $result")
        assertEquals(PairingResult.Success, result)
    }

    @Test
    fun connectAndControl() = runBlocking {
        assumeTrue(host != null && stage == "control")
        val events = Collections.synchronizedList(mutableListOf<RemoteEvent>())
        val ready = CompletableDeferred<Unit>()
        val s = session(ClientIdentity(identityFile))
        val run = async { s.run(onReady = { ready.complete(Unit) }, onEvent = { events += it }) }
        withTimeout(15_000) { ready.await() }
        println("Connected and active")
        delay(1_500) // collect the initial power/volume/app state
        // Mute twice: audible blip at most, leaves the TV as it was.
        assertTrue(s.send(RemoteProtocol.key(TvKey.MUTE.code)))
        delay(800)
        assertTrue(s.send(RemoteProtocol.key(TvKey.MUTE.code)))
        delay(1_500)
        s.close()
        println("Session end: ${run.await()}")
        events.forEach { println("Event: $it") }
        assertTrue("TV should report its volume", events.any { it is RemoteEvent.Volume })
    }
}
