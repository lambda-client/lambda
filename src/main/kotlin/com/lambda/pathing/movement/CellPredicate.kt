package com.lambda.pathing.movement

import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.VoxelPos

fun interface CellPredicate {
    fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean

    val extraReads: List<VoxelPos> get() = emptyList()

    companion object {

        val SUPPORT = below { view, x, y, z ->
            view.standingSurface(x, y, z) != null && !view.voxel(x, y, z).intrudesAbove
        }

        val CENTER_SLICE = below { view, x, y, z ->
            view.voxel(x, y, z).centerPassable && !view.voxel(x, y - 1, z).intrudesAbove
        }

        val FULL_SLICE = below { view, x, y, z ->
            view.voxel(x, y, z).fullyPassable && !view.voxel(x, y - 1, z).intrudesAbove
        }

        val CENTER_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).centerPassable }

        val FULL_HEAD = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

        fun mediumIs(medium: Medium) = CellPredicate { view, x, y, z -> view.medium(x, y, z) == medium }

        val CLIMBABLE = mediumIs(Medium.CLIMBABLE)

        private fun below(test: CellPredicate) = object : CellPredicate {
            override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int) = test.matches(view, x, y, z)
            override val extraReads = listOf(VoxelPos(0, -1, 0))
        }
    }
}

data class CellCondition(val offset: VoxelPos, val predicate: CellPredicate) {
    constructor(dx: Int, dy: Int, dz: Int, predicate: CellPredicate) :
        this(VoxelPos(dx, dy, dz), predicate)

    fun matches(view: CoarseVoxelView, originX: Int, originY: Int, originZ: Int): Boolean =
        predicate.matches(view, originX + offset.x, originY + offset.y, originZ + offset.z)

    fun reads(): List<VoxelPos> = buildList {
        add(offset)
        predicate.extraReads.forEach {
            add(VoxelPos(offset.x + it.x, offset.y + it.y, offset.z + it.z))
        }
    }
}
