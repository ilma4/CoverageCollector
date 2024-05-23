package org.sbone.research

import org.apache.commons.cli.*
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

fun getOptions(): Options = Options()
    .addOption("c", "configPath", true, "Path to config file")
    .addOption("b", "baseDir", true, "Path to the base dir")
    .addOption(Option.builder("j").longOpt("jars").hasArgs().desc("Paths to jars").build())
    .addOption("r", "runsNumber", true, "Runs number")
    .addOption("t", "timeout", true, "Timeout for tests")


private operator fun CommandLine.get(opt: String) = getOptionValue(opt)
private fun CommandLine.getList(opt: String) = getOptionValues(opt).toList()


fun main(args: Array<String>) {
    val params = DefaultParser().parse(getOptions(), args)
    val config = params["configPath"] as String
    val baseDir = params["baseDir"] as String
    val jarPaths = params.getList("jars").map { Path(it) }
    val runsNumber = params["runsNumber"].toInt()
    val timeoutMillis =
        params["timeout"].toIntOrNull()?.toLong()?.times(1000L) ?: Context.NO_TIMEOUT
    log.debug { "Execution timeout is $timeoutMillis" }

    val bench = BenchmarkCollection(File(config))

    bench.benchmarks.forEach { (name, task) ->
        for (run in 1..runsNumber) {
            log.debug("Start ${name}_$run")
            val context = Context(
                basePath = Path(baseDir) / "${name}_${run}",
                jarPaths = jarPaths,
                executionTimeoutMillis = timeoutMillis
            )

            val containerPaths =
                (listOf(task.binDirectory) + task.classPath).map { it.toPath().toAbsolutePath() }
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
                classPaths = containerPaths,
                jarPaths = jarPaths,
                testDirectory = context.testsDir,
                compileDir = context.compileDir
            )

            context.testsDir.toFile().walk().onLeave { dir ->
                dir.toPath().forEachDirectoryEntry("*.java") {
                    tryOrNull { compiler.compileFile(it) } ?: log.error("Failed to compile $it")
                }
            }.asSequence().lastOrNull()

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