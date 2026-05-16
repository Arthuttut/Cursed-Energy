package net.hekopdcre.cursedenergy.item;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CursedEnergy.MOD_ID);

    public static final DeferredItem<Item> SUKUNA_FINGER = ITEMS.registerItem("sukuna_finger",
            properties -> new Item(properties
                    .stacksTo(20)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .food(new FoodProperties.Builder()
                            .nutrition(2)
                            .saturationModifier(0.1F)
                            .alwaysEdible()
                            .build()
                    )
            ) {
                @Override
                public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
                    if (!level.isClientSide() && entity instanceof ServerPlayer player) {
                        MinecraftServer server = level.getServer();

                        if (server != null) {
                            CommandSourceStack source = server.createCommandSourceStack()
                                    .withSuppressedOutput();

                            server.getCommands().performPrefixedCommand(
                                    source,
                                    "execute as " + player.getScoreboardName() + " if score @s sukuna_fingers matches ..19 run scoreboard players add @s sukuna_fingers 1"
                            );
                        }
                    }

                    return super.finishUsingItem(stack, level, entity);
                }
            }
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}