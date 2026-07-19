package com.minato.module.modules.render

import com.minato.Minato.mc
import com.minato.config.Tab
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.event.events.ButtonEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.tan

/**
 * Sword blocking animation modes (inspired by PVPUtils).
 */
enum class SwordBlockMode(val displayName: String) {
    MODE_1_7("1.7 Classic"),
    MODE_PUSH("Push"),
    MODE_1_7_PLUS("1.7 Plus"),
    MODE_NEW("Modern");
}

@Suppress("unused")
object ViewModel : Module(
	name = "ViewModel",
	description = "Adjusts hand and held item rendering, sword blocking, and held item position",
	tag = ModuleTag.RENDER,
) {
	private const val GENERAL_TAB = "General"
	private const val SCALE_TAB = "Scale"
	private const val POSITION_TAB = "Position"
	private const val ROTATION_TAB = "Rotation"
	private const val FOV_TAB = "FOV"
	private const val HAND_TAB = "Hand"
	private const val SWORD_BLOCK_TAB = "Sword Block"
	private const val HELD_ITEM_TAB = "Held Item"

	@Tab(GENERAL_TAB) private val swingMode by setting("Swing Mode", SwingMode.Standard, "Changes which hands swing")
	@Tab(GENERAL_TAB)val swingDuration by setting("Swing Duration", 6, 0..20, 1, "Adjusts how fast the player swings", " ticks")
	@Tab(GENERAL_TAB)private val noSwingDelay by setting("No Swing Delay", false, "Removes the delay between swings")
	@Tab(GENERAL_TAB)val mainSwingProgress by setting("Main Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players main hand was this progress through the swing animation")
	@Tab(GENERAL_TAB)val offhandSwingProgress by setting("Offhand Swing Progress", 0.0f, 0.0f..1.0f, 0.025f, "Renders as if the players offhand was this progress through the swing animation")
	@Tab(GENERAL_TAB)val oldAnimations by setting("Old Animations", false, "Adjusts the animations to look like they did in 1.8")
	@Tab(GENERAL_TAB)val swapAnimation by setting("Swap Animation", true, "If disabled, removes the drop down animation when swapping item") { oldAnimations }

	@Tab(SCALE_TAB) private val splitScale by setting("Split Scale", false, "Splits left and right hand scale settings")
	@Tab(SCALE_TAB) private val xScale by setting("X Scale", 1.0f, 0.0f..2.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftXScale = to; rightXScale = to }
	@Tab(SCALE_TAB) private val yScale by setting("Y Scale", 1.0f, 0.0f..2.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftYScale = to; rightYScale = to }
	@Tab(SCALE_TAB) private val zScale by setting("Z Scale", 1.0f, 0.0f..2.0f, 0.025f) { !splitScale }.onValueChange { _, to -> leftZScale = to; rightZScale = to }
	@Tab(SCALE_TAB) private var leftXScale by setting("Left X Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }
	@Tab(SCALE_TAB) private var leftYScale by setting("Left Y Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }
	@Tab(SCALE_TAB) private var leftZScale by setting("Left Z Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }
	@Tab(SCALE_TAB) private var rightXScale by setting("Right X Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }
	@Tab(SCALE_TAB) private var rightYScale by setting("Right Y Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }
	@Tab(SCALE_TAB) private var rightZScale by setting("Right Z Scale", 1.0f, 0.0f..2.0f, 0.025f) { splitScale }

	@Tab(POSITION_TAB) private val splitPosition by setting("Split Position", false, "Splits left and right position settings")
	@Tab(POSITION_TAB) private val xPosition by setting("X Position", 0.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftXPosition = to; rightXPosition = to }
	@Tab(POSITION_TAB) private val yPosition by setting("Y Position", 0.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftYPosition = to; rightYPosition = to }
	@Tab(POSITION_TAB) private val zPosition by setting("Z Position", 0.0f, -1.0f..1.0f, 0.025f) { !splitPosition }.onValueChange { _, to -> leftZPosition = to; rightZPosition = to }
	@Tab(POSITION_TAB) private var leftXPosition by setting("Left X Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }
	@Tab(POSITION_TAB) private var leftYPosition by setting("Left Y Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }
	@Tab(POSITION_TAB) private var leftZPosition by setting("Left Z Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }
	@Tab(POSITION_TAB) private var rightXPosition by setting("Right X Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }
	@Tab(POSITION_TAB) private var rightYPosition by setting("Right Y Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }
	@Tab(POSITION_TAB) private var rightZPosition by setting("Right Z Position", 0.0f, -1.0f..1.0f, 0.025f) { splitPosition }

	@Tab(ROTATION_TAB) private val splitRotation by setting("Split Rotation", false, "Splits left and right rotation settings")
	@Tab(ROTATION_TAB) private val xRotation by setting("X Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftXRotation = to; rightXRotation = to }
	@Tab(ROTATION_TAB) private val yRotation by setting("Y Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftYRotation = to; rightYRotation = to }
	@Tab(ROTATION_TAB) private val zRotation by setting("Z Rotation", 0, -180..180, 1) { !splitRotation }.onValueChange { _, to -> leftZRotation = to; rightZRotation = to }
	@Tab(ROTATION_TAB) private var leftXRotation by setting("Left X Rotation", 0, -180..180, 1) { splitRotation }
	@Tab(ROTATION_TAB) private var leftYRotation by setting("Left Y Rotation", 0, -180..180, 1) { splitRotation }
	@Tab(ROTATION_TAB) private var leftZRotation by setting("Left Z Rotation", 0, -180..180, 1) { splitRotation }
	@Tab(ROTATION_TAB) private var rightXRotation by setting("Right X Rotation", 0, -180..180, 1) { splitRotation }
	@Tab(ROTATION_TAB) private var rightYRotation by setting("Right Y Rotation", 0, -180..180, 1) { splitRotation }
	@Tab(ROTATION_TAB) private var rightZRotation by setting("Right Z Rotation", 0, -180..180, 1) { splitRotation }

	@Tab(FOV_TAB) private val splitFov by setting("Split FOV", false, "Splits left and right Fov settings")
	@Tab(FOV_TAB) private val fov by setting("FOV", 70, 10..180, 1) { !splitFov }.onValueChange { _, to -> leftFov = to; rightFov = to }
	@Tab(FOV_TAB) private val fovAnchorDistance by setting("Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the FOV transformation from") { !splitFov }.onValueChange { _, to -> leftFovAnchorDistance = to; rightFovAnchorDistance = to }
	@Tab(FOV_TAB) private var leftFov by setting("Left FOV", 70, 10..180, 1) { splitFov }
	@Tab(FOV_TAB) private var leftFovAnchorDistance by setting("Left Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the left FOV transformation from") { splitFov }
	@Tab(FOV_TAB) private var rightFov by setting("Right FOV", 70, 10..180, 1) { splitFov }
	@Tab(FOV_TAB) private var rightFovAnchorDistance by setting("Right Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the right FOV transformation from") { splitFov }

	@Tab(HAND_TAB) private val enableHand by setting("Hand", false, "Enables settings for the players hand")
	@Tab(HAND_TAB) private val handXScale by setting("Hand X Scale", 1.0f, 0.0f..2.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handYScale by setting("Hand Y Scale", 1.0f, 0.0f..2.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handZScale by setting("Hand Z Scale", 1.0f, 0.0f..2.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handXPosition by setting("Hand X Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handYPosition by setting("Hand Y Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handZPosition by setting("Hand Z Position", 0.0f, -1.0f..1.0f, 0.025f) { enableHand }
	@Tab(HAND_TAB) private val handXRotation by setting("Hand X Rotation", 0, -180..180, 1) { enableHand }
	@Tab(HAND_TAB) private val handYRotation by setting("Hand Y Rotation", 0, -180..180, 1) { enableHand }
	@Tab(HAND_TAB) private val handZRotation by setting("Hand Z Rotation", 0, -180..180, 1) { enableHand }
	@Tab(HAND_TAB) private val handFov by setting("Hand FOV", 70, 10..180, 1) { enableHand }
	@Tab(HAND_TAB) private val handFovAnchorDistance by setting("Hand FOV Anchor Distance", 0.5f, 0.0f..1.0f, 0.01f, "The distance to anchor the hands FOV transformation from") { enableHand }

	// ── Sword Blocking Animation ──
	@Tab(SWORD_BLOCK_TAB) val swordBlocking by setting("Sword Blocking", false, "Enable sword blocking animation when right-clicking with a sword")
	@Tab(SWORD_BLOCK_TAB) val swordBlockMode by setting("Blocking Mode", SwordBlockMode.MODE_1_7, "Animation style when blocking with sword")
	@Tab(SWORD_BLOCK_TAB) val autoBlock by setting("Auto Block", false, "Automatically block when an entity is in range")
	@Tab(SWORD_BLOCK_TAB) val autoBlockRange by setting("Auto Block Range", 3.0, 1.0..6.0, 0.5, "Range to auto-detect entities")
	@Tab(SWORD_BLOCK_TAB) val swordBlockAnimSpeed by setting("Block Animation Speed", 1.0f, 0.1f..3.0f, 0.1f, "Speed multiplier for block animation")
	@Tab(SWORD_BLOCK_TAB) val blockingOffsetX by setting("Offset X", 0.0f, -1.0f..1.0f, 0.01f, "X offset while blocking")
	@Tab(SWORD_BLOCK_TAB) val blockingOffsetY by setting("Offset Y", 0.0f, -1.0f..1.0f, 0.01f, "Y offset while blocking")
	@Tab(SWORD_BLOCK_TAB) val blockingOffsetZ by setting("Offset Z", 0.0f, -1.0f..1.0f, 0.01f, "Z offset while blocking")

	// ── Held Item Position (transparency + swing speed) ──
	@Tab(HELD_ITEM_TAB) val heldItemPosition by setting("Held Item Position", false, "Enable held item position/alpha/swing speed customization")
	@Tab(HELD_ITEM_TAB) val heldItemMainAlpha by setting("Main Hand Alpha", 100, 0..100, 5, "Transparency of main hand item (0=invisible, 100=full)")
	@Tab(HELD_ITEM_TAB) val heldItemOffAlpha by setting("Off Hand Alpha", 100, 0..100, 5, "Transparency of off hand item (0=invisible, 100=full)")
	@Tab(HELD_ITEM_TAB) val heldItemMainSwingSpeed by setting("Main Swing Speed", 1.0f, 0.1f..5.0f, 0.1f, "Swing speed multiplier for main hand")
	@Tab(HELD_ITEM_TAB) val heldItemOffSwingSpeed by setting("Off Swing Speed", 1.0f, 0.1f..5.0f, 0.1f, "Swing speed multiplier for off hand")

	private var attackKeyTicksPressed = -1

	init {
		listen<ButtonEvent.Mouse.Click> { event ->
			if (event.button == mc.options.attackKey.boundKey.code)
				attackKeyTicksPressed = if (event.action == 0) -1 else 0
		}

		listen<ButtonEvent.Keyboard.Press> { event ->
			if (event.keyCode == mc.options.attackKey.boundKey.code) {
				if (event.isPressed) attackKeyTicksPressed = 0
				else if (event.isReleased) attackKeyTicksPressed = -1
			}
		}

		listen<TickEvent.Pre> {
			if (attackKeyTicksPressed != -1) attackKeyTicksPressed++
		}
	}

	fun transform(itemStack: ItemStack, hand: Hand, matrices: MatrixStack) {
		val side = if (mc.options.mainArm.value == Arm.LEFT) {
			if (hand == Hand.MAIN_HAND) Side.Left else Side.Right
		} else {
			if (hand == Hand.MAIN_HAND) Side.Right else Side.Left
		}

		val emptyHand = itemStack.isEmpty
		if (!enableHand && emptyHand) return

		applyItemFov(matrices, side, emptyHand)
		scale(side, matrices, emptyHand)
		position(side, matrices, emptyHand)
		rotate(side, matrices, emptyHand)
	}

	private fun applyItemFov(matrices: MatrixStack, side: Side, emptyHand: Boolean) {
		val fov = when {
			side == Side.Left -> leftFov
			emptyHand -> handFov
			else -> rightFov
		}.toFloat()

		if (fov == 70f) return

		val fovRatio = tan(Math.toRadians(fov.toDouble() / 2)).toFloat() / tan(Math.toRadians(70.0 / 2)).toFloat()

		val matrix = matrices.peek().positionMatrix

		val distance = if (emptyHand) handFovAnchorDistance
		else when (side) {
			Side.Left -> leftFovAnchorDistance
			Side.Right -> rightFovAnchorDistance
		}

		val warpMatrix = Matrix4f()
			.translate(0f, 0f, -distance)
			.scale(1f, 1f, fovRatio)
			.translate(0f, 0f, distance)

		matrix.mul(warpMatrix)
	}

	private fun scale(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
		val scaleVec = getScaleVec(side, emptyHand)
		matrices.scale(scaleVec.x, scaleVec.y, scaleVec.z)
	}

	private fun getScaleVec(side: Side, emptyHand: Boolean): Vector3f =
		if (emptyHand) Vector3f(handXScale, handYScale, handZScale)
		else when (side) {
			Side.Left -> Vector3f(leftXScale, leftYScale, leftZScale)
			Side.Right -> Vector3f(rightXScale, rightYScale, rightZScale)
		}

	private fun position(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
		val positionVec = getPositionVec(side, emptyHand)
		matrices.translate(positionVec.x, positionVec.y, positionVec.z)
	}

	private fun getPositionVec(side: Side, emptyHand: Boolean) =
		when (side) {
			Side.Left ->
				if (emptyHand) Vector3f(-handXPosition, handYPosition, handZPosition)
				else Vector3f(-leftXPosition, leftYPosition, leftZPosition)
			Side.Right ->
				if (emptyHand) Vector3f(handXPosition, handYPosition, handZPosition)
				else Vector3f(rightXPosition, rightYPosition, rightZPosition)
		}

	private fun rotate(side: Side, matrices: MatrixStack, emptyHand: Boolean) {
		val rotationVec = getRotationVec(side, emptyHand)
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotationVec.x.toFloat()))
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationVec.y.toFloat()))
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationVec.z.toFloat()))
	}

	private fun getRotationVec(side: Side, emptyHand: Boolean) =
		when (side) {
			Side.Left ->
				if (emptyHand) Vector3i(handXRotation, -handYRotation, -handZRotation)
				else Vector3i(leftXRotation, -leftYRotation, -leftZRotation)
			Side.Right ->
				if (emptyHand) Vector3i(handXRotation, handYRotation, handZRotation)
				else Vector3i(rightXRotation, rightYRotation, rightZRotation)
		}

	fun adjustSwing(hand: Hand, player: AbstractClientPlayerEntity) =
		when (swingMode) {
			SwingMode.Standard -> swingHand(hand, player)
			SwingMode.Opposites ->
				if (hand == Hand.MAIN_HAND) swingHand(Hand.OFF_HAND, player)
				else swingHand(Hand.MAIN_HAND, player)
			SwingMode.MainHand -> swingHand(Hand.MAIN_HAND, player)
			SwingMode.OffHand -> swingHand(Hand.OFF_HAND, player)
			SwingMode.None -> {}
		}

	private fun swingHand(hand: Hand, player: AbstractClientPlayerEntity) =
		with(player) {
			if (
				(!handSwinging || handSwingTicks >= handSwingDuration / 2) ||
				handSwingTicks < 0 ||
				(noSwingDelay && attackKeyTicksPressed <= 1))
			{
				handSwingTicks = -1
				handSwinging = true
				preferredHand = hand
			}
		}

	// ── Sword Blocking Helpers (called from HeldItemRendererMixin) ──

	/**
	 * Check if player is currently blocking with sword.
	 */
	fun isBlocking(): Boolean {
		if (!swordBlocking) return false
		val player = mc.player ?: return false
		val stack = player.mainHandStack
		if (stack.isEmpty || !stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) return false
		return mc.options.useKey.isPressed || (autoBlock && isEntityInRange())
	}

	/**
	 * Check if there's a living entity in auto-block range.
	 */
	fun isEntityInRange(): Boolean {
		val player = mc.player ?: return false
		val world = mc.world ?: return false
		val range = autoBlockRange
		val rangeF = range.toFloat()
		return world.getEntitiesByClass(
			net.minecraft.entity.LivingEntity::class.java,
			player.boundingBox.expand(range),
		) { it != player && it.isAlive && it.distanceTo(player) <= rangeF }.isNotEmpty()
	}

	/**
	 * Apply sword blocking animation transformations.
	 * Called from HeldItemRendererMixin when blocking is active.
	 */
	fun applyBlockingTransform(matrices: MatrixStack, hand: Hand, swingProgress: Float, side: Int) {
		val mode = swordBlockMode
		val animSpeed = swordBlockAnimSpeed
		val h = (swingProgress * animSpeed).coerceIn(0f, 1f)

		// Apply user-configured blocking offset (translate takes float)
		matrices.translate(blockingOffsetX * side, blockingOffsetY, blockingOffsetZ)

		// sqrt takes float, sin takes double in 1.21.x
		when (mode) {
			SwordBlockMode.MODE_1_7 -> {
				val sqrtH = net.minecraft.util.math.MathHelper.sqrt(h) // Float
				val factor = net.minecraft.util.math.MathHelper.sin(sqrtH.toDouble() * Math.PI) // Double
				val blend = 1.0f - factor.toFloat() // Float
				matrices.translate(side * (-0.139F * blend), 0.06F * blend, 0.20F * blend)
				matrices.translate(side * 0.430F, -0.190F, 0.520F)
				matrices.translate(side * -0.141F, 0.08F, -0.72F)
				val f17 = net.minecraft.util.math.MathHelper.sin((h * h).toDouble() * Math.PI) // Double
				val f22 = net.minecraft.util.math.MathHelper.sin(sqrtH.toDouble() * Math.PI) // Double
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + f17.toFloat() * -20.0F)))
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * f22.toFloat() * -20.0F))
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f22.toFloat() * -80.0F))
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F))
				renderOldSwordStance(matrices, side)
			}
			SwordBlockMode.MODE_PUSH -> {
				matrices.translate(side * 0.15F, -0.05F, 0.0F)
				renderOldSwordStance(matrices, side)
				val sqrtSwing = net.minecraft.util.math.MathHelper.sqrt(h) // Float
				val swingAmount = net.minecraft.util.math.MathHelper.sin(sqrtSwing.toDouble() * Math.PI) // Double
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * swingAmount.toFloat() * 35.0F))
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingAmount.toFloat() * -10.0F))
			}
			SwordBlockMode.MODE_1_7_PLUS -> {
				matrices.translate(side * 0.15F, -0.05F, 0.0F)
				renderOldSwordStance(matrices, side)
				val sqrtSwing = net.minecraft.util.math.MathHelper.sqrt(h) // Float
				val swingAmount = net.minecraft.util.math.MathHelper.sin(sqrtSwing.toDouble() * Math.PI) // Double
				matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingAmount.toFloat() * -45.0F))
				matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * swingAmount.toFloat() * 20.0F))
			}
			SwordBlockMode.MODE_NEW -> {
				matrices.translate(side * 0.15F, -0.05F, 0.0F)
				renderOldSwordStance(matrices, side)
				if (h > 0.0F) {
					val sqrtH = net.minecraft.util.math.MathHelper.sqrt(h) // Float
					val swingFactor = net.minecraft.util.math.MathHelper.sin(sqrtH.toDouble() * Math.PI) // Double
					matrices.translate(side * 0.430F * swingFactor.toFloat(), -0.190F * swingFactor.toFloat(), 0.520F * swingFactor.toFloat())
					matrices.translate(side * -0.141F * swingFactor.toFloat(), 0.08F * swingFactor.toFloat(), -0.72F * swingFactor.toFloat())
					val f17 = net.minecraft.util.math.MathHelper.sin((h * h).toDouble() * Math.PI) // Double
					val f22 = net.minecraft.util.math.MathHelper.sin(sqrtH.toDouble() * Math.PI) // Double
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45.0F + f17.toFloat() * -20.0F)))
					matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * f22.toFloat() * -20.0F))
					matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f22.toFloat() * -80.0F))
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45.0F))
				}
			}
		}
	}

	private fun renderOldSwordStance(matrices: MatrixStack, side: Int) {
		matrices.translate(-0.2F, 0.126F, 0.2F)
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F))
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 15.0F))
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 80.0F))
	}

	/**
	 * Get alpha value for held item (0-255).
	 * Called from HeldItemRendererMixin.
	 */
	fun itemAlpha(hand: Hand): Int {
		if (!heldItemPosition) return 255
		val percent = if (hand == Hand.MAIN_HAND) heldItemMainAlpha else heldItemOffAlpha
		return (percent.coerceIn(0, 100) * 2.55f).toInt().coerceIn(0, 255)
	}

	/**
	 * Get swing speed multiplier for held item.
	 * Called from HeldItemRendererMixin.
	 */
	fun swingSpeed(hand: Hand): Float {
		if (!heldItemPosition) return 1.0f
		return if (hand == Hand.MAIN_HAND) heldItemMainSwingSpeed else heldItemOffSwingSpeed
	}

	/**
	 * Apply swing speed modifier to swing progress.
	 * Called from HeldItemRendererMixin.
	 */
	fun applySwingSpeed(hand: Hand, swingProgress: Float): Float {
		val speed = swingSpeed(hand)
		if (speed <= 0f) return 0f
		return (swingProgress * speed).coerceIn(0f, 1f)
	}

	private enum class Side {
		Left, Right
	}

	private enum class SwingMode {
		Standard, Opposites, MainHand, OffHand, None
	}
}
