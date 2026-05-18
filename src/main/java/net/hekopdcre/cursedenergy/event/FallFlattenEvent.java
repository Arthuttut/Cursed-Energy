package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.threetag.palladium.power.PowerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CursedEnergy.MOD_ID)
public class FallFlattenEvent {

    private static final Identifier COMEDIAN = Identifier.parse("ce:comedian");

    private static final SoundEvent ANVIL =
            SoundEvent.createVariableRangeEvent(Identifier.parse("ce:anvil"));

    private static final Map<UUID, Integer> FLATTENED_PLAYERS = new HashMap<>();

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        if (!PowerUtil.hasPower(player, COMEDIAN))
            return;

        if (event.getDistance() < 7)
            return;

        event.setDamageMultiplier(0.0F);

        activateFlatten(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        Player player = event.getEntity();

        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        UUID id = player.getUUID();

        if (FLATTENED_PLAYERS.containsKey(id)) {

            int ticks = FLATTENED_PLAYERS.get(id) - 1;

            if (ticks <= 0) {
                FLATTENED_PLAYERS.remove(id);
            } else {
                FLATTENED_PLAYERS.put(id, ticks);
            }
        }

        if (!PowerUtil.hasPower(serverPlayer, COMEDIAN))
            return;

        ServerLevel level = (ServerLevel) serverPlayer.level();

        AABB box = serverPlayer.getBoundingBox().inflate(0.5);

        List<FallingBlockEntity> fallingBlocks =
                level.getEntitiesOfClass(FallingBlockEntity.class, box);

        for (FallingBlockEntity falling : fallingBlocks) {

            if (!falling.getBlockState().is(Blocks.ANVIL))
                continue;

            activateFlatten(serverPlayer);

            falling.discard();
            break;
        }
    }

    private static void activateFlatten(ServerPlayer player) {

        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ANVIL,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );

        FLATTENED_PLAYERS.put(player.getUUID(), 240);
    }

    public static boolean isFlattened(Player player) {
        return FLATTENED_PLAYERS.containsKey(player.getUUID());
    }
}