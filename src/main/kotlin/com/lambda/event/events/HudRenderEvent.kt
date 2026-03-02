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

package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.client.gui.DrawContext

/**
 * Event fired during HUD rendering with access to Minecraft's DrawContext.
 * Use this for rendering items, textures, and other GUI elements that need
 * to integrate with Minecraft's deferred GUI rendering system.
 */
class HudRenderEvent(val context: DrawContext) : Event
