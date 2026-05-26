package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.List;

public class EntityRideAbility extends Ability {

    public static final MapCodec<EntityRideAbility> CODEC = MapCodec.unit(
            new EntityRideAbility(
                    AbilityProperties.BASIC,
                    AbilityStateManager.EMPTY,
                    List.of()));

    public EntityRideAbility(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages) {
        super(properties, stateManager, energyBarUsages);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.ENTITY_RIDE.get();
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> instance, boolean enabled) {
        return false;
    }
}