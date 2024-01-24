package org.sbone.research

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.debug
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = args[0]
    val baseDir = args[1]
    val evosuitePath = args[2]
    val junitPath = args[3]
    val runsNumber = args[4].toInt()
    val logsPath = args[5]
    // arg[6] is timeout in seconds (eg: 120 is 2 minutes)
    val timeoutMillis = tryOrNull { args[6] }?.toLong()?.times(1000L) ?: Context.NO_TIMEOUT
    log.debug { "Execution timeout is $timeoutMillis" }

    val bench = BenchmarkCollection(File(config))

    bench.benchmarks.forEach { (name, task) ->
        for (run in 1..runsNumber) {
            log.debug("Start ${name}_$run")
            val context = Context(
                basePath = Path(baseDir) / "${name}_${run}",
                evosuitePath = Path(evosuitePath),
                junitPath = Path(junitPath),
                logsPath = Path(logsPath),
                executionTimeoutMillis = timeoutMillis
            )

            val containerPaths = (listOf(task.binDirectory) + task.classPath).map { it.toPath().toAbsolutePath() }
            val containers = listOfNotNull(
                *containerPaths.mapToArray {
                    it.asContainer()
                        ?: throw RuntimeException("Can't represent ${it.toAbsolutePath()} as class container")
                }
            )

            val analysisJars = listOfNotNull(*containers.toTypedArray())
            val cm = ClassManager(
                KfgConfig(
                    flags = Flags.readAll,
                    useCachingLoopManager = false,
                    failOnError = false,
                    verifyIR = false,
                    checkClasses = false
                )
            )
            cm.initialize(analysisJars)

            log.debug("Compile ${name}_$run")
            val compiler = CompilerHelper(
                containerPaths,
                context.testsDir,
                Path(evosuitePath),
                Path(junitPath),
                context.compileDir
            )

            context.testsDir.toFile().walk().onLeave { dir ->
                dir.toPath().forEachDirectoryEntry("*.java") {
                    tryOrNull { compiler.compileFile(it) } ?: log.error("Failed to compile $it")
                }
            }.asSequence().last()

            task.classNames.forEach {
                statsLogger.info("Benchmark ${name}_$run")
                log.debug("Benchmark ${name}_$run; class $it")
                reportCoverage(context, containers, cm, it)
                log.debug("Finished ${name}_$run; class $it")
            }
            log.debug("Finished ${name}_$run")
        }
    }
    log.debug("All finished")
    exitProcess(0)
}