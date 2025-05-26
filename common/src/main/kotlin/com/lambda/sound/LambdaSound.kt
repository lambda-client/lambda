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

package com.lambda.sound

import com.lambda.util.StringUtils.asIdentifier
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

enum class LambdaSound(val id: Identifier) {
    BUTTON_CLICK("button_click".asIdentifier),

    BOOLEAN_SETTING_ON("bool_on".asIdentifier),
    BOOLEAN_SETTING_OFF("bool_off".asIdentifier),

    MODULE_ON("module_on".asIdentifier),
    MODULE_OFF("module_off".asIdentifier),

    SETTINGS_OPEN("settings_open".asIdentifier),
    SETTINGS_CLOSE("settings_close".asIdentifier);

    val event: SoundEvent = SoundEvent.of(id)
}
