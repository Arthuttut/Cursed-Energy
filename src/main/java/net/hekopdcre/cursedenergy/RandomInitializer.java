package net.hekopdcre.cursedenergy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Random;

public class RandomInitializer {

    private static final Random RANDOM = new Random();

    private static final String CMD_POWER = "palladium superpower add ce:";
    private static final String SCORES_INIT = "ce_scores_initialized";
    private static final String POWER_DONE = "ce_random_start_power_done";

    private static final String[][] OBJECTIVES = {
            { "sprint", "Sprint" },
            { "celestial_restriction", "Celestial Restriction" },
            { "cursed_energy", "Cursed Energy" },
            { "max_cursed_energy", "Max Cursed Energy" },
            { "sukuna_fingers", "Sukuna Fingers" },
            { "paintings_of_death", "Paintings Of Death" },
            { "ce_speech_timer", "Cursed Speech Timer" },
    };

    // ------------------------------------------------------------------ //
    // EVENTS //
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        for (String[] obj : OBJECTIVES) {
            createScoreboardIfMissing(server, scoreboard, obj[0], obj[1]);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        MinecraftServer server = player.level().getServer();
        if (server == null)
            return;

        CompoundTag data = player.getPersistentData();

        initScores(server, player, data);
        rollPowers(server, player, data);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();

        copyBoolean(oldData, newData, SCORES_INIT);
        copyBoolean(oldData, newData, POWER_DONE);
    }

    // ------------------------------------------------------------------ //
    // LOGIC //
    // ------------------------------------------------------------------ //

    private static void initScores(MinecraftServer server, ServerPlayer player, CompoundTag data) {
        if (data.getBoolean(SCORES_INIT).orElse(false))
            return;
        data.putBoolean(SCORES_INIT, true);

        Scoreboard scoreboard = server.getScoreboard();
        setScore(scoreboard, player, "sprint", 0);
        setScore(scoreboard, player, "cursed_energy", 0);
        setScore(scoreboard, player, "max_cursed_energy", 900);
        setScore(scoreboard, player, "sukuna_fingers", 0);
        setScore(scoreboard, player, "celestial_restriction", 0);
        setScore(scoreboard, player, "paintings_of_death", 0);
        setScore(scoreboard, player, "ce_speech_timer", 0);
    }

    private static void rollPowers(MinecraftServer server, ServerPlayer player, CompoundTag data) {
        if (data.getBoolean(POWER_DONE).orElse(false))
            return;
        data.putBoolean(POWER_DONE, true);

        // 5% Restrição Celestial
        if (RANDOM.nextDouble() < 0.05) {
            givePower(server, player, "celestial_restriction");
            setScore(server.getScoreboard(), player, "celestial_restriction", 1);
            return;
        }

        // Feiticeiro sempre recebe Cursed Energy Control
        givePower(server, player, "cursed_energy_control");

        // 0.5% Six Eyes
        if (RANDOM.nextDouble() < 0.005) {
            givePower(server, player, "six_eyes");
            runCommand(server, "tag " + player.getScoreboardName() + " add six_eyes");
        }

        // Técnica extra
        double roll = RANDOM.nextDouble();

        if (roll < 0.20) {
            /* nenhuma */
        } else if (roll < 0.38) {
            givePower(server, player, "boogie_woogie");
        } else if (roll < 0.53) {
            givePower(server, player, "cursed_speech");
        } else if (roll < 0.65) {
            givePower(server, player, "projection_sorcery");
        } else if (roll < 0.75) {
            givePower(server, player, "vessel");
        } else if (roll < 0.84) {
            givePower(server, player, "copy");
        } else if (roll < 0.91) {
            givePower(server, player, "ten_shadows");
        } else if (roll < 0.97) {
            givePower(server, player, "comedian");
        } else {
            givePower(server, player, "limitless");
        }
    }

    // ------------------------------------------------------------------ //
    // HELPERS //
    // ------------------------------------------------------------------ //

    private static void givePower(MinecraftServer server, ServerPlayer player, String power) {
        runCommand(server, CMD_POWER + power + " " + player.getScoreboardName());
    }

    private static void setScore(Scoreboard scoreboard, ServerPlayer player, String objectiveName, int value) {
        Objective obj = scoreboard.getObjective(objectiveName);
        if (obj == null)
            return;

        ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, obj);
        score.set(value);
    }

    private static void createScoreboardIfMissing(MinecraftServer server, Scoreboard scoreboard,
            String objectiveName, String displayName) {
        if (scoreboard.getObjective(objectiveName) != null)
            return;
        runCommand(server, "scoreboard objectives add " + objectiveName + " dummy \"" + displayName + "\"");
    }

    private static void copyBoolean(CompoundTag from, CompoundTag to, String key) {
        if (from.contains(key)) {
            to.putBoolean(key, from.getBoolean(key).orElse(false));
        }
    }

    private static void runCommand(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }
}