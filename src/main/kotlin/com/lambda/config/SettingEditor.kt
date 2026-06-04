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

import com.lambda.context.SafeContext
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun <T : Config> T.withEdits(
	edits: context(EditContext.ConfigEditContext) T.() -> Unit
) = apply { with(EditContext.ConfigEditContext(this)) { edits() } }

@SettingEditorDsl
context(c: Config)
fun <T : ConfigBlock> ConfigBlockWrapper<T>.withEdits(
	edits: context(EditContext.BlockEditContext) T.() -> Unit
) = apply { with(EditContext.BlockEditContext(c, this)) { this@withEdits.settingBlock.edits() } }

@SettingEditorDsl
fun <T : ConfigBlock> ConfigBlockWrapper<T>.withEdits(
	c: Config,
	edits: context(EditContext.BlockEditContext) T.() -> Unit
) = with(c) { withEdits(edits) }

sealed class EditContext(internal val c: Config) {
	class ConfigEditContext internal constructor(c: Config) : EditContext(c)
	class BlockEditContext internal constructor(c: Config, internal val block: ConfigBlockWrapper<*>) : EditContext(c)
}

object SettingEditor {
	@SettingEditorDsl
	context(editContext: EditContext.ConfigEditContext)
	fun forEachSetting(block: BasicEditBuilder.() -> Unit) {
		val settings = mutableListOf<Setting<*>>()
		editContext.c.forEachSetting { _, single -> settings.add(single.setting) }
		BasicEditBuilder(settings).apply(block)
	}

	@SettingEditorDsl
	context(editContext: EditContext.ConfigEditContext)
	fun hideAllBlocksExcept(vararg except: SettingBlockProperty<ConfigBlock>, recursive: Boolean = true) =
		hideAllBlocksExcept(editContext.c.settingBlockLayers, *except, recursive = recursive)

	@SettingEditorDsl
	context(editContext: EditContext.BlockEditContext)
	fun forEachSetting(block: BasicEditBuilder.() -> Unit) {
		val settings = mutableListOf<Setting<*>>()
		editContext.c.forEachSettingBlock(editContext.block.layer) { _, single -> settings.add(single.setting) }
		BasicEditBuilder(settings).apply(block)
	}

	@SettingEditorDsl
	context(editContext: EditContext.BlockEditContext)
	fun hideAllBlocksExcept(
		vararg except: SettingBlockProperty<ConfigBlock>,
		recursive: Boolean = true
	) =
		hideAllBlocksExcept(editContext.block.layer, *except, recursive = recursive)

	@SettingEditorDsl
	context(_: EditContext)
	fun <T : Any> SettingProperty<T>.edit(edits: TypedEditBuilder<T>.() -> Unit) {
		TypedEditBuilder(listOf(setting)).edits()
	}

	@SettingEditorDsl
	context(_: EditContext)
	fun edit(
		vararg settings: SettingProperty<Any>,
		edits: BasicEditBuilder.() -> Unit
	) = BasicEditBuilder(settings.map { it.setting }).apply(edits)

	@SettingEditorDsl
	context(_: EditContext)
	fun <T : Any> editTyped(
		vararg settings: SettingProperty<T>,
		edits: TypedEditBuilder<T>.() -> Unit
	) = TypedEditBuilder(settings.map { it.setting }).apply(edits)

	@SettingEditorDsl
	context(_: EditContext)
	fun hide(vararg settings: SettingProperty<Any>) =
		hide(settings.map { it.setting.layer })

	@SettingEditorDsl
	context(_: EditContext)
	fun <T : ConfigBlock> hideBlock(settingBlock: SettingBlockProperty<T>) {
		settingBlock.configBlock.layer.settingLayers.forEach(::hide)
	}

	@SettingEditorDsl
	context(_: EditContext)
	fun hideBlocks(vararg configBlocks: SettingBlockProperty<ConfigBlock>) =
		configBlocks.forEach { hideBlock(it) }

	@SettingEditorDsl
	context(_: EditContext)
	fun <T : ConfigBlock> hideBlockExcept(
		settingBlock: SettingBlockProperty<T>,
		vararg except: SettingProperty<Any>,
		recursive: Boolean = true
	) {
		val exceptSettings = except.map { it.setting }
		fun processBlock(blockLayer: ConfigBlockLayer) {
			blockLayer.settingLayers.forEach { single ->
				if (single.setting !in exceptSettings) hide(single)
			}
			if (recursive) blockLayer.layers.forEach(::processBlock)
		}
		processBlock(settingBlock.configBlock.layer)
	}

	open class BasicEditBuilder internal constructor(
		open val settings: Collection<Setting<*>>
	) {
		@SettingEditorDsl
		fun hide() {
			settings.forEach {
				hide(it.layer)
			}
		}

		@SettingEditorDsl
		fun visibility(visibility: (() -> Boolean) -> () -> Boolean) {
			settings.forEach {
				it.visibility = visibility(it.visibility)
			}
		}

		@SettingEditorDsl
		fun onValueChange(block: SafeContext.(from: Any?, to: Any?) -> Unit) {
			settings.forEach { it.onValueChange(block) }
		}
	}

	class TypedEditBuilder<T : Any> internal constructor(
		override val settings: Collection<Setting<T>>
	) : BasicEditBuilder(settings) {
		@SettingEditorDsl
		fun defaultValue(value: T) =
			settings.forEach {
				it.originalCore.value = value
				it.originalCore.defaultValue = value
			}
	}

	private fun hide(layers: Collection<SettingLayer.Single<*, *>>) {
		layers.forEach(::hide)
	}

	private fun hide(layer: SettingLayer.Single<*, *>) {
		val parentLayer = layer.parent
		parentLayer.layers.remove(layer)
		if (parentLayer.layers.isEmpty())
			parentLayer.parent?.layers?.remove(parentLayer)
	}

	private fun hideAllBlocksExcept(
		root: ConfigBlockLayer,
		vararg except: SettingBlockProperty<ConfigBlock>,
		recursive: Boolean
	) {
		val exceptBlocks = except.map { it.configBlock.layer }
		fun processBlock(settingBlockLayer: ConfigBlockLayer) {
			val unProtected = settingBlockLayer !in exceptBlocks
			if (unProtected) settingBlockLayer.settingLayers.forEach(::hide)
			if (unProtected || !recursive) settingBlockLayer.layers.forEach(::processBlock)
		}
		processBlock(root)
	}

	private typealias Property<T> = KProperty0<T>
	private typealias SettingProperty<T> = KProperty0<T>
	private typealias SettingBlockProperty<T> = KProperty0<T>

	private val Property<*>.delegate
		get() = try {
			apply { isAccessible = true }.getDelegate()
		} catch (e: Exception) {
			throw IllegalStateException("Could not access delegate for property $name", e)
		}

	private val <T : Any> SettingProperty<T>.setting
		get() = this.delegate as? Setting<T>
			?: throw IllegalStateException("Setting delegate did not match the given type")

	private val <T : ConfigBlock> SettingBlockProperty<T>.configBlock
		get() = this.delegate as? ConfigBlockWrapper<T>
			?: throw IllegalStateException("SettingBlock delegate did not match the given type")
}
