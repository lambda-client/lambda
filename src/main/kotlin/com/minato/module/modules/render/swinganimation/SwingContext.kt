@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

import net.minecraft.entity.Entity
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d

/**
 * Context data cho một lần swing (vung tay).
 * Được tạo khi [SwingHandEvent] phát ra và truyền vào [ActiveSwingEffect].
 */
class SwingContext(
    /** Loại vũ khí đang cầm */
    val weaponType: WeaponType,
    /** Tay nào swing */
    val hand: Hand,
    /** Vị trí người chơi lúc swing (partialTick) */
    val playerPos: Vec3d,
    /** Hướng nhìn (yaw) lúc swing */
    val yaw: Float,
    /** Hướng nhìn (pitch) lúc swing */
    val pitch: Float,
    /** Fire/sử dụng remaining ticks (cho bow/crossbow) */
    val useRemainingTicks: Int,
    /** Target entity nếu đang nhắm */
    val targetEntity: Entity?,
    /** Hit result nếu có */
    val hitResult: HitResult?,
    /** Fall distance (cho Mace smash detection) */
    val fallDistance: Float,
    /** Server-confirmed crit flag (nếu có) */
    val isCritical: Boolean,
    /** Timestamp lúc swing (System.nanoTime() để tính progress) */
    val startTimeNanos: Long = System.nanoTime(),
    /** Combo stage cho SPEAR (0 = none, 1 = Thrust, 2 = Sweep, 3 = Spin Slash) */
    val comboStage: Int = 0,
    /** Fall Smash (Mace): fallDistance > 1.5 blocks */
    val isFallSmash: Boolean = false,
)
