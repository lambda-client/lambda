package com.lambda.plugin

import java.io.*
import java.util.jar.JarFile


class PluginClassLoader(
    private val jarFile: JarFile,
    parent: ClassLoader,
) : ClassLoader(parent), Closeable {
    private val classes = mutableMapOf<String, ByteArray>()
    private val resources = mutableMapOf<String, ByteArray>()

    val mixinFileName: String?
        get() = resources.keys.firstOrNull { it.endsWith(".mixins.json") }

    val accessWidenerFileName: String?
        get() = resources.keys.firstOrNull { it.endsWith(".accesswidener") }

    public override fun findClass(name: String): Class<*> {
        val clazz = classes[name] ?: return super.findClass(name)
        return defineClass(name, clazz, 0, clazz.size)
    }

    override fun close() {
        jarFile.close()
    }

    init {
        jarFile.entries().asSequence().forEach { entry ->
            if (entry.isDirectory) return@forEach

            val bytes = jarFile.getInputStream(entry).use { it.readBytes() }
            if (entry.name.endsWith(".class")) {
                classes[
                    entry.name.removeSuffix(".class")
                        .replace('/', '.')
                ] = bytes
            } else {
                resources[entry.name] = bytes
            }
        }
    }
}
