package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;

/**
 * Created by S-B99 on 01/12/2019.
 */
public class CreditsCommand extends Command {

    public CreditsCommand() {
        super("credits", null, "creds");
        setDescription("Prints KAMI Blue's authors and contributors");
    }

    @Override
    public void call(String[] args) {
        Command.sendChatMessage("\n" +
                "&l&9Author: \n" +
                "&b086\n" +
                "&l&9Contributors: \n" +
                "&bBella (S-B99)\n" +
                "&bhub (blockparole)\n" +
                "&bSasha (EmotionalLove)\n" +
                "&bQther (d1gress / Vonr)\n" +
                "&bHHSGPA\n" +
                "&b20kdc\n" +
                "&bIronException\n" +
                "&bcats (Cuhnt)\n" +
                "&bKatatje\n" +
                "&bDeauthorized\n" +
                "&bsnowmii\n" +
                "&bkdb424\n" +
                "&Jack (jacksonellsworth03)\n" +
                "&bcookiedragon234\n" +
                "&b0x2E (PretendingToCode)\n" +
                "&bbabbaj\n" +
                "&bZeroMemes\n" +
                "&bTheBritishMidget (TBM)\n" +
                "&bHamburger (Hamburger2k)\n" +
                "&bDarki\n" +
                "&bFINZ0\n" +
                "&bCrystallinqq\n" +
                "&bElementars\n" +
                "&bfsck\n" +
                "&bJamie (jamie27)\n" +
                "&bWaizy\n" +
                "&bIt is the end\n" +
                "&bfluffcq\n" +
                "&bleijurv\n" +
                "&bpolymer");
    }
}
