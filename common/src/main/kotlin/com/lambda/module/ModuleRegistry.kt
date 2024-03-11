package com.lambda.module

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.util.Eager
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

/**
 * The [ModuleRegistry] object is responsible for managing all [Module] instances in the system.
 *
 * @property modules A set of all [Module] instances in the system.
 */
@Eager
object ModuleRegistry {
    private val modules = mutableSetOf<Module>()

    init {
        unsafeListener<ClientEvent.Startup> {
            Reflections(
                ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage("com.lambda.module.modules"))
                    .setScanners(Scanners.SubTypes)
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
        }
    }
}