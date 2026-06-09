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

import com.lambda.config.Deserializer
import com.lambda.config.Serializer
import com.lambda.config.entries.Setting
import com.lambda.config.settings.collections.ClassCollectionSetting
import com.lambda.util.ReflectionUtils.className
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