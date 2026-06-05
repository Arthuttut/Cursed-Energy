package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;

import java.util.List;

public class LimitlessSerializer extends AbilitySerializer<LimitlessAbility> {

        @Override
        public MapCodec<LimitlessAbility> codec() {
                return LimitlessAbility.CODEC;
        }

        @Override
        public void addDocumentation(
                        CodecDocumentationBuilder<Ability, LimitlessAbility> builder,
                        HolderLookup.Provider provider) {
                builder.setDescription(
                                "Creates a distortion field around the player. Any entity or projectile that approaches slows down progressively between 'range' and 'effect', then freezes completely inside 'effect'.")
                                .addOptional("range", TYPE_DOUBLE,
                                                "Radius in blocks where the slowing effect starts.",
                                                5.0)
                                .addOptional("effect", TYPE_DOUBLE,
                                                "Radius in blocks where entities freeze completely. Must be smaller than range.",
                                                1.0)
                                .addOptional("affect_passive", TYPE_BOOLEAN,
                                                "Whether passive entities (animals, etc.) are affected by the distortion field.",
                                                false)
                                .addOptional("affect_hostile", TYPE_BOOLEAN,
                                                "Whether hostile entities (monsters, etc.) are affected by the distortion field.",
                                                true)
                                .addOptional("affect_inanimate", TYPE_BOOLEAN,
                                                "Whether inanimate entities such as projectiles, minecarts and falling blocks are affected.",
                                                true)
                                .addExampleObject(new LimitlessAbility(
                                                AbilityProperties.BASIC,
                                                AbilityStateManager.EMPTY,
                                                List.of(),
                                                10.0, 2.5, false, false, false));
        }
}