package net.hekopdcre.cursedenergy.event;

import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.threetag.palladium.power.EntityPowerHandler;
import net.threetag.palladium.power.PowerUtil;

@EventBusSubscriber(modid = "ce")
public class TameDeathHandler {

    private static final Identifier COPY_POWER = Identifier.parse("ce:copy");
    private static final Identifier RIKA_ID = Identifier.parse("ce:rika");
    private static final float SPAWN_CHANCE = 0.4f;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Level level = event.getEntity().level();
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (!(event.getEntity() instanceof TamableAnimal pet))
            return;
        if (!(pet.getOwner() instanceof ServerPlayer player))
            return;

        EntityPowerHandler handler = PowerUtil.getPowerHandler(player);
        if (handler == null)
            return;

        if (!PowerUtil.hasPower(player, COPY_POWER))
            return;

        // checa se já existe uma Rika viva com esse dono
        boolean rikaJaExiste = serverLevel.getEntitiesOfClass(
                RikaEntity.class,
                player.getBoundingBox().inflate(256),
                r -> player.getUUID().equals(r.getOwnerUUID()) && r.isAlive()).size() > 0;

        if (rikaJaExiste)
            return;

        if (serverLevel.getRandom().nextFloat() >= SPAWN_CHANCE)
            return;

        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE
                .getOptional(RIKA_ID).orElse(null);
        if (entityType == null)
            return;

        double x = pet.getX(), y = pet.getY(), z = pet.getZ();
        float yRot = pet.getYRot();

        final LivingEntity[] holder = new LivingEntity[1];
        entityType.create(serverLevel, e -> {
            holder[0] = (LivingEntity) e;
            e.setPos(x, y, z);
            e.setYRot(yRot);
        }, BlockPos.containing(x, y, z), EntitySpawnReason.MOB_SUMMONED, false, false);

        LivingEntity rika = holder[0];
        if (rika == null)
            return;

        if (rika instanceof RikaEntity rikaEntity) {
            rikaEntity.setOwnerUUID(player.getUUID());
        }

        serverLevel.addFreshEntity(rika);
    }
}