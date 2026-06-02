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

package com.lambda.config.settings

import com.lambda.config.Config
import com.lambda.config.Config.SettingLayer
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder

class FunctionSetting<T : () -> R, R>(
	name: String,
	description: String,
	defaultValue: T,
	config: Config,
	layer: SettingLayer.Single<FunctionSetting<T, R>, T>,
	visibility: () -> Boolean
) : Setting<T>(name, description, SettingCore(defaultValue), config, layer, visibility) {
	override fun ImGuiBuilder.buildLayout() {
        button(name) { value() }
        lambdaTooltip(description)
    }
}
