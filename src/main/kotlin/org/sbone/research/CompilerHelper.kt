package org.sbone.research

import java.nio.file.Path

class CompilerHelper(
    private val classPaths: List<Path>,
    private val testDirectory: Path,
    private val evosuitePath: Path,
    private val junitPath: Path,
    private val compileDir: Path,
) {
    fun compileFile(file: Path) {
        val compilerDriver = JavaCompilerDriver(
            listOf(*classPaths.toTypedArray(), evosuitePath, junitPath, testDirectory), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}