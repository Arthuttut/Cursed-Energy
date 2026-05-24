package net.hekopdcre.cursedenergy.entity.client;

import com.geckolib.renderer.GeoEntityRenderer;
import net.hekopdcre.cursedenergy.entity.custom.RikaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class RikaRenderer extends GeoEntityRenderer<RikaEntity, LivingEntityRenderState> {
    public RikaRenderer(EntityRendererProvider.Context context) {
        super(context, new RikaModel());
    }
}