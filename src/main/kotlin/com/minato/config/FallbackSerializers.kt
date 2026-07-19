
package com.minato.config

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
 * class (e.g., [com.minato.config.EntryCore]) that handles all its subclasses.
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

	private fun inheritanceDistance(sub: Class<*>, target: Class<*>): Int {
		if (sub == target) return 0

		val queue = ArrayDeque<Pair<Class<*>, Int>>()
		val visited = HashSet<Class<*>>()
		queue.add(sub to 0)
		visited.add(sub)

		while (queue.isNotEmpty()) {
			val (current, distance) = queue.removeFirst()

			current.superclass?.let { superclass ->
				if (superclass == target) return distance + 1
				if (visited.add(superclass)) queue.add(superclass to distance + 1)
			}

			current.interfaces.forEach { i ->
				if (i == target) return distance + 1
				if (visited.add(i)) queue.add(i to distance + 1)
			}
		}

		return Int.MAX_VALUE
	}
}
