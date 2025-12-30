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
import com.lambda.graphics.esp.ShapeScope
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.event.events.onStaticRender
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.blockEntitySearch
import com.lambda.util.world.entitySearch
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.extension.blockColor
import com.lambda.util.math.setAlpha
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
import java.awt.Color

object StorageESP : Module(
	name = "StorageESP",
	description = "Render storage blocks/entities",
	tag = ModuleTag.RENDER,
) {
	private val distance by setting("Distance", 64.0, 10.0..256.0, 1.0, "Maximum distance for rendering").group(Group.General)
	private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks").group(Group.Render)
	private var drawEdges: Boolean by setting("Draw Edges", true, "Draw edges of blocks").group(Group.Render)
	private val mode by setting("Outline Mode", DirectionMask.OutlineMode.And, "Outline mode").group(Group.Render)
	private val mesh by setting("Mesh", true, "Connect similar adjacent blocks").group(Group.Render)
	private val useBlockColor by setting("Use Block Color", true, "Use the color of the block instead").group(Group.Color)
	private val facesAlpha by setting("Faces Alpha", 0.3, 0.1..1.0, 0.05).group(Group.Color)
	private val edgesAlpha by setting("Edges Alpha", 0.3, 0.1..1.0, 0.05).group(Group.Color)
	private val outlineWidth by setting("Outline Width", 1.0f, 0.5f..5.0f, 0.5f) { drawEdges }.group(Group.Render)

	// TODO:
	//  val blockColors by setting("Block Colors", mapOf<String, Color>()) { page == Page.Color
	// &&
	// !useBlockColor }
	//  val renders by setting("Render Blocks", mapOf<String, Boolean>()) { page == Page.General
	// }
	//
	// TODO: Create enum of MapColors

	// I used this to extract the colors as rgb format
	// > function extract(color) {
	//    ... console.log((color >> 16) & 0xFF)
	//    ... console.log((color >> 8) & 0xFF)
	//    ... console.log(color & 0xFF)
	//    ... }

	private val barrelColor by setting("Barrel Color", Color(143, 119, 72)) { !useBlockColor }.group(Group.Color)
	private val blastFurnaceColor by setting("Blast Furnace Color", Color(153, 153, 153)) { !useBlockColor }.group(Group.Color)
	private val brewingStandColor by setting("Brewing Stand Color", Color(167, 167, 167)) { !useBlockColor }.group(Group.Color)
	private val trappedChestColor by setting("Trapped Chest Color", Color(216, 127, 51)) { !useBlockColor }.group(Group.Color)
	private val chestColor by setting("Chest Color", Color(216, 127, 51)) { !useBlockColor }.group(Group.Color)
	private val dispenserColor by setting("Dispenser Color", Color(153, 153, 153)) { !useBlockColor }.group(Group.Color)
	private val enderChestColor by setting("Ender Chest Color", Color(127, 63, 178)) { !useBlockColor }.group(Group.Color)
	private val furnaceColor by setting("Furnace Color", Color(153, 153, 153)) { !useBlockColor }.group(Group.Color)
	private val hopperColor by setting("Hopper Color", Color(76, 76, 76)) { !useBlockColor }.group(Group.Color)
	private val smokerColor by setting("Smoker Color", Color(112, 112, 112)) { !useBlockColor }.group(Group.Color)
	private val shulkerColor by setting("Shulker Color", Color(178, 76, 216)) { !useBlockColor }.group(Group.Color)
	private val itemFrameColor by setting("Item Frame Color", Color(216, 127, 51)) { !useBlockColor }.group(Group.Color)
	private val cartColor by setting("Minecart Color", Color(102, 127, 51)) { !useBlockColor }.group(Group.Color)

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
		onStaticRender { esp ->
			blockEntitySearch<BlockEntity>(distance)
				.filter { it::class in entities }
				.forEach { be ->
					esp.shapes(be.pos.x.toDouble(), be.pos.y.toDouble(), be.pos.z.toDouble()) {
						build(be, excludedSides(be))
					}
				}

			val mineCarts =
				entitySearch<AbstractMinecartEntity>(distance).filter {
					it::class in entities
				}
			val itemFrames =
				entitySearch<ItemFrameEntity>(distance).filter {
					it::class in entities
				}
			(mineCarts + itemFrames).forEach { entity ->
				esp.shapes(entity.getX(), entity.getY(), entity.getZ()) {
					build(entity, DirectionMask.ALL)
				}
			}
		}
	}

	private fun SafeContext.excludedSides(blockEntity: BlockEntity): Int {
		val isFullCube = blockEntity.cachedState.isFullCube(world, blockEntity.pos)
		return if (mesh && isFullCube) {
			buildSideMesh(blockEntity.pos) { neighbor ->
				val other =
					world.getBlockEntity(neighbor) ?: return@buildSideMesh false
				val otherFullCube = other.cachedState.isFullCube(world, other.pos)
				val sameType =
					blockEntity.cachedState.block == other.cachedState.block
				val searchedFor = other::class in entities

				searchedFor && otherFullCube && sameType
			}
		} else DirectionMask.ALL
	}

	private fun ShapeScope.build(block: BlockEntity, sides: Int) = runSafe {
		val color =
			if (useBlockColor) blockColor(block.cachedState, block.pos)
			else block.color ?: return@runSafe
		box(block, color.setAlpha(facesAlpha), color.setAlpha(edgesAlpha), sides, mode, thickness = outlineWidth)
	}

	private fun ShapeScope.build(entity: Entity, sides: Int) = runSafe {
		val color = entity.color ?: return@runSafe
		box(entity, color.setAlpha(facesAlpha), color.setAlpha(edgesAlpha), sides, mode, thickness = outlineWidth)
	}

	private val BlockEntity?.color
		get() =
			when (this) {
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

	private val Entity?.color
		get() =
			when (this) {
				is AbstractMinecartEntity -> cartColor
				is ItemFrameEntity -> itemFrameColor
				else -> null
			}

	private enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Render("Render"),
		Color("Color")
	}
}
