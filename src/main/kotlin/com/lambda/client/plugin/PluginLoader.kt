package com.lambda.client.plugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lambda.client.LambdaMod
import com.lambda.client.plugin.api.Plugin
import com.lambda.commons.interfaces.Nameable
import com.lambda.commons.utils.ClassUtils.instance
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Type
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.jar.JarFile

class PluginLoader(
    val file: File
) : Nameable {

    override val name: String get() = info.name

    private val url = file.toURI().toURL()
    private val loader = PluginClassLoader(JarFile(file), this.javaClass.classLoader)
    val info: PluginInfo = loader.getResourceAsStream("plugin_info.json")?.let {
        PluginInfo.fromStream(it)
    } ?: throw FileNotFoundException("plugin_info.json not found in jar ${file.name}!")

    init {
        // This will trigger the null checks in PluginInfo
        // In order to make sure all required infos are present
        info.toString()
    }

    fun verify(): Boolean {
        val bytes = file.inputStream().use {
            it.readBytes()
        }

        val result = StringBuilder().run {
            sha256.digest(bytes).forEach {
                append(String.format("%02x", it))
            }

            toString()
        }

        // ToDo: Do no spam when in Lambda menu
//        LambdaMod.LOG.info("SHA-256 checksum for ${file.name}: $result")

        return checksumSets.contains(result)
    }

    fun load(): Plugin {
        if (LambdaMod.ready && !info.hotReload) {
            throw IllegalAccessException("Plugin $this cannot be hot reloaded!")
        }

        val clazz = Class.forName(info.mainClass, true, loader)
        val obj = try {
            clazz.instance
        } catch (e: NoSuchFieldException) {
            clazz.newInstance()
        }

        println("plugin is ${obj.javaClass.superclass}")

        val plugin = obj as Plugin
         //   ?: throw IllegalArgumentException("The specific main class ${info.mainClass} is not a valid plugin main class")

        plugin.setInfo(info)
        return plugin
    }

    fun close() {
        loader.close()
    }

    override fun toString(): String {
        return "${runCatching { info.name }.getOrDefault("Unknown Plugin")}(${file.name})"
    }

    private companion object {
        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
        val type: Type = object : TypeToken<HashSet<String>>() {}.type
        val checksumSets = runCatching<HashSet<String>> {
            Gson().fromJson(File("verify.json").bufferedReader(), type)
        }.getOrElse { HashSet() }
    }
}