package me.itstautvydas.velcmdforw;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "velocity-command-forward",
        name = "VelocityCommandForward",
        version = BuildConstants.VERSION,
        url = "https://itstautvydas.me",
        authors = { "ItsTauTvyDas" }
)
public class VelocityCommandForward {

    private static final byte[] RESPONSE_BAD_SECRET = { 0 };
    private static final byte[] PROTECTION_MODE_ON = { 1 };
    private static final byte[] PROTECTION_MODE_OFF = { 2 };

    private final Logger logger;
    private final ProxyServer proxy;
    private final Path dataDirectory;

    private boolean protectionEnabled = true;
    private MessageCrypto protection;
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("velocity_command_forward:main");

    @Inject
    public VelocityCommandForward(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        setup();
        loadConfig();
        if (protectionEnabled)
            loadSecret();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        event.getPlayer()
                .getCurrentServer()
                .ifPresent(server -> server.sendPluginMessage(CHANNEL, protectionEnabled ? PROTECTION_MODE_ON : PROTECTION_MODE_OFF));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection server)) return;
        var serverName = server.getServerInfo().getName();
        byte[] data = event.getData();
        if (protectionEnabled) {
            if (protection == null) {
                logger.error("Rejected command from server {} because no secret is loaded", serverName);
                return;
            }
            try {
                data = protection.decrypt(data);
            } catch (GeneralSecurityException e) {
                logger.error("Rejected command from server {}: its {} is missing or does not match the proxy's", serverName, MessageCrypto.SECRET_FILE);
                server.sendPluginMessage(CHANNEL, RESPONSE_BAD_SECRET);
                return;
            }
        }
        String uuidRaw, command, log;
        byte flags;
        try {
            @SuppressWarnings("UnstableApiUsage")
            var in = ByteStreams.newDataInput(data);
            long now = System.currentTimeMillis();
            long sentAt = in.readLong();
            if (protectionEnabled) {
                if (Math.abs(now - sentAt) > MessageCrypto.MAX_AGE_MILLIS) {
                    logger.error("Rejected command from server {}: message is {} ms old (are the server clocks in sync?)", serverName, now - sentAt);
                    return;
                }
                if (isReplay(event.getData(), now)) {
                    logger.error("Rejected command from server {}: message was already received once", serverName);
                    return;
                }
            }
            uuidRaw = in.readUTF();
            command = in.readUTF();
            flags = in.readByte();
            log = in.readUTF();
        } catch (RuntimeException e) {
            logger.error("Could not read command from server {}", serverName);
            if (!protectionEnabled) server.sendPluginMessage(CHANNEL, PROTECTION_MODE_OFF);
            return;
        }
        boolean shouldFilterCommand = (flags & 0x01) != 0;
        if (uuidRaw.isEmpty()) { // Console
            if (!shouldFilterCommand && !log.isEmpty()) logger.info(log);
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
        } else { // Player
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException e) {
                logger.error("Server {} sent an invalid player UUID", serverName);
                return;
            }
            proxy.getPlayer(uuid).ifPresent(player -> {
                if (!shouldFilterCommand && !log.isEmpty()) logger.info(log);
                proxy.getCommandManager().executeAsync(player, command);
            });
        }
    }

    private void setup() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            logger.error("Failed to create data directory!");
        }
    }

    private void loadConfig() {
        Path file = dataDirectory.resolve("config.yml");
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(file)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    Files.copy(Objects.requireNonNull(in, "config.yml is missing from the jar"), file);
                }
            }
            protectionEnabled = YamlConfigurationLoader.builder().path(file)
                    .build()
                    .load()
                    .node("use-crypto-protection")
                    .getBoolean(true);
        } catch (IOException e) {
            logger.error("Could not load config.yml, crypto protection stays enabled", e);
        }
    }

    private void loadSecret() {
        Path file = dataDirectory.resolve(MessageCrypto.SECRET_FILE);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(file)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                Files.writeString(file, Base64.getEncoder().withoutPadding().encodeToString(random));
                logger.warn("Generated a new secret at {}", file);
                logger.warn("Copy this file into plugins/VelocityCommandForward/ on every backend server");
            }
            String secret = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (secret.isEmpty()) {
                logger.warn("{} is empty, crypto protection is disabled", file);
                protectionEnabled = false;
                protection = null;
                return;
            }
            protection = new MessageCrypto(secret);
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Could not load {}, all forwarded commands will be rejected", file, e);
        }
    }

    private boolean isReplay(byte[] message, long now) {
        seenNonces.values().removeIf(expiry -> expiry < now);
        String nonce = Base64.getEncoder().encodeToString(Arrays.copyOf(message, MessageCrypto.NONCE_LEN));
        return seenNonces.putIfAbsent(nonce, now + MessageCrypto.MAX_AGE_MILLIS * 2) != null;
    }
}
