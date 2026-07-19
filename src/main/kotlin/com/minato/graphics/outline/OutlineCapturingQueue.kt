
package com.minato.graphics.outline

import com.minato.Minato.mc
import com.minato.graphics.RenderMain
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.block.BlockState
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.model.Model
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.SpriteTexturedVertexConsumer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.block.BlockModelRenderer
import net.minecraft.client.render.block.MovingBlockRenderState
import net.minecraft.client.render.command.BatchingRenderCommandQueue
import net.minecraft.client.render.command.CustomCommandRenderer
import net.minecraft.client.render.command.LabelCommandRenderer
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.client.render.command.ModelPartCommandRenderer
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
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f

class OutlineCapturingQueue @JvmOverloads constructor(
    private val delegate: OrderedRenderCommandQueueImpl,
    private val entityId: Any,
    private val outlineOnly: Boolean = false
) : OrderedRenderCommandQueueImpl() {
    override fun clear() = delegate.clear()
    override fun onNextFrame() = delegate.onNextFrame()
    override fun getBatchingQueues() = delegate.batchingQueues

    private fun getTextureView(renderLayer: RenderLayer, sprite: Sprite?): GpuTextureView? {
        if (sprite != null) {
            val texManager = mc.textureManager
            return texManager.getTexture(sprite.atlasId)?.glTextureView
        }
        val outlineLayer = renderLayer.affectedOutline.orElse(null) ?: renderLayer
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
            val stack = Matrix4fStack(1)
            stack.set(result)
            transform.accept(stack)
            result.set(stack)
        }
        return result
    }

    private fun getClipTransformNoLayer(): Matrix4f {
        val proj = if (entityId == -1) RenderMain.baseProjectionMatrix else RenderMain.worldProjectionMatrix
        return Matrix4f(proj).mul(RenderMain.cameraRotationMatrix)
    }


    fun captureItemFrameModel(matrices: MatrixStack, layer: RenderLayer, model: BlockStateModel, r: Float, g: Float, b: Float, l: Int, o: Int, oc: Int) {
        if (layer.isOutline || layer.affectedOutline.isPresent) {
            VertexCapture.setActiveTexture(getTextureView(layer, null))
            val consumer = CapturingConsumer(layer)
            BlockModelRenderer.render(matrices.peek(), consumer, model, r, g, b, l, o)
            consumer.flush()
        }
        if (!outlineOnly) delegate.submitBlockStateModel(matrices, layer, model, r, g, b, l, o, oc)
    }

    override fun getBatchingQueue(i: Int): BatchingRenderCommandQueue =
        OutlineCapturingBatchingQueue(delegate.getBatchingQueue(i), this, outlineOnly)

    private inner class OutlineCapturingBatchingQueue(
        private val batchedDelegate: BatchingRenderCommandQueue,
        parent: OrderedRenderCommandQueueImpl,
        private val outlineOnly: Boolean
    ) : BatchingRenderCommandQueue(parent) {

        override fun submitShadowPieces(matrices: MatrixStack, radius: Float, pieces: List<EntityRenderState.ShadowPiece>) {
            if (!outlineOnly) batchedDelegate.submitShadowPieces(matrices, radius, pieces)
        }

        override fun submitLabel(matrices: MatrixStack, pos: Vec3d?, y: Int, label: Text, ns: Boolean, l: Int, dist: Double, cam: CameraRenderState) {
            if (!outlineOnly) batchedDelegate.submitLabel(matrices, pos, y, label, ns, l, dist, cam)
        }

        override fun submitText(matrices: MatrixStack, x: Float, y: Float, text: OrderedText, ds: Boolean, lt: TextRenderer.TextLayerType, l: Int, c: Int, bc: Int, oc: Int) {
            if (!outlineOnly) batchedDelegate.submitText(matrices, x, y, text, ds, lt, l, c, bc, oc)
        }

        override fun submitFire(matrices: MatrixStack, state: EntityRenderState, rot: Quaternionf) {
            if (!outlineOnly) batchedDelegate.submitFire(matrices, state, rot)
        }

        override fun submitLeash(matrices: MatrixStack, data: EntityRenderState.LeashData) {
            if (!outlineOnly) batchedDelegate.submitLeash(matrices, data)
        }

        override fun submitCustom(matrices: MatrixStack, layer: RenderLayer, renderer: OrderedRenderCommandQueue.Custom) {
            if (layer.isOutline || layer.affectedOutline.isPresent) {
                VertexCapture.setActiveTexture(getTextureView(layer, null))
                val baseConsumer = CapturingConsumer(layer)
                renderer.render(matrices.peek(), baseConsumer)
                baseConsumer.flush()
            }
            if (!outlineOnly) batchedDelegate.submitCustom(matrices, layer, renderer)
        }

        override fun submitCustom(renderer: OrderedRenderCommandQueue.LayeredCustom) {
            if (!outlineOnly) batchedDelegate.submitCustom(renderer)
        }

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
                val consumer = if (sprite != null) SpriteTexturedVertexConsumer(baseConsumer, sprite) else baseConsumer
                model.setAngles(state)
                model.render(matrices, consumer, light, overlay, tintedColor)
                baseConsumer.flush()
            }
            if (!outlineOnly) batchedDelegate.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay)
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
            if (renderLayer.isOutline || renderLayer.affectedOutline.isPresent) {
                VertexCapture.setActiveTexture(getTextureView(renderLayer, sprite))
                val baseConsumer = CapturingConsumer(renderLayer)
                val consumer = if (sprite != null) SpriteTexturedVertexConsumer(baseConsumer, sprite) else baseConsumer
                part.render(matrices, consumer, light, overlay, tintedColor)
                baseConsumer.flush()
            }
            if (!outlineOnly) batchedDelegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, i)
        }

        override fun submitBlock(matrices: MatrixStack, state: BlockState, light: Int, overlay: Int, outlineColor: Int) {
            val blockRenderManager = mc.blockRenderManager
            val model = blockRenderManager.getModel(state)
            VertexCapture.setActiveTexture(null)
            val consumer = CapturingConsumer(null)
            BlockModelRenderer.render(matrices.peek(), consumer, model, 1f, 1f, 1f, light, overlay)
            consumer.flush()
            if (!outlineOnly) batchedDelegate.submitBlock(matrices, state, light, overlay, outlineColor)
        }

        override fun submitMovingBlock(matrices: MatrixStack, state: MovingBlockRenderState) {
            val blockRenderManager = mc.blockRenderManager
            val model = blockRenderManager.getModel(state.blockState)
            VertexCapture.setActiveTexture(null)
            val consumer = CapturingConsumer(null)
            BlockModelRenderer.render(matrices.peek(), consumer, model, 1f, 1f, 1f, 0, 0)
            consumer.flush()
            if (!outlineOnly) batchedDelegate.submitMovingBlock(matrices, state)
        }

        override fun submitBlockStateModel(matrices: MatrixStack, layer: RenderLayer, model: BlockStateModel, r: Float, g: Float, b: Float, l: Int, o: Int, oc: Int) {
            if (!outlineOnly) batchedDelegate.submitBlockStateModel(matrices, layer, model, r, g, b, l, o, oc)
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
            if (!outlineOnly) batchedDelegate.submitItem(matrices, displayContext, light, overlay, outlineColors, tintLayers, quads, renderLayer, glintType)
        }

        override fun getShadowPiecesCommands(): List<ShadowPiecesCommand?>? = batchedDelegate.shadowPiecesCommands
        override fun getFireCommands(): List<FireCommand?>? = batchedDelegate.fireCommands
        override fun getLabelCommands(): LabelCommandRenderer.Commands? = batchedDelegate.labelCommands
        override fun getTextCommands(): List<TextCommand?>? = batchedDelegate.textCommands
        override fun getLeashCommands(): List<LeashCommand?>? = batchedDelegate.leashCommands
        override fun getBlockCommands(): List<BlockCommand?>? = batchedDelegate.blockCommands
        override fun getMovingBlockCommands(): List<MovingBlockCommand?>? = batchedDelegate.movingBlockCommands
        override fun getBlockStateModelCommands(): List<BlockStateModelCommand?>? = batchedDelegate.blockStateModelCommands
        override fun getModelPartCommands(): ModelPartCommandRenderer.Commands? = batchedDelegate.modelPartCommands
        override fun getItemCommands(): List<ItemCommand?>? = batchedDelegate.itemCommands
        override fun getLayeredCustomCommands(): List<OrderedRenderCommandQueue.LayeredCustom?>? = batchedDelegate.layeredCustomCommands
        override fun getModelCommands(): ModelCommandRenderer.Commands? = batchedDelegate.modelCommands
        override fun getCustomCommands(): CustomCommandRenderer.Commands? = batchedDelegate.customCommands
        override fun hasCommands() = batchedDelegate.hasCommands()
        override fun clear() = batchedDelegate.clear()
        override fun onNextFrame() = batchedDelegate.onNextFrame()
    }

    private inner class CapturingConsumer(renderLayer: RenderLayer?) : VertexConsumer {
        private val combined =
            if (renderLayer != null) getClipTransform(renderLayer)
            else getClipTransformNoLayer()
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
