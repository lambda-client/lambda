/*
 * Copyright 2024 Lambda
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

package com.lambda.forge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.AgnosticRegistries
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS


@Mod(Lambda.MOD_ID)
@OnlyIn(Dist.CLIENT)
object LambdaForge {
    @SubscribeEvent
    fun onClient(event: FMLClientSetupEvent) {
        Lambda.initialize {
            LOG.info("$MOD_NAME Forge $VERSION initialized.")
        }
    }

    // Forge forces the user to use their event in order to interact with registries.
    @SubscribeEvent
    fun onRegistrySetup(event: RegisterEvent) = AgnosticRegistries.dump(event.getVanillaRegistry<Any>())

    // Most events here are hooked due to forge not caring about others
    // and directly patching the minecraft classes.
    private object ClientEvents {
        // @SubscribeEvent
        // fun onHudRender(event: RenderGuiEvent.Post) { RenderMain.render2D() }
    }

    init {
        MOD_BUS.register(this)
        FORGE_BUS.register(ClientEvents)
    }
}
