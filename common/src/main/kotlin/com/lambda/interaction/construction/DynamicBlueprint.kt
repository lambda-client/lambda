package com.lambda.interaction.construction

import com.lambda.context.SafeContext
import com.lambda.util.primitives.extension.Structure
import net.minecraft.util.math.Vec3i

data class DynamicBlueprint(
    val initial: Structure = emptyMap(),
    val update: SafeContext.(Structure) -> Structure,
) : Blueprint() {
    fun update(safeContext: SafeContext) =
        safeContext.update(structure)

    override val structure: Structure by lazy { initial }

    companion object {
        fun offset(offset: Vec3i): SafeContext.(Structure) -> Structure = {
            it.map { (pos, state) ->
                pos.add(offset) to state
            }.toMap()
        }

        fun Structure.toBlueprint(
            update: SafeContext.(Structure) -> Structure
        ) = DynamicBlueprint(this, update)
    }
}