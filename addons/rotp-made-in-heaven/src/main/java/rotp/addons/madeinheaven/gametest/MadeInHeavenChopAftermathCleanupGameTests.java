package rotp.addons.madeinheaven.gametest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.addons.madeinheaven.entity.MadeInHeavenStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/** Only fixture-private setup/lifetime methods are reflected; production uses its public native factory. */
@GameTestHolder("rotp_mih_melee")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopAftermathCleanupGameTests {
    private static final String HOST=MadeInHeavenChopAftermathGameTests.class.getName();
    private static final class SetupFailure extends RuntimeException {}
    private MadeInHeavenChopAftermathCleanupGameTests() {}

    @GameTest(template="empty",templateNamespace="rotp_mih_melee",timeoutTicks=100,batch="mihChopAftermath10T1")
    public static void failedAftermathSetupCleansProvisionalUserAndPartialStand(GameTestHelper helper)throws Exception{
        ServerLevel level=helper.getLevel();Class<?> suiteClass=Class.forName(HOST+"$Suite"),sceneClass=Class.forName(HOST+"$Scene"),kindClass=Class.forName(HOST+"$Kind");
        for(Class<?> type:new Class<?>[]{MadeInHeavenChopAftermathCleanupGameTests.class,MadeInHeavenChopAftermathGameTests.class,suiteClass,sceneClass,MadeInHeavenAddon.class,MadeInHeavenChopAbility.class,MadeInHeavenChopAbility.MadeInHeavenChop.class,MadeInHeavenStandEntity.class,StandPowerTransitions.class,EntityStandType.class,StandPower.class,StandEntity.class}){
            String actual=sha(type),expected=System.getProperty("mih.cleanup.expected."+type.getName());helper.assertTrue(expected==null||expected.equalsIgnoreCase(actual),"Native class mismatch "+type.getName());log("IDENTITY "+type.getName()+"="+actual+" codeSource="+uri(type));
        }
        Constructor<?> suiteCtor=suiteClass.getDeclaredConstructor(GameTestHelper.class),sceneCtor=sceneClass.getDeclaredConstructor(suiteClass,kindClass);
        Method listen=suiteClass.getDeclaredMethod("listen"),suiteClose=suiteClass.getDeclaredMethod("close"),sceneClose=sceneClass.getDeclaredMethod("close");
        Method actor=sceneClass.getDeclaredMethod("actor",StandType.class,Vec3.class,float.class,double.class,boolean.class);
        for(var binding:new java.lang.reflect.AccessibleObject[]{suiteCtor,sceneCtor,listen,suiteClose,sceneClose,actor})binding.setAccessible(true);
        log("REFLECTION fixtureOnly="+HOST+" actor=(StandType,Vec3,float,double,boolean) constructors=Suite(GameTestHelper),Scene(Suite,Kind) lifetime=Suite.listen/close,Scene.close; no production internals");
        Object suite=null,scene=null;Player[] user={null};StandPower[] power={null};StandEntity[] partial={null};boolean[] factoryCaught={false},linkedAtCallback={false};int[] joins={0};
        int playersBefore=level.getServer().getPlayerList().getPlayers().size();List<String> failures=new ArrayList<>();int[] checks={0};StandType type=MadeInHeavenAddon.MADE_IN_HEAVEN.get();
        Consumer<EntityJoinLevelEvent> setup=event->{
            if(event.getLevel()!=level)return;joins[0]++;
            if(user[0]!=null||!(event.getEntity() instanceof Player candidate)||!candidate.getGameProfile().getName().equals("mih-aftermath"))return;
            user[0]=candidate;power[0]=PowerClass.STAND.attachGet(candidate);
            helper.assertTrue(StandPowerTransitions.insert(power[0],new StandInstance(type)).status()==StandPowerTransitions.Status.APPLIED,"Setup: actual public initial MIH grant");power[0].setResolveLevel(6);
            try{((EntityStandType)type).summon(candidate,power[0],stand->{partial[0]=stand;linkedAtCallback[0]=power[0].getSummonedStandEntity()==stand&&stand.getUser()==candidate&&stand.getUserPower()==power[0];throw new SetupFailure();},true);}
            catch(SetupFailure expected){factoryCaught[0]=true;log("FACTORY-CAUGHT actualException="+expected.getClass().getName()+" userUUID="+candidate.getUUID()+" partialUUID="+partial[0].getUUID()+" linked="+linkedAtCallback[0]+" partialWorld="+(level.getEntity(partial[0].getId())==partial[0])+" userWorld="+(level.getEntity(candidate.getId())==candidate));}
        };
        boolean registered=false;
        try{
            suite=suiteCtor.newInstance(helper);Object kind=java.util.Arrays.stream(kindClass.getEnumConstants()).filter(value->((Enum<?>)value).name().equals("CHOP_IDLE")).findFirst().orElseThrow();scene=sceneCtor.newInstance(suite,kind);listen.invoke(suite);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,EntityJoinLevelEvent.class,setup);registered=true;
            ChunkPos chunk=new ChunkPos(helper.absolutePos(BlockPos.ZERO));Vec3 origin=new Vec3(chunk.getMiddleBlockX()+0.5,helper.absolutePos(BlockPos.ZERO).getY()+8,chunk.getMiddleBlockZ()+0.5);
            Throwable actorFailure=null;try{actor.invoke(scene,type,origin,0F,28D,true);}catch(InvocationTargetException expected){actorFailure=expected.getCause();}
            helper.assertTrue(factoryCaught[0]&&linkedAtCallback[0]&&partial[0]!=null&&partial[0].getClass()==MadeInHeavenStandEntity.class,"Setup: actual registered factory callback failed after binding the real final MIH partial Stand");
            helper.assertTrue(actorFailure!=null&&actorFailure.getMessage().contains("public registered Stand grant"),"Setup: original actor's real grant premise rejected an already-present Stand");
            helper.assertTrue(StandPowerTransitions.insert(power[0],new StandInstance(type)).status()==StandPowerTransitions.Status.STAND_ALREADY_PRESENT,"Setup: actual stored grant failure is STAND_ALREADY_PRESENT");
            helper.assertTrue(level.getEntity(user[0].getId())==user[0]&&!user[0].isRemoved()&&power[0].getSummonedStandEntity()==partial[0]&&!partial[0].isRemoved(),"Setup: world-added provisional user and linked partial Stand exist before owned cleanup");
            helper.assertTrue(!partial[0].isManuallyControlled(),"Setup: failure precedes actual manual-control input; no fabricated active controller");
            log("BEFORE userUUID="+user[0].getUUID()+" partialUUID="+partial[0].getUUID()+" userWorld=true userRemoved=false powerLinked=true partialWorld="+(level.getEntity(partial[0].getId())==partial[0])+" partialRemoved=false manual=false actorException="+actorFailure.getClass().getName()+":"+actorFailure.getMessage()+" serverPlayerList="+playersBefore);
            sceneClose.invoke(scene);suiteClose.invoke(suite);NeoForge.EVENT_BUS.unregister(setup);registered=false;
            boolean userWorld=level.getEntity(user[0].getId())==user[0],linked=power[0].getSummonedStandEntity()==partial[0];
            check(!userWorld&&user[0].isRemoved(),"actual provisional user removed from world",checks,failures);
            check(!linked&&!power[0].isSummoned(),"actual partial Stand/power linkage cleared",checks,failures);
            check(partial[0].isRemoved()&&level.getEntity(partial[0].getId())!=partial[0],"actual native partial Stand removed/not in world",checks,failures);
            check(!partial[0].isManuallyControlled(),"manual-control negative remains settled",checks,failures);
            check(level.getServer().getPlayerList().getPlayers().size()==playersBefore,"anonymous fixture actor never changes actual server PlayerList",checks,failures);
            int joinsBeforeProbe=joins[0];Cow probe=new Cow(EntityType.COW,level);probe.moveTo(origin.x,origin.y,origin.z);helper.assertTrue(level.addFreshEntity(probe),"Setup: native post-close join probe added");probe.discard();
            check(joins[0]==joinsBeforeProbe,"actual native join probe no longer reaches the owned setup listener",checks,failures);
            log("AFTER userUUID="+user[0].getUUID()+" partialUUID="+partial[0].getUUID()+" userWorld="+userWorld+" userRemoved="+user[0].isRemoved()+" powerLinked="+linked+" partialWorld="+(level.getEntity(partial[0].getId())==partial[0])+" partialRemoved="+partial[0].isRemoved()+" serverPlayerList="+level.getServer().getPlayerList().getPlayers().size()+" joinCalls="+joins[0]+" joinsBeforeProbe="+joinsBeforeProbe);
            // Measure the OLD orphan first, then dispose it so a failing counterfactual cannot contaminate later tests.
            if(power[0].isSummoned())type.forceUnsummon(user[0],power[0]);user[0].discard();
            helper.assertTrue(level.getEntity(user[0].getId())!=user[0]&&power[0].getSummonedStandEntity()==null&&partial[0].isRemoved(),"Post-measurement explicit orphan disposal succeeded");
            log("CLEANUP measuredOrphan="+userWorld+" userWorld=false powerLinked=false partialRemoved=true listenerRegistered=false");
            log("SUMMARY fix=10-T1 scenes=1 checks="+checks[0]+" failures="+failures.size()+" details="+failures);
            helper.assertTrue(failures.isEmpty(),"Aftermath setup cleanup failed; see SUMMARY");helper.succeed();
        }finally{
            if(registered)NeoForge.EVENT_BUS.unregister(setup);
            if(scene!=null)sceneClose.invoke(scene);if(suite!=null)suiteClose.invoke(suite);
            if(power[0]!=null&&power[0].isSummoned())type.forceUnsummon(user[0],power[0]);if(user[0]!=null)user[0].discard();
        }
    }
    private static void check(boolean passed,String detail,int[] checks,List<String> failures){checks[0]++;log((passed?"PASS ":"FAIL ")+detail);if(!passed)failures.add(detail);}
    private static String sha(Class<?> type){try(var stream=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));}catch(Exception error){throw new IllegalStateException(error);}}
    private static String uri(Class<?> type){try{return type.getProtectionDomain().getCodeSource().getLocation().toURI().toASCIIString();}catch(Exception error){throw new IllegalStateException(error);}}
    private static void log(String message){JojoMod.getLogger().info("D-MIH-CHOP-AFTERMATH-CLEANUP "+message);}
}
