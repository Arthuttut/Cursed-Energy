package net.hekopdcre.cursedenergy.abilities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.registry.PalladiumRegistries;

public class CursedEnergyAbilities {
        public static final DeferredRegister<AbilitySerializer<?>> ABILITIES = DeferredRegister
                        .create(PalladiumRegistries.ABILITY_SERIALIZER, "ce");

        public static final DeferredHolder<AbilitySerializer<?>, DomainExpansionSerializer> DOMAIN_EXPANSION = ABILITIES
                        .register("domain_expansion", DomainExpansionSerializer::new);
        public static final DeferredHolder<AbilitySerializer<?>, EntityRideSerializer> ENTITY_RIDE = ABILITIES
                        .register("entity_ride", EntityRideSerializer::new);
        public static final DeferredHolder<AbilitySerializer<?>, DivergentFistSerializer> DIVERGENT_FIST = ABILITIES
                        .register("divergent_fist", DivergentFistSerializer::new);
        public static final DeferredHolder<AbilitySerializer<?>, SwapSerializer> SWAP = ABILITIES
                        .register("swap", SwapSerializer::new);
        public static final DeferredHolder<AbilitySerializer<?>, SummonSerializer> SUMMON = ABILITIES
                        .register("summon", SummonSerializer::new);

        public static void register(IEventBus modEventBus) {
                ABILITIES.register(modEventBus);
        }
}