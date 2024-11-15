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

package com.lambda.event.events

import com.lambda.event.Event

abstract class TickEvent : Event {
    /**
     * Triggered before each iteration of the game loop.
     *
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
    class Pre : TickEvent()

    /**
     * Triggered after each iteration of the game loop.
     * Targeted at 20 ticks per second.
     *
     * Phases:
     *
     * 1. **Pre-Tick**: Increments uptime, steps world tick manager, decrement item use cooldown.
     * 2. **GUI Update**: Processes delayed messages, updates HUD.
     * 3. **Game Mode Update**: Updates targeted entity, ticks tutorial, and interaction managers.
     * 4. **Texture Update**: Ticks texture manager.
     * 5. **Screen Handling**: Manages screen logic, ticks current screen.
     * 6. **Debug HUD Update**: Resets debug HUD chunk.
     * 7. **Input Handling**: Handles input events, decrements attack cooldown.
     * 8. **World Update**: Ticks game and world renderers, world entities (such as [TickEvent.Player]).
     * 9. **Music and Sound Update**: Ticks music tracker and sound manager.
     * 10. **Tutorial and Social Interactions**: Handles tutorial and social interactions, ticks world.
     * 11. **Pending Connection**: Ticks integrated server connection.
     * 12. **Keyboard Handling**: Polls for debug crash key presses.
     *
     * @see net.minecraft.client.MinecraftClient.tick
     */
    class Post : TickEvent()

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
    abstract class Render : TickEvent() {
        /**
         * Triggered before each render tick ([TickEvent.Render]) of the game loop.
         */
        class Pre : TickEvent()

        /**
         * Triggered after each render tick ([TickEvent.Render]) of the game loop.
         */
        class Post : TickEvent()
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
    abstract class Player : TickEvent() {
        /**
         * Triggered before each player tick ([TickEvent.Player]).
         */
        class Pre : Player()

        /**
         * Triggered after each player tick ([TickEvent.Player]).
         */
        class Post : Player()
    }
}
