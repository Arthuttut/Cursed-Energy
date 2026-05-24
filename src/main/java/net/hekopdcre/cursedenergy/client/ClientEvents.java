package net.hekopdcre.cursedenergy.client;

import net.hekopdcre.cursedenergy.entity.client.RikaRenderer;
import net.hekopdcre.cursedenergy.entity.ModEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RIKA.get(), RikaRenderer::new);
    }
}