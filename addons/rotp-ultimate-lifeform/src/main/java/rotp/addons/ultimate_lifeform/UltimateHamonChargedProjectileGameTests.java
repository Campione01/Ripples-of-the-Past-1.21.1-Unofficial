package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonCharge;
import rotp.core.impl.powers.hamon.ProjectileHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModStatusEffects;
import net.minecraft.world.effect.MobEffectInstance;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * A projectile shot by an Ultimate Lifeform with Hamon charge (1.16 KarsUtil.chargeNewEntity) carried the add-on's own
 * ProjectileUltimateHamonChargeCap: 3 x base x 4 raw damage with the plain DamageUtil.HAMON source through
 * invulnerability ticks, only while its owner has the power, with a charge that never wears off.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHamonChargedProjectileGameTests {
    // 3 x (1.5 x 4): ProjectileUltimateHamonChargeCap.getHamonDamage of an arrow
    private static final float ARROW_DAMAGE = 18.0F;
    // 3 x (0.75 x 4) of an egg, 3 x (1.0 x 4) of a water bottle
    private static final float EGG_DAMAGE = 9.0F;
    private static final float WATER_BOTTLE_DAMAGE = 12.0F;

    private UltimateHamonChargedProjectileGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_enderman", timeoutTicks = 40)
    public static void chargedArrowDamagesAnEndermanWithThePlainHamonSource(GameTestHelper helper) {
        Scene scene = new Scene(helper, "enderman");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 1000);
            EnderMan enderman = scene.enderman();
            List<Hit> hits = scene.hamonHits(enderman);
            Arrow arrow = scene.arrow(shooter, 1.0D);
            scene.fly(arrow, 20);
            scene.check(hits.size() == 1 && hits.get(0).plain() && Math.abs(hits.get(0).amount() - ARROW_DAMAGE) < 1.0E-4F
                            && Math.abs(40.0F - enderman.getHealth() - ARROW_DAMAGE) < 1.0E-4F,
                    "1.16 dealt the arrow's charge (18) with DamageUtil.HAMON, which names no entity and which an Enderman takes,"
                            + " but the Hamon hits were " + hits + " and health went 40.0 -> " + enderman.getHealth());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_raw", timeoutTicks = 40)
    public static void chargedArrowDealsTheRawDonorAmountToAnOrdinaryTarget(GameTestHelper helper) {
        Scene scene = new Scene(helper, "raw amount");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 6000);
            UltimateLifeformData data = scene.data(shooter);
            Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
            // a larger hit just before: inside its invulnerability ticks an ordinary hurt() of 18 is refused
            cow.hurt(scene.level.damageSources().generic(), 30.0F);
            scene.check(cow.getHealth() == 70.0F && cow.invulnerableTime > 10,
                    "fixture: the cow must be inside the invulnerability ticks of a 30 damage hit: health=" + cow.getHealth()
                            + " invulnerableTime=" + cow.invulnerableTime);
            List<Hit> hits = scene.hamonHits(cow);
            Arrow arrow = scene.arrow(shooter, 1.0D);
            scene.check(data.getHamonCharge() == 5000, "the arrow takes 1000 of the shooter's Hamon charge, but 6000 became "
                    + data.getHamonCharge());
            scene.fly(arrow, 20);
            scene.check(hits.size() == 1 && hits.get(0).plain() && Math.abs(hits.get(0).amount() - ARROW_DAMAGE) < 1.0E-4F
                            && Math.abs(cow.getHealth() - (70.0F - ARROW_DAMAGE)) < 1.0E-4F,
                    "1.16 passed 3 x 1.5 x 4 = 18 raw to hurtThroughInvulTicks (no x0.2 for a living target),"
                            + " but the Hamon hits were " + hits + " and health went 70.0 -> " + cow.getHealth());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_wear", timeoutTicks = 40)
    public static void chargeOfAnArrowDoesNotWearOff(GameTestHelper helper) {
        Scene scene = new Scene(helper, "no wear-off");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 1000);
            Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
            List<Hit> hits = scene.hamonHits(cow);
            Arrow arrow = scene.arrow(shooter, 0.0D);
            arrow.setNoGravity(true);
            // four times the 10 ticks of the arrow's charge properties, ticked the way the server ticks it
            for (int tick = 0; tick < 40; tick++) {
                scene.level.tickNonPassenger(arrow);
            }
            scene.check(!arrow.isRemoved() && hits.isEmpty() && arrow.position().distanceTo(scene.point(8.5D, 1.0D, 5.5D)) < 0.01D,
                    "fixture: the hovering arrow moved, hit something or was removed: " + arrow.position());
            arrow.setDeltaMovement(0.0D, 0.0D, 1.0D);
            scene.fly(arrow, 20);
            scene.check(hits.size() == 1 && hits.get(0).plain() && Math.abs(hits.get(0).amount() - ARROW_DAMAGE) < 1.0E-4F,
                    "1.16 chargeWearsOff() is false: after 40 ticks the arrow still deals the full 18, but the Hamon hits were "
                            + hits);
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_power", timeoutTicks = 40)
    public static void chargedArrowDealsNothingOnceItsOwnerIsNoUltimateLifeform(GameTestHelper helper) {
        Scene scene = new Scene(helper, "owner without the power");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 1000);
            Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
            List<Hit> hits = scene.hamonHits(cow);
            Arrow arrow = scene.arrow(shooter, 1.0D);
            PlayerPower.get(shooter).setPowerType(null);
            int impacts = scene.fly(arrow, 20);
            scene.check(impacts == 1 && hits.isEmpty() && cow.getHealth() < 100.0F,
                    "1.16 dealt the charge only while the arrow's owner had the Ultimate Lifeform power; impacts=" + impacts
                            + " Hamon hits=" + hits + " health 100.0 -> " + cow.getHealth());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_infused", timeoutTicks = 40)
    public static void arrowOfAnOrganismInfusedShooterDealsNoHamonDamage(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused shooter");
        try {
            scene.build();
            Player infuser = scene.ultimateLifeform(scene.point(10.5D, 0.0D, 4.5D), 0);
            Cow shooter = scene.cow(scene.point(8.5D, 0.0D, 4.5D), 10.0F);
            // the charge of UltimateLifeformCombat.performInfusion (Hamon Organism Infusion)
            UltimateHamonEntityCharge.get(shooter).setHamonCharge(50.0F, 802_183, infuser, 0.0F);
            UltimateHamonCharge infusion = UltimateHamonEntityCharge.get(shooter).getHamonCharge();
            scene.check(infusion != null && infusion.getTicks() == 802_183, "fixture: the shooter carries no infusion");
            Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
            List<Hit> hits = scene.hamonHits(cow);
            Arrow arrow = scene.arrow(shooter, 1.0D);
            int impacts = scene.fly(arrow, 20);
            scene.check(impacts == 1 && hits.isEmpty() && cow.getHealth() < 100.0F,
                    "1.16 gave the arrow of an infused shooter a charge without multiplyWithUserStrength, which deals nothing;"
                            + " impacts=" + impacts + " Hamon hits=" + hits + " health 100.0 -> " + cow.getHealth());
            scene.check(infusion.getTicks() == 0,
                    "1.16 took initialTicks x 1000 / 1000 = 802183 from the shooter's infusion exactly once, but its ticks went 802183 -> "
                            + infusion.getTicks());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_infused_egg", timeoutTicks = 40)
    public static void eggOfAnInfusedShooterTakesTwoTenthsOfTheInfusionOnce(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused shooter egg");
        try {
            scene.build();
            Player infuser = scene.ultimateLifeform(scene.point(10.5D, 0.0D, 4.5D), 0);
            Cow shooter = scene.cow(scene.point(8.5D, 0.0D, 4.5D), 10.0F);
            UltimateHamonEntityCharge.get(shooter).setHamonCharge(50.0F, 802_183, infuser, 0.0F);
            UltimateHamonCharge infusion = UltimateHamonEntityCharge.get(shooter).getHamonCharge();
            scene.check(infusion != null && infusion.getTicks() == 802_183, "fixture: the shooter carries no infusion");
            scene.drop(new ThrownEgg(scene.level, shooter));
            scene.check(infusion.getTicks() == 802_183 - 160_436,
                    "1.16 took initialTicks x 200 / 1000 = 160436 from the shooter's infusion exactly once, but its ticks went 802183 -> "
                            + infusion.getTicks());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_infused_scale", timeoutTicks = 40)
    public static void scaleOfAnInfusedShooterTakesTheInfusionOnce(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused shooter scale");
        try {
            scene.build();
            Player infuser = scene.ultimateLifeform(scene.point(10.5D, 0.0D, 4.5D), 0);
            Cow shooter = scene.cow(scene.point(8.5D, 0.0D, 4.5D), 10.0F);
            UltimateHamonEntityCharge.get(shooter).setHamonCharge(50.0F, 802_183, infuser, 0.0F);
            UltimateHamonCharge infusion = UltimateHamonEntityCharge.get(shooter).getHamonCharge();
            scene.check(infusion != null && infusion.getTicks() == 802_183, "fixture: the shooter carries no infusion");
            UltimateScaleProjectileEntity scale = UltimateLifeformAddon.SCALE_PROJECTILE.get().create(scene.level);
            scene.check(scale != null, "fixture: the registered scale type created no entity");
            scale.initialize(shooter, 5.0F, false, 0.0F, 0.0F);
            scene.add(scale);
            scene.check(infusion.getTicks() == 0,
                    "1.16 took initialTicks x 1000 / 1000 from the infusion of a scale's shooter, once (the core charges no scale),"
                            + " but its ticks went 802183 -> " + infusion.getTicks());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // 1.16: the infusion lives in the add-on's EntityUltimateHamonChargeCap, which the core's infused-shooter branch
    // (HamonUtil 119-160) never reads, so a Hamon user's arrow keeps only the charge of its own Hamon.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_infused_hamon", timeoutTicks = 60)
    public static void arrowOfAnInfusedHamonUserTakesTheInfusionOnce(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused Hamon user");
        ServerPlayer infuser;
        Player shooter;
        try {
            scene.build();
            infuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 4.5D), 500);
            shooter = scene.hamonUser(scene.point(8.5D, 0.0D, 4.5D));
            scene.infuse(infuser, shooter);
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
        helper.runAfterDelay(4, () -> {
            try {
                scene.check(!EntityHamonChargeState.get(shooter).hasHamonCharge(),
                        "Hamon Organism Infusion of an Ultimate Lifeform put a core Hamon charge on the Hamon user");
                UltimateHamonCharge infusion = UltimateHamonEntityCharge.get(shooter).getHamonCharge();
                scene.check(infusion != null && infusion.getDamage() == 50.0F,
                        "fixture: the Hamon user carries no Ultimate infusion");
                int before = infusion.getTicks();
                Arrow arrow = scene.arrow(shooter, 1.0D);
                scene.check(!ProjectileHamonChargeState.get(arrow).hasHamonCharge(),
                        "1.16 gave the arrow of a Hamon user without Hamon energy no core charge from the Ultimate infusion,"
                                + " but it carries " + ProjectileHamonChargeState.get(arrow).getHamonDamage());
                scene.check(infusion.getTicks() == before - 802_183,
                        "the add-on takes the infusion's 802183 ticks for the arrow once, but its ticks went " + before + " -> "
                                + infusion.getTicks());
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_gone_infuser", timeoutTicks = 60)
    public static void infusionStillCountsOnceItsInfuserCanNoLongerBeFound(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infuser gone");
        Cow shooter = scene.startInfusion();
        // the ability runs on the next ticks of its user
        helper.runAfterDelay(4, () -> {
            try {
                UltimateHamonEntityCharge state = UltimateHamonEntityCharge.get(shooter);
                UltimateHamonCharge infusion = state.getHamonCharge();
                scene.check(infusion != null && infusion.getDamage() == 50.0F && infusion.getInitialTicks() == 802_183
                                && infusion.getUser(scene.level) != null,
                        "fixture: Hamon Organism Infusion did not charge the cow through the ability: "
                                + (infusion == null ? "no charge" : infusion.getDamage() + " for " + infusion.getTicks()));
                // the cow saved and loaded while its infuser is offline or in another dimension: only the UUID comes back
                CompoundTag saved = state.serializeNBT(scene.level.registryAccess());
                saved.getCompound("HamonCharge").putUUID("ULFUser", UUID.randomUUID());
                state.deserializeNBT(scene.level.registryAccess(), saved);
                UltimateHamonCharge reloaded = state.getHamonCharge();
                scene.check(reloaded != null && reloaded.getUser(scene.level) == null,
                        "fixture: the reloaded infusion still finds its user");
                int before = reloaded.getTicks();
                Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
                List<Hit> hits = scene.hamonHits(cow);
                Arrow arrow = scene.arrow(shooter, 1.0D);
                int impacts = scene.fly(arrow, 20);
                scene.check(impacts == 1 && hits.isEmpty() && cow.getHealth() < 100.0F,
                        "1.16 kept the infusion on the shooter and gave its arrow a charge that deals nothing, wherever the infuser is;"
                                + " impacts=" + impacts + " Hamon hits=" + hits + " health 100.0 -> " + cow.getHealth());
                scene.check(reloaded.getTicks() == before - 802_183,
                        "the infusion pays the arrow's cost of 802183 ticks once, but its ticks went " + before + " -> "
                                + reloaded.getTicks());
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    // An arrow costs the whole infusion, so the core ends it while the arrow joins; a charge that has already run for
    // a while is then below zero and no longer visible as a charge to a handler that runs after the core's.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_aged_infusion", timeoutTicks = 60)
    public static void arrowOfAShooterWhoseInfusionHasRunForAWhileDealsNoHamonDamage(GameTestHelper helper) {
        Scene scene = new Scene(helper, "aged infusion");
        Cow shooter = scene.startInfusion();
        helper.runAfterDelay(4, () -> {
            try {
                UltimateHamonCharge infusion = UltimateHamonEntityCharge.get(shooter).getHamonCharge();
                scene.check(infusion != null && infusion.getTicks() < 802_183 && infusion.getUser(scene.level) != null,
                        "fixture: the infusion has not run for a while: "
                                + (infusion == null ? "no charge" : infusion.getTicks() + " ticks"));
                int before = infusion.getTicks();
                Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
                List<Hit> hits = scene.hamonHits(cow);
                Arrow arrow = scene.arrow(shooter, 1.0D);
                int impacts = scene.fly(arrow, 20);
                scene.check(impacts == 1 && hits.isEmpty() && cow.getHealth() < 100.0F,
                        "1.16 gave the arrow of an infused shooter a charge that deals nothing, however long the infusion had run;"
                                + " impacts=" + impacts + " Hamon hits=" + hits + " health 100.0 -> " + cow.getHealth());
                scene.check(infusion.getTicks() == before - 802_183,
                        "the infusion pays the arrow's cost of 802183 ticks once, but its ticks went " + before + " -> "
                                + infusion.getTicks());
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_other_charge", timeoutTicks = 60)
    public static void aCoreChargeAfterTheInfusionIsGoneIsNotTreatedAsTheInfusion(GameTestHelper helper) {
        Scene scene = new Scene(helper, "charge after infusion");
        Cow shooter = scene.startInfusion();
        helper.runAfterDelay(4, () -> {
            try {
                scene.check(UltimateHamonEntityCharge.get(shooter).hasHamonCharge(),
                        "fixture: Hamon Organism Infusion did not charge the cow");
                UltimateHamonEntityCharge.get(shooter).setHamonCharge(null);
                EntityHamonChargeState state = EntityHamonChargeState.get(shooter);
                state.setHamonCharge(30.0F, 1000, null, 0.0F);
                HamonCharge charge = state.getHamonCharge();
                Cow cow = scene.cow(scene.point(8.5D, 0.0D, 7.5D), 100.0F);
                List<Hit> hits = scene.hamonHits(cow);
                Arrow arrow = scene.arrow(shooter, 1.0D);
                int impacts = scene.fly(arrow, 20);
                scene.check(impacts == 1 && hits.size() == 1 && charge.getTicks() == 0,
                        "a Hamon charge put on the cow after its infusion ended is an ordinary core charge, which the arrow carries;"
                                + " impacts=" + impacts + " Hamon hits=" + hits + " charge ticks 1000 -> " + charge.getTicks());
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_egg", timeoutTicks = 40)
    public static void chickenOfAChargedEggCarriesTwiceTheEggDamage(GameTestHelper helper) {
        Scene scene = new Scene(helper, "egg");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 6000);
            UltimateLifeformData data = scene.data(shooter);
            long hatchSeed = hatchSeed();
            ThrownEgg egg = new ThrownEgg(scene.level, shooter);
            scene.drop(egg);
            scene.check(data.getHamonCharge() == 5800, "the egg takes 200 of the shooter's Hamon charge, but 6000 became "
                    + data.getHamonCharge());
            int impacts = 0;
            for (int tick = 0; tick < 20 && !egg.isRemoved(); tick++) {
                egg.getRandom().setSeed(hatchSeed);
                impacts = scene.fly(egg, 1);
            }
            List<Chicken> chickens = scene.level.getEntitiesOfClass(Chicken.class, scene.room());
            scene.check(impacts == 1 && egg.isRemoved() && chickens.size() == 1,
                    "fixture: the egg must break on the floor and hatch one chicken; impacts=" + impacts + " chickens=" + chickens);
            scene.check(!EntityHamonChargeState.get(chickens.get(0)).hasHamonCharge(),
                    "1.16 put the chicken's charge in the add-on's own capability, but the chicken carries a core Hamon charge");
            UltimateHamonCharge charge = UltimateHamonEntityCharge.get(chickens.get(0)).getHamonCharge();
            scene.check(charge != null && Math.abs(charge.getDamage() - 2.0F * EGG_DAMAGE) < 1.0E-4F
                            && charge.getInitialTicks() == Integer.MAX_VALUE,
                    "1.16 charged the chicken with the egg's 9 x 2.0 = 18 for Integer.MAX_VALUE ticks, but its charge is "
                            + (charge == null ? "none" : charge.getDamage() + " for " + charge.getInitialTicks() + " ticks"));
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_water", timeoutTicks = 40)
    public static void chargedWaterBottleSplashesTheRawDonorAmount(GameTestHelper helper) {
        Scene scene = new Scene(helper, "water bottle");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 6000);
            UltimateLifeformData data = scene.data(shooter);
            Cow cow = scene.cow(scene.point(9.5D, 0.0D, 7.5D), 100.0F);
            ThrownPotion bottle = new ThrownPotion(scene.level, shooter);
            bottle.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
            float[] expected = new float[1];
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow && event.getSource().is(ModDamageTypes.HAMON)) {
                    double distSqr = cow.distanceToSqr(bottle.getBoundingBox().getCenter());
                    expected[0] = WATER_BOTTLE_DAMAGE * (1.0F - (float) (distSqr / 16.0D));
                }
            });
            List<Hit> hits = scene.hamonHits(cow);
            scene.drop(bottle);
            scene.check(data.getHamonCharge() == 5250, "the water bottle takes 750 of the shooter's Hamon charge, but 6000 became "
                    + data.getHamonCharge());
            int impacts = scene.fly(bottle, 20);
            scene.check(impacts == 1 && hits.size() == 1 && hits.get(0).plain() && expected[0] > 4.0F
                            && Math.abs(hits.get(0).amount() - expected[0]) < 1.0E-4F,
                    "1.16 splashed 12 x (1 - distSqr / 16) raw with DamageUtil.HAMON (" + expected[0]
                            + " at this distance), but the Hamon hits were " + hits + "; impacts=" + impacts);
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // 1.16: the Sendo waves of a Molotov came from the core capability, which an Ultimate Lifeform never charged
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_molotov", timeoutTicks = 40)
    public static void chargedMolotovStartsNoSendoOverdrive(GameTestHelper helper) {
        Scene scene = new Scene(helper, "molotov");
        try {
            scene.build();
            Player shooter = scene.ultimateLifeform(scene.point(8.5D, 0.0D, 4.5D), 6000);
            UltimateLifeformData data = scene.data(shooter);
            MolotovEntity molotov = new MolotovEntity(scene.level, shooter, new ItemStack(ModItems.MOLOTOV.get()));
            scene.drop(molotov);
            scene.check(data.getHamonCharge() == 5500, "the Molotov takes 500 of the shooter's Hamon charge, but 6000 became "
                    + data.getHamonCharge());
            int impacts = scene.fly(molotov, 20);
            List<HamonSendoOverdriveEntity> waves = scene.level.getEntitiesOfClass(HamonSendoOverdriveEntity.class,
                    scene.room().inflate(2.0D));
            scene.check(impacts == 1 && molotov.isRemoved() && waves.isEmpty(),
                    "1.16 ProjectileUltimateHamonChargeCap.onTargetHit has no Molotov branch, but the block hit started "
                            + waves.size() + " Sendo Overdrive entities; impacts=" + impacts);
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // 1.16 UltimateHamonCharge.tick: the infused cow itself hurts what touches it (KarsUtil.dealUltimateHamonDamage,
    // 50 x 0.2 on a living target, then a tenth), and shocks it for 60 ticks because its infuser is an Ultimate Lifeform.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_entity_hit", timeoutTicks = 60)
    public static void infusedCowHitsWithTheUltimateChargeAndShocksForSixtyTicks(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused cow hit");
        Cow infused = scene.startInfusion();
        helper.runAfterDelay(4, () -> {
            Cow target;
            List<Hit> hits;
            try {
                target = scene.cow(scene.point(8.5D, 0.0D, 6.0D), 100.0F);
                // StunEffect.isApplicable: no stun for a mob without AI
                target.setNoAi(false);
                hits = scene.hamonHits(target);
            }
            catch (RuntimeException failure) {
                scene.close();
                throw failure;
            }
            helper.runAfterDelay(3, () -> {
                try {
                    float large = 50.0F * 0.2F * HamonAbilityHelpers.configHamonDamageMultiplier();
                    scene.check(hits.size() >= 2 && Math.abs(hits.get(0).amount() - large) < 1.0E-4F
                                    && Math.abs(hits.get(1).amount() - large * 0.1F) < 1.0E-4F
                                    && hits.get(0).direct() == infused && hits.get(0).causing() == infused,
                            "1.16 hit the cow beside the infused one for " + large + " from the infused cow, then for a tenth,"
                                    + " but the Hamon hits were " + hits);
                    MobEffectInstance shock = target.getEffect(ModStatusEffects.HAMON_SHOCK);
                    UltimateHamonCharge charge = UltimateHamonEntityCharge.get(infused).getHamonCharge();
                    Entity user = charge != null ? charge.getUser(scene.level) : null;
                    scene.check(shock != null && shock.getAmplifier() == 0 && shock.getDuration() > 50 && shock.getDuration() <= 60,
                            "1.16 gave Hamon Shock for 60 ticks on the first hit because the infuser has the Ultimate Lifeform power,"
                                    + " but the shock is " + shock + " (infuser " + user + ", Ultimate Lifeform "
                                    + (user instanceof LivingEntity living && scene.dataOrNull(living) != null) + ")");
                    scene.check(UltimateHamonEntityCharge.get(infused).hasHamonCharge()
                                    && !EntityHamonChargeState.get(infused).hasHamonCharge(),
                            "1.16 kept the infusion in the add-on's own capability, not in the core's Hamon charge");
                }
                finally {
                    scene.close();
                }
                helper.succeed();
            });
        });
    }

    // 1.16 UltimateHamonCharge gave no Hamon points (the core's HamonCharge gives each charge's energy once), and its
    // Hamon Shock needs an infuser with the Ultimate Lifeform power, whatever Hamon skills it has.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_entity_points", timeoutTicks = 80)
    public static void ultimateChargeGivesItsInfuserNoHamonPoints(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infuser points");
        ServerPlayer infuser;
        Cow first;
        Cow second;
        try {
            scene.build();
            infuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 5.5D), 1000);
            first = scene.cow(scene.point(8.5D, 0.0D, 4.5D), 10.0F);
            second = scene.cow(scene.point(8.5D, 0.0D, 6.5D), 10.0F);
            scene.infuse(infuser, first);
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
        helper.runAfterDelay(4, () -> {
            try {
                scene.infuse(infuser, second);
            }
            catch (RuntimeException failure) {
                scene.close();
                throw failure;
            }
            helper.runAfterDelay(4, () -> {
                HamonData hamon;
                int before;
                Cow target;
                List<Hit> hits;
                try {
                    scene.check(UltimateHamonEntityCharge.get(first).hasHamonCharge() || EntityHamonChargeState.get(first).hasHamonCharge(),
                            "fixture: the first cow carries no infusion");
                    scene.check(UltimateHamonEntityCharge.get(second).hasHamonCharge() || EntityHamonChargeState.get(second).hasHamonCharge(),
                            "fixture: the second cow carries no infusion");
                    PlayerPower power = PlayerPower.get(infuser);
                    power.setPowerType(null);
                    power.setPowerType(ModPlayerPowers.HAMON.get());
                    hamon = PlayerPower.getPowerData(infuser, ModPlayerPowers.HAMON).orElse(null);
                    scene.check(hamon != null, "fixture: the infuser did not become a Hamon user");
                    before = hamon.getHamonStrengthPoints();
                    target = scene.cow(scene.point(8.5D, 0.0D, 5.5D), 100.0F);
                    target.setNoAi(false);
                    hits = scene.hamonHits(target);
                }
                catch (RuntimeException failure) {
                    scene.close();
                    throw failure;
                }
                helper.runAfterDelay(3, () -> {
                    try {
                        scene.check(hits.size() >= 2, "fixture: the two infused cows did not both hit the cow between them: " + hits);
                        scene.check(hamon.getHamonStrengthPoints() == before,
                                "1.16 UltimateHamonCharge gave its infuser no Hamon points, but the infuser's Strength points went "
                                        + before + " -> " + hamon.getHamonStrengthPoints());
                        scene.check(target.getEffect(ModStatusEffects.HAMON_SHOCK) == null,
                                "1.16 shocked only when the infuser has the Ultimate Lifeform power, but the target is shocked");
                    }
                    finally {
                        scene.close();
                    }
                    helper.succeed();
                });
            });
        });
    }

    // 1.16 kept the two charges in two capabilities: the core's infusion checked only its own, the add-on's only its
    // own, and the core's Hamon damage spared only an entity with the core's charge.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_entity_separate", timeoutTicks = 60)
    public static void ultimateAndCoreChargesNeitherBlockNorShieldEachOther(GameTestHelper helper) {
        Scene scene = new Scene(helper, "separate charges");
        Cow ultimateOnly;
        Cow coreFirst;
        try {
            scene.build();
            ServerPlayer firstInfuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 4.5D), 500);
            ultimateOnly = scene.cow(scene.point(8.5D, 0.0D, 4.5D), 100.0F);
            ServerPlayer secondInfuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 9.5D), 500);
            coreFirst = scene.cow(scene.point(8.5D, 0.0D, 9.5D), 100.0F);
            EntityHamonChargeState.get(coreFirst).setHamonCharge(30.0F, 1000, null, 0.0F);
            scene.infuse(firstInfuser, ultimateOnly);
            scene.infuse(secondInfuser, coreFirst);
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
        helper.runAfterDelay(4, () -> {
            try {
                scene.check(UltimateHamonEntityCharge.get(coreFirst).hasHamonCharge()
                                && EntityHamonChargeState.get(coreFirst).hasHamonCharge(),
                        "a core Hamon charge kept Hamon Organism Infusion of an Ultimate Lifeform off the cow, or was replaced by it");
                scene.check(!EntityHamonChargeState.get(ultimateOnly).hasHamonCharge(),
                        "the Ultimate infusion put a core Hamon charge on the cow");
                float health = ultimateOnly.getHealth();
                boolean hurt = HamonAbilityHelpers.hamonHurt(ultimateOnly, 10.0F, null, null);
                scene.check(hurt && ultimateOnly.getHealth() < health,
                        "an entity with only the Ultimate charge is not immune to the core's Hamon damage, but the hit was refused");
                EntityHamonChargeState.get(ultimateOnly).setHamonCharge(30.0F, 1000, null, 0.0F);
                scene.check(UltimateHamonEntityCharge.get(ultimateOnly).hasHamonCharge(),
                        "a core Hamon charge put on the cow afterwards removed its Ultimate infusion");
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    // 1.16 KarsUtil.dealUltimateHamonDamage 211-213: every hit of the Ultimate charge on an undead target adds to its
    // Hamon spread, whatever Hamon skills the infuser has; the first 50 reach the fourth step (amplifier 3).
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_entity_spread", timeoutTicks = 60)
    public static void infusedCowSpreadsHamonIntoAnUndeadTargetWithoutHamonSkills(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infused cow spread");
        Cow infused = scene.startInfusion();
        helper.runAfterDelay(4, () -> {
            Zombie zombie;
            List<Hit> hits;
            try {
                UltimateHamonCharge charge = UltimateHamonEntityCharge.get(infused).getHamonCharge();
                Entity user = charge != null ? charge.getUser(scene.level) : null;
                scene.check(user instanceof LivingEntity living
                                && PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).isEmpty()
                                && scene.dataOrNull(living) != null,
                        "fixture: the infuser is not an Ultimate Lifeform without Hamon skills: " + user);
                zombie = scene.zombie(scene.point(8.5D, 0.0D, 6.0D), 500.0F);
                hits = scene.hamonHits(zombie);
            }
            catch (RuntimeException failure) {
                scene.close();
                throw failure;
            }
            helper.runAfterDelay(3, () -> {
                try {
                    scene.check(!hits.isEmpty() && hits.get(0).direct() == infused,
                            "fixture: the infused cow did not hit the zombie: " + hits);
                    MobEffectInstance spread = zombie.getEffect(ModStatusEffects.HAMON_SPREAD);
                    scene.check(spread != null && spread.getAmplifier() == 3,
                            "1.16 spread Hamon into an undead target on every Ultimate charge hit, whatever skills the"
                                    + " infuser has, but after the hits " + hits + " the zombie's Hamon spread is "
                                    + spread);
                }
                finally {
                    scene.close();
                }
                helper.succeed();
            });
        });
    }

    // 1.16 UltimateHamonBlockChargeEntity runs UltimateHamonCharge.tick with the block: with no direct entity
    // KarsUtil.dealUltimateHamonDamage 189 hits with the anonymous DamageUtil.HAMON, not in the infuser's name, and
    // spreads Hamon into an undead target.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_block_hit", timeoutTicks = 60)
    public static void chargedBlockHitsAnonymouslyAndSpreadsHamonIntoUndead(GameTestHelper helper) {
        Scene scene = new Scene(helper, "charged block hit");
        ServerPlayer infuser;
        Zombie zombie;
        List<Hit> hits;
        try {
            scene.build();
            infuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 4.5D), 500);
            UltimateAreaEffectEntity charge = scene.chargeBlock(infuser, scene.point(8.0D, 0.0D, 6.0D));
            zombie = scene.zombie(scene.point(8.5D, 1.0D, 6.5D), 500.0F);
            zombie.setNoGravity(true);
            hits = scene.hamonHits(zombie);
            scene.check(charge.getBlockChargeDamage() == 50.0F, "fixture: the block carries " + charge.getBlockChargeDamage());
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
        helper.runAfterDelay(3, () -> {
            try {
                float large = 50.0F * HamonAbilityHelpers.configHamonDamageMultiplier();
                scene.check(hits.size() == 1 && Math.abs(hits.get(0).amount() - large) < 1.0E-4F
                                && hits.get(0).plain(),
                        "1.16 hit the undead target on the charged block once for " + large
                                + " with the anonymous Hamon source (no direct entity, not in the name of the infuser "
                                + infuser.getGameProfile().getName() + "), but the hits were " + hits);
                scene.check(zombie.getLastHurtByMob() == null,
                        "an anonymous charged-block hit names no attacker, but the zombie was hurt by "
                                + zombie.getLastHurtByMob());
                MobEffectInstance spread = zombie.getEffect(ModStatusEffects.HAMON_SPREAD);
                scene.check(spread != null && spread.getAmplifier() == 3,
                        "1.16 spread Hamon into the undead target of a charged block, but its Hamon spread is " + spread);
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    // 1.16 UltimateHamonCharge gave no Hamon points, so a charged block gives none either, even once its infuser has
    // become a Hamon user (the core's HamonCharge gives the charge's energy once).
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_block_points", timeoutTicks = 60)
    public static void chargedBlockGivesItsInfuserNoHamonPoints(GameTestHelper helper) {
        Scene scene = new Scene(helper, "charged block points");
        Cow target;
        HamonData hamon;
        int before;
        List<Hit> hits;
        try {
            scene.build();
            ServerPlayer infuser = scene.ultimateServerPlayer(scene.point(10.5D, 0.0D, 4.5D), 500);
            // two blocks of one infusion, 2 x 500 energy: the core charge would give a point (750 energy each)
            scene.chargeBlock(infuser, scene.point(8.0D, 0.0D, 6.0D));
            scene.chargeBlock(infuser, scene.point(8.0D, 0.0D, 7.0D));
            PlayerPower power = PlayerPower.get(infuser);
            power.setPowerType(null);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(infuser, ModPlayerPowers.HAMON).orElse(null);
            scene.check(hamon != null, "fixture: the infuser did not become a Hamon user");
            before = hamon.getHamonStrengthPoints();
            target = scene.cow(scene.point(8.5D, 1.0D, 7.0D), 100.0F);
            target.setNoGravity(true);
            hits = scene.hamonHits(target);
        }
        catch (RuntimeException failure) {
            scene.close();
            throw failure;
        }
        helper.runAfterDelay(3, () -> {
            try {
                scene.check(hits.size() == 2, "fixture: the two charged blocks did not each hit the cow on them once: " + hits);
                scene.check(hamon.getHamonStrengthPoints() == before,
                        "1.16 UltimateHamonCharge gave its infuser no Hamon points, but the infuser's Strength points went "
                                + before + " -> " + hamon.getHamonStrengthPoints());
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    // 1.16 UltimateHamonBlockChargeEntity saved its charge as "UltimateHamonCharge" with the ULF keys. Earlier port
    // saves kept a core HamonCharge under "BlockCharge"; such a save loads as the Ultimate charge and saves anew.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blockChargeSavedAsACoreChargeLoadsAsTheUltimateCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID infuser = UUID.fromString("5e0b7c31-94a2-4d6f-8b1e-2c7d9f3a6e48");
        CompoundTag core = new CompoundTag();
        core.putFloat("Charge", 50.0F);
        core.putInt("ChargeTicksInitial", 802_183);
        core.putInt("ChargeTicks", 802_100);
        core.putUUID("HamonUser", infuser);
        core.putBoolean("GavePoints", true);
        core.putFloat("EnergySpent", 500.0F);
        core.putBoolean("LargeDmg", false);
        UltimateAreaEffectEntity old = UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get().create(level);
        helper.assertTrue(old != null, "fixture: no block charge entity");
        CompoundTag saved = new CompoundTag();
        old.saveWithoutId(saved);
        saved.remove("UltimateHamonCharge");
        saved.putLong("ChargedBlock", helper.absolutePos(new BlockPos(2, 2, 2)).asLong());
        saved.put("BlockCharge", core);
        UltimateAreaEffectEntity loaded = UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get().create(level);
        helper.assertTrue(loaded != null, "fixture: no block charge entity");
        loaded.load(saved);
        helper.assertTrue(loaded.getBlockChargeDamage() == 50.0F && loaded.getBlockChargeInitialTicks() == 802_183
                        && loaded.getBlockChargeTicks() == 802_100,
                "the old save did not load its block charge: " + loaded.getBlockChargeDamage() + " for "
                        + loaded.getBlockChargeTicks() + " of " + loaded.getBlockChargeInitialTicks() + " ticks");
        CompoundTag resaved = new CompoundTag();
        loaded.saveWithoutId(resaved);
        CompoundTag charge = resaved.getCompound("UltimateHamonCharge");
        helper.assertTrue(!resaved.contains("BlockCharge") && charge.size() == 6
                        && charge.getFloat("ULFCharge") == 50.0F && charge.getInt("ULFChargeTicksInitial") == 802_183
                        && charge.getInt("ULFChargeTicks") == 802_100 && charge.hasUUID("ULFUser")
                        && charge.getUUID("ULFUser").equals(infuser) && charge.getFloat("ULFEnergySpent") == 500.0F
                        && !charge.getBoolean("ULFLargeDmg"),
                "1.16 saved the block charge as UltimateHamonCharge with the ULF keys, but the loaded old save saved "
                        + resaved);
        helper.succeed();
    }

    // 1.16 EntityUltimateHamonChargeCapStorage saved the charge under the add-on's capability with the ULF keys.
    @GameTest(template = "empty", skyAccess = true, batch = "ulf_hamon_charge_entity_save", timeoutTicks = 60)
    public static void infusionSavesAndLoadsWithTheDonorKeys(GameTestHelper helper) {
        Scene scene = new Scene(helper, "infusion save");
        Cow infused = scene.startInfusion();
        helper.runAfterDelay(4, () -> {
            try {
                CompoundTag saved = new CompoundTag();
                infused.saveWithoutId(saved);
                CompoundTag attachments = saved.getCompound("neoforge:attachments");
                CompoundTag charge = attachments.getCompound(UltimateLifeformAddon.MOD_ID + ":entity_ultimate_hamon")
                        .getCompound("HamonCharge");
                scene.check(charge.size() == 6 && charge.getFloat("ULFCharge") == 50.0F
                                && charge.getInt("ULFChargeTicksInitial") == 802_183
                                && charge.getInt("ULFChargeTicks") > 0 && charge.getInt("ULFChargeTicks") < 802_183
                                && charge.hasUUID("ULFUser") && charge.getFloat("ULFEnergySpent") == 500.0F
                                && charge.getBoolean("ULFLargeDmg"),
                        "1.16 saved the infusion as ULFCharge, ULFChargeTicksInitial, ULFChargeTicks, ULFUser, ULFEnergySpent and"
                                + " ULFLargeDmg, but the saved attachments are " + attachments);
                Cow restored = EntityType.COW.create(scene.level);
                scene.check(restored != null, "fixture: no cow");
                restored.load(saved);
                UltimateHamonCharge back = UltimateHamonEntityCharge.getExisting(restored)
                        .map(UltimateHamonEntityCharge::getHamonCharge).orElse(null);
                scene.check(back != null && back.getDamage() == 50.0F && back.getInitialTicks() == 802_183
                                && back.getTicks() == charge.getInt("ULFChargeTicks"),
                        "the loaded cow did not get its infusion back: "
                                + (back == null ? "none" : back.getDamage() + " for " + back.getTicks() + " ticks"));
            }
            finally {
                scene.close();
            }
            helper.succeed();
        });
    }

    /** The egg's first roll hatches a chicken (nextInt(8) == 0) and its second keeps it at one (nextInt(32) != 0). */
    private static long hatchSeed() {
        for (long seed = 1L; seed < 100_000L; seed++) {
            RandomSource random = RandomSource.create(seed);
            if (random.nextInt(8) == 0 && random.nextInt(32) != 0) {
                return seed;
            }
        }
        throw new IllegalStateException("No seed hatches exactly one chicken");
    }

    private record Hit(float amount, Entity direct, Entity causing) {
        boolean plain() {
            return direct == null && causing == null;
        }

        @Override
        public String toString() {
            return amount + " direct=" + direct + " owner=" + causing;
        }
    }

    /** A stone-floored room in the test origin's chunk, 32 blocks above it; shots fly along +Z at x 8.5. */
    private static final class Scene {
        private final GameTestHelper helper;
        final ServerLevel level;
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final BlockPos from;
        private final BlockPos to;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<Entity> owned = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Runnable> closers = new ArrayList<>();
        private int impacts;
        private Projectile flying;
        private boolean closed;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.name = name;
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(origin);
            this.x = chunk.getMinBlockX();
            this.y = origin.getY() + 32;
            this.z = chunk.getMinBlockZ();
            this.from = new BlockPos(x + 4, y - 1, z + 3);
            this.to = new BlockPos(x + 12, y + 4, z + 13);
        }

        Vec3 point(double dx, double dy, double dz) {
            return new Vec3(x + dx, y + dy, z + dz);
        }

        AABB room() {
            return new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1.0D, to.getY() + 1.0D, to.getZ() + 1.0D);
        }

        void check(boolean condition, String message) {
            helper.assertTrue(condition, "ULF-HAMON-CHARGE " + name + ": " + message);
        }

        void build() {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                check(level.isLoaded(pos) && level.isEmptyBlock(pos), "fixture: room cell is not loaded and empty: " + pos);
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, new BlockPos(to.getX(), y - 1, to.getZ()))) {
                originals.put(pos.immutable(), level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            listen(ProjectileImpactEvent.class, event -> {
                if (event.getProjectile() == flying) {
                    impacts++;
                }
            });
        }

        /** A weightless survival player facing +Z with the Ultimate Lifeform power and this much Hamon charge. */
        Player ultimateLifeform(Vec3 feet, int hamonCharge) {
            Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "HamonChargeShooter")) {
                @Override public boolean isCreative() { return false; }
                @Override public boolean isSpectator() { return false; }
            };
            player.setNoGravity(true);
            player.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            player.setYHeadRot(0.0F);
            add(player);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get());
            closers.add(() -> power.setPowerType(null));
            UltimateLifeformData data = data(player);
            data.addHamonCharge(hamonCharge);
            check(data.getHamonCharge() == hamonCharge, "fixture: the shooter's Hamon charge is " + data.getHamonCharge());
            return player;
        }

        /** A weightless survival player facing +Z with the core Hamon power and no Hamon energy. */
        Player hamonUser(Vec3 feet) {
            Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "HamonChargeUser")) {
                @Override public boolean isCreative() { return false; }
                @Override public boolean isSpectator() { return false; }
            };
            player.setNoGravity(true);
            player.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            player.setYHeadRot(0.0F);
            add(player);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            closers.add(() -> power.setPowerType(null));
            check(PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).isPresent(), "fixture: the shooter has no Hamon power");
            return player;
        }

        /** A real server player with the Ultimate Lifeform power, so that its abilities run through the production input. */
        ServerPlayer ultimateServerPlayer(Vec3 feet, int hamonCharge) {
            ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "HamonChargeInfuser"));
            player.setPos(feet);
            player.setNoGravity(true);
            player.setGameMode(GameType.SURVIVAL);
            add(player);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            check(power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()), "fixture: the power grant failed");
            closers.add(() -> power.setPowerType(null));
            power.updateAvailableMoves();
            data(player).addHamonCharge(hamonCharge);
            return player;
        }

        /** A cow that an Ultimate Lifeform player is infusing: the key press is done, the ability runs on later ticks. */
        Cow startInfusion() {
            try {
                build();
                ServerPlayer infuser = ultimateServerPlayer(point(10.5D, 0.0D, 4.5D), 500);
                Cow shooter = cow(point(8.5D, 0.0D, 4.5D), 10.0F);
                infuse(infuser, shooter);
                return shooter;
            }
            catch (RuntimeException failure) {
                close();
                throw failure;
            }
        }

        /** Hamon Organism Infusion of the infuser on a living target, started the way a key press starts it. */
        void infuse(ServerPlayer infuser, LivingEntity target) {
            LivingComponentAction.getComponent(infuser).entityAim.setTarget(new ActionTarget(target));
            infuser.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            Ability ability = PlayerPower.get(infuser).getAbility(UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id);
            check(ability != null, "fixture: the infuser has no Hamon Organism Infusion");
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            Object held;
            try {
                held = AbilityInput.keyPress((short) 31_017, ability, infuser, extra, InputMethod.CLICK, 0.0F, buffering,
                        ability.getAbilityId());
            }
            finally {
                extra.release();
            }
            check(held != null || buffering.isActionSuccess, "fixture: the production input refused Hamon Organism Infusion");
            AbilityInput.keyRelease((short) 31_017, infuser);
        }

        UltimateLifeformData data(Player player) {
            UltimateLifeformData data = PlayerPower.getPowerData(player, UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElse(null);
            check(data != null, "fixture: the shooter has no Ultimate Lifeform data");
            return data;
        }

        UltimateLifeformData dataOrNull(LivingEntity entity) {
            return PlayerPower.getPowerData(entity, UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElse(null);
        }

        Cow cow(Vec3 feet, float health) {
            Cow cow = EntityType.COW.create(level);
            check(cow != null, "fixture: no cow");
            cow.setNoAi(true);
            cow.setPos(feet);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            cow.setHealth(health);
            return add(cow);
        }

        /** A melon at this lower corner, charged the way Hamon Organism Infusion charges a block. */
        UltimateAreaEffectEntity chargeBlock(LivingEntity infuser, Vec3 corner) {
            BlockPos pos = BlockPos.containing(corner);
            level.setBlockAndUpdate(pos, Blocks.MELON.defaultBlockState());
            UltimateAreaEffectEntity charge = UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get().create(level);
            check(charge != null, "fixture: no block charge");
            charge.initializeBlockCharge(infuser, pos, 50.0F, 802_183, 500.0F);
            return add(charge);
        }

        Zombie zombie(Vec3 feet, float health) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            check(zombie != null, "fixture: no zombie");
            zombie.setNoAi(true);
            zombie.setPos(feet);
            // a helmet keeps it from burning in the sunlit room
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            zombie.setHealth(health);
            return add(zombie);
        }

        /** An Enderman in the line of fire whose teleport always ends in a corner of the room. */
        EnderMan enderman() {
            EnderMan enderman = EntityType.ENDERMAN.create(level);
            check(enderman != null, "fixture: no Enderman");
            enderman.setNoAi(true);
            enderman.setPos(point(8.5D, 0.0D, 7.5D));
            add(enderman);
            Vec3 refuge = point(6.5D, 0.0D, 11.5D);
            listen(EntityTeleportEvent.EnderEntity.class, event -> {
                if (event.getEntity() == enderman) {
                    event.setTargetX(refuge.x);
                    event.setTargetY(refuge.y);
                    event.setTargetZ(refuge.z);
                }
            });
            return enderman;
        }

        /** An arrow one block in front of the shooter's place, added to the level so that the join event charges it. */
        Arrow arrow(LivingEntity owner, double speed) {
            Vec3 start = point(8.5D, 1.0D, 5.5D);
            Arrow arrow = new Arrow(level, start.x, start.y, start.z, new ItemStack(Items.ARROW), null);
            arrow.setOwner(owner);
            arrow.setDeltaMovement(0.0D, 0.0D, speed);
            return add(arrow);
        }

        /** Puts a thrown projectile above the floor two blocks ahead of the shooter, falling. */
        <T extends Projectile> T drop(T projectile) {
            Vec3 start = point(8.5D, 1.0D, 6.5D);
            projectile.setPos(start.x, start.y, start.z);
            projectile.setDeltaMovement(0.0D, -0.5D, 0.0D);
            return add(projectile);
        }

        /** Ticks the projectile as the server does until its first impact; returns the impacts seen. */
        int fly(Projectile projectile, int maxTicks) {
            flying = projectile;
            impacts = 0;
            for (int tick = 0; tick < maxTicks && impacts == 0 && !projectile.isRemoved(); tick++) {
                level.tickNonPassenger(projectile);
            }
            flying = null;
            return impacts;
        }

        List<Hit> hamonHits(LivingEntity target) {
            List<Hit> hits = new ArrayList<>();
            listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == target && event.getSource().is(ModDamageTypes.HAMON)) {
                    hits.add(new Hit(event.getAmount(), event.getSource().getDirectEntity(), event.getSource().getEntity()));
                }
            });
            return hits;
        }

        <T extends Entity> T add(T entity) {
            owned.add(entity);
            check(level.addFreshEntity(entity), "fixture: could not add " + entity);
            return entity;
        }

        <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
            listeners.add(listener);
            NeoForge.EVENT_BUS.addListener(type, listener);
        }

        void close() {
            if (closed) return;
            closed = true;
            listeners.forEach(NeoForge.EVENT_BUS::unregister);
            closers.forEach(Runnable::run);
            for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
            for (Entity entity : level.getEntities((Entity) null, room().inflate(2.0D), entity -> !(entity instanceof Player))) {
                entity.discard();
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (!level.isEmptyBlock(pos)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            originals.forEach(level::setBlockAndUpdate);
        }
    }
}
