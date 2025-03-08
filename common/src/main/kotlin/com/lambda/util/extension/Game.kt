/*
 * Copyright 2025 Lambda
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

package com.lambda.util.extension

import net.minecraft.client.input.Input
import net.minecraft.util.PlayerInput

var Input.forward: Boolean
    get() = playerInput.forward()
    set(value) {
        playerInput = PlayerInput(
            value,
            playerInput.backward(),
            playerInput.left(),
            playerInput.right(),
            playerInput.jump(),
            playerInput.sneak(),
            playerInput.sprint()
        )
    }

var Input.backward: Boolean
    get() = playerInput.backward()
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            value,
            playerInput.left(),
            playerInput.right(),
            playerInput.jump(),
            playerInput.sneak(),
            playerInput.sprint()
        )
    }

var Input.left: Boolean
    get() = playerInput.left()
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            playerInput.backward(),
            value,
            playerInput.right(),
            playerInput.jump(),
            playerInput.sneak(),
            playerInput.sprint()
        )
    }

var Input.right: Boolean
    get() = playerInput.right()
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            playerInput.backward(),
            playerInput.left(),
            value,
            playerInput.jump(),
            playerInput.sneak(),
            playerInput.sprint()
        )
    }

var Input.jumping: Boolean
    get() = playerInput.jump
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            playerInput.backward(),
            playerInput.left(),
            playerInput.right(),
            value,
            playerInput.sneak(),
            playerInput.sprint()
        )
    }

var Input.sneaking: Boolean
    get() = playerInput.sneak()
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            playerInput.backward(),
            playerInput.left(),
            playerInput.right(),
            playerInput.jump(),
            value,
            playerInput.sprint()
        )
    }

var Input.sprinting: Boolean
    get() = playerInput.sprint()
    set(value) {
        playerInput = PlayerInput(
            playerInput.forward(),
            playerInput.backward(),
            playerInput.left(),
            playerInput.right(),
            playerInput.jump(),
            playerInput.sneak(),
            value
        )
    }
