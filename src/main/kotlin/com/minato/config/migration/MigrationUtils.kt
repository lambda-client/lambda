
package com.minato.config.migration

import com.minato.config.Config
import com.minato.config.ConfigCategory
import com.minato.config.EntryLayer
import com.minato.config.entries.Setting
import com.minato.util.CommunicationUtils.logError
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

object MigrationUtils {
	fun locateAndMoveMisplacedSettings(category: ConfigCategory, rootNode: ObjectNode): Int {
		var settingsMoved = 0
		category.configs.forEach { config ->
			val configNode = rootNode.get(config.name) ?: return@forEach
			if (!configNode.isObject) return@forEach
			val objNode = configNode.asObject()
			moveContentsIntoSettings(objNode)
			settingsMoved += locateAndMoveMisplacedSettings(config, objNode.get("Settings") as? ObjectNode ?: return@forEach)
		}
		return settingsMoved
	}

	fun moveContentsIntoSettings(configNode: ObjectNode) {
		val settingsNode = configNode.objectNode()
		configNode.propertyStream().forEach { (key, value) ->
			settingsNode.set(key, value)
		}

		configNode.removeAll()
		configNode.set("Settings", settingsNode)
	}

	private fun locateAndMoveMisplacedSettings(config: Config, rootNode: ObjectNode): Int {
		var settingsMoved = 0
		fun walk(multiple: EntryLayer.Multiple<Setting<*>>, obj: ObjectNode) {
			multiple.forEachEntry(
				false,
				{ _, childMultiple ->
					val existing = obj.get(childMultiple.name)
					val nestedObj: ObjectNode = when {
						existing != null && existing.isObject -> existing.asObject()
						existing == null || existing.isNull -> obj.putObject(childMultiple.name)
						else -> return@forEachEntry
					}
					try {
						walk(childMultiple, nestedObj)
					} catch (e: Throwable) {
						logError("Failed to relocate settings in ${childMultiple.multipleType.toString().lowercase()} '${childMultiple.name}' in '${config.name}'", e)
					}
				}
			) { path, single ->
				if (obj.has(single.name)) return@forEachEntry

				val fallbackValue = locateAndRemove(single.name, path, rootNode) ?: return@forEachEntry
				try {
					obj.set(single.name, fallbackValue)
					settingsMoved++
				} catch (e: Throwable) {
					logError("Failed to relocate setting '${single.name}' in '${config.name}'", e)
				}
			}
		}

		walk(config.settingLayers, rootNode)
		return settingsMoved
	}

	private fun locateAndRemove(
		settingName: String,
		parentLayers: List<EntryLayer.Multiple<Setting<*>>>,
		rootObj: ObjectNode
	): JsonNode? {
		var bestMatch: JsonNode? = null
		var bestMatchKey: String? = null
		var bestMatchParent: ObjectNode? = null
		var bestMatchScore = 0

		fun search(currentObj: ObjectNode, jsonPath: List<String>) {
			currentObj.propertyStream().forEach { (key, element) ->
				if (key.endsWith(settingName, ignoreCase = true)) {
					var score = 0
					if (key.equals(settingName, ignoreCase = true)) {
						score += 10
					}

					val fullPathString = (jsonPath + key).joinToString("")
					parentLayers.forEach { layer ->
						if (fullPathString.contains(layer.name, ignoreCase = true)) {
							score += 5
						}
					}

					if (score > bestMatchScore) {
						bestMatchScore = score
						bestMatch = element
						bestMatchKey = key
						bestMatchParent = currentObj
					}
				}

				if (element.isObject) {
					search(element.asObject(), jsonPath + key)
				}
			}
		}

		search(rootObj, emptyList())

		if (bestMatch != null && bestMatchParent != null && bestMatchKey != null) {
			bestMatchParent.remove(bestMatchKey)
		}

		return bestMatch
	}
}