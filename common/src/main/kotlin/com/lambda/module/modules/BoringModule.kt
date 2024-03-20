package com.lambda.module.modules

import com.lambda.Lambda.LOG
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.Communication.toast
import com.lambda.util.KeyCode
import net.minecraft.util.math.BlockPos
import java.awt.Color

object BoringModule : Module(
    name = "BoringModule",
    description = "This is a boring module",
    defaultTags = setOf(ModuleTag.MISC, ModuleTag.COMBAT),
    defaultKeybind = KeyCode.Z
) {
    private val superBoring by setting("Super Boring", false)
    private val boringColor by setting("Boring Color", Color.RED)
    private val boringValue by setting("Boring Value", 0.0, 0.1..5.0, 0.1)
    private val boringFriends by setting("Boring Friends List", listOf("Peter", "Paul", "Mary", "John", "Ringo"))
    private val boringEnemies by setting("Boring Enemies Set", setOf("Sauron", "Voldemort", "Darth Vader", "The Joker"))
    private val boringMap by setting(
        "Boring Map",
        mapOf("One" to 1, "Two" to 2, "Three" to 3, "Four" to 4, "Five" to 5)
    )
    private val boringEnum by setting("Boring Enum", BoringEnum.ONE)

    //private val blockSetting by setting("Boring Block", Blocks.STONE) // Registries are not initialized yet
    private val blockPosSetting by setting("Boring BlockPos", BlockPos(420, 69, 1337))
//    private val blockListSetting by setting("Boring Block List", listOf(Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK))

    enum class BoringEnum {
        ONE, TWO, THREE, FOUR, FIVE
    }

    init {
        onEnable {
            LOG.info("I'm was enabled!")
        }

        onDisable {
            LOG.info("I'm was disabled!")
        }

        onToggle {
            LOG.info("I'm now ${if (it) "enabled" else "disabled"}!")
        }

        listener<TickEvent.Pre> {
            LOG.info("I'm ${if (superBoring) "super boring ($boringValue)" else "boring"}!")
        }
    }
}
