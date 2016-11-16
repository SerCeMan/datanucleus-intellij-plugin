package me.serce.datanucleus.build

import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.ResourcesTargetType
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.TargetBuilder

class DatanucleusBuilderService : BuilderService() {
    override fun getTargetTypes(): List<BuildTargetType<*>> {
        return JavaModuleBuildTargetType.ALL_TYPES + ResourcesTargetType.ALL_TYPES
    }

    override fun createModuleLevelBuilders(): List<ModuleLevelBuilder> {
        return listOf(DatanucleusModuleLevelBuilder())
    }

    override fun createBuilders(): List<TargetBuilder<*, *>> {
        return emptyList()
    }
}