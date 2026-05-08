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

import com.lambda.config.Config.Companion.forEachSetting
import com.lambda.config.Config.SettingLayer
import kotlin.reflect.KProperty0
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun <T : Config> T.applyEdits(edits: ConfigEditor<T>.() -> Unit) {
	ConfigEditor(this).apply(edits)
}

class ConfigEditor<T : Config>(val c: T) {
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
		TypedEditBuilder(this@ConfigEditor, listOf(delegate)).edits(delegate.core)
	}

	@SettingEditorDsl
	fun edit(
		vararg settings: SettingProperty<*>,
		edits: BasicEditBuilder.() -> Unit
	) = BasicEditBuilder(this, settings.map { (it as SettingProperty<Any>).setting }).apply(edits)

	@SettingEditorDsl
	fun <T : Any> editTyped(
		vararg settings: SettingProperty<T>,
		edits: TypedEditBuilder<T>.() -> Unit
	) = TypedEditBuilder(this, settings.map { it.setting }).apply(edits)

	@SettingEditorDsl
	fun hide(vararg settings: SettingProperty<Any>) =
		hide(settings.map { it.setting })

	@SettingEditorDsl
	fun <T : SettingBlock> hideBlock(settingGroup: SettingBlockProperty<T>) {
		forEachSetting(settingGroup.settingBlock) { setting ->
			hide(setting)
		}
	}

	@SettingEditorDsl
	fun hideBlocks(vararg settingGroups: SettingBlockProperty<SettingBlock>) =
		settingGroups.forEach { hideBlock(it) }

	@SettingEditorDsl
	fun <T : SettingBlock> hideBlockExcept(settingGroup: SettingBlockProperty<T>, vararg except: SettingProperty<Any>) {
		val exceptSettings = except.map { it.setting }
		forEachSetting(settingGroup.settingBlock) { setting ->
			if (setting !in exceptSettings) hide(setting)
		}
	}

	@SettingEditorDsl
	fun hideAllBlocksExcept(vararg except: SettingBlockWrapper<SettingBlock>) {
		Config.forEachSettingBlockWrapper(c) { block ->
			if (block !in except) hideBlock(block)
		}
		toHide.forEach { hide(it.collectSettings()) }
	}

	@SettingEditorDsl
	fun SettingBlock.forEachSetting(block: (Setting<*, *>) -> Unit) =
		forEachSetting(this, block)

	open class BasicEditBuilder(val c: ConfigEditor<*>, open val settings: Collection<Setting<*, *>>) {
		@SettingEditorDsl
		fun hide() = c.hide(settings)

		@SettingEditorDsl
		fun visibility(visibility: (() -> Boolean) -> () -> Boolean) {
			settings.forEach {
				it.visibility = visibility(it.visibility)
			}
		}
	}

	class TypedEditBuilder<T : Any>(
		c: ConfigEditor<*>,
		override val settings: Collection<Setting<SettingCore<T>, T>>
	) : BasicEditBuilder(c, settings) {
		@SettingEditorDsl
		fun defaultValue(value: T) =
			settings.forEach {
				it.core.defaultValue = value
				it.core.value = value
			}
	}

	private fun hide(settings: Collection<Setting<*, *>>) {
		settings.forEach(::hide)
	}

	private fun hide(setting: Setting<*, *>) {
		val parentLayer = setting.layer.parent
		parentLayer.layers.remove(setting.layer)
		if (parentLayer.layers.isEmpty())
			parentLayer.parent?.layers?.remove(parentLayer)
	}
}