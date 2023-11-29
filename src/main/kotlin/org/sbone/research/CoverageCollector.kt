@file:Suppress("MemberVisibilityCanBePrivate")

package org.sbone.research

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ICoverageNode
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.runtime.LoggerRuntime
import org.jacoco.core.runtime.RuntimeData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.mapToArray
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import org.vorpal.research.kthelper.tryOrNull
import java.io.File
import java.lang.reflect.Array
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.toList


val String.javaString get() = replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

interface CoverageInfo {
    val covered: Int
    val total: Int
    val ratio: Double
}

enum class CoverageUnit(unit: String) {
    INSTRUCTION("instructions"),
    BRANCH("branches"),
    LINE("lines"),
    COMPLEXITY("complexity");

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

enum class AnalysisUnit(unit: String) {
    METHOD("method"),
    CLASS("class");

    val unitName: String = unit

    override fun toString(): String {
        return unitName
    }
}

data class GenericCoverageInfo(
    override val covered: Int,
    override val total: Int,
    val unit: CoverageUnit
) : CoverageInfo {
    override val ratio: Double
        get() = when (total) {
            0 -> 0.0
            else -> covered.toDouble() / total
        }

    override fun toString(): String = buildString {
        append(String.format("%s of %s %s covered", covered, total, unit))
        if (total > 0) {
            append(String.format(" = %.2f", ratio * 100))
            append("%")
        }
    }
}

data class TestsCountInfo(val testsNumber: Int, val failureCount: Int) {
    override fun toString(): String {
        return "$testsNumber tests; $failureCount failure"
    }
}

abstract class CommonCoverageInfo(
    val name: String,
    val level: AnalysisUnit,
    val instructionCoverage: CoverageInfo,
    val branchCoverage: CoverageInfo,
    val linesCoverage: CoverageInfo,
    val complexityCoverage: CoverageInfo,
    val testsInfo: TestsCountInfo
) {
    open fun print(detailed: Boolean = false) = toString()

    override fun toString(): String = buildString {
        appendLine(String.format("Coverage of `%s` %s:", name, level))
        appendLine("    $instructionCoverage")
        appendLine("    $branchCoverage")
        appendLine("    $linesCoverage")
        appendLine("    $complexityCoverage")
        append("    $testsInfo")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommonCoverageInfo) return false

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class MethodCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo,
    testsInfo: TestsCountInfo,
) : CommonCoverageInfo(
    name,
    AnalysisUnit.METHOD,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage,
    testsInfo
)

class ClassCoverageInfo(
    name: String,
    instructionCoverage: CoverageInfo,
    branchCoverage: CoverageInfo,
    linesCoverage: CoverageInfo,
    complexityCoverage: CoverageInfo,
    testsInfo: TestsCountInfo,
) : CommonCoverageInfo(
    name,
    AnalysisUnit.CLASS,
    instructionCoverage,
    branchCoverage,
    linesCoverage,
    complexityCoverage,
    testsInfo
) {
    val methods = mutableSetOf<MethodCoverageInfo>()

    override fun print(detailed: Boolean) = buildString {
        appendLine(this@ClassCoverageInfo.toString())
        if (detailed) {
            methods.forEach {
                appendLine()
                appendLine(it.print(true))
            }
        }
    }
}

class CoverageReporter(
    private val context: Context,
    containers: List<Container> = listOf()
) {
    init {
        for (container in containers) {
            `try` { container.extract(context.jacocoInstrumentedDir) }
        }
    }

    private val Path.isClass get() = name.endsWith(".class")

    fun execute(
        cm: ClassManager,
        className: String,
    ): CommonCoverageInfo {
        val testClasses = Files.walk(context.compileDir).filter { it.isClass }.toList()
        val klass = className.replace(Package.CANONICAL_SEPARATOR, File.separatorChar)
        val (coverageBuilder, testsInfo) =
            getCoverageBuilderAndTestsInfo(listOf(context.jacocoInstrumentedDir.resolve("$klass.class")), testClasses)
        return getClassCoverage(cm, coverageBuilder, testsInfo).first()
    }

    private fun getCoverageBuilderAndTestsInfo(
        classes: List<Path>,
        testClasses: List<Path>,
        logProgress: Boolean = true
    ): Pair<CoverageBuilder, TestsCountInfo> {
        val runtime = LoggerRuntime()
        val originalClasses = mutableMapOf<Path, ByteArray>()
        for (classPath in classes) {
            originalClasses[classPath] = classPath.readBytes()
            val instrumented = classPath.inputStream().use {
                val fullyQualifiedName = classPath.fullyQualifiedName(context.jacocoInstrumentedDir)
                val instr = Instrumenter(runtime)
                instr.instrument(it, fullyQualifiedName)
            }
            classPath.writeBytes(instrumented)
        }
        val data = RuntimeData()
        runtime.startup(data)

        if (logProgress) log.debug("Running tests...")
        val classLoader = PathClassLoader(
            listOf(
                context.logsPath,
                context.jacocoInstrumentedDir,
                context.compileDir,
                context.junitPath,
                context.evosuitePath,
            )
        )
        var tests = 0
        var failures = 0
        for (testPath in testClasses) {
            val testClassName = testPath.fullyQualifiedName(context.compileDir)
            val testClass = classLoader.loadClass(testClassName)
            if (logProgress) log.debug("Running test $testClassName")
            val jcClass = classLoader.loadClass("org.junit.runner.JUnitCore")
            val jc = jcClass.newInstance()
            val computerClass = classLoader.loadClass("org.junit.runner.Computer")
            val result = jcClass.getMethod("run", computerClass, Array.newInstance(Class::class.java, 0).javaClass)
                .invoke(jc, computerClass.newInstance(), arrayOf(testClass))

            val resultClass = result.javaClass
            tests += resultClass.getMethod("getRunCount").invoke(result) as Int
            failures += resultClass.getMethod("getFailureCount").invoke(result) as Int
        }

        if (logProgress) log.debug("Analyzing Coverage...")
        val executionData = ExecutionDataStore()
        val sessionInfos = SessionInfoStore()
        data.collect(executionData, sessionInfos, false)
        runtime.shutdown()

        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        executionData.contents
        for (className in classes) {
            originalClasses[className]?.let {
                tryOrNull {
                    analyzer.analyzeClass(it, className.fullyQualifiedName(context.jacocoInstrumentedDir))
                }
            }
            className.writeBytes(originalClasses[className]!!)
        }
        return coverageBuilder to TestsCountInfo(tests, failures)
    }

    private val String.fullyQualifiedName: String
        get() = removeSuffix(".class").javaString

    private fun Path.fullyQualifiedName(base: Path): String =
        relativeTo(base).toString()
            .removePrefix("src/main")
            .removePrefix(File.separatorChar.toString())
            .replace(File.separatorChar, Package.CANONICAL_SEPARATOR)
            .removeSuffix(".class")

    private fun getClassCoverage(
        cm: ClassManager,
        coverageBuilder: CoverageBuilder,
        testsInfo: TestsCountInfo,
    ): Set<ClassCoverageInfo> =
        coverageBuilder.classes.mapTo(mutableSetOf()) {
            val kfgClass = cm[it.name]
            val classCov = getCommonCounters<ClassCoverageInfo>(it.name, it, testsInfo)
            for (mc in it.methods) {
                val kfgMethod = kfgClass.getMethod(mc.name, mc.desc)
                classCov.methods += getCommonCounters<MethodCoverageInfo>(
                    kfgMethod.prototype.fullyQualifiedName,
                    mc, testsInfo
                )
            }
            classCov
        }

    private fun getCounter(unit: CoverageUnit, counter: ICounter): CoverageInfo {
        val covered = counter.coveredCount
        val total = counter.totalCount
        return GenericCoverageInfo(covered, total, unit)
    }

    private inline fun <reified T : CommonCoverageInfo> getCommonCounters(
        name: String,
        coverage: ICoverageNode,
        testsInfo: TestsCountInfo,
    ): T {
        val insts = getCounter(CoverageUnit.INSTRUCTION, coverage.instructionCounter)
        val brs = getCounter(CoverageUnit.BRANCH, coverage.branchCounter)
        val lines = getCounter(CoverageUnit.LINE, coverage.lineCounter)
        val complexities = getCounter(CoverageUnit.COMPLEXITY, coverage.complexityCounter)

        return when (T::class.java) {
            MethodCoverageInfo::class.java -> MethodCoverageInfo(name, insts, brs, lines, complexities, testsInfo)
            ClassCoverageInfo::class.java -> ClassCoverageInfo(name, insts, brs, lines, complexities, testsInfo)
            else -> unreachable { log.error("Unknown common coverage info class ${T::class.java}") }
        } as T
    }

    class PathClassLoader(paths: List<Path>) : URLClassLoader(paths.mapToArray { it.toUri().toURL() }, null) {
        private val cache = hashMapOf<String, Class<*>>()

        private val loader = PathClassLoader::class.java.classLoader

        override fun loadClass(name: String): Class<*> {
            return cache.computeIfAbsent(name) { super.loadClass(name) }
        }

        override fun findClass(name: String): Class<*> {
            return `try` { super.findClass(name) }.getOrElse { loader.loadClass(name) }
        }
    }
}

val statsLogger: Logger = LoggerFactory.getLogger("coverage-info")

fun reportCoverage(context: Context, containers: List<Container>, cm: ClassManager, className: String) {
    val coverageInfo = CoverageReporter(context, containers).execute(cm, className)
    statsLogger.info(coverageInfo.print(false))
}
