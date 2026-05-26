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

import com.lambda.config.Config
import com.lambda.config.Config.SettingLayer
import com.lambda.config.ConfigCategory
import com.lambda.config.Serializer
import com.lambda.config.Setting
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.ser.std.StdSerializer

object ConfigCategorySerializer : Serializer<ConfigCategory>() {
	override val type = ConfigCategory::class.java

	override val serializer = object : StdSerializer<ConfigCategory>(type) {
		override fun serialize(category: ConfigCategory, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeStartObject()
			category.configs.forEach { config ->
				val serialized = mapper.valueToTree<JsonNode>(config)
				if (serialized.isEmpty) return@forEach
				gen.writeName(config.name)
				gen.writeTree(serialized)
			}
			gen.writeEndObject()
		}
	}

	override val deSerializer = object : StdDeserializer<ConfigCategory>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConfigCategory {
			throw initFromJsonException("ConfigCategory")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, category: ConfigCategory) =
			category.apply {
				val categoryJson = mapper.readTree(p)
				configs.forEach { config ->
					val configJson = categoryJson.get(config.name) ?: return@forEach
					mapper.updateValue(config, configJson)
				}
			}
	}
}

object ConfigSerializer : Serializer<Config>() {
	override val type = Config::class.java

	override val serializer = object : StdSerializer<Config>(type) {
		override fun serialize(config: Config, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writeObjectPropertyStart(config.name)
				.writePOJO(config.settingLayers)
				.writeEndObject()
		}
	}

	override val deSerializer = object : StdDeserializer<Config>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Config {
			throw initFromJsonException("Config")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, config: Config) =
			config.apply { mapper.updateValue(settingLayers, mapper.readTree(p)) }
	}
}

object MultipleSerializer : Serializer<SettingLayer.Multiple>() {
	override val type = SettingLayer.Multiple::class.java

	override val serializer = object : StdSerializer<SettingLayer.Multiple>(type) {
		override fun serialize(multiple: SettingLayer.Multiple, gen: JsonGenerator, ctxt: SerializationContext) {
			val notRoot = multiple !is SettingLayer.Root
			if (notRoot) gen.writeObjectPropertyStart(multiple.name)
			multiple.layers.forEach { layer ->
				if (layer is SettingLayer.Single<*, *> && !layer.setting.isModified) return@forEach
				gen.writePOJOProperty(layer.name, layer)
			}
			if (notRoot) gen.writeEndObject()
		}
	}

	override val deSerializer = object : StdDeserializer<SettingLayer.Multiple>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Multiple {
			throw initFromJsonException("Multiple")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, multiple: SettingLayer.Multiple) =
			multiple.apply {
				val multipleJson = mapper.readTree(p)
				layers.forEach { layer ->
					val layerJson = multipleJson.get(layer.name) ?: return@forEach
					mapper.updateValue(layer, layerJson)
				}
			}
	}
}

object SingleSerializer : Serializer<SettingLayer.Single<*, *>>() {
	override val type = SettingLayer.Single::class.java

	override val serializer = object : StdSerializer<SettingLayer.Single<*, *>>(type) {
		override fun serialize(single: SettingLayer.Single<*, *>, gen: JsonGenerator, ctxt: SerializationContext) {
			gen.writePOJOProperty(single.name, single.setting.value)
		}
	}

	override val deSerializer = object : StdDeserializer<SettingLayer.Single<*, *>>(type) {
		override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SettingLayer.Single<*, *> {
			throw initFromJsonException("Single")
		}

		override fun deserialize(p: JsonParser, ctxt: DeserializationContext, single: SettingLayer.Single<*, *>) =
			single.apply {
				try {
					@Suppress("unchecked_cast")
					(setting as Setting<*, Any>).value = p.readValueAs(setting.value.javaClass)
				} catch (e: Throwable) {
					throw IllegalStateException("Failed to deserialize setting '${setting.name}'", e)
				}
			}
	}
}