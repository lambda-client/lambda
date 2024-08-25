package com.lambda.module

import com.lambda.core.Loadable
import com.lambda.util.reflections.getInstances
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * The [ModuleRegistry] object is responsible for managing all [Module] instances in the system.
 */
object ModuleRegistry : Loadable {
    val modules = getInstances<Module> {
        forPackages("com.lambda.module.modules"); filterInputsBy { it.contains("com.lambda") }
    }.toMutableList()

    val moduleNames: Set<String>
        get() = modules.map { it.name }.toSet()

    override fun load(): String {
        return "Registered ${modules.size} modules with ${modules.sumOf { it.settings.size }} settings"
    }
}
