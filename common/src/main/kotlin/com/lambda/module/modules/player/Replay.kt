/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.google.gson.*
import com.lambda.brigadier.CommandResult
import com.lambda.config.groups.IRotationConfig
import com.lambda.context.SafeContext
import com.lambda.core.TimerManager
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.rotation.RotationMode
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.modules.player.Replay.InputAction.Companion.toAction
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Communication.info
import com.lambda.util.Communication.logError
import com.lambda.util.Communication.warn
import com.lambda.util.FolderRegister
import com.lambda.util.FolderRegister.locationBoundDirectory
import com.lambda.util.Formatting.asString
import com.lambda.util.Formatting.getTime
import com.lambda.util.KeyCode
import com.lambda.util.StringUtils.sanitizeForFilename
import com.lambda.util.extension.rotation
import com.lambda.util.text.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.input.Input
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import java.io.File
import java.lang.reflect.Type
import java.time.format.DateTimeFormatter
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// ToDo:
//  - Record other types of inputs: (place, break, inventory, etc.)
//  - Add HUD for recording / replaying info
//  - Maybe use a custom binary format to store the data (Protobuf / DB?)
object Replay : Module(
    name = "Replay",
    description = "Record gameplay actions and replay them like a TAS.",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val record by setting("Record", KeyCode.R)
    private val play by setting("Play / Stop", KeyCode.C)
    private val cycle by setting("Cycle Play Mode", KeyCode.B, description = "REPLAY: Replay the recording once. CONTINUE: Replay the recording and continue recording. LOOP: Loop the recording.")
    private val check by setting("Set Checkpoint", KeyCode.V, description = "Create a checkpoint while recording.")

    private val loops by setting("Loops", -1, -1..10, 1, description = "Number of times to loop the replay. -1 for infinite.", unit = " repeats")
    private val velocityCheck by setting("Velocity check", true, description = "Check if the player is moving before starting a recording.")
    private val cancelOnDeviation by setting("Cancel on deviation", true)
    private val deviationThreshold by setting("Deviation threshold", 0.1, 0.1..5.0, 0.1, description = "The threshold for the deviation to cancel the replay.") { cancelOnDeviation }
    private val lockCamera by setting("Lock Camera", true)

    private val rotationConfig = object : IRotationConfig.Instant {
        override val rotationMode = if (lockCamera) RotationMode.LOCK else RotationMode.SYNC
    }

    enum class State {
        INACTIVE,
        RECORDING,
        PLAYING,
    }

    enum class PlayMode {
        REPLAY,
        CONTINUE,
        LOOP
    }

    private var state = State.INACTIVE
    private var playMode = PlayMode.REPLAY

    private var buffer: Recording? = null
    private var playback: Recording? = null
    private var recordings = mutableListOf<Recording>()

    private var repeats = 0
    private val still = Vec3d(0.0, -0.0784000015258789, 0.0)
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SS")

    private val gsonCompact = GsonBuilder()
        .registerTypeAdapter(Recording::class.java, Recording())
        .create()

    init {
        listen<KeyboardEvent.Press> {
            if (mc.currentScreen != null && !mc.options.commandKey.isPressed) return@listen

            when (it.translated) {
                record -> handleRecord()
                play -> handlePlay()
                cycle -> handlePlayModeCycle()
                check -> handleCheckpoint()
                else -> {}
            }
        }

        listen<MovementEvent.InputUpdate> { event ->
            when (state) {
                State.RECORDING -> {
                    buffer?.let {
                        it.input.add(event.input.toAction())
                        it.position.add(player.pos)
                    }
                }

                State.PLAYING -> {
                    buffer?.let {
                        it.input.removeFirstOrNull()?.update(event.input)
                        it.position.removeFirstOrNull()?.let a@{ pos ->
                            val diff = pos.subtract(player.pos).length()
                            if (diff < 0.02) return@a

                            this@Replay.warn(
                                "Position deviates from the recording by ${
                                    "%.3f".format(diff)
                                } blocks. Desired position: ${pos.asString(3)}"
                            )
                            if (cancelOnDeviation && diff > deviationThreshold) {
                                state = State.INACTIVE
                                this@Replay.logError("Replay cancelled due to exceeding deviation threshold.")
                                return@listen
                            }
                        }
                    }
                }

                else -> {}
            }
        }

        listen<RotationEvent.Update> { event ->
            when (state) {
                State.RECORDING -> {
                    buffer?.rotation?.add(player.rotation)
                }

                State.PLAYING -> {
                    buffer?.rotation?.removeFirstOrNull()?.let { rot ->
                        event.context = RotationContext(rot, rotationConfig)
                    }
                }

                else -> {}
            }
        }

        listen<MovementEvent.Sprint> { event ->
            when (state) {
                State.RECORDING -> {
                    buffer?.sprint?.add(player.isSprinting)
                }

                State.PLAYING -> {
                    buffer?.sprint?.removeFirstOrNull()?.let { sprint ->
                        event.sprint = sprint
                        player.isSprinting = sprint
                    }
                }

                else -> {}
            }
        }

        listen<MovementEvent.Player.Post> {
            when (state) {
                State.RECORDING -> {
                    buffer?.let {
                        val standingStill = player.velocity.squaredDistanceTo(Vec3d.ZERO) == 0.0
                        val isNotStart = player.pos != it.startPos
                        val didntSaveYet = (recordings.lastOrNull()?.endPos != player.pos || recordings.isEmpty())
                        if (standingStill && didntSaveYet && isNotStart) {
                            val saving = it.duplicate()
                            recordings.add(saving)
                            val index = recordings.indexOf(saving)
                            this@Replay.info(buildText {
                                literal("Auto saved #")
                                color(GuiSettings.primaryColor) { literal("$index") }
                                literal(" of ")
                                color(GuiSettings.primaryColor) { literal(saving.duration.toString()) }
                                literal(" at ")
                                color(GuiSettings.primaryColor) { literal(saving.endPos.asString(1)) }
                                playMessage(saving)
                                saveMessage(saving)
                                pruneMessage(saving)
                            })
                        }
                    }
                }

                State.PLAYING -> {
                    buffer?.let {
                        if (it.size != 0) return@listen

                        if (playMode == PlayMode.LOOP && (repeats < loops || loops < 0)) {
                            if (repeats >= 0) repeats++
                            buffer = playback?.duplicate()
                            this@Replay.info(buildText {
                                if (repeats > 0) {
                                    color(GuiSettings.primaryColor) { literal("[$repeats / $loops]") }
                                } else {
                                    color(GuiSettings.primaryColor) { literal("[$repeats/∞]") }
                                }
                                literal(" Replay looped.")
                            })
                        } else {
                            repeats = 0

                            if (playMode != PlayMode.CONTINUE) {
                                state = State.INACTIVE
                                this@Replay.info(buildText {
                                    literal("Replay finished after ")
                                    color(GuiSettings.primaryColor) { literal(playback?.duration.toString()) }
                                    literal(".")
                                })
                                return@listen
                            }

                            state = State.RECORDING
                            buffer = playback?.duplicate()
                            playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)
                            this@Replay.info("Recording fully replayed. Continuing recording...")
                        }
                    }
                }

                else -> {}
            }
        }
    }

    fun loadRecording(file: File) {
        val deserialized = gsonCompact.fromJson(file.readText(), Recording::class.java)
        recordings.add(deserialized)

        info(buildText {
            literal("Recording #${recordings.indexOf(deserialized)} ")
            color(GuiSettings.primaryColor) { literal(file.nameWithoutExtension) }
            literal(" loaded. Duration: ")
            color(GuiSettings.primaryColor) { literal(deserialized.duration.toString()) }
            playMessage(deserialized)
            pruneMessage(deserialized)
        })
    }

    fun saveRecording(index: Int, name: String): CommandResult {
        val recording = recordings.getOrNull(index) ?: run {
            return CommandResult.failure("Recording #$index does not exist.")
        }

        save(recording, name)
        return CommandResult.success()
    }

    fun playRecording(index: Int): CommandResult {
        if (state != State.INACTIVE) {
            return CommandResult.failure("Cannot play recording while recording or replaying. Finish the current action first.")
        }

        val recording = recordings.getOrNull(index) ?: run {
            return CommandResult.failure("Recording #$index does not exist.")
        }

        state = State.PLAYING
        buffer = recording.duplicate()
        playback = recording
        info(buildText {
            literal("Replaying recording #")
            color(GuiSettings.primaryColor) { literal(index.toString()) }
            literal(" of ")
            color(GuiSettings.primaryColor) { literal(recording.duration.toString()) }
        })
        return CommandResult.success()
    }

    fun pruneRecording(index: Int): CommandResult {
        val toShorten = recordings.getOrNull(index) ?: run {
            return CommandResult.failure("Recording #$index does not exist.")
        }

        val pruned = toShorten.postProcess()
        recordings.add(pruned)
        info(buildText {
            literal("Shortened recording #")
            color(GuiSettings.primaryColor) { literal(recordings.indexOf(toShorten).toString()) }
            literal(" of ")
            color(GuiSettings.primaryColor) { literal(toShorten.duration.toString()) }
            literal(" to new recording #")
            color(GuiSettings.primaryColor) { literal(recordings.indexOf(pruned).toString()) }
            literal(" of ")
            color(GuiSettings.primaryColor) { literal(pruned.duration.toString()) }
            playMessage(pruned)
            saveMessage(pruned)
        })
        return CommandResult.success()
    }

    private fun handlePlay() {
        when (state) {
            State.INACTIVE -> {
                recordings.lastOrNull()?.let {
                    state = State.PLAYING
                    playback = it
                    buffer = it.duplicate()
                    info(buildText {
                        literal("Replaying most recent recording #${recordings.indexOf(it)}. Duration: ")
                        color(GuiSettings.primaryColor) { literal(it.duration.toString()) }
                    })
                } ?: run {
                    this@Replay.warn("No recording to replay.")
                }
            }

            State.PLAYING -> {
                state = State.INACTIVE
                this@Replay.info("Replay stopped.")
            }

            else -> {}
        }
    }

    private fun SafeContext.handleRecord() {
        when (state) {
            State.RECORDING -> {
                stopRecording()
            }

            State.INACTIVE -> {
                if (velocityCheck && player.velocity != still) {
                    this@Replay.logError("Cannot start recording while moving. Slow down and try again!")
                    return
                }

                buffer = Recording()
                state = State.RECORDING
                this@Replay.info("Recording started...")
            }

            else -> {}
        }
    }

    private fun stopRecording() {
        state = State.INACTIVE

        val rec = buffer ?: return
        recordings.add(rec)
        this@Replay.info(buildText {
            literal("Stopped recording #")
            color(GuiSettings.primaryColor) { literal("${recordings.indexOf(rec)}") }
            literal(" of ")
            color(GuiSettings.primaryColor) { literal(rec.duration.toString()) }
            literal(".")
            playMessage(rec)
            saveMessage(rec)
            pruneMessage(rec)
        })
    }

    private fun handleCheckpoint() {
        when (state) {
            State.RECORDING -> {
                val checkRec = buffer?.duplicate() ?: return
                recordings.add(checkRec)
                this@Replay.info(buildText {
                    literal("Checkpoint #")
                    color(GuiSettings.primaryColor) { literal("${recordings.indexOf(checkRec)}") }
                    literal(" created at ")
                    color(GuiSettings.primaryColor) { literal(checkRec.endPos.asString(0)) }
                    literal(".")
                    playMessage(checkRec)
                    saveMessage(checkRec)
                    pruneMessage(checkRec)
                })
            }

            else -> {
                this@Replay.info("Cannot set checkpoint while not recording.")
            }
        }
    }

    private fun handlePlayModeCycle() {
        val oldMode = playMode
        playMode = PlayMode.entries[(playMode.ordinal + 1) % PlayMode.entries.size]
        info(buildText {
            literal("Set play mode to ")
            color(GuiSettings.primaryColor) { literal(playMode.name) }
            literal(" (previously ")
            color(GuiSettings.primaryColor) { literal(oldMode.name) }
            literal(")")
        })
    }

    private fun save(
        recording: Recording,
        name: String,
    ) {
        if (recording.size <= 5) {
            this@Replay.warn("Recording too short. Minimum length: 5 ticks.")
            return
        }
        val file = FolderRegister.replay.toFile().locationBoundDirectory().resolve("$name.json")

        lambdaScope.launch(Dispatchers.IO) {
            file.writeText(gsonCompact.toJson(recording))

            this@Replay.info(buildText {
                literal("Saved recording #")
                color(GuiSettings.primaryColor) { literal("${recordings.indexOf(recording)}") }
                literal(" of ")
                color(GuiSettings.primaryColor) { literal(recording.duration.toString()) }
                literal(" in file ")
                color(GuiSettings.primaryColor) { literal(name) }
                val filePath = file.toPath().pathString
                hoverEvent(HoverEvents.showText(buildText {
                    literal("Open file ")
                    color(GuiSettings.primaryColor) { literal(filePath) }
                })) {
                    clickEvent(ClickEvents.openFile(filePath)) {
                        literal(" [")
                        color(GuiSettings.secondaryColor) { literal("OPEN FILE") }
                        literal("]")
                    }
                }
                val parentPath = file.parentFile.toPath().pathString
                hoverEvent(HoverEvents.showText(buildText {
                    literal("Open folder ")
                    color(GuiSettings.primaryColor) { literal(parentPath) }
                })) {
                    clickEvent(ClickEvents.openFile(parentPath)) {
                        literal(" [")
                        color(GuiSettings.secondaryColor) { literal("OPEN FOLDER") }
                        literal("]")
                    }
                }
            })
        }
    }

    private fun TextBuilder.playMessage(recording: Recording) {
        hoverEvent(HoverEvents.showText(buildText {
            literal("Click to replay recording #")
            color(GuiSettings.primaryColor) { literal("${recordings.indexOf(recording)}") }
            literal(".")
        })) {
            clickEvent(ClickEvents.suggestCommand(";replay play ${recordings.indexOf(recording)}")) {
                literal(" [")
                color(GuiSettings.secondaryColor) {
                    literal("PLAY")
                }
                literal("]")
            }
        }
    }

    private fun TextBuilder.pruneMessage(recording: Recording) {
        if (recording.pruneTimesave.inWholeMilliseconds <= 0) return

        hoverEvent(HoverEvents.showText(buildText {
            literal(" Recording can be shortened by ")
            color(GuiSettings.primaryColor) {
                literal(recording.pruneTimesave.toString())
            }
            literal(" by removing idles and cyclic paths.")
        })) {
            clickEvent(ClickEvents.suggestCommand(";replay prune ${recordings.indexOf(recording)}")) {
                literal(" [")
                color(GuiSettings.secondaryColor) {
                    literal("PRUNE ")
                }
                color(GuiSettings.primaryColor) {
                    literal("${recording.pruneTimesave}")
                }
                literal("]")
            }
        }
    }

    private fun TextBuilder.saveMessage(recording: Recording) {
        hoverEvent(HoverEvents.showText(buildText {
            literal("Click to save recording #")
            color(GuiSettings.primaryColor) { literal("${recordings.indexOf(recording)}") }
            literal(".")
        })) {
            clickEvent(ClickEvents.suggestCommand(";replay save ${recordings.indexOf(recording)} ${getTime(fileFormatter).sanitizeForFilename()}")) {
                literal(" [")
                color(GuiSettings.secondaryColor) { literal("SAVE") }
                literal("]")
            }
        }
    }

    private fun Recording.postProcess(): Recording {
        val pruned = duplicate()
        val cyclicPaths = position.findCyclicPaths()

        if (cyclicPaths.size >= pruned.size - 5) return pruned
        this@Replay.info("Removing cyclic paths...")

        cyclicPaths.sortedDescending().forEach {
            pruned.input.removeAt(it)
            pruned.rotation.removeAt(it)
            pruned.sprint.removeAt(it)
            pruned.position.removeAt(it)
        }
        this@Replay.info(buildText {
            literal("Postprocessing finished. Shortened recording by ")
            color(GuiSettings.primaryColor) { literal((duration - pruned.duration).toString()) }
            literal(".")
        })
        return pruned
    }

    private fun <T> List<T>.findCyclicPaths(minInterval: Int = 5) =
        flatMapIndexed { i, e ->
            subList(i + 1, size)
                .firstOrNull { it == e }
                ?.let {
                    val last = lastIndexOf(it) - 1
                    val interval = last - (i + 1)
                    if (interval > minInterval) {
                        return@flatMapIndexed (i + 2)..last - 2
                    }
                }
            return@flatMapIndexed emptyList()
        }.toSet()

    data class Recording(
        val input: MutableList<InputAction> = mutableListOf(),
        val rotation: MutableList<Rotation> = mutableListOf(),
        val sprint: MutableList<Boolean> = mutableListOf(),
        val position: MutableList<Vec3d> = mutableListOf(),
//        val interaction: MutableList<Interaction> = mutableListOf()
    ) : JsonSerializer<Recording>, JsonDeserializer<Recording> {
        val size: Int
            get() = minOf(input.size, rotation.size, sprint.size, position.size)
        val duration: Duration
            get() = (size * TimerManager.lastTickLength * 1.0).toDuration(DurationUnit.MILLISECONDS)
        val startPos: Vec3d
            get() = position.firstOrNull() ?: Vec3d.ZERO
        val endPos: Vec3d
            get() = position.lastOrNull() ?: Vec3d.ZERO
        val pruneTimesave: Duration
            get() = (position.findCyclicPaths(5).size * 50L).toDuration(DurationUnit.MILLISECONDS)

        fun duplicate() = Recording(
            input.take(size).toMutableList(),
            rotation.take(size).toMutableList(),
            sprint.take(size).toMutableList(),
            position.take(size).toMutableList()
        )

        override fun toString() = "Recording from ${
            startPos.asString(1)
        } to ${endPos.asString(1)} (in ${duration})"

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
            context: JsonDeserializationContext?,
        ): Recording = json?.asJsonArray?.let {
            val input = mutableListOf<InputAction>()
            val rotation = mutableListOf<Rotation>()
            val sprint = mutableListOf<Boolean>()
            val position = mutableListOf<Vec3d>()

            it.forEach { element ->
                val array = element.asJsonArray
                input.add(
                    InputAction(
                        array[0].asFloat,
                        array[1].asFloat,
                        array[2].asBoolean,
                        array[3].asBoolean,
                        array[4].asBoolean,
                        array[5].asBoolean,
                        array[6].asBoolean,
                        array[7].asBoolean
                    )
                )
                rotation.add(Rotation(array[8].asDouble, array[9].asDouble))
                sprint.add(array[10].asBoolean)
                position.add(Vec3d(array[11].asDouble, array[12].asDouble, array[13].asDouble))
            }

            Recording(input, rotation, sprint, position)
        } ?: Recording()
    }

    data class InputAction(
        val movementSideways: Float,
        val movementForward: Float,
        val pressingForward: Boolean,
        val pressingBack: Boolean,
        val pressingLeft: Boolean,
        val pressingRight: Boolean,
        val jumping: Boolean,
        val sneaking: Boolean,
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
