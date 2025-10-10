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

package com.lambda.interaction.request.interacting

import com.lambda.context.Automated
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.InteractionContext
import com.lambda.interaction.request.ActionInfo
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder

data class InteractionInfo(
    override val context: InteractionContext,
    override val pendingInteractionsList: MutableCollection<BuildContext>,
    private val automated: Automated
) : ActionInfo, InteractConfig by automated.interactConfig, LogContext {
    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Interaction Info") {
            text(context.getLogContextBuilder())
        }
    }
}