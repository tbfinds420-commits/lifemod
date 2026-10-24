package de.lifemod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class LifeMod implements ModInitializer {
    public static final String MOD_ID = "lifemod";

    /** Ein Lebensjahr bis 18: 12000 Ticks = 10 Minuten echte Zeit. */
    public static final int TICKS_PER_YEAR_YOUNG = 12000;
    /** Ein Lebensjahr ab 18: 72000 Ticks = 1 Stunde echte Zeit. */
    public static final int TICKS_PER_YEAR_ADULT = 72000;
    public static final int ADULT_AGE = 18;

    /** Um wie viele Jahre ein Kind pro Lebensjahr der Eltern älter wird. */
    public static final int CHILD_YEARS_PER_PARENT_YEAR = 3;
    /** Kosten pro Kind und Lebensjahr. */
    public static final int CHILD_COST_PER_YEAR = 100;

    public static int ticksPerYear(int age) {
        return age >= ADULT_AGE ? TICKS_PER_YEAR_ADULT : TICKS_PER_YEAR_YOUNG;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LifeCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerLife life = LifeState.get(server).getOrCreate(player.getUuid());
            if (!life.created) {
                player.sendMessage(Text.literal("§aWillkommen! Du wurdest noch nicht geboren. Erstelle dein Leben mit:"), false);
                player.sendMessage(Text.literal("§e/life create <name> <geschlecht> <familie> <talent>"), false);
                player.sendMessage(Text.literal("§7Geschlecht: maennlich, weiblich, divers | Familie: arm, normal, reich | Talent: schlau, musik, glueck"), false);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    private void onTick(MinecraftServer server) {
        LifeState state = LifeState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerLife life = state.getOrCreate(player.getUuid());
            if (!life.created) continue;

            if (life.schoolCooldown > 0) life.schoolCooldown--;
            if (life.workCooldown > 0) life.workCooldown--;

            life.yearTicks++;
            if (life.yearTicks >= ticksPerYear(life.age)) {
                life.yearTicks = 0;
                life.age++;
                player.sendMessage(Text.literal("§eHappy Birthday! Du bist jetzt " + life.age + " Jahre alt."), false);
                if (life.age == 6) {
                    player.sendMessage(Text.literal("§bDu kommst in die Schule! Nutze /life school."), false);
                } else if (life.age == 18) {
                    player.sendMessage(Text.literal("§bDu bist erwachsen! Schau dir /life jobs an."), false);
                }

                if (!life.children.isEmpty()) {
                    long cost = (long) CHILD_COST_PER_YEAR * life.children.size();
                    life.money -= cost;
                    player.sendMessage(Text.literal("§7Deine Kinder haben dich " + cost + " € gekostet."), false);
                    List<String> grown = life.ageChildren(CHILD_YEARS_PER_PARENT_YEAR);
                    for (String g : grown) {
                        player.sendMessage(Text.literal("§d" + g + " ist erwachsen geworden und zieht aus!"), false);
                    }
                }
            }
        }
    }
}
