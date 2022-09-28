package com.lambda.client.setting.groups

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.lambda.client.LambdaMod
import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.setting.settings.AbstractSetting

open class SettingGroup(
    override val name: String
) : Nameable {

    /** Settings in this group */
    protected val subSetting = LinkedHashMap<String, AbstractSetting<*>>()


    /**
     * Get a copy of the list of settings in this group
     *
     * @return A copy of [subSetting]
     */
    fun getSettings() = subSetting.values.toList()

    /**
     * Adds a setting to this group
     *
     * @param S type of the setting
     * @param setting Setting to add
     *
     * @return [setting]
     */
    open fun <S : AbstractSetting<*>> addSetting(setting: S): S {
        subSetting[setting.name.lowercase()] = setting
        return setting
    }


    /**
     * Writes setting values to a [JsonObject]
     *
     * @return [JsonObject] contains all the setting values
     */
    open fun write(): JsonObject = JsonObject().apply {
        add("name", JsonPrimitive(name))

        if (subSetting.isNotEmpty()) {
            add("settings", JsonObject().apply {
                for (setting in subSetting.values) {
                    add(setting.name.toJsonName(), setting.write())
                }
            })
        }
    }

    /**
     * Read setting values from a [JsonObject]
     *
     * @param jsonObject [JsonObject] to read from
     */
    open fun read(jsonObject: JsonObject?) {
        if (subSetting.isNotEmpty()) {
            (jsonObject?.get("settings") as? JsonObject)?.also {
                for (setting in subSetting.values) {
                    try {
                        val value = it.get(setting.name.toJsonName())
                        setting.read(value)
                    } catch (e: Exception) {
                        LambdaMod.LOG.warn("Failed loading setting ${setting.name} at $name", e)
                    }
                }
            }
        }
    }

    private fun String.toJsonName() =
        this.replace(' ', '_')
            .lowercase()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SettingGroup

        if (name != other.name) return false
        if (subSetting != other.subSetting) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + subSetting.hashCode()
        return result
    }

}