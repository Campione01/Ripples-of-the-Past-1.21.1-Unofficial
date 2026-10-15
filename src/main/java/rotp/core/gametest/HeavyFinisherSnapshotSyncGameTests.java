package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityHeavyAttack.onClick took the heavy punch's finisher snapshot on the client too. The port takes it on
 * the server as the action starts; the client installs the action before the Stand's synced snapshot changes, so the
 * action itself must bring the server's snapshot, as it brings the phase lengths.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyFinisherSnapshotSyncGameTests {
    private static final String SUBJECT = "heavy_finisher_snapshot_sync";
    private static final float METER = 0.8F;
    private HeavyFinisherSnapshotSyncGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200, batch = SUBJECT)
    public static void heavyPunchSyncsItsFinisherSnapshotWithTheAction(GameTestHelper helper) {
        new Suite(helper).start(HeavyFinisherSnapshotSyncGameTests.class);
    }

    @AfterBatch(batch = SUBJECT)
    public static void releaseHeavyFinisherSnapshotBatch(ServerLevel level) { PhaseEntryGameTestSupport.afterBatch(SUBJECT); }

    private static final class Suite extends PhaseEntryGameTestSupport.Suite {
        Suite(GameTestHelper helper) {
            super(helper, SUBJECT, List.of("magicians_red", "crazy_diamond"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        int readyTicks;

        Scene(Suite suite, String id) { super(suite, id); }

        @Override void prepare() {
            actor(id, 1, false);
            cow();
        }

        @Override void tick() {}

        @Override void post(Entity entity) {
            super.post(entity);
            if (done || entity != stand || ++readyTicks < 2) return;
            aim();
            stand.setFinisherMeter(METER);
            float clientSnapshot = stand.getLastHeavyFinisherValue();
            premise(stand.getFinisherMeter() == METER && clientSnapshot < 0.5F,
                    "Meter " + stand.getFinisherMeter() + " with an older snapshot " + clientSnapshot);
            var press = press("heavy_punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
            premise(press.success() && press.action() instanceof StandEntityHeavyPunch, "Registered heavy punch started");
            StandEntityHeavyPunch heavy = (StandEntityHeavyPunch) press.action();
            premise(PhaseEntryGameTestSupport.near(heavy.finisherValue, METER), "Server snapshot " + heavy.finisherValue);
            float recovery = heavy.phasesLength.getFloat(ActionPhase.RECOVERY);
            premise(PhaseEntryGameTestSupport.near(recovery, StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), METER)),
                    "Server recovery " + recovery + " from the snapshot");

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            EntityActionInstance.encode(buf, heavy);
            EntityActionInstance received = EntityActionInstance.decode(level, buf);
            premise(received instanceof StandEntityHeavyPunch && !buf.isReadable(), "The heavy punch action decodes whole");
            StandEntityHeavyPunch clientHeavy = (StandEntityHeavyPunch) received;
            check(PhaseEntryGameTestSupport.near(clientHeavy.finisherValue, METER),
                    "The action a client receives carries the server's finisher snapshot " + METER
                            + " (the client's Stand still has " + clientSnapshot + "), got " + clientHeavy.finisherValue);
            check(PhaseEntryGameTestSupport.near(clientHeavy.phasesLength.getFloat(ActionPhase.RECOVERY), recovery),
                    "The action a client receives carries the server's recovery " + recovery + ", got "
                            + clientHeavy.phasesLength.getFloat(ActionPhase.RECOVERY));
            done = true;
        }
    }
}
