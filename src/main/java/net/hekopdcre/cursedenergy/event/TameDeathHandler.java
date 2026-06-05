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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.EntityPowerHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

@EventBusSubscriber(modid = "ce")
public class TameDeathHandler {

    private static final Identifier COPY_POWER = Identifier.parse("ce:copy");
    private static final Identifier RIKA_ENTITY_ID = Identifier.parse("ce:rika");
    private static final Identifier RING_ID = Identifier.parse("ce:ring");
    private static final Identifier RIKAS_RING_ID = Identifier.parse("ce:rikas_ring");
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

        EntityPowerHandler powerHandler = PowerUtil.getPowerHandler(player);
        if (powerHandler == null)
            return;
        if (!PowerUtil.hasPower(player, COPY_POWER))
            return;

        // Check if player has ce:ring in the ring curio slot
        boolean hasRing = CuriosApi.getCuriosInventory(player).map(inventory -> {
            var slotResult = inventory.findFirstCurio(stack -> {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                return RING_ID.equals(itemId);
            });
            return slotResult.isPresent();
        }).orElse(false);

        if (!hasRing)
            return;

        // Check if a Rika already exists for this player
        boolean rikaAlreadyExists = serverLevel.getEntitiesOfClass(
                RikaEntity.class,
                player.getBoundingBox().inflate(256),
                r -> player.getUUID().equals(r.getOwnerUUID()) && r.isAlive()).size() > 0;

        if (rikaAlreadyExists)
            return;

        if (serverLevel.getRandom().nextFloat() >= SPAWN_CHANCE)
            return;

        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE
                .getOptional(RIKA_ENTITY_ID).orElse(null);
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
            rikaEntity.setSummonAbilityId(RIKA_ENTITY_ID);
            player.addTag("rika_cursed");
        }

        serverLevel.addFreshEntity(rika);

        // Replace ce:ring with ce:rikas_ring in the ring curio slot
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            ICurioStacksHandler ringHandler = inventory.getCurios().get("ring");
            if (ringHandler == null)
                return;

            var stacks = ringHandler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (RING_ID.equals(itemId)) {
                    stacks.setStackInSlot(i, getRikasRingStack());
                    return;
                }
            }
        });
    }

    private static ItemStack getRikasRingStack() {
        return BuiltInRegistries.ITEM.getOptional(RIKAS_RING_ID)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }
}