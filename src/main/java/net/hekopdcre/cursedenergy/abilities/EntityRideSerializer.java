package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilitySerializer;

public class EntityRideSerializer extends AbilitySerializer<EntityRideAbility> {

    @Override
    public MapCodec<EntityRideAbility> codec() {
        return EntityRideAbility.CODEC;
    }

    @Override
    public void addDocumentation(
            CodecDocumentationBuilder<Ability, EntityRideAbility> builder,
            HolderLookup.Provider provider
    ) {
        builder.setDescription(
                "Allows the player to mount and control any entity by right-clicking it."
        );
    }
}