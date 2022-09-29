package com.lambda.client.module.modules.render

import com.lambda.client.commons.utils.MathUtils.convertRange
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.getTargetList
import com.lambda.client.util.EntityUtils.isNeutral
import com.lambda.client.util.EntityUtils.isPassive
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.color.HueCycler
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap

object Tracers : Module(
    name = "Tracers",
    description = "Draws lines to other living entities",
    category = Category.RENDER
) {
    private val page by setting("Page", Page.ENTITY_TYPE)

    /* Entity type settings */
    private val players by setting("Players", true, { page == Page.ENTITY_TYPE })
    private val friends by setting("Friends", false, { page == Page.ENTITY_TYPE && players })
    private val sleeping by setting("Sleeping", false, { page == Page.ENTITY_TYPE && players })
    private val mobs by setting("Mobs", true, { page == Page.ENTITY_TYPE })
    private val passive by setting("Passive Mobs", false, { page == Page.ENTITY_TYPE && mobs })
    private val neutral by setting("Neutral Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val hostile by setting("Hostile Mobs", true, { page == Page.ENTITY_TYPE && mobs })
    private val invisible by setting("Invisible", true, { page == Page.ENTITY_TYPE })
    private val range by setting("Range", 64, 8..512, 8, { page == Page.ENTITY_TYPE })

    /* Color settings */
    private val colorPlayer by setting("Player Color", ColorHolder(155, 144, 255), false, { page == Page.COLOR })
    private val colorFriend by setting("Friend Color", ColorHolder(32, 250, 32), false, { page == Page.COLOR })
    private val colorPassive by setting("Passive Mob Color", ColorHolder(132, 240, 32), false, { page == Page.COLOR })
    private val colorNeutral by setting("Neutral Mob Color", ColorHolder(255, 232, 0), false, { page == Page.COLOR })
    private val colorHostile by setting("Hostile Mob Color", ColorHolder(250, 32, 32), false, { page == Page.COLOR })
    private val colorFar by setting("Far Color", ColorHolder(255, 255, 255), false, { page == Page.COLOR })

    /* General rendering settings */
    private val alpha by setting("Alpha", 255, 0..255, 1, { page == Page.RENDERING })
    private val yOffset by setting("Y Offset Percentage", 0, 0..100, 5, { page == Page.RENDERING })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f, { page == Page.RENDERING })
    private val fadeSpeed by setting("Fade Speed", 150, 0..500, 25, { page == Page.RENDERING }, unit = "ms")

    /* Range color settings */
    private val rangedColor by setting("Ranged Color", true, { page == Page.RANGE_COLOR })
    private val colorChangeRange by setting("Color Change Range", 16, 8..128, 8, { page == Page.RANGE_COLOR && rangedColor })
    private val playerOnly by setting("Player Only", true, { page == Page.RANGE_COLOR && rangedColor })
    private val alphaFar by setting("Far Alpha", 127, 0..255, 1, { page == Page.RANGE_COLOR && rangedColor })

    private enum class Page {
        ENTITY_TYPE, COLOR, RENDERING, RANGE_COLOR
    }

    private var renderList = ConcurrentHashMap<Entity, TracerData>()
    private var cycler = HueCycler(600)
    private val renderer = ESPRenderer()

    init {
        listener<RenderWorldEvent> {
            renderer.aTracer = alpha
            renderer.thickness = thickness
            renderer.tracerOffset = yOffset

            for ((entity, tracerData) in renderList) {
                val rgba = tracerData.color.clone()

                if (fadeSpeed > 0) {
                    val animationCoefficient = tracerData.age * tracerData.color.a / fadeSpeed

                    rgba.a = if (tracerData.isEntityPresent) {
                        animationCoefficient.toInt().coerceAtMost(tracerData.color.a)
                    } else {
                        (-animationCoefficient + tracerData.color.a).toInt().coerceAtLeast(0)
                    }
                }

                renderer.add(entity, rgba)
            }

            renderer.render(true)
        }

        safeListener<TickEvent.ClientTickEvent> {
            cycler++
            alwaysListening = renderList.isNotEmpty()

            val player = arrayOf(players, friends, sleeping)
            val mob = arrayOf(mobs, passive, neutral, hostile)
            val entityList = if (isEnabled) {
                getTargetList(player, mob, invisible, range.toFloat(), ignoreSelf = false)
            } else {
                ArrayList()
            }

            entityList.forEach { entity ->
                renderList.computeIfAbsent(entity) {
                    TracerData(getColor(entity), System.currentTimeMillis(), true)
                }
            }

            renderList.forEach { (entity, tracerData) ->
                if (entityList.contains(entity)) {
                    tracerData.color = getColor(entity)
                    return@forEach
                }

                if (tracerData.isEntityPresent) {
                    tracerData.isEntityPresent = false
                    tracerData.timeStamp = System.currentTimeMillis()
                    return@forEach
                }

                if (tracerData.age > fadeSpeed || fadeSpeed == 0) renderList.remove(entity)
            }
        }
    }

    private fun getColor(entity: Entity): ColorHolder {
        val color = when {
            FriendManager.isFriend(entity.name) -> colorFriend
            entity is EntityPlayer -> colorPlayer
            entity.isPassive -> colorPassive
            entity.isNeutral -> colorNeutral
            else -> colorHostile
        }

        return getRangedColor(entity, color)
    }

    private fun getRangedColor(entity: Entity, c: ColorHolder): ColorHolder {
        if (!rangedColor || playerOnly && entity !is EntityPlayer) return c
        val distance = mc.player.getDistance(entity)

        val r = convertRange(distance, 0f, colorChangeRange.toFloat(), c.r.toFloat(), colorFar.r.toFloat()).toInt()
        val g = convertRange(distance, 0f, colorChangeRange.toFloat(), c.g.toFloat(), colorFar.g.toFloat()).toInt()
        val b = convertRange(distance, 0f, colorChangeRange.toFloat(), c.b.toFloat(), colorFar.b.toFloat()).toInt()
        val a = convertRange(distance, 0f, colorChangeRange.toFloat(), alpha.toFloat(), alphaFar.toFloat()).toInt()
        return ColorHolder(r, g, b, a)
    }

    private data class TracerData(var color: ColorHolder, var timeStamp: Long, var isEntityPresent: Boolean) {
        val age get() = System.currentTimeMillis() - timeStamp
    }
}
