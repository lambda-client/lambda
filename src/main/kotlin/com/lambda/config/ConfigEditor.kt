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
import net.minecraft.client.toast.SystemToast.hide
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun Config.applyEdits(edits: ConfigEditor.() -> Unit) {
	ConfigEditor(this).apply(edits)
}

open class ConfigEditor(val c: Config) {
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
		get() = this.delegate as? Setting<SettingCore<T>, T>
			?: throw IllegalStateException("Setting delegate did not match the given type")

	private val <T : SettingBlock> SettingBlockProperty<T>.settingBlock
		get() = this.delegate as? SettingBlockWrapper<T>
			?: throw IllegalStateException("SettingBlock delegate did not match the given type")

	private fun <T : Any> SettingProperty<T>.settingCore() = setting.core

	@SettingEditorDsl
	fun <T : Any> SettingProperty<T>.edit(edits: TypedEditBuilder<T>.(SettingCore<T>) -> Unit) {
		val delegate = setting
		TypedEditBuilder(c, listOf(delegate)).edits(delegate.core)
	}

	@SettingEditorDsl
	fun edit(
		vararg settings: SettingProperty<Any>,
		edits: BasicEditBuilder.() -> Unit
	) = BasicEditBuilder(c, settings.map { it.setting }).apply(edits)

	@SettingEditorDsl
	fun <T : Any> editTyped(
		vararg settings: SettingProperty<T>,
		edits: TypedEditBuilder<T>.() -> Unit
	) = TypedEditBuilder(c, settings.map { it.setting }).apply(edits)

	@SettingEditorDsl
	fun hide(vararg settings: SettingProperty<Any>) =
		hide(settings.map { it.setting.layer })

	@SettingEditorDsl
	fun <T : SettingBlock> hideBlock(settingBlock: SettingBlockProperty<T>) {
		settingBlock.settingBlock.layer.settingLayers.forEach(::hide)
	}

	@SettingEditorDsl
	fun hideBlocks(vararg settingBlocks: SettingBlockProperty<SettingBlock>) =
		settingBlocks.forEach { hideBlock(it) }

	@SettingEditorDsl
	fun <T : SettingBlock> hideBlockExcept(settingBlock: SettingBlockProperty<T>, vararg except: SettingProperty<Any>) {
		val exceptSettings = except.map { it.setting }
		settingBlock.settingBlock.layer.settingLayers.forEach { layer ->
			if (layer.setting !in exceptSettings) hide(layer)
		}
	}

	@SettingEditorDsl
	fun hideAllBlocksExcept(vararg except: SettingBlockProperty<SettingBlock>) {
		val exceptBlocks = except.map { it.settingBlock.layer }
		fun processBlock(blockLayer: BlockLayer.Block) {
			blockLayer.layers.forEach(::processBlock)
			if (blockLayer !in exceptBlocks) blockLayer.settingLayers.forEach(::hide)
		}

		c.settingBlockLayers.layers.forEach(::processBlock)
	}

	open class BasicEditBuilder(
		c: Config,
		open val settings: Collection<Setting<*, *>>
	) : ConfigEditor(c) {
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
	}

	class TypedEditBuilder<T : Any>(
		c: Config,
		override val settings: Collection<Setting<SettingCore<T>, T>>
	) : BasicEditBuilder(c, settings) {
		@SettingEditorDsl
		fun defaultValue(value: T) =
			settings.forEach {
				it.core.defaultValue = value
				it.core.value = value
			}
	}

	protected fun hide(layers: Collection<SettingLayer.Single<*, *>>) {
		layers.forEach(::hide)
	}

	protected fun hide(layer: SettingLayer.Single<*, *>) {
		val parentLayer = layer.parent
		parentLayer.layers.remove(layer)
		if (parentLayer.layers.isEmpty())
			parentLayer.parent?.layers?.remove(parentLayer)
	}
}