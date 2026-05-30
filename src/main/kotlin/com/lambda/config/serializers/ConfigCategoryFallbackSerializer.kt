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
import com.lambda.config.Config.SettingLayer
import com.lambda.config.ConfigCategory
import com.lambda.config.FallbackDeserializer
import com.lambda.config.FallbackSerializer
import com.lambda.config.SettingCore
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
		gen.writeObjectPropertyStart(config.name)
			.writePOJO(config.settingLayers)
			.writeEndObject()
	}
}

object ConfigFallbackDeserializer : FallbackDeserializer<Config>(Config::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Config {
		throw initFromJsonException("Config")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, config: Config): Config =
		config.apply { mapper.updateValue(settingLayers, mapper.readTree(p)) }
}

object MultipleFallbackSerializer : FallbackSerializer<SettingLayer.Multiple>(SettingLayer.Multiple::class.java) {
	override fun serialize(multiple: SettingLayer.Multiple, gen: JsonGenerator, ctxt: SerializationContext) {
		val notRoot = multiple !is SettingLayer.Root
		if (notRoot) gen.writeObjectPropertyStart(multiple.name)
		multiple.layers.forEach { layer ->
			if (layer is SettingLayer.Single<*, *> && !layer.setting.isModified) return@forEach
			val serialized = mapper.valueToTree<JsonNode>(layer)
			if (serialized.isObject && serialized.isEmpty) return@forEach
			gen.writePOJOProperty(layer.name, serialized)
		}
		if (notRoot) gen.writeEndObject()
	}
}

object MultipleFallbackDeserializer : FallbackDeserializer<SettingLayer.Multiple>(SettingLayer.Multiple::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Multiple {
		throw initFromJsonException("Multiple")
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

object SingleFallbackSerializer : FallbackSerializer<SettingLayer.Single<*, *>>(SettingLayer.Single::class.java) {
	override fun serialize(single: SettingLayer.Single<*, *>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writePOJOProperty(single.name, single.setting.originalCore)
	}
}

object SingleFallbackDeserializer : FallbackDeserializer<SettingLayer.Single<*, *>>(SettingLayer.Single::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Single<*, *> {
		throw initFromJsonException("Single")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, single: SettingLayer.Single<*, *>): SettingLayer.Single<*, *> =
		single.apply {
			try {
				mapper.updateValue(setting.originalCore, mapper.readTree(p))
			} catch (e: Throwable) {
				Log.error("Failed to deserialize setting '${name}'", e)
			}
		}
}

object SettingCoreFallbackSerializer : FallbackSerializer<SettingCore<*>>(SettingCore::class.java) {
	override fun serialize(core: SettingCore<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writePOJO(core.coreValue)
	}
}

object SettingCoreFallbackDeserializer : FallbackDeserializer<SettingCore<*>>(SettingCore::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingCore<*> {
		throw initFromJsonException("SettingCore")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, core: SettingCore<*>) =
		core.apply {
			@Suppress("unchecked_cast")
			(this as SettingCore<Any>).coreValue = mapper.treeToValue(mapper.readTree(p), coreValue.javaClass)
		}
}