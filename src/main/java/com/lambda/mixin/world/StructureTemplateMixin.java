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

package com.lambda.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.structure.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {
    @Shadow
    private String author;

    @ModifyReturnValue(method = "getAuthor()Ljava/lang/String;", at = @At("RETURN"))
    public String getAuthor(String original) {
        return Objects.equals(original, "?") || Objects.equals(original, "") ? "unknown" : original;
    }

    @Inject(method = "writeNbt(Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/nbt/NbtCompound;", at = @At("TAIL"))
    public void writeNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        nbt.putString("author", author);
    }

    @Inject(method = "readNbt(Lnet/minecraft/registry/RegistryEntryLookup;Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    public void readNbt(RegistryEntryLookup<Block> blockLookup, NbtCompound nbt, CallbackInfo ci) {
        author = nbt.getString("author", "unknown");
    }
}
