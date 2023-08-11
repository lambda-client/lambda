package com.lambda.client.setting.settings.impl.other

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.lambda.client.setting.settings.ImmutableSetting
import com.lambda.client.util.Bind
import com.lambda.client.util.KeyboardUtils
import java.util.*

class BindSetting(
    name: String,
    value: Bind,
    visibility: () -> Boolean = { true },
    description: String = "",
    formatter: (Bind) -> String = { b -> "$b"},
    ) : ImmutableSetting<Bind>(name, value, visibility, { _, input -> input }, description, formatter, unit = "") {

    override val defaultValue: Bind = Bind(TreeSet(value.modifierKeys), value.key, null)

    override fun resetValue() {
        value.setBind(defaultValue.modifierKeys, defaultValue.key)
    }

    override fun setValue(valueIn: String) {
        if (valueIn.equals("None", ignoreCase = true)) {
            value.clear()
            return
        }
        if (valueIn.startsWith("Mouse", ignoreCase = true)) {
            valueIn.split("Mouse").lastOrNull()?.toIntOrNull()?.let {
                value.setMouseBind(it)
            }
            return
        }

        val splitNames = valueIn.split('+')
        val lastKey = KeyboardUtils.getKey(splitNames.last())

        // Don't clear if the string is fucked
        if (lastKey !in 1..255) {
            println("Invalid last key")
            return
        }

        val modifierKeys = TreeSet(Bind.keyComparator)
        for (index in 0 until splitNames.size - 1) {
            val name = splitNames[index]
            val key = KeyboardUtils.getKey(name)

            if (key !in 1..255) continue
            modifierKeys.add(key)
        }

        value.setBind(modifierKeys, lastKey)
    }

    override fun write() = JsonPrimitive(value.toString())

    override fun read(jsonElement: JsonElement?) {
        setValue(jsonElement?.asString ?: "None")
    }

}
