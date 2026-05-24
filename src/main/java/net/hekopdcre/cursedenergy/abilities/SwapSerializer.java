package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import java.util.List;

public class SwapSerializer extends AbilitySerializer<SwapAbility> {

        @Override
        public MapCodec<SwapAbility> codec() {
                return SwapAbility.CODEC;
        }

        @Override
        public void addDocumentation(
                        CodecDocumentationBuilder<Ability, SwapAbility> builder,
                        HolderLookup.Provider provider) {
                builder.setDescription(
                                "When activated, teleports the caster to the nearest entity in their line of sight, and that entity to the caster's original position. "
                                                +
                                                "The caster's rotation after the swap is controlled by 'invert_view': when true the caster is flipped 180° (original behaviour); "
                                                +
                                                "when false the caster keeps their original look direction. A custom sound can be set via 'teleport_sound'. "
                                                +
                                                "If 'play_sound_without_target' is true, the sound will also play at the caster's position even when no valid swap target is found.")
                                .addOptional("range", TYPE_INT,
                                                "Maximum distance in blocks to find a swap target.", 10)
                                .addOptional("invert_view", TYPE_BOOLEAN,
                                                "If true, the caster's yaw is rotated 180° and pitch inverted after the swap (original behaviour). "
                                                                +
                                                                "If false, the caster keeps looking in the same direction they were before the swap.",
                                                true)
                                .addOptional("teleport_sound", TYPE_STRING,
                                                "ResourceLocation of the sound played at both teleport positions (e.g. \"minecraft:entity.enderman.teleport\"). "
                                                                +
                                                                "Falls back to the Enderman teleport sound if the ID is invalid.",
                                                "minecraft:entity.enderman.teleport")
                                .addOptional("play_sound_without_target", TYPE_BOOLEAN,
                                                "If true, the configured teleport sound is played at the caster's position even when no valid swap target is found. "
                                                                +
                                                                "If false (default), no sound is played when the ability finds no target.",
                                                false)
                                .addExampleObject(
                                                new SwapAbility(
                                                                AbilityProperties.BASIC,
                                                                AbilityStateManager.EMPTY,
                                                                List.of(),
                                                                10,
                                                                true,
                                                                "minecraft:entity.enderman.teleport",
                                                                false));
        }
}