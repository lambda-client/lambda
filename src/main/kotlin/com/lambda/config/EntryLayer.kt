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

import com.lambda.config.entries.Property
import com.lambda.config.entries.Setting
import com.lambda.util.Nameable

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