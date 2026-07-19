
@file:Suppress("unused")

package com.minato.config.serializers

import com.minato.config.Deserializer
import com.minato.config.JsonOps
import com.minato.config.Serializer
import com.minato.config.Stringifiable
import net.minecraft.fluid.Fluid
import net.minecraft.registry.Registries
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext

object FluidSerializer : Serializer<Fluid>(Fluid::class.java), Stringifiable<Fluid> {
	override fun serialize(fluid: Fluid, gen: JsonGenerator, ctxt: SerializationContext) {
		gen.writeTree((Registries.FLUID.codec.encodeStart(JsonOps.UNCOMPRESSED, fluid).orThrow))
	}

	override fun stringify(value: Fluid) = Registries.FLUID.getId(value).path.replaceFirstChar { it.uppercase() }
}

object FluidDeserializer : Deserializer<Fluid>(Fluid::class.java) {
	override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Fluid =
		Registries.FLUID.codec.parse(JsonOps.UNCOMPRESSED, p.readValueAsTree()).orThrow
}