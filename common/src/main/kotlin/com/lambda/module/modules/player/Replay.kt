package com.lambda.module.modules.player

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.lambda.config.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.core.TimerManager
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.KeyPressEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.modules.player.Replay.InputAction.Companion.toAction
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.Communication.warn
import com.lambda.util.FolderRegister
import com.lambda.util.KeyCode
import com.lambda.util.primitives.extension.rotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.input.Input
import net.minecraft.util.math.Vec3d
import java.io.File
import java.lang.reflect.Type
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
    description = "Record gameplay actions and replay them like a TAS.",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val record by setting("Record", KeyCode.R)
    private val play by setting("Play / Pause", KeyCode.C)
    private val stop by setting("Stop", KeyCode.X)
    private val check by setting("Checkpoint", KeyCode.V, description = "Create a checkpoint while recording.")
    private val playCheck by setting("Play until checkpoint", KeyCode.B, description = "Replays until the last set checkpoint.")
    private val loop by setting("Loop", false)
    private val loops by setting("Loops", -1, -1..10, 1, description = "Number of times to loop the replay. -1 for infinite.", unit = "repeats") { loop }
    private val cancelOnDeviation by setting("Cancel on deviation", true)
    private val deviationThreshold by setting("Deviation threshold", 0.1, 0.1..5.0, 0.1, description = "The threshold for the deviation to cancel the replay.") { cancelOnDeviation }

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

    private var checkpoint: Recording? = null

    private var recording: Recording? = null
    private var replay: Recording? = null
    private var repeats = 0

    private val gsonCompact = GsonBuilder()
        .registerTypeAdapter(Recording::class.java, Recording())
        .create()

    fun loadRecording(file: File) {
        recording = gsonCompact.fromJson(file.readText(), Recording::class.java)

        info("Recording ${file.nameWithoutExtension} loaded. Duration: ${recording?.duration}.")
    }

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
                        it.input.add(event.input.toAction())
                        it.position.add(player.pos)
                    }
                }
                State.PLAYING, State.PLAYING_CHECKPOINTS -> {
                    replay?.let {
                        it.position.removeFirstOrNull()?.let a@{ pos ->
                            val diff = pos.subtract(player.pos).length()
                            if (diff < 0.001) return@a

                            this@Replay.warn("Position deviates from the recording by ${"%.3f".format(diff)} blocks.")
                            if (cancelOnDeviation && diff > deviationThreshold) {
                                state = State.INACTIVE
                                this@Replay.warn("Replay cancelled due to exceeding deviation threshold.")
                                return@listener
                            }
                        }
                        it.input.removeFirstOrNull()?.update(event.input)
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
                            player.isSprinting = sprint // ToDo: Find out why
                        }
                    }
                }
                else -> {}
            }
        }

        listener<MovementEvent.Post> {
            when (state) {
                State.PLAYING, State.PLAYING_CHECKPOINTS -> {
                    replay?.let {
                        if (it.size != 0) return@listener

                        if (loop && repeats < loops) {
                            if (repeats >= 0) repeats++
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
                    this@Replay.info("Replay started. ETA: ${it.duration}.")
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
                recording = Recording()
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

    private fun SafeContext.handleCheckpoint() {
        when (state) {
            State.RECORDING -> {
                if (player.velocity != Vec3d(0.0, -0.0784000015258789, 0.0)) {
                    this@Replay.logError("Cannot create checkpoint while moving. Try again!")
                    return
                }

                checkpoint = recording?.duplicate()
                lambdaScope.launch(Dispatchers.IO) {
                    FolderRegister.replays.mkdirs()
                    FolderRegister.replays.resolve("checkpoint-${
                        mc.currentServerEntry?.address?.replace(":", "_")
                    }-${
                        world.dimensionKey?.value?.path?.replace("/", "_")
                    }-${
                        System.currentTimeMillis()
                    }.json").writeText(gsonCompact.toJson(checkpoint))
                }
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
                this@Replay.info("Replaying until last set checkpoint. ETA: ${checkpoint?.duration}")
            }
            else -> {}
        }
    }

    data class Recording(
        val input: MutableList<InputAction> = mutableListOf(),
        val rotation: MutableList<Rotation> = mutableListOf(),
        val sprint: MutableList<Boolean> = mutableListOf(),
        val position: MutableList<Vec3d> = mutableListOf()
    ) : JsonSerializer<Recording>, JsonDeserializer<Recording> {
        val size: Int
            get() = minOf(input.size, rotation.size, sprint.size, position.size)
        val duration: Duration
            get() = (size * TimerManager.tickLength * 1.0).toDuration(DurationUnit.MILLISECONDS)

        fun duplicate() = Recording(
            input.take(size).toMutableList(),
            rotation.take(size).toMutableList(),
            sprint.take(size).toMutableList(),
            position.take(size).toMutableList()
        )

        override fun serialize(
            src: Recording?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?,
        ): JsonElement = src?.let { recording ->
            JsonArray().apply {
                repeat(recording.size) { i ->
                    add(JsonArray().apply {
                        val inputI = recording.input[i]
                        add(inputI.movementSideways)
                        add(inputI.movementForward)
                        add(inputI.pressingForward)
                        add(inputI.pressingBack)
                        add(inputI.pressingLeft)
                        add(inputI.pressingRight)
                        add(inputI.jumping)
                        add(inputI.sneaking)
                        val rotationI = recording.rotation[i]
                        add(rotationI.yaw)
                        add(rotationI.pitch)
                        add(recording.sprint[i])
                        val positionI = recording.position[i]
                        add(positionI.x)
                        add(positionI.y)
                        add(positionI.z)
                    })
                }
            }
        } ?: JsonNull.INSTANCE

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Recording = json?.asJsonArray?.let {
            val input = mutableListOf<InputAction>()
            val rotation = mutableListOf<Rotation>()
            val sprint = mutableListOf<Boolean>()
            val position = mutableListOf<Vec3d>()

            it.forEach { element ->
                val array = element.asJsonArray
                input.add(InputAction(
                    array[0].asFloat,
                    array[1].asFloat,
                    array[2].asBoolean,
                    array[3].asBoolean,
                    array[4].asBoolean,
                    array[5].asBoolean,
                    array[6].asBoolean,
                    array[7].asBoolean
                ))
                rotation.add(Rotation(array[8].asDouble, array[9].asDouble))
                sprint.add(array[10].asBoolean)
                position.add(Vec3d(array[11].asDouble, array[12].asDouble, array[13].asDouble))
            }

            Recording(input, rotation, sprint, position)
        } ?: Recording()
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
    ) {
        fun update(input: Input) {
            input.movementSideways = movementSideways
            input.movementForward = movementForward
            input.pressingForward = pressingForward
            input.pressingBack = pressingBack
            input.pressingLeft = pressingLeft
            input.pressingRight = pressingRight
            input.jumping = jumping
            input.sneaking = sneaking
        }

        companion object {
            fun Input.toAction() =
                InputAction(
                    movementSideways,
                    movementForward,
                    pressingForward,
                    pressingBack,
                    pressingLeft,
                    pressingRight,
                    jumping,
                    sneaking
                )
        }
    }
}