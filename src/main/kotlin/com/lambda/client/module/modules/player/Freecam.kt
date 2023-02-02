package com.lambda.client.module.modules.player

import baritone.api.pathing.goals.GoalTwoBlocks
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.commons.extension.toRadian
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerAttackEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.resetJumpSneak
import com.lambda.client.util.MovementUtils.resetMove
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.runBlocking
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.network.NetHandlerPlayClient
import net.minecraft.entity.Entity
import net.minecraft.entity.MoverType
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.Packet
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.SPacketEntityHeadLook
import net.minecraft.util.MovementInput
import net.minecraft.util.MovementInputFromOptions
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Freecam : Module(
    name = "Freecam",
    description = "Leave your body and transcend into the realm of the gods",
    category = Category.PLAYER
) {
    private val directionMode by setting("Flight Mode", FlightMode.CREATIVE)
    private val horizontalSpeed by setting("Horizontal Speed", 20.0f, 1.0f..50.0f, 1f)
    private val verticalSpeed by setting("Vertical Speed", 20.0f, 1.0f..50.0f, 1f, { directionMode == FlightMode.CREATIVE })
    private val autoRotate by setting("Auto Rotate", true)
    private val cheese by setting("Cheese", false, description = "Make group pictures without headache")
    private val arrowKeyMove by setting("Arrow Key Move", true)
    private val disableOnDisconnect by setting("Disconnect Disable", true)
    private val leftClickCome by setting("Left Click Come", false)
    private val relative by setting("Relative", false)

    private enum class FlightMode(override val displayName: String) : DisplayEnum {
        CREATIVE("Creative"),
        THREE_DEE("3D")
    }

    private var prevThirdPersonViewSetting = -1
    private val clickTimer = TickTimer(TimeUnit.SECONDS)
    var cameraGuy: EntityPlayer? = null; private set

    private const val ENTITY_ID = -6969420

    @JvmStatic
    fun handleTurn(entity: Entity, yaw: Float, pitch: Float, ci: CallbackInfo): Boolean {
        if (isDisabled) return false
        val player = mc.player ?: return false
        val cameraGuy = cameraGuy ?: return false

        return if (entity == player) {
            cameraGuy.turn(yaw, pitch)
            ci.cancel()
            true
        } else {
            false
        }
    }

    @JvmStatic
    fun getRenderChunkOffset(playerPos: BlockPos) =
        runSafeR {
            BlockPos(
                (player.posX / 16).floorToInt() * 16,
                (player.posY / 16).floorToInt() * 16,
                (player.posZ / 16).floorToInt() * 16
            )
        } ?: playerPos

    @JvmStatic
    fun getRenderViewEntity(renderViewEntity: EntityPlayer): EntityPlayer {
        val player = mc.player
        return if (isEnabled && player != null) {
            player
        } else {
            renderViewEntity
        }
    }

    init {
        onEnable {
            mc.renderChunksMany = false
        }

        onDisable {
            mc.renderChunksMany = true
            resetCameraGuy()
            mc.player?.let {
                resetMovementInput(it.movementInput)
            }
        }

        listener<ConnectionEvent.Disconnect> {
            prevThirdPersonViewSetting = -1
            if (disableOnDisconnect) disable()
            else cameraGuy = null
        }

        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketUseEntity) return@safeListener
            // Don't interact with self
            if (it.packet.getEntityFromWorld(world) == player) it.cancel()
        }

        safeListener<PlayerAttackEvent> {
            if (it.entity == player) it.cancel()
        }

        safeListener<InputEvent.KeyInputEvent> {
            // Force it to stay in first person lol
            if (mc.gameSettings.keyBindTogglePerspective.isKeyDown) mc.gameSettings.thirdPersonView = 2
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener

            if (player.isDead || player.health <= 0.0f) {
                if (cameraGuy != null) resetCameraGuy()
                return@safeListener
            }

            if (cameraGuy == null && player.ticksExisted > 5) spawnCameraGuy()
        }

        safeListener<InputUpdateEvent>(9999) {
            if (it.movementInput !is MovementInputFromOptions || BaritoneUtils.isPathing) return@safeListener

            resetMovementInput(it.movementInput)

            if (BaritoneUtils.isActive) return@safeListener

            if (cheese) {
                cameraGuy?.let { camGuy ->
                    world.loadedEntityList.filterIsInstance<EntityPlayer>()
                        .filter { otherPlayer -> otherPlayer != camGuy }
                        .forEach { otherPlayer ->
                            val rotation = getRotationTo(otherPlayer.getPositionEyes(1.0f), camGuy.getPositionEyes(1.0f))

                            otherPlayer.rotationYaw = rotation.x
                            otherPlayer.rotationYawHead = rotation.x
                            otherPlayer.rotationPitch = rotation.y
                        }
                }
            } else if (autoRotate) updatePlayerRotation()
            if (arrowKeyMove) updatePlayerMovement()
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet is SPacketEntityHeadLook && cheese) it.cancel()
        }

        listener<InputEvent.MouseInputEvent> {
            if (leftClickCome && Mouse.getEventButton() == 0 && clickTimer.tick(1L)) {
                val result: BlockPos = mc.objectMouseOver.blockPos ?: return@listener

                if (mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                    val pos = result.offset(mc.objectMouseOver.sideHit)
                    BaritoneUtils.cancelEverything()
                    BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalTwoBlocks(pos))
                }
            }
        }
    }

    private fun resetMovementInput(movementInput: MovementInput?) {
        if (movementInput is MovementInputFromOptions) {
            movementInput.resetMove()
            movementInput.resetJumpSneak()
        }
    }

    private fun SafeClientEvent.spawnCameraGuy() {
        // Create a cloned player
        cameraGuy = FakeCamera(world, player).also {
            // Add it to the world
            world.addEntityToWorld(ENTITY_ID, it)

            // Set the render view entity to our camera guy
            mc.renderViewEntity = it

            // Reset player movement input
            resetMovementInput(player.movementInput)

            // Stores prev third person view setting
            prevThirdPersonViewSetting = mc.gameSettings.thirdPersonView
            mc.gameSettings.thirdPersonView = 0
        }
    }

    private fun SafeClientEvent.updatePlayerRotation() {
        mc.objectMouseOver?.let {
            val hitVec = it.hitVec
            if (it.typeOfHit == RayTraceResult.Type.MISS || hitVec == null) return
            val rotation = getRotationTo(hitVec)
            player.apply {
                rotationYaw = rotation.x
                rotationPitch = rotation.y
            }
        }
    }

    private fun SafeClientEvent.updatePlayerMovement() {
        cameraGuy?.let {
            val forward = Keyboard.isKeyDown(Keyboard.KEY_UP) to Keyboard.isKeyDown(Keyboard.KEY_DOWN)
            val strafe = Keyboard.isKeyDown(Keyboard.KEY_LEFT) to Keyboard.isKeyDown(Keyboard.KEY_RIGHT)
            val movementInput = calcMovementInput(forward, strafe, false to false)

            val yawDiff = player.rotationYaw - it.rotationYaw
            val yawRad = calcMoveYaw(yawDiff, movementInput.first, movementInput.second).toFloat()
            val inputTotal = min(abs(movementInput.first) + abs(movementInput.second), 1.0f)

            player.movementInput?.apply {
                moveForward = cos(yawRad) * inputTotal
                moveStrafe = sin(yawRad) * inputTotal

                forwardKeyDown = moveForward > 0.0f
                backKeyDown = moveForward < 0.0f
                leftKeyDown = moveStrafe < 0.0f
                rightKeyDown = moveStrafe > 0.0f

                jump = Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
            }
        }
    }

    private fun resetCameraGuy() {
        cameraGuy = null
        runBlocking {
            onMainThreadSafe {
                world.removeEntityFromWorld(ENTITY_ID)
                mc.renderViewEntity = player
                if (prevThirdPersonViewSetting != -1) mc.gameSettings.thirdPersonView = prevThirdPersonViewSetting
            }
        }
    }

    private class FakeCamera(world: WorldClient, val player: EntityPlayerSP) : EntityPlayerSP(mc, world, NoOpNetHandlerPlayerClient(player.connection), player.statFileWriter, player.recipeBook) {
        init {
            copyLocationAndAnglesFrom(player)
            capabilities.allowFlying = true
            capabilities.isFlying = true
        }

        override fun onLivingUpdate() {
            // Update inventory
            inventory.copyInventory(player.inventory)

            this.movementInput = MovementInput()
            // Update yaw head
            updateEntityActionState()

            // We have to update movement input from key binds because mc.player.movementInput is used by Baritone
            val forward = mc.gameSettings.keyBindForward.isKeyDown to mc.gameSettings.keyBindBack.isKeyDown
            val strafe = mc.gameSettings.keyBindLeft.isKeyDown to mc.gameSettings.keyBindRight.isKeyDown
            val vertical = mc.gameSettings.keyBindJump.isKeyDown to mc.gameSettings.keyBindSneak.isKeyDown
            val movementInput = calcMovementInput(forward, strafe, vertical)

            moveForward = movementInput.first
            moveStrafing = movementInput.second
            moveVertical = movementInput.third

            // Update sprinting
            isSprinting = mc.gameSettings.keyBindSprint.isKeyDown

            val absYaw = RotationUtils.getRotationFromVec(Vec3d(moveStrafing.toDouble(), 0.0, moveForward.toDouble())).x
            val yawRad = (rotationYaw - absYaw).toDouble().toRadian()
            val speed = (horizontalSpeed / 20.0f) * min(abs(moveForward) + abs(moveStrafing), 1.0f)

            if (directionMode == FlightMode.THREE_DEE) {
                val pitchRad = rotationPitch.toDouble().toRadian() * moveForward
                motionX = -sin(yawRad) * cos(pitchRad) * speed
                motionY = -sin(pitchRad) * speed
                motionZ = cos(yawRad) * cos(pitchRad) * speed
            } else {
                motionX = -sin(yawRad) * speed
                motionY = moveVertical.toDouble() * (verticalSpeed / 20.0f)
                motionZ = cos(yawRad) * speed
            }

            if (isSprinting) {
                motionX *= 1.5
                motionY *= 1.5
                motionZ *= 1.5
            }

            if (relative) {
                motionX += player.posX - player.prevPosX
                motionY += player.posY - player.prevPosY
                motionZ += player.posZ - player.prevPosZ
            }

            move(MoverType.SELF, motionX, motionY, motionZ)
        }

        override fun getEyeHeight() = 1.65f

        override fun isSpectator() = true

        override fun isInvisible() = true

        override fun isInvisibleToPlayer(player: EntityPlayer) = true
    }

    private class NoOpNetHandlerPlayerClient(realNetHandler: NetHandlerPlayClient) : NetHandlerPlayClient(mc, null, realNetHandler.networkManager, realNetHandler.gameProfile) {
        override fun sendPacket(packetIn: Packet<*>) {
            // no packets from freecam player, thanks
        }
    }

    /**
     * @param forward <Forward, Backward>
     * @param strafe <Left, Right>
     * @param vertical <Up, Down>
     *
     * @return <Forward, Strafe, Vertical>
     */
    private fun calcMovementInput(forward: Pair<Boolean, Boolean>, strafe: Pair<Boolean, Boolean>, vertical: Pair<Boolean, Boolean>): Triple<Float, Float, Float> {
        // Forward movement input
        val moveForward = if (forward.first xor forward.second) {
            if (forward.first) 1.0f else -1.0f
        } else {
            0.0f
        }

        // Strafe movement input
        val moveStrafing = if (strafe.first xor strafe.second) {
            if (strafe.second) 1.0f else -1.0f
        } else {
            0.0f
        }

        // Vertical movement input
        val moveVertical = if (vertical.first xor vertical.second) {
            if (vertical.first) 1.0f else -1.0f
        } else {
            0.0f
        }

        return Triple(moveForward, moveStrafing, moveVertical)
    }
}