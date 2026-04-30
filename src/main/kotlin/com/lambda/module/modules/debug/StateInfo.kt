/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.debug

import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.KeyCode
import net.minecraft.block.BlockState
import net.minecraft.state.property.Properties
import net.minecraft.state.property.Property
import net.minecraft.util.hit.BlockHitResult

object StateInfo : Module(
    name = "StateInfo",
    description = "Prints the target block's state into chat",
    tag = ModuleTag.DEBUG,
) {
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
