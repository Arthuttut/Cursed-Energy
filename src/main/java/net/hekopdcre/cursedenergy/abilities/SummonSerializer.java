package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;

import java.util.List;

public class SummonSerializer extends AbilitySerializer<SummonAbility> {

        @Override
        public MapCodec<SummonAbility> codec() {
                return SummonAbility.CODEC;
        }

        @Override
        public void addDocumentation(
                        CodecDocumentationBuilder<Ability, SummonAbility> builder,
                        HolderLookup.Provider provider) {
                builder.setDescription(
                                "Summons any entity relative to the player when the ability is enabled. Discards it when disabled.")
                                .addOptional("entity", TYPE_STRING,
                                                "The entity ID to summon. E.g: \"minecraft:wolf\", \"ce:rika\".",
                                                "ce:rika")
                                .addOptional("spawn_side", TYPE_STRING,
                                                "Side where the entity will spawn relative to the player. Valid values: \"front\", \"back\", \"left\", \"right\".",
                                                "front")
                                .addOptional("spawn_distance", TYPE_DOUBLE,
                                                "Distance in blocks from the player where the entity will spawn.",
                                                2.0)
                                .addOptional("spawn_count", TYPE_INT,
                                                "How many entities to spawn. They will be spread in a line perpendicular to the spawn direction. Default is 1.",
                                                1)
                                .addOptional("spawn_delay_ticks", TYPE_INT,
                                                "How many ticks after activation to wait before spawning. 0 = immediate, 20 = 1 second.",
                                                0)
                                .addOptional("tame", TYPE_BOOLEAN,
                                                "If true, the summoned entity will be owned/tamed by the player.",
                                                false)
                                .addOptional("spawn_particle", TYPE_STRING,
                                                "Particle ID to display at the spawn point while waiting. Only shown if spawn_delay_ticks > 0. E.g: \"minecraft:portal\", \"minecraft:flame\".",
                                                "minecraft:portal")
                                .addOptional("spawn_particle_count", TYPE_INT,
                                                "How many particles to spawn per tick while waiting.",
                                                5)
                                .addOptional("freeze_until_spawn", TYPE_BOOLEAN,
                                                "If true, the player cannot move until the entity finishes spawning.",
                                                false)
                                .addOptional("show_particles", TYPE_BOOLEAN,
                                                "If true, particles will appear at the spawn point during the delay. Requires spawn_delay_ticks > 0.",
                                                true)
                                .addExampleObject(new SummonAbility(
                                                AbilityProperties.BASIC,
                                                AbilityStateManager.EMPTY,
                                                List.of(),
                                                Identifier.parse("ce:rika"),
                                                "front",
                                                2.0,
                                                1,
                                                0,
                                                true,
                                                Identifier.parse("minecraft:portal"),
                                                5,
                                                false,
                                                true));
        }
}