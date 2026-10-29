package rotp.addons.diverdown.ability;

import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownGameEvents;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;

/**
 * Diver Down's owner clean-up (a port addition: 1.16 let effects and shards expire) walks a copy of every entity of
 * every level. Death, clone, respawn, dimension change and logout run it only for an owner that bound an effect or a
 * shard, which bindEffect and BoneShardEntity.bind mark, so a dying mob that never used Diver Down costs nothing.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownCleanupGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;
    // DiverDownAuthority.OWNER_ACTIVE_TAG
    private static final String OWNER_MARK = DiverDownAddon.MOD_ID + ":authority_active";

    private DiverDownCleanupGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_cleanup_marked_owner",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void lifecycleCleanupsRunOnlyForAMarkedOwner(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene mobScene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                expectCleanupOnlyWhenMarked(helper, mobScene, "death", owner -> DiverDownGameEvents.onLivingDeath(
                        new LivingDeathEvent(owner, helper.getLevel().damageSources().generic())));
            } finally {
                mobScene.close();
            }
            DiverDownWallsGameTests.Scene playerScene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                ResourceKey<Level> from = helper.getLevel().dimension();
                ResourceKey<Level> to = from == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
                expectCleanupOnlyWhenMarked(helper, playerScene, "player death", owner -> DiverDownGameEvents
                        .onLivingDeath(new LivingDeathEvent(owner, helper.getLevel().damageSources().generic())));
                expectCleanupOnlyWhenMarked(helper, playerScene, "clone", owner -> DiverDownGameEvents.onPlayerClone(
                        new PlayerEvent.Clone((ServerPlayer) owner, (ServerPlayer) owner, true)));
                expectCleanupOnlyWhenMarked(helper, playerScene, "respawn", owner -> DiverDownGameEvents
                        .onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent((ServerPlayer) owner, false)));
                expectCleanupOnlyWhenMarked(helper, playerScene, "dimension change", owner -> DiverDownGameEvents
                        .onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(
                                (ServerPlayer) owner, from, to)));
                expectCleanupOnlyWhenMarked(helper, playerScene, "logout", owner -> DiverDownGameEvents
                        .onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent((ServerPlayer) owner)));
            } finally {
                playerScene.close();
            }
            helper.succeed();
        });
    }

    private static void expectCleanupOnlyWhenMarked(GameTestHelper helper, DiverDownWallsGameTests.Scene scene,
            String route, Consumer<LivingEntity> event) {
        LivingEntity owner = scene.owner;
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Could not create a cow");
        scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
        helper.assertTrue(DiverDownAuthority.bindEffect(cow, scene.stand, DiverDownAuthority.EffectKind.SPRING_LEGS,
                        SpringLegsAbility.DURATION_TICKS, SpringLegsAbility.AMPLIFIER),
                "Fixture: Spring Legs was not bound to the cow");
        BoneShardEntity shard = new BoneShardEntity(cow, owner, scene.stand, helper.getLevel());
        shard.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(shard) && owner.getUUID().equals(shard.getControllerUuid()),
                "Fixture: the bone shard was not bound to the owner");
        scene.extra.add(shard);
        helper.assertTrue(DiverDownAuthority.ownerHasAuthority(owner),
                "Fixture: binding an effect and a shard did not mark the owner");

        // the mark taken off stands for an owner that never bound anything: the walk is skipped and what it would have
        // found stays
        owner.getPersistentData().remove(OWNER_MARK);
        event.accept(owner);
        helper.assertTrue(cow.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT) && !shard.isRemoved(),
                "The " + route + " clean-up walked every entity of every level for an entity Diver Down never marked "
                        + "(no effect or shard bound): Spring Legs " + cow.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT)
                        + ", shard removed " + shard.isRemoved());

        DiverDownAuthority.markOwner(owner);
        event.accept(owner);
        helper.assertTrue(!cow.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT) && shard.isRemoved(),
                "The " + route + " clean-up of an owner that bound an effect and a shard left Spring Legs "
                        + cow.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT) + ", the shard removed " + shard.isRemoved());
        cow.discard();
        // a player's mark goes with the walk that found it; a mob's stays
        owner.getPersistentData().remove(OWNER_MARK);
    }
}
