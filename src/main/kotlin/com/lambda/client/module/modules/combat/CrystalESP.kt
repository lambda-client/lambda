package com.lambda.client.module.modules.combat

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.Quad
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.combat.CrystalUtils.canPlaceCollide
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

object CrystalESP : Module(
    name = "CrystalESP",
    description = "Renders ESP for End Crystals",
    category = Category.COMBAT
) {
    private val page = setting("Page", Page.DAMAGE_ESP)

    private val damageESP by setting("Damage ESP", false, { page.value == Page.DAMAGE_ESP })
    private val minAlpha by setting("Min Alpha", 0, 0..255, 1, { page.value == Page.DAMAGE_ESP })
    private val maxAlpha by setting("Max Alpha", 63, 0..255, 1, { page.value == Page.DAMAGE_ESP })
    private val damageRange by setting("Damage ESP Range", 4.0f, 0.0f..8.0f, 0.5f, { page.value == Page.DAMAGE_ESP })

    private val crystalESP by setting("Crystal ESP", true, { page.value == Page.CRYSTAL_ESP })
    private val onlyOwn by setting("Only Own", false, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val filled by setting("Filled", true, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val outline by setting("Outline", true, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val tracer by setting("Tracer", true, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val showDamage by setting("Damage", true, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val showSelfDamage by setting("Self Damage", true, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val textScale by setting("Text Scale", 1.0f, 0.0f..4.0f, 0.25f, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val animationScale by setting("Animation Scale", 1.0f, 0.0f..2.0f, 0.1f, { page.value == Page.CRYSTAL_ESP && crystalESP })
    private val crystalRange by setting("Crystal ESP Range", 16.0f, 0.0f..16.0f, 0.5f, { page.value == Page.CRYSTAL_ESP })

    private val color by setting("Color", GuiColors.primary, false, { page.value == Page.CRYSTAL_ESP_COLOR && crystalESP })
    private val aFilled by setting("Filled Alpha", 47, 0..255, 1, { page.value == Page.CRYSTAL_ESP_COLOR && crystalESP && filled })
    private val aOutline by setting("Outline Alpha", 127, 0..255, 1, { page.value == Page.CRYSTAL_ESP_COLOR && crystalESP && outline })
    private val aTracer by setting("Tracer Alpha", 200, 0..255, 1, { page.value == Page.CRYSTAL_ESP_COLOR && crystalESP && tracer })
    private val thickness by setting("Thickness", 2.0f, 0.25f..4.0f, 0.25f, { page.value == Page.CRYSTAL_ESP_COLOR && crystalESP && (outline || tracer) })

    private enum class Page {
        DAMAGE_ESP, CRYSTAL_ESP, CRYSTAL_ESP_COLOR
    }

    private var placeMap = emptyMap<BlockPos, CombatManager.CrystalDamage>()
    private val renderCrystalMap = LinkedHashMap<BlockPos, Quad<Float, Float, Float, Float>>() // <Crystal, <Target Damage, Self Damage, Prev Progress, Progress>>
    private val pendingPlacing = LinkedHashMap<BlockPos, Long>()

    init {
        safeListener<PacketEvent.PostSend>(0) {
            if (it.packet !is CPacketPlayerTryUseItemOnBlock) return@safeListener

            if (checkHeldItem(it.packet) && canPlaceCollide(it.packet.pos)) {
                pendingPlacing[it.packet.pos] = System.currentTimeMillis()
            }
        }
    }

    private fun SafeClientEvent.checkHeldItem(packet: CPacketPlayerTryUseItemOnBlock) = packet.hand == EnumHand.MAIN_HAND
        && player.serverSideItem.item == Items.END_CRYSTAL
        || packet.hand == EnumHand.OFF_HAND
        && player.heldItemOffhand.item == Items.END_CRYSTAL

    init {
        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeListener
            updateDamageESP()
            updateCrystalESP()
        }
    }

    private fun updateDamageESP() {
        placeMap = if (damageESP) {
            CombatManager.placeMap.filter { it.value.distance <= damageRange }
        } else {
            emptyMap()
        }
    }

    private fun updateCrystalESP() {
        if (crystalESP) {
            val placeMap = CombatManager.placeMap
            val crystalMap = CombatManager.crystalMap
            val cacheMap = HashMap<BlockPos, Quad<Float, Float, Float, Float>>()

            // Removes after 1 second
            pendingPlacing.entries.removeIf { System.currentTimeMillis() - it.value > 1000L }

            if (!onlyOwn) {
                for ((crystal, calculation) in crystalMap) {
                    if (calculation.distance > crystalRange) continue
                    cacheMap[crystal.position.down()] = Quad(calculation.targetDamage, calculation.selfDamage, 0.0f, 0.0f)
                }
            }

            for (pos in pendingPlacing.keys) {
                val damage = placeMap[pos] ?: continue
                cacheMap[pos] = Quad(damage.targetDamage, damage.selfDamage, 0.0f, 0.0f)
            }

            val scale = 1.0f / animationScale
            for ((pos, quad1) in renderCrystalMap) {
                cacheMap.computeIfPresent(pos) { _, quad2 -> Quad(quad2.first, quad2.second, quad1.fourth, min(quad1.fourth + 0.4f * scale, 1.0f)) }
                if (quad1.fourth < 2.0f) cacheMap.computeIfAbsent(pos) { Quad(quad1.first, quad1.second, quad1.fourth, min(quad1.fourth + 0.2f * scale, 2.0f)) }
            }

            renderCrystalMap.clear()
            renderCrystalMap.putAll(cacheMap)
        } else {
            renderCrystalMap.clear()
        }
    }

    init {
        listener<RenderWorldEvent> {
            val renderer = ESPRenderer()

            /* Damage ESP */
            if (damageESP && placeMap.isNotEmpty()) {
                renderer.aFilled = 255

                for ((pos, calculation) in placeMap) {
                    val rgb = MathUtils.convertRange(calculation.targetDamage.toInt(), 0, 20, 127, 255)
                    val a = MathUtils.convertRange(calculation.targetDamage.toInt(), 0, 20, minAlpha, maxAlpha)
                    val rgba = ColorHolder(rgb, rgb, rgb, a)
                    renderer.add(pos, rgba)
                }

                renderer.render(true)
            }

            /* Crystal ESP */
            if (crystalESP && renderCrystalMap.isNotEmpty()) {
                renderer.aFilled = if (filled) aFilled else 0
                renderer.aOutline = if (outline) aOutline else 0
                renderer.aTracer = if (tracer) aTracer else 0
                renderer.thickness = thickness

                for ((pos, quad) in renderCrystalMap) {
                    val progress = getAnimationProgress(quad.third, quad.fourth)
                    val box = AxisAlignedBB(pos).shrink(0.5 - progress * 0.5)
                    color.a = (progress * 255.0f).toInt()
                    renderer.add(box, color)
                }

                renderer.render(true)
            }
        }

        listener<RenderOverlayEvent> {
            if (!showDamage && !showSelfDamage) return@listener
            GlStateUtils.rescaleActual()

            for ((pos, quad) in renderCrystalMap) {
                glPushMatrix()

                val screenPos = ProjectionUtils.toScreenPos(pos.toVec3dCenter())
                glTranslated(screenPos.x, screenPos.y, 0.0)
                glScalef(textScale * 2.0f, textScale * 2.0f, 1.0f)

                val damage = abs(MathUtils.round(quad.first, 1))
                val selfDamage = abs(MathUtils.round(quad.second, 1))
                val alpha = (getAnimationProgress(quad.third, quad.fourth) * 255f).toInt()
                val color = ColorHolder(255, 255, 255, alpha)

                if (showDamage) {
                    val text = "Target: $damage"
                    val halfWidth = FontRenderAdapter.getStringWidth(text) / -2.0f
                    FontRenderAdapter.drawString(text, halfWidth, 0f, color = color)
                }
                if (showSelfDamage) {
                    val text = "Self: $selfDamage"
                    val halfWidth = FontRenderAdapter.getStringWidth(text) / -2.0f
                    FontRenderAdapter.drawString(text, halfWidth, FontRenderAdapter.getFontHeight() + 2.0f, color = color)
                }

                glPopMatrix()
            }

            GlStateUtils.rescaleMc()
        }
    }

    private fun getAnimationProgress(prevProgress: Float, progress: Float): Float {
        val interpolated = prevProgress + (progress - prevProgress) * LambdaTessellator.pTicks()
        return sin(interpolated * 0.5 * PI).toFloat()
    }
}