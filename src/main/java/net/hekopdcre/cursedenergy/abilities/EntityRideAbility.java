package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.hekopdcre.cursedenergy.summon.ISummonedEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EntityRideAbility extends Ability {

    private final boolean useWhitelist;
    private final List<String> whitelist;
    private final boolean useBlacklist;
    private final List<String> blacklist;
    private final boolean ownerOnly;

    public static final MapCodec<EntityRideAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ability.propertiesCodec(),
            Ability.stateCodec(),
            Ability.energyBarUsagesCodec(),

            Codec.BOOL
                    .optionalFieldOf("use_whitelist", false)
                    .forGetter(EntityRideAbility::isUseWhitelist),
            Codec.STRING.listOf()
                    .optionalFieldOf("whitelist", List.of())
                    .forGetter(EntityRideAbility::getWhitelist),

            Codec.BOOL
                    .optionalFieldOf("use_blacklist", false)
                    .forGetter(EntityRideAbility::isUseBlacklist),
            Codec.STRING.listOf()
                    .optionalFieldOf("blacklist", List.of())
                    .forGetter(EntityRideAbility::getBlacklist),

            Codec.BOOL
                    .optionalFieldOf("owner_only", false)
                    .forGetter(EntityRideAbility::isOwnerOnly)

    ).apply(instance, EntityRideAbility::of));

    // ── factory estático para evitar o problema de unboxing do codec ─────────
    public static EntityRideAbility of(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            Boolean useWhitelist,
            List<String> whitelist,
            Boolean useBlacklist,
            List<String> blacklist,
            Boolean ownerOnly) {
        return new EntityRideAbility(properties, stateManager, energyBarUsages,
                useWhitelist, whitelist, useBlacklist, blacklist, ownerOnly);
    }

    public EntityRideAbility(
            AbilityProperties properties,
            AbilityStateManager stateManager,
            List<EnergyBarUsage> energyBarUsages,
            boolean useWhitelist,
            List<String> whitelist,
            boolean useBlacklist,
            List<String> blacklist,
            boolean ownerOnly) {
        super(properties, stateManager, energyBarUsages);
        this.useWhitelist = useWhitelist;
        this.whitelist = whitelist;
        this.useBlacklist = useBlacklist;
        this.blacklist = blacklist;
        this.ownerOnly = ownerOnly;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return CursedEnergyAbilities.ENTITY_RIDE.get();
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> instance, boolean enabled) {
        return false;
    }

    public boolean canRide(LivingEntity entity, LivingEntity target) {
        String entityType = BuiltInRegistries.ENTITY_TYPE
                .getKey(target.getType())
                .toString();

        // ── owner_only ───────────────────────────────────────────────────────
        if (ownerOnly) {
            UUID ownerUUID = null;

            if (target instanceof TamableAnimal tamable) {
                LivingEntity owner = (LivingEntity) tamable.getOwner();
                ownerUUID = owner != null ? owner.getUUID() : null;
            } else if (target instanceof ISummonedEntity summoned) {
                ownerUUID = summoned.getSummonOwner();
            }

            if (ownerUUID == null || !ownerUUID.equals(entity.getUUID())) {
                return false;
            }
        }

        // ── whitelist ────────────────────────────────────────────────────────
        if (useWhitelist) {
            if (!Set.copyOf(whitelist).contains(entityType)) {
                return false;
            }
        }

        // ── blacklist ────────────────────────────────────────────────────────
        if (useBlacklist) {
            if (Set.copyOf(blacklist).contains(entityType)) {
                return false;
            }
        }

        return true;
    }

    public boolean isUseWhitelist() {
        return useWhitelist;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public boolean isUseBlacklist() {
        return useBlacklist;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }
}