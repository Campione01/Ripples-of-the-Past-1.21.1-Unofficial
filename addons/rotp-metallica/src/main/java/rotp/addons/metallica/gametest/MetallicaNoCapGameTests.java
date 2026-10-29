package rotp.addons.metallica.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.entity.MetallicaStandEntity;
import rotp.addons.metallica.state.MetallicaIronService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Metallica has no per-tick caps (owner, 2026-10-15: remove them as 1.16 did). 1.16 VictimBladeCreation hit every
 * eligible living entity in the 10-block box on each tick, and MetallicaStandType pulled every iron item and minecart
 * and visited every living entity in the box. Each count below is above the cap the port had: 16 victims, 32 items,
 * 8 minecarts, 16 equipment candidates.
 */
@GameTestHolder(MetallicaAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetallicaNoCapGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "metallica_no_caps";
    private static final short KEY = 51;
    private static final int VICTIMS = 24;
    private static final int ITEMS = 40;
    private static final int MINECARTS = 12;
    private static final int EQUIPPED = 24;
    // Equipment goes with a probability below one per pass, so the passes have to outnumber the odds.
    private static final int STRIP_PASSES = 100;
    private static final float VICTIM_HEALTH = 100.0F;

    private MetallicaNoCapGameTests() {}

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void victimBladesHitEveryEligibleEntityInOneTick(GameTestHelper helper) {
        run(helper, scene -> {
            List<Cow> cows = scene.spawnCows(VICTIMS);
            for (Cow cow : cows) {
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
                cow.setHealth(VICTIM_HEALTH);
            }
            Ability blades = scene.power.getAbility(MetallicaAddon.VICTIM_BLADES_NAME);
            helper.assertTrue(blades != null && AbilityInput.withConditionCheck(blades, scene.owner),
                    "The victim-blades input was not admitted");
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, blades, scene.owner, null, InputMethod.HOLD, 0.0F, pressed,
                    blades.getAbilityId());
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.stand);
            helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == blades,
                    "The victim-blades hold did not start");
            for (int tick = 0; tick < 20 && action.getPhase() != ActionPhase.PERFORM && !action.isOver(); tick++) {
                action._tickAction();
            }
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                    "The hold never reached its perform phase: " + action.getPhase());
            helper.assertTrue(cows.stream().noneMatch(cow -> cow.hasEffect(MetallicaAddon.LACK_OF_IRON)),
                    "A victim was hit before the perform phase");

            action._tickAction();

            int hit = 0;
            for (Cow cow : cows) {
                if (cow.hasEffect(MetallicaAddon.LACK_OF_IRON) && cow.getHealth() < VICTIM_HEALTH) {
                    hit++;
                }
            }
            helper.assertTrue(hit == VICTIMS,
                    "1.16 hit every eligible entity in the box on each tick: " + hit + " of " + VICTIMS
                            + " cows were hit in the first perform tick");
            LOGGER.info("R770_METALLICA_NOCAP_PASS victim_blades hit={}/{}", hit, VICTIMS);
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void passiveIronControlHandlesMoreThanTheOldCaps(GameTestHelper helper) {
        run(helper, scene -> {
            List<ItemEntity> items = new ArrayList<>();
            for (Vec3 spot : scene.spots(ITEMS)) {
                ItemEntity item = new ItemEntity(scene.level, spot.x, spot.y, spot.z, new ItemStack(Items.IRON_INGOT));
                item.setNoGravity(true);
                item.setDeltaMovement(Vec3.ZERO);
                scene.level.addFreshEntity(item);
                scene.spawned.add(item);
                items.add(item);
            }
            List<AbstractMinecart> minecarts = new ArrayList<>();
            for (Vec3 spot : scene.spots(MINECARTS)) {
                AbstractMinecart minecart = (AbstractMinecart) EntityType.MINECART.create(scene.level);
                minecart.setNoGravity(true);
                minecart.setPos(spot);
                minecart.setDeltaMovement(Vec3.ZERO);
                scene.level.addFreshEntity(minecart);
                scene.spawned.add(minecart);
                minecarts.add(minecart);
            }

            MetallicaIronService.tickActiveUser(scene.owner);

            int pulledItems = (int) items.stream().filter(item -> pulledToward(item, scene.owner)).count();
            helper.assertTrue(pulledItems == ITEMS,
                    "1.16 pulled every iron item in the box: " + pulledItems + " of " + ITEMS
                            + " items moved toward the user in one tick");
            LOGGER.info("R770_METALLICA_NOCAP_PASS iron_items pulled={}/{}", pulledItems, ITEMS);
            int pulledCarts = (int) minecarts.stream().filter(cart -> pulledToward(cart, scene.owner)).count();
            helper.assertTrue(pulledCarts == MINECARTS,
                    "1.16 pulled every minecart in the box: " + pulledCarts + " of " + MINECARTS
                            + " minecarts moved toward the user in one tick");
            LOGGER.info("R770_METALLICA_NOCAP_PASS iron_minecarts pulled={}/{}", pulledCarts, MINECARTS);
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void passiveEquipmentStripVisitsEveryEntityInTheBox(GameTestHelper helper) {
        run(helper, scene -> {
            List<Cow> cows = scene.spawnCows(EQUIPPED);
            for (Cow cow : cows) {
                cow.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            for (int pass = 0; pass < STRIP_PASSES; pass++) {
                // 1.16 gated each entity on its own tick count
                cows.forEach(cow -> cow.tickCount = 0);
                MetallicaIronService.tickActiveUser(scene.owner);
            }
            int stillArmed = (int) cows.stream()
                    .filter(cow -> cow.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)).count();
            helper.assertTrue(stillArmed == 0,
                    "1.16 visited every living entity in the box: " + stillArmed + " of " + EQUIPPED
                            + " cows still hold their iron sword after " + STRIP_PASSES + " passes");
            LOGGER.info("R770_METALLICA_NOCAP_PASS iron_equipment stripped={}/{} passes={}", EQUIPPED - stillArmed,
                    EQUIPPED, STRIP_PASSES);
        });
    }

    // 1.16 MetallicaStandType.tickUser took each living entity of the box on its own tick: entity.tickCount % 20 == 0,
    // whatever the user's tick count was.
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void equipmentStripGatesEachEntityOnItsOwnTickCountAsIn116(GameTestHelper helper) {
        run(helper, scene -> {
            List<Cow> cows = scene.spawnCows(2);
            Cow open = cows.get(0);
            Cow closed = cows.get(1);
            for (Cow cow : cows) {
                cow.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            for (int pass = 0; pass < STRIP_PASSES; pass++) {
                scene.owner.tickCount = 7;
                open.tickCount = 40;
                closed.tickCount = 41;
                MetallicaIronService.tickActiveUser(scene.owner);
            }
            boolean openStripped = !open.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD);
            boolean closedStripped = !closed.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD);
            helper.assertTrue(openStripped && !closedStripped,
                    "1.16 stripped an entity on its own tick count % 20 == 0 and none on the user's: the cow at tick 40"
                            + " (user at 7) was stripped " + openStripped + ", the cow at tick 41 " + closedStripped);
            LOGGER.info("R770_METALLICA_NOCAP_PASS strip_own_tick open={} closed={}", openStripped, closedStripped);
        });
    }

    // 1.16 MetallicaStandType.tickUser and pullIron took the box x - range .. x + range + 1, y - range .. y + range,
    // z - range .. z + range + 1 around the user's position (range 10): one block more to the east and south, none
    // above, and none beyond the range to the west and north. An iron item is pulled and an iron-armed cow stripped
    // only inside it.
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void ironBoxIsTheAsymmetricBoxOfTheDonorAsIn116(GameTestHelper helper) {
        run(helper, scene -> {
            double minX = new ChunkPos(scene.ownerPos).getMinBlockX();
            double minZ = new ChunkPos(scene.ownerPos).getMinBlockZ();
            double y = scene.ownerPos.getY();
            // owner position, item offset, cow offset, whether 1.16's box reaches them
            Object[][] cases = {
                    {"east", new Vec3(minX + 2.5D, y, minZ + 8.5D), new Vec3(10.9D, 0.0D, 0.0D),
                            new Vec3(11.2D, 0.0D, 0.0D), true},
                    {"south", new Vec3(minX + 8.5D, y, minZ + 2.5D), new Vec3(0.0D, 0.0D, 10.9D),
                            new Vec3(0.0D, 0.0D, 11.2D), true},
                    {"west", new Vec3(minX + 13.5D, y, minZ + 8.5D), new Vec3(-10.2D, 0.0D, 0.0D),
                            new Vec3(-10.6D, 0.0D, 0.0D), false},
                    {"north", new Vec3(minX + 8.5D, y, minZ + 13.5D), new Vec3(0.0D, 0.0D, -10.2D),
                            new Vec3(0.0D, 0.0D, -10.6D), false},
                    {"above", new Vec3(minX + 8.5D, y, minZ + 8.5D), new Vec3(0.0D, 10.1D, 0.0D),
                            new Vec3(0.0D, 10.05D, 0.0D), false},
            };
            Vec3 home = scene.owner.position();
            StringBuilder seen = new StringBuilder();
            for (Object[] row : cases) {
                String name = (String) row[0];
                Vec3 at = (Vec3) row[1];
                Vec3 itemAt = at.add((Vec3) row[2]);
                Vec3 cowAt = at.add((Vec3) row[3]);
                boolean reached = (Boolean) row[4];
                scene.owner.setPos(at);
                ItemEntity item = new ItemEntity(scene.level, itemAt.x, itemAt.y, itemAt.z, new ItemStack(Items.IRON_INGOT));
                item.setNoGravity(true);
                item.setDeltaMovement(Vec3.ZERO);
                scene.level.addFreshEntity(item);
                scene.spawned.add(item);
                Cow cow = EntityType.COW.create(scene.level);
                cow.setNoAi(true);
                cow.setNoGravity(true);
                cow.setSilent(true);
                cow.moveTo(cowAt.x, cowAt.y, cowAt.z, 0.0F, 0.0F);
                cow.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                scene.level.addFreshEntity(cow);
                scene.spawned.add(cow);
                boolean pulled = false;
                for (int pass = 0; pass < STRIP_PASSES; pass++) {
                    cow.tickCount = 0;
                    MetallicaIronService.tickActiveUser(scene.owner);
                    if (pass == 0) {
                        pulled = pulledToward(item, scene.owner);
                    }
                }
                boolean stripped = !cow.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD);
                item.discard();
                cow.discard();
                seen.append(name).append(": item ").append(pulled).append(", cow ").append(stripped).append("; ");
                helper.assertTrue(pulled == reached && stripped == reached,
                        "1.16's box (x-10..x+11, y-10..y+10, z-10..z+11 around the user) " + (reached ? "reaches" : "misses")
                                + " the " + name + " probes: pulled item " + pulled + ", stripped cow " + stripped);
            }
            scene.owner.setPos(home);
            LOGGER.info("R770_METALLICA_NOCAP_PASS iron_box {}", seen);
        });
    }

    // 1.16 VictimBladeCreation took every living entity of the box with IS_ALIVE alone: a Creative player and a
    // spectator took the effect like anyone (the damage does nothing to them).
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void victimBladesReachCreativeAndSpectatorPlayersAsIn116(GameTestHelper helper) {
        run(helper, scene -> {
            ServerPlayer creative = makeServerPlayer(scene.level, scene.ownerPos.offset(3, 0, 0));
            ServerPlayer spectator = makeServerPlayer(scene.level, scene.ownerPos.offset(-3, 0, 0));
            scene.spawned.add(creative);
            scene.spawned.add(spectator);
            creative.setNoGravity(true);
            spectator.setNoGravity(true);
            creative.setGameMode(GameType.CREATIVE);
            spectator.setGameMode(GameType.SPECTATOR);
            Ability blades = scene.power.getAbility(MetallicaAddon.VICTIM_BLADES_NAME);
            helper.assertTrue(blades != null && AbilityInput.withConditionCheck(blades, scene.owner),
                    "The victim-blades input was not admitted");
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, blades, scene.owner, null, InputMethod.HOLD, 0.0F, pressed,
                    blades.getAbilityId());
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(scene.stand);
            helper.assertTrue(pressed.isActionSuccess && action != null && action.ability == blades,
                    "The victim-blades hold did not start");
            for (int tick = 0; tick < 20 && action.getPhase() != ActionPhase.PERFORM && !action.isOver(); tick++) {
                action._tickAction();
            }
            helper.assertTrue(action.getPhase() == ActionPhase.PERFORM,
                    "The hold never reached its perform phase: " + action.getPhase());
            action._tickAction();
            boolean creativeHit = creative.hasEffect(MetallicaAddon.LACK_OF_IRON);
            boolean spectatorHit = spectator.hasEffect(MetallicaAddon.LACK_OF_IRON);
            helper.assertTrue(creativeHit && spectatorHit,
                    "1.16 gave a Creative player and a spectator the lack of iron like anyone in the box: Creative "
                            + creativeHit + ", spectator " + spectatorHit);
            LOGGER.info("R770_METALLICA_NOCAP_PASS victim_creative creative={} spectator={}", creativeHit, spectatorHit);
        });
    }

    private static boolean pulledToward(Entity entity, ServerPlayer user) {
        Vec3 motion = entity.getDeltaMovement();
        Vec3 toUser = user.position().subtract(entity.getBoundingBox().getCenter());
        return motion.lengthSqr() > 0.0D && motion.dot(toUser) > 0.0D;
    }

    private static void run(GameTestHelper helper, java.util.function.Consumer<Scene> body) {
        BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
        ChunkPos chunk = new ChunkPos(template);
        BlockPos ownerPos = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 8, chunk.getMinBlockZ() + 8);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().isPositionEntityTicking(ownerPos),
                        "Metallica's chunk never started ticking entities."))
                .thenExecute(() -> {
                    Scene scene = new Scene(helper, ownerPos);
                    try {
                        body.accept(scene);
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    // A GameTest listener, so a failed or timed-out test still clears its entities.
    private static final class Scene implements GameTestListener {
        private final ServerLevel level;
        private final BlockPos ownerPos;
        private final ServerPlayer owner;
        private final StandPower power;
        private final MetallicaStandEntity stand;
        private final List<Entity> spawned = new ArrayList<>();
        private boolean closed;

        private Scene(GameTestHelper helper, BlockPos ownerPos) {
            this.level = helper.getLevel();
            this.ownerPos = ownerPos;
            helper.testInfo.addListener(this);
            owner = makeServerPlayer(level, ownerPos);
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(MetallicaAddon.METALLICA.get());
            power.skipProgression();
            helper.assertTrue(MetallicaAddon.METALLICA.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof MetallicaStandEntity,
                    "Production Metallica summon failed");
            stand = (MetallicaStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            power.setStamina(power.getMaxStamina());
            LivingComponentAction.getComponent(owner);
            LivingComponentAction.getComponent(stand);
        }

        // Two blocks apart on a square lattice around the user, inside the user's own chunk.
        private List<Vec3> spots(int count) {
            List<Vec3> spots = new ArrayList<>();
            for (int row = 0; row < 7; row++) {
                for (int column = 0; column < 7; column++) {
                    if (row != 3 || column != 3) {
                        spots.add(new Vec3(ownerPos.getX() + 0.5D + (column - 3) * 2.0D, ownerPos.getY(),
                                ownerPos.getZ() + 0.5D + (row - 3) * 2.0D));
                    }
                }
            }
            if (count > spots.size()) {
                throw new IllegalStateException("The lattice has no room for " + count + " spots");
            }
            return spots.subList(0, count);
        }

        private List<Cow> spawnCows(int count) {
            List<Cow> cows = new ArrayList<>();
            for (Vec3 spot : spots(count)) {
                Cow cow = EntityType.COW.create(level);
                cow.setNoAi(true);
                cow.setNoGravity(true);
                cow.setSilent(true);
                cow.moveTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
                level.addFreshEntity(cow);
                spawned.add(cow);
                cows.add(cow);
            }
            return cows;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                for (Entity entity : spawned) {
                    entity.discard();
                }
                for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class,
                        owner.getBoundingBox().inflate(16.0D))) {
                    dropped.discard();
                }
                if (power.isSummoned()) {
                    MetallicaAddon.METALLICA.get().forceUnsummon(owner, power);
                }
                AbilityInput.keyRelease(KEY, owner);
            }
            finally {
                owner.discard();
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            close();
        }
    }

    static ServerPlayer makeServerPlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "metallica-no-caps");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentGameListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(pos));
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentGameListener extends ServerGamePacketListenerImpl {
        private SilentGameListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
