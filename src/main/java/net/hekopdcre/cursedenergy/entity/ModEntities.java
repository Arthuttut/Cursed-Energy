package net.hekopdcre.cursedenergy.entity;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

        public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
                        CursedEnergy.MOD_ID);

        public static final DeferredHolder<EntityType<?>, EntityType<RikaEntity>> RIKA = ENTITIES.register("rika",
                        () -> EntityType.Builder.<RikaEntity>of(
                                        RikaEntity::new,
                                        MobCategory.MONSTER)
                                        .sized(0.8F, 3.7F)
                                        .build(
                                                        ResourceKey.create(
                                                                        Registries.ENTITY_TYPE,
                                                                        Identifier.fromNamespaceAndPath(
                                                                                        CursedEnergy.MOD_ID,
                                                                                        "rika"))));

        public static void register(IEventBus eventBus) {
                ENTITIES.register(eventBus);
        }
}