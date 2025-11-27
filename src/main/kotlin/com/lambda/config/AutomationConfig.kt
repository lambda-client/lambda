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

import com.lambda.config.configurations.AutomationConfigs
import com.lambda.config.groups.BreakSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.EatSettings
import com.lambda.config.groups.HotbarSettings
import com.lambda.config.groups.InteractSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.PlaceSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.Automated
import com.lambda.event.events.onStaticRender
import com.lambda.interaction.construction.result.Drawable
import com.lambda.module.Module
import com.lambda.util.NamedEnum
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

@Suppress("unchecked_cast", "unused")
open class AutomationConfig(
    override val name: String,
    configuration: Configuration = AutomationConfigs
) : Configurable(configuration), Automated {
    enum class Group(override val displayName: String) : NamedEnum {
        Build("Build"),
        Break("Break"),
        Place("Place"),
        Interact("Interact"),
        Rotation("Rotation"),
        Interaction("Interaction"),
        Inventory("Inventory"),
        Hotbar("Hotbar"),
        Eat("Eat"),
        Render("Render"),
        Debug("Debug")
    }

    override val buildConfig = BuildSettings(this, Group.Build)
    override val breakConfig = BreakSettings(this, Group.Break)
    override val placeConfig = PlaceSettings(this, Group.Place)
    override val interactConfig = InteractSettings(this, Group.Interact)
    override val rotationConfig = RotationSettings(this, Group.Rotation)
    override val inventoryConfig = InventorySettings(this, Group.Inventory)
    override val hotbarConfig = HotbarSettings(this, Group.Hotbar)
    override val eatConfig = EatSettings(this, Group.Eat)

    val hiddenSettings = mutableSetOf<AbstractSetting<*>>()

    companion object {
        context(module: Module)
        fun automationConfig(name: String = module.name, edits: (AutomationConfig.() -> Unit)? = null): AutomationConfig =
            AutomationConfig("Default $name Automation Config").apply { edits?.invoke(this) }

        fun automationConfig(name: String, edits: (AutomationConfig.() -> Unit)? = null): AutomationConfig =
            AutomationConfig("Default $name Automation Config").apply { edits?.invoke(this) }

        object DEFAULT : AutomationConfig("Default") {
            val renders by setting("Render", false).group(Group.Render)
            val avoidDesync by setting("Avoid Desync", true, "Cancels incoming inventory update packets if they match previous actions").group(Group.Debug)
            val desyncTimeout by setting("Desync Timeout", 30, 1..30, 1, unit = " ticks", description = "Time to store previous inventory actions before dropping the cache") { avoidDesync }.group(Group.Debug)
            val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree").group(Group.Debug)
            val shrinkFactor by setting("Shrink Factor", 0.001, 0.0..1.0, 0.001).group(Group.Debug)
            val ignoreItemDropWarnings by setting("Ignore Drop Warnings", false, "Hides the item drop warnings from the break manager").group(Group.Debug)

            @Volatile
            var drawables = listOf<Drawable>()

            init {
                onStaticRender {
                    if (renders)
                        with(it) { drawables.forEach { with(it) { buildRenderer() } } }
                }
            }
        }
    }

    @DslMarker
    annotation class SettingEditorDsl

    private val KProperty0<*>.delegate
        get() = try {
            apply { isAccessible = true }.getDelegate()
        } catch (e: Exception) {
            throw IllegalStateException("Could not access delegate for property $name", e)
        }

    @SettingEditorDsl
    internal inline fun <T : Any> KProperty0<T>.edit(edits: TypedEditBuilder<T>.(AbstractSetting<T>) -> Unit) {
        val setting = delegate as? AbstractSetting<T> ?: throw IllegalStateException("Setting delegate did not match current value's type")
        TypedEditBuilder(this@AutomationConfig, listOf(setting)).edits(setting)
    }

    @SettingEditorDsl
    internal inline fun <T : Any, R : Any> KProperty0<T>.editWith(
        other: KProperty0<R>,
        edits: TypedEditBuilder<T>.(AbstractSetting<R>) -> Unit
    ) {
        val setting = delegate as? AbstractSetting<T> ?: throw IllegalStateException("Setting delegate did not match current value's type")
        TypedEditBuilder(this@AutomationConfig, listOf(setting)).edits(other.delegate as AbstractSetting<R>)
    }

    @SettingEditorDsl
    fun edit(
        vararg settings: KProperty0<*>,
        edits: BasicEditBuilder.() -> Unit
    ) { BasicEditBuilder(this@AutomationConfig, settings.map { it.delegate } as List<AbstractSetting<*>>).apply(edits) }

    @SettingEditorDsl
    internal inline fun <T : Any> editWith(
        vararg settings: KProperty0<*>,
        other: KProperty0<T>,
        edits: BasicEditBuilder.(AbstractSetting<T>) -> Unit
    ) { BasicEditBuilder(this@AutomationConfig, settings.map { it.delegate } as List<AbstractSetting<*>>).edits(other.delegate as AbstractSetting<T>) }

    @SettingEditorDsl
    internal inline fun <T : Any> editTyped(
        vararg settings: KProperty0<T>,
        edits: TypedEditBuilder<T>.() -> Unit
    ) { TypedEditBuilder(this@AutomationConfig, settings.map { it.delegate } as List<AbstractSetting<T>>).apply(edits) }

    @SettingEditorDsl
    internal inline fun <T : Any, R : Any> editTypedWith(
        vararg settings: KProperty0<T>,
        other: KProperty0<R>,
        edits: TypedEditBuilder<T>.(AbstractSetting<R>) -> Unit
    ) = TypedEditBuilder(this@AutomationConfig, settings.map { it.delegate } as List<AbstractSetting<T>>).edits(other.delegate as AbstractSetting<R>)

    @SettingEditorDsl
    fun hide(vararg settings: KProperty0<*>) {
        hideAll((settings.map { it.delegate } as List<AbstractSetting<*>>))
    }

    @SettingEditorDsl
    fun hideAll(settingGroup: SettingGroup) = hideAll(settingGroup.settings)

    @SettingEditorDsl
    fun hideAll(settings: Collection<AbstractSetting<*>>) {
        this@AutomationConfig.settings.removeAll(settings)
        hiddenSettings.addAll(settings)
    }

    @SettingEditorDsl
    fun hideAll(vararg settingGroups: SettingGroup) {
        settingGroups.forEach { hideAll(it.settings) }
    }

    @SettingEditorDsl
    fun hideAllExcept(settingGroup: SettingGroup, vararg settings: KProperty0<*>) {
        this@AutomationConfig.settings.removeIf {
            return@removeIf if (it in settingGroup.settings && it !in (settings.toList() as List<AbstractSetting<*>>)) {
                hiddenSettings.add(it)
                true
            } else false
        }
    }

    open class BasicEditBuilder(val c: AutomationConfig, open val settings: Collection<AbstractSetting<*>>) {
        @SettingEditorDsl
        fun visibility(vis: () -> Boolean) =
            settings.forEach { it.visibility = vis }

        @SettingEditorDsl
        fun hide() = c.hideAll(settings)

        @SettingEditorDsl
        fun groups(vararg groups: NamedEnum) =
            settings.forEach { it.groups = mutableListOf(groups.toList()) }

        @SettingEditorDsl
        fun groups(groups: MutableList<List<NamedEnum>>) =
            settings.forEach { it.groups = groups }
    }

    open class TypedEditBuilder<T : Any>(
        c: AutomationConfig,
        override val settings: Collection<AbstractSetting<T>>
    ) : BasicEditBuilder(c, settings) {
        @SettingEditorDsl
        fun defaultValue(value: T) =
            settings.forEach {
                it.defaultValue = value
                it.value = value
            }
    }

    enum class InsertMode {
        Above,
        Below
    }
}