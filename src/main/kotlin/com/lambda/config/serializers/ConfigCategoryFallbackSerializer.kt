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

@file:Suppress("unused")

package com.lambda.config.serializers

import com.lambda.Lambda.Log
import com.lambda.config.Config
import com.lambda.config.ConfigCategory
import com.lambda.config.Deserializer
import com.lambda.config.FallbackDeserializer
import com.lambda.config.FallbackSerializer
import com.lambda.config.Property
import com.lambda.config.PropertyLayer
import com.lambda.config.Serializer
import com.lambda.config.Setting
import com.lambda.config.SettingLayer
import com.lambda.config.migration.ConfigMigrationHandler
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext

object ConfigCategoryFallbackSerializer : FallbackSerializer<ConfigCategory>(ConfigCategory::class.java) {
	override fun serialize(category: ConfigCategory, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeStartObject()
		val latestSchemaVersion = ConfigMigrationHandler.latestVersion(category)
		if (latestSchemaVersion > 1) {
			gen.writeNumberProperty(
				ConfigMigrationHandler.schemaVersionKey(category) ?: ConfigMigrationHandler.DefaultSchemaVersionKey,
				latestSchemaVersion
			)
		}
		category.configs.forEach { config ->
			val serialized = mapper.valueToTree<JsonNode>(config)
			if (serialized.isEmpty) return@forEach
			gen.writePOJOProperty(config.name, serialized)
		}
		gen.writeEndObject()
	}
}

object ConfigCategoryFallbackDeserializer : FallbackDeserializer<ConfigCategory>(ConfigCategory::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConfigCategory {
		throw initFromJsonException("ConfigCategory")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, category: ConfigCategory): ConfigCategory =
		category.apply {
			val categoryJson = mapper.readTree(p)
			configs.forEach { config ->
				val configJson = categoryJson.get(config.name) ?: return@forEach
				mapper.updateValue(config, configJson)
			}
		}
}

object ConfigFallbackSerializer : FallbackSerializer<Config>(Config::class.java) {
	override fun serialize(config: Config, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeStartObject()
		val settingLayers = config.settingLayers
		val serializedSettings = mapper.valueToTree<JsonNode>(settingLayers)
		if (!serializedSettings.isEmpty) {
			gen.writePOJOProperty(settingLayers.name, serializedSettings)
		}
		val propertyLayers = config.propertyLayers
		val serializedProperties = mapper.valueToTree<JsonNode>(propertyLayers)
		if (!serializedProperties.isEmpty) {
			gen.writePOJOProperty(propertyLayers.name, serializedProperties)
		}
		gen.writeEndObject()
	}
}

object ConfigFallbackDeserializer : FallbackDeserializer<Config>(Config::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Config {
		throw initFromJsonException("Config")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, config: Config): Config =
		config.apply {
			val configJson = mapper.readTree(p)
			val settingsJson = configJson.get(settingLayers.name)
			if (settingsJson != null && settingsJson.isObject) mapper.updateValue(settingLayers, settingsJson)
			val propertiesJson = configJson.get(propertyLayers.name)
			if (propertiesJson != null && propertiesJson.isObject) mapper.updateValue(propertyLayers, propertiesJson)
		}
}

object MultipleSerializers {
	object SettingLayerMultipleFallbackSerializer : FallbackSerializer<SettingLayer.Multiple>(SettingLayer.Multiple::class.java) {
		override fun serialize(multiple: SettingLayer.Multiple, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeStartObject()
			multiple.layers.forEach { layer ->
				if (layer is SettingLayer.Single<*, *> && !layer.setting.isModified) return@forEach
				val serialized = mapper.valueToTree<JsonNode>(layer)
				if (serialized.isObject && serialized.isEmpty) return@forEach
				gen.writePOJOProperty(layer.name, serialized)
			}
			gen.writeEndObject()
		}
	}

	object SettingLayerMultipleFallbackDeserializer : FallbackDeserializer<SettingLayer.Multiple>(SettingLayer.Multiple::class.java) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Multiple {
			throw initFromJsonException("SettingLayer.Multiple")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, multiple: SettingLayer.Multiple): SettingLayer.Multiple =
			multiple.apply {
				val multipleJson = mapper.readTree(p)
				layers.forEach { layer ->
					val layerJson = multipleJson.get(layer.name) ?: return@forEach
					mapper.updateValue(layer, layerJson)
				}
			}
	}

	object PropertyLayerMultipleFallbackSerializer : FallbackSerializer<PropertyLayer.Multiple>(PropertyLayer.Multiple::class.java) {
		override fun serialize(multiple: PropertyLayer.Multiple, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeStartObject()
			multiple.layers.forEach { layer ->
				if (layer is PropertyLayer.Single<*> && !layer.property.isModified) return@forEach
				val serialized = mapper.valueToTree<JsonNode>(layer)
				if (serialized.isObject && serialized.isEmpty) return@forEach
				gen.writePOJOProperty(layer.name, serialized)
			}
			gen.writeEndObject()
		}
	}

	object PropertyLayerMultipleFallbackDeserializer : FallbackDeserializer<PropertyLayer.Multiple>(PropertyLayer.Multiple::class.java) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PropertyLayer.Multiple {
			throw initFromJsonException("PropertyLayer.Multiple")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, multiple: PropertyLayer.Multiple) =
			multiple.apply {
				val multipleJson = mapper.readTree(p)
				layers.forEach { layer ->
					val layerJson = multipleJson.get(layer.name) ?: return@forEach
					mapper.updateValue(layer, layerJson)
				}
			}
	}
}

object SingleSerializers {
	object SettingLayerSingleFallbackSerializer : FallbackSerializer<SettingLayer.Single<*, *>>(SettingLayer.Single::class.java) {
		override fun serialize(single: SettingLayer.Single<*, *>, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writePOJOProperty(single.name, single.setting)
		}
	}

	object SettingLayerSingleFallbackDeserializer : FallbackDeserializer<SettingLayer.Single<*, *>>(SettingLayer.Single::class.java) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Single<*, *> {
			throw initFromJsonException("SettingLayer.Single")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, single: SettingLayer.Single<*, *>): SettingLayer.Single<*, *> =
			single.apply {
				try {
					mapper.updateValue(setting, mapper.readTree(p))
				} catch (e: Throwable) {
					Log.error("Failed to deserialize setting '${name}'", e)
				}
			}
	}

	object PropertyLayerSingleSerializer : Serializer<PropertyLayer.Single<*>>(PropertyLayer.Single::class.java) {
		override fun serialize(single: PropertyLayer.Single<*>, gen: JsonGenerator, ctxt: SerializationContext?) {
			gen.writePOJOProperty(single.name, single.property.value)
		}
	}

	object PropertyLayerSingleDeserializer : Deserializer<PropertyLayer.Single<*>>(PropertyLayer.Single::class.java) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PropertyLayer.Single<*> {
			throw initFromJsonException("PropertyLayer.Single")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, single: PropertyLayer.Single<*>) =
			single.apply {
				@Suppress("unchecked_cast")
				try {
					(property as Property<Any>).value = ctxt.readValue(p, property.value.javaClass)
				} catch (e: Throwable) {
					Log.error("Failed to deserialize property '${name}'", e)
				}
			}
	}
}

object SettingFallbackSerializer : FallbackSerializer<Setting<*>>(Setting::class.java) {
	override fun serialize(setting: Setting<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writePOJO(setting.originalCore.value)
	}
}

object SettingFallbackDeserializer : FallbackDeserializer<Setting<*>>(Setting::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Setting<*> {
		throw initFromJsonException("Setting")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, setting: Setting<*>) =
		setting.apply {
			@Suppress("unchecked_cast")
			(this as Setting<Any>).originalCore.value = ctxt.readValue(p, originalCore.value.javaClass)
		}
}
