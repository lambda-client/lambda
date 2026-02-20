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

import com.lambda.graphics.RenderMain
import net.minecraft.client.model.Model
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.state.CameraRenderState
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemDisplayContext
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.render.command.BatchingRenderCommandQueue
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import org.joml.Quaternionf

class OutlineCapturingQueue(
    private val delegate: OrderedRenderCommandQueueImpl,
    private val entityId: Any
) : OrderedRenderCommandQueueImpl() {

    override fun getBatchingQueue(i: Int): BatchingRenderCommandQueue {
        return OutlineCapturingBatchingQueue(delegate.getBatchingQueue(i), this)
    }

    override fun clear() = delegate.clear()
    override fun onNextFrame() = delegate.onNextFrame()
    override fun getBatchingQueues() = delegate.batchingQueues

    private inner class OutlineCapturingBatchingQueue(
        private val batchedDelegate: BatchingRenderCommandQueue,
        parent: OrderedRenderCommandQueueImpl
    ) : BatchingRenderCommandQueue(parent) {

        private fun getTextureView(renderLayer: RenderLayer, sprite: Sprite?): GpuTextureView? {
            if (sprite != null) {
                val texManager = net.minecraft.client.MinecraftClient.getInstance().textureManager
                return texManager.getTexture(sprite.atlasId)?.glTextureView
            }
            val outlineLayer = renderLayer.getAffectedOutline().orElse(null) ?: renderLayer
            val setup = outlineLayer.renderSetup ?: return null
            val textures = setup.resolveTextures()
            return textures["Sampler0"]?.textureView ?: textures.values.firstOrNull()?.textureView
        }

        private fun getClipTransform(renderLayer: RenderLayer): Matrix4f {
            val proj =
                if (entityId == -1) RenderMain.baseProjectionMatrix
                else RenderMain.worldProjectionMatrix
            val camRot = RenderMain.cameraRotationMatrix
            val result = Matrix4f(proj).mul(camRot)
            
            val transform = renderLayer.renderSetup?.layeringTransform?.transform
            if (transform != null) {
                val stack = org.joml.Matrix4fStack(1)
                stack.set(result)
                transform.accept(stack)
                result.set(stack)
            }
            return result
        }

        override fun submitShadowPieces(matrices: MatrixStack, radius: Float, pieces: List<net.minecraft.client.render.entity.state.EntityRenderState.ShadowPiece>) =
            batchedDelegate.submitShadowPieces(matrices, radius, pieces)
        
        override fun submitLabel(matrices: MatrixStack, pos: Vec3d?, y: Int, label: Text, ns: Boolean, l: Int, dist: Double, cam: CameraRenderState) =
            batchedDelegate.submitLabel(matrices, pos, y, label, ns, l, dist, cam)
        
        override fun submitText(matrices: MatrixStack, x: Float, y: Float, text: OrderedText, ds: Boolean, lt: net.minecraft.client.font.TextRenderer.TextLayerType, l: Int, c: Int, bc: Int, oc: Int) =
            batchedDelegate.submitText(matrices, x, y, text, ds, lt, l, c, bc, oc)
        
        override fun submitFire(matrices: MatrixStack, state: net.minecraft.client.render.entity.state.EntityRenderState, rot: Quaternionf) =
            batchedDelegate.submitFire(matrices, state, rot)
        
        override fun submitLeash(matrices: MatrixStack, data: net.minecraft.client.render.entity.state.EntityRenderState.LeashData) =
            batchedDelegate.submitLeash(matrices, data)

        override fun <S> submitModel(
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
        ) {
            if (renderLayer.isOutline || renderLayer.affectedOutline.isPresent) {
                VertexCapture.setActiveTexture(getTextureView(renderLayer, sprite))
                val baseConsumer = CapturingConsumer(renderLayer)
                val consumer = if (sprite != null) net.minecraft.client.render.SpriteTexturedVertexConsumer(baseConsumer, sprite) else baseConsumer
                model.setAngles(state)
                model.render(matrices, consumer, light, overlay, tintedColor)
                baseConsumer.flush()
            }
            batchedDelegate.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay)
        }

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
        ) {
            if (renderLayer.isOutline || renderLayer.getAffectedOutline().isPresent) {
                VertexCapture.setActiveTexture(getTextureView(renderLayer, sprite))
                val baseConsumer = CapturingConsumer(renderLayer)
                val consumer = if (sprite != null) net.minecraft.client.render.SpriteTexturedVertexConsumer(baseConsumer, sprite) else baseConsumer
                part.render(matrices, consumer, light, overlay, tintedColor)
                baseConsumer.flush()
            }
            batchedDelegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, i)
        }

        override fun submitBlock(matrices: MatrixStack, state: net.minecraft.block.BlockState, light: Int, overlay: Int, outlineColor: Int) =
            batchedDelegate.submitBlock(matrices, state, light, overlay, outlineColor)
        
        override fun submitMovingBlock(matrices: MatrixStack, state: net.minecraft.client.render.block.MovingBlockRenderState) =
            batchedDelegate.submitMovingBlock(matrices, state)
        
        override fun submitBlockStateModel(matrices: MatrixStack, layer: RenderLayer, model: net.minecraft.client.render.model.BlockStateModel, r: Float, g: Float, b: Float, l: Int, o: Int, oc: Int) =
            batchedDelegate.submitBlockStateModel(matrices, layer, model, r, g, b, l, o, oc)

        override fun submitItem(
            matrices: MatrixStack,
            displayContext: ItemDisplayContext,
            light: Int,
            overlay: Int,
            outlineColors: Int,
            tintLayers: IntArray,
            quads: List<BakedQuad>,
            renderLayer: RenderLayer,
            glintType: net.minecraft.client.render.item.ItemRenderState.Glint
        ) {
            if (renderLayer.isOutline || renderLayer.affectedOutline.isPresent) {
                VertexCapture.setActiveTexture(getTextureView(renderLayer, null))
                val combined = getClipTransform(renderLayer).mul(matrices.peek().positionMatrix)
                val posVec = Vector4f()
                val normVec = Vector3f()

                for (quad in quads) {
                    val nxSrc = quad.face.offsetX.toFloat()
                    val nySrc = quad.face.offsetY.toFloat()
                    val nzSrc = quad.face.offsetZ.toFloat()
                    combined.transformDirection(nxSrc, nySrc, nzSrc, normVec)

                    for (vIdx in 0 until 4) {
                        val posSrc = quad.getPosition(vIdx)
                        combined.transform(posSrc.x(), posSrc.y(), posSrc.z(), 1.0f, posVec)
                        val packedUV = quad.getTexcoords(vIdx)
                        val u = java.lang.Float.intBitsToFloat((packedUV shr 32).toInt())
                        val v = java.lang.Float.intBitsToFloat(packedUV.toInt())
                        VertexCapture.captureVertex(posVec.x, posVec.y, posVec.z, posVec.w, normVec.x, normVec.y, normVec.z, u, v)
                    }
                }
            }
            batchedDelegate.submitItem(matrices, displayContext, light, overlay, outlineColors, tintLayers, quads, renderLayer, glintType)
        }

        override fun submitCustom(matrices: MatrixStack, layer: RenderLayer, renderer: OrderedRenderCommandQueue.Custom) =
            batchedDelegate.submitCustom(matrices, layer, renderer)
        
        override fun submitCustom(renderer: OrderedRenderCommandQueue.LayeredCustom) =
            batchedDelegate.submitCustom(renderer)

        override fun getShadowPiecesCommands() = batchedDelegate.shadowPiecesCommands
        override fun getFireCommands() = batchedDelegate.fireCommands
        override fun getLabelCommands() = batchedDelegate.labelCommands
        override fun getTextCommands() = batchedDelegate.textCommands
        override fun getLeashCommands() = batchedDelegate.leashCommands
        override fun getBlockCommands() = batchedDelegate.blockCommands
        override fun getMovingBlockCommands() = batchedDelegate.movingBlockCommands
        override fun getBlockStateModelCommands() = batchedDelegate.blockStateModelCommands
        override fun getModelPartCommands() = batchedDelegate.modelPartCommands
        override fun getItemCommands() = batchedDelegate.itemCommands
        override fun getLayeredCustomCommands() = batchedDelegate.layeredCustomCommands
        override fun getModelCommands() = batchedDelegate.modelCommands
        override fun getCustomCommands() = batchedDelegate.customCommands
        override fun hasCommands() = batchedDelegate.hasCommands()
        override fun clear() = batchedDelegate.clear()
        override fun onNextFrame() = batchedDelegate.onNextFrame()

        private inner class CapturingConsumer(renderLayer: RenderLayer) : VertexConsumer {
            private val combined = getClipTransform(renderLayer)
            private val posVec = Vector4f()
            private var isCapturingFull = false
            private var hasVertex = false
            private var curX = 0f; private var curY = 0f; private var curZ = 0f; private var curW = 1.0f
            private var curU = 0f; private var curV = 0f
            private var curNX = 0f; private var curNY = 0f; private var curNZ = 0f

            private fun commit() {
                if (hasVertex) {
                    VertexCapture.captureVertex(curX, curY, curZ, curW, curNX, curNY, curNZ, curU, curV)
                    hasVertex = false
                }
            }

            fun flush() = commit()

            override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
                if (!isCapturingFull) {
                    commit() 
                    combined.transform(x, y, z, 1.0f, posVec)
                    curX = posVec.x; curY = posVec.y; curZ = posVec.z; curW = posVec.w
                    curU = 0f; curV = 0f; curNX = 0f; curNY = 0f; curNZ = 1f
                    hasVertex = true
                }
                return this
            }

            override fun color(argb: Int): VertexConsumer = this
            override fun color(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
            override fun texture(u: Float, v: Float): VertexConsumer { curU = u; curV = v; return this }
            override fun overlay(u: Int, v: Int): VertexConsumer = this
            override fun light(u: Int, v: Int): VertexConsumer = this
            override fun lineWidth(width: Float): VertexConsumer = this
            override fun normal(nx: Float, ny: Float, nz: Float): VertexConsumer { curNX = nx; curNY = ny; curNZ = nz; return this }

            override fun vertex(x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, overlay: Int, light: Int, nx: Float, ny: Float, nz: Float) {
                isCapturingFull = true
                try {
                    commit() 
                    combined.transform(x, y, z, 1.0f, posVec)
                    VertexCapture.captureVertex(posVec.x, posVec.y, posVec.z, posVec.w, nx, ny, nz, u, v)
                } finally {
                    isCapturingFull = false
                }
            }
        }
    }
}
