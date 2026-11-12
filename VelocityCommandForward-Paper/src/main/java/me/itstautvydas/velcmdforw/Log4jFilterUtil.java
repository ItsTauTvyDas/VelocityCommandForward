package me.itstautvydas.velcmdforw;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Log4jFilterUtil extends AbstractFilter {

    private final String customCommandName;
    private volatile List<String> filteredCommands;

    public Log4jFilterUtil(JavaPlugin plugin, String customCommandName) {
        this.customCommandName = customCommandName;
        this.filteredCommands = new CopyOnWriteArrayList<>(plugin.getConfig().getStringList("filtered-commands"));
    }

    public void updateFilteredCommands(List<String> Commands) {
        this.filteredCommands = new CopyOnWriteArrayList<>(Commands);
    }

    @Override
    public Filter.Result filter(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (this.filteredCommands
                .stream()
                .anyMatch(command -> message.contains(" issued server command: /" + customCommandName + " " + command))) {
            return Result.DENY;
        }
        return Result.NEUTRAL;
    }
}