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

package com.lambda.gui.core

import com.lambda.Lambda.mc
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

object RenderThread : CoroutineDispatcher() {
    private val scope = CoroutineScope(this + SupervisorJob())

    override fun isDispatchNeeded(context: CoroutineContext) = !mc.isOnThread

    override fun dispatch(context: CoroutineContext, block: Runnable) = mc.send(block)

    @JvmStatic
    fun after(millis: Long, block: () -> Unit): Job =
        scope.launch {
            delay(millis.milliseconds)
            block()
        }
}
