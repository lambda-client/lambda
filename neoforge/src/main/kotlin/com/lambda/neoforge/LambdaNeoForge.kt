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

package com.lambda.neoforge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.AgnosticRegistries
import com.lambda.graphics.RenderMain
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(Lambda.MOD_ID)
@OnlyIn(Dist.CLIENT)
object LambdaNeoForge {
    @SubscribeEvent
    fun onClient(event: FMLClientSetupEvent) {
        Lambda.initialize {
            LOG.info("$MOD_NAME NeoForge $VERSION was successfully initialized after $it ms\n")
        }
    }

    @SubscribeEvent
    fun onRegistrySetup(event: RegisterEvent) = AgnosticRegistries.dump(event.registry)

    private object ClientEvents {
        @SubscribeEvent
        fun onHudRender(event: RenderGuiEvent.Post) { RenderMain.render2D() }
    }

    init {
        MOD_BUS.register(this)
        FORGE_BUS.register(ClientEvents)
    }
}
