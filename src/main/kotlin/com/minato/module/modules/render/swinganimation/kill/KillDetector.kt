@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import com.minato.Minato.mc
import com.minato.event.events.EntityEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * KillDetector — xác định khi nào player local hạ gục entity khác.
 *
 * ### Cơ chế
 * 1. Track `lastAttacker` của mỗi entity trong cửa sổ ~3 giây
 * 2. Khi `EntityEvent.Removal` với `KILLED` reason → kiểm tra lastAttacker
 * 3. Nếu lastAttacker == mc.player → emit kill event
 *
 * ### An toàn
 * - KHÔNG dự đoán/hiển thị trước khi server xác nhận death
 * - Chỉ trigger trên `RemovalReason.KILLED` (server-confirmed)
 * - Chỉ trigger cho PlayerEntity trừ khi `triggerOnMobs` bật
 */
object KillDetector {
    /** Track last attacker của mỗi entity (entityId → (attacker, timestamp)) */
    private val lastAttackers = mutableMapOf<Int, Pair<Int, Long>>()

    /** Time window (ms) để coi "last attacker" là hợp lệ */
    private const val ATTACK_WINDOW_MS = 3000L

    /** Callback: (victimPosition, victimEntity, killStreak, isMob) → Unit */
    var onKill: ((killPos: Vector3d, victim: Entity, streak: Int, isMob: Boolean) -> Unit)? = null

    /** Có trigger cho mob kill không */
    var triggerOnMobs: Boolean = false

    init {
        // Clean stale entries mỗi tick
        listen<TickEvent.Player.Pre> {
            val now = System.currentTimeMillis()
            lastAttackers.entries.removeIf { (_, value) ->
                now - value.second > ATTACK_WINDOW_MS
            }
        }

        // Track damage events
        listen<EntityEvent.Damage> { event ->
            if (event.amount <= 0f) return@listen
            val attacker = event.source.attacker
            val victim = event.entity
            if (attacker == null || victim == null) return@listen
            if (attacker !== mc.player) return@listen
            if (victim === mc.player) return@listen

            lastAttackers[victim.id] = attacker.id to System.currentTimeMillis()
        }

        // Detect kill on entity removal
        listen<EntityEvent.Removal> { event ->
            if (event.removalReason != Entity.RemovalReason.KILLED) return@listen
            val victim = event.entity

            // Check if player was last attacker
            val lastAttackerId = lastAttackers[victim.id]?.first ?: return@listen
            if (lastAttackerId != mc.player?.id) return@listen

            // Check player vs mob
            val isMob = victim !is PlayerEntity
            if (isMob && !triggerOnMobs) return@listen

            // Clean up
            lastAttackers.remove(victim.id)

            // Emit kill
            val killPos = victim.pos
            onKill?.invoke(
                Vector3d(killPos.x, killPos.y, killPos.z),
                victim,
                KillstreakTracker.registerKill(),
                isMob
            )
        }
    }

    /**
     * Tracks a damage event (called from mixin or event).
     * Public for manual tracking if needed.
     */
    fun trackDamage(attackerId: Int, victimId: Int) {
        lastAttackers[victimId] = attackerId to System.currentTimeMillis()
    }

    /** Reset tracking state */
    fun clear() {
        lastAttackers.clear()
    }
}

/**
 * Simple 3D vector for kill position.
 * Using custom class instead of Vec3d to avoid coupling to Minecraft's Vec3d
 * in the callback interface.
 */
data class Vector3d(val x: Double, val y: Double, val z: Double) {
    constructor(vec: net.minecraft.util.math.Vec3d) : this(vec.x, vec.y, vec.z)
}
