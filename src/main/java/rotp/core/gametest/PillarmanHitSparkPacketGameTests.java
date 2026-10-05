package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.netty.buffer.Unpooled;
import rotp.core.PacketsRegister;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Wire execution and compiled wiring only; client bytes are never loaded or invoked. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanHitSparkPacketGameTests {
    private static final String PACKET = "rotp/core/network/s2c/TrPillarmanParticlesPacket";
    private static final String HANDLER = PACKET + "$Handler";
    private static final String CLIENT = PACKET + "$ClientSide";
    private static final String REGISTER = "rotp/core/PacketsRegister";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String PAYLOAD = "net/minecraft/network/protocol/common/custom/CustomPacketPayload";
    private static final String REGISTRAR = "net/neoforged/neoforge/network/registration/PayloadRegistrar";
    private static final String ABILITIES = "rotp/core/impl/powers/pillarman/abilities/";
    private static final String ACTION = ABILITIES + "PillarmanActionAbility";
    private static final String SPARK_DESC = "(L" + ENTITY + ";I)V";
    private static final String CLIENT_DESC = "(L" + PACKET + ";)V";
    private static final String ID = "trpillarmanparticles";

    private PillarmanHitSparkPacketGameTests() {}

    @GameTest(template = "empty", batch = "r709_pillarman_spark", timeoutTicks = 20)
    public static void sparkCodecHasTwoFixedIntsInDonorOrder(GameTestHelper helper) throws ReflectiveOperationException {
        Class<?> packetClass;
        try { packetClass = Class.forName(PACKET.replace('/', '.')); }
        catch (ClassNotFoundException absent) {
            throw new GameTestAssertException("SPARK-WIRE source-contract packet absent: " + PACKET);
        }
        require(packetClass.isRecord(), "actual packet is a record");
        var constructor = packetClass.getConstructor(int.class, int.class);
        CustomPacketPayload initial = (CustomPacketPayload) constructor.newInstance(0, 0);
        require(initial.type() != null && initial.type().id().equals(JojoMod.resLoc(ID)),
                "ordinary registration initialized the payload type");
        var components = packetClass.getRecordComponents();
        require(components.length == 2 && components[0].getName().equals("entityId")
                && components[1].getName().equals("quantity")
                && components[0].getType() == int.class && components[1].getType() == int.class,
                "record contains only entityId and quantity ints");
        Object handlerObject = Class.forName(HANDLER.replace('/', '.'))
                .getConstructor(ResourceLocation.class).newInstance(JojoMod.resLoc(ID));
        require(handlerObject instanceof PacketsRegister.PacketOGHandler<?>, "actual packet codec handler");
        @SuppressWarnings("unchecked")
        var handler = (PacketsRegister.PacketOGHandler<CustomPacketPayload>) handlerObject;
        int[][] cases = { { 42, 9 }, { -7, 12 }, { 123, 60 }, { 0, 0 },
                { Integer.MIN_VALUE, Integer.MAX_VALUE }, { Integer.MAX_VALUE, Integer.MIN_VALUE } };
        for (int[] values : cases) {
            CustomPacketPayload packet = (CustomPacketPayload) constructor.newInstance(values[0], values[1]);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            Throwable primary = null;
            try {
                handler.encode(packet, buf);
                require(buf.readableBytes() == 8, "payload body is exactly eight bytes: " + packet);
                require(buf.readInt() == values[0] && buf.readInt() == values[1] && buf.readableBytes() == 0,
                        "encode order is entityId then quantity: " + packet);
                buf.clear();
                buf.writeInt(values[0]);
                buf.writeInt(values[1]);
                require(handler.decode(buf).equals(packet) && buf.readableBytes() == 0,
                        "decode accepts independent donor-order wire: " + packet);
                buf.clear();
                handler.encode(packet, buf);
                require(handler.decode(buf).equals(packet) && buf.readableBytes() == 0,
                        "actual codec round trip: " + packet);
            }
            catch (RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            }
            finally {
                try { buf.release(); }
                catch (RuntimeException | Error cleanup) {
                    if (primary == null) { throw cleanup; }
                    primary.addSuppressed(cleanup);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "r709_pillarman_spark", timeoutTicks = 20)
    public static void sparkHasOneActualClientboundRegistrationAndDeferredHandler(GameTestHelper helper) {
        MethodNode register = method(read(REGISTER), "register", null);
        MethodInsnNode ctor = only(register, HANDLER, "<init>");
        List<AbstractInsnNode> instructions = executable(register);
        int index = instructions.indexOf(ctor);
        require(index >= 6 && index + 1 < instructions.size(), "registration argument window");
        Cursor c = new Cursor(instructions.subList(index - 6, index + 2), "registration");
        c.var(Opcodes.ALOAD, 1);
        AbstractInsnNode factory = c.take();
        require(factory instanceof InvokeDynamicInsnNode, "actual packet-type method reference");
        int clientReferences = 0;
        for (Object argument : ((InvokeDynamicInsnNode) factory).bsmArgs) {
            if (argument instanceof Handle handle && handle.getOwner().equals(REGISTRAR)) {
                require(handle.getName().equals("playToClient") && handle.getTag() == Opcodes.H_INVOKEVIRTUAL
                        && handle.getDesc().equals("(L" + PAYLOAD + "$Type;Lnet/minecraft/network/codec/StreamCodec;"
                                + "Lnet/neoforged/neoforge/network/handling/IPayloadHandler;)L" + REGISTRAR + ";"),
                        "clientbound registrar binding");
                clientReferences++;
            }
        }
        require(clientReferences == 1, "one clientbound registrar method reference");
        c.type(Opcodes.NEW, HANDLER);
        c.op(Opcodes.DUP);
        c.constant(ID);
        c.call(Opcodes.INVOKESTATIC, "rotp/core/core/JojoMod", "resLoc",
                "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;");
        c.call(Opcodes.INVOKESPECIAL, HANDLER, "<init>", "(Lnet/minecraft/resources/ResourceLocation;)V");
        c.call(Opcodes.INVOKESTATIC, REGISTER, "registerPacket",
                "(L" + REGISTRAR + ";L" + REGISTER + "$PacketType;L" + REGISTER + "$PacketOGHandler;)V");
        c.end();

        ClassNode handler = read(HANDLER);
        MethodNode handle = method(handler, "handle",
                "(L" + PACKET + ";Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V");
        c = new Cursor(executable(handle), "deferred handler");
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKESTATIC, CLIENT, "handle", CLIENT_DESC);
        c.op(Opcodes.RETURN);
        c.end();
        for (ClassNode common : List.of(read(PACKET), handler)) {
            for (MethodNode m : common.methods) {
                for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof MethodInsnNode call) {
                        require(!call.owner.startsWith("net/minecraft/client/")
                                && !call.owner.startsWith("rotp/core/client/"),
                                "common packet code has no direct client invocation: " + m.name);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "r709_pillarman_spark", timeoutTicks = 20)
    public static void sparkProducerUsesPassedTargetOnceAndTracksSelf(GameTestHelper helper) {
        Cursor c = new Cursor(executable(method(read(ACTION), "sparkEffect", SPARK_DESC)), "producer");
        c.var(Opcodes.ALOAD, 0);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "level", "()L" + LEVEL + ";");
        c.type(Opcodes.INSTANCEOF, "net/minecraft/server/level/ServerLevel");
        JumpInsnNode serverOnly = c.jump(Opcodes.IFEQ);
        c.var(Opcodes.ALOAD, 0);
        c.type(Opcodes.NEW, PACKET);
        c.op(Opcodes.DUP);
        c.var(Opcodes.ALOAD, 0);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "getId", "()I");
        c.var(Opcodes.ILOAD, 1);
        c.call(Opcodes.INVOKESPECIAL, PACKET, "<init>", "(II)V");
        c.call(Opcodes.INVOKESTATIC, PACKET, "send", "(L" + ENTITY + ";L" + PACKET + ";)V");
        require(target(serverOnly) == c.peek(), "client-level branch skips the complete producer");
        c.op(Opcodes.RETURN);
        c.end();

        c = new Cursor(executable(method(read(PACKET), "send", "(L" + ENTITY + ";L" + PACKET + ";)V")), "tracking send");
        c.var(Opcodes.ALOAD, 0);
        c.var(Opcodes.ALOAD, 1);
        c.op(Opcodes.ICONST_0);
        c.type(Opcodes.ANEWARRAY, PAYLOAD);
        c.call(Opcodes.INVOKESTATIC, "net/neoforged/neoforge/network/PacketDistributor",
                "sendToPlayersTrackingEntityAndSelf", "(L" + ENTITY + ";L" + PAYLOAD + ";[L" + PAYLOAD + ";)V");
        c.op(Opcodes.RETURN);
        c.end();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "r709_pillarman_spark", timeoutTicks = 20)
    public static void sparkFourCallersKeepTargetQuantityAndSuccessGates(GameTestHelper helper) {
        String slash = ABILITIES + "PillarmanBladeSlashAbility";
        String dash = ABILITIES + "PillarmanBladeDashAttackAbility";
        String barrage = ABILITIES + "PillarmanBladeBarrageAbility";
        ClassNode slashBody = read(slash + "$BladeSlashInstance");
        ClassNode dashBody = read(dash + "$BladeDashInstance");
        ClassNode barrageBody = read(barrage + "$BladeBarrageInstance");
        ClassNode defenseBody = read(barrage);
        noSpark(read(slash));
        noSpark(read(dash));
        MethodNode slashHit = method(slashBody, "hitTarget", "(L" + LIVING + ";L" + LEVEL + ";)V");
        MethodNode dashHit = method(dashBody, "hitTargets", "(L" + LIVING + ";L" + LEVEL + ";)V");
        MethodNode barrageHit = method(barrageBody, "hitEntity", "(L" + LEVEL + ";L" + LIVING + ";L" + LIVING + ";)V");
        MethodNode defense = method(defenseBody, "onUserIncomingDamage",
                "(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)Z");
        MethodInsnNode s = caller(slashBody, slashHit, slash, 4, 9);
        skipsSpark(slashHit, only(slashHit, LIVING, "hurt"), Opcodes.IFEQ, s);
        s = caller(dashBody, dashHit, dash, 4, 60);
        skipsSpark(dashHit, only(dashHit, "java/util/Set", "add"), Opcodes.IFEQ, s);
        skipsSpark(dashHit, only(dashHit, dashBody.name, "dealPhysicalDamage"), Opcodes.IFEQ, s);
        s = caller(barrageBody, barrageHit, barrage, 2, 12);
        skipsSpark(barrageHit, only(barrageHit, "rotp/core/util/functions/DamageUtil", "hurtThroughInvulTicks"), Opcodes.IFEQ, s);
        s = caller(defenseBody, defense, barrage, 2, 12);
        MethodInsnNode onGround = only(defense, ENTITY, "onGround");
        Cursor ground = new Cursor(List.of(previous(onGround)), "projectile ground receiver");
        ground.var(Opcodes.ALOAD, 2);
        skipsSpark(defense, onGround, Opcodes.IFNE, s);
        MethodInsnNode direct = only(defense, "net/minecraft/world/damagesource/DamageSource", "getDirectEntity");
        Cursor source = new Cursor(List.of(next(direct)), "actual direct attacking entity");
        source.var(Opcodes.ASTORE, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "r709_pillarman_spark", timeoutTicks = 20)
    public static void sparkClientBytesUseInclusiveForcedOneShotDonorArguments(GameTestHelper helper) {
        Cursor c = new Cursor(executable(method(read(CLIENT), "handle", CLIENT_DESC)), "client request shape");
        c.var(Opcodes.ALOAD, 0);
        c.field(Opcodes.GETFIELD, PACKET, "entityId", "I");
        c.call(Opcodes.INVOKESTATIC, "rotp/core/client/ClientProxy", "getEntityById", "(I)L" + ENTITY + ";");
        c.var(Opcodes.ASTORE, 1);
        c.var(Opcodes.ALOAD, 1);
        JumpInsnNode nonnull = c.jump(Opcodes.IFNONNULL);
        c.op(Opcodes.RETURN);
        require(target(nonnull) == c.peek(), "missing entity returns without retry");
        c.op(Opcodes.ICONST_0);
        c.var(Opcodes.ISTORE, 2);
        AbstractInsnNode loop = c.peek();
        c.var(Opcodes.ILOAD, 2);
        c.var(Opcodes.ALOAD, 0);
        c.field(Opcodes.GETFIELD, PACKET, "quantity", "I");
        JumpInsnNode done = c.jump(Opcodes.IF_ICMPGT);
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "level", "()L" + LEVEL + ";");
        c.field(Opcodes.GETSTATIC, "rotp/core/init/ModParticles", "LIGHT_SPARK",
                "Lnet/neoforged/neoforge/registries/DeferredHolder;");
        c.call(Opcodes.INVOKEVIRTUAL, "net/neoforged/neoforge/registries/DeferredHolder", "get", "()Ljava/lang/Object;");
        AbstractInsnNode particleCast = c.take();
        require(particleCast instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST
                && (type.desc.equals("net/minecraft/core/particles/ParticleOptions")
                        || type.desc.equals("net/minecraft/core/particles/SimpleParticleType")), "LIGHT_SPARK argument cast");
        c.op(Opcodes.ICONST_1);
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "getX", "()D");
        c.var(Opcodes.ALOAD, 1);
        c.type(Opcodes.INSTANCEOF, LIVING);
        JumpInsnNode nonliving = c.jump(Opcodes.IFEQ);
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "getY", "()D");
        c.op(Opcodes.DCONST_1);
        c.op(Opcodes.DADD);
        JumpInsnNode yMerge = c.jump(Opcodes.GOTO);
        require(target(nonliving) == c.peek(), "nonliving takes unshifted Y branch");
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "getY", "()D");
        require(target(yMerge) == c.peek(), "living Y+1 rejoins the same request");
        c.var(Opcodes.ALOAD, 1);
        c.call(Opcodes.INVOKEVIRTUAL, ENTITY, "getZ", "()D");
        for (int axis = 0; axis < 3; axis++) {
            c.call(Opcodes.INVOKESTATIC, "java/lang/Math", "random", "()D");
            c.constant(0.5D);
            c.op(Opcodes.DSUB);
            c.constant(3.0D);
            c.op(Opcodes.DDIV);
        }
        c.call(Opcodes.INVOKEVIRTUAL, LEVEL, "addParticle", "(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V");
        AbstractInsnNode increment = c.take();
        require(increment instanceof IincInsnNode i && i.var == 2 && i.incr == 1, "one loop increment");
        require(target(c.jump(Opcodes.GOTO)) == loop, "loop returns to the inclusive quantity comparison");
        require(target(done) == c.peek(), "loop exits directly without emitter or sound");
        c.op(Opcodes.RETURN);
        c.end();
        helper.succeed();
    }

    private static MethodInsnNode caller(ClassNode owner, MethodNode method, String leaf, int slot, int quantity) {
        int count = 0;
        for (MethodNode m : owner.methods) { count += calls(m, null, "sparkEffect").size(); }
        require(count == 1, "one spark consumer in " + owner.name);
        MethodInsnNode spark = only(method, null, "sparkEffect");
        require(spark.getOpcode() == Opcodes.INVOKESTATIC && spark.desc.equals(SPARK_DESC)
                && (spark.owner.equals(ACTION) || spark.owner.equals(leaf) || spark.owner.equals(owner.name)),
                "actual shared spark binding: " + owner.name);
        AbstractInsnNode amount = previous(spark);
        require(amount instanceof IntInsnNode i && i.getOpcode() == Opcodes.BIPUSH && i.operand == quantity,
                "unchanged spark quantity " + quantity);
        new Cursor(List.of(previous(amount)), "passed consumer target").var(Opcodes.ALOAD, slot);
        return spark;
    }

    private static void skipsSpark(MethodNode method, MethodInsnNode gate, int opcode, MethodInsnNode spark) {
        AbstractInsnNode branch = next(gate);
        require(branch instanceof JumpInsnNode j && j.getOpcode() == opcode
                && method.instructions.indexOf(gate) < method.instructions.indexOf(spark)
                && method.instructions.indexOf(target(j)) > method.instructions.indexOf(spark),
                "failed " + gate.name + " skips spark in " + method.name);
    }

    private static void noSpark(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            require(calls(method, null, "sparkEffect").isEmpty(), "no additional spark consumer in " + owner.name);
        }
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals(name) && (owner == null || call.owner.equals(owner))) {
                result.add(call);
            }
        }
        return result;
    }

    private static MethodInsnNode only(MethodNode method, String owner, String name) {
        List<MethodInsnNode> result = calls(method, owner, name);
        require(result.size() == 1, "one " + name + " in " + method.name + ": " + result.size());
        return result.get(0);
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                require(result == null, "unambiguous " + owner.name + "." + name);
                result = method;
            }
        }
        require(result != null, "method bytes present: " + owner.name + "." + name);
        return result;
    }

    private static ClassNode read(String owner) {
        try (InputStream in = PillarmanHitSparkPacketGameTests.class.getResourceAsStream("/" + owner + ".class")) {
            require(in != null, "class bytes present: " + owner);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
        catch (IOException failure) {
            throw new GameTestAssertException("SPARK-WIRE class bytes " + owner + ": " + failure);
        }
    }

    private static List<AbstractInsnNode> executable(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) { if (insn.getOpcode() >= 0) { result.add(insn); } }
        return result;
    }

    private static AbstractInsnNode previous(AbstractInsnNode insn) {
        do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0);
        require(insn != null, "executable predecessor");
        return insn;
    }

    private static AbstractInsnNode next(AbstractInsnNode insn) {
        do { insn = insn.getNext(); } while (insn != null && insn.getOpcode() < 0);
        require(insn != null, "executable successor");
        return insn;
    }

    private static AbstractInsnNode target(JumpInsnNode jump) { return next(jump.label); }

    private static final class Cursor {
        private final List<AbstractInsnNode> instructions;
        private final String context;
        private int index;

        Cursor(List<AbstractInsnNode> instructions, String context) {
            this.instructions = instructions;
            this.context = context;
        }
        AbstractInsnNode peek() {
            require(index < instructions.size(), context + " instruction " + index + " present");
            return instructions.get(index);
        }
        AbstractInsnNode take() { AbstractInsnNode result = peek(); index++; return result; }
        void op(int opcode) { require(take().getOpcode() == opcode, context + " opcode " + opcode + " at " + (index - 1)); }
        void var(int opcode, int slot) {
            AbstractInsnNode insn = take();
            require(insn instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == slot, context + " local " + slot);
        }
        void type(int opcode, String type) {
            AbstractInsnNode insn = take();
            require(insn instanceof TypeInsnNode t && t.getOpcode() == opcode && t.desc.equals(type), context + " type " + type);
        }
        void field(int opcode, String owner, String name, String desc) {
            AbstractInsnNode insn = take();
            require(insn instanceof FieldInsnNode f && f.getOpcode() == opcode && f.owner.equals(owner)
                    && f.name.equals(name) && f.desc.equals(desc), context + " field " + name);
        }
        void call(int opcode, String owner, String name, String desc) {
            AbstractInsnNode insn = take();
            require(insn instanceof MethodInsnNode m && m.getOpcode() == opcode && m.owner.equals(owner)
                    && m.name.equals(name) && m.desc.equals(desc), context + " call " + owner + "." + name);
        }
        void constant(Object value) {
            AbstractInsnNode insn = take();
            require(insn instanceof LdcInsnNode ldc && value.equals(ldc.cst), context + " constant " + value);
        }
        JumpInsnNode jump(int opcode) {
            AbstractInsnNode insn = take();
            require(insn instanceof JumpInsnNode jump && jump.getOpcode() == opcode, context + " branch " + opcode);
            return (JumpInsnNode) insn;
        }
        void end() { require(index == instructions.size(), context + " no extra executable instructions"); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new GameTestAssertException("SPARK-WIRE " + message); }
    }
}
