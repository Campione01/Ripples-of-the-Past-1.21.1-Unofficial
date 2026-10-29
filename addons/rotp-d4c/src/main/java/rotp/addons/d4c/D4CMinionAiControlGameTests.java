package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.ability.D4CPhase2BAbility.Mode;
import rotp.addons.d4c.client.MinionAiSelectorLayout;
import rotp.addons.d4c.entity.D4CMinionAiMode;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.addons.d4c.network.OpenMinionAiSelectorPayload;
import rotp.addons.d4c.network.SelectMinionAiModePayload;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 minion_ai_control (015 aa/aa/ad.java, a StandAction): checkConditions :48-58 wanted an own minion in the user's
 * bounding box grown by 64 (:52-53) and refused with its own hard-coded text (no minion found, :54-56); the mode the
 * player then chose reached every own minion in the box 1000 blocks around the user (ak/aa/ab/aa.java:51-53).
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMinionAiControlGameTests {
    private static final String CONTROL = "minion_ai_control";

    private D4CMinionAiControlGameTests() {}

    @GameTest(template = "empty", batch = "d4cMinionAiControl", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void minionAiControlWantsAnOwnMinionInThe64BlockBoxAsIn116(GameTestHelper helper) {
        Vec3 start = helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D));
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 8)
                .cover(start.add(60.0D, 0.0D, 0.0D))
                .cover(start.add(70.0D, 0.0D, 0.0D))
                .cover(start.add(0.0D, 0.0D, 200.0D))
                .whenTicking(() -> {}, h -> body(h));
    }

    private static void body(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-minion-ai-control", true)) {
            Ability control = scene.power.getAbility(CONTROL);
            helper.assertTrue(control != null, "fixture: D4C has no " + CONTROL);
            ClientUser client = new ClientUser(helper.getLevel(), scene.owner.getGameProfile());
            client.moveTo(scene.owner.position());
            StandPower clientPower = PowerClass.STAND.attachGet(client);
            clientPower.setStand(D4CAddon.D4C.get());
            clientPower.skipProgression();
            clientPower.setStamina(clientPower.getMaxStamina());

            expect(wrong, scene.power, "no own minion", false, D4CPhase2BService.MSG_MINION_NOT_FOUND);
            D4CMinionEntity near = minion(scene, new Vec3(60.0D, 0.0D, 0.0D));
            expect(wrong, scene.power, "an own minion 60 blocks east (inside the 64 box)", true, null);
            expect(wrong, clientPower, "client, an own minion 60 blocks east", true, null);
            near.moveTo(scene.owner.position().add(70.0D, 0.0D, 0.0D));
            expect(wrong, scene.power, "the minion 70 blocks east (outside the 64 box)", false,
                    D4CPhase2BService.MSG_MINION_NOT_FOUND);
            expect(wrong, clientPower, "client, the minion 70 blocks east", false, null);

            near.moveTo(scene.owner.position().add(60.0D, 0.0D, 0.0D));
            D4CMinionEntity far = minion(scene, new Vec3(0.0D, 0.0D, 200.0D));
            D4CMinionEntity others = D4CAddon.SUMMONED_PLAYER.get().create(helper.getLevel());
            helper.assertTrue(others != null, "fixture: no minion");
            others.setNoAi(true);
            others.setNoGravity(true);
            others.moveTo(scene.owner.position().add(3.0D, 0.0D, 0.0D));
            others.setOwner(FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "d4c-other-owner")));
            helper.assertTrue(helper.getLevel().addFreshEntity(others), "fixture: the other minion could not join");
            scene.spawned.add(others);

            // 1.16 aa/aa/ad.java:60-72: the press opened the mode screen (ak/aa/aa/ai.java) and changed no mode
            int before = D4CMinionAiMode.stored(scene.owner.getPersistentData());
            boolean performed = D4CPhase2BService.execute(Mode.MINION_AI_CONTROL, helper.getLevel(), scene.owner);
            int opened = scene.payloads(OpenMinionAiSelectorPayload.class).size();
            if (!performed || opened != 1) {
                wrong.add("the press: performed " + performed + ", the selector was opened " + opened
                        + " times; 1.16 opened the mode screen once");
            }
            if (near.aiMode() != before || far.aiMode() != before
                    || D4CMinionAiMode.stored(scene.owner.getPersistentData()) != before) {
                wrong.add("the press itself changed the mode (minions " + near.aiMode() + " / " + far.aiMode()
                        + ", owner " + D4CMinionAiMode.stored(scene.owner.getPersistentData())
                        + "); 1.16 left it to the choice on the screen (" + before + ")");
            }

            // ak/aa/ab/aa.java:39-46: the chosen mode is the owner's and every own minion's within 1000 blocks
            for (int chosen : new int[] {D4CMinionAiMode.WAIT, D4CMinionAiMode.MELEE, D4CMinionAiMode.UNARMED}) {
                scene.clearMessages();
                boolean taken = D4CPhase2BService.selectMinionAiMode(scene.owner, chosen);
                if (!taken || near.aiMode() != chosen || far.aiMode() != chosen
                        || D4CMinionAiMode.stored(scene.owner.getPersistentData()) != chosen) {
                    wrong.add("chose " + chosen + ": taken " + taken + ", minions 60 / 200 blocks away " + near.aiMode()
                            + " / " + far.aiMode() + ", owner " + D4CMinionAiMode.stored(scene.owner.getPersistentData())
                            + "; 1.16 gave the owner and both minions the chosen mode");
                }
                if (others.aiMode() != D4CMinionAiMode.DEFAULT) {
                    wrong.add("chose " + chosen + ": another player's minion took mode " + others.aiMode());
                }
                if (!scene.keys().equals(List.of("rotp_d4c.message.minion_ai_mode"))) {
                    wrong.add("chose " + chosen + ": the port's mode message was not sent: " + scene.keys());
                }
                // the line names the mode as the screen's button does, not by a second set of texts
                if (!scene.firstArgumentKeys().equals(List.of(MinionAiSelectorLayout.MODE_KEYS[chosen]))) {
                    wrong.add("chose " + chosen + ": the message named the mode " + scene.firstArgumentKeys()
                            + ", the screen calls it " + MinionAiSelectorLayout.MODE_KEYS[chosen]);
                }
            }
            for (int bad : new int[] {-1, D4CMinionAiMode.COUNT}) {
                if (D4CPhase2BService.selectMinionAiMode(scene.owner, bad)
                        || near.aiMode() != D4CMinionAiMode.UNARMED) {
                    wrong.add("a mode the screen never sends (" + bad + ") was taken: minion " + near.aiMode());
                }
            }

            // the choice travels as 1.16's int (ak/aa/ab/aa.java:21-27)
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            SelectMinionAiModePayload.STREAM_CODEC.encode(buffer, new SelectMinionAiModePayload(D4CMinionAiMode.FOLLOW));
            int bytes = buffer.readableBytes();
            SelectMinionAiModePayload decoded = SelectMinionAiModePayload.STREAM_CODEC.decode(buffer);
            if (bytes != 4 || decoded.mode() != D4CMinionAiMode.FOLLOW) {
                wrong.add("the choice payload: " + bytes + " bytes, decoded mode " + decoded.mode());
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 minion_ai_control (aa/aa/ad.java:48-58, ak/aa/ab/aa.java:51): "
                + wrong);
        helper.succeed();
    }

    private static D4CMinionEntity minion(D4CGameTestScene scene, Vec3 offset) {
        int before = scene.spawned.size();
        scene.minion(offset);
        return (D4CMinionEntity) scene.spawned.get(before);
    }

    private static void expect(List<String> wrong, StandPower power, String setup, boolean usable, String key) {
        ConditionCheck check = power.getAbility(CONTROL).checkConditions(power);
        String got = check.getWarning() == null ? null
                : check.getWarning().getContents() instanceof TranslatableContents contents ? contents.getKey()
                : check.getWarning().getString();
        if (check.isPositive() != usable || !usable && !Objects.equals(key, got)) {
            wrong.add(setup + ": " + (check.isPositive() ? "usable" : "refused (" + got + ")") + ", 1.16 "
                    + (usable ? "usable" : "refused (" + key + ")"));
        }
    }

    // the user's own client's copy of it (LocalPlayer.isLocalPlayer), as D4COwnClientTargetGameTests stages it
    private static final class ClientUser extends Player {
        ClientUser(ServerLevel level, GameProfile profile) {
            super(level, BlockPos.ZERO, 0.0F, profile);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isLocalPlayer() {
            return true;
        }
    }
}
