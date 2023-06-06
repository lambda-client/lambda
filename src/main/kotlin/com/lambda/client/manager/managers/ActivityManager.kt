package com.lambda.client.manager.managers

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.inventory.DumpSlot
import com.lambda.client.activity.activities.storage.StoreItemsToStash
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.activity.types.RenderAABBActivity.Companion.checkAABBRender
import com.lambda.client.activity.types.RenderOverlayTextActivity
import com.lambda.client.activity.types.RenderOverlayTextActivity.Companion.checkOverlayRender
import com.lambda.client.activity.types.TimedActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.executionCountPerTick
import com.lambda.client.module.modules.client.BuildTools.textScale
import com.lambda.client.module.modules.client.BuildTools.tickDelay
import com.lambda.client.module.modules.player.AutoEat
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11

object ActivityManager : Manager, Activity() {
    private val renderer = ESPRenderer()
    const val MAX_DEPTH = 25
    private val tickTimer = TickTimer(TimeUnit.TICKS)

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (hasNoSubActivities
                || event.phase != TickEvent.Phase.START
            ) return@safeListener

            /* life support systems */
            if (AutoEat.eating) return@safeListener

            if (BuildTools.storageManagement
                && subActivities.filterIsInstance<StoreItemsToStash>().isEmpty()
                && player.inventorySlots.countEmpty() <= BuildTools.keepFreeSlots
            ) {
                MessageSendHelper.sendChatMessage("Inventory full, storing items to stash at ${BuildTools.storagePos1.value.asString()}")
                addSubActivities(StoreItemsToStash(listOf(Blocks.STONE.item, Blocks.DIRT.item)))
            }

            allSubActivities
                .filter { it.status == Status.RUNNING && it.subActivities.isEmpty() }
                .forEach {
                    with(it) {
                        updateTypesOnTick(it)
                    }
                }

            if (!tickTimer.tick(tickDelay * 1L)) return@safeListener

            var lastActivity: Activity? = null

            BaritoneUtils.settings?.allowInventory?.value = false

            repeat(executionCountPerTick) {
                val current = getCurrentActivity()

                with(current) {
                    // ToDo: Find a working way to guarantee specific age of activity
                    (lastActivity as? TimedActivity)?.let {
                        if (age < it.earliestFinish) return@repeat
                    }

                    updateActivity()
                    checkOverlayRender()
                    checkAABBRender()
                }

                lastActivity = current
            }
        }

        listener<RenderWorldEvent> {
            if (hasNoSubActivities) return@listener

            renderer.aFilled = BuildTools.aFilled
            renderer.aOutline = BuildTools.aOutline
            renderer.thickness = BuildTools.thickness

            RenderAABBActivity.normalizedRender.forEach { renderAABB ->
                renderer.add(renderAABB.renderAABB, renderAABB.color)
            }

            renderer.render(true)
        }

        listener<RenderOverlayEvent> {
            if (hasNoSubActivities) return@listener

            GlStateUtils.rescaleActual()

            RenderOverlayTextActivity.normalizedRender.forEach { renderText ->
                GL11.glPushMatrix()
                val screenPos = ProjectionUtils.toScreenPos(renderText.origin)
                GL11.glTranslated(screenPos.x, screenPos.y, 0.0)
                GL11.glScalef(textScale * 2.0f, textScale * 2.0f, 1.0f)

                val halfWidth = FontRenderAdapter.getStringWidth(renderText.text) / -2.0f
                val lineHeight = FontRenderAdapter.getFontHeight() + 2.0f
                val yLift = lineHeight * 3 / 2

                FontRenderAdapter.drawString(renderText.text, halfWidth, lineHeight * renderText.index - yLift, color = renderText.color)

                GL11.glPopMatrix()
            }
            GlStateUtils.rescaleMc()
        }
    }

    override fun getCurrentActivity(): Activity {
        return subActivities.maxByOrNull { it.owner?.modulePriority ?: 0 }?.getCurrentActivity() ?: this
    }

    fun reset() {
        ListenerManager.listenerMap.keys.filterIsInstance<Activity>().forEach {
            it.parent?.let { _ ->
                LambdaEventBus.unsubscribe(it)
                ListenerManager.unregister(it)
            }
        }
        ListenerManager.asyncListenerMap.keys.filterIsInstance<Activity>().forEach {
            it.parent?.let { _ ->
                LambdaEventBus.unsubscribe(it)
                ListenerManager.unregister(it)
            }
        }
        BaritoneUtils.primary?.pathingBehavior?.cancelEverything()
        subActivities.clear()
    }
}