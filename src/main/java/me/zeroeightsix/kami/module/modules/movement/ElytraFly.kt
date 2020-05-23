package me.zeroeightsix.kami.module.modules.movement

import me.zeroeightsix.kami.module.Module

@Module.Info(
        name = "ElytraFly",
        description = "",
        category = Module.Category.MOVEMENT
)
class ElytraFly : Module() {
//    private val glideSpeed = 1.9 //by r("Glide Speed", 1.9).min(0.0).max(2.0)
//    private val speed = 30 //by r("Base Speed", 130).min(0).max(150).unit("km/h")
//    private val downSpeed = 2.0 //by r("Down Speed", 2.0).min(0.0).max(3.0)
//
//    enum class PitchSpoofMode { ROTATE, SNAP }
//    val pitchSpoofMode = PitchSpoofMode.SNAP//by r("Pitch Spoof Mode", PitchSpoofMode.SNAP)
//    val rotateSpeed = 0.5 //by r("Rotate Speed", 0.5).min(0.05).max(1.0)
//
//    private val useTimerSpeed = false //by r("Timer", false)
//    private val timerSpeed = 1.0 //by r("Timer Speed", 1.0).min(1.0).max(2.0)
//    private val ascendAngle = 45 //by r("Ascension Angle", 45.0).min(0.0).max(90.0)
//    private val takeoffHelper = true //by r("Takeoff Helper", true)
//
//    //private val takeoffPacketDelay by r("Takeoff Packet Delay", 5).min(1).max(20)
//    val takeoffPacketDelay = 5
//    private val takeoffTimer = true //by r("Takeoff Timer", true)
//    private val takeoffTimerSpeed = 0.2 //by r("Takeoff Timer Speed", 0.2).min(0.1).max(1.0)
//
//    val setFlying: Boolean get() = mc.player.isElytraFlying
//    var spoofedPitch = 0.0f
//    var targetPitch = 0.0f
//
//    var goingUp = false
//    var enabledTimer = false
//
//    var forward = false
//    var backward = false
//    var left = false
//    var right = false
//    var down = false
//    var up = false
//
//    @EventHandler
//    var onInput = Listener(EventHook { event: InputUpdateEvent ->
//        left = event.movementInput.leftKeyDown
//        right = event.movementInput.rightKeyDown
//        forward = event.movementInput.forwardKeyDown
//        backward = event.movementInput.backKeyDown
//        up = event.movementInput.jump
//        down = event.movementInput.sneak
//    })
//
//    override fun onUpdate() {
//        goingUp = mc.gameSettings.keyBindJump.isKeyDown
//
//        val newPitch = if (goingUp) -ascendAngle.toFloat() else 0.0f
//        if (newPitch == 0.0f) {
//            spoofedPitch = 0.0f
//            targetPitch = 0.0f
//        } else {
//            if (pitchSpoofMode == PitchSpoofMode.SNAP) {
//                spoofedPitch = targetPitch
//                targetPitch = newPitch
//            } else {
//                if (newPitch != targetPitch) {
//                    spoofedPitch = Random.nextFloat() * 5.0f + 5.0f
//                    targetPitch = newPitch
//                    mc.player.connection.sendPacket(CPacketPlayer.Rotation(mc.player.rotationYaw, spoofedPitch, false))
//                }
//            }
//        }
//
//        if (!mc.player.wearingElytra) return
//
//        if (!mc.player.isElytraFlying && !mc.player.onGround && takeoffHelper) {
//            if (ticksPassed(takeoffPacketDelay)) {
//                mc.connection?.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING))
//            }
//            //return
//        }
//
//        if (!mc.player.isElytraFlying) {
//            if (takeoffTimer && !mc.player.onGround) {
//                if (!timer.isEnabled) timer.enable()
//                timer.speed = takeoffTimerSpeed
//                enabledTimer = true
//            }
//
//            if (!takeoffTimer) {
//                if (enabledTimer) {
//                    timer.disable()
//                    enabledTimer = false
//                }
//            }
//            return
//        }
//
//        if (pitchSpoofMode == PitchSpoofMode.ROTATE && spoofedPitch != targetPitch) {
//            spoofedPitch += targetPitch * Random.nextFloat() * rotateSpeed.toFloat()
//            if (spoofedPitch <= targetPitch) {
//                spoofedPitch = targetPitch
//            }
//                //mc.player.connection.sendPacket(CPacketPlayer.Rotation(mc.player.rotationYaw, spoofedPitch, false))
//        }
//
//        if (goingUp) {
//            return
//        }
//
//        val yaw = Math.toRadians(mc.player.rotationYaw.toDouble()).toFloat()
//        val tickSpeed = speed / 3.6 / 20.0
//        mc.player.motionX = MathHelper.clamp(mc.player.motionX, -tickSpeed, tickSpeed)
//        mc.player.motionZ = MathHelper.clamp(mc.player.motionZ, -tickSpeed, tickSpeed)
//
//        if (forward && left) {
//            mc.player.motionX = -MathHelper.sin(yaw - Math.PI.toFloat() / 4.0f) * tickSpeed
//            mc.player.motionZ = MathHelper.cos(yaw - Math.PI.toFloat() / 4.0f) * tickSpeed
//        } else if (forward && right) {
//            mc.player.motionX = -MathHelper.sin(yaw + Math.PI.toFloat() / 4.0f) * tickSpeed
//            mc.player.motionZ = MathHelper.cos(yaw + Math.PI.toFloat() / 4.0f) * tickSpeed
//        } else if (backward && left) {
//            mc.player.motionX = MathHelper.sin(yaw + Math.PI.toFloat() / 4.0f) * tickSpeed
//            mc.player.motionZ = -MathHelper.cos(yaw + Math.PI.toFloat() / 4.0f) * tickSpeed
//        } else if (backward && right) {
//            mc.player.motionX = MathHelper.sin(yaw - Math.PI.toFloat() / 4.0f) * tickSpeed
//            mc.player.motionZ = -MathHelper.cos(yaw - Math.PI.toFloat() / 4.0f) * tickSpeed
//        } else if (forward) {
//            mc.player.motionX = -MathHelper.sin(yaw) * tickSpeed
//            mc.player.motionZ = MathHelper.cos(yaw) * tickSpeed
//        } else if (backward) {
//            mc.player.motionX = MathHelper.sin(yaw) * tickSpeed
//            mc.player.motionZ = -MathHelper.cos(yaw) * tickSpeed
//        } else if (left) {
//            mc.player.motionX = -MathHelper.sin(yaw - Math.PI.toFloat() / 2.0f) * tickSpeed
//            mc.player.motionZ = MathHelper.cos(yaw - Math.PI.toFloat() / 2.0f) * tickSpeed
//        } else if (right) {
//            mc.player.motionX = -MathHelper.sin(yaw + Math.PI.toFloat() / 2.0f) * tickSpeed
//            mc.player.motionZ = MathHelper.cos(yaw + Math.PI.toFloat() / 2.0f) * tickSpeed
//        } else {
//            mc.player.motionX = 0.0
//            mc.player.motionZ = 0.0
//        }
//
//        val glide = glideSpeed / 100.0
//
//        if (down) {
//            mc.player.motionY = -downSpeed
//        } else {
//            mc.player.motionY = glide
//        }
//    }
//
//    var originalPitch = 0.0f
//    var changedPitch = false
//
//    @EventListener(state = EventState.PRE)
//    fun onTravelPre(event: TravelEvent) {
//        if (!mc.player.isElytraFlying) return
//
//        changedPitch = false
//
//        mc.player.capabilities.allowFlying = true
//        mc.player.capabilities.isFlying = true
//
//        originalPitch = mc.player.rotationPitch
//
//        mc.player.rotationPitch = spoofedPitch
//        changedPitch = true
//    }
//
//    @EventListener(state = EventState.POST)
//    fun onTravelPost(event: TravelEvent) {
//        mc.player.capabilities.allowFlying = false
//        mc.player.capabilities.isFlying = false
//        if (changedPitch) {
//            mc.player.rotationPitch = originalPitch
//            changedPitch = false
//        }
//    }
//
//    @EventListener(state = EventState.PRE)
//    fun onMotionPre(event: MotionEvent) {
//        if (!mc.player.isElytraFlying) return
//
//        changedPitch = false
//        originalPitch = mc.player.rotationPitch
//        mc.player.rotationPitch = spoofedPitch
//        changedPitch = true
//    }
//
//    @EventListener(state = EventState.POST)
//    fun onMotionPost(event: MotionEvent) {
//        if (changedPitch) {
//            mc.player.rotationPitch = originalPitch
//            changedPitch = false
//        }
//    }
//
//    override val hudInfo: String
//        get() = speed.toString()
}