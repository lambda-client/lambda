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

package com.lambda.mixin;

import com.lambda.Lambda;
import com.lambda.util.DynamicException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Modify the crash report behavior for dynamic remapping and Easter egg
@Mixin(CrashReport.class)
public class CrashReportMixin {
    @Mutable
    @Shadow @Final private Throwable cause;

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("TAIL"))
    void injectConstructor(String message, Throwable cause, CallbackInfo ci) {
        if (!Lambda.INSTANCE.isDebug() && MinecraftClient.getInstance() != null) {
            this.cause = new DynamicException(cause);
        }
    }

    @Inject(method = "generateWittyComment()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void generateWittyComment(CallbackInfoReturnable<String> cir) {
        String[] strings = new String[]{
                "Who set us up the TNT?",
                "Everything's going to plan. No, really, that was supposed to happen.",
                "Uh... Did I do that?",
                "Oops.",
                "Why did you do that?",
                "I feel sad now :(",
                "My bad.",
                "I'm sorry, Dave.",
                "I let you down. Sorry :(",
                "On the bright side, I bought you a teddy bear!",
                "Daisy, daisy...",
                "Oh - I know what I did wrong!",
                "Hey, that tickles! Hehehe!",
                "I blame Dinnerbone.",
                "You should try our sister game, Minceraft!",
                "Don't be sad. I'll do better next time, I promise!",
                "Don't be sad, have a hug! <3",
                "I just don't know what went wrong :(",
                "Shall we play a game?",
                "Quite honestly, I wouldn't worry myself about that.",
                "I bet Cylons wouldn't have this problem.",
                "Sorry :(",
                "Surprise! Haha. Well, this is awkward.",
                "Would you like a cupcake?",
                "Hi. I'm Minecraft, and I'm a crashaholic.",
                "Ooh. Shiny.",
                "This doesn't make any sense!",
                "Why is it breaking :(",
                "Don't do that.",
                "Ouch. That hurt :(",
                "You're mean.",
                "This is a token for 1 free hug. Redeem at your nearest Mojangsta: [~~HUG~~]",
                "There are four lights!",
                "But it works on my machine.",
                "Popbob was here.",
                "The oldest anarchy server in Minecraft."
        };

        try {
            cir.setReturnValue(strings[(int)(Util.getMeasuringTimeNano() % (long)strings.length)]);
        } catch (Throwable var2) {
            cir.setReturnValue("Witty comment unavailable :(");
        }
    }
}
