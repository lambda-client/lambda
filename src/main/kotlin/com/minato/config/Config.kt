
package com.minato.config

import com.minato.Minato.LOG
import com.minato.Minato.typeFactory
import com.minato.config.ConfigLoader.configs
import com.minato.config.entries.ConfigEntryDsl
import com.minato.config.entries.Property
import com.minato.config.entries.PropertyEntryLayer
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.settings.CharSetting
import com.minato.config.settings.FunctionSetting
import com.minato.config.settings.StringSetting
import com.minato.config.settings.collections.BlockCollectionSetting
import com.minato.config.settings.collections.ClassCollectionSetting
import com.minato.config.settings.collections.CollectionSetting
import com.minato.config.settings.collections.FluidCollectionSetting
import com.minato.config.settings.collections.ItemCollectionSetting
import com.minato.config.settings.collections.MapSetting
import com.minato.config.settings.comparable.BooleanSetting
import com.minato.config.settings.comparable.EnumSetting
import com.minato.config.settings.complex.Bind
import com.minato.config.settings.complex.BlockPosSetting
import com.minato.config.settings.complex.BlockSetting
import com.minato.config.settings.complex.ColorSetting
import com.minato.config.settings.complex.KeybindSetting
import com.minato.config.settings.complex.Vec3dSetting
import com.minato.config.settings.numeric.DoubleSetting
import com.minato.config.settings.numeric.FloatSetting
import com.minato.config.settings.numeric.IntegerSetting
import com.minato.config.settings.numeric.LongSetting
import com.minato.event.Muteable
import com.lambda.imgui.flag.ImGuiInputTextFlags
import com.minato.util.KeyCode
import com.minato.util.Nameable
import net.minecraft.block.Block
import net.minecraft.fluid.Fluid
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

/**
 * Represents a set of [EntryCore]s that are associated with the [name] of the [Config].
 * The settings are managed by this [Config] and are saved and loaded as part of the [ConfigCategory].
 *
 * This class also provides a series of helper methods ([setting]) for creating different types of settings.
 *
 * @property settingLayers A set of [EntryCore]s that this config manages.
 */
@Suppress("unused")
abstract class Config(
	final override val name: String,
	configCategory: ConfigCategory
) : Nameable {
	@get:JvmName("getSettingLayers")
	internal val settingLayers = EntryLayer.Root<Setting<*>>("Settings")
	internal val propertyLayers = EntryLayer.Root<Property<*>>("Properties")
	internal val configBlockLayers = ConfigBlockLayer.Root()
	private val registrationQueue = ArrayDeque<LayerSpecInfo>()

	init {
		if (configs.any { it.name == name }) throw IllegalStateException("Configs with name $name already exists.")
		enqueueConfigEntries(this::class, emptyList(), emptyList())
		configCategory.configs.add(this)
	}

	private fun enqueueConfigEntries(klass: KClass<*>, outerPath: List<SettingLayerSpec>, outerBlockPath: List<Int>) {
		var childBlockIndex = 0
		forEachConfigEntry(
			klass,
			onSetting = { enqueuePrimitiveConfigEntry(it, outerPath, outerBlockPath, it.name) },
			onSettingBlock = { configBlock, blockClass ->
				val fullBlockPath = outerBlockPath + childBlockIndex
				enqueueConfigEntries(
					blockClass,
					outerPath + buildPathFromAnnotations(configBlock),
					fullBlockPath
				)
				registrationQueue.addLast(
					LayerSpecInfo(
						emptyList(),
						fullBlockPath,
						configBlock.name
					)
				)
				childBlockIndex++
			},
			onProperty = { enqueuePrimitiveConfigEntry(it, outerPath, outerBlockPath, it.name) }
		)
	}

	private fun enqueuePrimitiveConfigEntry(
		property: KProperty<*>,
		outerPath: List<SettingLayerSpec>,
		outerBlockPath: List<Int>,
		propertyName: String
	) {
		registrationQueue.addLast(
			LayerSpecInfo(
				outerPath + buildPathFromAnnotations(property),
				outerBlockPath,
				propertyName
			)
		)
	}

	private fun forEachConfigEntry(
		klass: KClass<*>,
		onSetting: (property: KProperty<*>) -> Unit,
		onSettingBlock: (property: KProperty<*>, blockClass: KClass<*>) -> Unit,
		onProperty: (property: KProperty<*>) -> Unit
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
			// Reflecting over declaredFields can fail with NoClassDefFoundError if a field's
			// type comes from an optional runtime dependency that isn't installed. We catch
			// only LinkageError (parent of NoClassDefFoundError, ClassNotFoundException,
			// ClassFormatError, IncompatibleClassChangeError, etc.) so genuine bugs like
			// OutOfMemoryError / StackOverflowError still propagate.
			val fieldOrder = try {
				level.java.declaredFields
					.withIndex()
					.associate { (index, field) -> field.name to index }
			} catch (e: LinkageError) {
				LOG.warn(
					"Skipping config reflection for '${this@Config.name}' hierarchy level " +
						"${level.qualifiedName}: ${e.javaClass.simpleName}: ${e.message}"
				)
				return@forEach
			}

			level.declaredMemberProperties
				.sortedBy {
					val javaField = it.javaField ?: return@sortedBy Int.MAX_VALUE
					fieldOrder[javaField.name] ?: Int.MAX_VALUE
				}
				.forEach { property ->
					val fieldType = try { property.javaField?.type } catch (e: LinkageError) { null } ?: return@forEach
					when {
						Setting::class.java.isAssignableFrom(fieldType) -> onSetting(property)
						ConfigBlockWrapper::class.java.isAssignableFrom(fieldType) -> {
							val blockClass = property.returnType.classifier as? KClass<*>
							if (blockClass != null) onSettingBlock(property, blockClass)
						}
						Property::class.java.isAssignableFrom(fieldType) -> onProperty(property)
					}
				}
		}
	}

	private fun buildPathFromAnnotations(property: KProperty<*>): List<SettingLayerSpec> {
		val path = mutableListOf<SettingLayerSpec>()
		property.annotations.forEach { annotation ->
			when (annotation) {
				is Tab -> annotation.tabs.forEach { path.add(SettingLayerSpec(MultipleLayerType.Tab, it)) }
				is Group -> annotation.groups.forEach { path.add(SettingLayerSpec(MultipleLayerType.Group, it)) }
			}
		}
		return path
	}

	fun resetSettings() {
		settingLayers.forEachEntry { _, single ->
			single.entry.reset()
		}
	}

	fun resetProperties() {
		propertyLayers.forEachEntry { _, single ->
			single.entry.reset()
		}
	}

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Boolean,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> BooleanSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun <T : Enum<T>> setting(
		name: String,
		defaultValue: T,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> EnumSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Char,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> CharSetting(name, description, this, layer, defaultValue, visibility) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: String,
		multiline: Boolean = false,
		flags: Int = ImGuiInputTextFlags.None,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> StringSetting(name, description, this, layer, defaultValue, visibility, multiline, flags) }

	@ConfigEntryDsl
	@JvmName("collectionSetting1")
	fun setting(
		name: String,
		defaultValue: Collection<Block>,
		immutableCollection: Collection<Block> = Registries.BLOCK.toList(),
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> BlockCollectionSetting(name, description, this, layer, visibility, immutableCollection, defaultValue.toMutableList()) }

	@ConfigEntryDsl
	@JvmName("collectionSetting2")
	fun setting(
		name: String,
		defaultValue: Collection<Item>,
		immutableCollection: Collection<Item> = Registries.ITEM.toList(),
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> ItemCollectionSetting(name, description, this, layer, visibility, immutableCollection, defaultValue.toMutableList()) }

	@ConfigEntryDsl
	@JvmName("collectionSetting3")
	fun setting(
		name: String,
		defaultValue: Collection<Fluid>,
		immutableCollection: Collection<Fluid> = Registries.FLUID.toList(),
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> FluidCollectionSetting(name, description, this, layer, visibility, immutableCollection, defaultValue.toMutableList()) }

	@ConfigEntryDsl
	@JvmName("collectionSetting4")
	inline fun <reified T : Any> setting(
		name: String,
		defaultValue: Collection<T>,
		immutableList: Collection<T> = defaultValue,
		description: String = "",
		displayClassName: Boolean = false,
		serialize: Boolean = false,
		noinline visibility: () -> Boolean = { true }
	) = setting(name) { layer ->
		if (displayClassName) ClassCollectionSetting(name, description, this, layer, visibility, immutableList, defaultValue.toMutableList())
		else CollectionSetting(name, description, this, layer, visibility, defaultValue.toMutableList(), immutableList, typeFactory.constructCollectionType(Collection::class.java, T::class.java), serialize)
	}

	@ConfigEntryDsl
	inline fun <reified K : Any, reified V : Any> setting(
		name: String,
		defaultValue: Map<K, V>,
		description: String = "",
		noinline visibility: () -> Boolean = { true }
	) = setting(name) { layer ->
		MapSetting(
			name, description, this, layer, visibility, defaultValue.toMutableMap(),
			typeFactory.constructMapType(MutableMap::class.java, K::class.java, V::class.java)
		)
	}

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Double,
		range: ClosedRange<Double>,
		step: Double = 1.0,
		description: String = "",
		unit: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> DoubleSetting(name, description, this, layer, visibility, defaultValue, range, step, unit) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Float,
		range: ClosedRange<Float>,
		step: Float = 1f,
		description: String = "",
		unit: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> FloatSetting(name, description, this, layer, visibility, defaultValue, range, step, unit) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Int,
		range: ClosedRange<Int>,
		step: Int = 1,
		description: String = "",
		unit: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> IntegerSetting(name, description, this, layer, visibility, defaultValue, range, step, unit) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Long,
		range: ClosedRange<Long>,
		step: Long = 1,
		description: String = "",
		unit: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> LongSetting(name, description, this, layer, visibility, defaultValue, range, step, unit) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Bind,
		description: String = "",
		alwaysListening: Boolean = false,
		screenCheck: Boolean = true,
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> KeybindSetting(name, description, this, layer, visibility, defaultValue, this as? Muteable, alwaysListening, screenCheck) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: KeyCode,
		description: String = "",
		alwaysListening: Boolean = false,
		screenCheck: Boolean = true,
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> KeybindSetting(name, description, this, layer, visibility, defaultValue, this as? Muteable, alwaysListening, screenCheck) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Color,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> ColorSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Vec3d,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> Vec3dSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: BlockPos.Mutable,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> BlockPosSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: BlockPos,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> BlockPosSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun setting(
		name: String,
		defaultValue: Block,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> BlockSetting(name, description, this, layer, visibility, defaultValue) }

	@ConfigEntryDsl
	fun <T : () -> R, R> setting(
		name: String,
		defaultValue: T,
		description: String = "",
		visibility: () -> Boolean = { true }
	) = setting(name) { layer -> FunctionSetting(name, description, defaultValue, this, layer, visibility) }

	@ConfigEntryDsl
	fun <T : ConfigBlock> configBlock(
		configBlock: T
	): ConfigBlockWrapper<T> {
		val path = try {
			registrationQueue.removeFirst().blockSpecs
		} catch (_: NoSuchElementException) {
			throw IllegalStateException("Setting block registered from an unknown location for config '$name'. Layer path was not queued before setting block initialization")
		}

		var currentLayer: ConfigBlockLayer = configBlockLayers
		path.forEach { index ->
			currentLayer = currentLayer.layers.getOrElse(index) {
				ConfigBlockLayer.Block(currentLayer).also { currentLayer.layers.add(it) }
			}
		}

		if (currentLayer is ConfigBlockLayer.Root)
			throw IllegalStateException("Config block registered at the Root level. The Root config block should never be linked to any specific block")

		return ConfigBlockWrapper(configBlock, currentLayer)
			.also { currentLayer.blockWrapper = it }
	}

	@ConfigEntryDsl
	fun <T> property(
		defaultValue: T,
		value: T = defaultValue,
		setter: (T) -> T = { it },
		equals: T.(T) -> Boolean = { other -> this == other }
	): Property<T> {
		val spec = try {
			registrationQueue.removeFirst()
		} catch (_: NoSuchElementException) {
			throw IllegalStateException("Property registered from an unknown location for config '$name'. Layer path was not queued before property initialization")
		}

		var currentPropertyLayer: EntryLayer.Multiple<Property<*>> = propertyLayers
		spec.layerSpecs.forEach { spec ->
			currentPropertyLayer =
				currentPropertyLayer.layers
					.asSequence()
					.filter { it.name == spec.name }
					.also {
						it.forEach { layer ->
							if (layer !is EntryLayer.Multiple<*>)
								throw IllegalStateException("Multiple registered with a name '${layer.name}' matching a Single on the same layer")
						}
					}
					.filterIsInstance<EntryLayer.Multiple<Property<*>>>()
					.firstOrNull()
					?: EntryLayer.Group(spec.name, currentPropertyLayer).also {
						currentPropertyLayer.layers.add(it)
					}
		}

		if (currentPropertyLayer.layers.any { it.name == spec.propertyName })
			throw IllegalStateException("Duplicate layer name ('${spec.propertyName}') within ${currentPropertyLayer.name}")

		var currentBlockLayer: ConfigBlockLayer = configBlockLayers
		spec.blockSpecs.forEach { index ->
			currentBlockLayer =
				currentBlockLayer.layers.getOrElse(index) {
					ConfigBlockLayer.Block(currentBlockLayer).also {
						currentBlockLayer.layers.add(it)
					}
				}
		}

		val single =
			PropertyEntryLayer(currentPropertyLayer) { layer ->
				Property(spec.propertyName, defaultValue, value, layer, this, setter, equals)
			}
		currentPropertyLayer.layers.add(single)
		currentBlockLayer.propertyLayers.add(single)
		return single.entry
	}

	@ConfigEntryDsl
	fun <T> property(
		equals: T.(T) -> Boolean = { other -> this == other },
		setter: (T) -> T = { it },
		valueSupplier: (() -> T)? = null,
		defaultValueSupplier: () -> T
	) = property(defaultValueSupplier(), valueSupplier?.invoke() ?: defaultValueSupplier(), setter, equals)

	@ConfigEntryDsl
	fun <T> property(
		equals: T.(T) -> Boolean = { other -> this == other },
		setter: (T) -> T = { it },
		defaultValue: T,
		valueSupplier: () -> T = { defaultValue }
	) = property(defaultValue, valueSupplier(), setter, equals)

	@PublishedApi
	internal fun <T : Setting<R>, R> setting(name: String, settingSupplier: (single: SettingEntryLayer<T, R>) -> T): T {
		val spec = try {
			registrationQueue.removeFirst()
		} catch (_: NoSuchElementException) {
			throw IllegalStateException("Setting registered from an unknown location for config '${this@Config.name}'. Layer path was not queued before setting initialization")
		}

		var currentEntryLayer: EntryLayer.Multiple<Setting<*>> = settingLayers
		spec.layerSpecs.forEach { spec ->
			currentEntryLayer = currentEntryLayer.layers
				.asSequence()
				.filter { it.name == spec.name }
				.also {
					it.forEach { layer ->
						if (spec.type == MultipleLayerType.Tab && layer !is EntryLayer.Tab ||
							spec.type == MultipleLayerType.Group && layer !is EntryLayer.Group
							) throw IllegalStateException("Duplicate setting layers with differing types: ('${layer.name}') in '${this@Config.name}'")
					}
				}
				.filterIsInstance<EntryLayer.Multiple<Setting<*>>>()
				.firstOrNull()
				?: run {
					when (spec.type) {
						MultipleLayerType.Tab -> EntryLayer.Tab(spec.name, currentEntryLayer)
						MultipleLayerType.Group -> EntryLayer.Group(spec.name, currentEntryLayer)
						MultipleLayerType.Root -> throw IllegalStateException("Multiple root setting layers. Only the base class root layer should ever be created")
					}.also { currentEntryLayer.layers.add(it) }
				}
		}

		if (currentEntryLayer.layers.any { it.name == name })
			throw IllegalStateException("Duplicate layer name ('$name') within ${currentEntryLayer.name}")

		var currentBlockLayer: ConfigBlockLayer = configBlockLayers
		spec.blockSpecs.forEach { index ->
			currentBlockLayer =
				currentBlockLayer.layers.getOrElse(index) {
					ConfigBlockLayer.Block(currentBlockLayer).also {
						currentBlockLayer.layers.add(it)
					}
				}
		}

		val layer = SettingEntryLayer(currentEntryLayer, settingSupplier)
		currentEntryLayer.layers.add(layer)
		currentBlockLayer.settingLayers.add(layer)
		return layer.entry
	}

	private data class SettingLayerSpec(val type: MultipleLayerType, val name: String)
	private data class LayerSpecInfo(val layerSpecs: List<SettingLayerSpec>, val blockSpecs: List<Int>, val propertyName: String)
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tab(vararg val tabs: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Group(vararg val groups: String)