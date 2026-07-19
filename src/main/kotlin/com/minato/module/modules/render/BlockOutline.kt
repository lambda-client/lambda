
package com.minato.module.modules.render

import com.minato.config.ConfigEditor.forEachSetting
import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.blocks.OutlineSettings
import com.minato.config.blocks.WorldLineSettings
import com.minato.config.withEdits
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.blockState
import com.minato.util.extension.tickDelta
import com.minato.util.math.lerp
import com.minato.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.math.Box
import java.awt.Color

object BlockOutline : Module(
	name = "BlockOutline",
	description = "Overrides the default block outline rendering",
	tag = ModuleTag.RENDER
) {
	private enum class Mode {
		Boxes,
		Outline
	}

	private val mode by setting("Mode", Mode.Boxes)
	private val interpolate by setting("Interpolate", true) { mode == Mode.Boxes }
	private val depthTest by setting("Depth Test", true)

	private const val BOX_FILL_GROUP = "Box Fill"
	private const val BOX_OUTLINE_GROUP = "Box Outline"
	private const val OUTLINE_GROUP = "Outline"

	@Group(BOX_FILL_GROUP) private val fill by setting("Fill", true) { mode == Mode.Boxes }
	@Group(BOX_FILL_GROUP) private val fillColor by setting("Fill Color", Color(255, 255, 255, 20)) { fill && mode == Mode.Boxes }
	@Group(BOX_OUTLINE_GROUP) private val boxOutline by setting("Box Outline", true) { mode == Mode.Boxes }
	@Group(BOX_OUTLINE_GROUP) private val boxOutlineColor by setting("Box Outline Color", Color(255, 255, 255, 120)) { mode == Mode.Boxes && boxOutline }
	@Group(BOX_OUTLINE_GROUP) private val lineConfig by configBlock(WorldLineSettings(this))
		.withEdits {
			hide(::startColor, ::endColor)
			forEachSetting {
				visibility { old -> { old() && mode == Mode.Boxes } }
			}
		}

	@Group(OUTLINE_GROUP) private val outlineColor by setting("Outline Color", boxOutlineColor) { mode == Mode.Outline }
	@Group(OUTLINE_GROUP) private val outlineStyle by configBlock(OutlineSettings(this))
		.withEdits {
			forEachSetting {
				visibility { old -> { old() && mode == Mode.Outline } }
			}
		}

	var previous: List<Box>? = null

	init {
		immediateRenderer("BlockOutline Immediate Renderer", depthTest = { depthTest }) {
			runSafe {
				val hitResult = mc.crosshairTarget?.blockResult ?: return@runSafe
				val pos = hitResult.blockPos
				if (mode == Mode.Outline) {
					worldOutline(pos, outlineStyle.toStyle(outlineColor))
					return@runSafe
				}
				val blockState = blockState(pos)
				val boxes = blockState
					.getOutlineShape(world, pos)
					.boundingBoxes
					.let { boxes ->
						boxes.mapIndexed { index, box ->
							val offset = box.offset(pos)
							val interpolated = previous?.let { previous ->
								if (!interpolate || previous.size < boxes.size) null
								else lerp(mc.tickDelta, previous[index], offset)
							} ?: offset
							interpolated.expand(0.0001)
						}
					}

				boxes.forEach { box ->
					box(box, lineConfig) {
						colors(fillColor, boxOutlineColor)
						if (!fill) hideFill()
						if (!boxOutline) hideOutline()
					}
				}
			}
		}

		listen<TickEvent.Post> {
			val hitResult = mc.crosshairTarget?.blockResult ?: return@listen
			val state = blockState(hitResult.blockPos)
			previous = state
				.getOutlineShape(world, hitResult.blockPos).boundingBoxes
				.map { it.offset(hitResult.blockPos) }
		}
	}
}