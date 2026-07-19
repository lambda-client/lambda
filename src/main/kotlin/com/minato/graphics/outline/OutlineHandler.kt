
package com.minato.graphics.outline

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
