@file:Suppress("unused")

package com.minato.module.hud

import com.minato.Minato.mc
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.modules.client.BlockEntityCuller
import com.minato.module.modules.client.EntityCuller
import com.minato.module.modules.client.FpsManager
import com.minato.module.modules.client.ParticleLimiter
import com.minato.module.modules.client.PerformanceOptimizer
import com.minato.module.tag.ModuleTag
import java.awt.Color

/**
 * PerformanceHud — HUD hiển thị real-time thông số tối ưu FPS.
 *
 * Hiển thị:
 * - FPS hiện tại / target (color-coded: xanh ≥ target, vàng ≥ 30, đỏ < 20)
 * - Entity cull distance + trạng thái bật/tắt
 * - Particle cap/frame + trạng thái bật/tắt
 * - Render distance hiện tại + adjusted
 *
 * Dữ liệu cập nhật mỗi tick từ [FpsManager], [EntityCuller], [ParticleLimiter].
 */
object PerformanceHud : HudModule(
    name = "Performance HUD",
    description = "Hiển thị real-time FPS, entity dist, particle cap, render distance",
    tag = ModuleTag.HUD,
) {
    // ── Color constants ──
    private val GREEN = Color(0x7E, 0xEC, 0x8B)
    private val YELLOW = Color(0xFF, 0xD5, 0x3B)
    private val RED = Color(0xFF, 0x55, 0x55)
    private val GRAY = Color(0xAA, 0xAA, 0xAA)
    private val WHITE = Color(0xFF, 0xFF, 0xFF)
    private val CYAN = Color(0x7E, 0xC8, 0xE3)

    // ── Cached values (update mỗi tick) ──
    private var cachedFps = 0
    private var cachedTargetFps = 60
    private var cachedEntityDist = 256.0
    private var cachedParticleCap = 0
    private var cachedViewDist = 12
    private var cachedAdjustedViewDist = 12
    private var perfOptEnabled = false
    private var entitySkipEnabled = false
    private var blockEntitySkipEnabled = false
    private var particleCapEnabled = false
    private var selectiveParticleEnabled = false

    init {
        listen<TickEvent.Player.Pre> {
            // Cache tất cả giá trị 1 lần/tick
            perfOptEnabled = PerformanceOptimizer.isEnabled
            entitySkipEnabled = EntityCuller.enabled
            blockEntitySkipEnabled = BlockEntityCuller.enabled
            particleCapEnabled = ParticleLimiter.enabled
            selectiveParticleEnabled = PerformanceOptimizer.selectiveParticleCulling
            cachedFps = mc.currentFps
            cachedTargetFps = FpsManager.targetFps
            cachedEntityDist = EntityCuller.getMaxRenderDistance()
            cachedParticleCap = ParticleLimiter.getMaxParticlesPerFrame()
            cachedViewDist = mc.options.viewDistance.value
            cachedAdjustedViewDist = FpsManager.getAdjustedRenderDistance(cachedViewDist)
        }
    }

    override fun ImGuiBuilder.buildLayout() {
        // ── Header: [PERFORMANCE] ──
        textColored("[PERFORMANCE]", CYAN)
        separator()

        // ── 1. FPS ──
        text("FPS: ")
        sameLine()
        val fpsColor = when {
            cachedFps >= cachedTargetFps -> GREEN
            cachedFps >= 30 -> YELLOW
            cachedFps > 0 -> RED
            else -> GRAY
        }
        textColored("$cachedFps", fpsColor)
        sameLine()
        textColored("/$cachedTargetFps", GRAY)

        // ── 2. Entity + BlockEntity cull ──
        newLine()
        text("Dist: ")
        sameLine()
        if (entitySkipEnabled && perfOptEnabled) {
            val distColor = when {
                cachedEntityDist >= 128 -> GREEN
                cachedEntityDist >= 32 -> YELLOW
                else -> RED
            }
            textColored("${cachedEntityDist.toInt()}", distColor)
        } else {
            textColored("OFF", GRAY)
        }
        sameLine()
        text("  BE: ")
        sameLine()
        if (blockEntitySkipEnabled && perfOptEnabled) {
            textColored("ON", GREEN)
        } else {
            textColored("OFF", GRAY)
        }

        // ── 3. Particle cap + selective ──
        newLine()
        text("Part: ")
        sameLine()
        if (particleCapEnabled && perfOptEnabled) {
            val partColor = when {
                cachedParticleCap >= 500 -> GREEN
                cachedParticleCap >= 200 -> YELLOW
                else -> RED
            }
            textColored("$cachedParticleCap/frame", partColor)
        } else {
            textColored("OFF", GRAY)
        }
        sameLine()
        text("  FW: ")
        sameLine()
        if (selectiveParticleEnabled && perfOptEnabled) {
            textColored("ON", YELLOW)
        } else {
            textColored("OFF", GRAY)
        }

        // ── 4. View distance ──
        newLine()
        text("View: ")
        sameLine()
        if (perfOptEnabled && cachedAdjustedViewDist < cachedViewDist) {
            // Module đã adjust — show both
            textColored("$cachedAdjustedViewDist", YELLOW)
            sameLine()
            textColored("($cachedViewDist)", GRAY)
        } else {
            textColored("$cachedViewDist", GREEN)
        }

        // ── 5. Active indicator ──
        sameLine()
        text("  ")
        sameLine()
        if (perfOptEnabled) {
            textColored("⚡", CYAN)
        }
    }
}
