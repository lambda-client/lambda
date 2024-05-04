package com.lambda.module.modules.player

import com.google.gson.annotations.SerializedName
import com.lambda.config.RotationSettings
import com.lambda.core.TimerManager
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
import com.lambda.util.Communication.warn
import com.lambda.util.KeyCode
import com.lambda.util.primitives.extension.rotation
import net.minecraft.client.input.Input
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// ToDo:
//  - Use a custom binary format to store the data (Protobuf / DB?)
//  - Actually store the data in a file
//  - Implement a way to save and load the data (Commands?)
//  - Record other types of inputs: (Interactions, etc.)
object Replay : Module(
    name = "Replay",
    description = "Replay gameplay action recordings",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val record by setting("Record", KeyCode.R)
    private val play by setting("Play / Pause", KeyCode.C)
    private val stop by setting("Stop", KeyCode.X)
    private val check by setting("Checkpoint", KeyCode.V, description = "Create a checkpoint in the recording.")
    private val playCheck by setting("Play checkpoints", KeyCode.B, description = "Replays until the last set checkpoint.")
    private val loop by setting("Loop", false)
    private val loops by setting("Loops", -1, -1..10, 1, description = "Number of times to loop the replay. -1 for infinite.", unit = "repeats") { loop }
    private val cancelOnDerivation by setting("Cancel on derivation", true)
    private val derivationThreshold by setting("Derivation threshold", 0.1, 0.1..5.0, 0.1, description = "The threshold for the derivation to cancel the replay.") { cancelOnDerivation }

    private val rotationConfig = RotationSettings(this).apply {
        rotationMode = RotationMode.LOCK
    }

    enum class State {
        INACTIVE,
        RECORDING,
        PAUSED_RECORDING,
        PLAYING,
        PLAYING_CHECKPOINTS,
        PAUSED_REPLAY
    }

    private var state = State.INACTIVE

    data class Recording(
        val movement: MutableList<MoveInputAction>,
        val rotation: MutableList<Rotation>,
        val sprint: MutableList<Boolean>,
        val position: MutableList<Vec3d>
    ) {
        val size: Int
            get() = maxOf(movement.size, rotation.size, sprint.size, position.size)
        val duration: Duration
            get() = (size * TimerManager.tickLength * 1.0).toDuration(DurationUnit.MILLISECONDS)

        fun duplicate() = Recording(
            movement.toMutableList(),
            rotation.toMutableList(),
            sprint.toMutableList(),
            position.toMutableList()
        )

        companion object {
            fun new() = Recording(
                mutableListOf(),
                mutableListOf(),
                mutableListOf(),
                mutableListOf()
            )
        }
    }

    private var checkpoint: Recording? = null
    private var recording: Recording? = null
    private var replay: Recording? = null
    private var repeats = 0

    init {
        listener<KeyPressEvent> {
            if (mc.currentScreen != null && !mc.options.commandKey.isPressed) return@listener

            when (it.key) {
                record.key -> handleRecord()
                play.key -> handlePlay()
                stop.key -> handleStop()
                check.key -> handleCheckpoint()
                playCheck.key -> handlePlayCheckpoints()
                else -> {}
            }
        }

        listener<MovementEvent.InputUpdate> { event ->
            when (state) {
                State.RECORDING -> {
                    recording?.let {
                        it.movement.add(event.toAction())
                        it.position.add(player.pos)
                    }
                }
                State.PLAYING, State.PLAYING_CHECKPOINTS -> {
                    replay?.let {
                        it.position.removeFirstOrNull()?.let a@{ pos ->
                            val diff = pos.subtract(player.pos).length()
                            if (diff < 0.001) return@a

                            this@Replay.info("Current derivation: ${"%.3f".format(diff)} blocks.")
                            if (cancelOnDerivation && diff > derivationThreshold) {
                                state = State.INACTIVE
                                this@Replay.info("Replay cancelled due to exceeding derivation threshold.")
                                return@listener
                            }
                        }
                        it.movement.removeFirstOrNull()?.update(event) ?: run {
                            if (loop && repeats < loops) {
                                repeats++
                                replay = recording?.duplicate()
                                this@Replay.info("Replay looped. $repeats / $loops")
                            } else {
                                if (state != State.PLAYING_CHECKPOINTS) {
                                    state = State.INACTIVE
                                    this@Replay.info("Replay finished after ${recording?.duration}.")
                                    return@listener
                                }

                                state = State.RECORDING
                                recording = checkpoint?.duplicate()
                                this@Replay.info("Checkpoint replayed. Continued recording...")
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        listener<RotationEvent.Pre> { event ->
            when (state) {
                State.RECORDING -> {
                    recording?.rotation?.add(player.rotation)
                }
                State.PLAYING, State.PLAYING_CHECKPOINTS -> {
                    replay?.let {
                        it.rotation.removeFirstOrNull()?.let { rot ->
                            event.context = RotationContext(rot, rotationConfig)
                        }
                    }
                }
                else -> {}
            }
        }

        listener<MovementEvent.Sprint> { event ->
            when (state) {
                State.RECORDING -> {
                    recording?.sprint?.add(player.isSprinting)
                }
                State.PLAYING, State.PLAYING_CHECKPOINTS -> {
                    replay?.let {
                        it.sprint.removeFirstOrNull()?.let { sprint ->
                            event.sprint = sprint
                            player.isSprinting = sprint
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun handlePlay() {
        when (state) {
            State.INACTIVE -> {
                recording?.let {
                    state = State.PLAYING
                    replay = it.duplicate()
                    this@Replay.info("Replay started and will take ${it.duration}.")
                } ?: run {
                    this@Replay.warn("No recording to replay.")
                }
            }
            State.RECORDING -> {
                state = State.PAUSED_RECORDING
                this@Replay.info("Recording paused.")
            }
            State.PAUSED_RECORDING -> {
                state = State.RECORDING
                this@Replay.info("Recording resumed.")
            }
            State.PLAYING, State.PLAYING_CHECKPOINTS -> { // ToDo: More general pausing for all states
                state = State.PAUSED_REPLAY
                this@Replay.info("Replay paused.")
            }
            State.PAUSED_REPLAY -> {
                state = State.PLAYING
                this@Replay.info("Replay resumed.")
            }
        }
    }

    private fun handleRecord() {
        when (state) {
            State.RECORDING -> {
                state = State.INACTIVE
                this@Replay.info("Recording stopped. Recorded for ${recording?.duration}.")
            }
            State.INACTIVE -> {
                recording = Recording.new()
                state = State.RECORDING
                this@Replay.info("Recording started.")
            }
            else -> {}
        }
    }

    private fun handleStop() {
        when (state) {
            State.RECORDING, State.PAUSED_RECORDING -> {
                state = State.INACTIVE
                this@Replay.info("Recording stopped. Recorded for ${recording?.duration}.")
            }
            State.PLAYING, State.PAUSED_REPLAY, State.PLAYING_CHECKPOINTS -> {
                state = State.INACTIVE
                this@Replay.info("Replay stopped.")
            }
            else -> {}
        }
    }

    private fun handleCheckpoint() {
        when (state) {
            State.RECORDING -> {
                checkpoint = recording?.duplicate()
                this@Replay.info("Checkpoint created.")
            }
            else -> {}
        }
    }

    private fun handlePlayCheckpoints() {
        when (state) {
            State.INACTIVE -> {
                state = State.PLAYING_CHECKPOINTS
                replay = checkpoint?.duplicate()
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