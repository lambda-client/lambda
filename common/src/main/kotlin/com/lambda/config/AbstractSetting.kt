/*
 * Copyright 2024 Lambda
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

package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lambda.Lambda.LOG
import com.lambda.Lambda.gson
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.CommandRegistry
import com.lambda.command.commands.ConfigCommand
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.Nameable
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.*
import net.minecraft.command.CommandRegistryAccess
import java.lang.reflect.Type
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Represents a setting with a [defaultValue], [visibility] condition, and [description].
 * This setting is serializable ([Jsonable]) and has a [name].
 *
 * When the [value] is modified, all registered [listeners] are notified.
 * The [visibility] of the setting can be checked with the [isVisible] property.
 * The setting can be [reset] to its [defaultValue].
 *
 * Simple Usage:
 * ```kotlin
 * // this uses the delegate (by) association to access the setting value in the code directly.
 * val mode by setting("Mode", Modes.FREEZE, { page == Page.CUSTOM }, "The mode of the module.")
 *
 * init {
 *     listener<TickEvent.Pre> {
 *         LOG.info("Mode: $mode") // direct access of the value
 *     }
 * }
 * ```
 *
 * Advanced usage with listeners:
 * ```kotlin
 * // notice how this does not use the delegate (by) association, to access the setting object to register listeners.
 * val mode = setting("Mode", Modes.FREEZE, { page == Page.CUSTOM }, "The mode of the module.")
 *
 * init {
 *     mode.listener { from, to ->
 *        // Do something when the mode changes in a safe context
 *     }
 *     mode.unsafeListener { from, to ->
 *        // Do something when the mode changes in an unsafe context
 *     }
 *
 *     listener<TickEvent.Pre> {
 *         LOG.info("Mode: ${mode.value}") // indirect access of the value
 *     }
 * }
 * ```
 *
 * @property defaultValue The default value of the setting.
 * @property description A description of the setting.
 * @property type The type reflection of the setting.
 * @property visibility A function that determines whether the setting is visible.
 */
abstract class AbstractSetting<T : Any>(
    private val defaultValue: T,
    val type: Type,
    val description: String,
    val visibility: () -> Boolean,
) : Jsonable, Nameable {
    private val listeners = mutableListOf<ValueListener<T>>()

    var value: T by Delegates.observable(defaultValue) { _, from, to ->
        listeners.forEach {
            if (it.requiresValueChange && from == to) return@forEach
            it.execute(from, to)
        }
    }

    private val isVisible get() = visibility()
    val isModified get() = value != defaultValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value
    open operator fun setValue(thisRef: Any?, property: KProperty<*>, valueIn: T) {
        value = valueIn
    }

    override fun toJson(): JsonElement =
        gson.toJsonTree(value, type)

    override fun loadFromJson(serialized: JsonElement) {
        runCatching {
            value = gson.fromJson(serialized, type)
        }.onFailure {
            LOG.warn("Failed to load setting ${this.name} with value $serialized. Resetting to default value $defaultValue", it)
            value = defaultValue
        }
    }

    class ValueListener<T>(val requiresValueChange: Boolean, val execute: (from: T, to: T) -> Unit)

    /**
     * Will only register changes of the variable, not the content of the variable!
     * E.g., if the variable is a list, it will only register if the list reference changes, not if the content of the list changes.
     */
    fun onValueChange(block: SafeContext.(from: T, to: T) -> Unit) {
        listeners.add(ValueListener(true) { from, to ->
            runSafe {
                block(from, to)
            }
        })
    }

    fun onValueChangeUnsafe(block: (from: T, to: T) -> Unit) {
        listeners.add(ValueListener(true, block))
    }

    fun onValueSet(block: (from: T, to: T) -> Unit) {
        listeners.add(ValueListener(false, block))
    }

    fun reset() {
        if (value == defaultValue) {
            ConfigCommand.info(notChangedMessage())
            return
        }
        value = defaultValue
        ConfigCommand.info(resetMessage(defaultValue))
    }

    open fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(string("value as JSON")) { value ->
            executeWithResult {
                val valueString = value().value()
                val parsed = try {
                    JsonParser.parseString("\"$valueString\"")
                } catch (e: Exception) {
                    return@executeWithResult failure("$valueString is not a valid JSON string.")
                }
                val config = Configuration.configurableBySetting(this@AbstractSetting) ?: return@executeWithResult failure("No config found for $name.")
                val previous = this@AbstractSetting.value
                try {
                    loadFromJson(parsed)
                } catch (e: Exception) {
                    return@executeWithResult failure("Failed to load $valueString as a ${type::class.simpleName} for $name in ${config.name}.")
                }
                ConfigCommand.info(setMessage(previous))
                return@executeWithResult success()
            }
        }
    }

    fun trySetValue(newValue: T) {
        if (newValue == value) {
            ConfigCommand.info(notChangedMessage())
        } else {
            val previous = value
            value = newValue
            ConfigCommand.info(setMessage(previous))
        }
    }

    private fun setMessage(previousValue: T) = buildText {
        literal("Set ")
        changedMessage(previousValue)
        val config = Configuration.configurableBySetting(this@AbstractSetting) ?: return@buildText
        clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${ConfigCommand.name} reset ${config.commandName} $commandName")) {
            hoverEvent(HoverEvents.showText(buildText {
                literal("Click to reset to default value ")
                highlighted(defaultValue.toString())
            })) {
                highlighted(" [Reset]")
            }
        }
    }

    fun resetMessage(previousValue: T) = buildText {
        literal("Reset ")
        changedMessage(previousValue)
    }

    private fun notChangedMessage() = buildText {
        literal("No changes made to ")
        highlighted(name)
        literal(" as it is already set to ")
        highlighted(value.toString())
        literal(".")
    }

    private fun TextBuilder.changedMessage(previousValue: T) {
        val config = Configuration.configurableBySetting(this@AbstractSetting) ?: return
        highlighted(config.name)
        literal(" > ")
        highlighted(name)
        literal(" from ")
        highlighted(previousValue.toString())
        literal(" to ")
        highlighted(value.toString())
        literal(".")
        clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${ConfigCommand.name} set ${config.commandName} $commandName $previousValue")) {
            hoverEvent(HoverEvents.showText(buildText {
                literal("Click to undo to previous value ")
                highlighted(previousValue.toString())
            })) {
                highlighted(" [Undo]")
            }
        }
    }

    override fun toString() = "Setting $name: $value of type ${type.typeName}"

    override fun equals(other: Any?) = other is AbstractSetting<*> && name == other.name
    override fun hashCode() = name.hashCode()
}
