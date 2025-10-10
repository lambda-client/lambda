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

@Suppress("unchecked_cast")
abstract class SettingGroup(val c: Configurable, val startIndex: Int) {
    private val KProperty0<*>.delegate
        get() = try {
            apply { isAccessible = true }.getDelegate()
        } catch (e: Exception) {
            throw IllegalStateException("Could not access delegate for property $name", e)
        }

    @DslMarker
    annotation class SettingEditorDsl

    @SettingEditorDsl
    internal inline fun <T : Any> editSetting(setting: KProperty0<T>, edits: FullEditBuilder<T>.() -> Unit) {
        val setting = setting.delegate as? AbstractSetting<T> ?: throw IllegalStateException("Setting delegate did not match current value's type")
        FullEditBuilder(setting, c).apply(edits)
    }

    @SettingEditorDsl
    fun editSettings(vararg settings: KProperty0<*>, edits: BasicEditBuilder.() -> Unit) {
        BasicEditBuilder(settings.toSet() as Set<AbstractSetting<*>>)
            .apply(edits)
    }

    @SettingEditorDsl
    internal inline fun <T : Any> editSettings(vararg settings: KProperty0<T>, edits: TypedEditBuilder<T>.() -> Unit) {
        TypedEditBuilder((settings.map { it.delegate } as List<AbstractSetting<T>>), c)
            .apply(edits)
    }

    @SettingEditorDsl
    fun hide(vararg settings: KProperty0<*>) {
        (settings.map { it.delegate } as List<AbstractSetting<*>>).forEach { setting ->
            setting.visibility = { false }
        }
    }

    @SettingEditorDsl
    fun hideAll() {
        c.settings.listIterator(startIndex).forEach {
            it.visibility = { false }
        }
    }

    open class BasicEditBuilder(open val settings: Collection<AbstractSetting<*>>) {
        @SettingEditorDsl
        fun visibility(vis: () -> Boolean) {
            settings.forEach { it.visibility = vis }
        }

        @SettingEditorDsl
        fun groups(groups: List<NamedEnum>) {
            settings.forEach { it.groups = mutableListOf(groups) }
        }
    }

    open class TypedEditBuilder<T : Any>(
        override val settings: Collection<AbstractSetting<T>>,
        val c: Configurable
    ) : BasicEditBuilder(settings) {
        @SettingEditorDsl
        fun defaultValue(value: T) {
            settings.forEach { it.value = value }
        }
    }

    class FullEditBuilder<T : Any>(
        val setting: AbstractSetting<T>,
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

        @SettingEditorDsl
        fun insertSetting(insert: AbstractSetting<*>, at: AbstractSetting<*>, insertMode: InsertMode) {
            val index = c.settings.indexOf(at)
            c.settings.add(if (insertMode == InsertMode.Below) index + 1 else index, insert)
        }

        @SettingEditorDsl
        fun insertSettings(vararg inserts: AbstractSetting<*>, at: AbstractSetting<*>, insertMode: InsertMode) {
            val index = c.settings.indexOf(at)
            c.settings.addAll(if (insertMode == InsertMode.Below) index + 1 else index, inserts.toList())
        }

        enum class InsertMode {
            Above,
            Below
        }
    }
}