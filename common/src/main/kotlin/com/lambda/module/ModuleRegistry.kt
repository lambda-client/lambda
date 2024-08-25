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
    private val extraPackages = mutableSetOf<String>()
    val modules = getInstances<Module> { forPackages("com.lambda.module.modules", *extraPackages.toTypedArray()) }

    val moduleNames: Set<String>
        get() = modules.map { it.name }.toSet()

    /**
     * Injects a package into the [ModuleRegistry] for scanning.
     *
     * @param packageName The package to inject into the [ModuleRegistry].
     */
    fun injectPath(packageName: String) = extraPackages.add(packageName)

    override fun load(): String {
        return "Registered ${modules.size} modules with ${modules.sumOf { it.settings.size }} settings"
    }
}
