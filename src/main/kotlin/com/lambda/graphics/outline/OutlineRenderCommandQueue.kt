package com.lambda.graphics.outline

import net.minecraft.block.BlockState
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.model.Model
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.MovingBlockRenderState
import net.minecraft.client.render.command.ModelCommandRenderer
import net.minecraft.client.render.command.OrderedRenderCommandQueue
import net.minecraft.client.render.command.RenderCommandQueue
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

/**
 * A wrapper around an [OrderedRenderCommandQueue] that overrides the outline color for all submitted commands.
 * This is used to force block entities (which normally render outline color 0) to render outlines in the
 * custom ESP outline rendering pass.
 */
class OutlineRenderCommandQueue(
    private val delegate: OrderedRenderCommandQueue,
    var forcedOutlineColor: Int
) : OrderedRenderCommandQueue {

    override fun getBatchingQueue(order: Int): RenderCommandQueue {
        return OutlineRenderCommandQueueDelegate(delegate.getBatchingQueue(order), forcedOutlineColor)
    }

    override fun submitShadowPieces(matrices: MatrixStack?, shadowRadius: Float, shadowPieces: MutableList<EntityRenderState.ShadowPiece>?) {
        delegate.submitShadowPieces(matrices, shadowRadius, shadowPieces)
    }

    override fun submitLabel(
        matrices: MatrixStack?,
        nameLabelPos: Vec3d?,
        y: Int,
        label: Text?,
        notSneaking: Boolean,
        light: Int,
        squaredDistanceToCamera: Double,
        cameraState: CameraRenderState?
    ) {
        delegate.submitLabel(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState)
    }

    override fun submitText(
        matrices: MatrixStack?,
        x: Float,
        y: Float,
        text: OrderedText?,
        dropShadow: Boolean,
        layerType: TextRenderer.TextLayerType?,
        light: Int,
        color: Int,
        backgroundColor: Int,
        outlineColor: Int
    ) {
        delegate.submitText(matrices, x, y, text, dropShadow, layerType, light, color, backgroundColor, forcedOutlineColor)
    }

    override fun submitFire(matrices: MatrixStack?, renderState: EntityRenderState?, rotation: Quaternionf?) {
        delegate.submitFire(matrices, renderState, rotation)
    }

    override fun submitLeash(matrices: MatrixStack?, leashData: EntityRenderState.LeashData?) {
        delegate.submitLeash(matrices, leashData)
    }

    override fun <S : Any?> submitModel(
        model: Model<in S>?,
        state: S,
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        light: Int,
        overlay: Int,
        tintedColor: Int,
        sprite: Sprite?,
        outlineColor: Int,
        crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?
    ) {
        delegate.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, forcedOutlineColor, crumblingOverlay)
    }

    override fun submitModelPart(
        part: ModelPart?,
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        light: Int,
        overlay: Int,
        sprite: Sprite?,
        sheeted: Boolean,
        hasGlint: Boolean,
        tintedColor: Int,
        crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?,
        i: Int
    ) {
        delegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, i)
    }

    override fun submitBlock(matrices: MatrixStack?, state: BlockState?, light: Int, overlay: Int, outlineColor: Int) {
        delegate.submitBlock(matrices, state, light, overlay, forcedOutlineColor)
    }

    override fun submitMovingBlock(matrices: MatrixStack?, state: MovingBlockRenderState?) {
        delegate.submitMovingBlock(matrices, state)
    }

    override fun submitBlockStateModel(
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        model: BlockStateModel?,
        r: Float,
        g: Float,
        b: Float,
        light: Int,
        overlay: Int,
        outlineColor: Int
    ) {
        delegate.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, forcedOutlineColor)
    }

    override fun submitItem(
        matrices: MatrixStack?,
        displayContext: ItemDisplayContext?,
        light: Int,
        overlay: Int,
        outlineColors: Int,
        tintLayers: IntArray?,
        quads: MutableList<BakedQuad>?,
        renderLayer: RenderLayer?,
        glintType: ItemRenderState.Glint?
    ) {
        delegate.submitItem(matrices, displayContext, light, overlay, forcedOutlineColor, tintLayers, quads, renderLayer, glintType)
    }

    override fun submitCustom(
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        customRenderer: OrderedRenderCommandQueue.Custom?
    ) {
        delegate.submitCustom(matrices, renderLayer, customRenderer)
    }

    override fun submitCustom(customRenderer: OrderedRenderCommandQueue.LayeredCustom?) {
        delegate.submitCustom(customRenderer)
    }
}

class OutlineRenderCommandQueueDelegate(
    private val delegate: RenderCommandQueue,
    var forcedOutlineColor: Int
) : RenderCommandQueue {
    override fun submitShadowPieces(matrices: MatrixStack?, shadowRadius: Float, shadowPieces: MutableList<EntityRenderState.ShadowPiece>?) {
        delegate.submitShadowPieces(matrices, shadowRadius, shadowPieces)
    }

    override fun submitLabel(
        matrices: MatrixStack?,
        nameLabelPos: Vec3d?,
        y: Int,
        label: Text?,
        notSneaking: Boolean,
        light: Int,
        squaredDistanceToCamera: Double,
        cameraState: CameraRenderState?
    ) {
        delegate.submitLabel(matrices, nameLabelPos, y, label, notSneaking, light, squaredDistanceToCamera, cameraState)
    }

    override fun submitText(
        matrices: MatrixStack?,
        x: Float,
        y: Float,
        text: OrderedText?,
        dropShadow: Boolean,
        layerType: TextRenderer.TextLayerType?,
        light: Int,
        color: Int,
        backgroundColor: Int,
        outlineColor: Int
    ) {
        delegate.submitText(matrices, x, y, text, dropShadow, layerType, light, color, backgroundColor, forcedOutlineColor)
    }

    override fun submitFire(matrices: MatrixStack?, renderState: EntityRenderState?, rotation: Quaternionf?) {
        delegate.submitFire(matrices, renderState, rotation)
    }

    override fun submitLeash(matrices: MatrixStack?, leashData: EntityRenderState.LeashData?) {
        delegate.submitLeash(matrices, leashData)
    }

    override fun <S : Any?> submitModel(
        model: Model<in S>?,
        state: S,
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        light: Int,
        overlay: Int,
        tintedColor: Int,
        sprite: Sprite?,
        outlineColor: Int,
        crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?
    ) {
        delegate.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, forcedOutlineColor, crumblingOverlay)
    }

    override fun submitModelPart(
        part: ModelPart?,
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        light: Int,
        overlay: Int,
        sprite: Sprite?,
        sheeted: Boolean,
        hasGlint: Boolean,
        tintedColor: Int,
        crumblingOverlay: ModelCommandRenderer.CrumblingOverlayCommand?,
        i: Int
    ) {
        delegate.submitModelPart(part, matrices, renderLayer, light, overlay, sprite, sheeted, hasGlint, tintedColor, crumblingOverlay, i)
    }

    override fun submitBlock(matrices: MatrixStack?, state: BlockState?, light: Int, overlay: Int, outlineColor: Int) {
        delegate.submitBlock(matrices, state, light, overlay, forcedOutlineColor)
    }

    override fun submitMovingBlock(matrices: MatrixStack?, state: MovingBlockRenderState?) {
        delegate.submitMovingBlock(matrices, state)
    }

    override fun submitBlockStateModel(
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        model: BlockStateModel?,
        r: Float,
        g: Float,
        b: Float,
        light: Int,
        overlay: Int,
        outlineColor: Int
    ) {
        delegate.submitBlockStateModel(matrices, renderLayer, model, r, g, b, light, overlay, forcedOutlineColor)
    }

    override fun submitItem(
        matrices: MatrixStack?,
        displayContext: ItemDisplayContext?,
        light: Int,
        overlay: Int,
        outlineColors: Int,
        tintLayers: IntArray?,
        quads: MutableList<BakedQuad>?,
        renderLayer: RenderLayer?,
        glintType: ItemRenderState.Glint?
    ) {
        delegate.submitItem(matrices, displayContext, light, overlay, forcedOutlineColor, tintLayers, quads, renderLayer, glintType)
    }

    override fun submitCustom(
        matrices: MatrixStack?,
        renderLayer: RenderLayer?,
        customRenderer: OrderedRenderCommandQueue.Custom?
    ) {
        delegate.submitCustom(matrices, renderLayer, customRenderer)
    }

    override fun submitCustom(customRenderer: OrderedRenderCommandQueue.LayeredCustom?) {
        delegate.submitCustom(customRenderer)
    }
}
