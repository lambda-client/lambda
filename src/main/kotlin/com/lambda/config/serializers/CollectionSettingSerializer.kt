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

import com.fasterxml.jackson.core.JsonParseException
import com.lambda.config.FallbackDeserializer
import com.lambda.config.FallbackSerializer
import com.lambda.config.settings.collections.CollectionSetting
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object CollectionSettingSerializer : FallbackSerializer<CollectionSetting<*>>(CollectionSetting::class.java) {
	override fun serialize(setting: CollectionSetting<*>, gen: JsonGenerator, ctxt: SerializationContext) {
		if (setting.serialize) mapper.writeValue(gen, setting.coreValue)
		else {
			gen.writeStartArray()
			setting.coreValue.forEach { element ->
				gen.writeString(element.toString())
			}
			gen.writeEndArray()
		}
	}
}

object CollectionSettingDeserializer : FallbackDeserializer<CollectionSetting<*>>(CollectionSetting::class.java) {
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
				if (!node.isArray) throw JsonParseException("CollectionSetting's serialized value is not an array.")
				node.forEach { element ->
					if (!element.isString) throw JsonParseException("CollectionSetting's serialized array contains a non-string value. A CollectionSetting's JSON array must only contain strings if CollectionSetting.serialize is false.")
					val str = element.stringValue()
					val matched = immutableCollection.find { it.toString() == str }
					if (matched != null) newValue.add(matched)
				}
			}

			@Suppress("unchecked_cast")
			(this as CollectionSetting<Any>).coreValue = newValue
		}
}