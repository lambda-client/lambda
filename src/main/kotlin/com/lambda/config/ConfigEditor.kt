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

import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

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

	@SettingEditorDsl
	fun hide(settings: Collection<Setting<*, *>>) {
		c.settingContainers.removeAll(settings)
	}

	@SettingEditorDsl
	fun hide(vararg settings: KProperty0<Any>) =
		hide(settings.map { it.setting() })

	@SettingEditorDsl
	fun hideGroup(settingGroup: SettingBlock) = hide(settingGroup.settings)

	@SettingEditorDsl
	fun hideGroupExcept(settingGroup: SettingBlock, vararg except: KProperty0<Any>) {
		val exceptSettings = except.map { it.setting() }.toSet()
		hide(settingGroup.settings.filter { it !in exceptSettings })
	}

	@SettingEditorDsl
	fun hideGroups(vararg settingGroups: SettingBlock) =
		settingGroups.forEach { hide(it.settings) }

	@SettingEditorDsl
	fun hideAllGroupsExcept(vararg except: SettingBlock) =
		hideGroups(*(c.settingGroups - except.toSet()).toTypedArray())

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
}