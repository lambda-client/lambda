package com.lambda.client.module.modules.render

import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderEntityEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.EntityUtils.mobTypeSettings
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.color.HueCycler
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityTNTPrimed
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.*

object Chams : Module(
    name = "Chams",
    description = "Modify entity rendering",
    category = Category.RENDER
) {
    private val page by setting("Page", Page.ENTITY_TYPE)

    /* Entity type settings */
    private val self by setting("Self", false, { page == Page.ENTITY_TYPE })
    private val all by setting("All Entities", false, { page == Page.ENTITY_TYPE })
    private val experience by setting("Experience", false, { page == Page.ENTITY_TYPE && !all })
    private val arrows by setting("Arrows", false, { page == Page.ENTITY_TYPE && !all })
    private val throwable by setting("Throwable", false, { page == Page.ENTITY_TYPE && !all })
    private val items by setting("Items", false, { page == Page.ENTITY_TYPE && !all })
    private val tnt by setting("Primed TNT", false, { page == Page.ENTITY_TYPE && !all })
    private val crystals by setting("Crystals", false, { page == Page.ENTITY_TYPE && !all })
    private val players by setting("Players", true, { page == Page.ENTITY_TYPE && !all })
    private val friends by setting("Friends", false, { page == Page.ENTITY_TYPE && !all && players })
    private val sleeping by setting("Sleeping", false, { page == Page.ENTITY_TYPE && !all && players })
    private val mobs by setting("Mobs", true, { page == Page.ENTITY_TYPE && !all })
    private val passive by setting("Passive Mobs", false, { page == Page.ENTITY_TYPE && !all && mobs })
    private val neutral by setting("Neutral Mobs", true, { page == Page.ENTITY_TYPE && !all && mobs })
    private val hostile by setting("Hostile Mobs", true, { page == Page.ENTITY_TYPE && !all && mobs })

    /* Rendering settings */
    private val throughWall by setting("Through Wall", true, { page == Page.RENDERING })
    private val texture by setting("Texture", false, { page == Page.RENDERING })
    private val lightning by setting("Lightning", false, { page == Page.RENDERING })
    private val customColor by setting("Custom Color", false, { page == Page.RENDERING })
    private val rainbow by setting("Rainbow", false, { page == Page.RENDERING && customColor })
    private val color by setting("Color", ColorHolder(255, 255, 255, 160), visibility = { page == Page.RENDERING && customColor && !rainbow })

    private enum class Page {
        ENTITY_TYPE, RENDERING
    }

    private var cycler = HueCycler(600)

    init {
        safeListener<RenderEntityEvent.All>(2000) {
            if (!checkEntityType(it.entity)) return@safeListener

            when (it.phase) {
                Phase.PRE -> {
                    if (throughWall) glDepthRange(0.0, 0.01)
                }
                Phase.PERI -> {
                    if (throughWall) glDepthRange(0.0, 1.0)
                }
                else -> {
                    // Doesn't need to do anything on post phase
                }
            }
        }

        safeListener<RenderEntityEvent.Model> {
            if (!checkEntityType(it.entity)) return@safeListener

            when (it.phase) {
                Phase.PRE -> {
                    if (!texture) glDisable(GL_TEXTURE_2D)
                    if (!lightning) glDisable(GL_LIGHTING)
                    if (customColor) {
                        if (rainbow) cycler.currentRgba(color.a).setGLColor()
                        else glColor4f(color.r / 255.0f, color.g / 255.0f, color.b / 255.0f, color.a / 255.0f)

                        GlStateUtils.blend(true)
                        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
                    }
                }
                Phase.POST -> {
                    if (!texture) glEnable(GL_TEXTURE_2D)
                    if (!lightning) glEnable(GL_LIGHTING)
                    if (customColor) {
                        GlStateUtils.blend(false)
                        glColor4f(1f, 1f, 1f, 1f)
                    }
                }
                else -> {
                    // RenderEntityEvent.Model doesn't have peri phase
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.START) cycler++
        }
    }

    private fun SafeClientEvent.checkEntityType(entity: Entity) =
        (self || entity != player) && (
            all
                || experience && entity is EntityXPOrb
                || arrows && entity is EntityArrow
                || throwable && entity is EntityThrowable
                || items && entity is EntityItem
                || crystals && entity is EntityEnderCrystal
                || tnt && entity is EntityTNTPrimed
                || players && entity is EntityPlayer && EntityUtils.playerTypeCheck(entity, friends, sleeping)
                || mobTypeSettings(entity, mobs, passive, neutral, hostile)
            )
}
