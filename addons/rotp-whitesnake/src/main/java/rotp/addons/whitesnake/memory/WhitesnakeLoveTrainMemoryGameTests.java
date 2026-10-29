package rotp.addons.whitesnake.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.ability.WhitesnakeRemoveMemoryDiscAbility;
import rotp.addons.whitesnake.disc.MemoryDiscItem;
import rotp.addons.whitesnake.memory.MemoryDiscService.Status;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(WhitesnakeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhitesnakeLoveTrainMemoryGameTests {
    private static final String MEMORY_ABILITY = "removing_the_memory_disk";
    private static final String STAND_ABILITY = WhitesnakeRemoveMemoryDiscAbility.STAND_DISC_REMOVAL_ABILITY;
    private static final ResourceKey<MobEffect> LOVE_TRAIN = ResourceKey.create(
            Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("rotp_d4c", "love_train"));

    private WhitesnakeLoveTrainMemoryGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void serviceExtractionGuardLeavesDirectRestorationAvailable(GameTestHelper helper) {
        MessagePlayer caster = player(helper, "ws-lt-caster");
        MessagePlayer target = player(helper, "ws-lt-target");
        try {
            StandPower power = StandPower.get(target);
            helper.assertTrue(power != null, "Target has no Stand attachment");
            power.setStand(WhitesnakeAddon.WHITESNAKE.get());
            helper.assertTrue(WhitesnakeAddon.WHITESNAKE.get().summon(target, power),
                    "Target Stand did not summon");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Target Stand is absent");
            Holder<MobEffect> protection = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT).get(LOVE_TRAIN).orElse(null);
            boolean expectedPresent = Boolean.getBoolean("rotp.wsMemory.expectLoveTrain");
            helper.assertTrue((protection != null) == expectedPresent,
                    "Runtime does not match requested D4C presence: " + expectedPresent);
            if (protection != null) {
                target.addEffect(new MobEffectInstance(protection, 200));
                helper.assertTrue(target.hasEffect(protection), "Real D4C protection effect not applied");
                assertBlocked(helper, target, target, caster);
                assertBlocked(helper, stand, target, caster);
                target.removeEffect(protection);

                // The donor checks the normalized user, not a stray effect on a Stand.
                stand.addEffect(new MobEffectInstance(protection, 200));
                helper.assertTrue(!target.hasEffect(protection), "Stand-only fixture protected its user");
                assertApplied(helper, MemoryDiscService.extractTo(stand, caster).status(),
                        "Normalized user without protection");
                ItemStack disc = discFor(caster, target.getUUID());
                helper.assertTrue(!disc.isEmpty(), "Unprotected extraction did not create a disc");
                target.addEffect(new MobEffectInstance(protection, 200));
                assertApplied(helper, MemoryDiscService.restore(disc, stand).status(),
                        "Restoration while Love Train is active");
                helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted(),
                        "Protected restoration did not restore memory");
                disc.shrink(1);
                stand.removeEffect(protection);
                target.removeEffect(protection);
            }
            extractAndRestore(helper, target, target, caster);
            extractAndRestore(helper, stand, target, caster);
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void actionExtractionRejectionChargesDonorCostAndNormalizesStandTarget(GameTestHelper helper) {
        actionMatrix(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void actionRestorationRejectionChargesDonorCostAndPreservesDiscMemory(GameTestHelper helper) {
        actionMatrix(helper, true);
    }

    private static void actionMatrix(GameTestHelper helper, boolean restoring) {
        MessagePlayer caster = player(helper, "ws-action-caster");
        MessagePlayer target = player(helper, "ws-action-target");
        try {
            StandEntity performer = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            helper.assertTrue(!power.isStaminaInfinite()
                            && power.getMaxStamina() >= WhitesnakeRemoveMemoryDiscAbility.STAMINA_COST,
                    "Action cost fixture requires finite stamina with at least one full payment");
            var rawAbility = power.getAbility(MEMORY_ABILITY);
            helper.assertTrue(rawAbility instanceof WhitesnakeRemoveMemoryDiscAbility,
                    "Registered memory action is missing");
            WhitesnakeRemoveMemoryDiscAbility ability = (WhitesnakeRemoveMemoryDiscAbility) rawAbility;
            Holder<MobEffect> protection = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT).get(LOVE_TRAIN).orElse(null);
            helper.assertTrue((protection != null) == Boolean.getBoolean("rotp.wsMemory.expectLoveTrain"),
                    "Action runtime does not match requested D4C presence");
            for (LivingEntity aimed : new LivingEntity[] { target, targetStand }) {
                caster.getInventory().clearContent();
                power.setStamina(power.getMaxStamina());
                power.resetAbilityCooldowns();
                if (restoring) {
                    extractHeldDisc(helper, target, caster);
                }
                if (protection != null) {
                    target.addEffect(new MobEffectInstance(protection, 200));
                    helper.assertTrue(target.hasEffect(protection), "Protection was not applied");
                    power.setAbilityCooldown(MEMORY_ABILITY, 7);
                    power.setAbilityCooldown(STAND_ABILITY, 11);
                    assertActionBlocked(helper, ability, performer, aimed, target, caster);
                    power.setStamina(WhitesnakeRemoveMemoryDiscAbility.STAMINA_COST / 2.0F);
                    assertActionBlocked(helper, ability, performer, aimed, target, caster);
                    power.setStamina(0);
                    assertActionBlocked(helper, ability, performer, aimed, target, caster);
                    power.setStamina(power.getMaxStamina());
                    power.resetAbilityCooldowns();
                    if (restoring) {
                        ItemStack held = caster.getMainHandItem();
                        MemoryDiscItem item = (MemoryDiscItem) held.getItem();
                        helper.assertTrue(item.restoreMemory(held, aimed, item.copyMemoryPayload(held).orElseThrow()),
                                "Direct item restoration was incorrectly blocked by Love Train");
                        helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted(),
                                "Direct item helper left memory extracted");
                        caster.getInventory().clearContent();
                        target.removeEffect(protection);
                        extractHeldDisc(helper, target, caster);
                    }
                    target.removeEffect(protection);
                    // An effect on the Stand alone must not protect its normalized user.
                    targetStand.addEffect(new MobEffectInstance(protection, 200));
                    helper.assertTrue(!target.hasEffect(protection), "Stand-only effect protected the user");
                }
                caster.actionBarKeys.clear();
                target.actionBarKeys.clear();
                float stamina = power.getStamina();
                perform(ability, performer, caster, aimed);
                helper.assertTrue(WhitesnakeAddon.memoryState(target).isExtracted() != restoring,
                        "Unprotected action did not change memory state, restoring=" + restoring);
                helper.assertTrue(Math.abs(stamina - power.getStamina()
                                - WhitesnakeRemoveMemoryDiscAbility.STAMINA_COST) < 0.001F,
                        "Successful action did not charge exactly one stamina payment");
                helper.assertTrue(power.getAbilityCooldown(MEMORY_ABILITY) == WhitesnakeRemoveMemoryDiscAbility.COOLDOWN_TICKS
                                && power.getAbilityCooldown(STAND_ABILITY) == WhitesnakeRemoveMemoryDiscAbility.COOLDOWN_TICKS,
                        "Successful action did not set both donor cooldowns");
                helper.assertTrue(caster.actionBarKeys.isEmpty() && target.actionBarKeys.isEmpty(),
                        "Unprotected action emitted a false protection message");
                if (restoring) {
                    helper.assertTrue(caster.getMainHandItem().isEmpty(), "Successful restoration did not consume the disc");
                } else {
                    ItemStack disc = discFor(caster, target.getUUID());
                    helper.assertTrue(!disc.isEmpty(), "Successful extraction did not create a disc");
                    assertApplied(helper, MemoryDiscService.restore(disc, target).status(), "Extraction fixture reset");
                }
                if (protection != null) {
                    targetStand.removeEffect(protection);
                }
            }
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    private static StandEntity summon(GameTestHelper helper, MessagePlayer player) {
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "Player has no Stand attachment");
        power.setStand(WhitesnakeAddon.WHITESNAKE.get());
        helper.assertTrue(WhitesnakeAddon.WHITESNAKE.get().summon(player, power), "Stand did not summon");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null, "Summoned Stand is absent");
        stand.setPos(player.position());
        return stand;
    }

    private static void extractHeldDisc(GameTestHelper helper, MessagePlayer target, MessagePlayer caster) {
        assertApplied(helper, MemoryDiscService.extractTo(target, caster).status(), "Restoration setup");
        ItemStack disc = discFor(caster, target.getUUID()).copy();
        helper.assertTrue(!disc.isEmpty(), "Restoration setup has no disc");
        caster.getInventory().clearContent();
        caster.setItemInHand(InteractionHand.MAIN_HAND, disc);
    }

    private static void perform(WhitesnakeRemoveMemoryDiscAbility ability, StandEntity performer,
            MessagePlayer caster, LivingEntity aimed) {
        LivingComponentAction.getComponent(caster);
        LivingComponentAction.getAim(caster).setTarget(new ActionTarget(aimed));
        EntityActionInstance action = ability.initActionOnAbilityUse(caster.level(), caster, performer, null);
        LivingComponentAction.getComponent(performer).setAction(action, caster, SyncType.NO_SYNC);
        try {
            // Exercise the perform boundary, not normal input admission or the 30-tick hold.
            action.actionPerformStart();
        }
        finally {
            LivingComponentAction.getComponent(performer).setAction(null, caster, SyncType.NO_SYNC);
        }
    }

    private static void assertActionBlocked(GameTestHelper helper, WhitesnakeRemoveMemoryDiscAbility ability,
            StandEntity performer, LivingEntity aimed, MessagePlayer target, MessagePlayer caster) {
        MemoryLossState state = WhitesnakeAddon.memoryState(target);
        CompoundTag before = state.serializeNBT(target.registryAccess());
        ItemStack held = caster.getMainHandItem().copy();
        StandPower power = StandPower.get(caster);
        float stamina = power.getStamina();
        int memoryCooldown = power.getAbilityCooldown(MEMORY_ABILITY);
        int standCooldown = power.getAbilityCooldown(STAND_ABILITY);
        caster.actionBarKeys.clear();
        target.actionBarKeys.clear();
        int sounds = caster.standSounds(performer);
        perform(ability, performer, caster, aimed);
        // 1.16 played the PERFORM phase sound before standPerform, which Love Train cancelled only in its body
        helper.assertTrue(caster.standSounds(performer) - sounds == 1,
                "1.16 played whitesnake_remove_disc once at the perform Love Train protected against: "
                        + (caster.standSounds(performer) - sounds));
        helper.assertTrue(before.equals(state.serializeNBT(target.registryAccess())),
                "Protected action changed memory state");
        helper.assertTrue(ItemStack.matches(held, caster.getMainHandItem()), "Protected action changed the held disc");
        helper.assertTrue(Math.abs(power.getStamina()
                        - Math.max(0.0F, stamina - WhitesnakeRemoveMemoryDiscAbility.STAMINA_COST)) < 0.001F,
                "Protected perform did not charge exactly one donor stamina cost, clamped at zero");
        helper.assertTrue(power.getAbilityCooldown(MEMORY_ABILITY) == memoryCooldown
                        && power.getAbilityCooldown(STAND_ABILITY) == standCooldown,
                "Protected action changed cooldowns");
        helper.assertTrue(caster.actionBarKeys.equals(List.of(
                        "message.rotp_d4c.memory_extraction_blocked_by_love_train"))
                        && target.actionBarKeys.equals(List.of("message.rotp_d4c.memory_protected_by_love_train")),
                "Protected action did not deliver exactly one donor message to each normalized player");
        if (!state.isExtracted()) {
            helper.assertTrue(discFor(caster, target.getUUID()).isEmpty(), "Protected action created a disc");
        }
    }

    private static void assertBlocked(GameTestHelper helper, LivingEntity aimed,
            MessagePlayer target, MessagePlayer caster) {
        MemoryLossState state = WhitesnakeAddon.memoryState(target);
        CompoundTag before = state.serializeNBT(target.registryAccess());
        caster.actionBarKeys.clear();
        target.actionBarKeys.clear();
        Status status = MemoryDiscService.extractTo(aimed, caster).status();
        helper.assertTrue(status == Status.PROTECTED_BY_LOVE_TRAIN,
                "Love Train extraction returned " + status);
        helper.assertTrue(before.equals(state.serializeNBT(target.registryAccess()))
                        && !state.isExtracted(),
                "Blocked extraction mutated memory state");
        helper.assertTrue(discFor(caster, target.getUUID()).isEmpty(),
                "Blocked extraction created a memory disc");
        helper.assertTrue(caster.actionBarKeys.equals(List.of(
                        "message.rotp_d4c.memory_extraction_blocked_by_love_train")),
                "Caster did not receive exactly the donor action-bar message");
        helper.assertTrue(target.actionBarKeys.equals(List.of(
                        "message.rotp_d4c.memory_protected_by_love_train")),
                "Normalized target did not receive exactly the donor action-bar message");
    }

    private static void extractAndRestore(GameTestHelper helper, LivingEntity aimed,
            MessagePlayer target, MessagePlayer caster) {
        caster.actionBarKeys.clear();
        target.actionBarKeys.clear();
        assertApplied(helper, MemoryDiscService.extractTo(aimed, caster).status(),
                "Unprotected extraction");
        helper.assertTrue(WhitesnakeAddon.memoryState(target).isExtracted(),
                "Unprotected extraction did not change memory state");
        ItemStack disc = discFor(caster, target.getUUID());
        helper.assertTrue(!disc.isEmpty(), "Unprotected extraction did not create a real disc");
        assertApplied(helper, MemoryDiscService.restore(disc, aimed).status(), "Unprotected restoration");
        helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted(),
                "Restoration left the target without memory");
        helper.assertTrue(caster.actionBarKeys.isEmpty() && target.actionBarKeys.isEmpty(),
                "Unprotected path emitted a false protection message");
        disc.shrink(1);
    }

    private static void assertApplied(GameTestHelper helper, Status status, String action) {
        helper.assertTrue(status == Status.APPLIED, action + " returned " + status);
    }

    private static ItemStack discFor(ServerPlayer player, UUID target) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (MemoryDiscItem.getData(stack).filter(data -> data.targetId().equals(target)).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static MessagePlayer player(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        MessagePlayer player = new MessagePlayer(helper.getLevel(), profile, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
        player.listen(connection, cookie);
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.connection.disconnect(Component.literal("Whitesnake Love Train memory test complete"));
    }

    private static final class MessagePlayer extends ServerPlayer {
        private final List<String> actionBarKeys = new ArrayList<>();
        private final List<Packet<?>> sent = new ArrayList<>();

        private MessagePlayer(ServerLevel level, GameProfile profile, CommonListenerCookie cookie) {
            super(level.getServer(), level, profile, cookie.clientInformation());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar && actionBarKeys != null) {
                actionBarKeys.add(message.getContents() instanceof TranslatableContents translated
                        ? translated.getKey() : message.getString());
            }
            super.displayClientMessage(message, actionBar);
        }

        // The mock player is not ticked: its tracking is moved to where it stands and its own chunk counts as sent, so
        // it tracks its Stand and the Stand's sound packets reach it.
        private void listen(Connection connection, CommonListenerCookie cookie) {
            new Recording(this, connection, cookie);
            serverLevel().getChunkSource().move(this);
            this.connection.chunkSender.dropChunk(this, chunkPosition());
        }

        private int standSounds(StandEntity stand) {
            int count = 0;
            for (Packet<?> packet : List.copyOf(sent)) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof StandEntitySoundPacket sound
                        && sound.entityId() == stand.getId()
                        && sound.sound().value() == WhitesnakeAddon.WHITESNAKE_REMOVE_DISC.get()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class Recording extends ServerGamePacketListenerImpl {
        private final MessagePlayer recorder;

        private Recording(MessagePlayer player, Connection connection, CommonListenerCookie cookie) {
            super(player.getServer(), connection, player, cookie);
            this.recorder = player;
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (recorder != null) {
                recorder.sent.add(packet);
            }
            super.send(packet, listener);
        }
    }
}
