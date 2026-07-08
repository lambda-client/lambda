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

package com.lambda.worldview

import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf

/**
 * A hypothetical future: sparse edits over a parent [WorldView] (research
 * plan §4.2). Forking is cheap — a plan candidate forks the active view,
 * writes its intended edits, and either commits them down or is dropped.
 *
 * This is the WP1 mechanism only; the per-entry state machine (pending →
 * realized | violated | rolled-back, theory note T4) and provenance-tagged
 * invalidation land with edit-capable traversal (WP4).
 */
class OverlayWorldView(val parent: WorldView) : WorldView {
    private val edits = HashMap<FastVector, Int>()

    override fun stateId(x: Int, y: Int, z: Int): Int =
        edits[fastVectorOf(x, y, z)] ?: parent.stateId(x, y, z)

    /** Records a hypothetical edit. Overwrites an earlier edit at [pos]. */
    fun edit(pos: FastVector, stateId: Int) {
        edits[pos] = stateId
    }

    /**
     * Removes the edit at [pos], exposing the parent's value again.
     * Returns true if an edit was present.
     */
    fun revert(pos: FastVector): Boolean = edits.remove(pos) != null

    /** Independent copy over the same parent — the plan-candidate fork. */
    fun fork(): OverlayWorldView = OverlayWorldView(parent).also { it.edits.putAll(edits) }

    /**
     * Applies every edit into [target] and clears this overlay. The caller
     * owns invalidation: the returned set is exactly the voxels whose
     * resolved view may have changed.
     */
    fun commitTo(target: OverlayWorldView): Set<FastVector> {
        val changed = HashSet<FastVector>(edits.size)
        edits.forEach { (pos, id) ->
            target.edit(pos, id)
            changed += pos
        }
        edits.clear()
        return changed
    }

    /** Drops all edits. Returns the voxels whose resolved view may change. */
    fun rollback(): Set<FastVector> {
        val changed = HashSet<FastVector>(edits.keys)
        edits.clear()
        return changed
    }

    val editCount: Int get() = edits.size

    fun editAt(pos: FastVector): Int? = edits[pos]
}
