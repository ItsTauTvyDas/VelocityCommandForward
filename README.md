# VelocityCommandForward

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/velocitycommandforward) [![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_vector.svg)](https://hangar.papermc.io/ItsTauTvyDas/VelocityCommandForward)

![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/velocitycommandforward?style=for-the-badge)

**Forward commands from backend to proxy!**

## ✨ Features
* Custom command.
* Custom message.
* Customize commands to be sent silently (without sending messages or console logs).
* Players and console can execute proxy commands (sends a packet to the proxy to execute either as player or console).

## 📥 How to install? / Installation / Setup
Very simple, download both Velocity and Paper plugin and upload to their designed servers (proxy/backend).

1. Turn off your proxy (Velocity) and backend (Paper) server.
2. Download Velocity and Paper version plugin.
3. Drop both plugin files (for your server version) into the `plugins` folder.
4. Start your server.

## 💡 Notes
* Configuration only generates for backend server!

## ❓ Use case for this?
* My use case was very niche, I have a discord command that can run minecraft commands (if player is online) and
the issue was that the discord bot was running on backend server, and so I decided to make a plugin to run proxy
commands, such as `/skin`.
* Another common use case is when you install the TAB plugin on Velocity and need to send commands from the backend server to the proxy server, such as `/btab scoreboard` to toggle TAB's scoreboard. While executing it manually works fine, if you use custom items from other plugins that are bound to this command, the command may fail to execute. In such cases, simply relying on this plugin's commands can resolve the issue of TAB commands not working.

## 🕹️ Command usage
Command name can be changed in the backend plugin configuration:

```yaml
custom-command: proxyexec
```

| command                | description                                                                                          | permission                        |
|------------------------|------------------------------------------------------------------------------------------------------|-----------------------------------|
| `/proxyexec reload`    | Used to reload plugin config. Changing `custom-command` requires a server restart.                   | velocitycommandforward.admin (op) |
| `/proxyexec <command>` | Forward commands to the proxy. Example: `/proxyexec tab reload` executes `/tab reload` on the proxy. | velocitycommandforward.send       |

In the examples I changed the command to `proxy`, I didn't set it as default because I was worried that some other plugin could use this command already.

<img width="900" height="230" alt="PixPin_2025-08-11_01-42-47" src="https://github.com/user-attachments/assets/456e0029-ac56-4b4f-9df8-03102123fa5c" />

## Executing in console
If you execute the command in the console, you won't get any output, and instead it's going to show up in the proxy server logs.
There must be at least one online player to be able to send command packet to the proxy (that's how plugin messaging channels work between backend and proxy).

Backend server.

<img width="1300" height="100" alt="PixPin_2025-08-11_01-43-40" src="https://github.com/user-attachments/assets/32f065ae-9b73-4988-8c72-b7e6d6a244df" />

Proxy server.

<img width="1300" height="75" alt="PixPin_2025-08-11_01-44-22" src="https://github.com/user-attachments/assets/6e407eaa-9c59-4389-a03f-21472732198b" />

## 📄 Default configuration
Changing `custom-command` requires a server restart.
```yaml
# VelocityCommandForward

# Command for forwarding commands to proxy.
# You need to restart the server for the changes to apply.
custom-command: proxyexec

# Here you can specify root commands that will not send log and message.
filtered-commands:
  - example_command

# {command} = The command that was sent by the sender
# {sender} = The player who sent the command
# If set to '', no message will be sent.
messages:
  reload: '&aPlugin messages and filters have been reloaded!'
  command-sent-as-player: '&2Command sent to proxy &7=> &a/{command}'
  command-sent-as-console: '&2Command sent as console to proxy &7=> &a/{command}'
  no-online-player: '&cThere must be at least 1 online player to be able to execute proxy console commands!'
  console-log: '[{sender}] Sending command packet to proxy => /{command}'
  velocity-log: '[{sender}] Received proxy command packet => /{command}'
```

## ✅ Requirements
* Velocity plugin was built with `Java 17` and Paper plugin is `Java 21`.
* Compatible with versions 1.20.6 and above. Since the plugin uses Brigadier (added in 1.20.6) to build commands, servers running lower versions will encounter errors.
