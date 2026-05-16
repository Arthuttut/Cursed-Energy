package net.hekopdcre.cursedenergy;

import com.mojang.logging.LogUtils;
import net.hekopdcre.cursedenergy.event.ModEvents;
import net.hekopdcre.cursedenergy.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CursedEnergy.MOD_ID)
public class CursedEnergy {

    public static final String MOD_ID = "ce";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CursedEnergy(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);

        NeoForge.EVENT_BUS.register(ModEvents.class);
        NeoForge.EVENT_BUS.register(RandomInitializer.class);
        NeoForge.EVENT_BUS.register(LateDamage.class);
        NeoForge.EVENT_BUS.register(DomainTestHandler.class);
    }
}