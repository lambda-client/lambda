package com.lambda.module.modules.player

import com.google.gson.annotations.SerializedName
import com.lambda.config.RotationSettings
import com.lambda.event.events.KeyPressEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.modules.player.Replay.MoveInputAction.Companion.toAction
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import com.lambda.util.primitives.extension.rotation
import net.minecraft.client.input.Input
import net.minecraft.datafixer.fix.BlockEntitySignTextStrictJsonFix.GSON
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// ToDo: Needs more dense data storage (Not using JSON)
//  - Use a custom binary format to store the data
//  - Actually store the data in a file
//  - Implement a way to save and load the data (Commands?)
//  - Pause and resume the replay and recording
//  - Play n time - Loop mode
//  - Record other types of inputs: (Interactions, etc.)
object Replay : Module(
    name = "Replay",
    description = "Replays the last few seconds of gameplay",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val record by setting("Record", KeyCode.R)
    private val play by setting("Play", KeyCode.P)
    private val pause by setting("Pause", KeyCode.V)
    private val stop by setting("Stop", KeyCode.S)

    private val rotationConfig = RotationSettings(this).apply {
        rotationMode = RotationMode.LOCK
    }

    private var mode = ReplayMode.INACTIVE

    private val actions = mutableListOf<MoveInputAction>()
    private val rotations = mutableListOf<Rotation>()
    private val sprints = mutableListOf<Boolean>()
    private var actionSnapshot = mutableListOf<MoveInputAction>()
    private var rotationSnapshot = mutableListOf<Rotation>()
    private var sprintSnapshot = mutableListOf<Boolean>()

    private val duration: Duration
        get() = (actions.size * 50L).toDuration(DurationUnit.MILLISECONDS)

    enum class ReplayMode {
        INACTIVE,
        REPLAY,
        RECORD
    }

    init {
        listener<KeyPressEvent> {
            if (mc.currentScreen != null && !mc.options.commandKey.isPressed) return@listener

            when (it.key) {
                record.key -> handleRecord()
                play.key -> handlePlay()
            }
        }

        listener<MovementEvent.InputUpdate> { event ->
            when (mode) {
                ReplayMode.RECORD -> {
                    val action = event.toAction()
                    actions.add(action)
                }
                ReplayMode.REPLAY -> {
                    actionSnapshot.removeFirstOrNull()?.update(event) ?: run {
                        mode = ReplayMode.INACTIVE
                        this@Replay.info("Replay finished.")
                    }
                }
                else -> {}
            }
        }

        listener<RotationEvent.Pre> {
            when (mode) {
                ReplayMode.REPLAY -> {
                    rotationSnapshot.removeFirstOrNull()?.let { rot ->
                        it.context = RotationContext(rot, rotationConfig)
                    }
                }
                ReplayMode.RECORD -> {
                    rotations.add(player.rotation)
                }
                else -> {}
            }
        }

        listener<MovementEvent.Sprint> {
            when (mode) {
                ReplayMode.REPLAY -> {
                    sprintSnapshot.removeFirstOrNull()?.let { sprinting ->
                        if (!sprinting) {
                            it.cancel()
                        } else {
                            player.isSprinting = true
                        }
                    }
                }
                ReplayMode.RECORD -> {
                    sprints.add(player.isSprinting)
                }
                else -> {}
            }
        }
    }

    private fun handlePlay() {
        when (mode) {
            ReplayMode.REPLAY -> {
                mode = ReplayMode.INACTIVE
                this@Replay.info("Replay stopped.")
            }

            ReplayMode.INACTIVE -> {
                mode = ReplayMode.REPLAY
                actionSnapshot = actions.toMutableList()
                rotationSnapshot = rotations.toMutableList()
                sprintSnapshot = sprints.toMutableList()
                this@Replay.info("Replay started.")
            }

            else -> {}
        }
    }

    private fun handleRecord() {
        when (mode) {
            ReplayMode.RECORD -> {
                mode = ReplayMode.INACTIVE
//                this@Replay.info(GSON.toJson(actions))
                this@Replay.info("Recording stopped. Recorded for $duration")
            }

            ReplayMode.INACTIVE -> {
                if (actions.isNotEmpty()) {
                    this@Replay.info("Overriding previous recording.")
                    actions.clear()
                    rotations.clear()
                    sprints.clear()
                }
                this@Replay.info("Recording started.")
                mode = ReplayMode.RECORD
            }

            else -> {}
        }
    }

    data class MoveInputAction(
        @SerializedName("i")
        val input: InputAction,
        @SerializedName("s")
        val slowDown: Boolean,
        @SerializedName("f")
        val slowDownFactor: Float
    ) {
        fun update(event: MovementEvent.InputUpdate) {
            event.input.update(input)
            event.slowDown = slowDown
            event.slowDownFactor = slowDownFactor
        }

        companion object {
            fun MovementEvent.InputUpdate.toAction() =
                MoveInputAction(
                    InputAction(
                        input.movementSideways,
                        input.movementForward,
                        input.pressingForward,
                        input.pressingBack,
                        input.pressingLeft,
                        input.pressingRight,
                        input.jumping,
                        input.sneaking
                    ),
                    slowDown,
                    slowDownFactor
                )

            fun Input.update(input: InputAction) {
                movementSideways = input.movementSideways
                movementForward = input.movementForward
                pressingForward = input.pressingForward
                pressingBack = input.pressingBack
                pressingLeft = input.pressingLeft
                pressingRight = input.pressingRight
                jumping = input.jumping
                sneaking = input.sneaking
            }
        }
    }

    data class InputAction(
        @SerializedName("s")
        val movementSideways: Float,
        @SerializedName("f")
        val movementForward: Float,
        @SerializedName("pf")
        val pressingForward: Boolean,
        @SerializedName("pb")
        val pressingBack: Boolean,
        @SerializedName("pl")
        val pressingLeft: Boolean,
        @SerializedName("pr")
        val pressingRight: Boolean,
        @SerializedName("j")
        val jumping: Boolean,
        @SerializedName("sn")
        val sneaking: Boolean
    )
}