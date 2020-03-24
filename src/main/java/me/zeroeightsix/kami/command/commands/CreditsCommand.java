package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;

/**
 * Created by S-B99 on 01/12/2019.
 */
public class CreditsCommand extends Command {

    public CreditsCommand() {
        super("credits", null);
        setDescription("Prints KAMI Blue's authors and contributors");
    }

    @Override
    public void call(String[] args) {
        Command.sendChatMessage("\n" +
                "&l&9Author:\n" +
                "086\n" +
                "&l&9Contributors:\n" +
                "Bella (S-B99)\n" +
                "hub (blockparole)\n" +
                "Sasha (EmotionalLove)\n" +
                "Qther (d1gress / Vonr)\n" +
                "HHSGPA\n" +
                "20kdc\n" +
                "IronException\n" +
                "cats (Cuhnt)\n" +
                "Katatje\n" +
                "Deauthorized\n" +
                "snowmii\n" +
                "kdb424\n" +
                "Jack (jacksonellsworth03)\n" +
                "cookiedragon234\n" +
                "0x2E (PretendingToCode)\n" +
                "babbaj\n" +
                "ZeroMemes\n" +
                "TheBritishMidget (TBM)\n" +
                "Hamburger (Hamburger2k)\n" +
                "Darki\n" +
                "Crystallinqq\n" +
                "Elementars\n" +
                "fsck\n" +
                "Jamie (jamie27)\n" +
                "Waizy\n" +
                "It is the end\n" +
                "fluffcq\n" +
                "leijurv\n" +
                "polymer");
    }
}
