package rotp.core.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
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
}
