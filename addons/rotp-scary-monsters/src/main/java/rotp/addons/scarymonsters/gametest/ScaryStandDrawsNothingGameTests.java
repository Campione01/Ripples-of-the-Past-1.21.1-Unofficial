package rotp.addons.scarymonsters.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 never drew the Scary Monsters Stand. ClientInit :40 gave it GeoExampleStandRenderer (:22), whose EmptyStandModel
 * rendered nothing in the world, in the skins screen or in the Stand info screen, and GeoExampleStandEntity.tick
 * (:75-79) put FULL_INVISIBILITY (4 ticks, amplifier 2, no particles) on it every server tick, which made the client
 * cancel its RenderLivingEvent. Client classes are read as bytes here, never loaded.
 */
@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryStandDrawsNothingGameTests {
    private static final String CLIENT = "rotp/addons/scarymonsters/client/";
    private static final String EVENTS = CLIENT + "ScaryMonstersClientEvents";
    private static final String RENDERER = CLIENT + "ScaryMonstersStandRenderer";
    private static final String ADDON = "rotp/addons/scarymonsters/ScaryMonstersAddon";
    private static final String STAND_RENDERER = "rotp/core/client/entityrender/stand/StandEntityRenderer";
    private static final String STAND_MODEL = "rotp/core/client/entityrender/stand/StandEntityModel";
    private static final String MODEL_PART = "net/minecraft/client/model/geom/ModelPart";
    private static final String CREATE_DESC =
            "(Lnet/minecraft/client/model/geom/builders/LayerDefinition;)L" + STAND_MODEL + ";";

    private ScaryStandDrawsNothingGameTests() {}

    @GameTest(template = "empty", batch = "scary_draws_nothing")
    public static void theStandModelHasNoPartsAsThe116EmptyStandModel(GameTestHelper helper) {
        ClassNode events = read(EVENTS);
        Map<String, String> factories = new HashMap<>();
        String entityType = null;
        for (AbstractInsnNode insn : method(events, "registerRenderers", null).instructions) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(ADDON)) {
                entityType = field.name;
            }
            else if (insn instanceof InvokeDynamicInsnNode indy && entityType != null) {
                for (Object arg : indy.bsmArgs) {
                    if (arg instanceof Handle handle) {
                        factories.put(entityType, handle.getOwner() + "." + handle.getName());
                    }
                }
                entityType = null;
            }
        }
        require((RENDERER + ".<init>").equals(factories.get("STAND_ENTITY")),
                "1.16 drew the Stand with GeoExampleStandRenderer and EmptyStandModel; the port must register "
                        + RENDERER + " for STAND_ENTITY, it registers " + factories.get("STAND_ENTITY"));

        ClassNode renderer = read(RENDERER);
        require(STAND_RENDERER.equals(renderer.superName),
                "the Scary Monsters renderer must stay a Stand renderer, so the skins screen keeps an empty box "
                        + "as 1.16 did instead of the icon of a Stand without a renderer: " + renderer.superName);
        for (MethodNode any : renderer.methods) {
            for (AbstractInsnNode insn : any.instructions) {
                require(!(insn instanceof MethodInsnNode call && call.name.equals("bakeRoot")),
                        "the renderer bakes a skin's geometry in " + any.name + "; 1.16's model had none to draw");
            }
        }
        MethodNode create = null;
        for (MethodNode candidate : renderer.methods) {
            if (candidate.name.equals("createStandModel") && candidate.desc.equals(CREATE_DESC)
                    && (candidate.access & Opcodes.ACC_BRIDGE) == 0) {
                require(create == null, "two createStandModel overrides");
                create = candidate;
            }
        }
        require(create != null, "the renderer must make the Stand's model itself (createStandModel), "
                + "or the core bakes the skin's donor gecko geometry, which 1.16 never drew");
        List<String> made = new ArrayList<>();
        int modelParts = 0;
        for (AbstractInsnNode insn : create.instructions) {
            require(!(insn instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1),
                    "createStandModel reads the skin's geometry; 1.16's EmptyStandModel drew no part of it");
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                made.add(type.desc);
            }
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals(MODEL_PART) && call.name.equals("<init>")) {
                    require(call.desc.equals("(Ljava/util/List;Ljava/util/Map;)V"),
                            "the model root must be a ModelPart of its own cubes and children: " + call.desc);
                    modelParts++;
                }
                require(!call.name.equals("add") && !call.name.equals("put")
                                && !call.name.equals("addAll") && !call.name.equals("putAll"),
                        "createStandModel gives the empty root a cube or a child (" + call.owner + "." + call.name
                                + "); 1.16 drew nothing");
            }
        }
        require(modelParts == 1, "the model needs exactly one part, its empty root: " + modelParts);
        require(made.equals(List.of(STAND_MODEL, MODEL_PART, "java/util/ArrayList", "java/util/HashMap")),
                "createStandModel must return a Stand model on a root with an empty cube list and no children, "
                        + "it makes " + made);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scary_draws_nothing", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void theStandIsFullyInvisibleOnEveryTickAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> theStandIsFullyInvisibleBody(h));
    }

    private static void theStandIsFullyInvisibleBody(GameTestHelper helper) {
        require(ModStatusEffects.TRACKED_EFFECTS.contains(ModStatusEffects.FULL_INVISIBILITY),
                "FULL_INVISIBILITY must reach the clients, which cancel the draw of an entity that has it");
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ScaryUnseen"));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        user.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Scary Monsters user");
        StandPower power = PowerClass.STAND.attachGet(user);
        Runnable cleanup = () -> {
            if (power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(user, power);
            }
            user.discard();
        };
        StandEntity stand;
        try {
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Scary Monsters");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Scary Monsters could not be summoned");
            stand = power.getSummonedStandEntity();
        }
        catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
        long start = helper.getTick();
        long[] firstSeen = {-1L};
        // Every failure releases the user first: a FakePlayer left in the level would be the nearest player of
        // the other tests in this run.
        helper.startSequence().thenWaitUntil(() -> {
            MobEffectInstance effect = stand.getEffect(ModStatusEffects.FULL_INVISIBILITY);
            String failure = null;
            if (effect == null) {
                if (helper.getTick() - start < 4L) {
                    throw new GameTestAssertException("waiting for the Stand's first server ticks");
                }
                failure = "1.16 put FULL_INVISIBILITY on the Scary Monsters Stand every server tick; the summoned "
                        + "Stand has none after " + (helper.getTick() - start) + " ticks (its tickCount "
                        + stand.tickCount + ")";
            }
            else if (effect.getAmplifier() != 2 || effect.isVisible() || effect.isAmbient()) {
                failure = "1.16's FULL_INVISIBILITY was amplifier 2 without particles: " + effect;
            }
            else {
                if (firstSeen[0] < 0L) {
                    firstSeen[0] = helper.getTick();
                }
                if (helper.getTick() < firstSeen[0] + 6L) {
                    throw new GameTestAssertException("checking that the 4-tick effect is renewed every tick");
                }
                if (effect.getDuration() < 3) {
                    failure = "the 4-tick FULL_INVISIBILITY must be renewed every tick, it has "
                            + effect.getDuration() + " left";
                }
            }
            if (failure != null) {
                cleanup.run();
                GameTestAssertException error = new GameTestAssertException(failure);
                helper.testInfo.fail(error);
                throw error;
            }
        }).thenExecute(() -> {
            cleanup.run();
            helper.succeed();
        });
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        MethodNode found = null;
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                require(found == null, "two methods " + owner.name + "." + name);
                found = method;
            }
        }
        require(found != null, "missing " + owner.name + "." + name);
        return found;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = ScaryStandDrawsNothingGameTests.class.getResourceAsStream("/" + internalName + ".class")) {
            require(in != null, "missing class bytes of " + internalName);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
        catch (IOException e) {
            throw new GameTestAssertException("class bytes of " + internalName + ": " + e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
