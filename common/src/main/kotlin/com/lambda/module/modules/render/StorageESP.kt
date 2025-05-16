/*
 * Copyright 2025 Lambda
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

package com.lambda.module.modules.render

import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildFilledShape
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.builders.buildOutlineShape
import com.lambda.graphics.renderer.esp.impl.StaticESPRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.outlineShape
import com.lambda.util.math.setAlpha
import com.lambda.util.world.blockEntitySearch
import com.lambda.util.world.entitySearch
import net.minecraft.block.entity.BarrelBlockEntity
import net.minecraft.block.entity.BlastFurnaceBlockEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BrewingStandBlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.DispenserBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.FurnaceBlockEntity
import net.minecraft.block.entity.HopperBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.block.entity.SmokerBlockEntity
import net.minecraft.block.entity.TrappedChestBlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.entity.vehicle.AbstractMinecartEntity
import net.minecraft.entity.vehicle.MinecartEntity
import net.minecraft.util.math.BlockPos
import java.awt.Color

object StorageESP : Module(
    name = "StorageESP",
    description = "Render storage blocks/entities",
    defaultTags = setOf(ModuleTag.RENDER),
) {
    private val page by setting("Page", Page.Render)

    /* General settings */
    private val distance by setting("Distance", 64.0, 10.0..256.0, 1.0, "Maximum distance for rendering") { page == Page.General }

    /* Render settings */
    private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks") { page == Page.Render }.onValueSet { _, to -> if (!to) drawOutlines = true }
    private var drawOutlines: Boolean by setting("Draw Outlines", true, "Draw outlines of blocks") { page == Page.Render }.onValueSet { _, to -> if (!to) drawFaces = true }
    private val outlineMode by setting("Outline Mode", DirectionMask.OutlineMode.AND, "Outline mode") { page == Page.Render }
    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks") { page == Page.Render }

    /* Color settings */
    private val useBlockColor by setting("Use Block Color", true, "Use the color of the block instead") { page == Page.Color }
    private val alpha by setting("Alpha", 0.3, 0.1..1.0, 0.05) { page == Page.Color }

    // TODO:
    //  val blockColors by setting("Block Colors", mapOf<String, Color>()) { page == Page.Color && !useBlockColor }
    //  val renders by setting("Render Blocks", mapOf<String, Boolean>()) { page == Page.General }
    //
    // TODO: Create enum of MapColors

    // I used this to extract the colors as rgb format
    //> function extract(color) {
    //    ... console.log((color >> 16) & 0xFF)
    //    ... console.log((color >> 8) & 0xFF)
    //    ... console.log(color & 0xFF)
    //    ... }

    private val barrelColor by setting("Barrel Color", Color(143, 119, 72)) { page == Page.Color && !useBlockColor }
    private val blastFurnaceColor by setting("Blast Furnace Color", Color(153, 153, 153)) { page == Page.Color && !useBlockColor }
    private val brewingStandColor by setting("Brewing Stand Color", Color(167, 167, 167)) { page == Page.Color && !useBlockColor }
    private val trappedChestColor by setting("Trapped Chest Color", Color(216, 127, 51)) { page == Page.Color && !useBlockColor }
    private val chestColor by setting("Chest Color", Color(216, 127, 51)) { page == Page.Color && !useBlockColor }
    private val dispenserColor by setting("Dispenser Color", Color(153, 153, 153)) { page == Page.Color && !useBlockColor }
    private val enderChestColor by setting("Ender Chest Color", Color(127, 63, 178)) { page == Page.Color && !useBlockColor }
    private val furnaceColor by setting("Furnace Color", Color(153, 153, 153)) { page == Page.Color && !useBlockColor }
    private val hopperColor by setting("Hopper Color", Color(76, 76, 76)) { page == Page.Color && !useBlockColor }
    private val smokerColor by setting("Smoker Color", Color(112, 112, 112)) { page == Page.Color && !useBlockColor }
    private val shulkerColor by setting("Shulker Color", Color(178, 76, 216)) { page == Page.Color && !useBlockColor }
    private val itemFrameColor by setting("Item Frame Color", Color(216, 127, 51)) { page == Page.Color && !useBlockColor }
    private val cartColor by setting("Minecart Color", Color(102, 127, 51)) { page == Page.Color && !useBlockColor }

    private val entities = setOf(
        BarrelBlockEntity::class,
        BlastFurnaceBlockEntity::class,
        BrewingStandBlockEntity::class,
        TrappedChestBlockEntity::class,
        ChestBlockEntity::class,
        DispenserBlockEntity::class,
        EnderChestBlockEntity::class,
        FurnaceBlockEntity::class,
        HopperBlockEntity::class,
        SmokerBlockEntity::class,
        ShulkerBoxBlockEntity::class,
        AbstractMinecartEntity::class,
        ItemFrameEntity::class,
        MinecartEntity::class,
    )

    init {
        listen<RenderEvent.StaticESP> { event ->
            blockEntitySearch<BlockEntity>(distance)
                .filter { it::class in entities }
                .forEach { event.renderer.build(it, it.pos, excludedSides(it)) }

            val mineCarts = entitySearch<AbstractMinecartEntity>(distance)
            val itemFrames = entitySearch<ItemFrameEntity>(distance)
            (mineCarts + itemFrames)
                .forEach { event.renderer.build(it, DirectionMask.ALL) }
        }
    }

    private fun SafeContext.excludedSides(blockEntity: BlockEntity): Int {
        val isFullCube = blockEntity.cachedState.isFullCube(world, blockEntity.pos)
        return if (mesh && isFullCube) {
            buildSideMesh(blockEntity.pos) { neighbor ->
                val other = world.getBlockEntity(neighbor) ?: return@buildSideMesh false
                val otherFullCube = other.cachedState.isFullCube(world, other.pos)
                val sameType = blockEntity.cachedState.block == other.cachedState.block
                val searchedFor = other::class in entities

                searchedFor && otherFullCube && sameType
            }
        } else DirectionMask.ALL
    }

    private fun StaticESPRenderer.build(
        block: BlockEntity,
        pos: BlockPos,
        sides: Int,
    ) = runSafe {
        val color = if (useBlockColor) {
            blockColor(block.cachedState, pos)
        } else getBlockEntityColor(block) ?: return@runSafe
        val shape = outlineShape(block.cachedState, pos)

        if (drawFaces) buildFilledShape(shape, color.setAlpha(alpha), sides)
        if (drawOutlines) buildOutlineShape(shape, color, sides, outlineMode)
    }

    private fun StaticESPRenderer.build(
        entity: Entity,
        sides: Int,
    ) = runSafe {
        val color = getEntityColor(entity) ?: return@runSafe

        if (drawFaces) buildFilled(entity.boundingBox, color.setAlpha(alpha), sides)
        if (drawOutlines) buildOutline(entity.boundingBox, color, sides, outlineMode)
    }

    private fun getBlockEntityColor(block: BlockEntity?) =
        when (block) {
            is BarrelBlockEntity -> barrelColor
            is BlastFurnaceBlockEntity -> blastFurnaceColor
            is BrewingStandBlockEntity -> brewingStandColor
            is TrappedChestBlockEntity -> trappedChestColor
            is ChestBlockEntity -> chestColor
            is DispenserBlockEntity -> dispenserColor
            is EnderChestBlockEntity -> enderChestColor
            is FurnaceBlockEntity -> furnaceColor
            is HopperBlockEntity -> hopperColor
            is SmokerBlockEntity -> smokerColor
            is ShulkerBoxBlockEntity -> shulkerColor
            else -> null
        }

    private fun getEntityColor(entity: Entity?) =
        when (entity) {
            is AbstractMinecartEntity -> cartColor
            is ItemFrameEntity -> itemFrameColor
            else -> null
        }

    private enum class Page {
        General,
        Render,
        Color
    }
}
