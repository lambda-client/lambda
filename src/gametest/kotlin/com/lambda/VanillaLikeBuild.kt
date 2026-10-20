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

package com.lambda

import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.BreakConfig
import com.lambda.config.blocks.InteractConfig
import com.lambda.context.Automated

internal object VanillaLikeBuild : Automated by AutomationConfig.DEFAULT {
    override val breakConfig: BreakConfig = object : BreakConfig by AutomationConfig.DEFAULT.breakConfig {
        override val breakMode = BreakConfig.BreakMode.Vanilla
        override val breakThreshold = 1.0F
        override val fudgeFactor = 0
        override val doubleBreak = false
        override val rebreak = false
        override val breakDelay = 0
        override val rotate = true
        override val efficientOnly = false
        override val suitableToolsOnly = false
    }

    override val interactConfig: InteractConfig = object : InteractConfig by AutomationConfig.DEFAULT.interactConfig {
        override val rotate = true
        override val airPlace = InteractConfig.AirPlaceMode.None
    }
}
