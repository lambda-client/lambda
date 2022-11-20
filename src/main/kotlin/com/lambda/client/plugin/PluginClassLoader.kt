package com.lambda.client.plugin

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile

// A custom class loader that only opens the file while it is actually reading from it then closes the file so that it can be deleted / changed.
class PluginClassLoader(jar: JarFile, parent: ClassLoader) : ClassLoader(parent) {

    private val classes = HashMap<String, ByteArray>()
    private val resources = HashMap<String, ByteArray>()

    init {
        getClasses(jar)
        jar.close()
    }

    override fun getResourceAsStream(resName: String?): InputStream? {
        return resources[resName]?.inputStream()
    }

    public override fun findClass(name: String): Class<*> {
        var b = ByteArray(0)
        try {
            b = loadClassFromFile(name)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return defineClass(name, b, 0, b.size)
    }


    private fun loadClassFromFile(name: String): ByteArray {
        return classes[name] ?: throw ClassNotFoundException()
    }

    private fun getClasses(jar: JarFile) {
        for (entry in jar.entries()) {
            if (entry.name.endsWith(".class")) {
                classes[entry.name.removeSuffix(".class").replace("/", ".")] = getDataFromEntry(jar, entry)
            } else if (!entry.name.endsWith("/")) {
                resources[entry.name] = getDataFromEntry(jar, entry)
            }
        }
    }

    private fun getDataFromEntry(jar: JarFile, entry: JarEntry): ByteArray {
        val inputStream = jar.getInputStream(entry)
        val buffer: ByteArray
        val byteStream = ByteArrayOutputStream()
        var nextValue: Int
        try {
            while (true) {
                if (inputStream.read().also { nextValue = it } == -1) break
                byteStream.write(nextValue)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        buffer = byteStream.toByteArray()

        byteStream.close()
        inputStream.close()

        return buffer
    }

    fun close() {
        // everything has already been non locked, so just clear classes / resources

        classes.clear()
        resources.clear()
    }
}