package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.JojoModConfig;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenMeleeRegistrationGameTests {
    private static final short ATTACK=176,NEXT=177;
    private static final PowerSkillUnlocks.Owner UNLOCKS=PowerSkillUnlocks.register(MadeInHeavenAddon.id("melee_registration_tests"));
    private static PhaseSuite active;
    private static CounterSuite counterActive;
    private static CooldownSuite cooldownActive;
    private enum Role { HEAVY,CHOP,GRAB,CHARGED }
    private record Spec(Role role,double speed,float meter) {}
    private MadeInHeavenMeleeRegistrationGameTests() {}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=1800,batch="mihMeleePerform12")
    public static void ordinaryHeavyAndChopPerformOneTickWithoutChangingOtherRoles(GameTestHelper helper){
        PhaseSuite suite=new PhaseSuite(helper);try{
            identity(helper,MadeInHeavenAddon.class,MadeInHeavenChopAbility.class,MadeInHeavenChopAbility.MadeInHeavenChop.class,MadeInHeavenStandEntity.class,StandEntityHeavyPunchAbility.class,StandEntityHeavyPunch.class,StandEntityHeavyPunchChargedAbility.class,StandEntityChargedHeavy.class,StandEntity.class,AbilityInput.class,EntityActionInstance.class,MadeInHeavenMeleeRegistrationGameTests.class);
            suite.listen();helper.onEachTick(suite::tick);
        }catch(RuntimeException|Error failure){suite.close();throw failure;}
    }
    @AfterBatch(batch="mihMeleePerform12")
    public static void releasePhase(ServerLevel level){if(active!=null)active.close();log("BATCH-CLEANUP active="+(active!=null));}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=1800,batch="mihMeleeCounter13")
    public static void normalPunchUsesNativeCounterHalvesAndAdmittedTail(GameTestHelper helper){CounterSuite suite=new CounterSuite(helper);try{identity(helper,MadeInHeavenAddon.class,StandEntityPunchAbility.class,StandEntityPunchAbility.StandEntityPunch.class,StandEntity.class,AbilityInput.class,MadeInHeavenMeleeRegistrationGameTests.class);suite.listen();helper.onEachTick(suite::tick);}catch(RuntimeException|Error failure){suite.close();throw failure;}}
    @AfterBatch(batch="mihMeleeCounter13")
    public static void releaseCounter(ServerLevel level){if(counterActive!=null)counterActive.close();log("COUNTER-BATCH-CLEANUP active="+(counterActive!=null));}
    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=1800,batch="mihMeleeCooldown14")
    public static void normalPunchCooldownTicksPreserveResolveCreativeSettingsAndGrab(GameTestHelper helper){CooldownSuite suite=new CooldownSuite(helper);try{identity(helper,MadeInHeavenAddon.class,Ability.class,StandPower.class,StandEntityPunchAbility.class,StandEntityPunchAbility.StandEntityPunch.class,StandEntity.class,AbilityInput.class,ActionInputBuffer.class,PlayerClientBroadcastedSettings.class,MadeInHeavenMeleeRegistrationGameTests.class);suite.listen();helper.onEachTick(suite::tick);}catch(RuntimeException|Error failure){suite.close();throw failure;}}
    @AfterBatch(batch="mihMeleeCooldown14")
    public static void releaseCooldown(ServerLevel level){if(cooldownActive!=null)cooldownActive.close();log("COOLDOWN-BATCH-CLEANUP active="+(cooldownActive!=null));}
    private enum CooldownKind { FINITE,UNLOCKED,RESOLVE,CREATIVE,NO_COOLDOWN,GRAB,BUFFERED }
    private static final class CooldownSuite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();
        CooldownScene scene;RuntimeException observerFailure;int index,checks;boolean closed;
        CooldownSuite(GameTestHelper helper){helper.assertTrue(cooldownActive==null,"Overlapping cooldown suite");this.helper=helper;cooldownActive=this;helper.testInfo.addListener(this);}
        void listen(){Consumer<EntityTickEvent.Pre> pre=e->{if(scene!=null&&!closed)try{scene.pre(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};listeners.add(pre);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);}
        void tick(){if(closed)return;try{if(observerFailure!=null)throw observerFailure;if(scene!=null&&scene.done){scene.close();scene=null;}if(scene==null){if(index==CooldownKind.values().length){log("SUMMARY fix=14 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"MIH registered punch cooldown failed; see SUMMARY");helper.succeed();return;}scene=new CooldownScene(this,CooldownKind.values()[index++]);scene.prepare();}scene.tick();helper.assertTrue(++scene.ticks<120,"Natural cooldown scene timeout");}catch(RuntimeException|Error failure){close();throw failure;}}
        void check(boolean passed,String detail){checks++;log((passed?"PASS ":"FAIL ")+"COOLDOWN/"+scene.kind+" "+detail);if(!passed)failures.add(scene.kind+": "+detail);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();cooldownActive=null;log("COOLDOWN-CLEANUP actors=0 listeners=0");}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class CooldownScene implements AutoCloseable {
        static final short FIRST=196,REPEAT=197;
        final CooldownSuite suite;final CooldownKind kind;final ServerLevel level;final List<Integer> countdown=new ArrayList<>();final List<String> hurts=new ArrayList<>();
        Player user;StandPower power;StandType type;StandEntity stand;CooldownCow target;Vec3 origin,targetOrigin;Ability base,selected;EntityActionInstance first,next,lastSuccessfulHurtAction;
        int ticks,pressUserTick,lastDelta=-1,hurtCountBeforeNext,successfulHurts,successfulHurtsBeforeNext;long pressWorldTick;float healthBefore,healthBeforeNext;boolean requestedNext,queued,done,closed;
        CooldownScene(CooldownSuite suite,CooldownKind kind){this.suite=suite;this.kind=kind;level=suite.helper.getLevel();}
        int expected(){return kind==CooldownKind.RESOLVE||kind==CooldownKind.CREATIVE||kind==CooldownKind.NO_COOLDOWN||kind==CooldownKind.GRAB?0:3;}
        void prepare(){ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);targetOrigin=origin.add(0,0,1.5);type=MadeInHeavenAddon.MADE_IN_HEAVEN.get();
            user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-cooldown")){@Override public boolean isCreative(){return getAbilities().instabuild;}@Override public boolean isSpectator(){return false;}};
            user.setNoGravity(true);user.noPhysics=true;user.getAbilities().instabuild=kind==CooldownKind.CREATIVE;user.getAbilities().invulnerable=false;user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);user.setHealth(200);user.moveTo(origin.x,origin.y,origin.z,0,0);premise(level.addFreshEntity(user),"native player added and provisionally owned");
            power=PowerClass.STAND.attachGet(user);premise(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"actual registered public MIH grant");power.setResolveLevel(kind==CooldownKind.FINITE?0:power.getMaxResolveLevel());premise(type.summon(user,power),"actual registered final MIH summon factory");stand=power.getSummonedStandEntity();stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.noPhysics=true;stand.moveTo(origin.x,origin.y,origin.z,0,0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(kind==CooldownKind.BUFFERED?6:28);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            var settings=user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());settings.standAttackTargetLock=true;settings.noStandAbilityCooldown=kind==CooldownKind.NO_COOLDOWN;
            if(kind==CooldownKind.RESOLVE)premise(user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE,400,0)),"real active Resolve effect installed, not unlock level");refresh();
            target=new CooldownCow(this);target.setNoAi(true);target.setNoGravity(true);target.noPhysics=true;target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);premise(level.addFreshEntity(target),"native delegated hurt target added");
            if(kind==CooldownKind.GRAB){var unlock=UNLOCKS.forceUnlock(power,"grab_throw");premise(unlock==PowerSkillUnlocks.Result.UNLOCKED||unlock==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"existing grab context publicly unlocked");stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);}
            premise(stand.getClass()==MadeInHeavenStandEntity.class&&stand.getUser()==user&&stand.getUserPower()==power&&stand.getStandType().equals(type.getId()),"native final class/registry/user binding");
        }
        void refresh(){StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();}
        void pre(Entity entity){if(stand==null||entity!=stand||done||closed)return;user.moveTo(origin.x,origin.y,origin.z,0,0);stand.moveTo(origin.x,origin.y,origin.z,0,0);if(target!=null){target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);target.setDeltaMovement(Vec3.ZERO);}stand.setDeltaMovement(Vec3.ZERO);refresh();}
        void tick(){if(first==null){if(ticks<3)return;begin();return;}int delta=user.tickCount-pressUserTick;if(delta<=3&&delta!=lastDelta){premise(delta==lastDelta+1,"actual consecutive native user ticks, no manual power/action tick");sample(delta);if(delta==1)repeatGate();if(delta==3)requestNext();}
            if(requestedNext&&next==null){EntityActionInstance actual=stand.getCurStandAction();if(actual!=null&&actual!=first){next=actual;suite.check(queued,"new action arrived only through actual queued world replay");log("COOLDOWN-DEQUEUE kind="+kind+" worldTick="+level.getGameTime()+" userTick="+user.tickCount+" delta="+delta+" cooldown="+power.getAbilityCooldown(selected.name())+" action="+next.getClass().getName());}}
            if(next!=null&&next.punchedTarget!=null){suite.check(next.getClass()==StandEntityPunchAbility.StandEntityPunch.class&&next.ability==selected,"actual repeated/queued registered factory and selected ability retained");suite.check(!hurts.isEmpty()&&target.getHealth()<healthBefore&&hurts.size()>hurtCountBeforeNext&&successfulHurts>successfulHurtsBeforeNext&&lastSuccessfulHurtAction==next&&target.getHealth()<healthBeforeNext,"later registered action has its own successful delegated native hurt/HP loss, not an earlier strike");suite.check(power.getAbilityCooldown("grab_punch")==0,"unconfigured grab cooldown remains zero");log("COOLDOWN-END kind="+kind+" countdown="+countdown+" selected="+selected.name()+" activeResolve="+user.hasEffect(ModStatusEffects.RESOLVE)+" creative="+power.isUserCreative()+" noCooldown="+PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power)+" queued="+queued+" hurtSnapshot="+hurtCountBeforeNext+"->"+hurts.size()+" successfulSnapshot="+successfulHurtsBeforeNext+"->"+successfulHurts+" hpSnapshot="+healthBeforeNext+"->"+target.getHealth()+" nextIdentity="+System.identityHashCode(next)+" lastSuccessfulIdentity="+System.identityHashCode(lastSuccessfulHurtAction)+" nativeHurts="+hurts);done=true;}
        }
        void begin(){refresh();base=power.getAbility("punch");premise(base!=null,"actual normal registration exists");LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));stand.setFinisherMeter(0);var checked=power.updateAvailableMoves().getContextVariationContainer(base);premise(checked!=null&&AbilityInput.withConditionCheck(checked,user,InputMethod.CLICK),"initial actual checked registered input admitted");selected=checked.ability;
            premise(selected.name().equals(kind==CooldownKind.GRAB?"grab_punch":"punch")&&selected.getAbilityUsageCategory()==(kind==CooldownKind.GRAB?AbilityUsageGroup.GRAB:AbilityUsageGroup.COMBAT),"actual normal/grab context selection and group");premise(user.hasEffect(ModStatusEffects.RESOLVE)==(kind==CooldownKind.RESOLVE)&&power.isUserCreative()==(kind==CooldownKind.CREATIVE)&&PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power)==(kind==CooldownKind.NO_COOLDOWN),"actual effect, native Creative getter and stored no-cooldown setting qualified separately");log("COOLDOWN-PREMISE kind="+kind+" resolveLevel="+power.getResolveLevel()+" maxResolveLevel="+power.getMaxResolveLevel()+" activeResolve="+user.hasEffect(ModStatusEffects.RESOLVE)+" nativeCreative="+power.isUserCreative()+" storedNoCooldown="+PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power)+" storedStamina="+power.serializeNBT(level.registryAccess()).getFloat("Stamina")+" effectiveStamina="+power.getStamina()+" maxStamina="+power.getMaxStamina()+" infinite="+power.isStaminaInfinite()+" effectiveSpeed="+stand.getAttackSpeed()+" canAttack="+stand.canAttackEntity(target)+" immune="+StandEntity.isStandAttackInvulnerable(target));premise(power.getResolveLevel()==(kind==CooldownKind.FINITE?0:kind==CooldownKind.RESOLVE?1:power.getMaxResolveLevel())&&power.getStamina()==power.getMaxStamina()&&power.isStaminaInfinite()==(kind==CooldownKind.CREATIVE)&&stand.getAttackSpeed()>0&&stand.canAttackEntity(target)&&!StandEntity.isStandAttackInvulnerable(target),"actual unlock/finite-effective bar/melee and unprimed target premises");
            suite.check(selected.getCooldown(power,-1)==expected(),"donor local additional3 and actual exemptions expected="+expected()+" actual="+selected.getCooldown(power,-1));healthBefore=target.getHealth();pressWorldTick=level.getGameTime();pressUserTick=user.tickCount;BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(FIRST,selected,user,null,InputMethod.CLICK,0,buffer,base.getAbilityId());premise(buffer.isActionSuccess,"actual initial keyPress succeeded");first=entry!=null&&entry.action instanceof EntityActionInstance action?action:stand.getCurStandAction();premise(first!=null&&first.getClass()==StandEntityPunchAbility.StandEntityPunch.class&&first.ability==selected,"actual native action factory/ability");
            sample(0);suite.check(selected.checkMainModLogicConditions(power).isPositive()==(expected()==0),"live main condition sees newly installed cooldown without claiming warm-cache invalidation");log("COOLDOWN-PRESS kind="+kind+" worldTick="+pressWorldTick+" userTick="+pressUserTick+" effectiveSpeed="+stand.getAttackSpeed()+" stored="+power.serializeNBT(level.registryAccess()).getFloat("Stamina")+" effective="+power.getStamina()+" resolveLevel="+power.getResolveLevel()+" activeResolve="+user.hasEffect(ModStatusEffects.RESOLVE)+" creativeAbilities="+user.getAbilities().instabuild+" warmCheckedPositive="+checked.conditionCheck.isPositive()+" liveMainPositive="+selected.checkMainModLogicConditions(power).isPositive()+" W="+first.phasesLength.getFloat(ActionPhase.WINDUP)+" P="+first.phasesLength.getFloat(ActionPhase.PERFORM)+" R="+first.phasesLength.getFloat(ActionPhase.RECOVERY));
        }
        void sample(int delta){lastDelta=delta;int actual=power.getAbilityCooldown(selected.name()),want=Math.max(expected()-delta,0);countdown.add(actual);suite.check(actual==want,"native user-tick countdown delta="+delta+" expected="+want+" actual="+actual);suite.check(power.isAbilityOnCooldown(selected.name())==(want>0),"public cooldown admission flag delta="+delta);log("COOLDOWN-TICK kind="+kind+" worldTick="+level.getGameTime()+" userTick="+user.tickCount+" delta="+delta+" actual="+actual+" total="+power.getAbilityCooldownTotal(selected.name())+" currentPhase="+first.getPhase()+" actualPaused="+power.isPausedInStoppedTime());premise(!power.isPausedInStoppedTime(),"native user time is not stopped");}
        void repeatGate(){var checked=power.updateAvailableMoves().getContextVariationContainer(base);boolean gate=checked!=null&&AbilityInput.withConditionCheck(checked,user,InputMethod.CLICK);suite.check(gate==(expected()==0),"real next-user-tick checked repeat refusal or exemption admission");BufferingState buffer=BufferingState.clickOnly();if(gate)AbilityInput.keyPress(REPEAT,checked.ability,user,null,InputMethod.CLICK,0,buffer,base.getAbilityId());if(expected()>0)suite.check(!buffer.isActionSuccess,"cooldown-refused input does not start another action");log("COOLDOWN-REPEAT-GATE kind="+kind+" userTick="+user.tickCount+" gate="+gate+" actualKeyPress="+gate+" success="+buffer.isActionSuccess+" cooldown="+power.getAbilityCooldown(selected.name())+" phase="+stand.getCurStandAction().getPhase());}
        void requestNext(){hurtCountBeforeNext=hurts.size();successfulHurtsBeforeNext=successfulHurts;healthBeforeNext=target.getHealth();requestedNext=true;var checked=power.updateAvailableMoves().getContextVariationContainer(base);premise(checked!=null&&checked.ability==selected&&AbilityInput.withConditionCheck(checked,user,InputMethod.CLICK),"actual post-countdown selected input eligible");BufferingState buffer=kind==CooldownKind.BUFFERED?BufferingState.clickCanBuffer():BufferingState.clickOnly();if(kind==CooldownKind.BUFFERED)premise(stand.getCurStandAction()==first&&!first.canBeCancelledInto((StandEntityPunchAbility)selected),"slow native first action still busy when actual cooldown reaches zero");var entry=AbilityInput.keyPress(REPEAT,selected,user,null,InputMethod.CLICK,0,buffer,base.getAbilityId());queued=buffer.shouldBuffer;if(kind==CooldownKind.BUFFERED){suite.check(queued&&!buffer.isActionSuccess&&entry==null,"actual eligible click is buffered through existing production input path");}else{premise(buffer.isActionSuccess,"natural recovery permits actual post-countdown clickOnly");next=entry!=null&&entry.action instanceof EntityActionInstance action?action:stand.getCurStandAction();suite.check(power.getAbilityCooldown(selected.name())==expected(),"successful repeated keyPress resets only its configured cooldown");}log("COOLDOWN-NEXT kind="+kind+" worldTick="+level.getGameTime()+" userTick="+user.tickCount+" buffered="+queued+" success="+buffer.isActionSuccess+" cooldown="+power.getAbilityCooldown(selected.name())+" phase="+stand.getCurStandAction().getPhase()+" hurtSnapshot="+hurtCountBeforeNext+" successfulSnapshot="+successfulHurtsBeforeNext+" hpSnapshot="+healthBeforeNext);}
        void premise(boolean passed,String detail){suite.helper.assertTrue(passed,"COOLDOWN/"+kind+" premise: "+detail);}
        @Override public void close(){if(closed)return;closed=true;if(user!=null){AbilityInput.keyRelease(FIRST,user);AbilityInput.keyRelease(REPEAT,user);if(stand!=null)stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);if(user.hasData(ModDataAttachmentTypes.CONTROLLER))user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();StandPower partial=PowerClass.STAND.get(user);if(partial!=null&&partial.isSummoned())type.forceUnsummon(user,partial);user.discard();}if(target!=null)target.discard();}
    }
    private static final class CooldownCow extends Cow {
        final CooldownScene scene;CooldownCow(CooldownScene scene){super(EntityType.COW,scene.level);this.scene=scene;}
        @Override public boolean hurt(DamageSource source,float amount){boolean observed=scene!=null&&scene.stand!=null&&source.getDirectEntity()==scene.stand;EntityActionInstance actualAction=observed?scene.stand.getCurStandAction():null;float health=getHealth();boolean result=super.hurt(source,amount);if(observed){String actual="worldTick="+scene.level.getGameTime()+" userTick="+scene.user.tickCount+" amount="+amount+" nativeResult="+result+" hp="+health+"->"+getHealth()+" actionIdentity="+System.identityHashCode(actualAction);scene.hurts.add(actual);if(result&&getHealth()<health){scene.successfulHurts++;scene.lastSuccessfulHurtAction=actualAction;}log("COOLDOWN-HURT kind="+scene.kind+" "+actual);}return result;}
    }
    private enum CounterKind { HURT,CANCELED,REFUSED,SHIELD,IMMUNE,ZERO_M,ZERO_COUNTER,BOAT,BOAT_REFUSED,GRAB }
    private record CounterPulse(double strength,boolean nativeHurt,int order,Vec3 before,Vec3 after) {}
    private static final class CounterHurt {
        final float amount,counter,sourceAdditional,health;final int before;boolean result;int after;
        CounterHurt(CounterScene scene,DamageSource source,float amount){this.amount=amount;counter=scene.actor.stand.guardCounter();sourceAdditional=((DamageSourceModified)source).jojo_ripples$addKnockback();health=scene.target instanceof LivingEntity living?living.getHealth():0;before=++scene.order;}
    }
    private static final class CounterSuite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();
        final ModConfigSpec.DoubleValue multiplier=(ModConfigSpec.DoubleValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standPowerMultiplier"));final double originalMultiplier;
        CounterScene scene;RuntimeException observerFailure;int index,checks;boolean closed;
        CounterSuite(GameTestHelper helper){helper.assertTrue(counterActive==null,"Overlapping counter suite");this.helper=helper;originalMultiplier=multiplier.get();counterActive=this;helper.testInfo.addListener(this);}
        void listen(){Consumer<EntityTickEvent.Pre> pre=e->{if(scene!=null&&!closed)try{scene.pre(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<EntityTickEvent.Post> post=e->{if(scene!=null&&!closed)try{scene.post(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<LivingIncomingDamageEvent> damage=e->{if(scene!=null&&!closed)scene.incoming(e);};listeners.addAll(List.of(pre,post,damage));NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);}
        void tick(){if(closed)return;try{if(observerFailure!=null)throw observerFailure;if(scene!=null&&scene.done){scene.close();scene=null;}if(scene==null){if(index==CounterKind.values().length){log("SUMMARY fix=13 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"MIH native counter pulse admission failed; see SUMMARY");helper.succeed();return;}scene=new CounterScene(this,CounterKind.values()[index++]);scene.prepare();}scene.tick();helper.assertTrue(++scene.ticks<140,"Natural counter scene timeout");}catch(RuntimeException|Error failure){close();throw failure;}}
        void check(boolean passed,String detail){checks++;log((passed?"PASS ":"FAIL ")+"COUNTER/"+scene.kind+" "+detail);if(!passed)failures.add(scene.kind+": "+detail);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();multiplier.set(originalMultiplier);counterActive=null;log("COUNTER-CLEANUP actors=0 listeners=0 M="+multiplier.get());}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class CounterScene implements AutoCloseable {
        static final short GUARD=186,PRODUCER=187,CONTROL=188;
        final CounterSuite suite;final CounterKind kind;final ServerLevel level;final List<CounterPulse> pulses=new ArrayList<>();final List<Vec3> motions=new ArrayList<>();
        Actor actor,producer;Entity target;Vec3 targetOrigin;EntityActionInstance producerAction,primeAction,action;CounterHurt hurt;
        float producerHealth,counterAtInput;int ticks,order,incoming;boolean produced,guardReleased,sampling,insideHurt,immune,done,closed;
        CounterScene(CounterSuite suite,CounterKind kind){this.suite=suite;this.kind=kind;level=suite.helper.getLevel();}
        boolean boat(){return kind==CounterKind.BOAT||kind==CounterKind.BOAT_REFUSED;}
        void prepare(){suite.multiplier.set(1D);ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);actor=new Actor(suite.helper,origin,28);targetOrigin=origin.add(0,0,1.5);
            if(boat())target=new CounterBoat(this);else if(kind==CounterKind.SHIELD){CounterPlayer player=new CounterPlayer(this);player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);player.setHealth(200);player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));player.startUsingItem(InteractionHand.OFF_HAND);target=player;}else{CounterCow cow=new CounterCow(this);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);cow.setNoAi(true);target=cow;}
            target.setNoGravity(true);target.noPhysics=true;target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,kind==CounterKind.SHIELD?180:0,0);if(target instanceof LivingEntity living)living.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);premise(level.addFreshEntity(target),"native observed target added");if(kind==CounterKind.REFUSED||kind==CounterKind.BOAT_REFUSED)target.setInvulnerable(true);
            if(kind!=CounterKind.ZERO_COUNTER){producer=new Actor(suite.helper,origin.add(0,0,1.5),28);producer.stand.moveTo(producer.origin.x,producer.origin.y,producer.origin.z,180,0);producer.user.moveTo(producer.origin.x,producer.origin.y,producer.origin.z,180,0);press(actor,"manual_control",InputMethod.CLICK,CONTROL,true);press(actor,"guard",InputMethod.HOLD,GUARD,true);premise(actor.stand.isManuallyControlled()&&actor.stand.isStandBlocking()&&actor.stand.canBlockFromAngle(producer.stand.position()),"actual registered manually controlled held guard faces native producer");producerHealth=actor.user.getHealth();}
            log("COUNTER-SETUP kind="+kind+" counter="+actor.stand.guardCounter()+" target="+target.getClass().getName()+" stableStats=controlledFiniteMax");
        }
        void tick(){if(action!=null)return;if(kind==CounterKind.ZERO_COUNTER){if(ticks>=3)begin();return;}
            if(producerAction==null&&ticks>=3){producer.stand.setFinisherMeter(0.25F);LivingComponentAction.getComponent(producer.stand).entityAim.setTarget(new ActionTarget(actor.stand));producerAction=press(producer,"heavy_punch",InputMethod.CLICK,PRODUCER,true);return;}
            if(!produced||actor.stand.getCurStandAction()!=null)return;
            premise(actor.stand.guardCounter()>0,"real guard release/current action clear retained positive public counter");
            if(kind==CounterKind.IMMUNE){if(primeAction==null&&producer.stand.getCurStandAction()==null){producer.stand.setFinisherMeter(0.25F);LivingComponentAction.getComponent(producer.stand).entityAim.setTarget(new ActionTarget(target));primeAction=press(producer,"heavy_punch",InputMethod.CLICK,PRODUCER,true);}return;}
            if(kind==CounterKind.SHIELD&& !((Player)target).isBlocking())return;begin();
        }
        void begin(){counterAtInput=actor.stand.guardCounter();premise(kind==CounterKind.ZERO_COUNTER?counterAtInput==0:counterAtInput>0,"actual zero/nonzero native counter immediately before input");suite.multiplier.set(kind==CounterKind.ZERO_M?0D:1D);premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()==(kind==CounterKind.ZERO_M?0D:1D),"actual M scaling, not RAW admission");
            if(kind==CounterKind.GRAB){var result=UNLOCKS.forceUnlock(actor.power,"grab_throw");premise(result==PowerSkillUnlocks.Result.UNLOCKED||result==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"public grab variation unlock");actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget((LivingEntity)target);}
            actor.stand.setFinisherMeter(0);LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(target));premise(actor.stand.canAttackEntity(target)&&((target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living))==(kind==CounterKind.IMMUNE)),"actual target/RAW immunity premises before registered punch");if(kind==CounterKind.SHIELD)premise(((Player)target).getTicksUsingItem()>=5&&((Player)target).isBlocking(),"real held native front shield passed its delay");
            action=press(actor,"punch",InputMethod.CLICK,ATTACK,true);premise(action instanceof StandEntityPunchAbility.StandEntityPunch&&(action.ability.getAbilityUsageCategory()==AbilityUsageGroup.GRAB)==(kind==CounterKind.GRAB)&&actor.stand.guardCounter()==counterAtInput,"actual registered normal/grab punch factory and retained native counter");
            log("COUNTER-PRESS kind="+kind+" counter="+counterAtInput+" ability="+((Ability)action.ability).name()+" rawFormula="+(actor.stand.getAttackDamage()*0.25F));
        }
        void pre(Entity entity){if(entity!=actor.stand||done||closed)return;sampling=false;for(Actor a:producer==null?List.of(actor):List.of(actor,producer)){float yaw=a==actor?0:180;Vec3 at=a==producer&&boat()&&produced?a.origin.add(4,0,0):a.origin;a.stand.moveTo(at.x,at.y,at.z,yaw,0);a.user.moveTo(at.x,at.y,at.z,yaw,0);a.stand.setDeltaMovement(Vec3.ZERO);a.user.setDeltaMovement(Vec3.ZERO);a.power.setStamina(a.power.getMaxStamina());a.stand.updateStrengthMultipliers();}
            target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,kind==CounterKind.SHIELD?180:0,0);target.setDeltaMovement(Vec3.ZERO);
            // 1.16 kept no pressed entity that is not living (StandEntityAction.checkStandTarget) and the strike aimed again with
            // the precision ray from the manually controlled Stand's eyes, which takes a player or Stand first: once the
            // counter is produced the producer, standing where the boat is, steps aside and the Stand looks at the boat
            if(boat()&&produced)actor.stand.lookAt(EntityAnchorArgument.Anchor.EYES,target.getBoundingBox().getCenter());
            if(action==null)return;hurt=null;pulses.clear();motions.clear();incoming=0;order=0;immune=target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living);premise(immune==(kind==CounterKind.IMMUNE)&&actor.stand.guardCounter()==counterAtInput&&actor.stand.getAttackDamage()==15&&!actor.power.isStaminaInfinite(),"actual positiveRAW/effective strength/counter/immunity at natural attack tick");sampling=true;
        }
        void incoming(LivingIncomingDamageEvent event){if(!sampling||event.getEntity()!=target||event.getSource().getDirectEntity()!=actor.stand)return;incoming++;if(kind==CounterKind.CANCELED)event.setCanceled(true);}
        void post(Entity entity){if(done||closed)return;if(producer!=null&&entity==producer.stand){if(!produced&&producerAction!=null&&producerAction.punchedTarget!=null){premise(producerAction.punchedTarget.getMainEntity()==actor.stand&&actor.stand.isCurrentAttackBlocked()&&actor.stand.guardCounter()>0&&actor.user.getHealth()<producerHealth,"actual native blocked producer damage created positive counter");log("COUNTER-PRODUCER kind="+kind+" nativeHealthLoss="+(producerHealth-actor.user.getHealth())+" counter="+actor.stand.guardCounter()+" blocked=true");AbilityInput.keyRelease(GUARD,actor.user);guardReleased=true;produced=true;}
                if(action==null&&primeAction!=null&&primeAction.punchedTarget!=null){premise(primeAction.punchedTarget.getMainEntity()==target&&target instanceof LivingEntity living&&StandEntity.isStandAttackInvulnerable(living),"real registered primer established canonical RAW immunity");begin();}}
            if(entity!=actor.stand||action==null)return;sampling=false;if(action.punchedTarget==null)return;premise(action.punchedTarget.getMainEntity()==target,"natural primary target identity");
            if(kind==CounterKind.IMMUNE){suite.check(hurt==null&&pulses.isEmpty()&&motions.isEmpty()&&incoming==0,"canonical RAW immune has no real hurt/counter pulse/tail");}
            else{premise(hurt!=null,"one actual delegated target hurt");boolean expected=kind!=CounterKind.CANCELED&&kind!=CounterKind.REFUSED&&kind!=CounterKind.SHIELD&&kind!=CounterKind.BOAT_REFUSED;premise(hurt.result==expected,"qualified native real hurt result; Cow M0 returns true without health loss");boolean detached=kind!=CounterKind.GRAB;float half=counterAtInput*0.5F;List<CounterPulse> extras=pulses.stream().filter(p->!p.nativeHurt).toList();
                suite.check(hurt.sourceAdditional==(detached?0:counterAtInput),"actual damage source does not fold normal counter, grab legacy source unchanged");
                if(boat()){suite.check(!motions.isEmpty()&&near(target.getDeltaMovement(),new Vec3(0,0,half)),"admitted actual boat success/refusal gets donor half-counter replacement tail");}
                else{suite.check(extras.size()==(detached&&half>0?(hurt.result?2:1):0),"living hurt two counter halves or admitted no-hurt tail; zero/grab controls");suite.check(extras.stream().allMatch(p->p.order>hurt.after&&near(p.strength,half)),"each actual detached counter pulse has donor magnitude and post-hurt order");if(detached&&half>0)suite.check(extras.stream().allMatch(p->!near(p.before,p.after)),"actual detached counter pulses write nonzero velocity");}
                suite.check(kind==CounterKind.ZERO_M?hurt.amount==0:hurt.amount==3.75F,"actual M0 scaled-zero versus positive3.75 native amount");
                if(target instanceof LivingEntity living)suite.check(hurt.result&&kind!=CounterKind.ZERO_M?living.getHealth()<hurt.health:living.getHealth()==hurt.health,"real health outcome retained, including true-return zero-HP-loss Cow M0");
            }
            suite.check(near(actor.stand.getFinisherMeter(),0.2F),"donor/current perform and entity-attempt finisher increments .1+.1 retained");
            log("COUNTER-IMPACT kind="+kind+" counter="+counterAtInput+" immune="+immune+" hurt="+(hurt==null?"none":hurt.amount+"/"+hurt.result+" sourceExtra="+hurt.sourceAdditional+" @"+hurt.before+".."+hurt.after)+" pulses="+pulses+" motions="+motions+" finalVelocity="+target.getDeltaMovement()+" incoming="+incoming+" actualFinisher="+actor.stand.getFinisherMeter()+" actualHealth="+(target instanceof LivingEntity living?living.getHealth():"vehicle"));done=true;
        }
        CounterHurt beforeHurt(Entity entity,DamageSource source,float amount){if(!sampling||entity!=target||source.getDirectEntity()!=actor.stand)return null;hurt=new CounterHurt(this,source,amount);insideHurt=true;return hurt;}
        void afterHurt(CounterHurt record,boolean result){if(record!=null){record.result=result;record.after=++order;insideHurt=false;log("COUNTER-HURT kind="+kind+" nativeAmount="+record.amount+" nativeResult="+result+" hpBefore="+record.health+" hpAfter="+(target instanceof LivingEntity living?living.getHealth():"vehicle")+" sourceExtra="+record.sourceAdditional+" counter="+record.counter);}}
        void knockback(LivingEntity entity,double strength,double x,double z,Runnable nativeCall){boolean observed=sampling&&entity==target;int n=observed?++order:0;Vec3 before=entity.getDeltaMovement();nativeCall.run();if(observed)pulses.add(new CounterPulse(strength,insideHurt,n,before,entity.getDeltaMovement()));}
        EntityActionInstance press(Actor a,String name,InputMethod method,short key,boolean required){Ability base=a.power.getAbility(name);premise(base!=null,"registered "+name);var checked=a.power.updateAvailableMoves().getContextVariationContainer(base);boolean gate=checked!=null&&AbilityInput.withConditionCheck(checked,a.user,method);if(!gate){if(required)premise(false,"checked input refused "+name);return null;}BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(key,checked.ability,a.user,null,method,0,buffer,base.getAbilityId());if(required)premise(buffer.isActionSuccess,"native keyPress succeeded "+name);return entry!=null&&entry.action instanceof EntityActionInstance action?action:a.stand.getCurStandAction();}
        void premise(boolean passed,String detail){suite.helper.assertTrue(passed,"COUNTER/"+kind+" premise: "+detail);}
        @Override public void close(){if(closed)return;closed=true;sampling=false;for(Actor a:actor==null?List.<Actor>of():producer==null?List.of(actor):List.of(actor,producer)){AbilityInput.keyRelease(GUARD,a.user);AbilityInput.keyRelease(PRODUCER,a.user);AbilityInput.keyRelease(CONTROL,a.user);a.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);a.close();}if(target!=null)target.discard();}
    }
    private static final class CounterCow extends Cow {
        final CounterScene scene;CounterCow(CounterScene scene){super(EntityType.COW,scene.level);this.scene=scene;}
        @Override public boolean hurt(DamageSource source,float amount){CounterHurt record=scene.beforeHurt(this,source,amount);boolean result=super.hurt(source,amount);scene.afterHurt(record,result);return result;}
        @Override public void knockback(double strength,double x,double z){if(scene==null)super.knockback(strength,x,z);else scene.knockback(this,strength,x,z,()->super.knockback(strength,x,z));}
    }
    private static final class CounterPlayer extends Player {
        final CounterScene scene;CounterPlayer(CounterScene scene){super(scene.level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-counter-shield"));this.scene=scene;getAbilities().invulnerable=false;}
        @Override public boolean isCreative(){return false;}@Override public boolean isSpectator(){return false;}
        @Override public boolean hurt(DamageSource source,float amount){CounterHurt record=scene.beforeHurt(this,source,amount);boolean result=super.hurt(source,amount);scene.afterHurt(record,result);return result;}
        @Override public void knockback(double strength,double x,double z){if(scene==null)super.knockback(strength,x,z);else scene.knockback(this,strength,x,z,()->super.knockback(strength,x,z));}
    }
    private static final class CounterBoat extends Boat {
        final CounterScene scene;CounterBoat(CounterScene scene){super(EntityType.BOAT,scene.level);this.scene=scene;}
        @Override public boolean hurt(DamageSource source,float amount){CounterHurt record=scene.beforeHurt(this,source,amount);boolean result=super.hurt(source,amount);scene.afterHurt(record,result);return result;}
        @Override public void setDeltaMovement(Vec3 value){super.setDeltaMovement(value);if(scene!=null&&scene.sampling&&scene.target==this)scene.motions.add(getDeltaMovement());}
    }
    private static final class Actor implements AutoCloseable {
        final Player user;final StandPower power;final StandType type;final StandEntity stand;final Vec3 origin;
        Actor(GameTestHelper helper,Vec3 origin,double speed){
            this.origin=origin;ServerLevel level=helper.getLevel();type=MadeInHeavenAddon.MADE_IN_HEAVEN.get();
            user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-registration")){@Override public boolean isCreative(){return false;}@Override public boolean isSpectator(){return false;}};
            try{
                user.setNoGravity(true);user.noPhysics=true;user.getAbilities().invulnerable=false;user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);user.setHealth(200);user.moveTo(origin.x,origin.y,origin.z,0,0);helper.assertTrue(level.addFreshEntity(user),"Setup: actual player added");
                power=PowerClass.STAND.attachGet(user);helper.assertTrue(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"Setup: public registered MIH grant");power.setResolveLevel(6);helper.assertTrue(type.summon(user,power),"Setup: actual registered MIH factory");stand=power.getSummonedStandEntity();
                stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.noPhysics=true;stand.moveTo(origin.x,origin.y,origin.z,0,0);
                user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();
                user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock=true;
                helper.assertTrue(stand.getClass()==MadeInHeavenStandEntity.class&&stand.getUser()==user&&stand.getUserPower()==power&&stand.getStandType().equals(type.getId())&&stand.getAttackSpeed()==speed&&power.getStamina()==power.getMaxStamina()&&!power.isStaminaInfinite(),"Setup: actual final factory/registry/effective speed/finite max-stamina binding");
                log("ACTOR speed="+speed+" effectiveSpeed="+stand.getAttackSpeed()+" strength="+stand.getAttackDamage()+" stored="+power.serializeNBT(level.registryAccess()).getFloat("Stamina")+" class="+stand.getClass().getName()+" registry="+stand.getStandType());
            }catch(RuntimeException|Error failure){StandPower partial=PowerClass.STAND.get(user);if(partial!=null&&partial.isSummoned())type.forceUnsummon(user,partial);user.discard();throw failure;}
        }
        @Override public void close(){AbilityInput.keyRelease(ATTACK,user);AbilityInput.keyRelease(NEXT,user);if(user.hasData(ModDataAttachmentTypes.CONTROLLER))user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();if(power.isSummoned())type.forceUnsummon(user,power);user.discard();}
    }
    private static final class PhaseSuite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();final List<Spec> specs=List.of(new Spec(Role.HEAVY,6,0.1F),new Spec(Role.CHOP,6,0.51F),new Spec(Role.HEAVY,MadeInHeavenAddon.SPEED,0.25F),new Spec(Role.CHOP,MadeInHeavenAddon.SPEED,0.75F),new Spec(Role.HEAVY,28,0.49F),new Spec(Role.CHOP,28,1F),new Spec(Role.GRAB,MadeInHeavenAddon.SPEED,0.25F),new Spec(Role.CHARGED,MadeInHeavenAddon.SPEED,0.25F));
        PhaseScene scene;RuntimeException observerFailure;int index,checks;boolean closed;
        PhaseSuite(GameTestHelper helper){helper.assertTrue(active==null,"Overlapping registration suite");this.helper=helper;active=this;helper.testInfo.addListener(this);}
        void listen(){Consumer<EntityTickEvent.Pre> pre=e->{if(scene!=null&&!closed)try{scene.pre(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<EntityTickEvent.Post> post=e->{if(scene!=null&&!closed)try{scene.post(e.getEntity());}catch(RuntimeException failure){observerFailure=failure;}};Consumer<LivingIncomingDamageEvent> damage=e->{if(scene!=null&&!closed)scene.incoming(e);};listeners.addAll(List.of(pre,post,damage));NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);}
        void tick(){if(closed)return;try{if(observerFailure!=null)throw observerFailure;if(scene!=null&&scene.done){scene.close();scene=null;}if(scene==null){if(index==specs.size()){log("SUMMARY fix=12 scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"MIH local PERFORM duration failed; see SUMMARY");helper.succeed();return;}scene=new PhaseScene(this,specs.get(index++));scene.prepare();}scene.tick();helper.assertTrue(++scene.ticks<160,"Natural phase scene timeout");}catch(RuntimeException|Error failure){close();throw failure;}}
        void check(boolean passed,String detail){checks++;log((passed?"PASS ":"FAIL ")+scene.id()+" "+detail);if(!passed)failures.add(scene.id()+": "+detail);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();active=null;log("CLEANUP actors=0 listeners=0");}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class PhaseScene implements AutoCloseable {
        final PhaseSuite suite;final Spec spec;final ServerLevel level;Actor actor;Cow target;Vec3 targetOrigin;EntityActionInstance action;
        float windup,recovery,perform,staminaBefore,lossAtHit;long performStart=-1,performEntry=-1;int ticks,incoming,debits;boolean released,hitSeen,done,closed;
        PhaseScene(PhaseSuite suite,Spec spec){this.suite=suite;this.spec=spec;level=suite.helper.getLevel();}
        String id(){return spec.role+"/S"+spec.speed+"/F"+spec.meter;}
        boolean ordinary(){return spec.role==Role.HEAVY||spec.role==Role.CHOP;}
        void prepare(){ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);actor=new Actor(suite.helper,origin,spec.speed);targetOrigin=origin.add(0,0,1.5);target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setNoGravity(true);target.noPhysics=true;target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);premise(level.addFreshEntity(target),"actual native target added");}
        void tick(){if(action==null&&ticks>=3){
            if(spec.role==Role.CHOP)unlock("chop");if(spec.role==Role.GRAB){unlock("grab_throw");actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);}
            actor.stand.setFinisherMeter(spec.meter);LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(target));premise(actor.stand.canAttackEntity(target)&&!StandEntity.isStandAttackInvulnerable(target),"actual positive target/RAW admission before registered input");
            actor.power.setStamina(actor.power.getMaxStamina());actor.stand.updateStrengthMultipliers();premise(actor.stand.getAttackSpeed()==spec.speed&&actor.stand.getAttackDamage()==15&&actor.power.getStamina()==actor.power.getMaxStamina()&&!actor.power.isStaminaInfinite(),"actual effective initial speed/strength and finite max bar immediately before input");
            action=press(spec.role==Role.CHARGED?"heavy_charged":"heavy_punch",spec.role==Role.CHARGED?InputMethod.HOLD:InputMethod.CLICK,ATTACK,true);
            premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop)==(spec.role==Role.CHOP)&&(action instanceof StandEntityChargedHeavy)==(spec.role==Role.CHARGED)&&(action.ability.getAbilityUsageCategory()==AbilityUsageGroup.GRAB)==(spec.role==Role.GRAB),"actual registered action factories/roles");
            windup=action.phasesLength.getFloat(ActionPhase.WINDUP);recovery=action.phasesLength.getFloat(ActionPhase.RECOVERY);perform=action.phasesLength.getFloat(ActionPhase.PERFORM);
            if(spec.role!=Role.CHARGED)premise(((StandEntityHeavyPunch)action).finisherValue==spec.meter,"actual pre-consumption finisher snapshot");
            suite.check(perform==(ordinary()||spec.role==Role.GRAB?1:heavyWindup(spec.speed,spec.meter)*0.5F),"initialized local PERFORM1, the core grab heavy's own one tick (K5-30) or unchanged charged value actual="+perform);
            if(spec.role!=Role.CHARGED)suite.check(windup==heavyWindup(spec.speed,spec.meter)&&recovery==heavyRecovery(spec.speed,spec.meter),"unchanged donor-shaped initial cached WINDUP/RECOVERY");
            else suite.check(recovery==12&&action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE)==heavyWindup(spec.speed,spec.meter)*1.5F,"unchanged charged cache/control phases");
            log("PRESS "+id()+" role="+((Ability)action.ability).name()+" W="+windup+" P="+perform+" R="+recovery+" effectiveMeterAfter="+actor.stand.getFinisherMeter()+" action="+action.getClass().getName());
        }else if(action!=null&&spec.role==Role.CHARGED&&!released){AbilityInput.keyRelease(ATTACK,actor.user);released=true;log("RELEASE "+id()+" actualPublicKeyRelease phase="+action.getPhase());}}
        void pre(Entity entity){if(actor==null||entity!=actor.stand||action==null||done||closed)return;actor.stand.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);actor.user.moveTo(actor.origin.x,actor.origin.y,actor.origin.z,0,0);target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,0,0);target.setDeltaMovement(Vec3.ZERO);actor.stand.setDeltaMovement(Vec3.ZERO);actor.power.setStamina(actor.power.getMaxStamina());actor.stand.updateStrengthMultipliers();staminaBefore=actor.power.getStamina();premise(staminaBefore==actor.power.getMaxStamina()&&!actor.power.isStaminaInfinite()&&actor.stand.getAttackSpeed()==spec.speed,"actual finite max-stamina/effective-speed premise before natural action tick");}
        void incoming(LivingIncomingDamageEvent event){if(action==null||done||event.getEntity()!=target||event.getSource().getDirectEntity()!=actor.stand||action.punchedTarget!=null)return;incoming++;performStart=level.getGameTime();lossAtHit=target.getHealth();log("INCOMING "+id()+" worldTick="+performStart+" actualAmount="+event.getAmount()+" phase="+action.getPhase()+" phaseTick="+action.getPhaseTick());}
        void post(Entity entity){if(actor==null||entity!=actor.stand||action==null||done||closed)return;float debit=staminaBefore-actor.power.getStamina();if(debit>0){debits++;suite.check(debit==(spec.role==Role.CHARGED?100:50),"one natural attack tick actual debit="+debit);}
            if(action.getPhase()==ActionPhase.PERFORM&&performEntry<0){performEntry=level.getGameTime();log("PERFORM-ENTRY "+id()+" worldTick="+performEntry+" phaseTick="+action.getPhaseTick()+" cachedP="+perform+" effectiveSpeed="+actor.stand.getAttackSpeed());}
            if(!hitSeen&&action.punchedTarget!=null){premise(action.punchedTarget.getMainEntity()==target&&incoming==1&&target.getHealth()<lossAtHit,"one actual primary/native hurt, not a protected hook");lossAtHit-=target.getHealth();hitSeen=true;}
            if(action.getPhase()!=ActionPhase.RECOVERY||!hitSeen)return;
            suite.check(action.phasesLength.getFloat(ActionPhase.PERFORM)==perform&&action.phasesLength.getFloat(ActionPhase.RECOVERY)==recovery&&(spec.role==Role.CHARGED||action.phasesLength.getFloat(ActionPhase.WINDUP)==windup),"initial cached W/R and initialized P retained at natural REC entry; no later-getter parity claim");
            if(ordinary()||spec.role==Role.GRAB){premise(performEntry>=0&&performStart==performEntry+1,"actual natural PER entry precedes first native tick0 hit by one world tick");suite.check(level.getGameTime()-performEntry==1,"natural PER-entry-to-REC duration expected=1 actual="+(level.getGameTime()-performEntry));}else suite.check(perform==heavyWindup(spec.speed,spec.meter)*0.5F,"charged perform duration remains its speed-derived control");
            suite.check(incoming==1&&debits==1&&lossAtHit>0,"one native hit/debit retained through complete PERFORM");
            if(spec.role!=Role.CHARGED)suite.check(((StandEntityHeavyPunch)action).finisherValue==spec.meter&&action.lastDamageDealtToLiving==lossAtHit,"pre-consumption finisher snapshot/lastDamage retained");else suite.check(actor.stand.getFinisherMeter()==0,"charged finisher consumption remains unchanged");
            EntityActionInstance previous=action;EntityActionInstance next=press("punch",InputMethod.CLICK,NEXT,false);boolean success=next!=null&&next!=previous;
            suite.check(success==(spec.role==Role.CHOP),"first actual REC clickOnly routing preserves accepted Chop and ordinary/grab/charged refusals actual="+success);
            log("RECOVERY "+id()+" worldTick="+level.getGameTime()+" performEntry="+performEntry+" firstHurt="+performStart+" phaseElapsed="+(level.getGameTime()-performEntry)+" inclusiveHurtTicks="+(level.getGameTime()-performStart+1)+" incoming="+incoming+" debits="+debits+" firstLoss="+lossAtHit+" nextAccepted="+success+" cachedW="+action.phasesLength.getFloat(ActionPhase.WINDUP)+" cachedP="+perform+" cachedR="+recovery);done=true;
        }
        EntityActionInstance press(String name,InputMethod method,short key,boolean required){Ability base=actor.power.getAbility(name);premise(base!=null,"registered "+name);var checked=actor.power.updateAvailableMoves().getContextVariationContainer(base);boolean gate=checked!=null&&AbilityInput.withConditionCheck(checked,actor.user,method);if(!gate){if(required)premise(false,"actual checked input refused "+name);return null;}BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(key,checked.ability,actor.user,null,method,0,buffer,base.getAbilityId());if(required)premise(buffer.isActionSuccess,"actual native keyPress succeeded");log("INPUT "+id()+" name="+name+" method="+method+" gate="+gate+" success="+buffer.isActionSuccess+" phase="+(action==null?"none":action.getPhase()));return buffer.isActionSuccess?(entry!=null&&entry.action instanceof EntityActionInstance a?a:actor.stand.getCurStandAction()):null;}
        void unlock(String name){var result=UNLOCKS.forceUnlock(actor.power,name);premise(result==PowerSkillUnlocks.Result.UNLOCKED||result==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"public unlock "+name);}
        void premise(boolean passed,String detail){suite.helper.assertTrue(passed,id()+" premise: "+detail);}
        @Override public void close(){if(closed)return;closed=true;if(actor!=null){actor.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);actor.close();}if(target!=null)target.discard();}
    }
    private static int heavyWindup(double speed,float meter){float f=40-(float)speed*1.25F;return (int)Math.ceil(f*2/3+meter*(f/3-f*2/3));}
    private static int heavyRecovery(double speed,float meter){float max=(40-(float)speed*1.25F)*0.75F;return (int)Math.floor(max+meter*(max/2-max));}
    private static boolean near(double a,double b){return Math.abs(a-b)<0.0001;}
    private static boolean near(Vec3 a,Vec3 b){return a.distanceToSqr(b)<1E-8;}
    private static void identity(GameTestHelper helper,Class<?>... classes){for(Class<?> type:classes){String actual=sha(type),expected=System.getProperty("mih.registration.expected."+type.getName());helper.assertTrue(expected==null||expected.equalsIgnoreCase(actual),"Native class mismatch "+type.getName());log("IDENTITY "+type.getName()+"="+actual+" codeSource="+uri(type));}}
    private static String sha(Class<?> type){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}catch(Exception error){throw new IllegalStateException(error);}}
    private static String uri(Class<?> type){try{return type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString();}catch(Exception error){throw new IllegalStateException(error);}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-MELEE-REGISTRATION "+message);}
}
