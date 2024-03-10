package com.lambda.module

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

object ModuleRegistry {
    private val modules = mutableSetOf<Module>()

    init {
        unsafeListener<ClientEvent.Startup> {
            Reflections(
                ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage("com.lambda.module.modules"))
                    .setScanners(SubTypesScanner()) // ToDo: Deprecated, use Scanners.SubTypes instead
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