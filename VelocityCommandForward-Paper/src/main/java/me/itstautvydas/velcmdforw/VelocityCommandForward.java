package me.itstautvydas.velcmdforw;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VelocityCommandForward extends JavaPlugin {
    
    public static final String CHANNEL = "velcmdforw:main";

    public String customCommandName;
    public List<String> filteredCommands;
    private MessageUtil messageUtil;
    private Log4jFilterUtil log4jFilterUtil;

    @Override
    @ApiStatus.Internal
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.customCommandName = getConfig().getString("custom-command", "proxyexec");
        this.filteredCommands = getConfig().getStringList("filtered-commands");
        this.messageUtil = new MessageUtil(this);
        this.log4jFilterUtil = new Log4jFilterUtil(this, customCommandName);
        CommandUtil commandUtil = new CommandUtil(this, messageUtil);

        registerLogFilter();
        registerCommand(commandUtil.buildCustomCommand());
        this.getLogger().info("Custom Command: " + customCommandName);
    }

    public void registerCommand(LiteralCommandNode<CommandSourceStack> commandNode) {
        this.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                commands -> commands.registrar().register(commandNode)
        );
    }

    private void registerLogFilter() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        removeLogFilters(config);

        log4jFilterUtil = new Log4jFilterUtil(this, customCommandName);
        config.getRootLogger().addFilter(log4jFilterUtil);
        context.updateLoggers(config);
    }

    private void removeLogFilters(Configuration config) {
        Filter existing = config.getRootLogger().getFilter();
        if (existing instanceof Log4jFilterUtil) {
            config.getRootLogger().removeFilter(existing);
        }
    }

    public void reloadFilter() {
        List<String> filteredCommands = getConfig().getStringList("filtered-commands");
        log4jFilterUtil.updateFilteredCommands(filteredCommands);
        messageUtil.updateFilteredCommands(filteredCommands);
    }

    public void log(String sender, String command) {
        if (messageUtil.shouldFilterCommand(command)) return;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("sender", sender);
        placeholders.put("command", command);
        messageUtil.consoleLog("messages.console-log", placeholders);
    }

    @Override
    public void onDisable() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        removeLogFilters(config);
        context.updateLoggers();
    }
}
