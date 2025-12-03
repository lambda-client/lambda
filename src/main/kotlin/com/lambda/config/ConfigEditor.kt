/*
 * Copyright 2025 Lambda
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

import com.lambda.util.NamedEnum
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@DslMarker
annotation class SettingEditorDsl

@SettingEditorDsl
fun <T : Configurable> T.applyEdits(edits: ConfigurableEditor<T>.() -> Unit) {
	ConfigurableEditor(this).apply(edits)
}

@Suppress("unchecked_cast", "unused")
open class SettingGroupEditor<T : Configurable>(open val c: T) {
	val KProperty0<*>.delegate
		get() = try {
			apply { isAccessible = true }.getDelegate()
		} catch (e: Exception) {
			throw IllegalStateException("Could not access delegate for property $name", e)
		}

	fun <T> KProperty0<T>.setting() =
		this.delegate as? Setting<SettingCore<T>, T>
			?: throw IllegalStateException("Setting delegate did not match current value's type")

	fun <T> KProperty0<T>.settingCore() = setting().core

	@SettingEditorDsl
	inline fun <T : Any> KProperty0<T>.edit(edits: TypedEditBuilder<T>.(SettingCore<T>) -> Unit) {
		val delegate = setting()
		TypedEditBuilder(this@SettingGroupEditor, listOf(delegate)).edits(delegate.core)
	}

	@SettingEditorDsl
	inline fun <T : Any, R : Any> KProperty0<T>.editWith(
		other: KProperty0<R>,
		edits: TypedEditBuilder<T>.(SettingCore<R>) -> Unit
	) = TypedEditBuilder(this@SettingGroupEditor, listOf(setting())).edits(other.settingCore())

	@SettingEditorDsl
	fun edit(
		vararg settings: KProperty0<*>,
		edits: BasicEditBuilder.() -> Unit
	) = BasicEditBuilder(this, settings.map { it.setting() }).apply(edits)

	@SettingEditorDsl
	inline fun <T : Any> editWith(
		vararg settings: KProperty0<*>,
		other: KProperty0<T>,
		edits: BasicEditBuilder.(SettingCore<T>) -> Unit
	) = BasicEditBuilder(this, settings.map { it.setting() }).edits(other.settingCore())

	@SettingEditorDsl
	inline fun <T : Any> editTyped(
		vararg settings: KProperty0<T>,
		edits: TypedEditBuilder<T>.() -> Unit
	) = TypedEditBuilder(this, settings.map { it.setting() }).apply(edits)

	@SettingEditorDsl
	inline fun <T : Any, R : Any> editTypedWith(
		vararg settings: KProperty0<T>,
		other: KProperty0<R>,
		edits: TypedEditBuilder<T>.(SettingCore<R>) -> Unit
	) = TypedEditBuilder(this, settings.map { it.setting() }).edits(other.settingCore())

	@SettingEditorDsl
	fun hide(settings: Collection<Setting<*, *>>) {
		c.settings.removeAll(settings)
	}

	@SettingEditorDsl
	fun hide(vararg settings: KProperty0<*>) =
		hide(settings.map { it.setting() })

	open class BasicEditBuilder(val c: SettingGroupEditor<*>, open val settings: Collection<Setting<*, *>>) {
		@SettingEditorDsl
		fun hide() = c.hide(settings)

		@SettingEditorDsl
		fun groups(vararg groups: NamedEnum) =
			settings.forEach { it.groups = mutableListOf(groups.toList()) }

		@SettingEditorDsl
		fun groups(groups: MutableList<List<NamedEnum>>) =
			settings.forEach { it.groups = groups }
	}

	class TypedEditBuilder<T : Any>(
		c: SettingGroupEditor<*>,
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

@Suppress("unchecked_cast", "unused")
class ConfigurableEditor<T : Configurable>(override val c: T) : SettingGroupEditor<T>(c) {
	@SettingEditorDsl
	fun hideGroup(settingGroup: ISettingGroup) = hide(settingGroup.settings)

	@SettingEditorDsl
	fun hideGroupExcept(settingGroup: ISettingGroup, vararg except: KProperty0<*>) =
		hide(*((settingGroup.settings as List<KProperty0<*>>) - except.toSet()).toTypedArray())

	@SettingEditorDsl
	fun hideGroups(vararg settingGroups: ISettingGroup) =
		settingGroups.forEach { hide(it.settings) }

	@SettingEditorDsl
	fun hideAllGroupsExcept(vararg except: ISettingGroup) =
		hideGroups(*(c.settingGroups - except.toSet()).toTypedArray())
}