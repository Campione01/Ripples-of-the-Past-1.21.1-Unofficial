package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.ProjectileHamonChargeState;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import rotp.core.init.ModDamageTypes;

/**
 * 1.16.5 DamageUtil.dealHamonDamage built an IndirectEntityDamageSource whenever it got both a direct and an
 * indirect source entity (every Hamon projectile with an owner, a Hamon-charged projectile or thrown entity, the
 * entity knocked into the target by a Sunlight Yellow Overdrive punch), and a 1.16.5 Enderman answers every
 * indirect source by teleporting away unharmed. With a direct entity only, or with no entity, the hit landed.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonIndirectSourceEndermanGameTests {
    private HamonIndirectSourceEndermanGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_indirect_enderman_rule", timeoutTicks = 40)
    public static void endermanDodgesHamonWithASeparateOwnerAndTakesTheRest(GameTestHelper helper) {
        Scene scene = new Scene(helper, "rule");
        try {
            scene.build();
            Cow user = scene.cow(scene.point(10.5D, 0.0D, 4.5D));
            Cow carrier = scene.cow(scene.point(6.5D, 0.0D, 4.5D));

            boolean landed = scene.hit(carrier, user);
            scene.check(landed && scene.dodged(),
                    "1.16: an Enderman dodges Hamon damage that has a direct entity and a separate owner; " + scene.state(landed));

            landed = scene.hit(user, user);
            scene.check(landed && scene.damagedInPlace(),
                    "1.16: Hamon damage dealt by the user's own body is no indirect source and lands; " + scene.state(landed));

            landed = scene.hit(carrier, null);
            scene.check(landed && scene.damaged(),
                    "1.16: Hamon damage from an entity without an owner is no indirect source and lands; " + scene.state(landed));

            landed = scene.hit(null, null);
            scene.check(landed && scene.damaged(),
                    "1.16: Hamon damage without any entity lands; " + scene.state(landed));
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_indirect_enderman_bubble", timeoutTicks = 120)
    public static void endermanDodgesAHamonBubbleOfTheBubbleLauncher(GameTestHelper helper) {
        Scene scene = new Scene(helper, "bubble");
        helper.testInfo.addListener(scene);
        try {
            scene.build();
            Player shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            shooter.setNoGravity(true);
            Vec3 feet = scene.point(8.5D, 0.0D, 5.5D);
            shooter.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            shooter.setYHeadRot(0.0F);
            shooter.yBodyRot = 0.0F;
            scene.add(shooter);
            scene.reset();
            // the bubble as HamonBubbleLauncherAbility fires it, without the random spread
            HamonBubbleEntity bubble = new HamonBubbleEntity(shooter, scene.level);
            bubble.shootFromRotation(shooter, 0.5F, 0.0F);
            scene.add(bubble);
            scene.check(bubble.getOwner() == shooter, "fixture: the bubble has no owner");
            helper.runAfterDelay(1, () -> scene.pollBubble(bubble, 100));
        }
        catch (RuntimeException | Error error) {
            scene.close();
            throw error;
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "hamon_indirect_enderman_charged_arrow", timeoutTicks = 120)
    public static void endermanDodgesTheHamonChargeOfAnArrowWithAShooter(GameTestHelper helper) {
        chargedArrow(helper, "charged-arrow", false);
    }

    // 1.16 add-on charges (ProjectileUltimateHamonChargeCap) dealt the plain DamageUtil.HAMON, which no Enderman dodges
    @GameTest(template = "empty", skyAccess = true, batch = "hamon_plain_source_charged_arrow", timeoutTicks = 120)
    public static void endermanTakesAReloadedArrowChargeWithThePlainHamonSource(GameTestHelper helper) {
        chargedArrow(helper, "plain-charged-arrow", true);
    }

    private static void chargedArrow(GameTestHelper helper, String name, boolean plainSource) {
        Scene scene = new Scene(helper, name);
        helper.testInfo.addListener(scene);
        try {
            scene.build();
            Cow shooter = scene.cow(scene.point(10.5D, 0.0D, 4.5D));
            scene.reset();
            Vec3 from = scene.point(8.5D, 1.5D, 5.5D);
            Arrow shot = new Arrow(scene.level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            shot.setOwner(shooter);
            shot.shoot(0.0D, 0.0D, 1.0D, 1.0F, 0.0F);
            scene.add(shot);
            // the charge as HamonUtil.tryChargeProjectile and the add-ons set it
            ProjectileHamonChargeState charge = ProjectileHamonChargeState.get(shot);
            charge.setInfiniteChargeTime();
            charge.setMultiplyWithUserStrength(true);
            charge.setBaseDmg(1.5F);
            if (plainSource) {
                charge.setPlainHamonSource(true);
            }
            Arrow arrow = plainSource ? scene.reload(shot) : shot;
            scene.check(arrow.getOwner() == shooter && ProjectileHamonChargeState.get(arrow).hasHamonCharge(),
                    "fixture: the arrow lost its shooter or its Hamon charge");
            List<DamageSource> hamonHits = new ArrayList<>();
            boolean[] impact = new boolean[1];
            scene.listen(ProjectileImpactEvent.class, event -> {
                if (event.getProjectile() == arrow && event.getRayTraceResult() instanceof EntityHitResult hit
                        && hit.getEntity() == scene.enderman) {
                    impact[0] = true;
                }
            });
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == scene.enderman && event.getSource().is(ModDamageTypes.HAMON)) {
                    hamonHits.add(event.getSource());
                }
            });
            helper.runAfterDelay(1, () -> scene.pollArrow(arrow, impact, hamonHits, plainSource, 100));
        }
        catch (RuntimeException | Error error) {
            scene.close();
            throw error;
        }
    }

    private static final class Scene implements GameTestListener {
        private final GameTestHelper helper;
        final ServerLevel level;
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<Entity> owned = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Consumer<EntityTeleportEvent.EnderEntity> redirect;
        private EnderMan enderman;
        private Vec3 start;
        private Vec3 refuge;
        private float health;
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
        }

        Vec3 point(double dx, double dy, double dz) {
            return new Vec3(x + dx, y + dy, z + dz);
        }

        void check(boolean condition, String message) {
            helper.assertTrue(condition, "HAMON-ENDERMAN " + name + ": " + message);
        }

        void build() {
            BlockPos from = new BlockPos(x + 4, y - 1, z + 3);
            BlockPos to = new BlockPos(x + 12, y + 4, z + 13);
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                check(level.isEmptyBlock(pos) && level.isPositionEntityTicking(pos), "fixture: room cell is not empty and ticking: " + pos);
            }
            for (BlockPos pos : BlockPos.betweenClosed(from, new BlockPos(to.getX(), y - 1, to.getZ()))) {
                originals.put(pos.immutable(), level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            start = point(8.5D, 0.0D, 7.5D);
            refuge = point(6.5D, 0.0D, 11.5D);
            enderman = EntityType.ENDERMAN.create(level);
            enderman.setNoAi(true);
            enderman.setPos(start);
            add(enderman);
            health = enderman.getHealth();
            // the random destination is replaced, so the Enderman stays inside the room
            redirect = event -> {
                if (event.getEntity() == enderman) {
                    event.setTargetX(refuge.x);
                    event.setTargetY(refuge.y);
                    event.setTargetZ(refuge.z);
                }
            };
            NeoForge.EVENT_BUS.addListener(EntityTeleportEvent.EnderEntity.class, redirect);
        }

        Cow cow(Vec3 feet) {
            Cow cow = EntityType.COW.create(level);
            cow.setNoAi(true);
            cow.setPos(feet);
            return add(cow);
        }

        <T extends Entity> T add(T entity) {
            owned.add(entity);
            check(level.addFreshEntity(entity), "fixture: could not add " + entity);
            return entity;
        }

        <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
            listeners.add(listener);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, listener);
        }

        /** Saves the arrow as a chunk would, removes it and loads the saved data into a fresh arrow. */
        Arrow reload(Arrow arrow) {
            CompoundTag saved = new CompoundTag();
            check(arrow.save(saved), "fixture: the arrow could not be saved");
            arrow.discard();
            Entity loaded = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            check(loaded instanceof Arrow && loaded != arrow, "fixture: the saved arrow did not load back");
            return add((Arrow) loaded);
        }

        void reset() {
            enderman.teleportTo(start.x, start.y, start.z);
            enderman.setHealth(enderman.getMaxHealth());
            enderman.invulnerableTime = 0;
            health = enderman.getHealth();
        }

        boolean hit(@Nullable Entity direct, @Nullable Entity causing) {
            reset();
            return HamonAbilityHelpers.hamonHurt(enderman, 10.0F, direct, causing);
        }

        boolean dodged() {
            return enderman.isAlive() && enderman.getHealth() == health && enderman.position().distanceTo(refuge) < 0.01D;
        }

        boolean damaged() {
            return enderman.isAlive() && enderman.getHealth() < health;
        }

        boolean damagedInPlace() {
            return damaged() && enderman.position().distanceTo(start) < 0.01D;
        }

        String state(boolean landed) {
            return "hurt returned " + landed + ", health " + health + " -> " + enderman.getHealth() + ", "
                    + (enderman.position().distanceTo(refuge) < 0.01D ? "teleported away"
                            : enderman.position().distanceTo(start) < 0.01D ? "did not teleport" : "stands at " + enderman.position());
        }

        void pollBubble(HamonBubbleEntity bubble, int deadline) {
            if (closed) return;
            try {
                boolean touched = enderman.getHealth() != health || enderman.position().distanceTo(start) > 0.01D;
                if (touched) {
                    check(dodged(), "1.16: an Enderman dodges the Hamon hit of a bubble with an owner; " + state(true));
                    close();
                    helper.succeed();
                    return;
                }
                check(!bubble.isRemoved() && helper.getTick() < deadline,
                        "fixture: the bubble never reached the Enderman; bubble at " + bubble.position() + ", removed " + bubble.isRemoved());
                helper.runAfterDelay(1, () -> pollBubble(bubble, deadline));
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        void pollArrow(Arrow arrow, boolean[] impact, List<DamageSource> hamonHits, boolean plainSource, int deadline) {
            if (closed) return;
            try {
                if (impact[0]) {
                    String seen = "Hamon hits " + hamonHits.size() + ", " + state(true);
                    if (plainSource) {
                        check(hamonHits.size() == 1 && hamonHits.get(0).getDirectEntity() == null
                                && hamonHits.get(0).getEntity() == null && damaged(),
                                "a charge with the plain Hamon source has no source entity and hurts an Enderman, also after a reload; " + seen);
                    }
                    else {
                        check(hamonHits.isEmpty() && dodged(),
                                "1.16: an Enderman dodges the Hamon charge of an arrow that has a shooter; " + seen);
                    }
                    close();
                    helper.succeed();
                    return;
                }
                check(!arrow.isRemoved() && helper.getTick() < deadline,
                        "fixture: the arrow never reached the Enderman; arrow at " + arrow.position() + ", removed " + arrow.isRemoved());
                helper.runAfterDelay(1, () -> pollArrow(arrow, impact, hamonHits, plainSource, deadline));
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            if (redirect != null) NeoForge.EVENT_BUS.unregister(redirect);
            for (Entity entity : owned) if (!entity.isRemoved()) entity.discard();
            originals.forEach(level::setBlockAndUpdate);
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
