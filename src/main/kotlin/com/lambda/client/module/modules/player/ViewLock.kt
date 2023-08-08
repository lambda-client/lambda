package com.lambda.client.module.modules.player

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign


object ViewLock : Module(
    name = "ViewLock",
    description = "Locks your camera view",
    category = Category.PLAYER,
    alias = arrayOf("YawLock", "PitchLock")
) {
    val mode = setting("Mode", Mode.TRADITIONAL)
    private val page by setting("Page", Page.YAW, { mode.value == Mode.TRADITIONAL })

    val yaw = setting("Yaw", true, { page == Page.YAW && mode.value == Mode.TRADITIONAL })
    val autoYaw = setting("Auto Yaw", true, { page == Page.YAW && yaw.value && mode.value == Mode.TRADITIONAL })
    val hardAutoYaw = setting("Hard Auto Yaw", true, { page == Page.YAW && yaw.value && autoYaw.value && mode.value == Mode.TRADITIONAL },
        description = "Disables mouse movement snapping")
    val disableMouseYaw = setting("Disable Mouse Yaw", true, { page == Page.YAW && yaw.value && mode.value == Mode.TRADITIONAL })
    private val specificYaw by setting("Specific Yaw", 180.0f, -180.0f..180.0f, 1.0f, { page == Page.YAW && !autoYaw.value && yaw.value && mode.value == Mode.TRADITIONAL })
    val yawSlice = setting("Yaw Slice", 8, 2..32, 1, { page == Page.YAW && autoYaw.value && yaw.value && mode.value == Mode.TRADITIONAL })

    val pitch = setting("Pitch", true, { page == Page.PITCH && mode.value == Mode.TRADITIONAL })
    private val autoPitch = setting("Auto Pitch", true, { page == Page.PITCH && pitch.value && mode.value == Mode.TRADITIONAL })
    private val hardAutoPitch by setting("Hard Auto Pitch", true, { page == Page.PITCH && pitch.value && autoPitch.value && mode.value == Mode.TRADITIONAL },
        description = "Disables mouse movement snapping")
    private val disableMousePitch by setting("Disable Mouse Pitch", true, { page == Page.PITCH && pitch.value && mode.value == Mode.TRADITIONAL })
    private val specificPitch by setting("Specific Pitch", 0.0f, -90.0f..90.0f, 1.0f, { page == Page.PITCH && !autoPitch.value && pitch.value && mode.value == Mode.TRADITIONAL })
    private val pitchSlice = setting("Pitch Slice", 5, 2..32, 1, { page == Page.PITCH && autoPitch.value && pitch.value && mode.value == Mode.TRADITIONAL })

    private val xCoord by setting("X coordinate", "", { mode.value == Mode.COORDS })
    private val yCoord by setting("Y coordinate", "", { mode.value == Mode.COORDS })
    private val zCoord by setting("Z coordinate", "", { mode.value == Mode.COORDS })

    private enum class Page {
        YAW, PITCH
    }

    enum class Mode {
        TRADITIONAL, COORDS
    }

    private var yawSnap = 0
    private var pitchSnap = 0
    private val deltaXQueue = ArrayDeque<Pair<Float, Long>>()
    private val deltaYQueue = ArrayDeque<Pair<Float, Long>>()
    private var pitchSliceAngle = 1.0f
    private var yawSliceAngle = 1.0f

    init {
        onEnable {
            yawSliceAngle = 360.0f / yawSlice.value
            pitchSliceAngle = 180.0f / (pitchSlice.value - 1)
            if (autoYaw.value || autoPitch.value) snapToNext()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            if (mode.value == Mode.COORDS) {
                val x = xCoord.toDoubleOrNull()
                val y = yCoord.toDoubleOrNull()
                val z = zCoord.toDoubleOrNull()
                if (x == null || y == null || z == null) {
                    MessageSendHelper.sendErrorMessage("Invalid coordinates")
                    disable()
                    return@safeListener
                }

                val rotation = getRotationTo(Vec3d(x, y, z))
                player.rotationYaw = rotation.x
                player.rotationPitch = rotation.y
                return@safeListener
            }

            if (autoYaw.value || autoPitch.value) {
                snapToSlice()
            }

            if (yaw.value && !autoYaw.value) {
                player.rotationYaw = specificYaw
            }

            if (pitch.value && !autoPitch.value) {
                player.rotationPitch = specificPitch
            }
        }
    }

    @JvmStatic
    fun handleTurn(entity: Entity, deltaX: Float, deltaY: Float, ci: CallbackInfo) {
        if (isDisabled || mode.value == Mode.COORDS) return
        val player = mc.player ?: return
        if (entity != player) return

        val multipliedX = deltaX * 0.15f
        val multipliedY = deltaY * -0.15f

        val yawChange = if (yaw.value && autoYaw.value && !hardAutoYaw.value) handleDelta(multipliedX, deltaXQueue, yawSliceAngle) else 0
        val pitchChange = if (pitch.value && autoPitch.value && !hardAutoPitch) handleDelta(multipliedY, deltaYQueue, pitchSliceAngle) else 0

        turn(player, multipliedX, multipliedY)
        changeDirection(yawChange, pitchChange)
        player.ridingEntity?.applyOrientationToEntity(player)

        ci.cancel()
    }

    private fun turn(player: EntityPlayerSP, deltaX: Float, deltaY: Float) {
        if (!yaw.value || !disableMouseYaw.value) {
            player.prevRotationYaw += deltaX
            player.rotationYaw += deltaX
        }

        if (!pitch.value || !disableMousePitch) {
            player.prevRotationPitch += deltaY
            player.rotationPitch += deltaY
            player.rotationPitch = player.rotationPitch.coerceIn(-90.0f, 90.0f)
        }
    }

    private fun handleDelta(delta: Float, list: ArrayDeque<Pair<Float, Long>>, slice: Float): Int {
        val currentTime = System.currentTimeMillis()
        list.add(Pair(delta, currentTime))

        val sum = list.sumOf { it.first.toDouble() }.toFloat()
        return if (abs(sum) > slice) {
            list.clear()
            sign(sum).toInt()
        } else {
            while (list.first().second < currentTime - 500) {
                list.removeFirstOrNull()
            }
            0
        }
    }

    private fun changeDirection(yawChange: Int, pitchChange: Int) {
        yawSnap = Math.floorMod(yawSnap + yawChange, yawSlice.value)
        pitchSnap = (pitchSnap + pitchChange).coerceIn(0, pitchSlice.value - 1)
        snapToSlice()
    }

    private fun snapToNext() {
        mc.player?.let {
            yawSnap = (it.rotationYaw / yawSliceAngle).roundToInt()
            pitchSnap = ((it.rotationPitch + 90.0f) / pitchSliceAngle).roundToInt()
            snapToSlice()
        }
    }

    private fun snapToSlice() {
        mc.player?.let { player ->
            if (yaw.value && autoYaw.value) {
                player.rotationYaw = (yawSnap * yawSliceAngle).coerceIn(0f, 360f)
                player.ridingEntity?.let { it.rotationYaw = player.rotationYaw }
            }
            if (pitch.value && autoPitch.value) {
                player.rotationPitch = (pitchSnap * pitchSliceAngle - 90).coerceIn(-90f, 90f)
            }
        }
    }

    init {
        yawSlice.listeners.add {
            yawSliceAngle = 360.0f / yawSlice.value
            if (isEnabled && autoYaw.value) snapToNext()
        }

        pitchSlice.listeners.add {
            pitchSliceAngle = 180.0f / (pitchSlice.value - 1)
            if (isEnabled && autoPitch.value) snapToNext()
        }

        with({ _: Boolean, it: Boolean -> if (isEnabled && it) snapToNext() }) {
            autoPitch.valueListeners.add(this)
            autoYaw.valueListeners.add(this)
        }
    }
}