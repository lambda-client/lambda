package com.lambda.client.module.modules.render

import com.lambda.client.event.events.BlockBreakEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

object BreakingESP : Module(
    name = "BreakingESP",
    description = "Highlights blocks being broken near you",
    category = Category.RENDER
) {
    private val espSelf by setting("ESP Self", true)
    private val warnSelf by setting("Warn Self", false)
    private val obsidianOnly by setting("Obsidian Only", false)
    private val warning by setting("Warn", false)
    private val warningProgress by setting("Warn Progress", 4, 0..10, 1)
    private val chatWarn by setting("Chat Warning", false)
    private val screenWarn by setting("HUD Warning", true)
    private val soundWarn by setting("Sound Warning", false)
    private val range by setting("Range", 16.0f, 2.0f..32.0f, 2.0f)
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val tracer by setting("Tracer", false)
    private val color by setting("Color", ColorHolder(255, 255, 255))
    private val aFilled by setting("Filled Alpha", 31, 0..255, 1, { filled })
    private val aOutline by setting("Outline Alpha", 200, 0..255, 1, { outline })
    private val aTracer by setting("Tracer Alpha", 255, 0..255, 1, { outline })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)

    private val breakingBlockList = LinkedHashMap<Int, Triple<BlockPos, Int, Pair<Boolean, Boolean>>>() /* <BreakerID, <Position, Progress, <Warned, Render>> */
    private var warn = false
    private var delay = 0
    private var warningText = ""

    init {
        safeListener<RenderWorldEvent> {
            val renderer = ESPRenderer()
            renderer.aFilled = if (filled) aFilled else 0
            renderer.aOutline = if (outline) aOutline else 0
            renderer.aTracer = if (tracer) aTracer else 0
            renderer.thickness = thickness

            var selfBreaking: AxisAlignedBB? = null
            for ((breakID, triple) in breakingBlockList) {
                if (triple.third.second) {
                    val box = world.getBlockState(triple.first).getSelectedBoundingBox(world, triple.first)
                    val progress = triple.second / 9f
                    val resizedBox = box.shrink((1f - progress) * box.averageEdgeLength * 0.5)
                    if (world.getEntityByID(breakID) == player) {
                        selfBreaking = resizedBox
                        continue
                    }
                    renderer.add(resizedBox, color)
                }
            }
            renderer.render(true)

            if (selfBreaking != null) {
                renderer.aTracer = 0
                renderer.add(selfBreaking, color)
                renderer.render(true)
            }
        }

        listener<RenderOverlayEvent> {
            if (screenWarn && warn) {
                if (delay++ > 100) warn = false
                val scaledResolution = ScaledResolution(mc)
                val posX = scaledResolution.scaledWidth / 2f - FontRenderAdapter.getStringWidth(warningText) / 2f
                val posY = scaledResolution.scaledHeight / 2f - 16f
                val color = ColorHolder(240, 87, 70)
                FontRenderAdapter.drawString(warningText, posX, posY, color = color)
            }
        }

        safeListener<BlockBreakEvent> {
            if (player.distanceTo(it.position) > range) return@safeListener
            val breaker = world.getEntityByID(it.breakerID) ?: return@safeListener
            if (it.progress in 0..9) {
                val render = player != breaker || espSelf
                breakingBlockList.putIfAbsent(it.breakerID, Triple(it.position, it.progress, Pair(false, render)))
                breakingBlockList.computeIfPresent(it.breakerID) { _, triple -> Triple(it.position, it.progress, triple.third) }
                if (warning && (player != breaker || warnSelf) && it.progress >= warningProgress && !breakingBlockList[it.breakerID]!!.third.first
                    && ((obsidianOnly && world.getBlockState(it.position).block == Blocks.OBSIDIAN) || !obsidianOnly)) {
                    if (soundWarn) mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    warningText = "${breaker.name} is breaking near you!"
                    if (chatWarn) sendChatMessage(warningText)
                    delay = 0
                    warn = true
                    breakingBlockList[it.breakerID] = Triple(it.position, it.progress, Pair(true, render))
                }
            } else {
                breakingBlockList.remove(it.breakerID)
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            breakingBlockList.values.removeIf { triple ->
                world.isAirBlock(triple.first)
            }
        }
    }
}
