package net.hekopdcre.cursedenergy.abilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import java.util.List;

public class DivergentFistAbility extends Ability {

        public static final MapCodec<DivergentFistAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                        .group(
                                        propertiesCodec(),
                                        stateCodec(),
                                        energyBarUsagesCodec(),
                                        Codec.INT.fieldOf("delay_ticks")
                                                        .orElse(20)
                                                        .forGetter(a -> a.delayTicks),
                                        Codec.DOUBLE.fieldOf("initial_damage")
                                                        .orElse(1.0)
                                                        .forGetter(a -> a.initialDamage),
                                        Codec.DOUBLE.fieldOf("extra_damage")
                                                        .orElse(3.0)
                                                        .forGetter(a -> a.extraDamage),
                                        Codec.INT.fieldOf("particle_color")
                                                        .orElse(0x00CCFF)
                                                        .forGetter(a -> a.particleColor))
                        .apply(instance, DivergentFistAbility::new));

        public final int delayTicks;
        public final double initialDamage;
        public final double extraDamage;
        public final int particleColor;

        public DivergentFistAbility(
                        AbilityProperties properties,
                        AbilityStateManager stateManager,
                        List<EnergyBarUsage> energyBarUsages,
                        Integer delayTicks,
                        Double initialDamage,
                        Double extraDamage,
                        Integer particleColor) {
                super(properties, stateManager, energyBarUsages);
                this.delayTicks = delayTicks;
                this.initialDamage = initialDamage;
                this.extraDamage = extraDamage;
                this.particleColor = particleColor;
        }

        @Override
        public AbilitySerializer<?> getSerializer() {
                return CursedEnergyAbilities.DIVERGENT_FIST.get();
        }
}