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
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

@DslMarker
private annotation class SettingDsl

/**
 * Represents a set of [SettingCore]s that are associated with the [name] of the [Config].
 * The settings are managed by this [Config] and are saved and loaded as part of the [ConfigCategory].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settingLayers A set of [SettingCore]s that this config manages.
 */
@Suppress("unused")
abstract class Config(configCategory: ConfigCategory) : Jsonable, Nameable {
    internal val settingLayers = SettingLayer.Root()
	internal val settingBlockLayers = BlockLayer.Root()
    private val registrationQueue = ArrayDeque<LayerSpecInfo>()

    init {
        if (configs.any { it.name == name }) throw IllegalStateException("Configs with name $name already exists")
        enqueueProperties(this::class, emptyList(), emptyList())
        configCategory.configs.add(this)
    }

	private fun enqueueProperties(klass: KClass<*>, outerPath: List<SettingLayerSpec>, outerBlockPath: List<Int>) {
		var childBlockIndex = 0
		forEachConfigProperty(
			klass,
			onSetting = { setting ->
				registrationQueue.addLast(
					LayerSpecInfo(
						outerPath + buildPathFromAnnotations(setting),
						outerBlockPath
					)
				)
			},
			onSettingBlock = { settingBlock, blockClass ->
				val fullBlockPath = outerBlockPath + childBlockIndex
				enqueueProperties(
					blockClass,
					outerPath + buildPathFromAnnotations(settingBlock),
					fullBlockPath
				)
				registrationQueue.addLast(
					LayerSpecInfo(
						emptyList(),
						fullBlockPath
					)
				)
				childBlockIndex++
			}
		)
	}

	private fun forEachConfigProperty(
		klass: KClass<*>,
		onSetting: (property: KProperty<*>) -> Unit = {},
		onSettingBlock: (property: KProperty<*>, blockClass: KClass<*>) -> Unit = { _, _ -> }
	) {
		val hierarchy = buildList {
			var current: KClass<*>? = klass
			while (current != null && current != Config::class && current != Any::class) {
				add(current)
				current = current.supertypes
					.mapNotNull { it.classifier as? KClass<*> }
					.firstOrNull { !it.java.isInterface }
			}
		}.reversed()

		hierarchy.forEach { level ->
			val fieldOrder = level.java.declaredFields
				.withIndex()
				.associate { (index, field) -> field.name to index }

			level.declaredMemberProperties
				.sortedBy {
					val javaField = it.javaField ?: return@sortedBy Int.MAX_VALUE
					fieldOrder[javaField.name] ?: Int.MAX_VALUE
				}
				.forEach { property ->
					val fieldType = property.javaField?.type ?: return@forEach
					when {
						Setting::class.java.isAssignableFrom(fieldType) -> onSetting(property)
						SettingBlockWrapper::class.java.isAssignableFrom(fieldType) -> {
							val blockClass = property.returnType.classifier as? KClass<*>
							if (blockClass != null) onSettingBlock(property, blockClass)
						}
					}
				}
		}
	}

	private fun buildPathFromAnnotations(property: KProperty<*>): List<SettingLayerSpec> {
		val path = mutableListOf<SettingLayerSpec>()
		property.annotations.forEach { annotation ->
			when (annotation) {
				is Tab -> annotation.tabs.forEach { path.add(SettingLayerSpec(SettingLayerType.Tab, it)) }
				is Group -> annotation.groups.forEach { path.add(SettingLayerSpec(SettingLayerType.Group, it)) }
			}
		}
		return path
	}

	fun reset() {
		forEachSettingBlock(settingBlockLayers) { _, single ->
			single.setting.reset()
		}
	}

	final override fun toJson() =
		JsonObject().apply {
			fun process(multiple: SettingLayer.Multiple, target: JsonObject) {
				forEachSetting(
					multiple,
					false,
					{ _, multiple ->
						val nested = JsonObject()
						target.add(multiple.name, nested)
						process(multiple, nested)
					}
				) { _, single ->
					try {
						target.add(single.setting.name, single.setting.toJson())
					} catch (e: Throwable) {
						logError("Failed to serialize '${single.setting}'", e)
					}
				}
			}
			process(settingLayers, this)
		}

	final override fun loadFromJson(serialized: JsonElement) {
		val rootObj = serialized.asJsonObject

		fun load(multiple: SettingLayer.Multiple, obj: JsonObject) {
			forEachSetting(
				multiple,
				false,
				{ _, multiple ->
					val nestedObj = obj[multiple.name]
					if (nestedObj == null || !nestedObj.isJsonObject) {
						logError("No data for ${multiple.type.toString().lowercase()} '${multiple.name}' in '$name'}")
					}
					try {
						load(multiple, nestedObj.asJsonObject)
					} catch(e: Throwable) {
						logError("Failed to deserialize ${multiple.type.toString().lowercase()} '${multiple.name}' in '$name'", e)
					}
				}
			) { _, single ->
				val jsonValue = obj[single.setting.name]
				if (jsonValue != null) {
					try {
						single.setting.loadFromJson(jsonValue)
					} catch (e: Throwable) {
						logError("Failed to deserialize setting '$name'", e)
					}
				} else {
					logError("No saved value for setting '${single.setting.name}' in '$name'")
				}
			}
		}

		load(settingLayers, rootObj)
	}

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Boolean,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, BooleanSetting(defaultValue), visibility)

	@SettingDsl
    fun <T : Enum<T>> setting(
        name: String,
        defaultValue: T,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description,EnumSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Char,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, CharSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: String,
        multiline: Boolean = false,
        flags: Int = ImGuiInputTextFlags.None,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, StringSetting(defaultValue, multiline, flags), visibility)

	@SettingDsl
	@JvmName("collectionSetting1")
    fun setting(
        name: String,
        defaultValue: Collection<Block>,
        immutableCollection: Collection<Block> = Registries.BLOCK.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, BlockCollectionSetting(immutableCollection, defaultValue.toMutableList()), visibility)

	@SettingDsl
	@JvmName("collectionSetting2")
    fun setting(
        name: String,
        defaultValue: Collection<Item>,
        immutableCollection: Collection<Item> = Registries.ITEM.toList(),
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, ItemCollectionSetting(immutableCollection, defaultValue.toMutableList()), visibility)

	@SettingDsl
	@JvmName("collectionSetting3")
    inline fun <reified T : Any> setting(
        name: String,
        defaultValue: Collection<T>,
        immutableList: Collection<T> = defaultValue,
        description: String = "",
        displayClassName: Boolean = false,
        serialize: Boolean = false,
        noinline visibility: () -> Boolean = { true },
    ) = setting(
	    name,
	    description,
        if (displayClassName) ClassCollectionSetting(immutableList, defaultValue.toMutableList())
        else CollectionSetting(defaultValue.toMutableList(), immutableList, TypeToken.getParameterized(Collection::class.java, T::class.java).type, serialize),
	    visibility
	)

	@SettingDsl
    // ToDo: Actually implement maps
	inline fun <reified K : Any, reified V : Any> setting(
        name: String,
        defaultValue: Map<K, V>,
        description: String = "",
        noinline visibility: () -> Boolean = { true },
    ) = setting(
	    name,
	    description,
	    MapSetting(
		    defaultValue.toMutableMap(),
		    TypeToken.getParameterized(MutableMap::class.java, K::class.java, V::class.java).type
		),
		visibility
	)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Double,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, DoubleSetting(defaultValue, range, step, unit), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Float,
        range: ClosedRange<Float>,
        step: Float = 1f,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, FloatSetting(defaultValue, range, step, unit), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Int,
        range: ClosedRange<Int>,
        step: Int = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, IntegerSetting(defaultValue, range, step, unit), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Long,
        range: ClosedRange<Long>,
        step: Long = 1,
        description: String = "",
        unit: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, LongSetting(defaultValue, range, step, unit), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Bind,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: KeyCode,
        description: String = "",
        alwaysListening: Boolean = false,
        screenCheck: Boolean = true,
        visibility: () -> Boolean = { true },
    ) = setting(name, description, KeybindSetting(defaultValue, this as? Muteable, alwaysListening, screenCheck), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Color,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, ColorSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Vec3d,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, Vec3dSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: BlockPos.Mutable,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, BlockPosSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: BlockPos,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, BlockPosSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: Block,
        description: String = "",
        visibility: () -> Boolean = { true },
    ) = setting(name, description, BlockSetting(defaultValue), visibility)

	@SettingDsl
    fun setting(
        name: String,
        defaultValue: () -> Unit,
        description: String = "",
        visibility: () -> Boolean = { true }
    ) = setting(name, description, FunctionSetting(defaultValue), visibility)

	@SettingDsl
	fun <T : SettingBlock> settingBlock(
		settingBlock: T,
		block: (context(EditContext.BlockEditContext) T.() -> Unit)? = null
	): SettingBlockWrapper<T> =
		settingBlock
			.let { settingBlock ->
				val path = try {
					registrationQueue.removeFirst().settingBlockSpecs
				} catch(_: NoSuchElementException) {
					throw IllegalStateException("Setting block registered from an unknown location; layer path was not queued before setting initialization")
				}

				var currentLayer: BlockLayer = settingBlockLayers

				path.forEach { index ->
					val existing = currentLayer.layers.getOrNull(index)
					if (existing != null) currentLayer = existing
					else {
						val layer = BlockLayer.Block(currentLayer)
						currentLayer.layers.add(layer)
						currentLayer = layer
					}
				}

				SettingBlockWrapper(settingBlock, currentLayer)
					.also { wrapper ->
						(currentLayer as? BlockLayer.Block)?.settingBlock = wrapper
					}
					.also { wrapper ->
						if (block != null) {
							with (EditContext.BlockEditContext(wrapper)) {
								settingBlock.block()
							}
						}
					}
			}

	@PublishedApi
	internal fun <T : SettingCore<R>, R : Any> setting(name: String, description: String, settingCore: T, visibility: () -> Boolean): Setting<T, R> {
		val layerSpecInfo = try {
			registrationQueue.removeFirst()
		} catch(_: NoSuchElementException) {
			throw IllegalStateException("Setting registered from an unknown location; layer path was not queued before setting initialization")
		}

		var currentSettingLayer: SettingLayer.Multiple = settingLayers
		layerSpecInfo.settingLayerSpecs.forEach { spec ->
			val existing = currentSettingLayer.layers
				.asSequence()
				.filterIsInstance<SettingLayer.Multiple>()
				.filter { it.name == spec.name }
				.also {
					it.forEach { layer ->
						if (spec.type == SettingLayerType.Tab && layer !is SettingLayer.Tab ||
							spec.type == SettingLayerType.Group && layer !is SettingLayer.Group
							) throw IllegalStateException("Duplicate setting layers with differing types: ${layer.name} with type ${layer.type.toString().lowercase()} and ${spec.name} with type ${spec.type.toString().lowercase()}")
					}
				}
				.firstOrNull()

			if (existing != null) currentSettingLayer = existing
			else {
				val newSettingLayer = when (spec.type) {
					SettingLayerType.Tab -> SettingLayer.Tab(spec.name, mutableListOf(), currentSettingLayer)
					SettingLayerType.Group -> SettingLayer.Group(spec.name, mutableListOf(), currentSettingLayer)
					SettingLayerType.Root -> throw IllegalStateException("Multiple root setting layers; only the base class root layer should ever be created")
				}
				currentSettingLayer.layers.add(newSettingLayer)
				currentSettingLayer = newSettingLayer
			}
		}

		if (currentSettingLayer.layers.any {
			it is SettingLayer.Single<*, *> && it.setting.name == name
		}) throw IllegalStateException("Duplicate setting name ('$name') within ${currentSettingLayer.name}")

		var currentBlockLayer: BlockLayer = settingBlockLayers
		layerSpecInfo.settingBlockSpecs.forEach { index ->
			val existing = currentBlockLayer.layers.getOrNull(index)
			if (existing != null) currentBlockLayer = existing
			else {
				val newBlockLayer = BlockLayer.Block(currentBlockLayer)
				currentBlockLayer.layers.add(newBlockLayer)
				currentBlockLayer = newBlockLayer
			}
		}

		val layer = SettingLayer.Single(
			currentSettingLayer,
			currentBlockLayer,
			name,
			description,
			settingCore,
			this@Config,
			visibility
		)
		currentSettingLayer.layers.add(layer)
		currentBlockLayer.settingLayers.add(layer)
		return layer.setting
	}

    enum class SettingLayerType { Root, Tab, Group }
    private data class SettingLayerSpec(val type: SettingLayerType, val name: String)
	private data class LayerSpecInfo(val settingLayerSpecs: List<SettingLayerSpec>, val settingBlockSpecs: List<Int>)

	sealed interface SettingLayer {
		val parent: SettingLayer?

	    sealed class Multiple(
		    override val name: String,
		    val layers: MutableList<SettingLayer>,
		    override val parent: Multiple?
	    ) : SettingLayer, Nameable {
		    abstract val type: SettingLayerType
	    }

	    class Root : Multiple(
		    "Root",
		    mutableListOf(),
		    null
	    ) {
		    override val type = SettingLayerType.Root
	    }

	    class Tab(
		    name: String,
		    layers: MutableList<SettingLayer>,
		    parent: Multiple
	    ) : Multiple(name, layers, parent) {
		    override val type = SettingLayerType.Tab
	    }

	    class Group(
		    name: String,
		    layers: MutableList<SettingLayer>,
		    parent: Multiple
	    ) : Multiple(name, layers, parent) {
		    override val type = SettingLayerType.Group
	    }

        class Single<T : SettingCore<R>, R : Any>(
	        override val parent: Multiple,
	        val blockLayer: BlockLayer,
	        name: String,
	        description: String,
	        settingCore: T,
	        config: Config,
	        visibility: () -> Boolean,
		) : SettingLayer {
			val setting = Setting(name, description, settingCore, config, this, visibility)
		}
    }

	sealed class BlockLayer {
		open val parent: BlockLayer? = null
		val layers = mutableListOf<BlockLayer.Block>()
		val settingLayers = mutableListOf<SettingLayer.Single<*, *>>()

		class Root : BlockLayer() {
			override val parent = null
		}

		class Block(
			override val parent: BlockLayer?
		) : BlockLayer() {
			var settingBlock: SettingBlockWrapper<*>? = null
		}
	}

	internal fun forEachSetting(
		root: SettingLayer.Multiple = settingLayers,
		recurse: Boolean = true,
		onMultiple: ((path: List<String>, single: SettingLayer.Multiple) -> Unit)? = null,
		onSingle: ((path: List<String>, single: SettingLayer.Single<*, *>) -> Unit)? = null
	) {
		fun internalForEach(layer: SettingLayer.Multiple, path: List<String>) {
			layer.layers.forEach { layer ->
				when (layer) {
					is SettingLayer.Single<*, *> if onSingle != null -> onSingle(path, layer)
					is SettingLayer.Multiple if onMultiple != null -> {
						onMultiple(path, layer)
						if (recurse) internalForEach(layer, path + layer.name)
					}
					else -> {}
				}
			}
		}
		internalForEach(root, emptyList())
	}

	internal fun forEachSettingBlock(
		root: BlockLayer = settingBlockLayers,
		recurse: Boolean = true,
		onBlockLayer: ((path: List<Int>, block: BlockLayer.Block) -> Unit)? = null,
		onSetting: ((path: List<Int>, single: SettingLayer.Single<*, *>) -> Unit)? = null
	) {
		fun internalForEach(layer: BlockLayer, path: List<Int>) {
			if (onSetting != null) layer.settingLayers.forEach { onSetting(path, it) }
			layer.layers.forEachIndexed { index, blockLayer ->
				val fullPath = path + index
				onBlockLayer?.invoke(fullPath, blockLayer)
				if (recurse) internalForEach(blockLayer, fullPath)
			}
		}
		internalForEach(root, emptyList())
	}
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tab(vararg val tabs: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Group(vararg val groups: String)