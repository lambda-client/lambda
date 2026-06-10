/*
 * Copyright 2026 Lambda
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

package com.lambda.event.events

import com.lambda.event.Event

/**
 * Phases:
 *
 * 1. **Pre-Tick**: Increments uptime, steps world tick manager, decrement item use cooldown.
 * 2. **GUI Update**: Processes delayed messages, updates HUD.
 * 3. **Game Mode Update**: Updates targeted entity, ticks tutorial, and interaction managers.
 * 4. **Texture Update**: Ticks texture manager.
 * 5. **Screen Handling**: Manages screen logic, ticks current screen.
 * 6. **Debug HUD Update**: Resets debug HUD chunk.
 * 7. **Input Handling**: Handles input events, decrements attack cooldown.
 * 8. **World Update**: Ticks game and world renderers, world entities.
 * 9. **Music and Sound Update**: Ticks music tracker and sound manager.
 * 10. **Tutorial and Social Interactions**: Handles tutorial and social interactions, ticks world.
 * 11. **Pending Connection**: Ticks integrated server connection.
 * 12. **Keyboard Handling**: Polls for debug crash key presses.
 *
 * @see net.minecraft.client.MinecraftClient.tick
 */
sealed class TickEvent : Event {
    /**
     * Triggered before each iteration of the game loop.
     *
     * Phases:
     *
     * 1. Increments uptime
     * 2. Steps world tick manager
     * 3. Decrements item use cooldown
     */
    data object Pre : TickEvent()

    /**
     * Triggered after each iteration of the game loop.
     */
    data object Post : TickEvent()

    /**
     * Triggered during the network tick stage only
     *
     * Phases:
     *
     * 1. Synchronizes player inventory slot changes
     * 2. Clears the outgoing packet queue
     * 3. Ticks packet listeners
     * 4. Flushes the connection channel
     * 5. Updates network statistics
     * 6. Updates the packet logger
     *
     * @see net.minecraft.client.network.ClientPlayerInteractionManager.tick
     */
    sealed class Network {
        data object Pre : TickEvent()
        data object Post : TickEvent()
    }

    /**
     * Triggered during the input tick stage
     *
     * Phases:
     *
     * 1. Handles various game specific keys
     * 2. Handles block breaking
     * 3. Adds block breaking particles
     * 4. Swings the player arm
     * 5. Decrements attack cooldown
     *
     * @see net.minecraft.client.MinecraftClient.handleInputEvents
     */
    sealed class Input {
        data object Pre : TickEvent()
        data object Post : TickEvent()
    }

    /**
     * Triggered during the world render tick stage
     *
     * @see net.minecraft.client.render.WorldRenderer.tick
     */
    sealed class WorldRender {
        data object Pre : TickEvent()
        data object Post : TickEvent()
    }

    /**
     * Triggered during the sound update tick stage
     *
     * @see net.minecraft.client.sound.SoundManager.tick
     */
    sealed class Sound {
        data object Pre : TickEvent()
        data object Post : TickEvent()
    }

    /**
     * Triggered before ([Pre]) and after ([Post]) each render tick.
     *
     * Phases:
     *
     * 1. **Pre-Render**: Prepares the window for rendering, checks for window close, handles resource reloads.
     * 2. **Task Execution**: Executes pending render tasks.
     * 3. **Client Tick**: Ticks the client ([TickEvent.Pre] and [TickEvent.Post]) until tick target was met.
     * 4. **Render**: Performs the actual rendering of the game.
     * 5. **Post-Render**: Finalizes the rendering process, updates the window.
     *
     * @see net.minecraft.client.MinecraftClient.render
     */
    sealed class Render {
        /**
         * Triggered before each render tick ([TickEvent.Render]) of the game loop.
         */
        data object Pre : TickEvent()

        /**
         * Triggered after each render tick ([TickEvent.Render]) of the game loop.
         */
        data object Post : TickEvent()
    }

    /**
     * Triggered before ([Pre]) and after ([Post]) each player tick that is run during the game loop [TickEvent.Pre].
     *
     * Phases:
     *
     * 1. **Pre-Tick**: Prepares player state before the tick.
     * 2. **Movement**: Handles player movement and input.
     * 3. **Action**: Processes player actions like swinging hand.
     * 4. **Post-Tick**: Finalizes player state after the tick.
     *
     * @see net.minecraft.client.network.ClientPlayerEntity.tick
     */
    sealed class Player {
        /**
         * Triggered before each player tick ([TickEvent.Player]).
         */
        data object Pre : TickEvent()

        /**
         * Triggered after each player tick ([TickEvent.Player]).
         */
        data object Post : TickEvent()
    }

    companion object {
        val AllStages by lazy {
            listOf(
                Pre,
                Post,
                Network.Pre,
                Network.Post,
                Input.Pre,
                Input.Post,
                WorldRender.Pre,
                WorldRender.Post,
                Sound.Pre,
                Sound.Post,
                Render.Pre,
                Render.Post,
                Player.Pre,
                Player.Post,
            )
        }
    }
}
