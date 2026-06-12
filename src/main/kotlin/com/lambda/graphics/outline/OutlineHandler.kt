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

package com.lambda.graphics.outline

import net.minecraft.util.math.BlockPos

@Suppress("unused")
object OutlineHandler {
    private val depthTestedEntityOutlines = mutableMapOf<Int, OutlineStyle>()
    private val xrayEntityOutlines = mutableMapOf<Int, OutlineStyle>()

    private val depthTestedBlockOutlines = mutableMapOf<BlockPos, OutlineStyle>()
    private val xrayBlockOutlines = mutableMapOf<BlockPos, OutlineStyle>()

    fun setEntityOutline(entityId: Int, style: OutlineStyle?, depthTest: Boolean = true) {
        if (style != null) {
            if (depthTest) {
                depthTestedEntityOutlines[entityId] = style
                xrayEntityOutlines.remove(entityId)
            } else {
                xrayEntityOutlines[entityId] = style
                depthTestedEntityOutlines.remove(entityId)
            }
        } else {
            depthTestedEntityOutlines.remove(entityId)
            xrayEntityOutlines.remove(entityId)
        }
    }

    fun setBlockOutline(pos: BlockPos, style: OutlineStyle?, depthTest: Boolean = true) {
        if (style != null) {
            if (depthTest) {
                depthTestedBlockOutlines[pos] = style
                xrayBlockOutlines.remove(pos)
            } else {
                xrayBlockOutlines[pos] = style
                depthTestedBlockOutlines.remove(pos)
            }
        } else {
            depthTestedBlockOutlines.remove(pos)
            xrayBlockOutlines.remove(pos)
        }
    }

    fun getEntityOutlineStyle(id: Int): OutlineStyle? =
        depthTestedEntityOutlines[id] ?: xrayEntityOutlines[id]

    fun getEntityOutline(entityId: Int): OutlineStyle? = 
        depthTestedEntityOutlines[entityId] ?: xrayEntityOutlines[entityId]
    fun getBlockOutline(pos: BlockPos): OutlineStyle? =
        depthTestedBlockOutlines[pos] ?: xrayBlockOutlines[pos]

    fun hasEntityOutlines(): Boolean = depthTestedEntityOutlines.isNotEmpty() || xrayEntityOutlines.isNotEmpty()
    fun hasBlockOutlines(): Boolean = depthTestedBlockOutlines.isNotEmpty() || xrayBlockOutlines.isNotEmpty()

    @JvmStatic
    fun shouldCapture(entityId: Int): Boolean =
        depthTestedEntityOutlines.containsKey(entityId) || xrayEntityOutlines.containsKey(entityId)
    @JvmStatic
    fun shouldCapture(pos: BlockPos): Boolean =
        depthTestedBlockOutlines.containsKey(pos) || xrayBlockOutlines.containsKey(pos)
    
    fun getDepthTestedEntityStyles(): Map<Int, OutlineStyle> = depthTestedEntityOutlines
    fun getXrayEntityStyles(): Map<Int, OutlineStyle> = xrayEntityOutlines
    
    fun getDepthTestedBlockStyles(): Map<BlockPos, OutlineStyle> = depthTestedBlockOutlines
    fun getXrayBlockStyles(): Map<BlockPos, OutlineStyle> = xrayBlockOutlines
    
    fun clear() {
        depthTestedEntityOutlines.clear()
        xrayEntityOutlines.clear()
        depthTestedBlockOutlines.clear()
        xrayBlockOutlines.clear()
    }
}
