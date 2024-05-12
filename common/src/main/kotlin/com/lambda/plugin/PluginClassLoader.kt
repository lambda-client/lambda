package com.lambda.plugin

import java.util.jar.JarFile

class PluginClassLoader(
    private val jarFile: JarFile,
    parent: ClassLoader,
) : ClassLoader(parent) {
    private val classes = mutableMapOf<String, ByteArray>()
    private val resources = mutableMapOf<String, ByteArray>()

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

    public override fun findClass(name: String): Class<*> {
        val clazz = classes[name] ?: return parent.loadClass(name)
        return defineClass(name, clazz, 0, clazz.size)
    }

    fun close() {
        jarFile.close()
    }
}
