package rotp.core.command;

import rotp.core.command.commands.JojoPowerCommand;
import rotp.core.command.commands.JojoCommandsCommand;
import rotp.core.command.commands.HamonStatCommand;
import rotp.core.command.commands.JojoEnergyCommand;
import rotp.core.command.commands.JojoControlsCommand;
import rotp.core.command.commands.JojoConfigCommand;
import rotp.core.command.commands.PillarmanModeCommand;
import rotp.core.command.commands.RockPaperScissorsCommand;
import rotp.core.command.commands.StandCommand;
import rotp.core.command.commands.StandDiscGiveCommand;
import rotp.core.command.commands.StandExpCommand;
import rotp.core.command.commands.StandLevelCommand;
import rotp.core.command.commands.StandSkillsCommand;
import rotp.core.core.JojoMod;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class ModChatCommands {

	@SubscribeEvent
	public static void registerCommands(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		CommandBuildContext context = event.getBuildContext();
		
		StandCommand.register(dispatcher, context);		// "stand"
		StandExpCommand.register(dispatcher, context);	// "stand_exp"
		StandLevelCommand.register(dispatcher);	// "standlevel" / "stand_level"
		StandDiscGiveCommand.register(dispatcher, context);	// "stand_disc" / "standdisc"
		JojoPowerCommand.register(dispatcher, context);	// "power"
		HamonStatCommand.register(dispatcher);		// "hamonstat" / "power_hamon stat"
		JojoEnergyCommand.register(dispatcher);		// "jojoenergy" / typed power energy
		PillarmanModeCommand.register(dispatcher);		// "pillarman"
		RockPaperScissorsCommand.register(dispatcher);	// "rockpaperscissors" / "rps"
		JojoControlsCommand.register(dispatcher);	// "jojocontrols"
		JojoConfigCommand.register(dispatcher, context);	// "jojoconfig"
		StandSkillsCommand.register(dispatcher);	// "stand_skills unlock/unlock_all/reset"
		JojoCommandsCommand.register(dispatcher);	// "jojocommands" / "commands_list"
	}

}
