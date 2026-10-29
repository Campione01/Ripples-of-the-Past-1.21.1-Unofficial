package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

/** Registered inputs and delegated native observers; no protected attack hook is invoked by a test. */
@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopGuardGameTests {
    private static final short ATTACK = 146, GUARD = 147, CONTROL = 148, PRIME = 149;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(MadeInHeavenAddon.id("chop_guard_tests"));
    private static Suite active;
    private enum Kind { IDLE, GUARDED, CANCELED, CHANCE_FAIL, IMMUNE, ZERO_M, RAW0_EXTENSION, ZERO_STRENGTH, ORDINARY, NATIVE_FACTORY }
    private enum Shape { AXIS, TALL_OFFSET, SHORT_HIGH, OBLIQUE_TALL, WIDE_TARGET, EYE_INSIDE, NEAR_EDGE, RANGE_ZERO, CHANCE_ZERO }
    private record Spec(Kind kind, Shape shape, boolean fail) {}
    private record Oracle(double distance, double chance, Vec3 end, double enter) {}
    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}
    private record Roll(float value, double chance, Oracle donor, boolean guard, int order, String caller, Vec3 eye, AABB targetBox, float width, float height, float eyeHeight, double reach) {}
    private static final class Raw {
        final float damage;
        final boolean flag, nonlethal, guard;
        final int before;
        boolean result;
        int after;
        Raw(Scene scene, DamageSource source, float damage) {
            this.damage=damage; flag=((DamageSourceModified)source).jojo_ripples$breaksStandGuard();
            nonlethal=((DamageSourceModified)source).jojo_ripples$isNonLethal();
            guard=scene.defender.stand.isStandBlocking(); before=++scene.order;
        }
    }
    private static final class Hurt {
        final float amount;
        final boolean guard, flag, bleeding;
        final int before;
        boolean result;
        int after;
        Hurt(Scene scene, DamageSource source, float amount) {
            this.amount=amount; guard=scene.defender.stand.isStandBlocking();
            flag=((DamageSourceModified)source).jojo_ripples$breaksStandGuard();
            bleeding=scene.bleedAttemptOrder>0; before=++scene.order;
        }
    }
    private MadeInHeavenChopGuardGameTests() {}

    @GameTest(template="empty", templateNamespace="rotp_mih_melee", timeoutTicks=1600, batch="mihChopGuard04")
    public static void chopGuardBreakFollowsRawAdmissionBeforeHurt(GameTestHelper helper) {
        start(helper,false);
    }
    @GameTest(template="empty", templateNamespace="rotp_mih_melee", timeoutTicks=1600, batch="mihChopGeometry05")
    public static void chopChanceUsesDonorEyeRayAndAttackerWidth(GameTestHelper helper) {
        start(helper,true);
    }
    @GameTest(template="empty", templateNamespace="rotp_mih_melee", timeoutTicks=1600, batch="mihChopRng06")
    public static void chopZeroChanceDoesNotConsumeGuardRandomness(GameTestHelper helper) {
        start(helper,true,true);
    }
    private static void start(GameTestHelper helper,boolean geometry) {
        start(helper,geometry,false);
    }
    private static void start(GameTestHelper helper,boolean geometry,boolean zeroChance) {
        Suite suite=new Suite(helper,geometry,zeroChance);
        try {
            for(Class<?> type : new Class<?>[]{MadeInHeavenAddon.class, MadeInHeavenChopAbility.class,
                    MadeInHeavenChopAbility.MadeInHeavenChop.class, MadeInHeavenStandEntity.class,
                    StandEntityHeavyPunchAbility.class, StandEntity.class, AbilityInput.class, MadeInHeavenChopGuardGameTests.class}) {
                String actual=sha(type), expected=System.getProperty("mih.guard.expected."+type.getName());
                helper.assertTrue(expected==null || expected.equalsIgnoreCase(actual), "Native class mismatch "+type.getName());
                log("IDENTITY "+type.getName()+"="+actual+" codeSource="+sourceUri(type));
            }
            suite.listen(); helper.onEachTick(suite::tick);
        } catch(RuntimeException|Error failure) { suite.close(); throw failure; }
    }
    @AfterBatch(batch="mihChopGuard04")
    public static void releaseGuardBatch(ServerLevel level) { if(active!=null) active.close(); log("BATCH-CLEANUP active="+(active!=null)); }
    @AfterBatch(batch="mihChopGeometry05")
    public static void releaseGeometryBatch(ServerLevel level) { if(active!=null) active.close(); log("BATCH-CLEANUP active="+(active!=null)); }
    @AfterBatch(batch="mihChopRng06")
    public static void releaseRngBatch(ServerLevel level) { if(active!=null) active.close(); log("BATCH-CLEANUP active="+(active!=null)); }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final boolean geometry,zeroChance;
        final List<Spec> specs;
        final ModConfigSpec.DoubleValue multiplier=(ModConfigSpec.DoubleValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standPowerMultiplier"));
        final double originalMultiplier;
        final List<Object> listeners=new ArrayList<>();
        final List<String> failures=new ArrayList<>();
        Scene scene;
        RuntimeException observerFailure;
        float idleLoss;
        int index,checks;
        boolean closed;
        Suite(GameTestHelper helper,boolean geometry,boolean zeroChance) { helper.assertTrue(active==null,"Overlapping guard suites"); this.helper=helper; this.geometry=geometry; this.zeroChance=zeroChance; specs=specs(geometry,zeroChance); originalMultiplier=multiplier.get(); active=this; helper.testInfo.addListener(this); }
        void listen() {
            Consumer<LivingIncomingDamageEvent> incoming=event->{if(scene!=null&&!closed) scene.incoming(event);};
            Consumer<EntityTickEvent.Pre> pre=event->{if(scene!=null&&!closed) try{scene.pre(event.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};
            Consumer<EntityTickEvent.Post> post=event->{if(scene!=null&&!closed) try{scene.post(event.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};
            listeners.addAll(List.of(incoming,pre,post));
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);
        }
        void tick() {
            if(closed)return;
            try {
                if(observerFailure!=null)throw observerFailure;
                if(scene!=null&&scene.done){scene.close();scene=null;}
                if(scene==null){
                    if(index==specs.size()){log("SUMMARY fix="+(zeroChance?"06":geometry?"05":"04")+" scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"Chop guard/geometry/RNG failed; see SUMMARY");helper.succeed();return;}
                    scene=new Scene(this,specs.get(index++));scene.prepare();
                }
                scene.tick();helper.assertTrue(++scene.ticks<100,"Natural guard scene timeout "+scene.kind);
            }catch(RuntimeException|Error failure){close();throw failure;}
        }
        void check(boolean passed,String detail){checks++;log((passed?"PASS ":"FAIL ")+scene.id()+" "+detail);if(!passed)failures.add(scene.id()+": "+detail);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();multiplier.set(originalMultiplier);active=null;log("CLEANUP listeners=0 M="+multiplier.get());}
        @Override public void testStructureLoaded(GameTestInfo test){}
        @Override public void testPassed(GameTestInfo test,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo test,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldTest,GameTestInfo newTest,GameTestRunner runner){close();}
    }
    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final Shape shape;
        final boolean forceFail;
        final ServerLevel level;
        final List<Actor> actors=new ArrayList<>();
        final List<Roll> rolls=new ArrayList<>();
        final List<Float> nativeDraws=new ArrayList<>();
        final List<String> nativeCallers=new ArrayList<>();
        Actor caster,defender,primer;
        EntityActionInstance action,primeAction;
        CrazyDAngeloRockPunchEffect extension;
        Raw raw;
        Hurt hurt;
        long seed;
        float firstDraw,secondDraw,healthBefore;
        int ticks,order,incomingCalls,bleedAttemptOrder;
        boolean guardAtIncoming,immuneBefore,sampling,done,closed;
        Scene(Suite suite,Spec spec){this.suite=suite;kind=spec.kind;shape=spec.shape;forceFail=spec.fail;level=suite.helper.getLevel();}
        String id(){return suite.geometry?shape+(forceFail?"_FAIL":""):kind.name();}
        void prepare(){
            suite.multiplier.set(kind==Kind.ZERO_M?0D:1D);
            premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()==(kind==Kind.ZERO_M?0D:1D),"actual multiplier premise");
            ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);
            caster=actor(MadeInHeavenAddon.MADE_IN_HEAVEN.get(),origin,0,28,kind==Kind.NATIVE_FACTORY, true);
            StandType sp=JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            defender=actor(sp,origin.add(0,0,1.5),180,14,kind==Kind.NATIVE_FACTORY,false);
            if(suite.geometry)configureGeometry(origin);
            press(defender,"manual_control",InputMethod.CLICK,CONTROL);
            premise(defender.stand.isManuallyControlled(),"native manual control excludes passive auto-guard");
            if(kind!=Kind.IDLE&&kind!=Kind.IMMUNE){press(defender,"guard",InputMethod.HOLD,GUARD);premise(defender.stand.isStandBlocking()&&(suite.geometry||defender.stand.canBlockFromAngle(caster.stand.position())),"real held Stand guard; normal guard fixtures are front-facing");}
            if(kind==Kind.IMMUNE){primer=actor(sp,origin.add(-0.5,0,0),0,32,true,false);aim(primer);primer.stand.setFinisherMeter(0.25F);primeAction=press(primer,"heavy_punch",InputMethod.CLICK,PRIME);}
            log("START "+id()+" casterClass="+caster.stand.getClass().getName()+" targetClass="+defender.stand.getClass().getName());
        }
        void tick(){if(action==null&&primer==null&&ticks>=3)begin();}
        void begin(){
            if(kind==Kind.ZERO_STRENGTH){caster.user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(0);StandEntity.updateStandStatAttributes(caster.stand,caster.user);}
            caster.power.setStamina(caster.power.getMaxStamina());caster.stand.updateStrengthMultipliers();
            premise(!caster.power.isStaminaInfinite()&&!caster.user.isCreative()&&caster.power.getStamina()>=50,"finite initial stamina satisfies accepted Chop gate");
            if(kind!=Kind.ORDINARY)unlock(caster,"chop");
            caster.stand.setFinisherMeter(kind==Kind.ORDINARY?0.25F:0.75F);
            aim(caster);healthBefore=defender.user.getHealth();
            if(kind==Kind.RAW0_EXTENSION)defender.stand.setHealth(0.0001F);
            premise(caster.stand.canAttackEntity(defender.stand)&&defender.stand.isAlive(),"actual friendly-fire/alive admission");
            if(kind==Kind.IMMUNE)premise(StandEntity.isStandAttackInvulnerable(defender.stand)&&defender.stand.isStandBlocking(),"natural primer established RAW immunity while preserving guard");
            else premise(!StandEntity.isStandAttackInvulnerable(defender.stand),"no accidental RAW immunity before registered attack");
            if(shape==Shape.CHANCE_ZERO)premise(defender.stand.isStandBlocking()&&defender.stand.canBlockFromAngle(caster.stand.position()),"real front-facing guard for the zero-chance native damage control");
            action=press(caster,"heavy_punch",InputMethod.CLICK,ATTACK);
            premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop)==(kind!=Kind.ORDINARY),"registered ordinary/Chop action factory");
            if(kind==Kind.RAW0_EXTENSION){extension=ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get().create(level);extension.withStand(caster.power);premise(action.getPunchModifiers().contains(extension)&&defender.stand.getHealth()==0.0001F,"installed public production modifier and exact nonlethal boundary health");}
            double chance=aabbChance(caster.stand,defender.stand);
            if(suite.geometry){
                Oracle donor=donorOracle(caster.stand,defender.stand);
                seed=geometrySeed(chance,donor.chance,forceFail);firstDraw=RandomSource.create(seed).nextFloat();secondDraw=firstDraw;
                if(forceFail)premise(firstDraw>=donor.chance,"seeded donor-chance failure before attack");
                else if(Math.abs(chance-donor.chance)>0.0001)premise(firstDraw>Math.min(chance,donor.chance)&&firstDraw<Math.max(chance,donor.chance),"seeded draw distinguishes donor from AABB gap before attack");
                else premise((firstDraw<donor.chance)==(donor.chance>0),"matching/zero-range seeded control before attack");
                log("GEOMETRY-PRE "+id()+" eye="+caster.stand.getEyePosition(1)+" targetBox="+defender.stand.getBoundingBox()+" end="+donor.end+" distance="+donor.distance+" oldChance="+chance+" donorChance="+donor.chance+" draw="+firstDraw);
            }else{boolean success=kind!=Kind.CHANCE_FAIL;seed=seed(chance,success);RandomSource expectedRandom=RandomSource.create(seed);firstDraw=expectedRandom.nextFloat();secondDraw=expectedRandom.nextFloat();premise((firstDraw<chance)==success&&(secondDraw<chance)==success,"first two native seed draws qualify the same success/failure before attack");}
            log("PRESS "+id()+" ability="+((Ability)action.ability).name()+" strength="+caster.stand.getAttackDamage()+" chance="+chance+" seed="+seed+" firstNativeDraw="+firstDraw);
        }
        void pre(Entity entity){
            if(entity!=caster.stand||action==null||done||closed)return;
            sampling=false;raw=null;hurt=null;rolls.clear();nativeDraws.clear();nativeCallers.clear();incomingCalls=0;order=0;bleedAttemptOrder=0;
            defender.stand.setDeltaMovement(Vec3.ZERO);caster.stand.setDeltaMovement(Vec3.ZERO);
            if(kind==Kind.RAW0_EXTENSION)defender.stand.setHealth(0.0001F);
            immuneBefore=StandEntity.isStandAttackInvulnerable(defender.stand);
            caster.stand.getRandom().setSeed(seed);sampling=true;
        }
        void incoming(LivingIncomingDamageEvent event){
            if(!sampling||event.getEntity()!=defender.stand||event.getSource().getDirectEntity()!=caster.stand)return;
            incomingCalls++;guardAtIncoming=defender.stand.isStandBlocking();
            log("INCOMING "+kind+" amount="+event.getAmount()+" guard="+guardAtIncoming);
            if(kind==Kind.CANCELED)event.setCanceled(true);
        }
        void post(Entity entity){
            if(done||closed)return;
            if(primer!=null&&action==null&&entity==primer.stand&&primeAction.punchedTarget!=null){premise(StandEntity.isStandAttackInvulnerable(defender.stand),"unguarded natural primer established actual RAW immunity");press(defender,"guard",InputMethod.HOLD,GUARD);premise(defender.stand.isStandBlocking(),"real guard restored after primer before Chop input");begin();}
            if(entity!=caster.stand||action==null)return;
            sampling=false;if(action.punchedTarget==null)return;
            if(!suite.geometry||shape!=Shape.RANGE_ZERO)premise(action.punchedTarget.getMainEntity()==defender.stand,"natural primary target identity");
            verify();done=true;
        }
        void verify(){
            if(suite.zeroChance&&shape==Shape.CHANCE_ZERO){verifyZeroChance();return;}
            if(suite.geometry){verifyGeometry();return;}
            float loss=healthBefore-defender.user.getHealth();
            boolean admitted=kind!=Kind.IMMUNE&&kind!=Kind.RAW0_EXTENSION;
            boolean expectedBreak=admitted&&kind!=Kind.CHANCE_FAIL&&kind!=Kind.ORDINARY;
            if(kind==Kind.NATIVE_FACTORY){
                premise(caster.stand instanceof MadeInHeavenStandEntity,"actual final MIH factory control");
                suite.check(incomingCalls==1&&!guardAtIncoming,"native factory control breaks guard before Incoming");
                suite.check(!defender.stand.isStandBlocking()&&near(loss,suite.idleLoss),"native factory damage matches idle production baseline");return;
            }
            premise(raw!=null,"actual public helper captured raw argument once");
            if(kind==Kind.IMMUNE)premise(immuneBefore&&StandEntity.isStandAttackInvulnerable(defender.stand),"real immunity persists to impact");
            if(kind==Kind.RAW0_EXTENSION)premise(raw.damage==0&&raw.nonlethal&&extension!=null&&action.getPunchModifiers().contains(extension)&&hurt==null,"PORT-HELPER boundary actual raw0 with installed production modifier and no real hurt");
            else premise(raw.damage==(kind==Kind.ZERO_STRENGTH?1F:15F),"actual heavy RAW floor/value before scale");
            suite.check(raw.flag==expectedBreak,"admitted seeded guard flag at actual RAW helper entry expected="+expectedBreak+" actual="+raw.flag);
            if(kind!=Kind.ORDINARY){
                suite.check(rolls.size()==(admitted?1:0),"guard chance drawn only on admitted RAW count="+rolls.size());
                if(!rolls.isEmpty()){
                    Roll roll=rolls.getFirst();premise(roll.value==firstDraw||roll.value==secondDraw,"observed guard draw belongs to the qualified native sequence");
                    suite.check(roll.order<raw.before,"guard roll precedes the one mutating RAW helper");
                }
                suite.check(bleedAttemptOrder>0&&bleedAttemptOrder<raw.before,"native bleeding application is attempted before RAW/hurt even on veto");
            }
            if(!admitted){
                suite.check(hurt==null&&incomingCalls==0&&loss==0,"RAW veto occurs before real hurt and health change");
                suite.check(defender.stand.isStandBlocking()&&defender.stand.canStartBlocking(),"nonadmitted attempt preserves held guard/eligibility");
            }else{
                premise(hurt!=null,"one native virtual hurt recorded");
                suite.check(hurt.guard==(kind!=Kind.IDLE&&!expectedBreak),"native virtual hurt sees the pre-hurt guard outcome");
                suite.check(hurt.flag==expectedBreak&&hurt.before>raw.before&&hurt.after<raw.after,"native source flag and virtual-call order");
                if(kind!=Kind.ORDINARY)suite.check(hurt.bleeding,"native bleeding application precedes virtual hurt");
                if(kind==Kind.CANCELED||kind==Kind.ZERO_M)suite.check(!hurt.result&&loss==0&&!defender.stand.isStandBlocking(),"positive RAW break survives canceled hurt or M0");
                else if(kind==Kind.IDLE){suite.idleLoss=loss;suite.check(loss>0,"idle linked-user baseline="+loss);}
                else if(kind==Kind.GUARDED)suite.check(near(loss,suite.idleLoss),"guarded admitted hit equals idle health loss "+loss);
                else if(kind==Kind.CHANCE_FAIL||kind==Kind.ORDINARY)suite.check(defender.stand.isStandBlocking(),"chance-fail/ordinary guard remains active");
                else suite.check(loss>0&&!defender.stand.isStandBlocking(),"zero-strength floor remains an admitted hit");
            }
            log("IMPACT "+kind+" raw="+raw.damage+" flag="+raw.flag+" hurt="+(hurt==null?"none":hurt.result)+" loss="+loss+" guard="+defender.stand.isStandBlocking());
        }
        Raw beforeRaw(StandEntity actor,Entity target,DamageSource source,float amount){if(!sampling||actor!=caster.stand||target!=defender.stand)return null;healthBefore=defender.user.getHealth();raw=new Raw(this,source,amount);log("RAW "+kind+" amount="+amount+" targetHealth="+defender.stand.getHealth()+" userHealth="+healthBefore+" flag="+raw.flag+" guard="+raw.guard);return raw;}
        Hurt beforeHurt(Entity target,DamageSource source,float amount){if(!sampling||target!=defender.stand||source.getDirectEntity()!=caster.stand)return null;hurt=new Hurt(this,source,amount);return hurt;}
        void roll(float value,String caller){if(!sampling)return;StandEntity c=caster.stand;Roll roll=new Roll(value,aabbChance(c,defender.stand),donorOracle(c,defender.stand),defender.stand.isStandBlocking(),++order,caller,c.getEyePosition(1),defender.stand.getBoundingBox(),c.getBbWidth(),c.getBbHeight(),c.getEyeHeight(),c.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));rolls.add(roll);log("NATIVE-DRAW "+id()+" value="+value+" oldChance="+roll.chance+" donorChance="+roll.donor.chance+" donorDistance="+roll.donor.distance+" caller="+caller+" guard="+roll.guard+" order="+roll.order);}
        void configureGeometry(Vec3 origin){
            double casterScale=1,targetScale=1;Vec3 offset=new Vec3(0,0,1.5);
            switch(shape){
                case TALL_OFFSET->{targetScale=1.7;offset=new Vec3(0,0.5,1.3);}
                case SHORT_HIGH->{targetScale=0.5;offset=new Vec3(0,1.4,1.5);}
                case OBLIQUE_TALL->{casterScale=0.75;targetScale=1.5;offset=new Vec3(1.1,0.3,1.1);}
                case WIDE_TARGET->{targetScale=1.6;offset=new Vec3(0.8,-0.3,1.4);}
                case EYE_INSIDE,RANGE_ZERO->{targetScale=1.5;offset=Vec3.ZERO;}
                case NEAR_EDGE->offset=new Vec3(0,0,0.45);
                case CHANCE_ZERO->{targetScale=2;offset=new Vec3(0,2,1.6);}
                default->{}
            }
            caster.stand.getAttribute(Attributes.SCALE).setBaseValue(casterScale);defender.stand.getAttribute(Attributes.SCALE).setBaseValue(targetScale);
            caster.stand.refreshDimensions();defender.stand.refreshDimensions();
            Vec3 target=origin.add(offset);defender.stand.moveTo(target.x,target.y,target.z,180,0);defender.stand.setYHeadRot(180);defender.user.moveTo(target.x,target.y,target.z,180,0);defender.user.setYHeadRot(180);
            if(shape==Shape.RANGE_ZERO)caster.stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(0);
            premise(near(caster.stand.getAttributeValue(Attributes.SCALE),casterScale)&&near(defender.stand.getAttributeValue(Attributes.SCALE),targetScale),"actual native scale attributes");
            if(shape==Shape.EYE_INSIDE||shape==Shape.RANGE_ZERO)premise(defender.stand.getBoundingBox().contains(caster.stand.getEyePosition(1)),"actual eye lies inside target box");
            if(shape==Shape.RANGE_ZERO)premise(caster.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)==0,"actual zero entity reach");
            boolean valid=StandEntityPunchAbility.validatePunchTarget(caster.stand,new ActionTarget(defender.stand)).getMainEntity()==defender.stand;
            premise(valid==(shape!=Shape.RANGE_ZERO),"actual production target validation rejects zero reach even with eye inside");
            if(shape==Shape.CHANCE_ZERO)premise(valid&&donorOracle(caster.stand,defender.stand).chance==0,"admitted within-reach target has exact donor zero guard chance before input");
            log("GEOMETRY-SETUP "+id()+" casterWidth="+caster.stand.getBbWidth()+" casterHeight="+caster.stand.getBbHeight()+" casterEyeHeight="+caster.stand.getEyeHeight()+" targetWidth="+defender.stand.getBbWidth()+" targetHeight="+defender.stand.getBbHeight()+" offset="+offset+" range="+caster.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        }
        void verifyGeometry(){
            if(shape==Shape.RANGE_ZERO){
                Oracle donor=donorOracle(caster.stand,defender.stand);
                premise(caster.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)==0&&donor.chance==0,"actual zero reach and donor zero-ratio oracle");
                suite.check(action.punchedTarget.isEmpty(level),"registered Chop naturally has no target at zero reach");
                suite.check(raw==null&&hurt==null&&incomingCalls==0,"zero-reach target refusal occurs before RAW/hurt");
                suite.check(rolls.isEmpty(),"no fabricated guard roll on the unreachable zero-reach helper path");
                suite.check(defender.stand.isStandBlocking(),"zero-reach no-target control preserves held guard");
                suite.check(defender.user.getHealth()==healthBefore,"zero-reach no-target control preserves native health");
                suite.check(bleedAttemptOrder==0,"zero-reach no-target control does not invoke Chop entity-hit effects");
                log("GEOMETRY-BOUNDARY "+id()+" range=0 donorChance=0 target="+action.punchedTarget+" RAW=none roll=none");
                return;
            }
            premise(raw!=null&&raw.damage==15&&hurt!=null&&rolls.size()==1&&!immuneBefore,"one admitted native RAW/hurt/chance observation");
            Roll roll=rolls.getFirst();premise(roll.value==firstDraw,"actual guard draw equals prequalified native seed before hurt");
            boolean breaks=roll.value<roll.donor.chance;
            suite.check(raw.flag==breaks,"source flag matches independent donor eye-ray chance expected="+breaks+" actual="+raw.flag);
            suite.check(hurt.guard==!breaks,"virtual hurt sees donor-derived pre-hurt guard state");
            suite.check(defender.stand.isStandBlocking()==!breaks,"final guard state follows donor chance without a late callback");
            suite.check(roll.order<raw.before&&raw.before<hurt.before&&hurt.after<raw.after,"chance/RAW/hurt native ordering retained");
            suite.check(hurt.flag==breaks&&hurt.amount==15,"positive RAW/M1 source amount and flag retained");
            suite.check(bleedAttemptOrder>0&&bleedAttemptOrder<raw.before,"bleeding application attempt retained before RAW");
            log("GEOMETRY-IMPACT "+id()+" draw="+roll.value+" oldChance="+roll.chance+" donorChance="+roll.donor.chance+" donorDistance="+roll.donor.distance+" sourceFlag="+raw.flag+" preHurtGuard="+hurt.guard);
        }
        void verifyZeroChance(){
            Oracle donor=donorOracle(caster.stand,defender.stand);
            premise(raw!=null&&raw.damage==15&&hurt!=null&&hurt.result&&raw.result&&!immuneBefore&&donor.chance==0,"actual admitted positive RAW/native hurt with exact zero guard chance");
            float loss=healthBefore-defender.user.getHealth();premise(loss>0&&loss<0.98F,"actual guarded linked damage is below the unchanged blood-splash threshold");
            suite.check(rolls.isEmpty(),"zero chance short-circuits before the native guard nextFloat");
            suite.check(nativeDraws.isEmpty()&&nativeCallers.isEmpty(),"no native float is consumed: zero guard chance and subthreshold blood damage");
            suite.check(!raw.flag&&!hurt.flag&&raw.guard&&hurt.guard&&defender.stand.isStandBlocking(),"zero chance preserves guard and the existing source flag through hurt");
            suite.check(defender.user.getHealth()<healthBefore,"guarded zero-chance hit still reaches native health damage");
            suite.check(raw.before<hurt.before&&hurt.after<raw.after,"one RAW helper encloses the one real hurt");
            suite.check(bleedAttemptOrder>0&&bleedAttemptOrder<raw.before,"native bleeding attempt remains before RAW/hurt");
            log("RNG-IMPACT "+id()+" chance="+donor.chance+" distance="+donor.distance+" RAW="+raw.damage+" hurt="+hurt.result+" healthLoss="+loss+" guard="+defender.stand.isStandBlocking()+" guardDraws="+rolls.size()+" allNativeFloats="+nativeDraws+" callers="+nativeCallers);
        }
        Actor actor(StandType type,Vec3 position,float yaw,double speed,boolean nativeFactory,boolean mih){
            Player user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-guard-test")){
                @Override public boolean isSpectator(){return false;}
                @Override public boolean isCreative(){return false;}
            };
            user.noPhysics=true;user.setNoGravity(true);user.getAbilities().invulnerable=false;user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);user.setHealth(200);
            user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);user.moveTo(position.x,position.y,position.z,yaw,0);user.setYHeadRot(yaw);premise(level.addFreshEntity(user),"native player added");
            StandPower power=PowerClass.STAND.attachGet(user);premise(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"public registered Stand grant");power.setResolveLevel(6);
            StandEntity stand;
            if(nativeFactory){premise(type.summon(user,power),"native factory summon");stand=power.getSummonedStandEntity();}
            else{stand=new ObservedStand(this,mih?MadeInHeavenAddon.MADE_IN_HEAVEN_ENTITY.get():ModEntityTypes.STAR_PLATINUM_STAND.get(),level);stand.withStandType(type);power.setSummonedStand(stand);premise(level.addFreshEntity(stand),"observational registered EntityType added");}
            Actor actor=new Actor(user,power,type,stand);actors.add(actor);
            stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.moveTo(position.x,position.y,position.z,yaw,0);stand.setYHeadRot(yaw);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock=true;
            premise(stand.getStandType().equals(type.getId())&&stand.getUser()==user&&stand.getUserPower()==power&&power.getSummonedStandEntity()==stand,"real registry/type/owner/power binding");
            log("SETUP class="+stand.getClass().getName()+" type="+type.getId()+" width="+stand.getBbWidth()+" height="+stand.getBbHeight()+" eye="+stand.getEyeHeight()+" strength="+stand.getAttackDamage()+" speed="+stand.getAttackSpeed()+" durability="+stand.getDurability()+" reach="+stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)+" blockReach="+stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)+" stamina="+power.getStamina());
            return actor;
        }
        EntityActionInstance press(Actor actor,String name,InputMethod method,short key){
            Ability base=actor.power.getAbility(name);premise(base!=null,"registered "+name);
            if(actor==caster&&name.equals("heavy_punch")){var binding=actor.power.getMoveset().controlScheme.defaultGroup.separateBinds.get(name);premise(binding!=null&&binding.getFirst()==InputMethod.CLICK&&binding.getSecond() instanceof InputKey k&&k.device==InputKey.RMB.device&&k.keyCode==InputKey.RMB.keyCode,"registered RMB CLICK");}
            var checked=actor.power.updateAvailableMoves().getContextVariationContainer(base);premise(checked!=null&&AbilityInput.withConditionCheck(checked,actor.user,method),"production condition-checked admission "+name);
            BufferingState buffer=BufferingState.clickOnly();var held=AbilityInput.keyPress(key,checked.ability,actor.user,null,method,0,buffer,base.getAbilityId());premise(buffer.isActionSuccess,"native keyPress succeeded");
            return held!=null&&held.action instanceof EntityActionInstance a?a:actor.stand.getCurStandAction();
        }
        void aim(Actor actor){LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(defender.stand));}
        void unlock(Actor actor,String name){var result=UNLOCKS.forceUnlock(actor.power,name);premise(result==PowerSkillUnlocks.Result.UNLOCKED||result==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"public unlock "+name);}
        void premise(boolean passed,String detail){suite.helper.assertTrue(passed,id()+" premise: "+detail);}
        @Override public void close(){if(closed)return;closed=true;sampling=false;if(extension!=null&&action!=null)action.getPunchModifiers().remove(extension);for(Actor actor:actors){AbilityInput.keyRelease(ATTACK,actor.user);AbilityInput.keyRelease(PRIME,actor.user);AbilityInput.keyRelease(GUARD,actor.user);AbilityInput.keyRelease(CONTROL,actor.user);if(actor.user.hasData(ModDataAttachmentTypes.CONTROLLER))actor.user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();if(actor.power.isSummoned())actor.type.forceUnsummon(actor.user,actor.power);actor.user.discard();}}
    }
    private static final class ObservedStand extends StandEntity {
        final Scene scene;
        final TracedRandom trace;
        ObservedStand(Scene scene,EntityType<? extends StandEntity> type,Level level){super(type,level);this.scene=scene;trace=new TracedRandom(scene,this,super.getRandom());}
        @Override public RandomSource getRandom(){return trace!=null?trace:super.getRandom();}
        @Override public boolean hurtWithStandAttack(Entity target,DamageSource source,float rawDamage){Raw record=scene.beforeRaw(this,target,source,rawDamage);boolean result=super.hurtWithStandAttack(target,source,rawDamage);if(record!=null){record.result=result;record.after=++scene.order;}return result;}
        @Override public boolean hurt(DamageSource source,float amount){Hurt record=scene.beforeHurt(this,source,amount);boolean result=super.hurt(source,amount);if(record!=null){record.result=result;record.after=++scene.order;}return result;}
        @Override public boolean addEffect(MobEffectInstance effect,Entity source){if(scene!=null&&scene.sampling&&scene.defender!=null&&scene.defender.stand==this&&effect.getEffect().is(ModStatusEffects.BLEEDING)){scene.bleedAttemptOrder=++scene.order;log("BLEED-ATTEMPT "+scene.kind+" order="+scene.bleedAttemptOrder);}return super.addEffect(effect,source);}
    }
    private static final class TracedRandom implements RandomSource {
        final Scene scene;final StandEntity owner;final RandomSource delegate;
        TracedRandom(Scene scene,StandEntity owner,RandomSource delegate){this.scene=scene;this.owner=owner;this.delegate=delegate;}
        @Override public RandomSource fork(){return delegate.fork();}
        @Override public PositionalRandomFactory forkPositional(){return delegate.forkPositional();}
        @Override public void setSeed(long seed){delegate.setSeed(seed);}
        @Override public int nextInt(){return delegate.nextInt();}
        @Override public int nextInt(int bound){return delegate.nextInt(bound);}
        @Override public long nextLong(){return delegate.nextLong();}
        @Override public boolean nextBoolean(){return delegate.nextBoolean();}
        @Override public float nextFloat(){float value=delegate.nextFloat();if(scene.sampling&&scene.caster!=null&&scene.caster.stand==owner){scene.nativeDraws.add(value);StackWalker.StackFrame frame=StackWalker.getInstance().walk(frames->frames.filter(f->!f.getClassName().equals(TracedRandom.class.getName())).findFirst().orElseThrow());String nativeCaller=frame.getClassName()+"."+frame.getMethodName();scene.nativeCallers.add(nativeCaller);String caller=frame.getClassName().equals(MadeInHeavenChopAbility.MadeInHeavenChop.class.getName())&&(frame.getMethodName().equals("afterHeavyPunchHit")||frame.getMethodName().equals("attemptHeavyPunch"))?frame.getMethodName():null;log("RNG "+scene.id()+" index="+scene.nativeDraws.size()+" value="+value+" guardCaller="+caller+" nativeCaller="+nativeCaller);if(caller!=null){if(scene.suite.geometry)scene.premise(value==scene.firstDraw,"actual native pre-hurt guard draw is the prequalified geometry seed");else scene.premise((value<aabbChance(scene.caster.stand,scene.defender.stand))==(scene.kind!=Kind.CHANCE_FAIL),"actual native draw qualifies the configured chance before guard comparison");scene.roll(value,caller);}}return value;}
        @Override public double nextDouble(){return delegate.nextDouble();}
        @Override public double nextGaussian(){return delegate.nextGaussian();}
    }
    private static long seed(double chance,boolean success){for(long seed=0;seed<100000;seed++){RandomSource random=RandomSource.create(seed);float a=random.nextFloat(),b=random.nextFloat();if(success?a<chance*0.5&&b<chance*0.5:a>chance+(1-chance)*0.5&&b>chance+(1-chance)*0.5)return seed;}throw new IllegalStateException("No qualified native seed");}
    private static List<Spec> specs(boolean geometry,boolean zeroChance){List<Spec> result=new ArrayList<>();if(zeroChance){result.add(new Spec(Kind.GUARDED,Shape.CHANCE_ZERO,false));result.add(new Spec(Kind.GUARDED,Shape.AXIS,false));result.add(new Spec(Kind.CHANCE_FAIL,Shape.AXIS,true));}else if(!geometry){for(Kind kind:Kind.values())result.add(new Spec(kind,Shape.AXIS,false));}else{for(Shape shape:new Shape[]{Shape.AXIS,Shape.TALL_OFFSET,Shape.SHORT_HIGH,Shape.OBLIQUE_TALL,Shape.WIDE_TARGET,Shape.EYE_INSIDE,Shape.NEAR_EDGE,Shape.RANGE_ZERO})result.add(new Spec(Kind.GUARDED,shape,false));result.add(new Spec(Kind.CHANCE_FAIL,Shape.TALL_OFFSET,true));result.add(new Spec(Kind.CHANCE_FAIL,Shape.EYE_INSIDE,true));}return result;}
    private static long geometrySeed(double oldChance,double donorChance,boolean fail){for(long seed=0;seed<1000000;seed++){float v=RandomSource.create(seed).nextFloat();boolean qualifies=fail?v>donorChance+(1-donorChance)*0.5:Math.abs(oldChance-donorChance)>0.0001?v>Math.min(oldChance,donorChance)+0.1*Math.abs(oldChance-donorChance)&&v<Math.max(oldChance,donorChance)-0.1*Math.abs(oldChance-donorChance):donorChance>0?v<donorChance*0.5:v>0.1;if(qualifies)return seed;}throw new IllegalStateException("No native geometry seed");}
    // Independent segment-box slab oracle; it does not invoke AABB.clip or the product helper.
    private static Oracle donorOracle(StandEntity caster,LivingEntity target){
        Vec3 eye=caster.getEyePosition(1);AABB b=target.getBoundingBox();float fraction=caster.getBbHeight()==0?0:caster.getEyeHeight()/caster.getBbHeight();
        Vec3 end=new Vec3(b.minX+0.5*(b.maxX-b.minX),b.minY+fraction*(b.maxY-b.minY),b.minZ+0.5*(b.maxZ-b.minZ));
        double distance,enter=0;
        if(eye.x>=b.minX&&eye.x<b.maxX&&eye.y>=b.minY&&eye.y<b.maxY&&eye.z>=b.minZ&&eye.z<b.maxZ)distance=0;
        else{double exit=1;double[] start={eye.x,eye.y,eye.z},finish={end.x,end.y,end.z},min={b.minX,b.minY,b.minZ},max={b.maxX,b.maxY,b.maxZ};boolean intersects=true;
            for(int axis=0;axis<3;axis++){double delta=finish[axis]-start[axis];if(Math.abs(delta)<1E-12){if(start[axis]<min[axis]||start[axis]>max[axis])intersects=false;continue;}double a=(min[axis]-start[axis])/delta,c=(max[axis]-start[axis])/delta;enter=Math.max(enter,Math.min(a,c));exit=Math.min(exit,Math.max(a,c));}
            intersects&=enter<=exit&&enter<=1&&exit>=0;Vec3 hit=eye.add(end.subtract(eye).scale(enter));distance=intersects?eye.distanceTo(hit)-caster.getBbWidth()/2:-1;
        }
        double range=caster.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),ratio=range>0?1-distance/range:0;
        return new Oracle(distance,Math.max(0,Math.min(1,(float)ratio-0.25F)),end,enter);
    }
    private static double aabbChance(StandEntity caster,LivingEntity target){double range=caster.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);AABB a=caster.getBoundingBox(),b=target.getBoundingBox();double x=Math.max(Math.max(a.minX-b.maxX,b.minX-a.maxX),0),y=Math.max(Math.max(a.minY-b.maxY,b.minY-a.maxY),0),z=Math.max(Math.max(a.minZ-b.maxZ,b.minZ-a.maxZ),0);return Math.max(0,Math.min(1,(float)(range>0?1-Math.sqrt(x*x+y*y+z*z)/range:0)-0.25F));}
    private static boolean near(double actual,double expected){return Math.abs(actual-expected)<0.0001;}
    private static String sha(Class<?> type){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}catch(Exception failure){throw new IllegalStateException("Missing native class "+type.getName(),failure);}}
    private static String sourceUri(Class<?> type){try{return type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString();}catch(Exception failure){throw new IllegalStateException("Missing code source "+type.getName(),failure);}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-CHOP-GUARD "+message);}
}
