package com.lambda.module

import com.lambda.Loadable
import com.lambda.module.ModuleRegistry.modules
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
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

    override fun load(): String {
        Reflections(
            ConfigurationBuilder()
                // Let's hope the maintainer of the library releases a new version soon
                // because this is horrible, it takes multiple SECONDS to scan the classpath,
                // and it's not even that big
                //
                // The culprit may be due to [ClasspathHelper.forClassLoader()] loading
                // the classes from the main thread while we are in a different thread.
                // If this is the case I wish the maintainer a very bad day.
                .addUrls(ClasspathHelper.forJavaClassPath())
                .addUrls(ClasspathHelper.forClassLoader())
                .filterInputsBy { it.contains("lambda") }
                .forPackage("com.lambda.module.modules")
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
