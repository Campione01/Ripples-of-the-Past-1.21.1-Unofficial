package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.init.ModItems;

/** Pickup rule of a Clackers that never had a player thrower: the 1.16.5 arrow default, PickupStatus.DISALLOWED. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersPickupStateGameTests {
    private static final String FALLING = "Motion:[0.0d,-0.5d,0.0d]";

    private ClackersPickupStateGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_summoned_pickup_state", timeoutTicks = 100)
    public static void summonedClackersCannotBePickedUpAndExpireOnTheGround(GameTestHelper helper) {
        ClackersScene.start(helper, "summoned-pickup", 80, scene -> {
            scene.stone(4, -1, 4, 12, -1, 13);
            Player toucher = scene.bystander(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 6.2D), 0.0F);
            scene.then(() -> {
                // Five grounded ticks short of the arrow ground life, within the toucher's reach.
                scene.summon(scene.point(8.5D, 1.0D, 7.0D), "{" + FALLING + ",life:1195s}");
                scene.summon(scene.point(8.5D, 1.0D, 12.0D), "{" + FALLING + "}");
            });
            scene.await("five ticks on the ground", () -> scene.shots.get(0).isRemoved() && scene.groundTicks(0) < 5
                    || scene.groundTicks(0) >= 5 && scene.groundTicks(1) >= 5);
            scene.then(() -> {
                ClackersEntity touched = scene.shots.get(0);
                scene.check(toucher.isAlive() && toucher.getInventory().countItem(ModItems.CLACKERS.get()) == 0,
                        "a touching Survival player took a summoned Clackers");
                ClackersScene.Track expiring = scene.track(0);
                int at = expiring.steps.indexOf(expiring.landing());
                for (int ticks = 0; ticks <= 4; ticks++) {
                    ClackersScene.Frame frame = expiring.steps.get(at + ticks).post();
                    scene.check(frame.grounded() && !frame.removed(), "the summoned Clackers was gone after " + ticks + " ground ticks: " + frame.reason());
                }
                ClackersScene.Frame end = expiring.steps.get(at + 5).post();
                scene.check(end.removed() && end.reason() == Entity.RemovalReason.DISCARDED && touched.isRemoved(),
                        "ground life 1200 did not discard the summoned Clackers");
                scene.check(scene.level.getEntitiesOfClass(ItemEntity.class, scene.room).isEmpty(), "the expiry left an item");
                ClackersScene.Track counting = scene.track(1);
                ClackersScene.Frame fifth = counting.steps.get(counting.steps.indexOf(counting.landing()) + 5).post();
                scene.check(fifth.grounded() && !fifth.removed() && fifth.life() == 5,
                        "a summoned Clackers without a life tag must count its ground ticks from zero: " + fifth.life());
                scene.check(fifth.tag().contains("pickup", 99) && fifth.tag().getByte("pickup") == 0 && !fifth.tag().getBoolean("CreativeOnlyPickup"),
                        "a summoned Clackers must save the arrow pickup state 0: " + fifth.tag());
                // 1.16 AbstractArrow.setOwner: a player owner decides the pickup state by their game mode
                counting.shot.setOwner(toucher);
                scene.check(counting.shot.saveWithoutId(new CompoundTag()).getByte("pickup") == 1,
                        "a Survival player owner must make the Clackers recoverable");
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_legacy_pickup_flag", timeoutTicks = 100)
    public static void savedPickupFlagOfEarlierVersionsStillDecidesWhoMayPickUp(GameTestHelper helper) {
        ClackersScene.start(helper, "legacy-pickup-flag", 80, scene -> {
            scene.stone(4, -1, 4, 12, -1, 13);
            Player first = scene.bystander(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 6.2D), 0.0F);
            Player second = scene.bystander(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 11.2D), 0.0F);
            scene.then(() -> {
                scene.summon(scene.point(8.5D, 1.0D, 7.0D), "{" + FALLING + ",CreativeOnlyPickup:0b}");
                scene.summon(scene.point(8.5D, 1.0D, 12.0D), "{" + FALLING + ",CreativeOnlyPickup:1b}");
            });
            scene.await("five ticks on the ground", () -> scene.groundTicks(1) >= 5);
            scene.then(() -> {
                scene.check(scene.shots.get(0).isRemoved() && first.getInventory().countItem(ModItems.CLACKERS.get()) == 1,
                        "a recoverable Clackers saved by an earlier version was not picked up");
                ClackersScene.Frame kept = scene.track(1).steps.getLast().post();
                scene.check(kept.grounded() && !kept.removed() && kept.tag().getBoolean("CreativeOnlyPickup")
                        && second.getInventory().countItem(ModItems.CLACKERS.get()) == 0,
                        "a Creative-only Clackers saved by an earlier version was taken by a Survival player");
            });
        });
    }
}
