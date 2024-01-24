package org.sbone.research

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// like runBlocking{ withTimeout(...){ yourCode() }}
// but works as expected if yourCode is not suspendable
fun <T> runWithTimeoutOrNull(timeoutMillis: Long, function: () -> T): T? {
    if (timeoutMillis <= 0) {
        return null;
    }

    val latch = CountDownLatch(1)
    var result: T? = null
    var functionFailed = false

    // Create a thread to run the function
    val thread = thread(start = true) {
        try {
            result = function()
        } catch (e: Exception) {
            functionFailed = true
        } finally {
            latch.countDown() // Count down the latch to signal the function has finished
        }
    }

    // Wait for the function to finish or the timeout to be reached
    val completed = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)

    if (!completed || functionFailed) {
        thread.interrupt()
        return null
    }
    return result
}
