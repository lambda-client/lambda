
package com.minato.config.entries

import com.minato.Minato.mapper
import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.CommandRegistry
import com.minato.command.commands.ConfigCommand
import com.minato.config.Config
import com.minato.config.ConfigEntry
import com.minato.config.EntryCore
import com.minato.config.EntryLayer
import com.minato.config.MultipleLayerType
import com.minato.context.SafeContext
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils.info
import com.minato.util.Describable
import com.minato.util.Nameable
import com.minato.util.extension.CommandBuilder
import com.minato.util.text.ClickEvents
import com.minato.util.text.HoverEvents
import com.minato.util.text.TextBuilder
import com.minato.util.text.buildText
import com.minato.util.text.clickEvent
import com.minato.util.text.highlighted
import com.minato.util.text.hoverEvent
import com.minato.util.text.literal
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
	defaultValue: T,
	value: T,
	override val layer: SettingEntryLayer<*, T>,
	override val config: Config,
	var visibility: () -> Boolean
) : ConfigEntry<T>, Nameable, Describable {
	constructor(
		name: String,
		description: String,
		defaultValue: T,
		layer: SettingEntryLayer<*, T>,
		config: Config,
		visibility: () -> Boolean
	) : this(name, description, defaultValue, defaultValue, layer, config, visibility)

	override var core = EntryCore(defaultValue, value)
	override val originalCore = core
	var disabled = { false }

	open var value by this

	val listeners = mutableListOf<ValueListener<T>>()

	override val isModified get() = originalCore.value != originalCore.defaultValue

	override operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
		val oldValue = core.value
		core.value = newValue
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
			var current: EntryLayer.Multiple<Setting<*>> = layer.parent
			while (true) {
				current = current.parent ?: break
				if (current.multipleType == MultipleLayerType.Root) break
				add(current.commandName)
			}
		}.asReversed()

	override fun toString() = "Setting $name: $value"

	companion object {
		/**
		 * Will only register changes of the variable, not the content of the variable!
		 * E.g., if the variable is a list, it will only register if the list reference changes, not if the content of the list changes.
		 */
		@ConfigEntryDsl
		fun <S : Setting<T>, T> S.onValueChange(block: SafeContext.(from: T, to: T) -> Unit) =
			apply {
				listeners.add(ValueListener(true) { from, to ->
					runSafe { block(from, to) }
				})
			}

		@ConfigEntryDsl
		fun <S : Setting<T>, T> S.onValueChangeUnsafe(block: (from: T, to: T) -> Unit) =
			apply { listeners.add(ValueListener(true, block)) }

		@ConfigEntryDsl
		fun <S : Setting<T>, T> S.onValueSet(block: SafeContext.(from: T, to: T) -> Unit) =
			apply {
				listeners.add(ValueListener(false) { from, to ->
					runSafe { block(from, to) }
				})
			}

		@ConfigEntryDsl
		fun <S : Setting<T>, T> S.onValueSetUnsafe(block: (from: T, to: T) -> Unit) =
			apply { listeners.add(ValueListener(false, block)) }

		@ConfigEntryDsl
		fun <S : Setting<T>, T> S.disabled(predicate: () -> Boolean) =
			apply { disabled = predicate }
	}

	class ValueListener<T>(val requiresValueChange: Boolean, val execute: (from: T, to: T) -> Unit)
}

class SettingEntryLayer<T : Setting<U>, U>(
	override val parent: EntryLayer.Multiple<Setting<*>>,
	entrySupplier: (layer: SettingEntryLayer<T, U>) -> T
) : EntryLayer.Single<Setting<*>>() {
	override val entry = entrySupplier(this)
}

@DslMarker
annotation class ConfigEntryDsl