package org.sbone.research

import java.nio.file.Path
import kotlin.io.path.div

data class Context(
    val basePath: Path,
    val jarPaths: List<Path>,
    val executionTimeoutMillis: Long = NO_TIMEOUT // timeout for one testcase
) {
    companion object {
        const val NO_TIMEOUT: Long = -1
    }

    private val tempDir = basePath / "temp"

    val testsDir = tempDir / "testcases"

    private val jacocoDir = tempDir / "jacocoDir"

    val jacocoInstrumentedDir = (jacocoDir / "jacoco").also {
        it.toFile().mkdirs()
    }

    val compileDir = (jacocoDir / "compiled").also {
        it.toFile().mkdirs()
    }
}