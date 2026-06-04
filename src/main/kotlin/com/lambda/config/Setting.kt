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

package com.lambda.config

import com.google.common.base.Defaults.defaultValue
import com.lambda.Lambda.mapper
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.CommandRegistry
import com.lambda.command.commands.ConfigCommand
import com.lambda.context.SafeContext
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.Describable
import com.lambda.util.Nameable
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.HoverEvents
import com.lambda.util.text.TextBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.highlighted
import com.lambda.util.text.hoverEvent
import com.lambda.util.text.literal
import net.minecraft.command.CommandRegistryAccess
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
 * val mode by setting("Mode", Modes.Freeze, { page == Page.Custom }, "The mode of the module.")
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
 * val mode = setting("Mode", Modes.Freeze, { page == Page.Custom }, "The mode of the module.")
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
 *         Log.info("Mode: ${mode.value}") // indirect access of the value
 *     }
 * }
 * ```
 *
 * @property defaultValue The default value of the setting.
 * @property type The type reflection of the setting.
 */
@Suppress("unused")
abstract class Setting<T>(
	override val name: String,
	override val description: String,
	var core: SettingCore<T>,
	val config: Config,
	val layer: SettingLayer.Single<*, *>,
	var visibility: () -> Boolean
) : Nameable, Describable {
	val originalCore = core
	var disabled = { false }

	open var value by this

	val listeners = mutableListOf<ValueListener<T>>()

	open val isModified get() = originalCore.value != originalCore.defaultValue

	operator fun getValue(thisRef: Any?, property: KProperty<*>) = core.value
	operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
		val oldValue = originalCore.value
		originalCore.value = newValue
		listeners.forEach {
			if (it.requiresValueChange && oldValue == newValue) return@forEach
			it.execute(oldValue, newValue)
		}
	}

	fun reset(silent: Boolean = false) {
		if (!silent && originalCore.value == originalCore.defaultValue) {
			ConfigCommand.info(notChangedMessage())
			return
		}
		if (!silent) ConfigCommand.info(resetMessage(value, originalCore.defaultValue))
		value = originalCore.defaultValue
	}

	fun restoreOriginalCore() {
		core = originalCore
	}

	abstract fun ImGuiBuilder.buildLayout()

	open fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(string("value as JSON")) { value ->
			executeWithResult {
				val valueString = value().value()
				val previous = originalCore.value
				try {
					this@Setting.value = mapper.readValue(valueString, this@Setting.value?.javaClass)
				} catch (_: Throwable) {
					return@executeWithResult failure("Failed to deserialize $valueString as a ${type::class.simpleName} for $name in ${config.name}.")
				}
				ConfigCommand.info(setMessage(previous, originalCore.value))
				return@executeWithResult success()
			}
		}
	}

	/**
	 * Will only register changes of the variable, not the content of the variable!
	 * E.g., if the variable is a list, it will only register if the list reference changes, not if the content of the list changes.
	 */
	@ConfigEntryD5l
	fun onValueChange(block: SafeContext.(from: T, to: T) -> Unit) = apply {
		listeners.add(ValueListener(true) { from, to ->
			runSafe {
				block(from, to)
			}
		})
	}

	@ConfigEntryD5l
	fun onValueChangeUnsafe(block: (from: T, to: T) -> Unit) = apply {
		listeners.add(ValueListener(true, block))
	}

	@ConfigEntryD5l
	fun onValueSet(block: (from: T, to: T) -> Unit) = apply {
		listeners.add(ValueListener(false, block))
	}

	@ConfigEntryD5l
	fun disabled(predicate: () -> Boolean) = apply {
		disabled = predicate
	}

	fun trySetValue(newValue: T) {
		if (newValue == originalCore.value) {
			ConfigCommand.info(notChangedMessage())
		} else {
			val previous = originalCore.value
			value = newValue
			ConfigCommand.info(setMessage(previous, newValue))
		}
	}

	fun setMessage(previousValue: T, newValue: T) = buildText {
		literal("Set ")
		changedMessage(previousValue, newValue)
		val commandPath = getConfigCommandPath().joinToString("->", postfix = "->").takeIf { it != "->" } ?: ""
		clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${ConfigCommand.commandName} reset ${config.commandName} $commandPath$commandName")) {
			hoverEvent(HoverEvents.showText(buildText {
				literal("Click to reset to default value ")
				highlighted(originalCore.defaultValue.toString())
			})) {
				highlighted(" [Reset]")
			}
		}
	}

	private fun resetMessage(previousValue: T, newValue: T) = buildText {
		literal("Reset ")
		changedMessage(previousValue, newValue)
	}

	private fun notChangedMessage() = buildText {
		literal("No changes made to ")
		highlighted(name)
		literal(" as it is already set to ")
		highlighted(originalCore.value.toString())
		literal(".")
	}

	private fun TextBuilder.changedMessage(previousValue: T, newValue: T) {
		highlighted(config.name)
		literal(" > ")
		highlighted(name)
		literal(" from ")
		highlighted(previousValue.toString())
		literal(" to ")
		highlighted(newValue.toString())
		literal(".")
		val commandPath = getConfigCommandPath().joinToString("->", postfix = "->").takeIf { it != "->" } ?: ""
		clickEvent(ClickEvents.suggestCommand("${CommandRegistry.prefix}${ConfigCommand.name} set ${config.commandName} $commandPath$commandName $previousValue")) {
			hoverEvent(HoverEvents.showText(buildText {
				literal("Click to undo to previous value ")
				highlighted(previousValue.toString())
			})) {
				highlighted(" [Undo]")
			}
		}
	}

	internal fun getConfigCommandPath(): Collection<String> =
		buildList {
			var current: SettingLayer.Multiple = layer.parent
			while (true) {
				current = current.parent ?: break
				if (current.multipleType == MultipleLayerType.Root) break
				add(current.commandName)
			}
		}.asReversed()

	override fun toString() = "Setting $name: $value"

	class ValueListener<T>(val requiresValueChange: Boolean, val execute: (from: T, to: T) -> Unit)
}

class SettingCore<T>(
	var defaultValue: T,
	var value: T = defaultValue,
)

@DslMarker
annotation class ConfigEntryD5l