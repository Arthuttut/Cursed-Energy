package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;

import java.util.List;

public class DomainExpansionSerializer extends AbilitySerializer<DomainExpansionAbility> {

        @Override
        public MapCodec<DomainExpansionAbility> codec() {
                return DomainExpansionAbility.CODEC;
        }

        @Override
        public void addDocumentation(
                        CodecDocumentationBuilder<Ability, DomainExpansionAbility> builder,
                        HolderLookup.Provider provider) {
                builder.setDescription(
                                "Expands a cursed domain around the player, replacing nearby blocks with a dome structure.")
                                .addOptional("radius", TYPE_INT, "Radius of the domain sphere in blocks.", 15)
                                .addOptional("height", TYPE_INT, "Maximum height of the dome.", 15)
                                .addOptional("expand_speed", TYPE_DOUBLE, "Expansion speed per tick.", 0.75)
                                .addOptional("restore_speed", TYPE_DOUBLE,
                                                "Restoration speed per tick when deactivated.", 0.85)
                                .addOptional("dome_thickness", TYPE_DOUBLE, "Thickness of the dome wall.", 1.65)
                                .addOptional("restore_batch_size", TYPE_INT,
                                                "Blocks restored per tick when deactivated.", 200)
                                .addOptional("spawn_particles_on_water", TYPE_BOOLEAN,
                                                "Spawn particles when activated underwater.", true)
                                .addOptional("dome_main_block", TYPE_STRING, "Main block of the dome.",
                                                "minecraft:black_concrete")
                                .addOptional("dome_accent_block_1", TYPE_STRING, "First accent block of the dome.",
                                                "minecraft:crying_obsidian")
                                .addOptional("dome_accent_block_2", TYPE_STRING, "Second accent block of the dome.",
                                                "minecraft:blackstone")
                                .addOptional("dome_base_accent_block", TYPE_STRING, "Base accent block of the dome.",
                                                "minecraft:polished_blackstone")
                                .addOptional("floor_main_block", TYPE_STRING, "Main block of the floor.",
                                                "minecraft:black_concrete")
                                .addOptional("floor_accent_block_1", TYPE_STRING, "First accent block of the floor.",
                                                "minecraft:crying_obsidian")
                                .addOptional("floor_accent_block_2", TYPE_STRING, "Second accent block of the floor.",
                                                "minecraft:polished_blackstone")
                                .addExampleObject(new DomainExpansionAbility(
                                                AbilityProperties.BASIC,
                                                AbilityStateManager.EMPTY,
                                                List.of(),
                                                15, 15,
                                                0.75, 0.85, 1.65,
                                                200, true,
                                                new DomainExpansionAbility.BlockConfig(
                                                                "minecraft:black_concrete",
                                                                "minecraft:crying_obsidian",
                                                                "minecraft:blackstone",
                                                                "minecraft:polished_blackstone",
                                                                "minecraft:black_concrete",
                                                                "minecraft:crying_obsidian",
                                                                "minecraft:polished_blackstone")));
        }
}