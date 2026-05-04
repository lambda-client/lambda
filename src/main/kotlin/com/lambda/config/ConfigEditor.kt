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

import com.lambda.config.Config.SettingContainer
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun <T : Config> T.applyEdits(edits: ConfigEditor<T>.() -> Unit) {
	ConfigEditor(this).apply(edits)
}

class ConfigEditor<T : Config>(val c: T) {
	private val KProperty0<*>.delegate
		get() = try {
			apply { isAccessible = true }.getDelegate()
		} catch (e: Exception) {
			throw IllegalStateException("Could not access delegate for property $name", e)
		}

	private fun <T : Any> KProperty0<T>.setting() =
		this.delegate as? Setting<SettingCore<T>, T>
			?: throw IllegalStateException("Setting delegate did not match current value's type")

	private fun <T : Any> KProperty0<T>.settingCore() = setting().core

	@SettingEditorDsl
	fun <T : Any> KProperty0<T>.edit(edits: TypedEditBuilder<T>.(SettingCore<T>) -> Unit) {
		val delegate = setting()
		TypedEditBuilder(this@ConfigEditor, listOf(delegate)).edits(delegate.core)
	}

	@SettingEditorDsl
	fun <T : Any, R : Any> KProperty0<T>.editWith(
		other: KProperty0<R>,
		edits: TypedEditBuilder<T>.(SettingCore<R>) -> Unit
	) = TypedEditBuilder(this@ConfigEditor, listOf(setting())).edits(other.settingCore())

	@SettingEditorDsl
	fun edit(
		vararg settings: KProperty0<*>,
		edits: BasicEditBuilder.() -> Unit
	) = BasicEditBuilder(this, settings.map { (it as KProperty0<Any>).setting() }).apply(edits)

	@SettingEditorDsl
	fun <T : Any> editWith(
		vararg settings: KProperty0<*>,
		other: KProperty0<T>,
		edits: BasicEditBuilder.(SettingCore<T>) -> Unit
	) = BasicEditBuilder(this, settings.map { (it as KProperty0<Any>).setting() }).edits(other.settingCore())

	@SettingEditorDsl
	fun <T : Any> editTyped(
		vararg settings: KProperty0<T>,
		edits: TypedEditBuilder<T>.() -> Unit
	) = TypedEditBuilder(this, settings.map { it.setting() }).apply(edits)

	@SettingEditorDsl
	fun <T : Any, R : Any> editTypedWith(
		vararg settings: KProperty0<T>,
		other: KProperty0<R>,
		edits: TypedEditBuilder<T>.(SettingCore<R>) -> Unit
	) = TypedEditBuilder(this, settings.map { it.setting() }).edits(other.settingCore())

	/**
	 * Recursively removes matching [Setting]s from the [Config.settingContainers] tree.
	 * After removal, any [SettingContainer.Multiple] (group/tab) left empty is also pruned.
	 */
	@SettingEditorDsl
	fun hide(settings: Collection<Setting<*, *>>) {
		removeFromContainers(c.settingContainers, settings.toSet())
	}

	@SettingEditorDsl
	fun hide(vararg settings: KProperty0<Any>) =
		hide(settings.map { it.setting() })

	@SettingEditorDsl
	fun hideBlock(settingGroup: SettingBlock) = hide(settingGroup.collectSettings())

	@SettingEditorDsl
	fun hideBlockExcept(settingGroup: SettingBlock, vararg except: KProperty0<Any>) {
		val exceptSettings = except.map { it.setting() }
		hide(settingGroup.collectSettings().filter { it !in exceptSettings })
	}

	@SettingEditorDsl
	fun hideBlocks(vararg settingGroups: SettingBlock) =
		settingGroups.forEach { hide(it.collectSettings()) }

	@SettingEditorDsl
	fun hideAllBlocksExcept(vararg except: SettingBlock) {
		val toHide = c.collectSettingBlocks().filter { it !in except }
		toHide.forEach { hide(it.collectSettings()) }
	}

	@SettingEditorDsl
	fun SettingBlock.forEachSetting(block: (Setting<*, *>) -> Unit) =
		Config.forEachSetting(this, block)

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

	/**
	 * Recursively removes [SettingContainer.Single] entries whose [Setting] is in [toRemove],
	 * and prunes any [SettingContainer.Multiple] (group/tab) left empty after removal.
	 */
	private fun removeFromContainers(
		containers: MutableList<SettingContainer>,
		toRemove: Set<Setting<*, *>>
	) {
		val iterator = containers.iterator()
		while (iterator.hasNext()) {
			when (val container = iterator.next()) {
				is SettingContainer.Single -> {
					if (container.setting in toRemove) iterator.remove()
				}
				is SettingContainer.Multiple -> {
					removeFromContainers(container.settings, toRemove)
					if (container.settings.isEmpty()) iterator.remove()
				}
			}
		}
	}

	/**
	 * Collects all [Setting] instances from a [SettingBlock] via reflection,
	 * recursing into nested [SettingBlock] fields.
	 */
	private fun SettingBlock.collectSettings(): List<Setting<*, *>> {
		val result = mutableListOf<Setting<*, *>>()
		collectSettingsRecursive(this, result)
		return result
	}

	private fun collectSettingsRecursive(instance: Any, result: MutableList<Setting<*, *>>) {
		Config.forEachSettingProperty(instance::class,
			onSetting = { property ->
				val field = property.javaField ?: return@forEachSettingProperty
				field.isAccessible = true
				(field.get(instance) as? Setting<*, *>)?.let { result.add(it) }
			},
			onSettingBlock = { property, _ ->
				val field = property.javaField ?: return@forEachSettingProperty
				field.isAccessible = true
				field.get(instance)?.let { collectSettingsRecursive(it, result) }
			}
		)
	}
}