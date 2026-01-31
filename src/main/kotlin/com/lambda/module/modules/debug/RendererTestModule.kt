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

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.ScreenRenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.LineDashStyle.Companion.marchingAnts
import com.lambda.graphics.mc.LineDashStyle.Companion.screenMarchingAnts
import com.lambda.graphics.mc.RenderBuilder.SDFGlow
import com.lambda.graphics.mc.RenderBuilder.SDFOutline
import com.lambda.graphics.mc.RenderBuilder.SDFShadow
import com.lambda.graphics.mc.RenderBuilder.SDFStyle
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedEsp
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.graphics.mc.renderer.TickedRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.world.toBlockPos
import net.minecraft.item.Items
import net.minecraft.util.Identifier
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import java.awt.Color

/**
 * Test module for ChunkedRenderer - renders blocks around the player using chunk-based caching.
 * Geometry is cached per-chunk and only rebuilt when chunks change.
 */
object ChunkedRendererTest : Module(
	name = "ChunkedRendererTest",
	description = "Test module for ChunkedRenderer - cached chunk-based rendering",
	tag = ModuleTag.DEBUG,
) {
	var changedAlready = false

	private val esp = chunkedEsp("ChunkedRendererTest", depthTest = false) { world, pos ->
		runSafe {
			if (player.chunkPos != ChunkPos(pos.toBlockPos())) return@chunkedEsp
			if (changedAlready) return@chunkedEsp
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

			// Screen-space test renders (normalized 0-1 coordinates)
			// Test screen rect with gradient
			screenRectGradient(
				0.02f, 0.1f, 0.15f, 0.05f,  // x, y, width, height (0-1)
				Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
			)

			// Test screen rect with solid color
			screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

			// Test screen line
			screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

			// Test screen line with gradient
			screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

			// Test screen text
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

			// Test centered screen text
			screenText(
				"Centered Screen Text",
				0.5f,  // 50% from left = center
				0.05f, // 5% from top
				size = 0.03f,  // 3% of screen
				style = SDFStyle(
					color = Color.YELLOW,
					glow = SDFGlow(Color(255, 200, 0, 150)),
					shadow = SDFShadow()
				),
				centered = true
			)

			// ========== Item Rendering Tests ==========
			// Test screen items at various positions and sizes
			// Size is normalized (e.g., 0.03 = 3% of screen height)
			screenItem(Items.DIAMOND_SWORD.defaultStack, 0.02f, 0.40f)  // Default size ~1.5%
			screenItem(Items.NETHERITE_CHESTPLATE.defaultStack, 0.06f, 0.40f)
			screenItem(Items.ENCHANTED_GOLDEN_APPLE.defaultStack, 0.10f, 0.40f)
			// Test larger item (5% of screen height)
			screenItem(Items.DIAMOND.defaultStack, 0.14f, 0.40f, size = 0.05f)

			// ========== Image Rendering Tests ==========
			// Test screen image using simple Identifier-based API
			//				screenImage(
			//					texture = Identifier.ofVanilla("textures/gui/sprites/hud/heart/hardcore_full.png"),
			//					x = 0.02f, y = 0.48f,
			//					width = 0.12f, height = 0.12f,
			//					tint = Color.WHITE
			//				)

			// Test screen image with tint
			screenImage(
				texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
				x = 0.08f, y = 0.48f,
				width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
				hasOverlay = true              // With glint
			)

			// Test world image - billboard facing the camera
			val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
			worldImage(
				texture = Identifier.ofVanilla("textures/item/diamond.png"),
				pos = worldImagePos,
				size = 0.8f,
				tint = Color.WHITE
			)

			// Test world image with glint
			worldImage(
				texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
				pos = worldImagePos.offset(Direction.EAST, 2.0),
				size = 0.8f,
				tint = Color.WHITE,
				hasOverlay = true
			)
			changedAlready = true
		}
	}

	init {
		listen<ScreenRenderEvent> {
			esp.renderScreen()
		}

		listen<TickEvent.Post> { changedAlready = false }

		onDisable { esp.close() }
	}
}

/**
 * Test module for TickedRenderer - rebuilds geometry every tick.
 * Uses tick-camera relative coordinates with render-time delta interpolation.
 */
object TickedRendererTest : Module(
	name = "TickedRendererTest",
	description = "Test module for TickedRenderer - tick-based rendering with interpolation",
	tag = ModuleTag.DEBUG,
) {
	private val throughWalls by setting("Through Walls", true)
	private val renderer = TickedRenderer("TickedRendererTest")

	init {
		listen<RenderEvent.Render> {
			renderer.render()
		}
		
		listen<ScreenRenderEvent> {
			renderer.renderScreen()
		}

		listen<TickEvent.Post> {
			renderer.depthTest = !throughWalls
			renderer.clear()
			
			renderer.shapes {
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

				// Screen-space test renders (normalized 0-1 coordinates)
				// Test screen rect with gradient
				screenRectGradient(
					0.02f, 0.1f, 0.15f, 0.05f,  // x, y, width, height (0-1)
					Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
				)

				// Test screen rect with solid color
				screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

				// Test screen line
				screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

				// Test screen line with gradient
				screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

				// Test screen text
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

				// Test centered screen text
				screenText(
					"Centered Screen Text",
					0.5f,  // 50% from left = center
					0.05f, // 5% from top
					size = 0.03f,  // 3% of screen
					style = SDFStyle(
						color = Color.YELLOW,
						glow = SDFGlow(Color(255, 200, 0, 150)),
						shadow = SDFShadow()
					),
					centered = true
				)

				// ========== Item Rendering Tests ==========
				// Test screen items at various positions and sizes
				// Size is normalized (e.g., 0.03 = 3% of screen height)
				screenItem(Items.DIAMOND_SWORD.defaultStack, 0.02f, 0.40f)  // Default size ~1.5%
				screenItem(Items.NETHERITE_CHESTPLATE.defaultStack, 0.06f, 0.40f)
				screenItem(Items.ENCHANTED_GOLDEN_APPLE.defaultStack, 0.10f, 0.40f)
				// Test larger item (5% of screen height)
				screenItem(Items.DIAMOND.defaultStack, 0.14f, 0.40f, size = 0.05f)

				// ========== Image Rendering Tests ==========
				// Test screen image using simple Identifier-based API
				//				screenImage(
				//					texture = Identifier.ofVanilla("textures/gui/sprites/hud/heart/hardcore_full.png"),
				//					x = 0.02f, y = 0.48f,
				//					width = 0.12f, height = 0.12f,
				//					tint = Color.WHITE
				//				)

				// Test screen image with tint
				screenImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					x = 0.08f, y = 0.48f,
					width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
					hasOverlay = true              // With glint
				)

				// Test world image - billboard facing the camera
				val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
				worldImage(
					texture = Identifier.ofVanilla("textures/item/diamond.png"),
					pos = worldImagePos,
					size = 0.8f,
					tint = Color.WHITE
				)

				// Test world image with glint
				worldImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					pos = worldImagePos.offset(Direction.EAST, 2.0),
					size = 0.8f,
					tint = Color.WHITE,
					hasOverlay = true
				)
			}
			
			renderer.upload()
		}

		onDisable { renderer.close() }
	}
}

/**
 * Test module for ImmediateRenderer - rebuilds geometry every frame.
 * Uses render-camera relative coordinates for smooth interpolated rendering.
 */
object ImmediateRendererTest : Module(
	name = "ImmediateRendererTest",
	description = "Test module for ImmediateRenderer - frame-based interpolated rendering",
	tag = ModuleTag.DEBUG,
) {
	private val throughWalls by setting("Through Walls", true)
	private val renderer = ImmediateRenderer("ImmediateRendererTest")

	init {
		listen<RenderEvent.Render> {
			renderer.depthTest = !throughWalls
			renderer.tick()
			
			renderer.shapes {
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

				// Screen-space test renders (normalized 0-1 coordinates)
				// Test screen rect with gradient
				screenRectGradient(
					0.02f, 0.1f, 0.15f, 0.05f,  // x, y, width, height (0-1)
					Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW
				)

				// Test screen rect with solid color
				screenRect(0.02f, 0.17f, 0.1f, 0.03f, Color(50, 50, 200, 180))

				// Test screen line
				screenLine(0.02f, 0.22f, 0.17f, 0.25f, Color.CYAN, 0.003f, dashStyle = screenMarchingAnts())

				// Test screen line with gradient
				screenLineGradient(0.02f, 0.27f, Color.MAGENTA, 0.17f, 0.27f, Color.ORANGE, 0.004f)

				// Test screen text
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

				// Test centered screen text
				screenText(
					"Centered Screen Text",
					0.5f,  // 50% from left = center
					0.05f, // 5% from top
					size = 0.03f,  // 3% of screen
					style = SDFStyle(
						color = Color.YELLOW,
						glow = SDFGlow(Color(255, 200, 0, 150)),
						shadow = SDFShadow()
					),
					centered = true
				)

				// ========== Item Rendering Tests ==========
				// Test screen items at various positions and sizes
				// Size is normalized (e.g., 0.03 = 3% of screen height)
				screenItem(Items.DIAMOND_SWORD.defaultStack, 0.02f, 0.40f)  // Default size ~1.5%
				screenItem(Items.NETHERITE_CHESTPLATE.defaultStack, 0.06f, 0.40f)
				screenItem(Items.ENCHANTED_GOLDEN_APPLE.defaultStack, 0.10f, 0.40f)
				// Test larger item (5% of screen height)
				screenItem(Items.DIAMOND.defaultStack, 0.14f, 0.40f, size = 0.05f)

				// ========== Image Rendering Tests ==========
				// Test screen image using simple Identifier-based API
//				screenImage(
//					texture = Identifier.ofVanilla("textures/gui/sprites/hud/heart/hardcore_full.png"),
//					x = 0.02f, y = 0.48f,
//					width = 0.12f, height = 0.12f,
//					tint = Color.WHITE
//				)

				// Test screen image with tint
				screenImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					x = 0.08f, y = 0.48f,
					width = 0.1f, height = 0.1f * mc.window.width / mc.window.height.toFloat(),
					hasOverlay = true              // With glint
				)

				// Test world image - billboard facing the camera
				val worldImagePos = startPos.offset(Direction.NORTH, 3.0).add(0.0, 1.5, 0.0)
				worldImage(
					texture = Identifier.ofVanilla("textures/item/diamond.png"),
					pos = worldImagePos,
					size = 0.8f,
					tint = Color.WHITE
				)

				// Test world image with glint
				worldImage(
					texture = Identifier.ofVanilla("textures/item/netherite_sword.png"),
					pos = worldImagePos.offset(Direction.EAST, 2.0),
					size = 0.8f,
					tint = Color.WHITE,
					hasOverlay = true
				)
			}
			
			renderer.upload()
			renderer.render()
		}
		
		listen<ScreenRenderEvent> {
			renderer.renderScreen()
		}

		onDisable { renderer.close() }
	}
}
