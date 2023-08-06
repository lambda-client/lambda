package com.lambda.client.manager.managers

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.*
import com.lambda.client.activity.types.RenderAABBActivity
import com.lambda.client.activity.types.RenderAABBActivity.Companion.checkAABBRender
import com.lambda.client.activity.types.RenderOverlayTextActivity
import com.lambda.client.activity.types.RenderOverlayTextActivity.Companion.checkOverlayRender
import com.lambda.client.activity.types.TimedActivity
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.executionCountPerTick
import com.lambda.client.module.modules.client.BuildTools.textScale
import com.lambda.client.module.modules.client.BuildTools.tickDelay
import com.lambda.client.module.modules.misc.WorldEater
import com.lambda.client.module.modules.player.AutoEat
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.countItem
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11
import java.util.*

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

            if (BuildTools.storageManagement) maintainInventory()

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

            RenderAABBActivity.normalizedRender
                .forEach { renderAABB ->
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

                FontRenderAdapter.drawString(
                    renderText.text,
                    halfWidth,
                    lineHeight * renderText.index - yLift,
                    color = renderText.color
                )

                GL11.glPopMatrix()
            }
            GlStateUtils.rescaleMc()
        }
    }

    override fun getCurrentActivity() =
        subActivities.maxByOrNull { it.owner?.modulePriority ?: 0 }?.getCurrentActivity() ?: this

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

    private fun SafeClientEvent.maintainInventory() {
        val stashOrders = mutableListOf<Pair<Stash, Order>>()

        if (subActivities.filterIsInstance<StoreItemToShulkerBox>().isEmpty()
            && player.inventorySlots.countEmpty() <= BuildTools.keepFreeSlots
        ) {
            val itemsToStore = WorldEater.collectables.filter {
                player.inventorySlots.countItem(it) > 0
            }

            if (itemsToStore.isNotEmpty()) {
                MessageSendHelper.sendChatMessage("Compressing ${
                    itemsToStore.joinToString { "${it.registryName}" }
                } to shulker boxes.")

                addSubActivities(itemsToStore.map {
                    StoreItemToShulkerBox(ItemInfo(it, 0))
                })
            } else if (subActivities.filterIsInstance<StashTransaction>().isEmpty()) {
                stashOrders.addAll(itemsToStore.mapNotNull { itemToStore ->
                    WorldEater.dropOff
                        .filter { it.items.contains(itemToStore) }
                        .minByOrNull { player.distanceTo(it.area.center) }?.let { stash ->
                            stash to Order(Action.PUSH, ItemInfo(itemToStore, number = 0))
                        }
                })
            }
        }

        if (subActivities.filterIsInstance<StashTransaction>().isNotEmpty()) return

        if (BuildTools.usePickaxe) checkItem(Items.DIAMOND_PICKAXE).ifPresent { stashOrders.add(it) }
        if (BuildTools.useShovel) checkItem(Items.DIAMOND_SHOVEL).ifPresent { stashOrders.add(it) }
        if (BuildTools.useAxe) checkItem(Items.DIAMOND_AXE).ifPresent { stashOrders.add(it) }
        if (BuildTools.useSword) checkItem(Items.DIAMOND_SWORD).ifPresent { stashOrders.add(it) }
        if (BuildTools.useShears) checkItem(Items.SHEARS).ifPresent { stashOrders.add(it) }

        checkItem(Items.GOLDEN_APPLE).ifPresent { stashOrders.add(it) }

        if (stashOrders.isNotEmpty()) {
            addSubActivities(stashOrders.groupBy { it.first }.map { group ->
                StashTransaction(group.value.map { it.second }, group.key)
            })
            MessageSendHelper.sendChatMessage("Inventory full. Storing ${
                stashOrders.joinToString(" ") { "${it.second.itemInfo.item.registryName} -> ${it.first.area.center}" }
            }")
        }
    }

    private fun SafeClientEvent.checkItem(item: Item): Optional<Pair<Stash, Order>> {
        if (player.allSlots.countItem(item) >= BuildTools.minToolAmount) return Optional.empty()

        val optimalStash = WorldEater.stashes
            .filter { it.items.contains(item) }
            .minByOrNull { player.distanceTo(it.area.center) } ?: return Optional.empty()

        MessageSendHelper.sendChatMessage("Missing ${
            item.registryName
        }. Fetching from stash at ${optimalStash.area.center.asString()}.")

        return Optional.of(
            optimalStash to Order(Action.PULL, ItemInfo(item))
        )
    }
}