package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.ColourConverter
import me.zeroeightsix.kami.util.GeometryMasks
import me.zeroeightsix.kami.util.KamiTessellator
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityThrowable
import org.lwjgl.opengl.GL11
import java.util.stream.Collectors

/**
 * @author polymer
 * Updated by dominikaaaa on 30/03/20
 */
@Module.Info(
        name = "BoxESP",
        description = "Draws a box around small entities",
        category = Module.Category.RENDER
)
class BoxESP : Module() {
    private val experience = register(Settings.b("Experience", true))
    private val arrows = register(Settings.b("Arrows", true))
    private val throwable = register(Settings.b("Throwable", true))
    private val items = register(Settings.b("Items", false))
    private val alpha = register(Settings.integerBuilder("Alpha").withMinimum(1).withMaximum(255).withValue(100).build())
    private val red = register(Settings.integerBuilder("Red").withMinimum(1).withMaximum(255).withValue(155).build())
    private val green = register(Settings.integerBuilder("Green").withMinimum(1).withMaximum(255).withValue(144).build())
    private val blue = register(Settings.integerBuilder("Blue").withMinimum(1).withMaximum(255).withValue(255).build())
    override fun onWorldRender(event: RenderEvent) {
        val entities = mc.world.loadedEntityList.stream().filter { entity: Entity -> getEntity(entity) }.collect(Collectors.toList())
        for (e in entities) {
            KamiTessellator.prepare(GL11.GL_QUADS)
            val colour = ColourConverter.rgbToInt(red.value, green.value, blue.value, alpha.value)
            KamiTessellator.drawBoxSmall(e.positionVector.x.toFloat() - 0.25f, e.positionVector.y.toFloat(), e.positionVector.z.toFloat() - 0.25f, colour, GeometryMasks.Quad.ALL)
            KamiTessellator.release()
        }
    }

    private fun getEntity(entity: Entity): Boolean {
        return if (entity is EntityXPOrb && experience.value) true else if (entity is EntityArrow && arrows.value) true else if (entity is EntityThrowable && throwable.value) true else entity is EntityItem && items.value
    }
}