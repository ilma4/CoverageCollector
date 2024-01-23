package org.sbone.research

import java.nio.file.Path
import kotlin.io.path.div

data class Context(
    val basePath: Path,
    val evosuitePath: Path,
    val junitPath: Path,
    val logsPath: Path,
    val executionTimeoutMilliseconds: Long = defaultTimeoutMillis
) {
    companion object {
        public const val defaultTimeoutMillis: Long = Long.MAX_VALUE
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