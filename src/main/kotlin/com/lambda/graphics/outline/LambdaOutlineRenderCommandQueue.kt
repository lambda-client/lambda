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

import net.minecraft.block.BlockState
import net.minecraft.client.model.Model
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.MovingBlockRenderState
import net.minecraft.client.render.command.BatchingRenderCommandQueue
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.BlockStateModel
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemDisplayContext
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.joml.Quaternionf
import java.awt.Color

class LambdaOutlineRenderCommandQueue : OrderedRenderCommandQueueImpl() {
    private var currentColor: Int = 0xFFFFFFFF.toInt()
    private var tintCache: IntArray? = null
    
    fun setColor(color: Int) {
        this.currentColor = color
    }
    
    fun setColor(color: Color) {
        this.currentColor = color.rgb
    }
    
    override fun getBatchingQueue(order: Int): BatchingRenderCommandQueue =
        batchingQueues.computeIfAbsent(order) { OutlineBatchingQueue(this) }
    
    private inner class OutlineBatchingQueue(
        orderedQueue: OrderedRenderCommandQueueImpl
    ) : BatchingRenderCommandQueue(orderedQueue) {
        override fun submitShadowPieces(matrices: MatrixStack, shadowRadius: Float, shadowPieces: List<EntityRenderState.ShadowPiece>) {}
        override fun submitLabel(matrices: MatrixStack, nameLabelPos: Vec3d?, y: Int, label: Text, notSneaking: Boolean, light: Int, squaredDistanceToCamera: Double, cameraState: CameraRenderState) {}
        override fun submitText(matrices: MatrixStack, x: Float, y: Float, text: OrderedText, dropShadow: Boolean, layerType: net.minecraft.client.font.TextRenderer.TextLayerType, light: Int, color: Int, backgroundColor: Int, outlineColor: Int) {}
        override fun submitFire(matrices: MatrixStack, renderState: EntityRenderState, rotation: Quaternionf) {}
        override fun submitLeash(matrices: MatrixStack, leashData: EntityRenderState.LeashData) {}
        

        override fun <S : Any> submitModel(
            model: Model<in S>,
            state: S,
            matrices: MatrixStack,
            renderLayer: RenderLayer,
            light: Int,
            overlay: Int,
            tintedColor: Int,
            sprite: Sprite?,
            outlineColor: Int,
            crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?
        ) = super.submitModel(model, state, matrices, renderLayer, light, overlay, currentColor, sprite, 0, crumblingOverlay)
        

        override fun submitModelPart(
            part: ModelPart,
            matrices: MatrixStack,
            renderLayer: RenderLayer,
            light: Int,
            overlay: Int,
            sprite: Sprite?,
            sheeted: Boolean,
            hasGlint: Boolean,
            tintedColor: Int,
            crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?,
            i: Int
        ) = super.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, currentColor, crumblingOverlay, i)

        override fun submitBlock(matrices: MatrixStack, state: BlockState, light: Int, overlay: Int, outlineColor: Int) { }
        override fun submitMovingBlock(matrices: MatrixStack, state: MovingBlockRenderState) {}

        override fun submitBlockStateModel(
            matrices: MatrixStack,
            renderLayer: RenderLayer,
            model: BlockStateModel,
            r: Float,
            g: Float,
            b: Float,
            light: Int,
            overlay: Int,
            outlineColor: Int
        ) {
            val newR = ((currentColor shr 16) and 0xFF) / 255f
            val newG = ((currentColor shr 8) and 0xFF) / 255f
            val newB = (currentColor and 0xFF) / 255f
            super.submitBlockStateModel(matrices, renderLayer, model, newR, newG, newB, light, overlay, outlineColor)
        }
        

        override fun submitItem(
            matrices: MatrixStack,
            displayContext: ItemDisplayContext,
            light: Int,
            overlay: Int,
            outlineColors: Int,
            tintLayers: IntArray,
            quads: List<BakedQuad>,
            renderLayer: RenderLayer,
            glintType: ItemRenderState.Glint
        ) {
            if (tintCache == null || tintCache!![0] != currentColor) {
                tintCache = intArrayOf(currentColor, currentColor, currentColor, currentColor)
            }
            super.submitItem(matrices, displayContext, light, overlay, outlineColors, tintCache!!, quads, renderLayer, glintType)
        }

        override fun submitCustom(matrices: MatrixStack, renderLayer: RenderLayer, customRenderer: OrderedRenderCommandQueue.Custom) {}
        override fun submitCustom(customRenderer: OrderedRenderCommandQueue.LayeredCustom) {}
    }
}
