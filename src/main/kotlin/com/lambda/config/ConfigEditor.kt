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

@file:Suppress("unchecked_cast", "unused")

package com.lambda.config

import com.lambda.config.entries.Property
import com.lambda.config.entries.Setting
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.context.SafeContext
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@DslMarker
annotation class ConfigEditorD5l

@ConfigEditorD5l
fun <T : Config> T.withEdits(
	edits: context(EditContext.ConfigEditContext) T.() -> Unit
) = apply { with(EditContext.ConfigEditContext(this)) { edits() } }

@ConfigEditorD5l
context(c: Config)
fun <T : ConfigBlock> ConfigBlockWrapper<T>.withEdits(
	edits: context(EditContext.BlockEditContext) T.() -> Unit
) = apply { with(EditContext.BlockEditContext(c, this)) { this@withEdits.configBlock.edits() } }

@ConfigEditorD5l
fun <T : ConfigBlock> ConfigBlockWrapper<T>.withEdits(
	c: Config,
	edits: context(EditContext.BlockEditContext) T.() -> Unit
) = with(c) { withEdits(edits) }

sealed class EditContext(internal val c: Config) {
	class ConfigEditContext internal constructor(c: Config) : EditContext(c)
	class BlockEditContext internal constructor(c: Config, internal val block: ConfigBlockWrapper<*>) : EditContext(c)
}

object ConfigEditor {
	@ConfigEditorD5l
	context(editContext: EditContext.ConfigEditContext)
	fun forEachSetting(block: SettingEditBuilder<*>.() -> Unit) {
		val settings = buildList {
			editContext.c.settingLayers.forEachEntry { _, single -> add(single.entry as Setting<Any?>) }
		}
		SettingEditBuilder(settings).apply(block)
	}

	@ConfigEditorD5l
	context(editContext: EditContext.ConfigEditContext)
	fun forEachProperty(block: PropertyEditBuilder<*>.() -> Unit) {
		val properties = buildList {
			editContext.c.propertyLayers.forEachEntry { _, single -> add(single.entry as Property<Any?>) }
		}
		PropertyEditBuilder(properties).apply(block)
	}

	@ConfigEditorD5l
	context(editContext: EditContext.ConfigEditContext)
	fun hideAllExcept(
		vararg except: KProperty0<*>,
		recursive: Boolean = true
	) = internalHideAllExcept(editContext.c.configBlockLayers, *except, recursive = recursive)

	@ConfigEditorD5l
	context(editContext: EditContext.BlockEditContext)
	fun forEachSetting(block: SettingEditBuilder<*>.() -> Unit) {
		val settings = buildList {
			editContext.block.layer.forEachConfigBlock { _, single -> add(single.entry as Setting<Any?>) }
		}
		SettingEditBuilder(settings).apply(block)
	}

	@ConfigEditorD5l
	context(editContext: EditContext.BlockEditContext)
	fun forEachProperty(block: PropertyEditBuilder<*>.() -> Unit) {
		val properties = buildList {
			editContext.block.layer.forEachConfigBlock(onProperty = { _, single -> add(single.entry as Property<Any?>) })
		}
		PropertyEditBuilder(properties).apply(block)
	}

	@ConfigEditorD5l
	context(editContext: EditContext.BlockEditContext)
	fun hideAllExcept(
		vararg except: KProperty0<*>,
		recursive: Boolean = true
	) = internalHideAllExcept(editContext.block.layer, *except, recursive = recursive)

	@ConfigEditorD5l
	context(_: EditContext)
	fun <T> SettingProperty<T>.editSetting(edits: SettingEditBuilder<T>.() -> Unit) {
		SettingEditBuilder(listOf(setting)).edits()
	}

	@ConfigEditorD5l
	context(_: EditContext)
	fun <T> PropertyProperty<T>.editProperty(edits: PropertyEditBuilder<T>.() -> Unit) {
		PropertyEditBuilder(listOf(property)).edits()
	}

	@ConfigEditorD5l
	context(_: EditContext)
	fun editSettings(
		vararg settings: SettingProperty<*>,
		edits: SettingEditBuilder<*>.() -> Unit
	) = SettingEditBuilder(settings.map { it.setting }).apply(edits)

	@ConfigEditorD5l
	context(_: EditContext)
	fun editProperties(
		vararg properties: PropertyProperty<*>,
		edits: PropertyEditBuilder<*>.() -> Unit
	) = PropertyEditBuilder(properties.map { it.property }).apply(edits)

	@ConfigEditorD5l
	context(_: EditContext)
	fun <T> editTypedSettings(
		vararg settings: SettingProperty<T>,
		edits: SettingEditBuilder<T>.() -> Unit
	) = SettingEditBuilder(settings.map { it.setting }).apply(edits)

	@ConfigEditorD5l
	context(_: EditContext)
	fun <T> editTypedProperties(
		vararg properties: PropertyProperty<T>,
		edits: PropertyEditBuilder<T>.() -> Unit
	) = PropertyEditBuilder(properties.map { it.property }).apply(edits)

	@ConfigEditorD5l
	context(_: EditContext)
	fun hide(vararg entries: ConfigEntryProperty<*>) =
		internalHide(entries.map { it.configEntry.layer })

	@ConfigEditorD5l
	context(_: EditContext)
	fun hideBlock(configBlock: ConfigBlockProperty<ConfigBlock>) {
		configBlock.configBlock.layer.settingLayers.forEach(::internalHide)
	}

	@ConfigEditorD5l
	context(_: EditContext)
	fun hideBlocks(vararg configBlocks: ConfigBlockProperty<ConfigBlock>) =
		configBlocks.forEach { hideBlock(it) }

	@ConfigEditorD5l
	context(_: EditContext)
	fun hideBlockExcept(
		configBlock: ConfigBlockProperty<ConfigBlock>,
		vararg except: KProperty0<*>,
		recursive: Boolean = true
	) { internalHideAllExcept(configBlock.configBlock.layer, *except, recursive = recursive) }

	interface BasicEditBuilder {
		val entries: Collection<ConfigEntry<*>>

		@ConfigEditorD5l
		fun hide() {
			entries.forEach { internalHide(it.layer) }
		}
	}

	interface TypedEditBuilder<T> : BasicEditBuilder {
		override val entries: Collection<ConfigEntry<T>>

		@ConfigEditorD5l
		fun defaultValue(value: T) =
			entries.forEach {
				it.originalCore.value = value
				it.originalCore.defaultValue = value
			}
	}

	class SettingEditBuilder<T> internal constructor(
		override val entries: Collection<Setting<T>>
	) : TypedEditBuilder<T> {
		@ConfigEditorD5l
		fun visibility(visibility: (() -> Boolean) -> () -> Boolean) {
			entries.forEach {
				it.visibility = visibility(it.visibility)
			}
		}

		@ConfigEditorD5l
		fun onValueChange(block: SafeContext.(from: T, to: T) -> Unit) {
			entries.forEach { it.onValueChange(block) }
		}
	}

	class PropertyEditBuilder<T> internal constructor(
		override val entries: Collection<Property<T>>
	) : TypedEditBuilder<T> {
		@ConfigEditorD5l
		fun setEquals(equals: (T, T) -> Boolean) {
			entries.forEach { it.equals = equals }
		}
	}

	private fun internalHide(layers: Collection<EntryLayer<*>>) {
		layers.forEach(::internalHide)
	}

	private fun internalHide(layer: EntryLayer<*>) {
		layer.parent?.layers?.let { parentLayers ->
			parentLayers.remove(layer)
			if (parentLayers.isEmpty())
				parentLayers.remove(layer)
		}
	}

	private fun internalHideAllExcept(
		root: ConfigBlockLayer,
		vararg except: KProperty0<*>,
		recursive: Boolean
	) {
		val exceptEntries = except.map { it.delegate }
		if (root.blockWrapper in exceptEntries) return
		fun processBlock(blockLayer: ConfigBlockLayer) {
			blockLayer.settingLayers.forEach { single ->
				if (single.entry !in exceptEntries) internalHide(single)
			}
			blockLayer.propertyLayers.forEach { single ->
				if (single.entry !in exceptEntries) internalHide(single)
			}
			if (recursive) blockLayer.layers.forEach { blockLayer ->
				if (blockLayer.blockWrapper !in exceptEntries) processBlock(blockLayer)
			}
		}
		processBlock(root)
	}

	private val <T> SettingProperty<T>.setting
		get() = this.delegate as? Setting<T>
			?: throw IllegalStateException("Setting delegate did not match the given type")

	private val <T> PropertyProperty<T>.property
		get() = this.delegate as? Property<T>
			?: throw IllegalStateException("Property delegate did not match the given type")

	private val <T> ConfigEntryProperty<T>.configEntry
		get() = this.delegate as? ConfigEntry<T>
			?: throw IllegalStateException("ConfigEntry delegate did not match the given type")

	private val <T : ConfigBlock> ConfigBlockProperty<T>.configBlock
		get() = this.delegate as? ConfigBlockWrapper<T>
			?: throw IllegalStateException("ConfigBlock delegate did not match the given type")

	private val KProperty0<*>.delegate
		get() = try {
			apply { isAccessible = true }.getDelegate()
		} catch (e: Exception) {
			throw IllegalStateException("Could not access delegate for property $name", e)
		}

	private typealias SettingProperty<T> = ConfigEntryProperty<T>
	private typealias PropertyProperty<T> = ConfigEntryProperty<T>
	private typealias ConfigEntryProperty<T> = KProperty0<T>
	private typealias ConfigBlockProperty<T> = KProperty0<T>
}
