package me.zeroeightsix.kami.command.commands;

import me.zeroeightsix.kami.command.Command;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.AbstractHorse;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Created by d1gress/Qther on 25/11/2017.
 */

public class EntityStatsCommand extends Command {

    public EntityStatsCommand() {
        super("entitystats", null);
        setDescription("Print the statistics of the entity you're currently riding");
    }

    @Override
    public void call(String[] args) {
        String maxHealth;
        String speed;
        if (mc.player.getRidingEntity() != null && mc.player.getRidingEntity() instanceof AbstractHorse) {
            // EntityHorse, EntityMule, etc etc all extend AbstractHorse
            // no need to check if the riding entity is an instanceof everything @d1gress
            AbstractHorse h = (AbstractHorse) mc.player.getRidingEntity();

            maxHealth = h.getMaxHealth() + " \2472HP";
            speed = round(43.17 * h.getAIMoveSpeed(), 2) + " \2472m/s";
            String jumpHeight = round(-0.1817584952 * Math.pow(h.getHorseJumpStrength(), 3) + 3.689713992 * Math.pow(h.getHorseJumpStrength(), 2) + 2.128599134 * h.getHorseJumpStrength() - 0.343930367, 4) + " \2472m";
            String tamer = h.getOwnerUniqueId() == null ? "Not tamed." : h.getOwnerUniqueId().toString();
            // TODO: Function that resolves UUID's to Minecraft usernames. @bella
            Command.sendChatMessage("\2476Entity Stats:\n\247cMax Health: \247b" + maxHealth + "\n\247cSpeed: \247b" + speed + "\n\247cJump: \247b" + jumpHeight + "\n\247cOwner UUID: \247b" + tamer);
        } else if (mc.player.getRidingEntity() instanceof EntityLivingBase) {
            EntityLivingBase l = (EntityLivingBase) mc.player.getRidingEntity();
            maxHealth = l.getMaxHealth() + " \2472HP";
            speed = round(43.17 * l.getAIMoveSpeed(), 2) + " \2472m/s";
            Command.sendChatMessage("\2476Entity Stats:\n\247cMax Health: \247b" + maxHealth + "\n\247cSpeed: \247b" + speed);
        } else {
            Command.sendChatMessage("\2474\247lERROR: \247cNot riding a compatible entity.");
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
