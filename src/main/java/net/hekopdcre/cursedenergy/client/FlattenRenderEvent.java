package net.hekopdcre.cursedenergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.hekopdcre.cursedenergy.CursedEnergy;
import net.hekopdcre.cursedenergy.event.FallFlattenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = CursedEnergy.MOD_ID, value = Dist.CLIENT)
public class FlattenRenderEvent {

    @SubscribeEvent
    @SuppressWarnings("rawtypes")
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null)
            return;

        if (!FallFlattenEvent.isFlattened(player))
            return;

        PoseStack poseStack = event.getPoseStack();

        poseStack.scale(1.5F, 0.2F, 1.5F);
    }
}