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

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Lifecycle
import com.mojang.serialization.ListBuilder
import com.mojang.serialization.MapLike
import com.mojang.serialization.RecordBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.UnaryOperator
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * A translation of Minecraft's [com.mojang.serialization.JsonOps], built to use Jackson instead of Gson
 */
class JsonOps private constructor(private val compressed: Boolean) : DynamicOps<JsonNode> {
	@Suppress("unused")
	companion object {
		val Uncompressed = JsonOps(false)
		val Compressed = JsonOps(true)
	}

	override fun empty(): JsonNode = NullNode.getInstance()

	override fun emptyMap(): JsonNode = JsonNodeFactory.instance.objectNode()

	override fun emptyList(): JsonNode = JsonNodeFactory.instance.arrayNode()

	override fun <U> convertTo(outOps: DynamicOps<U>, input: JsonNode): U {
		return when {
			input.isObject -> convertMap(outOps, input)
			input.isArray -> convertList(outOps, input)
			input.isNull -> outOps.empty()
			input.isString -> outOps.createString(input.stringValue())
			input.isBoolean -> outOps.createBoolean(input.booleanValue())
			input.isNumber -> {
				when (val number = input.numberValue()) {
					is Byte -> outOps.createByte(number)
					is Short -> outOps.createShort(number)
					is Int -> {
						val asByte = number.toByte()
						if (asByte.toInt() == number) outOps.createByte(asByte)
						else {
							val asShort = number.toShort()
							if (asShort.toInt() == number) outOps.createShort(asShort)
							else outOps.createInt(number)
						}
					}
					is Long -> {
						val asByte = number.toByte()
						if (asByte.toLong() == number) outOps.createByte(asByte)
						else {
							val asShort = number.toShort()
							if (asShort.toLong() == number) outOps.createShort(asShort)
							else {
								val asInt = number.toInt()
								if (asInt.toLong() == number) outOps.createInt(asInt)
								else outOps.createLong(number)
							}
						}
					}
					is Float -> outOps.createFloat(number)
					is Double -> outOps.createDouble(number)
					else -> {
						val big = number as? BigDecimal ?: BigDecimal(number.toString())
						try {
							when (val l = big.longValueExact()) {
								l.toByte().toLong() -> outOps.createByte(l.toByte())
								l.toShort().toLong() -> outOps.createShort(l.toShort())
								l.toInt().toLong() -> outOps.createInt(l.toInt())
								else -> outOps.createLong(l)
							}
						} catch (_: ArithmeticException) {
							val d = big.toDouble()
							if (d.toFloat().toDouble() == d) outOps.createFloat(d.toFloat())
							else outOps.createDouble(d)
						}
					}
				}
			}
			else -> throw IllegalArgumentException("Unknown JSON node type: $input")
		}
	}

	override fun getNumberValue(input: JsonNode): DataResult<Number> =
		when {
			input.isNumber -> DataResult.success(input.numberValue())
			compressed && input.isString -> {
				try {
					DataResult.success(Integer.parseInt(input.stringValue()))
				} catch (e: NumberFormatException) {
					DataResult.error { "Not a number: $e $input" }
				}
			}
			else -> DataResult.error { "Not a number: $input" }
		}

	override fun createNumeric(i: Number): JsonNode =
		when (i) {
			is Byte -> JsonNodeFactory.instance.numberNode(i)
			is Short -> JsonNodeFactory.instance.numberNode(i)
			is Int -> JsonNodeFactory.instance.numberNode(i)
			is Long -> JsonNodeFactory.instance.numberNode(i)
			is BigInteger -> JsonNodeFactory.instance.numberNode(i)
			is Float -> JsonNodeFactory.instance.numberNode(i)
			is Double -> JsonNodeFactory.instance.numberNode(i)
			is BigDecimal -> JsonNodeFactory.instance.numberNode(i)
			else -> throw IllegalStateException("Unknown number type: $i")
		}

	override fun getBooleanValue(input: JsonNode): DataResult<Boolean> =
		if (input.isBoolean) DataResult.success(input.booleanValue())
		else DataResult.error { "Not a boolean: $input" }

	override fun createBoolean(value: Boolean): JsonNode = JsonNodeFactory.instance.booleanNode(value)

	override fun getStringValue(input: JsonNode): DataResult<String> =
		when {
			input.isString -> DataResult.success(input.stringValue())
			compressed && input.isNumber -> DataResult.success(input.asString())
			else -> DataResult.error { "Not a string: $input" }
		}

	override fun createString(value: String): JsonNode = JsonNodeFactory.instance.stringNode(value)

	override fun mergeToList(list: JsonNode, value: JsonNode): DataResult<JsonNode> {
		if (!list.isArray && list != empty()) {
			return DataResult.error({ "mergeToList called with not a list: $list" }, list)
		}

		val result = JsonNodeFactory.instance.arrayNode()
		if (list != empty()) {
			result.addAll(list as ArrayNode)
		}
		result.add(value)
		return DataResult.success(result)
	}

	override fun mergeToList(list: JsonNode, values: List<JsonNode>): DataResult<JsonNode> {
		if (!list.isArray && list != empty()) {
			return DataResult.error({ "mergeToList called with not a list: $list" }, list)
		}

		if (values.isEmpty()) {
			return if (list == empty()) DataResult.success(emptyList())
			else DataResult.success(list)
		}

		val result = JsonNodeFactory.instance.arrayNode()
		if (list != empty()) {
			result.addAll(list as ArrayNode)
		}
		values.forEach(result::add)
		return DataResult.success(result)
	}

	override fun mergeToMap(map: JsonNode, key: JsonNode, value: JsonNode): DataResult<JsonNode> {
		if (!map.isObject && map != empty()) {
			return DataResult.error({ "mergeToMap called with not a map: $map" }, map)
		}
		if (!key.isString && !compressed) {
			return DataResult.error({ "key is not a string: $key" }, map)
		}

		val output = JsonNodeFactory.instance.objectNode()
		if (map != empty()) {
			output.setAll(map as ObjectNode)
		}
		val keyStr = if (key.isString) key.stringValue() else key.asString()
		output.set(keyStr, value)

		return DataResult.success(output)
	}

	override fun mergeToMap(map: JsonNode, values: MapLike<JsonNode>): DataResult<JsonNode> {
		if (!map.isObject && map != empty()) {
			return DataResult.error({ "mergeToMap called with not a map: $map" }, map)
		}

		val valuesIterator = values.entries().iterator()
		if (!valuesIterator.hasNext()) {
			return if (map == empty()) DataResult.success(emptyMap())
			else DataResult.success(map)
		}

		val output = JsonNodeFactory.instance.objectNode()
		if (map != empty()) {
			output.setAll(map as ObjectNode)
		}

		val missed = mutableListOf<JsonNode>()

		valuesIterator.forEachRemaining { (key, value) ->
			if (!key.isString && !compressed) {
				missed.add(key)
				return@forEachRemaining
			}
			val keyStr = if (key.isString) key.stringValue() else key.asString()
			output.set(keyStr, value)
		}

		if (missed.isNotEmpty()) {
			return DataResult.error({ "some keys are not strings: $missed" }, output)
		}

		return DataResult.success(output)
	}

	override fun getMapValues(input: JsonNode): DataResult<Stream<Pair<JsonNode, JsonNode>>> {
		if (!input.isObject) {
			return DataResult.error { "Not a JSON object: $input" }
		}
		val obj = input as ObjectNode
		return DataResult.success(obj.propertyStream().map { (key, value) ->
			Pair(createString(key), if (value.isNull) null else value)
		})
	}

	override fun getMapEntries(input: JsonNode): DataResult<Consumer<BiConsumer<JsonNode, JsonNode>>> {
		return if (!input.isObject) {
			DataResult.error { "Not a JSON object: $input" }
		} else DataResult.success(Consumer { c ->
			val obj = input as ObjectNode
			obj.propertyStream().map { (key, value) ->
				if (!value.isNull) c.accept(createString(key), value)
			}
		})
	}

	override fun getMap(input: JsonNode): DataResult<MapLike<JsonNode>> {
		if (!input.isObject) {
			return DataResult.error { "Not a JSON object: $input" }
		}
		val obj = input as ObjectNode
		return DataResult.success(object : MapLike<JsonNode> {
			override fun get(key: JsonNode): JsonNode? {
				if (!key.isString) return null
				val element = obj.get(key.stringValue())
				return if (element != null && element.isNull) null else element
			}

			override fun get(key: String): JsonNode? {
				val element = obj.get(key)
				return if (element != null && element.isNull) null else element
			}

			override fun entries(): Stream<Pair<JsonNode, JsonNode>> {
				return obj.propertyStream().map { (key, value) ->
					Pair(createString(key), value)
				}
			}

			override fun toString(): String = "MapLike[$obj]"
		})
	}

	override fun createMap(map: Stream<Pair<JsonNode, JsonNode>>): JsonNode {
		val result = JsonNodeFactory.instance.objectNode()
		map.forEach { (key, value) ->
			if (!key.isString) throw IllegalArgumentException("Key is not a string: $key")
			result.set(key.stringValue(), value)
		}
		return result
	}

	override fun getStream(input: JsonNode): DataResult<Stream<JsonNode>> {
		return if (input.isArray) {
			val array = input as ArrayNode
			DataResult.success(StreamSupport.stream(array.spliterator(), false)
				.map { if (it.isNull) null else it })
		} else DataResult.error { "Not a json array: $input" }
	}

	override fun getList(input: JsonNode): DataResult<Consumer<Consumer<JsonNode>>> {
		if (input.isArray) {
			val array = input as ArrayNode
			return DataResult.success(Consumer { c ->
				array.forEach { element ->
					if (!element.isNull) c.accept(element)
				}
			})
		}
		return DataResult.error { "Not a json array: $input" }
	}

	override fun createList(input: Stream<JsonNode>): JsonNode {
		val result = JsonNodeFactory.instance.arrayNode()
		input.forEach(result::add)
		return result
	}

	override fun remove(input: JsonNode, key: String): JsonNode {
		if (input.isObject) {
			val result = JsonNodeFactory.instance.objectNode()
			val obj = input as ObjectNode
			obj.propertyStream().forEach { (k, v) ->
				if (!Objects.equals(k, key)) {
					result.set(k, v)
				}
			}
			return result
		}
		return input
	}

	override fun toString(): String = "JSON"

	override fun listBuilder(): ListBuilder<JsonNode> = ArrayBuilder()

	private inner class ArrayBuilder : ListBuilder<JsonNode> {
		private var builder: DataResult<ArrayNode> = DataResult.success(JsonNodeFactory.instance.arrayNode(), Lifecycle.stable())

		override fun ops(): DynamicOps<JsonNode> = this@JsonOps

		override fun add(value: JsonNode): ListBuilder<JsonNode> =
			apply {
				builder = builder.map { b ->
					b.add(value)
					b
				}
			}

		override fun add(value: DataResult<JsonNode>): ListBuilder<JsonNode> =
			apply {
				builder = builder.apply2stable({ b, element ->
					b.add(element)
					b
				}, value)
			}

		override fun withErrorsFrom(result: DataResult<*>): ListBuilder<JsonNode> =
			apply { builder = builder.flatMap { r -> result.map { r } } }

		override fun mapError(onError: UnaryOperator<String>): ListBuilder<JsonNode> =
			apply { builder = builder.mapError(onError) }

		override fun build(prefix: JsonNode): DataResult<JsonNode> {
			val result = builder.flatMap { b ->
				if (!prefix.isArray && prefix != ops().empty()) {
					return@flatMap DataResult.error({ "Cannot append a list to not a list: $prefix" }, prefix)
				}
				val array = JsonNodeFactory.instance.arrayNode()
				if (prefix != ops().empty()) {
					array.addAll(prefix as ArrayNode)
				}
				array.addAll(b)
				DataResult.success(array, Lifecycle.stable())
			}

			builder = DataResult.success(JsonNodeFactory.instance.arrayNode(), Lifecycle.stable())
			return result
		}
	}

	override fun compressMaps(): Boolean = compressed

	override fun mapBuilder(): RecordBuilder<JsonNode> = JsonRecordBuilder()

	private inner class JsonRecordBuilder : RecordBuilder.AbstractStringBuilder<JsonNode, ObjectNode>(this@JsonOps) {
		override fun initBuilder(): ObjectNode = JsonNodeFactory.instance.objectNode()

		override fun append(key: String, value: JsonNode, builder: ObjectNode): ObjectNode =
			builder.apply { builder.set(key, value) }

		override fun build(builder: ObjectNode, prefix: JsonNode): DataResult<JsonNode> {
			return when {
				prefix.isNull -> DataResult.success(builder)
				prefix.isObject -> {
					val result = JsonNodeFactory.instance.objectNode()
					result.setAll(prefix as ObjectNode)
					result.setAll(builder)
					DataResult.success(result)
				}
				else -> DataResult.error({ "mergeToMap called with not a map: $prefix" }, prefix)
			}
		}
	}

	private operator fun Pair<JsonNode, JsonNode>.component1(): JsonNode = first
	private operator fun Pair<JsonNode, JsonNode>.component2(): JsonNode = second
}