package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.syntax.SyntaxChunk;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;

/**
 * Created by 086 on 18/12/2017.
 */
public class DupeCommand extends Command {
    public DupeCommand() {
        super("dupe", new SyntaxChunk[]{});
    }

    @Override
    public void call(String[] args) {
        if ((Wrapper.getMinecraft().player != null) && (Wrapper.getMinecraft().playerController != null)) {
            Wrapper.getMinecraft().getConnection().sendPacket(new CPacketUseEntity(Wrapper.getPlayer(), EnumHand.MAIN_HAND));
            for(int i = 9; i < 45; i++)
                Wrapper.getMinecraft().playerController.windowClick(0, i, 1, ClickType.THROW,
                        Wrapper.getMinecraft().player);
        }
    }
}
