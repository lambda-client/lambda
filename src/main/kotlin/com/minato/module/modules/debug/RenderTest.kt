
package com.minato.module.modules.debug

import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.minato.graphics.util.DynamicAABB.Companion.dynamicBox
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.extension.tickDelta
import com.minato.util.math.setAlpha
import com.minato.util.world.entitySearch
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import java.awt.Color

@Suppress("unused")
object RenderTest : Module(
    name = "Render:shrimp:Test:canned_food:",
    description = "RenderTest",
    tag = ModuleTag.DEBUG,
) {
    private val test1 by setting("Toggle visibility", true)
    private val test21 by setting("Hallo 1", true, visibility = ::test1)
    private val test22 by setting("Hallo Slider", 1.0, 0.0..5.0, 0.5, visibility = ::test1)
    private val test23 by setting("Hallo String", "bruh", visibility = ::test1)
    private val test31 by setting("Holla huh 1", true, visibility = { !test1 })
    private val test32 by setting("Holla buh 2", true, visibility = { !test1 })

    private val outlineColor = Color(100, 150, 255).setAlpha(0.5)
    private val filledColor = outlineColor.setAlpha(0.2)

    init {
        immediateRenderer("RenderTest Immediate Renderer") {
            runSafe {
                entitySearch<LivingEntity>(8.0)
                    .forEach { entity ->
                        box(entity.dynamicBox.box(mc.tickDelta) ?: return@forEach) {
                            colors(filledColor, outlineColor)
                        }
                    }
            }
        }

        tickedRenderer("RenderTest Ticked Renderer") {
            runSafe {
                box(Box.of(player.pos, 0.3, 0.3, 0.3)) {
                    colors(filledColor, outlineColor)
                }
            }
        }
    }
}
