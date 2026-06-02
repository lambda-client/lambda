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

import com.lambda.config.Config.BlockLayer
import com.lambda.config.Config.SettingLayer
import com.lambda.context.SafeContext
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun <T : Config> T.withEdits(edits: context(EditContext.ConfigEditContext) T.() -> Unit): T {
	with(EditContext.ConfigEditContext(this)) { edits() }
	return this
}

@SettingEditorDsl
context(c: Config)
fun <T : SettingBlock> SettingBlockWrapper<T>.withEdits(edits: context(EditContext.BlockEditContext) T.() -> Unit): SettingBlockWrapper<T> {
	with(EditContext.BlockEditContext(c, this)) { this@withEdits.settingBlock.edits() }
	return this
}

@SettingEditorDsl
fun <T : SettingBlock> SettingBlockWrapper<T>.withEdits(c: Config, edits: context(EditContext.BlockEditContext) T.() -> Unit): SettingBlockWrapper<T> {
	with(EditContext.BlockEditContext(c, this)) { this@withEdits.settingBlock.edits() }
	return this
}

sealed class EditContext(internal val c: Config) {
	class ConfigEditContext internal constructor(c: Config) : EditContext(c)
	class BlockEditContext internal constructor(c: Config, internal val block: SettingBlockWrapper<*>) : EditContext(c)
}

object ConfigEditor {
	@SettingEditorDsl
	context(editContext: EditContext.ConfigEditContext)
	fun forEachSetting(block: BasicEditBuilder.() -> Unit) {
		val settings = mutableListOf<Setting<*>>()
		editContext.c.forEachSetting { _, single -> settings.add(single.setting) }
		BasicEditBuilder(settings).apply(block)
	}

	@SettingEditorDsl
	context(editContext: EditContext.ConfigEditContext)
	fun hideAllBlocksExcept(vararg except: SettingBlockProperty<SettingBlock>, recursive: Boolean = true) =
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
	fun hideAllBlocksExcept(vararg except: SettingBlockProperty<SettingBlock>, recursive: Boolean = true) =
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
	fun <T : SettingBlock> hideBlock(settingBlock: SettingBlockProperty<T>) {
		settingBlock.settingBlock.layer.settingLayers.forEach(::hide)
	}

	@SettingEditorDsl
	context(_: EditContext)
	fun hideBlocks(vararg settingBlocks: SettingBlockProperty<SettingBlock>) =
		settingBlocks.forEach { hideBlock(it) }

	@SettingEditorDsl
	context(_: EditContext)
	fun <T : SettingBlock> hideBlockExcept(settingBlock: SettingBlockProperty<T>, vararg except: SettingProperty<Any>, recursive: Boolean = true) {
		val exceptSettings = except.map { it.setting }
		fun processBlock(blockLayer: BlockLayer) {
			blockLayer.settingLayers.forEach { single ->
				if (single.setting !in exceptSettings) hide(single)
			}
			if (recursive) blockLayer.layers.forEach(::processBlock)
		}
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

	private fun hideAllBlocksExcept(root: BlockLayer, vararg except: SettingBlockProperty<SettingBlock>, recursive: Boolean) {
		val exceptBlocks = except.map { it.settingBlock.layer }
		fun processBlock(blockLayer: BlockLayer) {
			val unProtected = blockLayer !in exceptBlocks
			if (unProtected) blockLayer.settingLayers.forEach(::hide)
			if (unProtected || !recursive) blockLayer.layers.forEach(::processBlock)
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

	private val <T : SettingBlock> SettingBlockProperty<T>.settingBlock
		get() = this.delegate as? SettingBlockWrapper<T>
			?: throw IllegalStateException("SettingBlock delegate did not match the given type")
}
