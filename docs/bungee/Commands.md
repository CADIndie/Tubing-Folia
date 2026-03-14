# BungeeCord Commands

Tubing provides a streamlined command handling system for BungeeCord that eliminates boilerplate, automatically handles dependency injection, and simplifies command registration. This guide covers everything you need to know about creating commands in Tubing for BungeeCord proxies.

## Overview

The Tubing command system for BungeeCord offers several advantages over traditional command handling:

- **Automatic Registration**: Commands are discovered and registered automatically
- **Dependency Injection**: Command handlers receive dependencies via constructor injection
- **Reduced Boilerplate**: No manual command registration in your main plugin class
- **Clean Architecture**: Separate command logic from plugin initialization
- **Conditional Registration**: Register commands based on configuration properties
- **Multiprovider Support**: Load multiple command implementations dynamically

## Basic Command Handler

### Simple Command

To create a BungeeCord command in Tubing, you extend `net.md_5.bungee.api.plugin.Command` and use the `@IocBungeeCommandHandler` annotation:

```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

@IocBungeeCommandHandler
public class ServerCommand extends Command {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        super("server", null, "hub", "lobby");
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage("Only players can use this command!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /server <name>");
            return;
        }

        serverManager.connectToServer(player, args[0]);
    }
}
```

**Key Points:**
- Extend `net.md_5.bungee.api.plugin.Command`
- Use `@IocBungeeCommandHandler` to mark the class for automatic registration
- Pass command name, permission, and aliases to the `super()` constructor
- Dependencies are injected through the constructor
- Override `execute()` to implement command logic
- No manual registration needed - Tubing handles it automatically

### Command Constructor

The BungeeCord `Command` class constructor signature is:

```java
public Command(String name, String permission, String... aliases)
```

**Parameters:**
- `name`: The primary command name (e.g., "server")
- `permission`: Optional permission node required to execute the command (can be `null`)
- `aliases`: Variable number of alternative command names

**Example with permission:**
```java
public ServerCommand(ServerManager serverManager) {
    super("server", "myplugin.server", "hub", "lobby");
    this.serverManager = serverManager;
}
```

## Command Method Signatures

### Execute Method

The `execute()` method is where your command logic lives:

```java
@Override
public void execute(CommandSender sender, String[] args) {
    // Command implementation
}
```

**Parameters:**
- `sender`: The `CommandSender` executing the command (player or console)
- `args`: Array of command arguments (empty array if no arguments)

**Important Notes:**
- The method does not return a boolean (unlike Bukkit commands)
- You must handle all error cases within the method
- Cast `sender` to `ProxiedPlayer` when player-specific actions are needed

### Checking for Player Senders

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
        sender.sendMessage("This command is only available to players!");
        return;
    }

    ProxiedPlayer player = (ProxiedPlayer) sender;
    // Player-specific logic
}
```

## Dependency Injection

Command handlers support full dependency injection through constructor parameters.

### Injecting Services

```java
@IocBungeeCommandHandler
public class MessageCommand extends Command {

    private final MessageService messageService;
    private final PlayerDataRepository playerData;
    private final ConfigService config;

    public MessageCommand(MessageService messageService,
                         PlayerDataRepository playerData,
                         ConfigService config) {
        super("msg", "myplugin.message", "tell", "whisper");
        this.messageService = messageService;
        this.playerData = playerData;
        this.config = config;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage("Only players can send messages!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length < 2) {
            player.sendMessage("Usage: /msg <player> <message>");
            return;
        }

        String targetName = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        messageService.sendPrivateMessage(player, targetName, message);
    }
}
```

### Injecting Configuration

You can inject configuration services or use `@ConfigProperty` in injected beans:

```java
@IocMultiProvider(ServerProvider.class)
public class ServerConfig {

    @ConfigProperty("servers.survival")
    private String survivalServer;

    @ConfigProperty("servers.creative")
    private String creativeServer;

    public String getSurvivalServer() {
        return survivalServer;
    }

    public String getCreativeServer() {
        return creativeServer;
    }
}

@IocBungeeCommandHandler
public class JoinServerCommand extends Command {

    private final ServerConfig serverConfig;

    public JoinServerCommand(ServerConfig serverConfig) {
        super("joinserver", "myplugin.joinserver");
        this.serverConfig = serverConfig;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /joinserver <survival|creative>");
            return;
        }

        String serverName = args[0].equalsIgnoreCase("survival")
            ? serverConfig.getSurvivalServer()
            : serverConfig.getCreativeServer();

        ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(serverName);
        if (serverInfo != null) {
            player.connect(serverInfo);
        } else {
            player.sendMessage("Server not found!");
        }
    }
}
```

## Permissions

### Setting Permissions

Permissions are set in the Command constructor:

```java
public MyCommand() {
    super("mycommand", "myplugin.mycommand");
}
```

### Runtime Permission Checks

BungeeCord automatically checks the permission before executing the command. For additional permission checks:

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (!sender.hasPermission("myplugin.admin")) {
        sender.sendMessage("You don't have permission to use this!");
        return;
    }

    // Admin-only logic
}
```

### No Permission Set

If you want the command to be available to everyone, pass `null` as the permission:

```java
public HelpCommand() {
    super("help", null);
}
```

## Tab Completion

BungeeCord commands can provide tab completion by implementing the `net.md_5.bungee.api.plugin.TabExecutor` interface:

```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@IocBungeeCommandHandler
public class ServerCommand extends Command implements TabExecutor {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        super("server", "myplugin.server");
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command implementation
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Return list of server names
            return serverManager.getServerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
```

### Tab Completion Method

```java
Iterable<String> onTabComplete(CommandSender sender, String[] args)
```

**Parameters:**
- `sender`: The command sender requesting tab completion
- `args`: Current command arguments including the partial argument being typed

**Returns:**
- An `Iterable<String>` of completion suggestions (typically a `List<String>`)
- Return an empty list if no completions are available

### Dynamic Tab Completion

```java
@IocBungeeCommandHandler
public class TeleportCommand extends Command implements TabExecutor {

    private final PlayerTracker playerTracker;

    public TeleportCommand(PlayerTracker playerTracker) {
        super("tp", "myplugin.teleport");
        this.playerTracker = playerTracker;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Teleport implementation
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete player names
            String partial = args[0].toLowerCase();
            completions = playerTracker.getOnlinePlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Complete target player names
            String partial = args[1].toLowerCase();
            completions = playerTracker.getOnlinePlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return completions;
    }
}
```

## Advanced Features

### Conditional Registration

Register commands only when a configuration property is enabled:

```java
@IocBungeeCommandHandler(conditionalOnProperty = "features.custom-servers:enabled")
public class CustomServerCommand extends Command {

    public CustomServerCommand(ServerManager serverManager) {
        super("customserver", "myplugin.customserver");
        // ...
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command implementation
    }
}
```

The command is only registered if the configuration file contains:

```yaml
features:
  custom-servers:
    enabled: true
```

### Multiprovider Support

Load multiple command implementations dynamically:

```java
public interface CommandProvider {
    // Marker interface for command providers
}

@IocBungeeCommandHandler(multiproviderClass = CommandProvider.class)
public class DynamicCommand extends Command {

    public DynamicCommand() {
        super("dynamic", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Implementation
    }
}
```

This is useful for plugin systems where multiple modules can provide commands.

### Priority Loading

Load commands with priority (useful for overriding default behavior):

```java
@IocBungeeCommandHandler(priority = true)
public class PriorityCommand extends Command {

    public PriorityCommand() {
        super("priority", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // This command is loaded with priority
    }
}
```

## Messaging and Formatting

### Sending Messages

```java
@Override
public void execute(CommandSender sender, String[] args) {
    // Simple text
    sender.sendMessage("Hello, world!");

    // Colored text using ChatColor
    sender.sendMessage(ChatColor.GREEN + "Success!");

    // Text components (recommended for BungeeCord)
    TextComponent message = new TextComponent("Click here!");
    message.setColor(ChatColor.BLUE);
    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help"));
    sender.sendMessage(message);
}
```

### Rich Text Components

```java
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

@Override
public void execute(CommandSender sender, String[] args) {
    ComponentBuilder builder = new ComponentBuilder("Server Status: ")
        .color(ChatColor.GRAY)
        .append("Online")
        .color(ChatColor.GREEN)
        .bold(true);

    TextComponent component = new TextComponent(builder.create());
    component.setHoverEvent(new HoverEvent(
        HoverEvent.Action.SHOW_TEXT,
        new ComponentBuilder("Click to refresh").create()
    ));
    component.setClickEvent(new ClickEvent(
        ClickEvent.Action.RUN_COMMAND,
        "/server refresh"
    ));

    sender.sendMessage(component);
}
```

## Argument Handling

### Parsing Arguments

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
        sender.sendMessage("Usage: /mycommand <arg1> [arg2]");
        return;
    }

    String requiredArg = args[0];

    String optionalArg = args.length > 1 ? args[1] : "default";

    // Process command
}
```

### Validating Arguments

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length < 2) {
        sender.sendMessage(ChatColor.RED + "Usage: /transfer <player> <server>");
        return;
    }

    String playerName = args[0];
    String serverName = args[1];

    ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);
    if (target == null) {
        sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
        return;
    }

    ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
    if (server == null) {
        sender.sendMessage(ChatColor.RED + "Server not found: " + serverName);
        return;
    }

    target.connect(server);
    sender.sendMessage(ChatColor.GREEN + "Transferred " + playerName + " to " + serverName);
}
```

### Parsing Numbers

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
        sender.sendMessage("Usage: /setslots <amount>");
        return;
    }

    try {
        int slots = Integer.parseInt(args[0]);
        if (slots < 1 || slots > 1000) {
            sender.sendMessage("Slots must be between 1 and 1000");
            return;
        }

        // Use the slots value
        configureSlots(slots);
        sender.sendMessage("Set slots to: " + slots);

    } catch (NumberFormatException e) {
        sender.sendMessage("Invalid number: " + args[0]);
    }
}
```

### Combining Arguments

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length < 2) {
        sender.sendMessage("Usage: /announce <message...>");
        return;
    }

    // Join all arguments into a single message
    String message = String.join(" ", args);

    // Or skip the first argument
    String messageWithoutFirst = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

    // Broadcast the message
    ProxyServer.getInstance().broadcast(message);
}
```

## Error Handling

### Try-Catch Pattern

```java
@Override
public void execute(CommandSender sender, String[] args) {
    try {
        // Command logic that might fail
        performRiskyOperation(sender, args);

    } catch (IllegalArgumentException e) {
        sender.sendMessage(ChatColor.RED + "Invalid argument: " + e.getMessage());
    } catch (IllegalStateException e) {
        sender.sendMessage(ChatColor.RED + "Command unavailable: " + e.getMessage());
    } catch (Exception e) {
        sender.sendMessage(ChatColor.RED + "An error occurred while executing the command");
        e.printStackTrace();
    }
}
```

### Custom Exception Handler

Create a service to handle common error patterns:

```java
@IocBean
public class CommandExceptionHandler {

    public void handle(CommandSender sender, Exception e) {
        if (e instanceof IllegalArgumentException) {
            sender.sendMessage(ChatColor.RED + "Invalid input: " + e.getMessage());
        } else if (e instanceof IllegalStateException) {
            sender.sendMessage(ChatColor.RED + "Action unavailable: " + e.getMessage());
        } else {
            sender.sendMessage(ChatColor.RED + "An unexpected error occurred");
            e.printStackTrace();
        }
    }
}

@IocBungeeCommandHandler
public class MyCommand extends Command {

    private final CommandExceptionHandler exceptionHandler;

    public MyCommand(CommandExceptionHandler exceptionHandler) {
        super("mycommand", null);
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        try {
            // Command logic
        } catch (Exception e) {
            exceptionHandler.handle(sender, e);
        }
    }
}
```

## Working with Players

### Getting Online Players

```java
@Override
public void execute(CommandSender sender, String[] args) {
    Collection<ProxiedPlayer> players = ProxyServer.getInstance().getPlayers();

    sender.sendMessage("Online players (" + players.size() + "):");
    for (ProxiedPlayer player : players) {
        sender.sendMessage("- " + player.getName());
    }
}
```

### Finding a Specific Player

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
        sender.sendMessage("Usage: /info <player>");
        return;
    }

    String playerName = args[0];
    ProxiedPlayer target = ProxyServer.getInstance().getPlayer(playerName);

    if (target == null) {
        sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
        return;
    }

    sender.sendMessage("Player: " + target.getName());
    sender.sendMessage("Server: " + target.getServer().getInfo().getName());
    sender.sendMessage("Ping: " + target.getPing() + "ms");
}
```

### Connecting Players to Servers

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
        sender.sendMessage("Only players can use this command!");
        return;
    }

    ProxiedPlayer player = (ProxiedPlayer) sender;

    if (args.length == 0) {
        sender.sendMessage("Usage: /connect <server>");
        return;
    }

    String serverName = args[0];
    ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);

    if (server == null) {
        player.sendMessage(ChatColor.RED + "Server not found: " + serverName);
        return;
    }

    player.connect(server);
    player.sendMessage(ChatColor.GREEN + "Connecting to " + serverName + "...");
}
```

## Best Practices

### 1. Validate Input Early

```java
@Override
public void execute(CommandSender sender, String[] args) {
    // Validate all inputs at the start
    if (args.length < 2) {
        sender.sendMessage("Usage: /mycommand <arg1> <arg2>");
        return;
    }

    if (!(sender instanceof ProxiedPlayer)) {
        sender.sendMessage("Only players can use this command!");
        return;
    }

    ProxiedPlayer player = (ProxiedPlayer) sender;

    // Now proceed with validated inputs
    String arg1 = args[0];
    String arg2 = args[1];

    // Command logic
}
```

### 2. Use Dependency Injection

```java
// Good: Use dependency injection
@IocBungeeCommandHandler
public class GoodCommand extends Command {

    private final PlayerService playerService;
    private final ConfigService config;

    public GoodCommand(PlayerService playerService, ConfigService config) {
        super("good", null);
        this.playerService = playerService;
        this.config = config;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Use injected dependencies
        playerService.doSomething();
    }
}

// Bad: Use singletons or static accessors
@IocBungeeCommandHandler
public class BadCommand extends Command {

    public BadCommand() {
        super("bad", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Don't do this
        PlayerService.getInstance().doSomething();
    }
}
```

### 3. Keep Commands Focused

Each command should have a single responsibility:

```java
// Good: Focused command
@IocBungeeCommandHandler
public class ListServersCommand extends Command {

    private final ServerManager serverManager;

    public ListServersCommand(ServerManager serverManager) {
        super("servers", null);
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Only lists servers
        List<String> servers = serverManager.getServerList();
        sender.sendMessage("Available servers:");
        servers.forEach(server -> sender.sendMessage("- " + server));
    }
}

// Bad: Command doing too much
@IocBungeeCommandHandler
public class ServerAdminCommand extends Command {

    public ServerAdminCommand() {
        super("serveradmin", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Don't create a mega-command that does everything
        if (args[0].equals("list")) {
            // List servers
        } else if (args[0].equals("add")) {
            // Add server
        } else if (args[0].equals("remove")) {
            // Remove server
        } else if (args[0].equals("config")) {
            // Configure server
        }
        // ... and so on
    }
}
```

### 4. Provide Clear Feedback

```java
@Override
public void execute(CommandSender sender, String[] args) {
    if (args.length == 0) {
        // Clear usage message
        sender.sendMessage(ChatColor.YELLOW + "Usage: /mycommand <action> [target]");
        sender.sendMessage(ChatColor.YELLOW + "Actions: reload, status, help");
        return;
    }

    String action = args[0];

    switch (action.toLowerCase()) {
        case "reload":
            // Clear success message
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            break;
        case "status":
            sender.sendMessage(ChatColor.GREEN + "Status: All systems operational");
            break;
        default:
            // Clear error message
            sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
            sender.sendMessage(ChatColor.YELLOW + "Available actions: reload, status, help");
    }
}
```

### 5. Handle Asynchronous Operations

BungeeCord is largely asynchronous. For long-running operations:

```java
@IocBungeeCommandHandler
public class DataCommand extends Command {

    private final DataService dataService;

    public DataCommand(DataService dataService) {
        super("data", null);
        this.dataService = dataService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Loading data...");

        // Perform async operation
        ProxyServer.getInstance().getScheduler().runAsync(
            TubingBungeePlugin.getInstance(),
            () -> {
                try {
                    // Long-running operation
                    List<String> data = dataService.loadData();

                    // Send results back to sender
                    sender.sendMessage(ChatColor.GREEN + "Data loaded: " + data.size() + " entries");

                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Failed to load data: " + e.getMessage());
                }
            }
        );
    }
}
```

### 6. Use Constants for Permissions

```java
public class Permissions {
    public static final String SERVER_COMMAND = "myplugin.command.server";
    public static final String ADMIN_COMMAND = "myplugin.command.admin";
    public static final String RELOAD_COMMAND = "myplugin.command.reload";
}

@IocBungeeCommandHandler
public class MyCommand extends Command {

    public MyCommand() {
        super("mycommand", Permissions.SERVER_COMMAND);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission(Permissions.ADMIN_COMMAND)) {
            // Admin-only functionality
        }
    }
}
```

### 7. Implement Tab Completion

Always implement tab completion for better user experience:

```java
@IocBungeeCommandHandler
public class MyCommand extends Command implements TabExecutor {

    public MyCommand() {
        super("mycommand", null);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command implementation
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("status");
            completions.add("help");

            // Filter based on partial input
            String partial = args[0].toLowerCase();
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return completions;
    }
}
```

### 8. Document Your Commands

```java
/**
 * Command to manage server connections and player transfers.
 *
 * Usage:
 *   /server - Show current server
 *   /server <name> - Connect to a server
 *   /server list - List available servers
 *
 * Permissions:
 *   myplugin.command.server - Use the command
 *   myplugin.command.server.list - List servers
 *
 * Aliases: hub, lobby
 */
@IocBungeeCommandHandler
public class ServerCommand extends Command {
    // Implementation
}
```

## Migration from Traditional BungeeCord Commands

### Before: Traditional Registration

```java
public class MyPlugin extends Plugin {

    @Override
    public void onEnable() {
        ServerManager serverManager = new ServerManager();
        getProxy().getPluginManager().registerCommand(
            this, new ServerCommand(this, serverManager));
    }
}

public class ServerCommand extends Command {

    private final Plugin plugin;
    private final ServerManager serverManager;

    public ServerCommand(Plugin plugin, ServerManager serverManager) {
        super("server", null);
        this.plugin = plugin;
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command logic
    }
}
```

### After: Tubing Registration

```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;

@IocBungeeCommandHandler
public class ServerCommand extends Command {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        super("server", null);
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command logic
    }
}
```

**Benefits:**
- No manual registration in main plugin class
- Automatic dependency injection
- Cleaner separation of concerns
- Easier testing with mock dependencies

## Common Patterns

### Subcommand Pattern

```java
@IocBungeeCommandHandler
public class AdminCommand extends Command {

    private final ReloadHandler reloadHandler;
    private final StatusHandler statusHandler;
    private final ConfigHandler configHandler;

    public AdminCommand(ReloadHandler reloadHandler,
                       StatusHandler statusHandler,
                       ConfigHandler configHandler) {
        super("admin", "myplugin.admin");
        this.reloadHandler = reloadHandler;
        this.statusHandler = statusHandler;
        this.configHandler = configHandler;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String subcommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case "reload":
                reloadHandler.handle(sender, subArgs);
                break;
            case "status":
                statusHandler.handle(sender, subArgs);
                break;
            case "config":
                configHandler.handle(sender, subArgs);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subcommand);
                sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Available subcommands:");
        sender.sendMessage(ChatColor.YELLOW + "  /admin reload - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "  /admin status - Show plugin status");
        sender.sendMessage(ChatColor.YELLOW + "  /admin config - Configure settings");
    }
}
```

### Help Command Pattern

```java
@IocBungeeCommandHandler
public class HelpCommand extends Command implements TabExecutor {

    private final Map<String, String> commandHelp = new HashMap<>();

    public HelpCommand() {
        super("help", null);
        initializeHelp();
    }

    private void initializeHelp() {
        commandHelp.put("server", "Connect to a different server");
        commandHelp.put("msg", "Send a private message to a player");
        commandHelp.put("list", "Show online players");
        commandHelp.put("help", "Show this help message");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAllHelp(sender);
        } else {
            showCommandHelp(sender, args[0]);
        }
    }

    private void showAllHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Available Commands ===");
        commandHelp.forEach((cmd, desc) -> {
            sender.sendMessage(ChatColor.YELLOW + "/" + cmd + ChatColor.WHITE + " - " + desc);
        });
    }

    private void showCommandHelp(CommandSender sender, String command) {
        String help = commandHelp.get(command.toLowerCase());
        if (help != null) {
            sender.sendMessage(ChatColor.YELLOW + "/" + command + ChatColor.WHITE + " - " + help);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown command: " + command);
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return commandHelp.keySet().stream()
                .filter(cmd -> cmd.startsWith(partial))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
```

## See Also

- [BungeeCord Listeners](Listeners.md) - Event handling in BungeeCord
- [Dependency Injection](../core/Dependency-Injection.md) - Understanding DI in Tubing
- [Configuration](../core/Configuration.md) - Configuration management
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Understanding bean creation and initialization
