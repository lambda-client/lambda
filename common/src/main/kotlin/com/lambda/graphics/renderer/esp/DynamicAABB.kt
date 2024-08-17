package com.lambda.graphics.renderer.esp

import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.plus
import com.lambda.util.primitives.extension.max
import com.lambda.util.primitives.extension.min
import com.lambda.util.primitives.extension.prevPos
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box

class DynamicAABB {
    private var prev: Box? = null
    private var curr: Box? = null

    fun update(box: Box): DynamicAABB {
        prev = curr ?: box
        curr = box

        return this
    }

    fun reset() {
        prev = null
        curr = null
    }

    fun getBoxPair(): Pair<Box, Box>? {
        prev?.let { previous ->
            curr?.let { current ->
                return previous to current
            }
        }

        return null
    }

    companion object {
        val Entity.dynamicBox get() = DynamicAABB().apply {
            update(boundingBox.offset(prevPos - pos))
            update(boundingBox)
        }
    }
}