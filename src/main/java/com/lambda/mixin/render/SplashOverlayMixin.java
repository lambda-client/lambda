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

package com.lambda.mixin.render;

import com.lambda.util.LambdaResourceKt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.ReloadableTexture;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.util.function.IntSupplier;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Mutable
    @Shadow
    @Final
    public static Identifier LOGO;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        LOGO = Identifier.of("lambda", "textures/lambda_banner.png");
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int wrapBrandArgb(IntSupplier originalSupplier, Operation<Integer> original) {
        return ColorHelper.getArgb(255, 35, 35, 35);
    }

    @Mixin(SplashOverlay.LogoTexture.class)
    static abstract class LogoTextureMixin extends ReloadableTexture {
        public LogoTextureMixin(Identifier location) {
            super(location);
        }

        @WrapOperation(method = "loadContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ResourceFactory;open(Lnet/minecraft/util/Identifier;)Ljava/io/InputStream;"))
        InputStream wrapLoadTextureData(ResourceFactory instance, Identifier id, Operation<InputStream> original) {
            return LambdaResourceKt.getStream("textures/lambda_banner.png");
        }
    }
}
