package net.hekopdcre.cursedenergy;

import com.mojang.logging.LogUtils;
import net.hekopdcre.cursedenergy.abilities.CursedEnergyAbilities;
import net.hekopdcre.cursedenergy.abilities.DomainExpansionAbility;
import net.hekopdcre.cursedenergy.client.ClientEvents;
import net.hekopdcre.cursedenergy.entity.ModEntities;
import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.hekopdcre.cursedenergy.event.SummonDieEvent;
import net.hekopdcre.cursedenergy.item.ModItems;
import net.hekopdcre.cursedenergy.network.FlattenClientHandler;
import net.hekopdcre.cursedenergy.network.FlattenPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(CursedEnergy.MOD_ID)
public class CursedEnergy {
    public static final String MOD_ID = "ce";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CursedEnergy(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        CursedEnergyAbilities.ABILITIES.register(modEventBus);
        modEventBus.addListener(RikaEntity::onRegisterAttributes);
        NeoForge.EVENT_BUS.register(RandomInitializer.class);
        NeoForge.EVENT_BUS.register(LateDamage.class);
        NeoForge.EVENT_BUS.register(SummonDieEvent.class); // <- evento de morte
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                DomainExpansionAbility.globalTick(level);
            }
        });
        modEventBus.addListener(CursedEnergy::onRegisterPayloads);
        modEventBus.addListener(ClientEvents::registerRenderers);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                FlattenPayload.TYPE,
                FlattenPayload.STREAM_CODEC,
                FlattenClientHandler::handle);
    }
}