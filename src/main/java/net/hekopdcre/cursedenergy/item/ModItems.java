package net.hekopdcre.cursedenergy.item;

import net.hekopdcre.cursedenergy.CursedEnergy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.PowerUtil;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CursedEnergy.MOD_ID);

    public static final DeferredItem<Item> SUKUNA_FINGER =
            ITEMS.registerItem("sukuna_finger", props ->
                    new Item(props
                            .stacksTo(20)
                            .rarity(Rarity.EPIC)
                            .fireResistant()
                            .food(new FoodProperties.Builder()
                                    .nutrition(2)
                                    .saturationModifier(0.1F)
                                    .alwaysEdible()
                                    .build())) {

                        @Override
                        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {

                            if (!level.isClientSide() && entity instanceof ServerPlayer player) {

                                if (level.getServer() == null) return super.finishUsingItem(stack, level, entity);

                                if (PowerUtil.hasPower(player, Identifier.parse("ce:vessel"))) {

                                    Scoreboard scoreboard = level.getServer().getScoreboard();
                                    Objective obj = scoreboard.getObjective("sukuna_fingers");

                                    Objective maxCE = scoreboard.getObjective("max_cursed_energy");

                                    if (obj != null && maxCE != null) {
                                        ScoreAccess score    = scoreboard.getOrCreatePlayerScore(player, obj);
                                        ScoreAccess maxScore = scoreboard.getOrCreatePlayerScore(player, maxCE);

                                        int current = score.get();

                                        if (current < 20) {
                                            score.set(current + 1);
                                            int newMax = Math.min(maxScore.get() + 500, 10000);
                                            maxScore.set(newMax);
                                        }
                                    }

                                } else {
                                    player.addEffect(new MobEffectInstance(
                                            MobEffects.POISON,
                                            200,
                                            1
                                    ));
                                }
                            }

                            return super.finishUsingItem(stack, level, entity);
                        }
                    });

    public static final DeferredItem<Item> PAINTING_OF_DEATH =
            ITEMS.registerItem("painting_of_death", props ->
                    new Item(props
                            .stacksTo(9)
                            .rarity(Rarity.RARE)
                            .food(new FoodProperties.Builder()
                                    .nutrition(0)
                                    .saturationModifier(0.0F)
                                    .alwaysEdible()
                                    .build())) {

                        @Override
                        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {

                            if (!level.isClientSide() && entity instanceof ServerPlayer player) {

                                if (level.getServer() == null) return super.finishUsingItem(stack, level, entity);

                                if (!PowerUtil.hasPower(player, Identifier.parse("ce:vessel"))) {
                                    player.addEffect(new MobEffectInstance(
                                            MobEffects.POISON,
                                            200,
                                            1
                                    ));
                                    return super.finishUsingItem(stack, level, entity);
                                }

                                Scoreboard scoreboard = level.getServer().getScoreboard();

                                Objective paintings = scoreboard.getObjective("paintings_of_death");
                                Objective maxCE = scoreboard.getObjective("max_cursed_energy");

                                if (paintings != null && maxCE != null) {

                                    ScoreAccess pScore =
                                            scoreboard.getOrCreatePlayerScore(player, paintings);

                                    ScoreAccess maxScore =
                                            scoreboard.getOrCreatePlayerScore(player, maxCE);

                                    int current = pScore.get();

                                    if (current < 9) {
                                        pScore.set(current + 1);
                                        maxScore.set(maxScore.get() + 150);
                                    }
                                }
                            }

                            return super.finishUsingItem(stack, level, entity);
                        }
                    });

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}