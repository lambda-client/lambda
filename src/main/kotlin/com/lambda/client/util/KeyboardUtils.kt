package com.lambda.client.util

import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.capitalize
import com.lambda.client.util.text.formatValue
import org.lwjgl.input.Keyboard

object KeyboardUtils {
    val allKeys = IntArray(Keyboard.KEYBOARD_SIZE) { it }

    private val displayNames = Array(Keyboard.KEYBOARD_SIZE) { name ->
        Keyboard.getKeyName(name)?.lowercase()?.capitalize()
    }

    private val keyMap: Map<String, Int> = HashMap<String, Int>().apply {
        // LWJGL names
        for (key in 0 until Keyboard.KEYBOARD_SIZE) {
            val name = Keyboard.getKeyName(key) ?: continue
            this[name.lowercase()] = key
        }

        // Display names
        for ((index, name) in displayNames.withIndex()) {
            name?.let {
                this[it.lowercase()] = index
            }
        }

        // Modifier names
        this["ctrl"] = Keyboard.KEY_LCONTROL
        this["alt"] = Keyboard.KEY_LMENU
        this["shift"] = Keyboard.KEY_LSHIFT
        this["meta"] = Keyboard.KEY_LMETA
    }

    fun sendUnknownKeyError(bind: String) {
        MessageSendHelper.sendErrorMessage("Unknown key [${formatValue(bind)}]! " +
            "Right shift is ${formatValue("rshift")}, " +
            "left control is ${formatValue("lcontrol")}, " +
            "and ` is ${formatValue("grave")}. " +
            "You cannot bind the ${formatValue("meta")} key."
        )
    }

    fun getKey(keyName: String): Int {
        return keyMap[keyName.lowercase()] ?: 0
    }

    fun getKeyName(keycode: Int): String? {
        return Keyboard.getKeyName(keycode)
    }

    fun getDisplayName(keycode: Int): String? {
        return displayNames.getOrNull(keycode)
    }
}