package me.itstautvydas.velcmdforw;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VelocityCommandForward extends JavaPlugin implements PluginMessageListener {

    public static final String CHANNEL = "velocity_command_forward:main";

    private static final byte RESPONSE_BAD_SECRET = 0;
    private static final byte PROTECTION_MODE_ON = 1;
    private static final byte PROTECTION_MODE_OFF = 2;

    private MessageUtil messageUtil;
    private Log4jFilterUtil log4jFilterUtil;

    private volatile MessageCrypto protection;
    private volatile Boolean proxyProtectionEnabled;

    public String customCommandName;
    public List<String> filteredCommands;

    @Override
    @ApiStatus.Internal
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.customCommandName = getConfig().getString("custom-command", "proxyexec");
        this.filteredCommands = getConfig().getStringList("filtered-commands");
        this.messageUtil = new MessageUtil(this);
        this.log4jFilterUtil = new Log4jFilterUtil(this, customCommandName);
        CommandUtil commandUtil = new CommandUtil(this, messageUtil);

        loadSecret();
        registerLogFilter();
        registerCommand(commandUtil.buildCustomCommand());
        this.getLogger().info("Custom Command: " + customCommandName);
    }

    public void loadSecret() {
        Path file = getDataFolder().toPath().resolve(MessageCrypto.SECRET_FILE);
        protection = null;
        if (Files.notExists(file))
            return;
        try {
            String secret = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (secret.isEmpty()) return;
            protection = new MessageCrypto(secret);
        } catch (IOException | GeneralSecurityException e) {
            getLogger().severe("Could not load " + file + ": " + e);
        }
    }

    public MessageCrypto getProtection() {
        return protection;
    }

    public boolean isProtectionEnabled() {
        Boolean mode = proxyProtectionEnabled;
        return mode != null ? mode : protection != null;
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] message) {
        if (!CHANNEL.equals(channel) || message.length != 1) return;
        switch (message[0]) {
            case RESPONSE_BAD_SECRET -> getLogger().severe(
                    "The proxy rejected a forwarded command, " + MessageCrypto.SECRET_FILE + " seems to be missing or does not match the proxy's content!"
            );
            case PROTECTION_MODE_ON -> {
                boolean changed = !Boolean.TRUE.equals(proxyProtectionEnabled);
                proxyProtectionEnabled = true;
                if (changed && protection == null)
                    getLogger().severe("The proxy has crypto protection enabled, but " + MessageCrypto.SECRET_FILE
                            + " is missing here. Copy it from the proxy's plugins/velocity-command-forward/ folder"
                            + " into " + getDataFolder() + " and run /" + customCommandName + " reload.");
            }
            case PROTECTION_MODE_OFF -> proxyProtectionEnabled = false;
        }
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
