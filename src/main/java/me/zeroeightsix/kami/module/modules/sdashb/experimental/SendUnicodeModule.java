package me.zeroeightsix.kami.module.modules.sdashb.experimental;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketChatMessage;

/**
 * @author S-B99
 * Updated by S-B99 on 28/10/19
 */
@Module.Info(name = "SendUnicode", category = Module.Category.MISC, description = "Converts all text into raw unicode")
public class SendUnicodeModule extends Module {

    //private Setting<Boolean> commands = register(Settings.b("Convert", true));

    //private final String KAMI_SUFFIX = " \u23d0 \u1d0b\u1d00\u1d0d\u026a \u0299\u029f\u1d1c\u1d07";

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketChatMessage) {
            String toSend = ((CPacketChatMessage) event.getPacket()).getMessage();
            if (toSend.startsWith("/")) 
            	return;
            else if (toSend.startsWith(",")) 
            	return;
            else if (toSend.startsWith(".")) 
            	return;
            else if (toSend.startsWith("-")) 
            	return;
            
            //toSend = toSend;//KAMI_SUFFIX;
            if (toSend.length() >= 256) 
            	toSend = toSend.substring(0,256);
            ((CPacketChatMessage) event.getPacket()).message = toSend;
        }
    });

}
