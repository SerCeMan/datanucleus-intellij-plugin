package me.serce.datanucleus.build

import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.util.*

class DatanucleusModuleLevelBuilder : ModuleLevelBuilder(BuilderCategory.CLASS_INSTRUMENTER) {
    private val LOG = Logger.getInstance(DatanucleusModuleLevelBuilder::class.java)


    @Throws(ProjectBuildException::class, IOException::class)
    override fun build(context: CompileContext, chunk: ModuleChunk, dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>, outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode {
        if (outputConsumer.compiledClasses.isEmpty()) {
            return ModuleLevelBuilder.ExitCode.NOTHING_DONE
        }

        val progress = getProgressMessage()
        val shouldShowProgress = !StringUtil.isEmptyOrSpaces(progress)
        if (shouldShowProgress) {
            context.processMessage(ProgressMessage(progress + " [" + chunk.presentableShortName + "]"))
        }

        var exitCode: ModuleLevelBuilder.ExitCode = ModuleLevelBuilder.ExitCode.NOTHING_DONE
        try {
            val platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false)
            val classpath = ArrayList<File>()
            classpath.addAll(ProjectPaths.getCompilationClasspath(chunk, false))
            classpath.addAll(ProjectPaths.getSourceRootsWithDependents(chunk).keys)
            val sdk = chunk.representativeTarget().module.getSdk(JpsJavaSdkType.INSTANCE)


            val platformUrls = ArrayList<URL>()
            if (sdk != null && JpsJavaSdkType.getJavaVersion(sdk) >= 9) {
                platformUrls.add(InstrumentationClassFinder.createJDKPlatformUrl(sdk.homePath))
            }

            for (file in platformCp) {
                platformUrls.add(file.toURI().toURL())
            }

            val urls = ArrayList<URL>()
            for (file in classpath) {
                urls.add(file.toURI().toURL())
            }

            val buildClassLoader = URLClassLoader(platformUrls.plus(urls).toTypedArray(), this.javaClass.classLoader)
            try {
                for (moduleChunk in chunk.targets) {
                    val persistense = moduleChunk.module.sourceRoots
                            .firstOrNull { it.rootType == JavaResourceRootType.RESOURCE }
                            ?.file
                            ?.absolutePath
                            ?.plus("/META-INF/persistence.xml")

                    if (persistense != null && File(persistense).exists()) {
                        context.processMessage(ProgressMessage("Enhancing ${moduleChunk.presentableName}"))
                        val cl = Thread.currentThread().contextClassLoader
                        try {
                            Thread.currentThread().contextClassLoader = buildClassLoader
                            val c = buildClassLoader.loadClass("org.datanucleus.enhancer.DataNucleusEnhancer")
                            val m = c.getMethod("main", Array<String>::class.java)
                            val compiledClassesList = File("dn-out${UUID.randomUUID()}.txt")
                            compiledClassesList.bufferedWriter().use { out ->
                                outputConsumer.compiledClasses.forEach {
                                    out.write("${it.value.outputFile.absolutePath}\n")
                                }
                            }
                            m.isAccessible = true
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
            } finally {
                buildClassLoader.close()
            }
        } finally {
            if (shouldShowProgress) {
                context.processMessage(ProgressMessage("")) // cleanup progress
            }
        }
        return exitCode
    }

    override fun getPresentableName() = "Datanucleus enhancer"

    fun getProgressMessage() = "Enhancing classes..."
}
