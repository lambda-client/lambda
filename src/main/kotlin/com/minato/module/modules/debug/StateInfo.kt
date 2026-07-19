
package com.minato.module.modules.debug

import com.minato.config.settings.complex.KeybindSetting.Companion.onPress
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.BlockUtils.blockState
import com.minato.util.CommunicationUtils.info
import com.minato.util.KeyCode
import net.minecraft.block.BlockState
import net.minecraft.state.property.Properties
import net.minecraft.state.property.Property
import net.minecraft.util.hit.BlockHitResult

object StateInfo : Module(
    name = "StateInfo",
    description = "Prints the target block's state into chat",
    tag = ModuleTag.DEBUG,
) {
    @Suppress("unused")
    private val printBind by setting("Print", KeyCode.Unbound, "The bind used to print the info to chat")
        .onPress {
            val crosshair = mc.crosshairTarget ?: return@onPress
            if (crosshair !is BlockHitResult) return@onPress
            info(blockState(crosshair.blockPos).betterToString())
        }

    val propertyFields = Properties::class.java.declaredFields
        .filter { Property::class.java.isAssignableFrom(it.type) }
        .associateBy { it.get(null) as Property<*> }

    init {
        onEnable {
            val crosshair = mc.crosshairTarget ?: return@onEnable
            if (crosshair !is BlockHitResult) return@onEnable
            info(blockState(crosshair.blockPos).betterToString())
        }
    }

    private fun BlockState.betterToString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(this.owner.toString() + "\n")

        if (entries.isNotEmpty()) {
            stringBuilder.append("      [\n")

            stringBuilder.append(
                entries.entries.joinToString("\n") { (property, value) ->
                    val fieldName = propertyFields[property]?.name ?: property.toString()
                    "          $fieldName = ${nameValue(property, value)}"
                }
            )

            stringBuilder.append("\n      ]")
        }

        return stringBuilder.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Comparable<T>?> nameValue(property: Property<T>, value: Comparable<*>): String {
        return property.name(value as T)
    }
}
