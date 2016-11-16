package me.serce.datanucleus.build

import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.instrumentation.ClassProcessingBuilder
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import java.io.File
import java.util.*

class DatanucleusModuleLevelBuilder : ClassProcessingBuilder(BuilderCategory.CLASS_INSTRUMENTER) {
    private val LOG = Logger.getInstance(DatanucleusModuleLevelBuilder::class.java)

    override fun performBuild(context: CompileContext, chunk: ModuleChunk, finder: InstrumentationClassFinder,
                              outputConsumer: OutputConsumer): ExitCode {
        var exitCode = ExitCode.NOTHING_DONE
        for (moduleChunk in chunk.targets) {
            val persistense = finder.getResourceAsStream("META-INF/persistence.xml")
            if (persistense != null) {
                context.processMessage(ProgressMessage("Enhancing ${moduleChunk.presentableName}"))
                val cl = Thread.currentThread().contextClassLoader
                try {
                    Thread.currentThread().contextClassLoader = finder.loader
                    val c = finder.loader.loadClass("org.datanucleus.enhancer.DataNucleusEnhancer")
                    val m = c.getMethod("main", Array<String>::class.java)
                    val compiledClassesList = File("dn-out${UUID.randomUUID()}.txt")
                    compiledClassesList.bufferedWriter().use { out ->
                        outputConsumer.compiledClasses.forEach {
                            out.write("${it.value.outputFile.absolutePath}\n")
                        }
                    }
                    m.invoke(null, arrayOf("-api", "JPA", "-flf", compiledClassesList.absolutePath))
                    exitCode = ExitCode.OK
                } catch (e: Exception) {
                    LOG.error("Enhancing failed", e)
                    context.processMessage(CompilerMessage("Enhancing failed", e))
                } finally {
                    Thread.currentThread().contextClassLoader = cl
                }
            }
        }
        return exitCode
    }

    override fun isEnabled(context: CompileContext, chunk: ModuleChunk): Boolean {
        //TODO
        return true
    }

    override fun getPresentableName() = "Datanucleus enhancer"

    override fun getProgressMessage() = "Enhancing classes..."
}
