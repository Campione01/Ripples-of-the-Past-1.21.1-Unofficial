package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
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
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAngeloRockPunchEffect;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
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

/** Server observers delegate native damage, pulses and random values; packet capture is not transport. */
@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopShieldGameTests {
    private static final short ATTACK=166,PRIME=167;
    private static final PowerSkillUnlocks.Owner UNLOCKS=PowerSkillUnlocks.register(MadeInHeavenAddon.id("chop_shield_tests"));
    private static Suite active;
    private enum Kind { BACK,SIDE,NATIVE_FACTORY,CHANCE_FAIL,ZERO_CHANCE,FRONT_BLOCKED,CANCELED,IMMUNE,ZERO_M,NO_SHIELD,RAW0_EXTENSION,RELOCATE_EXTENSION,ORDINARY }
    private record Actor(Player user,StandPower power,StandType type,StandEntity stand,Vec3 origin) {}
    private record Oracle(double distance,float chance) {}
    private record Pulse(double strength,boolean nativeHurt,int order,boolean using,boolean cooldown,Vec3 before,Vec3 after) {}
    private record Roll(float value,String caller,int order) {}
    private record Event(int order,byte id,boolean using,boolean cooldown) {}
    private static final class Raw {
        final float damage;final int before;boolean result;int after;
        Raw(float damage,int order){this.damage=damage;before=order;}
    }
    private static final class Hurt {
        final DamageSource source;final float amount;final int before;boolean result;int after;
        Hurt(DamageSource source,float amount,int order){this.source=source;this.amount=amount;before=order;}
    }
    private MadeInHeavenChopShieldGameTests() {}

    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=3600,batch="mihChopShield11")
    public static void chopShieldDisableUsesPreHurtChanceBeforeTail(GameTestHelper helper){
        Suite suite=new Suite(helper);
        try{
            for(Class<?> type:new Class<?>[]{MadeInHeavenAddon.class,MadeInHeavenChopAbility.class,MadeInHeavenChopAbility.MadeInHeavenChop.class,MadeInHeavenStandEntity.class,StandEntityHeavyPunchAbility.class,StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class,StandEntity.class,AbilityInput.class,MadeInHeavenChopShieldGameTests.class,ServerPlayer.class,ServerLevel.class}){
                String actual=sha(type),expected=System.getProperty("mih.shield.expected."+type.getName());
                helper.assertTrue(expected==null||expected.equalsIgnoreCase(actual),"Native class mismatch "+type.getName());
                log("IDENTITY "+type.getName()+"="+actual+" codeSource="+uri(type));
            }
            suite.listen();helper.onEachTick(suite::tick);
        }catch(RuntimeException|Error failure){suite.close();throw failure;}
    }
    @AfterBatch(batch="mihChopShield11")
    public static void releaseBatch(ServerLevel level){if(active!=null)active.close();log("BATCH-CLEANUP active="+(active!=null));}

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;final List<Object> listeners=new ArrayList<>();final List<String> failures=new ArrayList<>();
        final ModConfigSpec.DoubleValue multiplier=(ModConfigSpec.DoubleValue)JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings","standPowerMultiplier"));
        final double originalMultiplier;Scene scene;RuntimeException observerFailure;int index,checks;boolean closed;
        Suite(GameTestHelper helper){helper.assertTrue(active==null,"Overlapping shield suites");this.helper=helper;originalMultiplier=multiplier.get();active=this;helper.testInfo.addListener(this);}
        void listen(){
            Consumer<EntityTickEvent.Pre> pre=event->{if(scene!=null&&!closed)try{scene.pre(event.getEntity());}catch(RuntimeException error){observerFailure=error;}};
            Consumer<EntityTickEvent.Post> post=event->{if(scene!=null&&!closed)try{scene.post(event.getEntity());}catch(RuntimeException error){observerFailure=error;}};
            Consumer<LivingIncomingDamageEvent> damage=event->{if(scene!=null&&!closed)scene.incoming(event);};
            listeners.addAll(List.of(pre,post,damage));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Pre.class,pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,EntityTickEvent.Post.class,post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,LivingIncomingDamageEvent.class,damage);
        }
        void tick(){if(closed)return;try{
            if(observerFailure!=null)throw observerFailure;
            if(scene!=null&&scene.done){scene.close();scene=null;}
            if(scene==null){if(index==Kind.values().length){log("SUMMARY fix=SHIELD scenes="+index+" checks="+checks+" failures="+failures.size()+" details="+failures);close();helper.assertTrue(failures.isEmpty(),"Chop shield failed; see SUMMARY");helper.succeed();return;}scene=new Scene(this,Kind.values()[index++]);scene.prepare();}
            scene.tick();helper.assertTrue(++scene.ticks<250,"Shield scene timeout "+scene.kind);
        }catch(RuntimeException|Error failure){close();throw failure;}}
        void check(boolean passed,String message){checks++;log((passed?"PASS ":"FAIL ")+scene.kind+" "+message);if(!passed)failures.add(scene.kind+": "+message);}
        void close(){if(closed)return;closed=true;for(Object listener:listeners)NeoForge.EVENT_BUS.unregister(listener);listeners.clear();if(scene!=null)scene.close();multiplier.set(originalMultiplier);active=null;log("CLEANUP actors=0 listeners=0 M="+multiplier.get());}
        @Override public void testStructureLoaded(GameTestInfo info){}
        @Override public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
        @Override public void testAddedForRerun(GameTestInfo oldInfo,GameTestInfo newInfo,GameTestRunner runner){close();}
    }
    private static final class Scene implements AutoCloseable {
        final Suite suite;final Kind kind;final ServerLevel level;final List<Actor> actors=new ArrayList<>();final List<Player> ownedUsers=new ArrayList<>();
        final List<Pulse> pulses=new ArrayList<>();final List<Roll> rolls=new ArrayList<>();final List<Event> events=new ArrayList<>();final List<Integer> cooldownDurations=new ArrayList<>();
        final List<String> rngOperations=new ArrayList<>();
        Actor caster,primer;ObservedPlayer target;EmbeddedChannel ownedChannel;CapturingListener recipient;EntityActionInstance action,primeAction;CrazyDAngeloRockPunchEffect extension;
        Raw raw;Hurt hurt;Vec3 targetOrigin;Oracle beforeChance;float healthBefore;long seed,lastDrivenWorldTick=Long.MIN_VALUE;int ticks,order,incoming,cooldownTicks,stopOrder,playerTicks,worldPlayerTicks,rawCalls;
        boolean sampling,insideHurt,attacked,done,closed,immuneBefore,replayMatches;
        Scene(Suite suite,Kind kind){this.suite=suite;this.kind=kind;level=suite.helper.getLevel();}
        boolean expectedDisable(){return kind==Kind.BACK||kind==Kind.SIDE||kind==Kind.NATIVE_FACTORY||kind==Kind.RELOCATE_EXTENSION;}
        boolean expectedHurt(){return kind!=Kind.FRONT_BLOCKED&&kind!=Kind.CANCELED&&kind!=Kind.IMMUNE&&kind!=Kind.ZERO_M&&kind!=Kind.RAW0_EXTENSION;}
        boolean expectedRoll(){return kind!=Kind.ZERO_CHANCE&&kind!=Kind.FRONT_BLOCKED&&kind!=Kind.CANCELED&&kind!=Kind.IMMUNE&&kind!=Kind.ZERO_M&&kind!=Kind.NO_SHIELD&&kind!=Kind.RAW0_EXTENSION&&kind!=Kind.ORDINARY;}
        void prepare(){
            suite.multiplier.set(kind==Kind.ZERO_M?0D:1D);premise(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get()==(kind==Kind.ZERO_M?0D:1D),"actual M multiplier before attack");
            ChunkPos chunk=new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,suite.helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);
            caster=actor(MadeInHeavenAddon.MADE_IN_HEAVEN.get(),origin,28,kind==Kind.NATIVE_FACTORY);
            targetOrigin=kind==Kind.SIDE?origin.add(1.5,0,0):kind==Kind.ZERO_CHANCE?origin.add(0,2,1.6):origin.add(0,0,1.5);
            GameProfile profile=new GameProfile(UUID.randomUUID(),"mih-shield");target=new ObservedPlayer(this,profile);
            Connection connection=new Connection(PacketFlow.SERVERBOUND);ownedChannel=new EmbeddedChannel(connection);recipient=new CapturingListener(level.getServer(),connection,target,CommonListenerCookie.createInitial(profile,false),this);
            target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,kind==Kind.FRONT_BLOCKED?180:0,0);target.setYHeadRot(target.getYRot());level.addNewPlayer(target);target.setGameMode(GameType.SURVIVAL);target.setNoGravity(true);target.noPhysics=true;target.getAbilities().invulnerable=false;
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            if(kind==Kind.ZERO_CHANCE){target.getAttribute(Attributes.SCALE).setBaseValue(2);target.refreshDimensions();}
            if(kind!=Kind.NO_SHIELD){target.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));target.startUsingItem(InteractionHand.OFF_HAND);}
            level.broadcastEntityEvent(target,(byte)1);premise(recipient.probes==1,"native tracked self-recipient route delivered the harmless event1 probe, not a fabricated event30");
            beforeChance=donorOracle(caster.stand,target);seed=qualifiedSeed(beforeChance.chance,kind!=Kind.CHANCE_FAIL);
            premise(StandEntityPunchAbility.validatePunchTarget(caster.stand,new ActionTarget(target)).getMainEntity()==target,"actual entity-target validation within reach");
            premise(kind==Kind.ZERO_CHANCE?beforeChance.chance==0:beforeChance.chance>0&&beforeChance.chance<1,"independent donor chance premise before registered input");
            log("SETUP "+kind+" targetClass="+target.getClass().getName()+" casterClass="+caster.stand.getClass().getName()+" eye="+caster.stand.getEyePosition(1)+" box="+target.getBoundingBox()+" donorDistance="+beforeChance.distance+" chance="+beforeChance.chance+" seed="+seed+" first8="+draws(seed));
        }
        void tick(){
            // The embedded connection has no transport tick; advance only the public native player lifecycle.
            long worldTick=level.getGameTime();premise(worldTick!=lastDrivenWorldTick,"one fixture-owned public player lifecycle driver per real world tick");lastDrivenWorldTick=worldTick;
            int age=target.tickCount,useTicks=target.getTicksUsingItem();premise(age==worldPlayerTicks,"actual entity age equals observed native world tickNonPassenger visits");boolean using=target.isUsingItem();target.doTick();playerTicks++;
            premise(target.tickCount==age,"public native doTick advances player lifecycle, not a second entity-age tick");
            if(using)premise(target.getTicksUsingItem()==useTicks+1,"actual native held-item lifecycle advances one tick");
            if(attacked){if(expectedDisable()){
                cooldownTicks++;if(cooldownTicks==99)suite.check(target.getCooldowns().isOnCooldown(Items.SHIELD)&&near(target.getCooldowns().getCooldownPercent(Items.SHIELD,0),0.01),"native cooldown still active after99 public player ticks");
                if(cooldownTicks==100){suite.check(!target.getCooldowns().isOnCooldown(Items.SHIELD),"native cooldown expires at100 public player ticks");done=true;}
            }else done=true;return;}
            if(action!=null||ticks<65)return;
            premise(kind==Kind.NO_SHIELD?!target.isUsingItem():target.isBlocking()&&target.getTicksUsingItem()>=5,"actual raised native shield has passed its five-tick delay, or no-shield control");
            log("LIFECYCLE-PRE "+kind+" realWorldTick="+worldTick+" playerAge="+target.tickCount+" worldEntityVisits="+worldPlayerTicks+" driverCalls="+playerTicks+" useTicks="+target.getTicksUsingItem()+" nativeBlocking="+target.isBlocking()+" using="+target.isUsingItem()+" cooldown="+target.getCooldowns().isOnCooldown(Items.SHIELD));
            if(kind==Kind.IMMUNE){if(primer==null){StandType sp=JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));primer=actor(sp,caster.origin.add(-0.5,0,0),32,true);primer.stand.setFinisherMeter(0.25F);aim(primer);primeAction=press(primer,PRIME);}return;}
            begin();
        }
        void begin(){
            if(kind!=Kind.ORDINARY){var result=UNLOCKS.forceUnlock(caster.power,"chop");premise(result==PowerSkillUnlocks.Result.UNLOCKED||result==PowerSkillUnlocks.Result.ALREADY_UNLOCKED,"public Chop unlock");}
            caster.stand.setFinisherMeter(kind==Kind.ORDINARY?0.25F:0.75F);caster.power.setStamina(caster.power.getMaxStamina());aim(caster);
            premise(!target.isCreative()&&!target.isSpectator()&&!target.getAbilities().invulnerable&&!caster.power.isStaminaInfinite()&&caster.power.getStamina()>=50&&caster.stand.canAttackEntity(target),"finite/actor/friendly-fire admission before registered input");
            premise(!target.isBaby(),"actual native ServerPlayer uses the pinned adult voice-pitch path");
            premise(StandEntity.isStandAttackInvulnerable(target)==(kind==Kind.IMMUNE),"actual RAW immunity state before input");
            action=press(caster,ATTACK);premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop)==(kind!=Kind.ORDINARY),"registered finisher Chop versus ordinary-heavy action factory");
            if(kind==Kind.RAW0_EXTENSION){extension=ModStandAbilities.EFFECT_CD_PUNCH_ANGELO_ROCK.get().create(level);extension.withStand(caster.power);premise(action.getPunchModifiers().contains(extension),"actual public nonlethal modifier installed; port-helper extension boundary only");}
        }
        void aim(Actor actor){LivingComponentAction.getComponent(actor.stand).entityAim.setTarget(new ActionTarget(target));}
        void pre(Entity entity){if(entity==target){worldPlayerTicks++;return;}if(entity!=caster.stand||action==null||attacked||closed)return;
            sampling=false;raw=null;hurt=null;incoming=0;order=0;stopOrder=0;rawCalls=0;pulses.clear();rolls.clear();events.clear();cooldownDurations.clear();rngOperations.clear();replayMatches=true;
            caster.stand.moveTo(caster.origin.x,caster.origin.y,caster.origin.z,0,0);caster.stand.setDeltaMovement(Vec3.ZERO);caster.user.moveTo(caster.origin.x,caster.origin.y,caster.origin.z,0,0);
            target.moveTo(targetOrigin.x,targetOrigin.y,targetOrigin.z,kind==Kind.FRONT_BLOCKED?180:0,0);target.setYHeadRot(target.getYRot());target.setDeltaMovement(Vec3.ZERO);
            if(kind==Kind.RAW0_EXTENSION)target.setHealth(0.0001F);healthBefore=target.getHealth();immuneBefore=StandEntity.isStandAttackInvulnerable(target);beforeChance=donorOracle(caster.stand,target);target.getRandom().setSeed(seed);sampling=true;
            premise(immuneBefore==(kind==Kind.IMMUNE)&&caster.stand.canAttackEntity(target),"actual readonly RAW/friendly-fire premise before the natural attack tick");
            premise(kind==Kind.ZERO_CHANCE?beforeChance.chance==0:beforeChance.chance>0,"actual donor chance immediately before natural attack");
            premise(!target.getCooldowns().isOnCooldown(Items.SHIELD)&&(kind==Kind.NO_SHIELD?!target.isUsingItem():target.isBlocking()),"actual initial shield/cooldown gate before natural attack");
        }
        void incoming(LivingIncomingDamageEvent event){if(!sampling||event.getEntity()!=target||event.getSource().getDirectEntity()!=caster.stand)return;incoming++;log("INCOMING "+kind+" nativeAmount="+event.getAmount()+" order="+(++order)+" health="+target.getHealth()+" shield="+target.isBlocking());if(kind==Kind.CANCELED)event.setCanceled(true);if(kind==Kind.RELOCATE_EXTENSION){Vec3 moved=targetOrigin.add(0,0,20);target.moveTo(moved.x,moved.y,moved.z);premise(donorOracle(caster.stand,target).chance==0,"public event relocation produces actual post-hurt zero chance without replacing native hurt");}}
        void post(Entity entity){if(closed||attacked)return;
            if(primer!=null&&action==null&&entity==primer.stand&&primeAction.punchedTarget!=null){premise(StandEntity.isStandAttackInvulnerable(target),"actual registered primer naturally established RAW immunity");begin();}
            if(entity!=caster.stand||action==null)return;sampling=false;if(action.punchedTarget==null)return;
            premise(action.punchedTarget.getMainEntity()==target,"natural registered primary-target identity");
            premise(immuneBefore==(kind==Kind.IMMUNE),"RAW immunity premise persists to actual attack tick");
            if(kind!=Kind.NATIVE_FACTORY)premise(rawCalls==1&&raw!=null&&(raw.damage==(kind==Kind.RAW0_EXTENSION?0:15)),"exactly one real RAW helper with captured positive15 or qualified modifier-driven RAW0");
            if(kind==Kind.RAW0_EXTENSION)premise(target.getHealth()==0.0001F&&action.getPunchModifiers().contains(extension)&&hurt==null&&incoming==0,"RAW0 extension has installed modifier/effective boundary health/no real hurt before shield interpretation");
            else if(kind==Kind.IMMUNE)premise(hurt==null&&incoming==0,"canonical RAW-immunity veto occurs before real hurt");
            else premise(hurt!=null&&hurt.result==expectedHurt()&&(expectedHurt()?healthBefore>target.getHealth():healthBefore==target.getHealth()),"qualified native hurt/health result; no fabricated damage acceptance");
            List<Pulse> detached=pulses.stream().filter(p->!p.nativeHurt).toList();List<Roll> shieldRolls=rolls.stream().filter(r->r.caller.equals(StandEntityHeavyPunchAbility.class.getName()+".disableShieldOnHit")).toList();
            premise(replayMatches,"observed native primitives and the actual adult voice-pitch return match independent native replay; hidden pitch operands are oracle-only");
            if(!shieldRolls.isEmpty()){premise(shieldRolls.size()==1&&(shieldRolls.getFirst().value<beforeChance.chance)==expectedDisable(),"actual delegated native shield RNG qualifies pre-hurt donor success/failure");}
            suite.check(target.getCooldowns().isOnCooldown(Items.SHIELD)==expectedDisable(),"shield cooldown only on successful positive-chance Chop hurt");
            suite.check(kind==Kind.NO_SHIELD?!target.isUsingItem():target.isUsingItem()!=expectedDisable(),"successful shield disable stops use; negative controls preserve held use");
            suite.check(cooldownDurations.equals(expectedDisable()?List.of(100):List.of()),"actual native cooldown packet duration100 or strict absence");
            suite.check(events.size()==(expectedDisable()?1:0)&&events.stream().allMatch(e->e.id==30&&!e.using&&e.cooldown),"one actual event30 after stop-use/cooldown, or strict absence");
            suite.check(shieldRolls.size()==(expectedRoll()?1:0),"one native shield nextFloat only for eligible positive chance; no failed-hurt/zero-chance/no-shield roll");
            if(kind!=Kind.IMMUNE&&kind!=Kind.RAW0_EXTENSION){
                suite.check(detached.size()==(expectedHurt()?2:1),"admitted successful extra+tail or no-hurt tail count retained");
                double half=kind==Kind.ORDINARY?(0.5+15/(8-0.25*4))/2:0.25;
                suite.check(detached.stream().allMatch(p->near(p.strength,half)&&p.order>hurt.after),"unchanged exact detached half magnitude/order after native hurt");
                if(expectedDisable()){Pulse extra=detached.getFirst(),tail=detached.getLast();int packet=events.isEmpty()?-1:events.getFirst().order,roll=shieldRolls.isEmpty()?-1:shieldRolls.getFirst().order;
                    suite.check(extra.using&&!extra.cooldown&&!tail.using&&tail.cooldown&&hurt.after<extra.order&&extra.order<roll&&roll<stopOrder&&stopOrder<packet&&packet<tail.order,"native hurt -> extra half -> RNG -> stop use -> event30 -> inherited tail");
                }else suite.check(detached.stream().noneMatch(Pulse::cooldown)&&stopOrder==0,"negative/ordinary tail does not disable or stop the shield");
            }else suite.check(detached.isEmpty()&&pulses.isEmpty()&&shieldRolls.isEmpty(),"nonadmitted RAW immunity/extension zero has no pulse or shield RNG");
            float loss=healthBefore-target.getHealth();suite.check(near(action.lastDamageDealtToLiving,loss),"lastDamage equals real native health loss, zero on veto");
            if(kind==Kind.RELOCATE_EXTENSION)suite.check(beforeChance.chance>0&&donorOracle(caster.stand,target).chance==0&&target.getCooldowns().isOnCooldown(Items.SHIELD),"pre-hurt chance snapshot survives actual public event relocation; extension control");
            log("IMPACT "+kind+" raw="+(raw==null?"nativeFactory":raw.damage+"/"+raw.result+"@"+raw.before+".."+raw.after)+" hurt="+(hurt==null?"none":hurt.amount+"/"+hurt.result+"@"+hurt.before+".."+hurt.after)+" loss="+loss+" chanceBefore="+beforeChance.chance+" chanceAfter="+donorOracle(caster.stand,target).chance+" pulses="+pulses+" nativeFloats="+rolls+" rngOperations="+rngOperations+" stopOrder="+stopOrder+" cooldownPackets="+cooldownDurations+" events="+events+" using="+target.isUsingItem()+" cooldownPercent="+target.getCooldowns().getCooldownPercent(Items.SHIELD,0));attacked=true;
        }
        Actor actor(StandType type,Vec3 origin,double speed,boolean nativeFactory){
            Player user=new Player(level,BlockPos.ZERO,0,new GameProfile(UUID.randomUUID(),"mih-shield-caster")){@Override public boolean isCreative(){return false;}@Override public boolean isSpectator(){return false;}};
            ownedUsers.add(user);user.noPhysics=true;user.setNoGravity(true);user.getAbilities().invulnerable=false;user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);user.setHealth(200);user.moveTo(origin.x,origin.y,origin.z,0,0);premise(level.addFreshEntity(user),"native player actor added");
            StandPower power=PowerClass.STAND.attachGet(user);premise(StandPowerTransitions.insert(power,new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"public registered Stand grant");power.setResolveLevel(6);
            StandEntity stand;if(nativeFactory){premise(type.summon(user,power),"actual native registered summon factory");stand=power.getSummonedStandEntity();}else{stand=new ObservedStand(this,MadeInHeavenAddon.MADE_IN_HEAVEN_ENTITY.get(),level);stand.withStandType(type);power.setSummonedStand(stand);premise(level.addFreshEntity(stand),"observer retains actual registered MIH EntityType");}
            Actor actor=new Actor(user,power,type,stand,origin);actors.add(actor);stand.summonLockTicks=0;stand.gradualSummonWeaknessTicks=0;stand.setCanFollowUser(false);stand.setNoGravity(true);stand.noPhysics=true;stand.moveTo(origin.x,origin.y,origin.z,0,0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);StandEntity.updateStandStatAttributes(stand,user);power.setStamina(power.getMaxStamina());stand.updateStrengthMultipliers();
            user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock=true;
            premise(stand.getUser()==user&&stand.getUserPower()==power&&stand.getStandType().equals(type.getId())&&power.getSummonedStandEntity()==stand,"actual user/power/registry/entity bindings");
            premise(!nativeFactory||stand.getClass()==(type==MadeInHeavenAddon.MADE_IN_HEAVEN.get()?MadeInHeavenStandEntity.class:StandEntity.class),"actual final native summon class control");
            log("ACTOR factory="+nativeFactory+" class="+stand.getClass().getName()+" type="+type.getId()+" entityType="+stand.getType()+" width="+stand.getBbWidth()+" height="+stand.getBbHeight()+" eye="+stand.getEyeHeight()+" strength="+stand.getAttackDamage()+" speed="+stand.getAttackSpeed()+" durability="+stand.getDurability()+" entityReach="+stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)+" blockReach="+stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)+" stored="+power.serializeNBT(level.registryAccess()).getFloat("Stamina"));return actor;
        }
        EntityActionInstance press(Actor actor,short key){Ability base=actor.power.getAbility("heavy_punch");premise(base!=null,"registered heavy input");var checked=actor.power.updateAvailableMoves().getContextVariationContainer(base);premise(checked!=null&&AbilityInput.withConditionCheck(checked,actor.user,InputMethod.CLICK),"actual production condition-checked heavy input");BufferingState buffer=BufferingState.clickOnly();var entry=AbilityInput.keyPress(key,checked.ability,actor.user,null,InputMethod.CLICK,0,buffer,base.getAbilityId());premise(buffer.isActionSuccess,"actual native keyPress succeeded");return entry!=null&&entry.action instanceof EntityActionInstance a?a:actor.stand.getCurStandAction();}
        void premise(boolean passed,String message){suite.helper.assertTrue(passed,kind+" premise: "+message);}
        @Override public void close(){if(closed)return;closed=true;sampling=false;if(extension!=null&&action!=null)action.getPunchModifiers().remove(extension);for(Actor actor:actors){AbilityInput.keyRelease(ATTACK,actor.user);AbilityInput.keyRelease(PRIME,actor.user);if(actor.power.isSummoned())actor.type.forceUnsummon(actor.user,actor.power);actor.user.discard();}for(Player user:ownedUsers){StandPower power=PowerClass.STAND.get(user);if(power!=null&&power.isSummoned()){StandEntity stand=power.getSummonedStandEntity();power.setSummonedStand(null);if(stand!=null)stand.discard();}user.discard();}if(target!=null)target.discard();if(ownedChannel!=null)ownedChannel.finishAndReleaseAll();}
    }
    private static final class ObservedStand extends StandEntity {
        final Scene scene;
        ObservedStand(Scene scene,EntityType<? extends StandEntity> type,Level level){super(type,level);this.scene=scene;}
        @Override public boolean hurtWithStandAttack(Entity target,DamageSource source,float damage){Raw record=scene.sampling&&scene.caster.stand==this&&scene.target==target?new Raw(damage,++scene.order):null;if(record!=null){scene.raw=record;scene.rawCalls++;}boolean result=super.hurtWithStandAttack(target,source,damage);if(record!=null){record.result=result;record.after=++scene.order;}return result;}
    }
    private static final class ObservedPlayer extends ServerPlayer {
        final Scene scene;final TracedRandom trace;
        ObservedPlayer(Scene scene,GameProfile profile){super(scene.level.getServer(),scene.level,profile,ClientInformation.createDefault());this.scene=scene;trace=new TracedRandom(scene,super.getRandom());}
        @Override public RandomSource getRandom(){return trace==null?super.getRandom():trace;}
        @Override public float getVoicePitch(){float value=super.getVoicePitch();if(scene!=null&&scene.sampling){float a=trace.replay.nextFloat(),b=trace.replay.nextFloat();float expected=(a-b)*0.2F+1.0F;scene.replayMatches&=!isBaby()&&value==expected;scene.rngOperations.add("getVoicePitch.nativeReturn="+value+"/replay="+expected+"/operands_ORACLE_ONLY=["+a+","+b+"]");log("PITCH "+scene.kind+" nativeReturned="+value+" adult="+!isBaby()+" oracleOperands_ONLY=["+a+","+b+"] expectedNativeReturn="+expected);}return value;}
        @Override public boolean hurt(DamageSource source,float amount){Hurt record=scene.sampling&&source.getDirectEntity()==scene.caster.stand?new Hurt(source,amount,++scene.order):null;if(record!=null){scene.hurt=record;scene.insideHurt=true;}boolean result;try{result=super.hurt(source,amount);}finally{if(record!=null)scene.insideHurt=false;}if(record!=null){record.result=result;record.after=++scene.order;}return result;}
        @Override public void knockback(double strength,double x,double z){boolean observe=scene!=null&&scene.sampling;int order=observe?++scene.order:0;Vec3 before=getDeltaMovement();boolean using=isUsingItem(),cooldown=getCooldowns().isOnCooldown(Items.SHIELD);super.knockback(strength,x,z);if(observe)scene.pulses.add(new Pulse(strength,scene.insideHurt,order,using,cooldown,before,getDeltaMovement()));}
        @Override public void stopUsingItem(){if(scene!=null&&scene.sampling)scene.stopOrder=++scene.order;super.stopUsingItem();}
    }
    private static final class CapturingListener extends ServerGamePacketListenerImpl {
        final Scene scene;int probes;
        CapturingListener(MinecraftServer server,Connection connection,ServerPlayer player,CommonListenerCookie cookie,Scene scene){super(server,connection,player,cookie);this.scene=scene;}
        @Override public void send(Packet<?> packet){capture(packet);}
        @Override public void send(Packet<?> packet,PacketSendListener listener){capture(packet);}
        void capture(Packet<?> packet){if(scene==null)return;if(packet instanceof ClientboundEntityEventPacket event&&event.getEntity(scene.level)==scene.target){if(event.getEventId()==1)probes++;if(scene.sampling&&event.getEventId()==30){scene.events.add(new Event(++scene.order,event.getEventId(),scene.target.isUsingItem(),scene.target.getCooldowns().isOnCooldown(Items.SHIELD)));log("EVENT-PACKET "+scene.kind+" id=30 order="+scene.order+" using="+scene.target.isUsingItem()+" cooldown="+scene.target.getCooldowns().isOnCooldown(Items.SHIELD));}}if(scene.sampling&&packet instanceof ClientboundCooldownPacket cooldown&&cooldown.item()==Items.SHIELD){scene.cooldownDurations.add(cooldown.duration());log("COOLDOWN-PACKET "+scene.kind+" duration="+cooldown.duration()+" order="+(++scene.order));}}
    }
    private static final class TracedRandom implements RandomSource {
        final Scene scene;final RandomSource delegate;final RandomSource replay=RandomSource.create(0);
        TracedRandom(Scene scene,RandomSource delegate){this.scene=scene;this.delegate=delegate;}
        @Override public RandomSource fork(){RandomSource value=delegate.fork();if(scene.sampling){replay.fork();scene.rngOperations.add("fork");}return value;}
        @Override public PositionalRandomFactory forkPositional(){PositionalRandomFactory value=delegate.forkPositional();if(scene.sampling){replay.forkPositional();scene.rngOperations.add("forkPositional");}return value;}
        @Override public void setSeed(long seed){delegate.setSeed(seed);replay.setSeed(seed);}
        @Override public int nextInt(){int value=delegate.nextInt();if(scene.sampling)observe("nextInt",value,replay.nextInt());return value;}
        @Override public int nextInt(int bound){int value=delegate.nextInt(bound);if(scene.sampling)observe("nextInt("+bound+")",value,replay.nextInt(bound));return value;}
        @Override public long nextLong(){long value=delegate.nextLong();if(scene.sampling)observe("nextLong",value,replay.nextLong());return value;}
        @Override public boolean nextBoolean(){boolean value=delegate.nextBoolean();if(scene.sampling)observe("nextBoolean",value,replay.nextBoolean());return value;}
        @Override public float nextFloat(){float value=delegate.nextFloat();if(scene.sampling){observe("nextFloat",value,replay.nextFloat());StackWalker.StackFrame frame=StackWalker.getInstance().walk(frames->frames.filter(f->!f.getClassName().equals(TracedRandom.class.getName())).findFirst().orElseThrow());String caller=frame.getClassName()+"."+frame.getMethodName();scene.rolls.add(new Roll(value,caller,++scene.order));log("RNG "+scene.kind+" nativeValue="+value+" caller="+caller+" order="+scene.order);}return value;}
        @Override public double nextDouble(){double value=delegate.nextDouble();if(scene.sampling)observe("nextDouble",value,replay.nextDouble());return value;}
        @Override public double nextGaussian(){double value=delegate.nextGaussian();if(scene.sampling)observe("nextGaussian",value,replay.nextGaussian());return value;}
        void observe(String operation,Object actual,Object expected){scene.replayMatches&=actual.equals(expected);scene.rngOperations.add(operation+"="+actual+"/replay="+expected);}
    }
    private static long qualifiedSeed(float chance,boolean success){if(chance==0)return 1;for(long seed=0;seed<1000000;seed++){RandomSource random=RandomSource.create(seed);boolean qualifies=true;for(int i=0;i<8;i++){float draw=random.nextFloat();qualifies&=success?draw<chance*0.8:draw>chance+(1-chance)*0.2;}if(qualifies)return seed;}throw new IllegalStateException("No native shield seed");}
    private static List<Float> draws(long seed){RandomSource random=RandomSource.create(seed);List<Float> result=new ArrayList<>();for(int i=0;i<8;i++)result.add(random.nextFloat());return result;}
    // Independent slab intersection, not AABB.clip or the local production chance helper.
    private static Oracle donorOracle(StandEntity stand,LivingEntity target){Vec3 eye=stand.getEyePosition(1);AABB b=target.getBoundingBox();float fraction=stand.getBbHeight()==0?0:stand.getEyeHeight()/stand.getBbHeight();Vec3 end=new Vec3((b.minX+b.maxX)/2,b.minY+fraction*(b.maxY-b.minY),(b.minZ+b.maxZ)/2);double distance;
        if(eye.x>=b.minX&&eye.x<b.maxX&&eye.y>=b.minY&&eye.y<b.maxY&&eye.z>=b.minZ&&eye.z<b.maxZ)distance=0;
        else{double enter=0,exit=1;double[] start={eye.x,eye.y,eye.z},finish={end.x,end.y,end.z},min={b.minX,b.minY,b.minZ},max={b.maxX,b.maxY,b.maxZ};boolean hit=true;for(int axis=0;axis<3;axis++){double delta=finish[axis]-start[axis];if(Math.abs(delta)<1E-12){if(start[axis]<min[axis]||start[axis]>max[axis])hit=false;}else{double a=(min[axis]-start[axis])/delta,c=(max[axis]-start[axis])/delta;enter=Math.max(enter,Math.min(a,c));exit=Math.min(exit,Math.max(a,c));}}hit&=enter<=exit&&enter<=1&&exit>=0;distance=hit?eye.distanceTo(eye.add(end.subtract(eye).scale(enter)))-stand.getBbWidth()/2:-1;}
        double range=stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),ratio=range>0?1-distance/range:0;return new Oracle(distance,Math.max(0,Math.min(1,(float)ratio-0.25F)));}
    private static boolean near(double a,double b){return Math.abs(a-b)<0.0001;}
    private static String sha(Class<?> type){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}catch(Exception error){throw new IllegalStateException(error);}}
    private static String uri(Class<?> type){try{return type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString();}catch(Exception error){throw new IllegalStateException(error);}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-CHOP-SHIELD "+message);}
}
