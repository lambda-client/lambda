
package com.minato.graphics.util

import com.minato.Minato.mc
import com.minato.util.extension.prevPos
import com.minato.util.extension.tickDelta
import com.minato.util.math.lerp
import com.minato.util.math.minus
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box

class DynamicAABB {
    private var prev: Box? = null
    private var curr: Box? = null

    val pair get() = prev?.let { prev -> curr?.let { curr -> prev to curr } }

    fun update(box: Box): DynamicAABB {
        prev = curr ?: box
        curr = box

        return this
    }

    fun box(tickDelta: Double): Box? =
        prev?.let { prev ->
            curr?.let { curr ->
                lerp(tickDelta, prev, curr)
            }
        }

    fun reset() {
        prev = null
        curr = null
    }

    companion object {
        val Entity.interpolatedBox
            get() = boundingBox.let { box ->
                lerp(mc.tickDelta, box.offset(prevPos - pos), box)
            }
        val Entity.dynamicBox
            get() = DynamicAABB().apply {
                update(boundingBox.offset(prevPos - pos))
                update(boundingBox)
            }
    }
}
