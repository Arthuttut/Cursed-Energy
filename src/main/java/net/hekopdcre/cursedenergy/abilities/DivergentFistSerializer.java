package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import java.util.List;

public class DivergentFistSerializer extends AbilitySerializer<DivergentFistAbility> {

        @Override
        public MapCodec<DivergentFistAbility> codec() {
                return DivergentFistAbility.CODEC;
        }

        @Override
        public void addDocumentation(
                        CodecDocumentationBuilder<Ability, DivergentFistAbility> builder,
                        HolderLookup.Provider provider) {
                builder.setDescription(
                                "Applies initial damage on hit, then delayed extra damage after a set number of ticks.")
                                .addOptional("delay_ticks", TYPE_INT, "Ticks before the delayed damage is applied.", 20)
                                .addOptional("initial_damage", TYPE_FLOAT, "Damage applied immediately on hit.", 1.0F)
                                .addOptional("extra_damage", TYPE_FLOAT, "Extra delayed damage amount.", 3.0F)
                                .addOptional("particle_color", TYPE_INT, "Color of the delayed damage particles.",
                                                0x00CCFF)
                                .addExampleObject(
                                                new DivergentFistAbility(
                                                                AbilityProperties.BASIC,
                                                                AbilityStateManager.EMPTY,
                                                                List.of(),
                                                                20,
                                                                1.0,
                                                                3.0,
                                                                0x00CCFF));
        }
}