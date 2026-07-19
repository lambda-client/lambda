
package com.minato.interaction.construction.blueprint

import com.minato.context.SafeContext
import com.minato.threading.runSafe
import com.minato.util.extension.Structure
import net.minecraft.util.math.Vec3i

data class PropagatingBlueprint(
    val onFinish: SafeContext.(Structure) -> Structure? = { it },
) : Blueprint() {
    fun next() =
        runSafe {
            onFinish(structure)?.also { new ->
                structure = new
            }
        }

    override var structure: Structure = emptyMap()
        private set(value) {
            field = value
            bounds.update()
        }

    override fun toString() = "Propagating Blueprint at ${center?.toShortString()}"

    companion object {
        fun offset(offset: Vec3i): SafeContext.(Structure) -> Structure? = {
            it.map { (pos, state) ->
                pos.add(offset) to state
            }.toMap()
        }

        fun propagatingBlueprint(
            onFinish: SafeContext.(Structure) -> Structure?,
        ) = PropagatingBlueprint(onFinish)
    }
}
