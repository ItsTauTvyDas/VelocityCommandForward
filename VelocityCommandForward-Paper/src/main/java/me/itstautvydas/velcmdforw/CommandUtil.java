package me.itstautvydas.velcmdforw;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
public class CommandUtil {

    private final VelocityCommandForward plugin;
    private final MessageUtil messageUtil;

    public CommandUtil(VelocityCommandForward plugin, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.messageUtil = messageUtil;
    }

    public LiteralCommandNode<CommandSourceStack> buildCustomCommand() {
        return Commands.literal(plugin.customCommandName)
                .then(Commands.literal("reload")
                        .requires(sender -> sender.getSender().hasPermission("velocitycommandforward.admin"))
                        .executes(ctx -> {
                            plugin.saveDefaultConfig();
                            plugin.reloadConfig();
                            plugin.reloadFilter();
                            plugin.loadSecret();
                            CommandSender sender = ctx.getSource().getSender();
                            messageUtil.sendMessage(sender, "messages.reload");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .requires(sender -> sender.getSender().hasPermission("velocitycommandforward.send"))
                        .executes(ctx -> {
                            String command = StringArgumentType.getString(ctx, "command");
                            handleCommand(ctx.getSource(), new String[]{command});
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void handleCommand(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String command = String.join(" ", args);
        if (plugin.isProtectionEnabled() && plugin.getProtection() == null) {
            messageUtil.sendMessage(sender, "messages.secret-missing");
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeLong(System.currentTimeMillis());
        if (sender instanceof Player player) {
            handlePlayerCommand(player, command, out);
        } else if (sender instanceof ConsoleCommandSender) {
            handleConsoleCommand(sender, command, out);
        }
    }

    private void handlePlayerCommand(Player player, String command, ByteArrayDataOutput out) {
        Map<String, String> placeholders = setPlaceholders(player.getName(), command);
        byte filter = 0;
        if (messageUtil.shouldFilterCommand(command)) filter |= 0x01;
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(command);
        out.writeByte(filter);
        out.writeUTF(messageUtil.velocityLog("messages.velocity-log", placeholders));
        if (!send(player, player, out)) return;
        if (filter == 0x01) return;
        plugin.log(player.getName(), command);
        messageUtil.sendMessage(player, "messages.command-sent-as-player", placeholders);
    }

    private void handleConsoleCommand(CommandSender sender, String command, ByteArrayDataOutput out) {
        Map<String, String> placeholders = setPlaceholders(sender.getName(), command);
        byte filter = 0;
        if (messageUtil.shouldFilterCommand(command)) filter |= 0x01;
        out.writeUTF(""); // Console
        out.writeUTF(command);
        out.writeByte(filter);
        out.writeUTF(messageUtil.velocityLog("messages.velocity-log", placeholders));
        Player onlinePlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (onlinePlayer == null) {
            messageUtil.sendMessage(sender, "messages.no-online-player");
            return;
        }
        if (!send(sender, onlinePlayer, out)) return;
        if (filter == 0x01) return;
        plugin.log("CONSOLE", command);
        messageUtil.sendMessage(sender, "messages.command-sent-as-console", placeholders);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean send(CommandSender sender, Player carrier, ByteArrayDataOutput out) {
        byte[] data = out.toByteArray();
        if (plugin.isProtectionEnabled()) {
            MessageCrypto protection = plugin.getProtection();
            if (protection == null) {
                messageUtil.sendMessage(sender, "messages.secret-missing");
                return false;
            }
            try {
                data = protection.encrypt(data);
            } catch (GeneralSecurityException e) {
                plugin.getLogger().severe("Could not encrypt command: " + e);
                return false;
            }
        }
        carrier.sendPluginMessage(plugin, VelocityCommandForward.CHANNEL, data);
        return true;
    }

    private Map<String, String> setPlaceholders(String sender, String command) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("command", command);
        placeholders.put("sender", sender);
        return placeholders;
    }
}
