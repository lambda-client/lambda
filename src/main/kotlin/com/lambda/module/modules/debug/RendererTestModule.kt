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

package com.lambda.module.modules.debug

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.ItemLighting
import com.lambda.graphics.mc.ItemOverlay
import com.lambda.graphics.mc.LineDashStyle.Companion.marchingAnts
import com.lambda.graphics.mc.LineDashStyle.Companion.screenMarchingAnts
import com.lambda.graphics.mc.RenderBuilder.SDFGlow
import com.lambda.graphics.mc.RenderBuilder.SDFOutline
import com.lambda.graphics.mc.RenderBuilder.SDFShadow
import com.lambda.graphics.mc.RenderBuilder.SDFStyle
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.world.toBlockPos
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import org.joml.Quaternionf
import java.awt.Color

object ChunkedRendererTest : Module(
	name = "ChunkedRendererTest",
	description = "Test module for ChunkedRenderer",
	tag = ModuleTag.DEBUG,
) {
	var updated = false

	init {
		chunkedRenderer("ChunkedRendererTest", depthTest = { false }) { pos ->
			runSafe {
				if (updated) return@chunkedRenderer
				if (player.chunkPos != ChunkPos(pos.toBlockPos())) return@chunkedRenderer
				updated = true

				val startPos = lerp(mc.tickDelta, player.prevPos, player.pos)
				lineGradient(
					startPos,
					Color.BLUE,
					startPos.offset(Direction.EAST, 5.0),
					Color.RED,
					0.1f,
					marchingAnts(1f)
				)
				worldText(
					"Test sdf font!",
					startPos.offset(Direction.EAST, 5.0),
					style = SDFStyle(
						outline = SDFOutline(),
						glow = SDFGlow(),
						shadow = SDFShadow()
					)
				)

				screenRectGradient(
					0.02f, 0.1f, 0.15f, 0.05f,
					Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
				)

				screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

				screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

				screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

				screenText(
					"Screen Space Text!",
					0.02f,
					0.30f,
					size = 0.025f,
					style = SDFStyle(
						color = Color.WHITE,
						outline = SDFOutline(),
						shadow = SDFShadow()
					)
				)

				screenText(
					"Centered Screen Text",
					0.5f,
					0.05f,
					size = 0.03f,
					style = SDFStyle(
						color = Color.YELLOW,
						glow = SDFGlow(Color(255, 200, 0, 150)),
						shadow = SDFShadow()
					),
					centered = true
				)

				screenImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					x = 0.08f, y = 0.48f,
					width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
					hasOverlay = true
				)

				val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
				worldImage(
					texture = Identifier.ofVanilla("textures/item/diamond.png"),
					pos = worldImagePos,
					size = 0.8f,
					tint = Color.WHITE
				)

				worldImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					pos = worldImagePos.offset(Direction.EAST, 2.0),
					size = 0.8f,
					tint = Color.WHITE,
					hasOverlay = true
				)

				val stoneState = net.minecraft.block.Blocks.STONE.defaultState
				val stoneModel = mc.bakedModelManager.blockModels.getModel(stoneState)
				val stoneRandom = Random.create()
				val stoneParts = stoneModel.getParts(stoneRandom)

				stoneParts.forEach { part ->
					model(
						part,
						pos = startPos.offset(Direction.WEST, 2.0).add(0.0, 1.0, 0.0),
						scale = Vec3d(1.0, 1.0, 1.0),
						pixelPerfect = true
					)
				}
			}
		}

		listen<TickEvent.Post> {
			updated = false
		}
	}
}

object TickedRendererTest : Module(
	name = "TickedRendererTest",
	description = "Test module for TickedRenderer",
	tag = ModuleTag.DEBUG,
) {
	private val throughWalls by setting("Through Walls", true)

	init {
		tickedRenderer("TickedRendererTest", depthTest = { !throughWalls }) {
			runSafe {
				val startPos = lerp(mc.tickDelta, player.prevPos, player.pos)
				lineGradient(
					startPos,
					Color.BLUE,
					startPos.offset(Direction.EAST, 5.0),
					Color.RED,
					0.1f,
					marchingAnts(1f)
				)
				worldText(
					"Test sdf font!",
					startPos.offset(Direction.EAST, 5.0),
					style = SDFStyle(
						outline = SDFOutline(),
						glow = SDFGlow(),
						shadow = SDFShadow()
					)
				)

				screenRectGradient(
					0.02f, 0.1f, 0.15f, 0.05f,
					Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
				)

				screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

				screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

				screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

				screenText(
					"Screen Space Text!",
					0.02f,
					0.30f,
					size = 0.025f,
					style = SDFStyle(
						color = Color.WHITE,
						outline = SDFOutline(),
						shadow = SDFShadow()
					)
				)

				screenText(
					"Centered Screen Text",
					0.5f,
					0.05f,
					size = 0.03f,
					style = SDFStyle(
						color = Color.YELLOW,
						glow = SDFGlow(Color(255, 200, 0, 150)),
						shadow = SDFShadow()
					),
					centered = true
				)

				screenImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					x = 0.08f, y = 0.48f,
					width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
					hasOverlay = true
				)

				val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
				worldImage(
					texture = Identifier.ofVanilla("textures/item/diamond.png"),
					pos = worldImagePos,
					size = 0.8f,
					tint = Color.WHITE
				)

				worldImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					pos = worldImagePos.offset(Direction.EAST, 2.0),
					size = 0.8f,
					tint = Color.WHITE,
					hasOverlay = true
				)

				val goldState = net.minecraft.block.Blocks.GOLD_BLOCK.defaultState
				val goldModel = mc.bakedModelManager.blockModels.getModel(goldState)
				val goldRandom = Random.create()
				val goldParts = goldModel.getParts(goldRandom)

				goldParts.forEach { part ->
					model(
						part,
						pos = startPos.offset(Direction.WEST, 3.0).add(0.0, 1.0, 0.0),
						scale = Vec3d(1.0, 1.0, 1.0),
						pixelPerfect = true
					)
				}
			}
		}
	}
}

object ImmediateRendererTest : Module(
	name = "ImmediateRendererTest",
	description = "Test module for ImmediateRenderer",
	tag = ModuleTag.DEBUG,
) {
	private val throughWalls by setting("Through Walls", true)

	init {
		immediateRenderer("ImmediateRendererTest", depthTest = { !throughWalls }) {
			runSafe {
				val startPos = lerp(mc.tickDelta, player.prevPos, player.pos)
				lineGradient(
					startPos,
					Color.BLUE,
					startPos.offset(Direction.EAST, 5.0),
					Color.RED,
					0.1f,
					marchingAnts(1f)
				)
				worldText(
					"Test sdf font!",
					startPos.offset(Direction.EAST, 5.0),
					style = SDFStyle(
						outline = SDFOutline(),
						glow = SDFGlow(),
						shadow = SDFShadow()
					)
				)

				screenRectGradient(
					0.02f, 0.1f, 0.15f, 0.05f,
					Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
				)

				screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

				screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

				screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

				screenText(
					"Screen Space Text!",
					0.02f,
					0.30f,
					size = 0.025f,  // 2.5% of screen
					style = SDFStyle(
						color = Color.WHITE,
						outline = SDFOutline(),
						shadow = SDFShadow()
					)
				)

				screenText(
					"Centered Screen Text",
					0.5f,
					0.05f,
					size = 0.03f,
					style = SDFStyle(
						color = Color.YELLOW,
						glow = SDFGlow(Color(255, 200, 0, 150)),
						shadow = SDFShadow()
					),
					centered = true
				)

				screenImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					x = 0.08f, y = 0.48f,
					width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
					hasOverlay = true              // With glint
				)

				val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
				worldImage(
					texture = Identifier.ofVanilla("textures/item/diamond.png"),
					pos = worldImagePos,
					size = 0.8f,
					tint = Color.WHITE
				)

				worldImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					pos = worldImagePos.offset(Direction.EAST, 2.0),
					size = 0.8f,
					tint = Color.WHITE,
					hasOverlay = true
				)

				val diamondState = net.minecraft.block.Blocks.DIAMOND_BLOCK.defaultState
				val diamondModel = mc.bakedModelManager.blockModels.getModel(diamondState)
				val diamondParts = diamondModel.getParts(Random.create())

				val time = System.currentTimeMillis() % 2000L / 2000f
				val rotation = Quaternionf().rotateY(time * Math.PI.toFloat() * 2f)

				diamondParts.forEach { part ->
					model(
						part,
						pos = startPos.offset(Direction.WEST, 4.0).add(0.0, 1.0, 0.0),
						scale = Vec3d(0.7, 0.7, 0.7),
						rotation = rotation,
						centered = true,
						pixelPerfect = true,
						smartAA = true
					)
				}

				worldGuiItem(
					stack = ItemStack(Items.NETHERITE_SWORD),
					pos = startPos.offset(Direction.NORTH, 2.0).add(0.0, 1.5, 0.0),
					scale = 0.5f,
				)

				worldGuiItem(
					stack = ItemStack(Items.GRASS_BLOCK),
					pos = startPos.offset(Direction.NORTH, 2.0).offset(Direction.WEST, 1.0).add(0.0, 1.5, 0.0),
					scale = 0.5f,
					overlay = ItemOverlay.ENCHANT_GLINT,
				)

				screenGuiItem(
					stack = ItemStack(Items.DIAMOND_PICKAXE),
					x = 0.5f, y = 0.4f,
					size = 0.08f,
				)

				val lightTime = (System.currentTimeMillis() % 4000L / 4000f) * 360f

				worldGuiItem(
					stack = ItemStack(Items.GRASS_BLOCK),
					pos = startPos.offset(Direction.NORTH, 3.0),
					scale = 0.5f,
					flat = true,
					overlay = ItemOverlay.ENCHANT_GLINT
				)

				worldGuiItem(
					stack = ItemStack(Items.GOLDEN_APPLE),
					pos = startPos.offset(Direction.NORTH, 3.0).offset(Direction.EAST, 1.0),
					scale = 0.5f,
				)

				screenGuiItem(
					stack = ItemStack(Items.GRASS_BLOCK),
					x = 0.6f, y = 0.4f,
					size = 0.08f,
					rotation = Vec3d(0.0, 0.0, lightTime.toDouble()), // Spinning on screen
					lighting = ItemLighting.NONE
				)

				withOutline(OutlineStyle(Color.CYAN)) {
					box(Box(startPos.add(3.0, 1.0, 3.0), startPos.add(4.0, 2.0, 4.0))) {
						fillColor(Color(255, 255, 255, 60))
						hideOutline()
					}

					worldText(
						"Outlined Text in Immediate!",
						startPos.add(3.5, 2.5, 3.5),
						size = 0.4f,
						style = SDFStyle(color = Color.WHITE)
					)
				}
			}
		}
	}
}
