package club.baldhack.mixin.client;

import club.baldhack.event.events.GuiScreenEvent;
import club.baldhack.BaldHack;
import club.baldhack.capes.Capes;
import club.baldhack.rpc.RPCshit;
import club.baldhack.util.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.crash.CrashReport;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by 086 on 17/11/2017.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    WorldClient world;
    @Shadow
    EntityPlayerSP player;
    @Shadow
    GuiScreen currentScreen;
    @Shadow
    GameSettings gameSettings;
    @Shadow
    GuiIngame ingameGUI;
    @Shadow
    boolean skipRenderWorld;
    @Shadow
    SoundHandler mcSoundHandler;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void initMinecraft(CallbackInfo callbackInfo) {
        new BaldHack();
    }

    @Inject(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;ingameGUI:Lnet/minecraft/client/gui/GuiIngame;", shift = At.Shift.AFTER))
    public void startClient(CallbackInfo ci) {
        BaldHack.getInstance().init();
    }

    @Inject(method = "init", at = @At(value = "RETURN"))
    public void startCapes(CallbackInfo ci) {
        new Capes();
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo info) {
        GuiScreenEvent.Closed screenEvent = new GuiScreenEvent.Closed(Wrapper.getMinecraft().currentScreen);
        BaldHack.EVENT_BUS.post(screenEvent);
        GuiScreenEvent.Displayed screenEvent1 = new GuiScreenEvent.Displayed(guiScreenIn);
        BaldHack.EVENT_BUS.post(screenEvent1);
        guiScreenIn = screenEvent1.getScreen();

        if (guiScreenIn == null && this.world == null)
        {
            guiScreenIn = new GuiMainMenu();
        }
        else if (guiScreenIn == null && this.player.getHealth() <= 0.0F)
        {
            guiScreenIn = new GuiGameOver(null);
        }

        GuiScreen old = this.currentScreen;
        GuiOpenEvent event = new GuiOpenEvent(guiScreenIn);

        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return;

        guiScreenIn = event.getGui();
        if (old != null && guiScreenIn != old)
        {
            old.onGuiClosed();
        }

        if (guiScreenIn instanceof GuiMainMenu || guiScreenIn instanceof GuiMultiplayer)
        {
            this.gameSettings.showDebugInfo = false;
            this.ingameGUI.getChatGUI().clearChatMessages(true);
        }

        this.currentScreen = guiScreenIn;

        if (guiScreenIn != null)
        {
            Minecraft.getMinecraft().setIngameNotInFocus();
            KeyBinding.unPressAllKeys();

            while (Mouse.next())
            {}

            while (Keyboard.next())
            {}

            ScaledResolution scaledresolution = new ScaledResolution(Minecraft.getMinecraft());
            int i = scaledresolution.getScaledWidth();
            int j = scaledresolution.getScaledHeight();
            guiScreenIn.setWorldAndResolution(Minecraft.getMinecraft(), i, j);
            this.skipRenderWorld = false;
        }
        else
        {
            this.mcSoundHandler.resumeSounds();
            Minecraft.getMinecraft().setIngameFocus();
        }

        info.cancel();
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayCrashReport(Lnet/minecraft/crash/CrashReport;)V"))
    public void displayCrashReport(Minecraft minecraft, CrashReport crashReport) {
        save();
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    public void shutdown(CallbackInfo info) {
        save();
    }

    private void save() {
        BaldHack.saveConfiguration();
        System.out.println("config saved.");
    }

}