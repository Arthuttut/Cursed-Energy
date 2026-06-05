package net.hekopdcre.cursedenergy.abilities.summon;

import net.hekopdcre.cursedenergy.abilities.SummonAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = "cursedenergy")
public class MinionDefenseHandler {

    @SubscribeEvent
    public static void onOwnerHurt(LivingIncomingDamageEvent event) {
        System.out.println("[Minion] OWNER HURT EVENT — entity: "
                + event.getEntity().getType().toShortString());

        if (!(event.getEntity() instanceof Player owner))
            return;
        if (owner.level().isClientSide())
            return;

        var source = event.getContainer().getSource();
        LivingEntity attacker = null;
        if (source.getEntity() instanceof LivingEntity le) {
            attacker = le;
        } else if (source.getDirectEntity() instanceof LivingEntity le) {
            attacker = le;
        }

        System.out.println("[Minion] attacker: "
                + (attacker != null ? attacker.getType().toShortString() : "null"));

        if (attacker == null)
            return;
        notifyMinions(owner, attacker);
    }

    @SubscribeEvent
    public static void onOwnerAttack(AttackEntityEvent event) {
        System.out.println("[Minion] OWNER ATTACK EVENT — entity: "
                + event.getEntity().getType().toShortString());

        if (!(event.getEntity() instanceof Player owner))
            return;
        if (owner.level().isClientSide())
            return;
        if (!(event.getTarget() instanceof LivingEntity target))
            return;
        notifyMinions(owner, target);
    }

    private static void notifyMinions(Player owner, LivingEntity target) {
        if (target == owner || !target.isAlive())
            return;

        AABB box = owner.getBoundingBox().inflate(64.0);
        List<Mob> minions = owner.level().getEntitiesOfClass(Mob.class, box, m -> {
            Optional<UUID> uuid = SummonAbility.readOwnerUUID(m.getPersistentData());
            return uuid.isPresent() && uuid.get().equals(owner.getUUID());
        });

        System.out.println("[Minion] notifyMinions — encontrados: " + minions.size()
                + " | target: " + target.getType().toShortString());

        for (Mob minion : minions) {
            if (target != minion) {
                minion.setTarget(target);
                System.out.println("[Minion] setTarget em: " + minion.getType().toShortString());
            }
        }
    }
}