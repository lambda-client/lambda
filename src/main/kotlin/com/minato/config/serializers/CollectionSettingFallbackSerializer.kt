
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.FallbackDeserializer
import com.minato.config.FallbackSerializer
import com.minato.config.settings.collections.CollectionSetting
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object CollectionSettingFallbackSerializer : FallbackSerializer<CollectionSetting<*>>(CollectionSetting::class.java) {
	override fun serialize(setting: CollectionSetting<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		if (setting.serialize) mapper.writeValue(gen, setting.originalCore.value)
		else {
			gen.writeStartArray()
			setting.originalCore.value.forEach { element ->
				gen.writeString(element.toString())
			}
			gen.writeEndArray()
		}
	}
}

object CollectionSettingFallbackDeserializer : FallbackDeserializer<CollectionSetting<*>>(CollectionSetting::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CollectionSetting<*> {
		throw initFromJsonException("CollectionSetting")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, settingCore: CollectionSetting<*>): CollectionSetting<*> =
		settingCore.apply {
			val newValue = mutableListOf<Any>()

			if (serialize) {
				val tree = mapper.readTree(p)
				val deserialized = mapper.treeToValue<Collection<Any>>(tree, type)
				newValue.addAll(deserialized)
			} else {
				val node = mapper.readTree(p)
				if (!node.isArray) throw IllegalStateException("CollectionSetting's serialized value is not an array.")
				node.forEach { element ->
					if (!element.isString) throw IllegalStateException("CollectionSetting's serialized array contains a non-string value. A CollectionSetting's JSON array must only contain strings if CollectionSetting.serialize is false.")
					val str = element.stringValue()
					val matched = immutableCollection.find { it.toString() == str }
					if (matched != null) newValue.add(matched)
				}
			}

			@Suppress("unchecked_cast")
			(this as CollectionSetting<Any>).originalCore.value = newValue
		}
}