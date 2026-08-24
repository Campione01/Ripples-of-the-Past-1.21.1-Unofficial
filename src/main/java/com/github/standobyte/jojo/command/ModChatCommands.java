package com.github.standobyte.jojo.command;

import com.github.standobyte.jojo.command.commands.JojoPowerCommand;
import com.github.standobyte.jojo.command.commands.JojoCommandsCommand;
import com.github.standobyte.jojo.command.commands.HamonStatCommand;
import com.github.standobyte.jojo.command.commands.JojoEnergyCommand;
import com.github.standobyte.jojo.command.commands.JojoControlsCommand;
import com.github.standobyte.jojo.command.commands.JojoConfigCommand;
import com.github.standobyte.jojo.command.commands.PillarmanModeCommand;
import com.github.standobyte.jojo.command.commands.RockPaperScissorsCommand;
import com.github.standobyte.jojo.command.commands.StandCommand;
import com.github.standobyte.jojo.command.commands.StandDiscGiveCommand;
import com.github.standobyte.jojo.command.commands.StandExpCommand;
import com.github.standobyte.jojo.command.commands.StandLevelCommand;
import com.github.standobyte.jojo.command.commands.StandSkillsCommand;
import com.github.standobyte.jojo.core.JojoMod;
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
