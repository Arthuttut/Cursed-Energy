package net.hekopdcre.cursedenergy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Random;

public class RandomInitializer {

    private static final Random RANDOM = new Random();

    private static final String SUPERPOWER_COMMAND_PREFIX = "palladium superpower add ce:";

    private static final String SCORES_INITIALIZED = "ce_scores_initialized";
    private static final String RANDOM_POWER_DONE = "ce_random_start_power_done";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();

        createScoreboardIfMissing(server, "sprint", "Sprint");
        createScoreboardIfMissing(server, "celestial_restriction", "Celestial Restriction");
        createScoreboardIfMissing(server, "cursed_energy", "Cursed Energy");
        createScoreboardIfMissing(server, "sukuna_fingers", "Sukuna Fingers");
        createScoreboardIfMissing(server, "ce_speech_timer", "Cursed Speech Timer");
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.level().getServer();

        if (server == null) {
            return;
        }

        CompoundTag data = player.getPersistentData();

        if (!data.getBoolean(SCORES_INITIALIZED).orElse(false)) {
            data.putBoolean(SCORES_INITIALIZED, true);

            runAsPlayer(server, player, "scoreboard players set @s sprint 0");
            runAsPlayer(server, player, "scoreboard players set @s cursed_energy 0");
            runAsPlayer(server, player, "scoreboard players set @s sukuna_fingers 0");
            runAsPlayer(server, player, "scoreboard players set @s celestial_restriction 0");
            runAsPlayer(server, player, "scoreboard players set @s ce_speech_timer 0");
        }

        if (data.getBoolean(RANDOM_POWER_DONE).orElse(false)) {
            return;
        }

        data.putBoolean(RANDOM_POWER_DONE, true);

        /*
         * Roll 1:
         * 5%  = Restrição Celestial
         * 95% = Feiticeiro com Cursed Energy Control
         */
        double birthRoll = RANDOM.nextDouble();

        if (birthRoll < 0.05) {
            runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "celestial_restriction @s");
            runAsPlayer(server, player, "scoreboard players set @s celestial_restriction 1");
            return;
        }

        /*
         * Feiticeiro sempre ganha Cursed Energy Control.
         */
        runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "cursed_energy_control @s");
        /*
         * Roll raro Six Eyes
         *
         * 0.5% = Six Eyes
         */
        double sixEyesRoll = RANDOM.nextDouble();

        if (sixEyesRoll < 0.005) {
            runAsPlayer(server, player, "tag @s add six_eyes");
            runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "six_eyes @s");
        }

        /*
         * Roll 2 — Técnica extra:
         *
         * 25% Nenhuma extra
         * 30% Boogie Woogie
         * 20% Cursed Speech
         * 15%  Ten Shadows
         * 10%  Limitless
         */
        double extraPowerRoll = RANDOM.nextDouble();

        if (extraPowerRoll < 0.25) {
            // 25% nenhuma técnica extra
            return;
        }

        if (extraPowerRoll < 0.55) {
            // 30% Boogie Woogie
            runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "boogie_woogie @s");
            return;
        }

        if (extraPowerRoll < 0.75) {
            // 20% Cursed Speech
            runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "cursed_speech @s");
            return;
        }

        if (extraPowerRoll < 0.90) {
            // 15% Ten Shadows
            runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "ten_shadows @s");
            return;
        }

// 10% Limitless
        runAsPlayer(server, player, SUPERPOWER_COMMAND_PREFIX + "limitless @s");
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();

        if (oldData.contains(SCORES_INITIALIZED)) {
            newData.putBoolean(
                    SCORES_INITIALIZED,
                    oldData.getBoolean(SCORES_INITIALIZED).orElse(false)
            );
        }

        if (oldData.contains(RANDOM_POWER_DONE)) {
            newData.putBoolean(
                    RANDOM_POWER_DONE,
                    oldData.getBoolean(RANDOM_POWER_DONE).orElse(false)
            );
        }
    }

    private static void createScoreboardIfMissing(MinecraftServer server, String objective, String displayName) {
        Scoreboard scoreboard = server.getScoreboard();

        if (scoreboard.getObjective(objective) != null) {
            return;
        }

        runServerCommand(server, "scoreboard objectives add " + objective + " dummy \"" + displayName + "\"");
    }

    private static void runServerCommand(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput();

        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void runAsPlayer(MinecraftServer server, ServerPlayer player, String command) {
        String playerUuid = player.getUUID().toString();

        runServerCommand(server, "execute as " + playerUuid + " run " + command);
    }
}