@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.trail

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.Arm
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin

/** Pre-computed PI constant */
private val PI = kotlin.math.PI.toFloat()

/**
 * Tính toán world-position của đầu vũ khí mỗi frame.
 *
 * ### Vấn đề: ViewModel interference
 * Khi ViewModel module enabled với custom swing mode, nó có thể bypass
 * `swingHand()` super call, khiến `player.handSwinging` không được set.
 * Để giải quyết, [WeaponTipTracker] tự theo dõi thời gian swing độc lập
 * bằng [System.nanoTime] — không phụ thuộc vào `handSwingTicks`.
 *
 * ### Fallback strategy
 * 1. **Primary**: Dùng elapsed time từ [swingStartNanoTime] (được set khi
 *    `SwingAnimationModule.SwingHand` listener chạy — ngay sau event fire).
 * 2. **Fallback**: Nếu không có swingStartNanoTime, dùng `handSwingTicks`
 *    của vanilla (vẫn hoạt động khi ViewModel tắt hoặc không intercept).
 *
 * TODO: Cần Mixin vào HeldItemRenderer.renderFirstPersonItem() để capture
 * matrix thật. Hiện tại dùng phương án xấp xỉ (fallback).
 */
object WeaponTipTracker {
    private val mc = MinecraftClient.getInstance()

    /** Thời điểm bắt đầu swing (nanoseconds) — set từ SwingAnimationModule */
    var swingStartNanoTime: Long = 0L
        private set

    /** Swing progress (0..1) */
    var swingProgress = 0f
        private set

    /** Hằng số thời gian swing mặc định (~6 ticks = 300ms @ 20 TPS) */
    private const val SWING_DURATION_NS: Long = 300_000_000L // 300ms

    /** Được gọi từ SwingAnimationModule khi SwingHand event fire */
    fun onSwingStart() {
        swingStartNanoTime = System.nanoTime()
    }

    /** Updated mỗi frame — gọi từ PreRenderWorld */
    fun update(partialTick: Float) {
        val player = mc.player ?: return

        // Primary: dùng elapsed time từ swingStartNanoTime
        val elapsed = System.nanoTime() - swingStartNanoTime
        if (elapsed in 1 until SWING_DURATION_NS * 2) {
            // Swing đang active, tính progress từ elapsed time
            // Dùng SWING_DURATION_NS + partialTick correction
            val rawProgress = elapsed.toFloat() / SWING_DURATION_NS
            swingProgress = rawProgress.coerceIn(0f, 1f)
            return
        }

        // Fallback: dùng vanilla handSwingTicks nếu elapsed không còn active
        swingProgress = if (player.handSwinging) {
            val progress = (player.handSwingTicks.toFloat() + partialTick) / player.handSwingDuration.toFloat()
            if (progress > 1f) 1f else progress
        } else 0f
    }

    /**
     * Tính vị trí đầu vũ khí trong world space.
     * Sử dụng hướng camera + offset swing để xấp xỉ.
     */
    fun getTipPosition(player: PlayerEntity, partialTick: Float): Vec3d {
        val pos = player.getCameraPosVec(partialTick)
        val lookVec = player.getRotationVec(partialTick)
        val arm = player.mainArm

        val isMace = player.mainHandStack.item == Items.MACE
        val reach = if (isMace) 3.5 else 3.0

        val armSide = if (arm == Arm.RIGHT) 1f else -1f
        val swingOffset = sin(swingProgress * PI * 2f) * 0.4f * armSide
        val verticalOffset = cos(swingProgress * PI) * 0.3f - 0.3f

        val forward = lookVec.normalize().multiply(reach)
        val right = lookVec.crossProduct(Vec3d(0.0, 1.0, 0.0)).normalize().multiply(swingOffset.toDouble())

        return pos.add(forward).add(right).add(0.0, verticalOffset.toDouble(), 0.0)
    }

    /**
     * Lấy 2 điểm tip cho Trident (2 chĩa).
     */
    fun getTridentTipPositions(player: PlayerEntity, partialTick: Float): List<Vec3d> {
        val base = getTipPosition(player, partialTick)
        val lookVec = player.getRotationVec(partialTick)
        val right = lookVec.crossProduct(Vec3d(0.0, 1.0, 0.0)).normalize().multiply(0.2)
        return listOf(base.add(right), base.subtract(right))
    }

    /** Reset state (gọi khi module disable) */
    fun reset() {
        swingStartNanoTime = 0L
        swingProgress = 0f
    }
}
