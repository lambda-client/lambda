package me.zeroeightsix.kami;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.bewwawho.misc.DiscordSettings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLLog;

import static me.zeroeightsix.kami.KamiMod.APP_ID;

/***
 * @author S-B99
 * Updated by S-B99 on 13/01/20
 */
public class DiscordPresence {
    private static final DiscordRPC rpc;
    public static DiscordRichPresence presence;
    private static boolean hasStarted;
    public static final Minecraft mc = Minecraft.getMinecraft();
    private static String details;
    private static String state;
    private static DiscordSettings discordSettings;
    private static String separator = " | ";

    public static void start() {
        FMLLog.log.info("Starting Discord RPC");
        if (DiscordPresence.hasStarted) return;
        DiscordPresence.hasStarted = true;
        final DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.disconnected = ((var1, var2) -> System.out.println("Discord RPC disconnected, var1: " + String.valueOf(var1) + ", var2: " + var2));
        DiscordPresence.rpc.Discord_Initialize(APP_ID, handlers, true, "");
        DiscordPresence.presence.startTimestamp = System.currentTimeMillis() / 1000L;

        discordSettings = ((DiscordSettings) ModuleManager.getModuleByName("DiscordRPC"));
        details = discordSettings.getLine(discordSettings.line1Setting.getValue()) + " " + discordSettings.getLine(discordSettings.line3Setting.getValue());
        state = discordSettings.getLine(discordSettings.line2Setting.getValue()) + " " + discordSettings.getLine(discordSettings.line4Setting.getValue());

        DiscordPresence.presence.details = details;
        DiscordPresence.presence.state = state;

        DiscordPresence.presence.largeImageKey = "kami";
        DiscordPresence.presence.largeImageText = "bella.wtf/kamiblue";

        DiscordPresence.rpc.Discord_UpdatePresence(DiscordPresence.presence);
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DiscordPresence.rpc.Discord_RunCallbacks();

                    discordSettings = ((DiscordSettings) ModuleManager.getModuleByName("DiscordRPC"));
                    details = discordSettings.getLine(discordSettings.line1Setting.getValue()) + separator + discordSettings.getLine(discordSettings.line3Setting.getValue());
                    state = discordSettings.getLine(discordSettings.line2Setting.getValue()) + separator + discordSettings.getLine(discordSettings.line4Setting.getValue());
                    DiscordPresence.presence.details = details;
                    DiscordPresence.presence.state = state;

                    DiscordPresence.rpc.Discord_UpdatePresence(DiscordPresence.presence);
                }
                catch (Exception e2) { e2.printStackTrace(); }
                try { Thread.sleep(4000L); }
                catch (InterruptedException e3) { e3.printStackTrace(); }
            }
        }, "Discord-RPC-Callback-Handler").start();
        FMLLog.log.info("Discord RPC initialised successfully");
    }

    private static /* synthetic */ void lambdastart1() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DiscordPresence.rpc.Discord_RunCallbacks();

                discordSettings = ((DiscordSettings) ModuleManager.getModuleByName("DiscordRPC"));
                details = discordSettings.getLine(discordSettings.line1Setting.getValue()) + separator + discordSettings.getLine(discordSettings.line3Setting.getValue());
                state = discordSettings.getLine(discordSettings.line2Setting.getValue()) + separator + discordSettings.getLine(discordSettings.line4Setting.getValue());

                DiscordPresence.presence.details = details;
                DiscordPresence.presence.state = state;
                DiscordPresence.rpc.Discord_UpdatePresence(DiscordPresence.presence);
            }
            catch (Exception e2) { e2.printStackTrace(); }
            try { Thread.sleep(4000L); }
            catch (InterruptedException e3) { e3.printStackTrace(); }
        }
    }

    private static /* synthetic */ void lambdastart0(final int var1, final String var2) {
        System.out.println("Discord RPC disconnected, var1: " + var1 + ", var2: " + var2);
    }

    static {
        rpc = DiscordRPC.INSTANCE;
        DiscordPresence.presence = new DiscordRichPresence();
        DiscordPresence.hasStarted = false;
    }
}
