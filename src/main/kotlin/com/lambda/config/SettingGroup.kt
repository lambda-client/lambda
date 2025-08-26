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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class SettingGroup(
    val c: Configurable,
) {
    val settings = arrayListOf<AbstractSetting<*>>()
    val inserts = mutableMapOf<Pair<Int, InsertType>, MutableList<AbstractSetting<*>>>()

    protected inline fun <reified T : AbstractSetting<R>, reified R : Any> T.index(): SettingDelegate<T, R> {
        val index = settings.size
        settings.add(this)
        return SettingDelegate<T, R>(settings, index)
    }

    @Suppress("Unchecked_Cast")
    class SettingDelegate<T : AbstractSetting<R>, R : Any>(
        private val settings: List<AbstractSetting<*>>,
        val index: Int
    ) : ReadWriteProperty<Any?, R> {
        val setting: T
            get() = settings[index] as T

        fun get() = setting.value
        override fun getValue(thisRef: Any?, property: KProperty<*>) = get()

        fun set(value: R) { setting.value = value }
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: R) = set(value)
    }

    @DslMarker
    annotation class SettingGroupDsl

    @SettingGroupDsl
    fun configure(block: SettingGroupBuilder.() -> Unit) {
        SettingGroupBuilder().apply(block).build()
    }

    @SettingGroupDsl
    inline fun <reified T : AbstractSetting<R>, reified R : Any> SettingGroupBuilder.override(old: SettingDelegate<T, R>, new: T) {
        settings[old.index] = new
    }

    @SettingGroupDsl
    fun SettingGroupBuilder.insert(delegate: SettingDelegate<*, *>, insert: AbstractSetting<*>, insertType: InsertType) {
        inserts.getOrPut(delegate.index to insertType) { mutableListOf(insert) }.add(insert)
    }

    @SettingGroupDsl
    inline fun <reified T : AbstractSetting<R>, reified R : Any> SettingGroupBuilder.modifyDefaultValue(
        delegate: SettingDelegate<T, R>,
        new: (old: R) -> R
    ) {
        (settings[delegate.index] as T).value = new(delegate.get())
    }

    @SettingGroupDsl
    inline fun SettingGroupBuilder.modifyName(
        delegate: SettingDelegate<*, *>,
        new: (old: String) -> String
    ) {
        delegate.setting.name = new(delegate.setting.name)
    }

    @SettingGroupDsl
    inline fun SettingGroupBuilder.modifyDescription(
        delegate: SettingDelegate<*, *>,
        new: (old: String) -> String
    ) {
        delegate.setting.description = new(delegate.setting.description)
    }

    @SettingGroupDsl
    inline fun SettingGroupBuilder.modifyVisibility(
        delegate: SettingDelegate<*, *>,
        new: (old: () -> Boolean) -> () -> Boolean
    ) {
        delegate.setting.visibility = new(delegate.setting.visibility)
    }

    @SettingGroupDsl
    private fun SettingGroupBuilder.build() {
        settings.forEachIndexed { index, setting ->
            with(c) {
                val insertEntries = inserts.entries.filter {
                    it.key.first == index
                }
                insertEntries.forEach { entry ->
                    if (entry.key.second == InsertType.Before)
                        entry.value.forEach { it.register() }
                }
                setting.register()
                insertEntries.forEach { entry ->
                    if (entry.key.second == InsertType.After)
                        entry.value.forEach { it.register() }
                }
            }
        }
    }

    @SettingGroupDsl
    class SettingGroupBuilder

    enum class InsertType {
        Before,
        After
    }
}