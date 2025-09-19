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

package com.lambda.module.modules.player

import com.lambda.config.groups.EatSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Request.Companion.submit
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.Rotation.Companion.wrap
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.EatTask
import com.lambda.task.tasks.EatTask.Companion.eat
import com.lambda.task.tasks.EatTask.Companion.shouldEat
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.MathHelper.wrapDegrees
import kotlin.random.Random

object AutoEat : Module(
    name = "AutoEat",
    description = "Eats food when you are hungry",
    tag = ModuleTag.PLAYER,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        FOOD("Food"),
        FIRE("Fire"),
        HEAL("Heal"),
    }

    private val eat = EatSettings(this, Group.FOOD)

    private var eatTask: EatTask? = null

    init {
        listen<TickEvent.Pre> {
            if (eatTask != null || !shouldEat(eat)) return@listen

            val task = eat(eat)
            task.finally { eatTask = null }
            task.run()
            eatTask = task
        }

        onDisable {
            eatTask?.cancel()
            eatTask = null
        }
    }
}
