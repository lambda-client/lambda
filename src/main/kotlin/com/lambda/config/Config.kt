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

package com.lambda.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.lambda.Lambda.LOG
import com.lambda.config.ConfigLoader.configs
import com.lambda.config.settings.CharSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.config.settings.StringSetting
import com.lambda.config.settings.collections.BlockCollectionSetting
import com.lambda.config.settings.collections.ClassCollectionSetting
import com.lambda.config.settings.collections.CollectionSetting
import com.lambda.config.settings.collections.ItemCollectionSetting
import com.lambda.config.settings.collections.MapSetting
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.BlockPosSetting
import com.lambda.config.settings.complex.BlockSetting
import com.lambda.config.settings.complex.ColorSetting
import com.lambda.config.settings.complex.KeybindSetting
import com.lambda.config.settings.complex.Vec3dSetting
import com.lambda.config.settings.numeric.DoubleSetting
import com.lambda.config.settings.numeric.FloatSetting
import com.lambda.config.settings.numeric.IntegerSetting
import com.lambda.config.settings.numeric.LongSetting
import com.lambda.event.Muteable
import com.lambda.imgui.flag.ImGuiInputTextFlags
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.KeyCode
import com.lambda.util.Nameable
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.jvm.java
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

/**
 * Represents a set of [SettingCore]s that are associated with the [name] of the [Config].
 * The settings are managed by this [Config] and are saved and loaded as part of the [ConfigCategory].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settingContainers A set of [SettingCore]s that this config manages.
 */
abstract class Config(configCategory: ConfigCategory) : Jsonable, Nameable {
    val settingContainers = mutableListOf<SettingContainer>()
    private val registrationQueue = ArrayDeque<List<ContainerSpec>>()

    init {
        if (configs.any { it.name == name }) throw IllegalStateException("Configs with name $name already exists")
        enqueueProperties(this::class, emptyList())
        configCategory.configs.add(this)
    }

    /**
     * Recursively reflects over [klass]'s declared properties to build the [registrationQueue].
     * For each property:
     * - If its backing field is a [Setting], enqueue its annotation path.
     * - If its backing field is a [SettingBlock], recurse into that class with the current path as outer context.
     * - If it has no backing field (computed property), skip it.
     */
    private fun enqueueProperties(klass: KClass<*>, outerPath: List<ContainerSpec>) {
        klass.declaredMemberProperties.forEach { property ->
            val path = outerPath + buildPathFromAnnotations(property)
            val fieldType = property.javaField?.type
            when {
                fieldType == null -> {}
                SettingBlock::class.java.isAssignableFrom(fieldType) ->
                    enqueueProperties(fieldType.kotlin, path)
                Setting::class.java.isAssignableFrom(fieldType) ->
                    registrationQueue.addLast(path)
            }
        }
    }

    /**
     * Reads [Tab] and [Group] annotations from a property and builds a nesting path.
     * Annotation order in source determines nesting order.
     */
    private fun buildPathFromAnnotations(property: KProperty<*>): List<ContainerSpec> {
        val path = mutableListOf<ContainerSpec>()
	    property.annotations.forEach { annotation ->
		    when (annotation) {
			    is Tab -> annotation.tabs.forEach { path.add(ContainerSpec(ContainerType.Tab, it)) }
			    is Group -> annotation.groups.forEach { path.add(ContainerSpec(ContainerType.Group, it)) }
		    }
	    }
        return path
    }

    /**
     * Registers a [Setting] into the [settingContainers] tree.
     * Dequeues the next path from [registrationQueue] and navigates/creates
     * the container hierarchy, coalescing containers with the same name and type.
     */
    fun register(setting: Setting<*, *>) {
        val path = registrationQueue.removeFirst()
        var currentList = settingContainers

	    path.forEach { spec ->
		    val existing = currentList.firstOrNull {
			    it is SettingContainer.Multiple &&
					    it.name == spec.name &&
					    ((spec.type == ContainerType.Tab && it is SettingContainer.Tab) ||
							    (spec.type == ContainerType.Group && it is SettingContainer.Group))
		    } as? SettingContainer.Multiple

		    if (existing != null) currentList = existing.settings
		    else {
			    val newContainer = when (spec.type) {
				    ContainerType.Tab -> SettingContainer.Tab(spec.name, mutableListOf())
				    ContainerType.Group -> SettingContainer.Group(spec.name, mutableListOf())
			    }
			    currentList.add(newContainer)
			    currentList = newContainer.settings
		    }
	    }

        currentList.add(SettingContainer.Single(setting))
    }

    final override fun toJson() =
        JsonObject().apply {
            fun JsonObject.addSettings(settings: Collection<SettingContainer>) {
                settings.forEach { container ->
                    when (container) {
                        is SettingContainer.Single ->
                            try {
                                add(container.setting.name, container.setting.toJson())
                            } catch(e: Throwable) {
                                logError("Failed to serialize '${container.setting}' in ${this::class.simpleName}", e)
                            }
                        is SettingContainer.Multiple -> {
                            val grouped = JsonObject()
                            grouped.addSettings(container.settings)
                            try {
                                add(container.name, grouped)
                            } catch(e: Throwable) {
                                logError("Failed to serialize ${container.typeStr}: ${container.name} in ${this::class.simpleName}", e)
                            }
                        }
                    }
                }
            }
            addSettings(settingContainers)
        }

    final override fun loadFromJson(serialized: JsonElement) {
        val rootObj = serialized.asJsonObject

        fun loadFromObject(obj: JsonObject, containers: Collection<SettingContainer>) {
	        containers.forEach { container ->
		        when (container) {
			        is SettingContainer.Single -> {
				        val jsonValue = obj[container.setting.name]
				        if (jsonValue != null) {
					        try {
						        container.setting.loadFromJson(jsonValue)
					        } catch (e: Throwable) {
						        logError("Failed to deserialize setting '${container.setting.name}'", e)
					        }
				        } else {
					        LOG.warn("No saved value for setting '${container.setting.name}' in ${this::class.simpleName}")
				        }
			        }
			        is SettingContainer.Multiple -> {
				        val nestedObj = obj[container.name]?.asJsonObject
				        if (nestedObj != null) {
					        loadFromObject(nestedObj, container.settings)
				        } else {
					        LOG.debug("No data for group/tab '${container.name}' in ${this::class.simpleName}")
				        }
			        }
		        }
	        }
        }

        loadFromObject(rootObj, settingContainers)
    }

    fun setting(
        name: String,
        defaultValue: Boolean,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BooleanSetting(defaultValue), this, visibility)

    inline fun <reified T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = Setting(name, description,EnumSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: Char,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, CharSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: String,
        multiline: Boolean = false,
        flags: Int = ImGuiInputTextFlags.None,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, StringSetting(defaultValue, multiline, flags), this, visibility)

	@JvmName("collectionSetting1")
    fun setting(
        name: String,
        defaultValue: Collection<Block>,
        immutableCollection: Collection<Block> = Registries.BLOCK.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockCollectionSetting(immutableCollection, defaultValue.toMutableList()), this, visibility)

	@JvmName("collectionSetting2")
    fun setting(
        name: String,
        defaultValue: Collection<Item>,
        immutableCollection: Collection<Item> = Registries.ITEM.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ItemCollectionSetting(immutableCollection, defaultValue.toMutableList()), this, visibility)

	@JvmName("collectionSetting3")
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: Collection<T>,
        immutableList: Collection<T> = defaultValue,
        description: String = "",
        displayClassName: Boolean = false,
        serialize: Boolean = false,
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
        if (displayClassName) ClassCollectionSetting(immutableList, defaultValue.toMutableList())
        else CollectionSetting(defaultValue.toMutableList(), immutableList, TypeToken.getParameterized(Collection::class.java, T::class.java).type, serialize),
		this,
	    visibility
	)

    // ToDo: Actually implement maps
    inline fun <reified K : Any, reified V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = Setting(
	    name,
	    description,
	    MapSetting(
		    defaultValue.toMutableMap(),
		    TypeToken.getParameterized(MutableMap::class.java, K::class.java, V::class.java).type
		),
	    this,
		visibility
	)

    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, DoubleSetting(defaultValue, range, step, unit), this, visibility)

    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, FloatSetting(defaultValue, range, step, unit), this, visibility)

    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, IntegerSetting(defaultValue, range, step, unit), this, visibility)

    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, LongSetting(defaultValue, range, step, unit), this, visibility)

    fun setting(
        name: String,
        defaultValue: Bind,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), this, visibility)

    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), this, visibility)

    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, ColorSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, Vec3dSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockPosSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: Block,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = Setting(name, description, BlockSetting(defaultValue), this, visibility)

    fun setting(
        name: String,
        defaultValue: () -> Unit,
        description: String = "",
        visibility: () -> Boolean = { true }
    ) = Setting(name, description, FunctionSetting(defaultValue), this, visibility)

    fun <T : SettingBlock> settingBlock(settingBlock: T, block: (T.() -> Unit)? = null) =
        settingBlock.apply { block?.invoke(this) }

    private enum class ContainerType { Tab, Group }
    private data class ContainerSpec(val type: ContainerType, val name: String)

    @Target(AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Tab(vararg val tabs: String)

    @Target(AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Group(vararg val groups: String)

    sealed interface SettingContainer {
        class Single(val setting: Setting<*, *>) : SettingContainer

        sealed class Multiple(
            val name: String,
            val settings: MutableList<SettingContainer>
        ) : SettingContainer {
            abstract val typeStr: String
        }

        class Tab(
            name: String,
            settings: MutableList<SettingContainer>
        ) : Multiple(name, settings) {
            override val typeStr = "tab"
        }

        class Group(
            name: String,
            settings: MutableList<SettingContainer>
        ) : Multiple(name, settings) {
            override val typeStr = "group"
        }
    }
}