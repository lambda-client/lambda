package me.zeroeightsix.kami.module.modules.movement;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;

@Module.Info(name = "ElytraFlightCycle", description = "Cycles through the ElytraFlight modes as a fix for some servers", category = Module.Category.MOVEMENT, showOnArray = Module.ShowOnArray.OFF)
public class ElytraFlightCycle extends Module {
    public void onEnable() {
        ElytraFlight elytraFlight = new ElytraFlight();
        if (elytraFlight.mode.getValue().equals(ElytraFlight.ElytraFlightMode.HIGHWAY)) {
            elytraFlight.mode.setValue(ElytraFlight.ElytraFlightMode.FLY);
            elytraFlight.mode.setValue(ElytraFlight.ElytraFlightMode.HIGHWAY);
        }
        else if (elytraFlight.mode.getValue().equals(ElytraFlight.ElytraFlightMode.FLY)) {
            elytraFlight.mode.setValue(ElytraFlight.ElytraFlightMode.HIGHWAY);
            elytraFlight.mode.setValue(ElytraFlight.ElytraFlightMode.FLY);
        }
        Command.sendChatMessage("[ElytraFlightCycle] Cycled!");
        this.disable();
    }
}
