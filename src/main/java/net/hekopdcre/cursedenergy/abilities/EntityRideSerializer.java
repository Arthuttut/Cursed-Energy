package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;

import java.util.List;

public class EntityRideSerializer extends AbilitySerializer<EntityRideAbility> {

    @Override
    public MapCodec<EntityRideAbility> codec() {
        return EntityRideAbility.CODEC;
    }

    @Override
    public void addDocumentation(
            CodecDocumentationBuilder<Ability, EntityRideAbility> builder,
            HolderLookup.Provider provider) {

        builder.setDescription("Allows the player to mount and control any entity by right-clicking it.")
                .addOptional("use_whitelist", TYPE_BOOLEAN,
                        "If true, only entities listed in 'whitelist' can be mounted.",
                        false)
                .addOptional("whitelist", TYPE_STRING,
                        "List of entity IDs that can be mounted. Only used when 'use_whitelist' is true. E.g: [\"minecraft:chicken\", \"minecraft:wolf\"].",
                        "[]")
                .addOptional("use_blacklist", TYPE_BOOLEAN,
                        "If true, entities listed in 'blacklist' cannot be mounted.",
                        false)
                .addOptional("blacklist", TYPE_STRING,
                        "List of entity IDs that cannot be mounted. Only used when 'use_blacklist' is true. E.g: [\"minecraft:warden\"].",
                        "[]")
                .addOptional("owner_only", TYPE_BOOLEAN,
                        "If true, the player can only mount an entity if they are its owner.",
                        false)
                .addExampleObject(new EntityRideAbility(
                        AbilityProperties.BASIC,
                        AbilityStateManager.EMPTY,
                        List.of(),
                        true,
                        List.of("minecraft:chicken", "minecraft:wolf"),
                        false,
                        List.of("minecraft:warden"),
                        true));
    }
}