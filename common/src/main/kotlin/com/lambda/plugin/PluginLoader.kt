package com.lambda.plugin

import java.io.*
import java.util.jar.JarFile

/**
 * A class for loading classes from a JAR file.
 *
 * @property file The JAR file from which to load classes
 * @property parent The parent ClassLoader to delegate to.
 */
internal class PluginLoader(
    private val file: JarFile,
    parent: ClassLoader,
) : ClassLoader(parent), Closeable {
    private val classes = mutableMapOf<String, ByteArray>()

    init {
        file.entries().asSequence().forEach { entry ->
            if (entry.isDirectory) return@forEach

            val bytes = file.getInputStream(entry).use { it.readBytes() }

            if (entry.name.endsWith(".class")) {
                classes[
                    entry.name.removeSuffix(".class")
                        .replace('/', '.')
                ] = bytes
            }
        }
    }

    /**
     * Loads the class with the specified name.
     *
     * @param name The binary name of the class.
     * @return The resulting class object.
     */
    public override fun findClass(name: String): Class<*> {
        val clazz = classes[name] ?: throw ClassNotFoundException(name)

        return defineClass(name, clazz, 0, clazz.size)
    }

    /**
     * Closes the JAR file.
     */
    override fun close() { file.close() }
}
