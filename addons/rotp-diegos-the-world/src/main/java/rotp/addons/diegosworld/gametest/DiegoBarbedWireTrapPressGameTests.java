package rotp.addons.diegosworld.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 BarbedWireTrap had TargetRequirement.ENTITY: PowerBaseImpl.checkTarget refused the press, with nothing paid,
 * unless it was at an entity within 8 blocks of the performer ("target_too_far" beyond) that
 * StandEntityAction.checkStandTarget took (a living one the Stand may attack). The press at an accepted mob, also with
 * the Stand away (the press summons it), placed the wire around that mob.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoBarbedWireTrapPressGameTests {
    private static final short KEY = 126;

    private DiegoBarbedWireTrapPressGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "rotpDiegoBarbedWirePress")
    public static void barbedWireTrapPressNeedsAnAttackableLivingTargetAsIn116(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.userPos),
                        "Diego's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.run();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget first ran StandEntityAction.overrideVanillaMouseTarget on the press: the trap
     * (TargetRequirement.ENTITY) pressed at a torch took the cow behind it, which The World's collision ray met, and the
     * wire went up around the cow.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "rotpDiegoBarbedWireTorch")
    public static void barbedWireTrapPressedAtATorchTrapsTheCowBehindAsIn116(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.userPos),
                        "Diego's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.runTorch();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos userPos;
        private final RecordingPlayer user;
        private final List<Entity> spawned = new ArrayList<>();
        private final TimeStopState timeStops;
        private StandPower power;
        private boolean timeStopped;
        private int placedWire;
        private BlockPos torch;
        private boolean closed;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            userPos = new BlockPos(chunk.getMinBlockX() + 2, template.getY() + 8, chunk.getMinBlockZ() + 4);
            user = makePlayer(level, userPos);
            user.setNoGravity(true);
            user.setGameMode(GameType.SURVIVAL);
            timeStops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        }

        private void run() {
            setUp();
            Mob cow = mob(EntityType.COW, 3.0D, 0.0D);
            Mob farCow = mob(EntityType.COW, 11.0D, 0.0D);
            Wolf wolf = (Wolf) mob(EntityType.WOLF, 0.0D, -3.0D);
            wolf.tame(user);
            helper.assertTrue(wolf.getOwner() == user, "fixture: the wolf is not the user's");
            BlockPos stone = userPos.offset(-3, 0, 0);
            level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);

            refused("pressed at nothing", ActionTarget.EMPTY, "target_entity");
            refused("pressed at a stone block", new ActionTarget(stone, Direction.UP), "target_entity");
            refused("pressed at a cow 11 blocks away", new ActionTarget(farCow), "target_too_far");
            refused("pressed at the user's own wolf", new ActionTarget(wolf), "target_entity");

            placed("pressed at a cow 3 blocks away", cow, 6);

            // the Stand away: the press is checked at the user's aim and summons the Stand
            DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
            helper.assertTrue(power.getSummonedStandEntity() == null, "fixture: the Stand is still out");
            power.resetAbilityCooldowns();
            Mob otherCow = mob(EntityType.COW, 0.0D, 5.0D);
            refused("pressed with the Stand away at the user's own wolf", new ActionTarget(wolf), "target_entity");
            helper.assertTrue(power.getSummonedStandEntity() == null,
                    "a refused press with the Stand away summoned it");
            placed("pressed with the Stand away at a cow", otherCow, 0);
        }

        // The Stand's eyes level with its user's, just south of the user's box, looking south as the user does, as
        // in the core's scenes; a wall torch on the crosshair a block ahead, a cow 2 blocks south of the eyes.
        private void runTorch() {
            setUp();
            user.setYRot(0.0F);
            user.setYHeadRot(0.0F);
            user.setXRot(0.0F);
            StandEntity stand = power.getSummonedStandEntity();
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getEyeY() - stand.getEyeHeight(), user.getZ() + 0.35D, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            torch = BlockPos.containing(user.getEyePosition()).south();
            level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            Mob cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(user.getX(), user.getEyeY() - cow.getBbHeight() / 2.0D, user.getZ() + 2.0D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            spawned.add(cow);
            placedAt("pressed at a torch with a cow behind it", new ActionTarget(torch, Direction.NORTH), cow, 6);
        }

        private void setUp() {
            power = PowerClass.STAND.attachGet(user);
            power.setStand(DiegosTheWorldAddon.THE_WORLD.get());
            power.skipProgression();
            helper.assertTrue(DiegosTheWorldAddon.THE_WORLD.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Could not summon Diego's The World");
            user.getInventory().add(new ItemStack(DiegosTheWorldAddon.BARBED_WIRE_ITEM.get(), 12));
            helper.assertTrue(wire() == 12, "fixture: the user does not carry 12 barbed wire");
            // the user's own stopped time, which the trap needs
            user.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 200, 0));
            timeStopped = timeStops.tryPutInstance(new TimeStopState.Instance(user.getId(), 200, 200,
                    new ChunkPos(userPos), 1, user.getId(), "diego_barbed_wire_press_test"));
            helper.assertTrue(timeStopped && timeStops.isTimeStopped(user),
                    "fixture: the user did not stop time");
            helper.assertTrue(!timeStops.shouldFreeze(user), "fixture: the user froze in their own stopped time");
        }

        private void refused(String why, ActionTarget target, String messageKey) {
            aim(target);
            power.setStamina(power.getMaxStamina());
            float stamina = power.getStamina();
            user.messages.clear();
            AbilityConditionCheck selected = selected();
            helper.assertTrue(!AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "1.16 refused the barbed wire trap " + why + " at the press; the port accepts it");
            List<String> expected = List.of("jojo.message.action_condition." + messageKey);
            helper.assertTrue(user.messages.equals(expected),
                    "the barbed wire trap " + why + " says " + expected + ", got " + user.messages);
            StandEntity stand = power.getSummonedStandEntity();
            EntityActionInstance started = stand != null ? stand.getCurStandAction() : null;
            helper.assertTrue(started == null, "the refused press " + why + " started " + started);
            helper.assertTrue(power.getStamina() == stamina && wire() == 12 - placedWire
                            && !power.isAbilityOnCooldown(DiegosTheWorldAddon.BARBED_WIRE_TRAP),
                    "1.16 paid nothing for a press refused " + why + ": stamina " + stamina + " -> "
                            + power.getStamina() + ", wire " + wire() + ", cooldown "
                            + power.getAbilityCooldown(DiegosTheWorldAddon.BARBED_WIRE_TRAP));
        }

        private void placed(String why, Mob target, int wireLeft) {
            placedAt(why, new ActionTarget(target), target, wireLeft);
        }

        private void placedAt(String why, ActionTarget pressedAt, Mob target, int wireLeft) {
            BlockPos ground = target.blockPosition().below();
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            aim(pressedAt);
            power.setStamina(power.getMaxStamina());
            float stamina = power.getStamina();
            user.messages.clear();
            AbilityConditionCheck selected = selected();
            helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "the barbed wire trap " + why + " was refused: " + user.messages);
            BufferingState state = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, state,
                    selected.ability.getAbilityId());
            AbilityInput.keyRelease(KEY, user);
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(state.isActionSuccess && stand != null && stand.getCurStandAction() != null,
                    "the barbed wire trap " + why + " did not start: " + state.isActionSuccess + ", " + stand);
            EntityActionInstance action = stand.getCurStandAction();
            for (int i = 0; i < 5 && wire() == 12 - placedWire && !action.isOver(); i++) {
                action._tickAction();
            }
            placedWire = 12 - wireLeft;
            helper.assertTrue(wire() == wireLeft,
                    "the barbed wire trap " + why + " took no wire for its trap: " + wire() + " left, messages "
                            + user.messages);
            BlockPos wall = ground.west(2).above();
            helper.assertTrue(level.getBlockState(wall).is(DiegosTheWorldAddon.BARBED_WIRE.get()),
                    "the barbed wire trap " + why + " placed no wire west of the cow at " + wall + ": "
                            + level.getBlockState(wall));
            helper.assertTrue(power.getStamina() == stamina - 50.0F,
                    "the barbed wire trap " + why + " pays 50 stamina: " + stamina + " -> " + power.getStamina());
        }

        private int wire() {
            int count = 0;
            for (ItemStack stack : user.getInventory().items) {
                if (stack.is(DiegosTheWorldAddon.BARBED_WIRE_ITEM.get())) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private Mob mob(EntityType<? extends Mob> type, double east, double south) {
            Mob mob = type.create(level);
            helper.assertTrue(mob != null, "Could not make " + type);
            mob.moveTo(user.getX() + east, user.getY(), user.getZ() + south, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(mob), "Could not add " + type);
            spawned.add(mob);
            return mob;
        }

        // What the client sends as the user's and the Stand's crosshair.
        private void aim(ActionTarget target) {
            LivingComponentAction.getComponent(user).entityAim.setTarget(target);
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
            }
        }

        private AbilityConditionCheck selected() {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(DiegosTheWorldAddon.BARBED_WIRE_TRAP);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null, "the barbed wire trap is not in the moveset");
            return selected;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (timeStopped) {
                    timeStops.removeInstance(user.getId());
                }
                AbilityInput.keyRelease(KEY, user);
                if (power != null && power.isSummoned()) {
                    DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
                }
                spawned.forEach(Entity::discard);
                if (torch != null) {
                    level.setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            finally {
                user.discard();
            }
        }
    }

    private static RecordingPlayer makePlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "diego-wire-press");
        RecordingPlayer player = new RecordingPlayer(server, level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentGameListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(pos));
        level.addNewPlayer(player);
        return player;
    }

    // Keeps the translation keys of the action-bar messages the silent connection drops.
    private static final class RecordingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message.getContents() instanceof TranslatableContents translatable
                    ? translatable.getKey()
                    : message.getString());
        }
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
