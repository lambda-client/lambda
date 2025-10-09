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

package com.lambda.interaction.request

import com.lambda.config.groups.BuildConfig
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.request.breaking.BrokenBlockHandler
import com.lambda.util.collections.LimitedDecayQueue

abstract class PostActionHandler<T : ActionInfo> {
    abstract val pendingActions: LimitedDecayQueue<T>

    init {
        listen<TickEvent.Pre>(priority = Int.MAX_VALUE) {
            pendingActions.cleanUp()
        }

        listenUnsafe<ConnectionEvent.Connect.Pre>(priority = Int.MIN_VALUE) {
            pendingActions.clear()
        }
    }

    fun T.startPending() {
        pendingActions.add(this)
        pendingInteractionsList.add(context)
    }

    fun T.stopPending() {
        pendingActions.remove(this)
        pendingInteractionsList.remove(context)
    }

    fun setPendingConfigs(build: BuildConfig) {
        BrokenBlockHandler.pendingActions.setSizeLimit(build.breakConfig.maxPendingBreaks)
        BrokenBlockHandler.pendingActions.setDecayTime(build.interactionTimeout * 50L)
    }
}