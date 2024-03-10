package com.lambda.module.modules

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object BoringModule : Module(
    name = "BoringModule",
    description = "This is a boring module",
    tags = setOf(ModuleTag.MISC, ModuleTag.COMBAT),
) {
    private val superBoring by setting("Super Boring", false)
    private val boringValue by setting("Boring Value", 0.0, 0.1..5.0, 0.1)
    private val boringFriends by setting("Boring Friends List", listOf("Peter", "Paul", "Mary", "John", "Ringo"))
    private val boringEnemies by setting("Boring Enemies Set", setOf("Sauron", "Voldemort", "Darth Vader", "The Joker"))
    private val boringMap by setting("Boring Map", mapOf("One" to 1, "Two" to 2, "Three" to 3, "Four" to 4, "Five" to 5))
    private val boringEnum by setting("Boring Enum", BoringEnum.ONE)

    enum class BoringEnum {
        ONE, TWO, THREE, FOUR, FIVE
    }

    init {
        listener<TickEvent.Pre> {
            if (isEnabled) println("I'm ${if (superBoring) "super boring ($boringValue)" else "boring"}!")
        }
    }
}