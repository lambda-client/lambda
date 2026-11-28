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

package com.lambda.mixin.render;

import com.lambda.gui.render.ItemAtlas;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	@WrapOperation(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"), require = 1)
	private Framebuffer drawIntoItemAtlas(MinecraftClient client, Operation<Framebuffer> original) {
		Framebuffer atlas = ItemAtlas.getTarget();
		return atlas != null ? atlas : original.call(client);
	}

	@WrapOperation(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferWidth()I"), require = 1)
	private int itemAtlasWidth(Window window, Operation<Integer> original) {
		Framebuffer atlas = ItemAtlas.getTarget();
		return atlas != null ? atlas.textureWidth : original.call(window);
	}

	@WrapOperation(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getFramebufferHeight()I"), require = 1)
	private int itemAtlasHeight(Window window, Operation<Integer> original) {
		Framebuffer atlas = ItemAtlas.getTarget();
		return atlas != null ? atlas.textureHeight : original.call(window);
	}

	@WrapOperation(method = {"renderPreparedDraws", "getWindowScaleFactor"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;getScaleFactor()I"), require = 1)
	private int itemAtlasScale(Window window, Operation<Integer> original) {
		return ItemAtlas.getTarget() != null ? ItemAtlas.getScale() : original.call(window);
	}
}
