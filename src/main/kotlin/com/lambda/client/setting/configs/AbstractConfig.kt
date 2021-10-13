package com.lambda.client.setting.configs

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.setting.groups.SettingGroup
import com.lambda.client.setting.groups.SettingMultiGroup
import com.lambda.client.setting.settings.AbstractSetting
import com.lambda.client.setting.settings.SettingRegister
import com.lambda.client.util.ConfigUtils
import java.io.File

abstract class AbstractConfig<T : Any>(
    name: String,
    val filePath: String
) : SettingMultiGroup(name), IConfig, SettingRegister<T> {

    override val file get() = File("$filePath$name.json")
    override val backup get() = File("$filePath$name.bak")

    final override fun <S : AbstractSetting<*>> T.setting(setting: S): S {
        addSettingToConfig(this, setting)
        return setting
    }

    abstract fun addSettingToConfig(owner: T, setting: AbstractSetting<*>)

    abstract fun getSettings(owner: T): List<AbstractSetting<*>>

    override fun save() {
        File(filePath).run {
            if (!exists()) mkdirs()
        }

        saveToFile(this, file, backup)
    }

    override fun load() {
        try {
            loadFromFile(this, file)
        } catch (e: Exception) {
            LambdaMod.LOG.warn("Failed to load latest, loading backup.")
            loadFromFile(this, backup)
        }
    }

    /**
     * Save a group to a file
     *
     * @param group Group to save
     * @param file Main file of [group]'s json
     * @param backup Backup file of [group]'s json
     */
    protected fun saveToFile(group: SettingGroup, file: File, backup: File) {
        ConfigUtils.fixEmptyJson(file)
        ConfigUtils.fixEmptyJson(backup)
        if (file.exists()) file.copyTo(backup, true)

        file.bufferedWriter().use {
            gson.toJson(group.write(), it)
        }
    }

    /**
     * Load settings values of a group
     *
     * @param group Group to load
     * @param file file of [group]'s json
     */
    protected fun loadFromFile(group: SettingGroup, file: File) {
        ConfigUtils.fixEmptyJson(file)

        val jsonObject = file.bufferedReader().use {
            parser.parse(it).asJsonObject
        }
        group.read(jsonObject)
    }

    /**
     * Contains a gson object and a parser object
     */
    protected companion object {
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        val parser = JsonParser()
    }

}