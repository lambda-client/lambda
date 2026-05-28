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

package com.lambda.config.migration

import com.lambda.config.Config
import com.lambda.config.Config.SettingLayer
import com.lambda.config.ConfigCategory
import com.lambda.util.CommunicationUtils.logError
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

object MigrationUtils {
	fun locateAndMoveMisplacedSettings(category: ConfigCategory, rootNode: ObjectNode): Int {
		var settingsMoved = 0
		category.configs.forEach { config ->
			val configNode = rootNode.get(config.name) ?: return@forEach
			if (!configNode.isObject) return@forEach
			settingsMoved += locateAndMoveMisplacedSettings(config, configNode.asObject())
		}
		return settingsMoved
	}

	private fun locateAndMoveMisplacedSettings(config: Config, rootNode: ObjectNode): Int {
		var settingsMoved = 0
		fun walk(multiple: SettingLayer.Multiple, obj: ObjectNode) {
			config.forEachSetting(
				multiple,
				false,
				{ _, childMultiple ->
					val existing = obj.get(childMultiple.name)
					val nestedObj: ObjectNode = when {
						existing != null && existing.isObject -> existing.asObject()
						existing == null || existing.isNull -> obj.putObject(childMultiple.name)
						else -> return@forEachSetting
					}
					try {
						walk(childMultiple, nestedObj)
					} catch (e: Throwable) {
						logError("Failed to relocate settings in ${childMultiple.multipleType.toString().lowercase()} '${childMultiple.name}' in '${config.name}'", e)
					}
				}
			) { path, single ->
				if (obj.has(single.name)) return@forEachSetting

				val fallbackValue = locateAndRemove(single.name, path, rootNode) ?: return@forEachSetting
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
		parentLayers: List<SettingLayer.Multiple>,
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