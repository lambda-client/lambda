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

import com.lambda.module.modules.network.AutoReconnect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private DirectionalLayoutWidget grid;
    @Shadow @Final private Screen parent;

    @Unique
    private ButtonWidget lambda$reconnectButton;

    @Unique
    private long lambda$autoReconnectTime = -1L;

    @Unique
    private boolean lambda$reconnected = false;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;isMultiplayerEnabled()Z"))
    private void addReconnectButton(CallbackInfo ci) {
        lambda$reconnected = false;

        boolean hasTarget = AutoReconnect.getLastReconnectTarget() != null;
        if (AutoReconnect.canAutoReconnect()) {
            lambda$autoReconnectTime = System.currentTimeMillis() + (long) (AutoReconnect.INSTANCE.getDelay() * 1000.0);
        } else {
            lambda$autoReconnectTime = -1L;
        }

        lambda$reconnectButton = ButtonWidget.builder(lambda$getButtonText(), button -> {
            lambda$reconnected = true;
            AutoReconnect.reconnect(this.parent);
        }).width(200).build();

        lambda$reconnectButton.active = hasTarget;
        this.grid.add(lambda$reconnectButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        if (lambda$reconnectButton == null || lambda$reconnected) return;

        if (AutoReconnect.canAutoReconnect() && lambda$autoReconnectTime > 0) {
            long remainingMillis = lambda$autoReconnectTime - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                lambda$reconnected = true;
                AutoReconnect.reconnect(this.parent);
            } else {
                double remainingSeconds = remainingMillis / 1000.0;
                lambda$reconnectButton.setMessage(Text.literal(String.format(Locale.ROOT, "Reconnect (%.1fs)", remainingSeconds)));
            }
        } else {
            lambda$reconnectButton.setMessage(Text.literal("Reconnect"));
        }
    }

    @Override
    public void removed() {
        super.removed();
        lambda$autoReconnectTime = -1L;
        lambda$reconnected = true;
    }

    @Unique
    private Text lambda$getButtonText() {
        if (AutoReconnect.canAutoReconnect() && lambda$autoReconnectTime > 0) {
            long remainingMillis = Math.max(0, lambda$autoReconnectTime - System.currentTimeMillis());
            double remainingSeconds = remainingMillis / 1000.0;
            return Text.literal(String.format(Locale.ROOT, "Reconnect (%.1fs)", remainingSeconds));
        }
        return Text.literal("Reconnect");
    }
}
