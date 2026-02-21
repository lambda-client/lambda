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

object OutlineManager {
    private val depthTestedEntityOutlines = mutableMapOf<Int, OutlineStyle>()
    private val xrayEntityOutlines = mutableMapOf<Int, OutlineStyle>()

    private val depthTestedBlockOutlines = mutableMapOf<BlockPos, OutlineStyle>()
    private val xrayBlockOutlines = mutableMapOf<BlockPos, OutlineStyle>()

    private val depthTestedCustomOutlines = mutableMapOf<Int, OutlineStyle>()
    private val xrayCustomOutlines = mutableMapOf<Int, OutlineStyle>()
    private var nextCustomId = 1_000_000

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

    fun registerCustomOutline(style: OutlineStyle, depthTest: Boolean = true): Int {
        val id = nextCustomId++
        if (depthTest) {
            depthTestedCustomOutlines[id] = style
        } else {
            xrayCustomOutlines[id] = style
        }
        return id
    }

    fun getOutlineStyle(id: Int): OutlineStyle? =
        depthTestedEntityOutlines[id] ?: xrayEntityOutlines[id] ?:
        depthTestedCustomOutlines[id] ?: xrayCustomOutlines[id]

    fun getEntityOutline(entityId: Int): OutlineStyle? = 
        depthTestedEntityOutlines[entityId] ?: xrayEntityOutlines[entityId]
    
    fun hasEntityOutlines(): Boolean = depthTestedEntityOutlines.isNotEmpty() || xrayEntityOutlines.isNotEmpty()
    
    @JvmStatic
    fun shouldCapture(entityId: Int): Boolean =
        depthTestedEntityOutlines.containsKey(entityId) || xrayEntityOutlines.containsKey(entityId)
    
    fun getEntityOutlines(): Map<Int, Pair<OutlineStyle, Boolean>> {
        val all = mutableMapOf<Int, Pair<OutlineStyle, Boolean>>()
        depthTestedEntityOutlines.forEach { (id, style) -> all[id] = style to true }
        xrayEntityOutlines.forEach { (id, style) -> all[id] = style to false }
        return all
    }

    fun getDepthTestedEntityIds(): Set<Int> = depthTestedEntityOutlines.keys
    fun getXrayEntityIds(): Set<Int> = xrayEntityOutlines.keys
    
    fun getDepthTestedEntityStyles(): Map<Int, OutlineStyle> = depthTestedEntityOutlines
    fun getXrayEntityStyles(): Map<Int, OutlineStyle> = xrayEntityOutlines

    fun getCustomOutlines(): Map<Int, Pair<OutlineStyle, Boolean>> {
        val all = mutableMapOf<Int, Pair<OutlineStyle, Boolean>>()
        depthTestedCustomOutlines.forEach { (id, style) -> all[id] = style to true }
        xrayCustomOutlines.forEach { (id, style) -> all[id] = style to false }
        return all
    }

    fun getDepthTestedCustomStyles(): Map<Int, OutlineStyle> = depthTestedCustomOutlines
    fun getXrayCustomStyles(): Map<Int, OutlineStyle> = xrayCustomOutlines

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
    
    fun getBlockOutline(pos: BlockPos): OutlineStyle? = 
        depthTestedBlockOutlines[pos] ?: xrayBlockOutlines[pos]

    fun hasBlockOutlines(): Boolean = depthTestedBlockOutlines.isNotEmpty() || xrayBlockOutlines.isNotEmpty()

    @JvmStatic
    fun isBlockCaptured(pos: BlockPos): Boolean =
        depthTestedBlockOutlines.containsKey(pos) || xrayBlockOutlines.containsKey(pos)
    
    fun getDepthTestedBlockStyles(): Map<BlockPos, OutlineStyle> = depthTestedBlockOutlines
    fun getXrayBlockStyles(): Map<BlockPos, OutlineStyle> = xrayBlockOutlines
    
    fun clear() {
        depthTestedEntityOutlines.clear()
        xrayEntityOutlines.clear()
        depthTestedBlockOutlines.clear()
        xrayBlockOutlines.clear()
        depthTestedCustomOutlines.clear()
        xrayCustomOutlines.clear()
    }
}
