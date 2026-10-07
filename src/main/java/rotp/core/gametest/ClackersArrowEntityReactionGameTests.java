package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;

/** Reactions vanilla entities keep for arrows, which the 1.16.5 Clackers were. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersArrowEntityReactionGameTests {
    private ClackersArrowEntityReactionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_closed_shulker", timeoutTicks = 120)
    public static void closedShulkerDeflectsClackers(GameTestHelper helper) {
        ClackersScene.start(helper, "closed-shulker", 100, scene -> {
            scene.stone(8, 0, 6, 8, 0, 6);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Shulker shulker = EntityType.SHULKER.create(scene.level);
            shulker.setNoAi(true);
            shulker.setPos(scene.point(8.5D, 1.0D, 6.5D));
            scene.add(shulker);
            float health = shulker.getHealth();
            scene.throwClackers(user);
            scene.await("the contact with the shulker", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == shulker && shulker.isAlive()
                        && shulker.saveWithoutId(new CompoundTag()).getByte("Peek") == 0, "fixture: not one contact with a closed shulker");
                scene.check(shulker.getHealth() == health && !contact.post().boomerang(),
                        "1.16: Clackers are arrows, and a closed shulker deflects an arrow; health " + health + " -> " + shulker.getHealth()
                                + ", hit counted as landed: " + contact.post().boomerang());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_armor_stand", timeoutTicks = 120)
    public static void clackersBreakAnArmorStandAtOnce(GameTestHelper helper) {
        ClackersScene.start(helper, "armor-stand", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            ArmorStand stand = EntityType.ARMOR_STAND.create(scene.level);
            stand.setNoGravity(true);
            stand.setPos(scene.point(8.5D, 0.0D, 6.5D));
            scene.add(stand);
            scene.throwClackers(user);
            scene.await("the contact with the armour stand", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == stand,
                        "fixture: not one contact with the armour stand");
                List<ItemEntity> drops = scene.level.getEntitiesOfClass(ItemEntity.class, scene.room,
                        item -> item.getItem().is(Items.ARMOR_STAND));
                scene.check(stand.isRemoved() && drops.size() == 1, "1.16: Clackers are arrows, and one arrow breaks an armour stand;"
                        + " broken: " + stand.isRemoved() + ", armour stand items dropped: " + drops.size());
            });
        });
    }

    /** Clackers without a thrower carry no Hamon hit, so the stand takes exactly one hit. */
    @GameTest(template = "empty", skyAccess = true, batch = "clackers_armor_stand_one_hit", timeoutTicks = 80)
    public static void oneClackersHitIsEnoughToBreakAnArmorStand(GameTestHelper helper) {
        ClackersScene.start(helper, "armor-stand-one-hit", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            ArmorStand stand = EntityType.ARMOR_STAND.create(scene.level);
            stand.setNoGravity(true);
            stand.setPos(scene.point(8.5D, 0.0D, 6.5D));
            scene.add(stand);
            scene.then(() -> scene.summon(spawn, "{Motion:[0.0d,0.0d,2.0d]}"));
            scene.await("the summoned Clackers hitting the armour stand", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == stand
                        && scene.shots.get(0).getOwner() == null, "fixture: not one contact of ownerless Clackers with the armour stand");
                List<ItemEntity> drops = scene.level.getEntitiesOfClass(ItemEntity.class, scene.room,
                        item -> item.getItem().is(Items.ARMOR_STAND));
                scene.check(stand.isRemoved() && drops.size() == 1, "1.16: one arrow hit breaks an armour stand at once;"
                        + " broken: " + stand.isRemoved() + ", armour stand items dropped: " + drops.size());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_perched_dragon", timeoutTicks = 120)
    public static void perchedDragonBurnsClackersAndTakesNoDamage(GameTestHelper helper) {
        ClackersScene.start(helper, "perched-dragon", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            EnderDragon dragon = EntityType.ENDER_DRAGON.create(scene.level);
            dragon.setNoAi(true);
            dragon.setPos(scene.point(8.5D, 0.0D, 10.5D));
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
            scene.add(dragon);
            // a dragon without AI leaves its parts where they are put
            dragon.head.setPos(user.getX(), user.getEyeY() - 0.1D - 0.5D, user.getZ() + 3.0D);
            float health = dragon.getHealth();
            scene.throwClackers(user);
            scene.await("the contact with the dragon's head", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == dragon.head
                        && dragon.getPhaseManager().getCurrentPhase().isSitting(), "fixture: not one contact with the head of a perched dragon");
                int fire = contact.post().tag().getShort("Fire");
                scene.check(dragon.getHealth() == health && fire == 20, "1.16: Clackers are arrows, and a perched dragon takes no damage from"
                        + " an arrow and sets it on fire for a second; health " + health + " -> " + dragon.getHealth()
                        + ", Clackers fire ticks " + fire);
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_armoured_wither", timeoutTicks = 120)
    public static void armouredWitherRefusesClackers(GameTestHelper helper) {
        ClackersScene.start(helper, "armoured-wither", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            WitherBoss wither = EntityType.WITHER.create(scene.level);
            wither.setNoAi(true);
            wither.setNoGravity(true);
            wither.setPos(scene.point(8.5D, 0.0D, 6.5D));
            scene.add(wither);
            wither.setHealth(wither.getMaxHealth() / 2.0F);
            float health = wither.getHealth();
            scene.throwClackers(user);
            scene.await("the contact with the wither", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == wither && wither.isAlive()
                        && wither.isPowered() && wither.getInvulnerableTicks() == 0 && contact.pre().tag().getFloat("HamonDamage") > 0.0F,
                        "fixture: not one contact of Hamon Clackers with an armoured wither");
                scene.check(wither.getHealth() == health && !contact.post().boomerang(),
                        "1.16: Clackers are arrows, and a wither at half health refuses every arrow hit; health " + health + " -> "
                                + wither.getHealth() + ", hit counted as landed: " + contact.post().boomerang());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_burning_tnt_minecart", timeoutTicks = 80)
    public static void burningClackersExplodeATntMinecartAtOnce(GameTestHelper helper) {
        tntMinecart(helper, "burning-tnt-minecart", true);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_cold_tnt_minecart", timeoutTicks = 80)
    public static void clackersThatDoNotBurnLeaveATntMinecartWhole(GameTestHelper helper) {
        tntMinecart(helper, "cold-tnt-minecart", false);
    }

    private static void tntMinecart(GameTestHelper helper, String name, boolean burning) {
        ClackersScene.start(helper, name, 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            MinecartTNT cart = EntityType.TNT_MINECART.create(scene.level);
            cart.setNoGravity(true);
            cart.setPos(spawn.x, spawn.y + 0.25D - 0.35D, spawn.z + 2.0D);
            scene.add(cart);
            List<Explosion> explosions = new ArrayList<>();
            scene.listen(ExplosionEvent.Start.class, event -> {
                if (event.getLevel() == scene.level && scene.room.contains(event.getExplosion().center())) {
                    explosions.add(event.getExplosion());
                }
            });
            // slow enough that the hit alone does not break the minecart
            scene.then(() -> scene.summon(spawn, "{Motion:[0.0d,0.0d,1.5d]" + (burning ? ",Fire:200s" : "") + "}"));
            scene.await("the summoned Clackers hitting the TNT minecart", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cart
                        && contact.pre().tag().getShort("Fire") > 0 == burning && !cart.isPrimed(),
                        "fixture: not one contact of " + (burning ? "burning" : "cold") + " Clackers with an unprimed TNT minecart");
                if (burning) {
                    scene.check(explosions.size() == 1 && explosions.get(0).getDirectSourceEntity() == cart && cart.isRemoved(),
                            "1.16: Clackers are arrows, and a burning arrow explodes a TNT minecart at once; explosions: "
                                    + explosions.size() + ", minecart gone: " + cart.isRemoved());
                }
                else {
                    scene.check(explosions.isEmpty() && !cart.isRemoved(),
                            "an arrow that does not burn leaves a TNT minecart whole, but Clackers that do not burn made "
                                    + explosions.size() + " explosion(s), minecart gone: " + cart.isRemoved());
                }
            });
        });
    }
}
