
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.Minato.LOG
import com.minato.config.Config
import com.minato.config.ConfigCategory
import com.minato.config.ConfigEntry
import com.minato.config.EntryLayer
import com.minato.config.FallbackDeserializer
import com.minato.config.FallbackSerializer
import com.minato.config.migration.ConfigMigrationHandler
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
				ConfigMigrationHandler.schemaVersionKey(category) ?: ConfigMigrationHandler.DEFAULT_SCHEMA_VERSION_KEY,
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

object MultipleFallbackSerializer : FallbackSerializer<EntryLayer.Multiple<*>>(EntryLayer.Multiple::class.java) {
	override fun serialize(multiple: EntryLayer.Multiple<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeStartObject()
		multiple.layers.forEach { layer ->
			if (layer is EntryLayer.Single<*> && !layer.entry.isModified) return@forEach
			val serialized = mapper.valueToTree<JsonNode>(layer)
			if (serialized.isObject && serialized.isEmpty) return@forEach
			gen.writePOJOProperty(layer.name, serialized)
		}
		gen.writeEndObject()
	}
}

object MultipleFallbackDeserializer : FallbackDeserializer<EntryLayer.Multiple<*>>(EntryLayer.Multiple::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EntryLayer.Multiple<*> {
		throw initFromJsonException("SettingLayer.Multiple")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, multiple: EntryLayer.Multiple<*>): EntryLayer.Multiple<*> =
		multiple.apply {
			val multipleJson = mapper.readTree(p)
			layers.forEach { layer ->
				val layerJson = multipleJson.get(layer.name) ?: return@forEach
				mapper.updateValue(layer, layerJson)
			}
		}
}

object SingleFallbackSerializer : FallbackSerializer<EntryLayer.Single<*>>(EntryLayer.Single::class.java) {
	override fun serialize(single: EntryLayer.Single<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writePOJO(single.entry)
	}
}

object SingleFallbackDeserializer : FallbackDeserializer<EntryLayer.Single<*>>(EntryLayer.Single::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EntryLayer.Single<*> {
		throw initFromJsonException("SettingLayer.Single")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, single: EntryLayer.Single<*>): EntryLayer.Single<*> =
		single.apply {
			try {
				mapper.updateValue(entry, mapper.readTree(p))
			} catch (e: Throwable) {
				LOG.error("Failed to deserialize setting '${name}'", e)
			}
		}
}

object ConfigEntryFallbackSerializer : FallbackSerializer<ConfigEntry<*>>(ConfigEntry::class.java) {
	override fun serialize(entry: ConfigEntry<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writePOJO(entry.originalCore.value)
	}
}

object ConfigEntryFallbackDeserializer : FallbackDeserializer<ConfigEntry<*>>(ConfigEntry::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConfigEntry<*> {
		throw initFromJsonException("ConfigEntry")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, entry: ConfigEntry<*>) =
		entry.apply {
			@Suppress("unchecked_cast")
			(this as ConfigEntry<Any>).originalCore.value = ctxt.readValue(p, originalCore.value.javaClass)
		}
}
