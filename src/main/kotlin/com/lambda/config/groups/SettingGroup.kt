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

package com.lambda.config.groups

import com.lambda.config.AbstractSetting
import com.lambda.config.Configurable
import com.lambda.util.NamedEnum
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

private val KProperty0<*>.delegate
    get() = try {
        apply { isAccessible = true }.getDelegate()
    } catch (e: Exception) {
        throw IllegalStateException("Could not access delegate for property $name", e)
    }

@Suppress("unchecked_cast")
abstract class SettingGroup(val c: Configurable) {
    @DslMarker
    annotation class SettingEditorDsl

    @SettingEditorDsl
    internal inline fun <T : Any> KProperty0<T>.edit(edits: FullEditBuilder<T>.(AbstractSetting<T>) -> Unit) {
        val setting = delegate as? AbstractSetting<T> ?: throw IllegalStateException("Setting delegate did not match current value's type")
        FullEditBuilder(setting, c).edits(setting)
    }

    @SettingEditorDsl
    internal inline fun <T : Any> KProperty0<T>.editWith(
        other: KProperty0<*>,
        edits: FullEditBuilder<T>.(AbstractSetting<*>) -> Unit
    ) {
        val setting = delegate as? AbstractSetting<T> ?: throw IllegalStateException("Setting delegate did not match current value's type")
        FullEditBuilder(setting, c).edits(other.delegate as AbstractSetting<*>)
    }

    @SettingEditorDsl
    fun edit(
        vararg settings: KProperty0<*>,
        edits: BasicEditBuilder.() -> Unit
    ) { BasicEditBuilder(c, settings.map { it.delegate } as List<AbstractSetting<*>>).apply(edits) }

    @SettingEditorDsl
    fun editWith(
        vararg settings: KProperty0<*>,
        other: KProperty0<*>,
        edits: BasicEditBuilder.(AbstractSetting<*>) -> Unit
    ) { BasicEditBuilder(c, settings.map { it.delegate } as List<AbstractSetting<*>>).edits(other.delegate as AbstractSetting<*>) }

    @SettingEditorDsl
    internal inline fun <T : Any> editTyped(
        vararg settings: KProperty0<T>,
        edits: TypedEditBuilder<T>.() -> Unit
    ) { TypedEditBuilder(settings.map { it.delegate } as List<AbstractSetting<T>>, c).apply(edits) }

    @SettingEditorDsl
    internal inline fun <T : Any, R : Any> editTypedWith(
        vararg settings: KProperty0<T>,
        other: KProperty0<R>,
        edits: TypedEditBuilder<T>.(AbstractSetting<R>) -> Unit
    ) = TypedEditBuilder(settings.map { it.delegate } as List<AbstractSetting<T>>, c).edits(other.delegate as AbstractSetting<R>)

    @SettingEditorDsl
    fun hide(vararg settings: KProperty0<*>) =
        c.settings.removeAll(settings.map { it.delegate } as List<AbstractSetting<*>>)

    @SettingEditorDsl
    fun KProperty0<*>.insert(insert: KProperty0<*>, insertMode: InsertMode) {
        val delegate = insert.delegate as AbstractSetting<*>
        c.settings.remove(delegate)
        val index = c.settings.indexOf(this.delegate as AbstractSetting<*>)
        c.settings.add(if (insertMode == InsertMode.Below) index + 1 else index, delegate)
    }

    @SettingEditorDsl
    fun KProperty0<*>.insert(vararg inserts: KProperty0<*>, insertMode: InsertMode) {
        inserts.forEach { c.settings.remove(it.delegate as AbstractSetting<*>) }
        val index = c.settings.indexOf(delegate as AbstractSetting<*>)
        c.settings.addAll(
            if (insertMode == InsertMode.Below) index + 1 else index,
            inserts.map { it.delegate } as List<AbstractSetting<*>>
        )
    }

    open class BasicEditBuilder(val c: Configurable, open val settings: Collection<AbstractSetting<*>>) {
        @SettingEditorDsl
        fun visibility(vis: () -> Boolean) =
            settings.forEach { it.visibility = vis }

        @SettingEditorDsl
        fun hide() {
            c.settings.removeAll(settings)
        }

        @SettingEditorDsl
        fun groups(vararg groups: NamedEnum) =
            settings.forEach { it.groups = mutableListOf(groups.toList()) }

        @SettingEditorDsl
        fun groups(groups: MutableList<List<NamedEnum>>) =
            settings.forEach { it.groups = groups }
    }

    open class TypedEditBuilder<T : Any>(
        override val settings: Collection<AbstractSetting<T>>,
        c: Configurable
    ) : BasicEditBuilder(c, settings) {
        @SettingEditorDsl
        fun defaultValue(value: T) =
            settings.forEach {
                it.defaultValue = value
                it.value = value
            }
    }

    class FullEditBuilder<T : Any>(
        private val setting: AbstractSetting<T>,
        c: Configurable
    ) : TypedEditBuilder<T>(setOf(setting), c) {
        @SettingEditorDsl
        fun name(name: String) {
            setting.name = name
        }

        @SettingEditorDsl
        fun description(description: String) {
            setting.description = description
        }
    }

    enum class InsertMode {
        Above,
        Below
    }
}