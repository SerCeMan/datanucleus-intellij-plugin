package me.serce.datanucleus

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project

class DNProjectComponent(project: Project) : AbstractProjectComponent(project) {
    override fun getComponentName() = "Datanucleus Enhancer"
}
