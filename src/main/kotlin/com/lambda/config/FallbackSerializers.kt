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

/**
 * A combined [Serializers] and [Deserializers] implementation that resolves
 * serializers/deserializers by walking the class hierarchy to find the closest
 * registered supertype. This allows registering a single serializer for a base
 * class (e.g., [com.lambda.config.SettingCore]) that handles all its subclasses.
 *
 * Exact-match serializers registered via [tools.jackson.databind.module.SimpleModule.addSerializer]
 * take priority over these fallbacks in Jackson's resolution order.
 */
class FallbackSerializers(
	private val serializers: Map<Class<*>, FallbackSerializer<*>>,
	private val deserializers: Map<Class<*>, FallbackDeserializer<*>>
) : Serializers, Deserializers {
	override fun findSerializer(
		config: SerializationConfig,
		type: JavaType,
		beanDescRef: BeanDescription.Supplier,
		formatOverrides: JsonFormat.Value?
	): ValueSerializer<*>? = findClosestSupertype(type.rawClass, serializers)

	override fun findBeanDeserializer(
		type: JavaType,
		config: DeserializationConfig,
		beanDescRef: BeanDescription.Supplier
	): ValueDeserializer<*>? = findClosestSupertype(type.rawClass, deserializers)

	override fun hasDeserializerFor(config: DeserializationConfig, valueType: Class<*>?): Boolean =
		valueType != null && findClosestSupertype(valueType, deserializers) != null

	/**
	 * Finds the value mapped to the closest supertype of [target] among the [candidates] map keys.
	 * "Closest" is determined by the shortest inheritance distance up the class hierarchy.
	 */
	private fun <V> findClosestSupertype(target: Class<*>, candidates: Map<Class<*>, V>): V? {
		var bestValue: V? = null
		var bestDistance = Int.MAX_VALUE

		candidates.forEach { (candidateClass, value) ->
			if (!candidateClass.isAssignableFrom(target)) return@forEach
			val distance = inheritanceDistance(target, candidateClass)
			if (distance < bestDistance) {
				bestDistance = distance
				bestValue = value
			}
		}
		return bestValue
	}

	private fun inheritanceDistance(sub: Class<*>, superClass: Class<*>): Int {
		var distance = 0
		var current: Class<*>? = sub
		while (current != null && current != superClass) {
			distance++
			current = current.superclass
		}
		return if (current == superClass) distance else Int.MAX_VALUE
	}
}
