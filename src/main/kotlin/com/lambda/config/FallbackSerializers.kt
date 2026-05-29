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

package com.lambda.config

import com.fasterxml.jackson.annotation.JsonFormat
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.JavaType
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.deser.Deserializers
import tools.jackson.databind.ser.Serializers

class FallbackSerializers(
	val fallbackSerializers: Map<Class<*>, FallbackSerializer<*>>,
	val fallbackDeserializers: Map<Class<*>, FallbackDeserializer<*>>
) : Serializers, Deserializers {
	override fun findSerializer(
		config: SerializationConfig,
		type: JavaType,
		beanDescRef: BeanDescription.Supplier,
		formatOverrides: JsonFormat.Value?
	): ValueSerializer<*>? {
		fallbackSerializers.forEach { (baseType, serializer) ->
			if (baseType.isAssignableFrom(type.rawClass)) return serializer
		}
		return null
	}

	override fun findBeanDeserializer(
		type: JavaType,
		config: DeserializationConfig,
		beanDescRef: BeanDescription.Supplier
	): ValueDeserializer<*>? {
		fallbackDeserializers.forEach { (baseType, deserializer) ->
			if (baseType.isAssignableFrom(type.rawClass)) return deserializer
		}
		return null
	}

	override fun hasDeserializerFor(config: DeserializationConfig, valueType: Class<*>?) =
		valueType != null && fallbackDeserializers.any { (baseType, _) -> baseType.isAssignableFrom(valueType) }
}