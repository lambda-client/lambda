package me.sdashb.kamiblue;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

// discord imports
import club.minnced.discord.rpc.*;
import com.google.common.hash.Hashing;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.common.FMLLog;
 
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * @author cookiedragon234
 * Updated by S-B99 on 28/10/19
 */
@Module.Info(name = "DiscordRPC", category = Module.Category.MISC, description = "Discord Rich Presence")
public class DiscordKBlueRPC extends Module {

	//1//protected void onEnable() {
	//1//
	//1//	if (isDisabled()) {
	//1//		this.disable();
	//1//		return;
	//1//	}
	//1//}

    private static final String APP_ID = "638403216278683661";
    private static final DiscordKBlueRPC rpc = DiscordKBlueRPC.INSTANCE;
   
    private static DiscordRichPresence presence = new DiscordRichPresence();
   
    private static boolean hasStarted = false;
   
    public static boolean start()
    {
        FMLLog.log.info("Starting Discord RPC");
       
        if(hasStarted) return false;
       
        hasStarted = true;
       
        DiscordEventHandlers handlers = new DiscordEventHandlers();
       
        handlers.disconnected = (int var1, String var2) ->
        {
            System.out.println("Discord RPC disconnected, var1: " + String.valueOf(var1) + ", var2: " + var2);
        };
       
        rpc.Discord_Initialize(APP_ID, handlers, true, "");
       
        presence.startTimestamp = System.currentTimeMillis() / 1000;
        presence.details = "Main Menu";
        presence.state = "discord.gg/ncQkFKU";
        //presence.smallImageKey = "backdoored_logo";
        presence.largeImageKey = "backdoored_logo";
        //presence.spectateSecret = String.valueOf(new Random().nextInt((9000000 - 100000) + 1) + 100000);
        //presence.joinSecret = String.valueOf(new Random().nextInt((9000000 - 100000) + 1) + 100000);
       
        rpc.Discord_UpdatePresence(presence);
       
        new Thread(() ->
        {
            while(!Thread.currentThread().isInterrupted())
            {
               
                try
                {
                    // Run callbacks
                    rpc.Discord_RunCallbacks();
                   
                    String details = "";
                    String state = "";
                    int players = 0;
                    int maxPlayers = 0;
                   
                    // If we're in singleplayer
                    if (Globals.mc.isIntegratedServerRunning())
                    {
                        details = "Singleplayer";
                    }
                    else
                    {
                        if (Globals.mc.getCurrentServerData() != null)
                        {
                            ServerData svr = Globals.mc.getCurrentServerData();
                            if (!svr.serverIP.equals(""))
                            {
                                // If we're on multiplayer
                                details = "Multiplayer";
                                state = svr.serverIP;
                                if(svr.populationInfo != null)
                                {
                                    String[] popInfo = svr.populationInfo.split("/");
                                    if(popInfo.length > 2)
                                    {
                                        players = Integer.valueOf(popInfo[0]);
                                        maxPlayers = Integer.valueOf(popInfo[1]);
                                    }
                                }
                               
                                if(state.contains("2b2t.org"))
                                {
                                    try
                                    {
                                        if(Backdoored.lastChat.startsWith("Position in queue: "))
                                        {
                                            state = state + " " + Integer.parseInt(Backdoored.lastChat.substring(19)) + " in queue";
                                        }
                                    } catch(Throwable e){ e.printStackTrace(); }
                                }
                            }
                        }
                        // If we're in the main menu
                        else
                        {
                            details = "Main Menu";
                            state = "discord.gg/ncQkFKU";
                        }
                    }
                   
                    if(!details.equals(presence.details) || !state.equals(presence.state))
                    {
                        presence.startTimestamp = System.currentTimeMillis() / 1000;
                    }
                   
                    presence.details = details;
                    presence.state = state;
                    //presence.partySize = players;
                    //presence.partyMax = maxPlayers;
                   
                    /*if(players > 0)
                    {
                        presence.partyId = String.valueOf(new Random().nextInt((9000000 - 100000) + 1) + 100000);
                    }*/
                   
                    rpc.Discord_UpdatePresence(presence);
                } catch(Exception e){e.printStackTrace();}
               
                try
                {
                    Thread.sleep(5000);
                }
                catch(InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }, "Discord-RPC-Callback-Handler").start();
        FMLLog.log.info("Discord RPC initialised succesfully");
        return true;
    }
}
