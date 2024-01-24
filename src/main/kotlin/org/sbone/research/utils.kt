package org.sbone.research

import org.vorpal.research.kthelper.tryOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/*
  Like runBlocking{ withTimeout(timeout){ yourCode() }}
  But works as expected if yourCode is not suspendable
*/
fun <T> runWithTimeoutOrNull(timeoutMillis: Long, function: () -> T): T? {
    if (timeoutMillis <= 0) {
        return null
    }

    val latch = CountDownLatch(1)
    val result = AtomicReference<T>(null)
    val functionFailed = AtomicBoolean(false)

    // Create a thread to run the function
    val thread = thread(start = true) {
        try {
            result.set(function())
        } catch (e: Exception) {
            functionFailed.set(true)
        } finally {
            latch.countDown() // Count down the latch to signal the function has finished
        }
    }

    // Wait for the function to finish or the timeout to be reached
    val completed = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)

    if (!completed || functionFailed.get()) {
        tryOrNull { thread.interrupt() }
        return null
    }
    return result.get()
}
