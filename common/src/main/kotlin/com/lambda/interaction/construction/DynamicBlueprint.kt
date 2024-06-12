package com.lambda.interaction.construction

import com.lambda.context.SafeContext
import com.lambda.util.primitives.extension.Structure
import net.minecraft.util.math.Vec3i

data class DynamicBlueprint(
    val init: SafeContext.(Structure) -> Structure = { emptyMap() },
    val update: SafeContext.(Structure) -> Structure = { it },
) : Blueprint() {
    fun update(ctx: SafeContext) {
        structure = ctx.update(structure)
    }

    fun create(ctx: SafeContext) {
        structure = ctx.init(structure)
    }

    override var structure: Structure = emptyMap()
        private set

    companion object {
        fun offset(offset: Vec3i): SafeContext.(Structure) -> Structure = {
            it.map { (pos, state) ->
                pos.add(offset) to state
            }.toMap()
        }

        fun blueprintOnTick(
            init: SafeContext.(Structure) -> Structure = { emptyMap() },
            onTick: SafeContext.(Structure) -> Structure
        ) = DynamicBlueprint(init = init, update = onTick)

        fun Structure.toBlueprint(
            onTick: SafeContext.(Structure) -> Structure
        ) = DynamicBlueprint(init = { emptyMap() }, update = onTick)
    }
}