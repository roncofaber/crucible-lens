package crucible.lens.data.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SingleFlightTest {
    @Test
    fun concurrentRequestsShareOneExecution() = runTest {
        val flight = SingleFlight<String, String>()
        var executions = 0

        val results = List(20) {
            async {
                flight.run("projects") {
                    executions++
                    yield()
                    "result"
                }
            }
        }.awaitAll()

        assertEquals(1, executions)
        assertEquals(List(20) { "result" }, results)
    }

    @Test
    fun failedExecutionDoesNotBlockRetry() = runTest {
        val flight = SingleFlight<String, String>()

        assertFailsWith<IllegalStateException> {
            flight.run("project") { error("network failure") }
        }

        assertEquals("recovered", flight.run("project") { "recovered" })
    }
}
