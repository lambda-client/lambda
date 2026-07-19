
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.Serializer
import com.minato.config.entries.Setting
import com.minato.config.settings.collections.ClassCollectionSetting
import com.minato.util.ReflectionUtils.className
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object ClassCollectionSettingSerializer : Serializer<ClassCollectionSetting<*>>(ClassCollectionSetting::class.java) {
	override fun serialize(setting: ClassCollectionSetting<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeStartArray()
		setting.originalCore.value.forEach { element ->
			gen.writeString(element.className)
		}
		gen.writeEndArray()
	}
}

object ClassCollectionSettingDeserializer : Deserializer<ClassCollectionSetting<*>>(ClassCollectionSetting::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ClassCollectionSetting<*> {
		throw initFromJsonException("ClassCollectionSetting")
	}

	override fun deserialize(p: JsonParser, ctxt: DeserializationContext, setting: ClassCollectionSetting<*>): ClassCollectionSetting<*> =
		setting.apply {
			val arrayNode = mapper.readTree(p)
			if (!arrayNode.isArray) throw IllegalStateException("ClassCollectionSetting's serialized value is not an array.")
			val classNames = mutableListOf<String>()
			arrayNode.forEach { node ->
				if (!node.isString) throw IllegalStateException("ClassCollectionSetting's serialized array contains a non-string value.")
				if (node.isString) classNames.add(node.stringValue())
			}

			@Suppress("unchecked_cast")
			(setting as Setting<Any>).originalCore.value = classNames.mapNotNull { className ->
				setting.immutableCollection.find { it.className == className }
			}
		}
}