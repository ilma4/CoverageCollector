package org.sbone.research

import java.nio.file.Path

class CompilerHelper(
    private val classPaths: List<Path>,
    private val testDirectory: Path,
    private val evosuitePath: Path,
    private val junitPath: Path,
    private val compileDir: Path,
    private val extraPaths: List<Path> = emptyList(),
) {
    fun compileFile(file: Path) {
        val compilerDriver = JavaCompilerDriver(
            listOf(
                *classPaths.toTypedArray(),
                evosuitePath,
                junitPath,
                testDirectory,
                *extraPaths.toTypedArray()
            ), compileDir
        )
        compilerDriver.compile(listOf(file))
    }
}