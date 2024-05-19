package com.lambda.module

import com.lambda.core.Loadable
import com.lambda.module.ModuleRegistry.modules
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * The [ModuleRegistry] object is responsible for managing all [Module] instances in the system.
 *
 * @property modules A set of all [Module] instances in the system.
 */
object ModuleRegistry : Loadable {
    val modules = mutableSetOf<Module>()

    val moduleNames: Set<String>
        get() = modules.map { it.name }.toSet()

    private val paths = mutableSetOf("com.lambda.module.modules")

    fun injectPath(path: String) = paths.add(path)

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                .forPackages(*paths.toTypedArray())
                .addScanners(Scanners.SubTypes)
        ).getSubTypesOf(Module::class.java).forEach { moduleClass ->
            moduleClass.declaredFields.find {
                it.name == "INSTANCE"
            }?.apply {
                isAccessible = true
                (get(null) as? Module)?.let { module ->
                    modules.add(module)
                }
            }
        }

        return "Registered ${modules.size} modules with ${modules.sumOf { it.settings.size }} settings"
    }
}
