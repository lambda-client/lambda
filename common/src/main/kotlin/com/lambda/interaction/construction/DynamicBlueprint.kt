package com.lambda.interaction.construction

import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.primitives.extension.Structure
import net.minecraft.util.math.Vec3i

data class DynamicBlueprint(
    val init: SafeContext.(Structure) -> Structure = { emptyMap() },
    val onTick: SafeContext.(Structure) -> Structure = { it },
    val onDone: SafeContext.(Structure) -> Structure? = { null }
) : Blueprint() {
    fun onTick(ctx: SafeContext) {
        structure = ctx.onTick(structure)
    }

    fun onDone(ctx: SafeContext): Boolean {
        structure = ctx.onDone(structure) ?: return true
        return false
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
        ) = DynamicBlueprint(init, onTick = onTick)

        fun blueprintOnDone(
            init: SafeContext.(Structure) -> Structure = { emptyMap() },
            onDone: SafeContext.(Structure) -> Structure
        ) = DynamicBlueprint(init, onDone = onDone)

        fun Structure.toBlueprint(
            onTick: SafeContext.(Structure) -> Structure
        ) = DynamicBlueprint({ emptyMap() }, onTick)
    }
}