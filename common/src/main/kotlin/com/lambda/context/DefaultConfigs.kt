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

package com.lambda.context

import com.lambda.module.modules.client.TaskFlowModule

object DefaultConfigs : Configured {
    override val build     = TaskFlowModule.build
    override val interact  = TaskFlowModule.interact
    override val inventory = TaskFlowModule.inventory
    override val hotbar    = TaskFlowModule.hotbar
    override val rotation  = TaskFlowModule.rotation
}

val Configured.breaking get() = build.breaking
val Configured.placing get() = build.placing