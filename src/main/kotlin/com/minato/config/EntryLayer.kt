
package com.minato.config

import com.minato.config.entries.Property
import com.minato.config.entries.Setting
import com.minato.util.Nameable

interface EntryLayer<T : ConfigEntry<*>> : Nameable {
	override val name: String
	val parent: Multiple<T>?

	open class Multiple<T : ConfigEntry<*>>(
		override val name: String,
		val multipleType: MultipleLayerType = MultipleLayerType.Group,
		override val parent: Multiple<T>?
	) : EntryLayer<T> {
		val layers = mutableListOf<EntryLayer<T>>()

		fun forEachEntry(
			recurse: Boolean = true,
			onMultiple: ((path: List<Multiple<T>>, single: Multiple<T>) -> Unit)? = null,
			onSingle: ((path: List<Multiple<T>>, single: Single<T>) -> Unit)? = null
		) {
			fun internalForEach(layer: Multiple<T>, path: List<Multiple<T>>) {
				layer.layers.forEach { layer ->
					when (layer) {
						is Single<T> if onSingle != null -> onSingle(path, layer)
						is Multiple -> {
							if (onMultiple != null) onMultiple(path, layer)
							if (recurse) internalForEach(layer, path + layer)
						}
						else -> {}
					}
				}
			}
			internalForEach(this, emptyList())
		}
	}

	class Root<T : ConfigEntry<*>>(
		rootName: String
	) : Multiple<T>(rootName, MultipleLayerType.Root, null)

	class Tab<T : ConfigEntry<*>>(
		name: String,
		parent: Multiple<T>
	) : Multiple<T>(name, MultipleLayerType.Tab, parent)

	class Group<T : ConfigEntry<*>>(
		name: String,
		parent: Multiple<T>
	) : Multiple<T>(name, MultipleLayerType.Group, parent)

	abstract class Single<T : ConfigEntry<*>> : EntryLayer<T> {
		abstract override val parent: Multiple<T>
		abstract val entry: T
		override val name get() = entry.name
	}
}

enum class MultipleLayerType { Root, Tab, Group }

sealed class ConfigBlockLayer {
	var blockWrapper: ConfigBlockWrapper<*>? = null
	open val parent: ConfigBlockLayer? = null
	val layers = mutableListOf<Block>()
	val settingLayers = mutableListOf<EntryLayer.Single<Setting<*>>>()
	val propertyLayers = mutableListOf<EntryLayer.Single<Property<*>>>()

	open class Block(override val parent: ConfigBlockLayer?) : ConfigBlockLayer()

	class Root : Block(null)

	fun forEachConfigBlock(
		recurse: Boolean = true,
		onBlock: ((path: List<Block>, block: Block) -> Unit)? = null,
		onProperty: ((path: List<Block>, single: EntryLayer.Single<Property<*>>) -> Unit)? = null,
		onSetting: ((path: List<Block>, single: EntryLayer.Single<Setting<*>>) -> Unit)? = null
	) {
		fun internalForEach(layer: ConfigBlockLayer, path: List<Block>) {
			if (onSetting != null) layer.settingLayers.forEach { onSetting(path, it) }
			if (onProperty != null) layer.propertyLayers.forEach { onProperty(path, it) }
			layer.layers.forEach { blockLayer ->
				onBlock?.invoke(path, blockLayer)
				if (recurse) internalForEach(blockLayer, path + blockLayer)
			}
		}
		internalForEach(this, emptyList())
	}
}