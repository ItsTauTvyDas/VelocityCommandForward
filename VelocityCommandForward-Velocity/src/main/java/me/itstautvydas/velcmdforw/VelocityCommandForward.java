package me.itstautvydas.velcmdforw;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.UUID;

@Plugin(
        id = "velocity-command-forward",
        name = "VelocityCommandForward",
        version = BuildConstants.VERSION,
        url = "https://itstautvydas.me",
        authors = { "ItsTauTvyDas" }
)
@SuppressWarnings("ClassCanBeRecord")
public class VelocityCommandForward {

    private final Logger logger;
    private final ProxyServer proxy;

    private static final String CHANNEL_ID = "velcmdforw:main";
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(CHANNEL_ID);
    private static final LegacyChannelIdentifier LEGACY_CHANNEL = new LegacyChannelIdentifier(CHANNEL_ID);

    @Inject
    public VelocityCommandForward(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL, LEGACY_CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL_ID.equals(event.getIdentifier().getId())) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection)) return;

        @SuppressWarnings("UnstableApiUsage")
        var in = ByteStreams.newDataInput(event.getData());
        var uuidRaw = in.readUTF();
        var command = in.readUTF();
        byte flags = in.readByte();
        String log = in.readUTF();
        boolean shouldFilterCommand = (flags & 0x01) != 0;

        if (uuidRaw.isEmpty()) { // Console
            if (!shouldFilterCommand && !log.isEmpty()) logger.info(log);
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
        } else { // Player
            var uuid = UUID.fromString(uuidRaw);
            proxy.getPlayer(uuid).ifPresent(player -> {
                if (!shouldFilterCommand && !log.isEmpty()) logger.info(log);
                proxy.getCommandManager().executeAsync(player, command);
            });
        }
    }
}
