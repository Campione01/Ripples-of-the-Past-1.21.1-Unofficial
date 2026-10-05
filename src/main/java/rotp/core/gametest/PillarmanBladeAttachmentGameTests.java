package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Client attachment wiring only: client classes are read as resources, never loaded. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanBladeAttachmentGameTests {
    private static final String LAYER = "rotp/core/impl/powers/pillarman/client/PillarmanBladesLayer";
    private static final String MODEL = "rotp/core/impl/powers/pillarman/client/PillarmanBladesModel";
    private static final String BEND = "rotp/core/client/entityanim/playerbend/IPlayerLimbBend";
    private static final String HUMANOID = "net/minecraft/client/model/HumanoidModel";
    private static final String PART = "net/minecraft/client/model/geom/ModelPart";
    private static final String ARM = "net/minecraft/world/entity/HumanoidArm";
    private static final String POSE = "com/mojang/blaze3d/vertex/PoseStack";
    private static final String VERTEX = "com/mojang/blaze3d/vertex/VertexConsumer";
    private static final String SIDE_DESC = "(L" + ARM + ";L" + POSE + ";L" + VERTEX + ";)V";

    private PillarmanBladeAttachmentGameTests() {}

    @GameTest(template = "empty")
    public static void bladeChildrenDetachOnlyTheirInheritedBend(GameTestHelper helper) {
        ClassNode layer = readLayer();
        MethodNode ctor = method(layer, "<init>", null);
        require(ctor.desc.equals("(Lnet/minecraft/client/renderer/entity/RenderLayerParent;L" + MODEL + ";)V"),
                "BLADE-WIRE constructor model slot");
        int modelAssignments = 0;
        for (AbstractInsnNode insn : ctor.instructions) {
            if (insn instanceof FieldInsnNode f && f.owner.equals(LAYER) && f.name.equals("bladesModel")
                    && f.getOpcode() == Opcodes.PUTFIELD) {
                require(f.desc.equals("L" + MODEL + ";"), "BLADE-WIRE model field type");
                load(prev(f), 2);
                load(prev(prev(f)), 0);
                modelAssignments++;
            }
        }
        require(modelAssignments == 1, "BLADE-WIRE stores supplied private model");
        List<MethodInsnNode> setters = calls(ctor, "jojo_ripples$setBendBone");
        require(setters.size() == 2, "BLADE-WIRE two bend detachments");
        List<String> fields = new ArrayList<>();
        for (MethodInsnNode call : setters) {
            require(call.owner.equals(BEND) && call.desc.equals("(L" + PART + ";Z)V")
                    && call.getOpcode() == Opcodes.INVOKEINTERFACE, "BLADE-WIRE bend API");
            AbstractInsnNode flag = prev(call);
            AbstractInsnNode nil = prev(flag);
            AbstractInsnNode cast = prev(nil);
            require(flag.getOpcode() == Opcodes.ICONST_0 && nil.getOpcode() == Opcodes.ACONST_NULL
                    && cast instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST
                    && type.desc.equals(BEND), "BLADE-WIRE null false");
            FieldInsnNode blade = field(prev(cast), MODEL, null, "L" + PART + ";");
            load(prev(blade), 2);
            fields.add(blade.name);
        }
        require(fields.contains("bladeLeft") && fields.contains("bladeRight"), "BLADE-WIRE both children");
        for (MethodNode method : layer.methods) {
            if (method != ctor) {
                require(calls(method, "jojo_ripples$setBendBone").isEmpty(), "BLADE-WIRE no frame bend mutation");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bladeSideUsesExactlyOneParentHandAndOwnRigidChild(GameTestHelper helper) {
        ClassNode layer = readLayer();
        MethodNode side = method(layer, "renderSide", SIDE_DESC);
        require((side.access & Opcodes.ACC_STATIC) == 0, "BLADE-WIRE instance helper");
        MethodInsnNode hand = only(side, "translateToHand");
        require(hand.owner.equals(HUMANOID) && hand.desc.equals("(L" + ARM + ";L" + POSE + ";)V"),
                "BLADE-WIRE hand signature");
        load(prev(hand), 2);
        load(prev(prev(hand)), 1);
        AbstractInsnNode cast = prev(prev(prev(hand)));
        require(cast instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST
                && type.desc.equals(HUMANOID), "BLADE-WIRE parent cast");
        require(prev(cast) instanceof MethodInsnNode parent && parent.name.equals("getParentModel")
                && (parent.owner.equals(LAYER) || parent.owner.equals("net/minecraft/client/renderer/entity/layers/RenderLayer")),
                "BLADE-WIRE actual parent");
        load(prev(prev(cast)), 0);

        MethodInsnNode draw = only(side, "render");
        require(draw.owner.equals(PART) && draw.desc.equals("(L" + POSE + ";L" + VERTEX + ";II)V"),
                "BLADE-WIRE direct child draw");
        field(prev(draw), "net/minecraft/client/renderer/texture/OverlayTexture", "NO_OVERLAY", "I");
        require(number(prev(prev(draw)), 15728880), "BLADE-WIRE full bright");
        load(prev(prev(prev(draw))), 3);
        load(prev(prev(prev(prev(draw)))), 2);
        AbstractInsnNode receiver = prev(prev(prev(prev(prev(draw)))));
        require(receiver instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD, "BLADE-WIRE selected child local");
        int slot = ((VarInsnNode) receiver).var;
        VarInsnNode store = null;
        for (AbstractInsnNode insn : side.instructions) {
            if (insn instanceof VarInsnNode v && v.getOpcode() == Opcodes.ASTORE && v.var == slot) {
                require(store == null, "BLADE-WIRE one selected child assignment");
                store = v;
            }
        }
        require(store != null, "BLADE-WIRE child assignment");
        FieldInsnNode right = field(prev(store), MODEL, "bladeRight", "L" + PART + ";");
        ownModel(prev(right));
        AbstractInsnNode rightStart = prev(prev(right));
        JumpInsnNode branch = null;
        for (AbstractInsnNode insn : side.instructions) {
            if (insn instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ACMPNE
                    && next(jump.label) == rightStart) {
                require(branch == null, "BLADE-WIRE one side selector");
                branch = jump;
            }
        }
        require(branch != null, "BLADE-WIRE left/right branch");
        field(prev(branch), ARM, "LEFT", "L" + ARM + ";");
        load(prev(prev(branch)), 1);
        AbstractInsnNode leftStart = next(branch);
        load(leftStart, 0);
        field(next(leftStart), LAYER, "bladesModel", "L" + MODEL + ";");
        FieldInsnNode left = field(next(next(leftStart)), MODEL, "bladeLeft", "L" + PART + ";");
        require(next(left) instanceof JumpInsnNode merge && merge.getOpcode() == Opcodes.GOTO
                && next(merge.label) == store, "BLADE-WIRE selected child merge");
        require(side.instructions.indexOf(hand) < side.instructions.indexOf(draw), "BLADE-WIRE hand before draw");
        balancedFinally(side, 2, hand, draw);
        for (MethodNode method : layer.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    require(!call.name.equals("copyPropertiesTo") && !call.name.equals("renderToBuffer")
                            && !call.name.startsWith("translateToAnimHand") && !call.name.equals("rotateAndTranslateBack")
                            && !call.name.equals("onItemLikeLayerRender"), "BLADE-WIRE no duplicate transform path");
                    require(!call.name.equals("translateToHand") || method == side, "BLADE-WIRE hand only in helper");
                    require(method != side || !call.owner.equals(POSE)
                            || call.name.equals("pushPose") || call.name.equals("popPose"),
                            "BLADE-WIRE no extra local pose transform");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bladeLayerCallsBothSidesInsideSingleYoungScope(GameTestHelper helper) {
        MethodNode render = method(readLayer(), "render",
                "(L" + POSE + ";Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V");
        List<MethodInsnNode> sides = calls(render, "renderSide");
        require(sides.size() == 2, "BLADE-WIRE exactly two sides");
        List<String> names = new ArrayList<>();
        int consumerSlot = -1;
        for (MethodInsnNode side : sides) {
            require(side.owner.equals(LAYER) && side.desc.equals(SIDE_DESC), "BLADE-WIRE side helper binding");
            require(prev(side) instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD, "BLADE-WIRE consumer local");
            int slot = ((VarInsnNode) prev(side)).var;
            require(consumerSlot < 0 || consumerSlot == slot, "BLADE-WIRE same consumer");
            consumerSlot = slot;
            load(prev(prev(side)), 1);
            FieldInsnNode arm = field(prev(prev(prev(side))), ARM, null, "L" + ARM + ";");
            names.add(arm.name);
            load(prev(arm), 0);
        }
        require(names.contains("LEFT") && names.contains("RIGHT"), "BLADE-WIRE two distinct sides");
        MethodInsnNode scale = only(render, "scale");
        require(scale.owner.equals(POSE) && scale.desc.equals("(FFF)V"), "BLADE-WIRE young scale API");
        AbstractInsnNode arg = prev(scale);
        for (int i = 0; i < 3; i++, arg = prev(arg)) {
            require(number(arg, 0.5F), "BLADE-WIRE half scale");
        }
        load(arg, 1);
        MethodInsnNode translate = only(render, "translate");
        require(translate.owner.equals(POSE) && translate.desc.equals("(DDD)V"), "BLADE-WIRE young translate API");
        require(number(prev(translate), 0D) && number(prev(prev(translate)), .75D)
                && number(prev(prev(prev(translate))), 0D), "BLADE-WIRE young offset");
        load(prev(prev(prev(prev(translate)))), 1);
        JumpInsnNode youngBranch = null;
        for (AbstractInsnNode insn : render.instructions) {
            if (insn instanceof FieldInsnNode f && f.name.equals("young")) {
                require(youngBranch == null && next(f) instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFEQ,
                        "BLADE-WIRE young conditional");
                youngBranch = (JumpInsnNode) next(f);
                require(f.owner.equals(HUMANOID) && f.desc.equals("Z"), "BLADE-WIRE parent young");
                AbstractInsnNode cast = prev(f);
                require(cast instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST
                        && type.desc.equals(HUMANOID), "BLADE-WIRE young parent cast");
                require(prev(cast) instanceof MethodInsnNode parent && parent.name.equals("getParentModel")
                        && (parent.owner.equals(LAYER) || parent.owner.equals("net/minecraft/client/renderer/entity/layers/RenderLayer")),
                        "BLADE-WIRE young actual parent");
                load(prev(prev(cast)), 0);
            }
        }
        require(youngBranch != null && render.instructions.indexOf(youngBranch) < render.instructions.indexOf(translate)
                && render.instructions.indexOf(translate) < render.instructions.indexOf(scale)
                && render.instructions.indexOf(youngBranch.label) > render.instructions.indexOf(scale)
                && render.instructions.indexOf(youngBranch.label) <= render.instructions.indexOf(sides.get(0)),
                "BLADE-WIRE young guards both constants only");
        balancedFinally(render, 1, translate, sides.get(1));
        List<MethodInsnNode> foil = calls(render, "getFoilBufferDirect");
        require(foil.size() == 1, "BLADE-WIRE selected foil once");
        {
            MethodInsnNode call = foil.get(0);
            require(call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer")
                    && call.desc.equals("(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)L" + VERTEX + ";")
                    && prev(call).getOpcode() == Opcodes.ICONST_1 && prev(prev(call)).getOpcode() == Opcodes.ICONST_0,
                    "BLADE-WIRE foil false true");
            AbstractInsnNode textureCall = prev(prev(prev(call)));
            require(textureCall instanceof MethodInsnNode texture && texture.owner.equals("net/minecraft/client/renderer/RenderType")
                    && texture.name.equals("entityCutoutNoCull")
                    && texture.desc.equals("(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"),
                    "BLADE-WIRE blade texture render type");
            field(prev(textureCall), LAYER, "TEXTURE", "Lnet/minecraft/resources/ResourceLocation;");
            load(prev(prev(textureCall)), 2);
            require(next(call) instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE
                    && store.var == consumerSlot, "BLADE-WIRE actual foil consumer reaches both blades");
            int assignments = 0;
            for (AbstractInsnNode insn : render.instructions) {
                if (insn instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE && store.var == consumerSlot) {
                    assignments++;
                }
            }
            require(assignments == 1, "BLADE-WIRE no consumer replacement");
        }
        helper.succeed();
    }

    private static void balancedFinally(MethodNode method, int poseSlot, AbstractInsnNode first, AbstractInsnNode last) {
        MethodInsnNode push = only(method, "pushPose");
        List<MethodInsnNode> pops = calls(method, "popPose");
        require(push.owner.equals(POSE) && pops.size() == 2, "BLADE-WIRE push and two finally exits");
        load(prev(push), poseSlot);
        require(method.instructions.indexOf(push) < method.instructions.indexOf(first), "BLADE-WIRE push before work");
        TryCatchBlockNode scope = null;
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            if (block.type == null && method.instructions.indexOf(block.start) <= method.instructions.indexOf(first)
                    && method.instructions.indexOf(block.end) > method.instructions.indexOf(last)) {
                require(scope == null, "BLADE-WIRE one covering finally");
                scope = block;
            }
        }
        require(scope != null, "BLADE-WIRE draw covered by finally");
        AbstractInsnNode handler = next(scope.handler);
        require(handler instanceof VarInsnNode v && v.getOpcode() == Opcodes.ASTORE, "BLADE-WIRE exception retained");
        int errorSlot = ((VarInsnNode) handler).var;
        load(next(handler), poseSlot);
        require(next(next(handler)) instanceof MethodInsnNode cleanup && pops.contains(cleanup), "BLADE-WIRE exceptional pop");
        load(next(next(next(handler))), errorSlot);
        require(next(next(next(next(handler)))).getOpcode() == Opcodes.ATHROW, "BLADE-WIRE rethrow original");
        MethodInsnNode exceptional = (MethodInsnNode) next(next(handler));
        for (MethodInsnNode pop : pops) {
            require(pop.owner.equals(POSE) && pop.desc.equals("()V"), "BLADE-WIRE pop API");
            load(prev(pop), poseSlot);
            if (pop != exceptional) {
                require(method.instructions.indexOf(pop) > method.instructions.indexOf(last), "BLADE-WIRE normal pop after work");
                AbstractInsnNode exit = next(pop);
                if (exit instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO) {
                    exit = next(jump.label);
                }
                require(exit.getOpcode() == Opcodes.RETURN, "BLADE-WIRE normal return after pop");
            }
        }
    }

    private static void ownModel(AbstractInsnNode insn) {
        field(insn, LAYER, "bladesModel", "L" + MODEL + ";");
        load(prev(insn), 0);
    }

    private static FieldInsnNode field(AbstractInsnNode insn, String owner, String name, String desc) {
        require(insn instanceof FieldInsnNode f && f.owner.equals(owner) && (name == null || f.name.equals(name))
                && f.desc.equals(desc) && (f.getOpcode() == Opcodes.GETFIELD || f.getOpcode() == Opcodes.GETSTATIC),
                "BLADE-WIRE field " + (name == null ? "selection" : name));
        return (FieldInsnNode) insn;
    }

    private static void load(AbstractInsnNode insn, int slot) {
        require(insn instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == slot,
                "BLADE-WIRE load " + slot);
    }

    private static boolean number(AbstractInsnNode insn, double value) {
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Number n) {
            return n.doubleValue() == value;
        }
        return insn != null && value == 0 && (insn.getOpcode() == Opcodes.DCONST_0 || insn.getOpcode() == Opcodes.FCONST_0);
    }

    private static List<MethodInsnNode> calls(MethodNode method, String name) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) {
                result.add(call);
            }
        }
        return result;
    }

    private static MethodInsnNode only(MethodNode method, String name) {
        List<MethodInsnNode> found = calls(method, name);
        require(found.size() == 1, "BLADE-WIRE one " + name);
        return found.get(0);
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        MethodNode result = null;
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) {
                require(result == null, "BLADE-WIRE ambiguous " + name);
                result = method;
            }
        }
        require(result != null, "BLADE-WIRE missing " + name);
        return result;
    }

    private static AbstractInsnNode prev(AbstractInsnNode insn) {
        require(insn != null, "BLADE-WIRE missing predecessor");
        do { insn = insn.getPrevious(); } while (insn != null && insn.getOpcode() < 0);
        require(insn != null, "BLADE-WIRE empty predecessor");
        return insn;
    }

    private static AbstractInsnNode next(AbstractInsnNode insn) {
        require(insn != null, "BLADE-WIRE missing successor");
        do { insn = insn.getNext(); } while (insn != null && insn.getOpcode() < 0);
        require(insn != null, "BLADE-WIRE empty successor");
        return insn;
    }

    private static ClassNode readLayer() {
        try (InputStream in = PillarmanBladeAttachmentGameTests.class.getResourceAsStream("/" + LAYER + ".class")) {
            require(in != null, "BLADE-WIRE missing client bytes");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
        catch (IOException e) {
            throw new GameTestAssertException("BLADE-WIRE class bytes: " + e.getClass().getSimpleName());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
