# Velocity Commands

Tubing provides a streamlined command handling system for Velocity that eliminates boilerplate, automatically handles dependency injection, and integrates seamlessly with Velocity's `SimpleCommand` interface. This guide covers everything you need to know about creating commands in Tubing for Velocity proxies.

## Overview

The Tubing command system for Velocity offers several advantages over traditional command handling:

- **Automatic Registration**: Commands are discovered and registered automatically
- **Dependency Injection**: Command handlers receive dependencies via constructor injection
- **Command Aliases**: Built-in support for command aliases through the annotation
- **SimpleCommand Integration**: Direct implementation of Velocity's `SimpleCommand` interface
- **Tab Completion**: Native Velocity tab completion support
- **Reduced Boilerplate**: No manual command registration in your main plugin class
- **Clean Architecture**: Separate command logic from plugin initialization
- **Conditional Registration**: Register commands based on configuration properties
- **Property References**: Command names and aliases can reference configuration values
- **Multiprovider Support**: Load multiple command implementations dynamically

## Basic Command Handler

### Simple Command

To create a Velocity command in Tubing, you implement `com.velocitypowered.api.command.SimpleCommand` and use the `@IocVelocityCommandHandler` annotation:

```java
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

@IocVelocityCommandHandler("alert")
public class AlertCommand implements SimpleCommand {

    private final MessageService messageService;

    public AlertCommand(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command!"));
            return;
        }

        Player player = (Player) invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /alert <message>"));
            return;
        }

        String message = String.join(" ", args);
        messageService.broadcastAlert(message);
    }
}
```

**Key Points:**
- Implement `com.velocitypowered.api.command.SimpleCommand`
- Use `@IocVelocityCommandHandler` with the command name as the value
- Dependencies are injected through the constructor
- Override `execute()` to implement command logic
- No manual registration needed - Tubing handles it automatically

### @IocVelocityCommandHandler Annotation

The `@IocVelocityCommandHandler` annotation provides several attributes for configuring command registration:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IocVelocityCommandHandler {

    // The primary command name (required)
    String value();

    // Command aliases (optional)
    String[] aliases() default "";

    // Conditional registration based on config property
    String conditionalOnProperty() default "";

    // Multiprovider support
    Class multiproviderClass() default Object.class;

    // Priority loading
    boolean priority() default false;
}
```

## Command Aliases

Velocity commands support aliases through the annotation. Unlike BungeeCord, aliases are specified in the annotation rather than the constructor:

```java
@IocVelocityCommandHandler(value = "lobby", aliases = {"hub", "spawn"})
public class LobbyCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public LobbyCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            return;
        }

        Player player = (Player) invocation.source();
        serverManager.sendToLobby(player);
        player.sendMessage(Component.text("Connecting to lobby...", NamedTextColor.GREEN));
    }
}
```

**Multiple Aliases:**
```java
@IocVelocityCommandHandler(
    value = "teleport",
    aliases = {"tp", "tele", "goto"}
)
public class TeleportCommand implements SimpleCommand {
    // Implementation
}
```

The command is accessible via `/teleport`, `/tp`, `/tele`, and `/goto`.

## SimpleCommand Interface

### Invocation Object

The `SimpleCommand.Invocation` object provides access to the command context:

```java
@Override
public void execute(Invocation invocation) {
    // Get the command source (player or console)
    CommandSource source = invocation.source();

    // Get command arguments
    String[] args = invocation.arguments();

    // Get the alias used to invoke the command
    String alias = invocation.alias();
}
```

**CommandSource Methods:**
```java
// Send a message to the source
source.sendMessage(Component.text("Hello!"));

// Check permissions
boolean hasPermission = source.hasPermission("myplugin.command");

// Check if source is a player
if (source instanceof Player) {
    Player player = (Player) source;
    // Player-specific operations
}
```

### Execute Method

The `execute()` method is where your command logic lives:

```java
@Override
public void execute(Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    // Command implementation
}
```

**Important Notes:**
- The method does not return a value
- You must handle all error cases within the method
- Cast `source` to `Player` when player-specific actions are needed
- Arguments are provided as a `String[]` array (empty array if no arguments)

### Checking for Player Sources

```java
@Override
public void execute(Invocation invocation) {
    if (!(invocation.source() instanceof Player)) {
        invocation.source().sendMessage(
            Component.text("This command is only available to players!",
                         NamedTextColor.RED)
        );
        return;
    }

    Player player = (Player) invocation.source();
    // Player-specific logic
}
```

## Dependency Injection

Command handlers support full dependency injection through constructor parameters.

### Injecting Services

```java
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

@IocVelocityCommandHandler("msg")
public class MessageCommand implements SimpleCommand {

    private final MessageService messageService;
    private final PlayerDataRepository playerData;
    private final ConfigService config;

    public MessageCommand(MessageService messageService,
                         PlayerDataRepository playerData,
                         ConfigService config) {
        this.messageService = messageService;
        this.playerData = playerData;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(
                Component.text("Only players can send messages!")
            );
            return;
        }

        Player player = (Player) invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /msg <player> <message>"));
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
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
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

@IocVelocityCommandHandler("joinserver")
public class JoinServerCommand implements SimpleCommand {

    private final ServerConfig serverConfig;
    private final ProxyServer proxyServer;

    public JoinServerCommand(ServerConfig serverConfig, ProxyServer proxyServer) {
        this.serverConfig = serverConfig;
        this.proxyServer = proxyServer;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            return;
        }

        Player player = (Player) invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /joinserver <survival|creative>"));
            return;
        }

        String serverName = args[0].equalsIgnoreCase("survival")
            ? serverConfig.getSurvivalServer()
            : serverConfig.getCreativeServer();

        proxyServer.getServer(serverName).ifPresentOrElse(
            server -> player.createConnectionRequest(server).fireAndForget(),
            () -> player.sendMessage(Component.text("Server not found!", NamedTextColor.RED))
        );
    }
}
```

### Injecting ProxyServer

The Velocity `ProxyServer` instance can be injected directly:

```java
@IocVelocityCommandHandler("list")
public class ListCommand implements SimpleCommand {

    private final ProxyServer proxyServer;

    public ListCommand(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public void execute(Invocation invocation) {
        Collection<Player> players = proxyServer.getAllPlayers();

        invocation.source().sendMessage(
            Component.text("Online players (" + players.size() + "):",
                         NamedTextColor.YELLOW)
        );

        for (Player player : players) {
            invocation.source().sendMessage(
                Component.text("- " + player.getUsername())
            );
        }
    }
}
```

## Permissions

### Checking Permissions

Velocity's permission system is integrated into the `CommandSource` interface:

```java
@IocVelocityCommandHandler("admin")
public class AdminCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission("myplugin.admin")) {
            source.sendMessage(
                Component.text("You don't have permission to use this!",
                             NamedTextColor.RED)
            );
            return;
        }

        // Admin-only logic
        source.sendMessage(Component.text("Admin command executed!"));
    }
}
```

### Permission Nodes

Define permission constants for consistency:

```java
public class Permissions {
    public static final String SERVER_COMMAND = "myplugin.command.server";
    public static final String ADMIN_COMMAND = "myplugin.command.admin";
    public static final String RELOAD_COMMAND = "myplugin.command.reload";
}

@IocVelocityCommandHandler("reload")
public class ReloadCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission(Permissions.RELOAD_COMMAND)) {
            invocation.source().sendMessage(
                Component.text("No permission!", NamedTextColor.RED)
            );
            return;
        }

        // Reload logic
    }
}
```

### hasPermission Method

The `hasPermission()` method provides fine-grained permission checking:

```java
@Override
public void execute(Invocation invocation) {
    CommandSource source = invocation.source();

    // Check single permission
    if (source.hasPermission("myplugin.admin")) {
        // Admin functionality
    }

    // Check multiple permission tiers
    if (source.hasPermission("myplugin.mod")) {
        // Moderator functionality
    } else if (source.hasPermission("myplugin.helper")) {
        // Helper functionality
    } else {
        // Regular user functionality
    }
}
```

## Tab Completion

Velocity commands support tab completion through the `SimpleCommand` interface:

```java
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@IocVelocityCommandHandler("server")
public class ServerCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
        // Command implementation
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0 || args.length == 1) {
            // Return list of server names
            String partial = args.length == 1 ? args[0].toLowerCase() : "";

            return serverManager.getServerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
```

### Suggest Method

```java
List<String> suggest(Invocation invocation)
```

**Parameters:**
- `invocation`: The tab completion invocation context

**Returns:**
- A `List<String>` of completion suggestions
- Return an empty list if no completions are available

**Notes:**
- The method is called synchronously, so avoid long-running operations
- Arguments include the partial argument being typed
- Empty string in args array when user is at the start of an argument

### Dynamic Tab Completion

```java
@IocVelocityCommandHandler("teleport")
public class TeleportCommand implements SimpleCommand {

    private final ProxyServer proxyServer;

    public TeleportCommand(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public void execute(Invocation invocation) {
        // Teleport implementation
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> completions = new ArrayList<>();

        if (args.length <= 1) {
            // Complete player names
            String partial = args.length == 1 ? args[0].toLowerCase() : "";

            completions = proxyServer.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());

        } else if (args.length == 2) {
            // Complete target player names
            String partial = args[1].toLowerCase();

            completions = proxyServer.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .filter(name -> !name.equalsIgnoreCase(args[0])) // Exclude first player
                .collect(Collectors.toList());
        }

        return completions;
    }
}
```

### Async Tab Completion

For expensive tab completion operations, you can return a `CompletableFuture`:

```java
@Override
public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
    return CompletableFuture.supplyAsync(() -> {
        // Expensive operation
        return databaseService.getPlayerNames().stream()
            .filter(name -> name.startsWith(invocation.arguments()[0]))
            .collect(Collectors.toList());
    });
}
```

### Context-Aware Completions

```java
@IocVelocityCommandHandler("admin")
public class AdminCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        // Command implementation
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        // First argument: subcommands
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return Stream.of("reload", "status", "config", "debug")
                .filter(cmd -> cmd.startsWith(partial))
                .collect(Collectors.toList());
        }

        // Second argument: depends on subcommand
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            switch (subcommand) {
                case "config":
                    return Stream.of("get", "set", "list")
                        .filter(cmd -> cmd.startsWith(partial))
                        .collect(Collectors.toList());

                case "debug":
                    return Stream.of("enable", "disable")
                        .filter(cmd -> cmd.startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
```

## Property References

Command names and aliases can reference configuration properties using the `property:key` syntax:

```java
@IocVelocityCommandHandler(
    value = "commands:server-command",
    aliases = {"commands:server-aliases"}
)
public class ServerCommand implements SimpleCommand {
    // Implementation
}
```

**Configuration file:**
```yaml
commands:
  server-command: "server"
  server-aliases: "hub,lobby,spawn"
```

This allows you to configure command names externally without changing code.

**Note:** Properties containing a colon (`:`) are treated as property references. The value is resolved from the configuration at registration time. If the property is not found, a `TubingVelocityException` is thrown.

## Advanced Features

### Conditional Registration

Register commands only when a configuration property is enabled:

```java
@IocVelocityCommandHandler(
    value = "customserver",
    conditionalOnProperty = "features.custom-servers:enabled"
)
public class CustomServerCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public CustomServerCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
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

If the property is missing or `false`, the command is not registered.

### Multiprovider Support

Load multiple command implementations dynamically:

```java
public interface CommandProvider {
    // Marker interface for command providers
}

@IocVelocityCommandHandler(
    value = "dynamic",
    multiproviderClass = CommandProvider.class
)
public class DynamicCommand implements SimpleCommand {

    public DynamicCommand() {
        // Constructor
    }

    @Override
    public void execute(Invocation invocation) {
        // Implementation
    }
}
```

This is useful for plugin systems where multiple modules can provide commands. All beans implementing `CommandProvider` will be loaded as instances of the command.

### Priority Loading

Load commands with priority (useful for overriding default behavior):

```java
@IocVelocityCommandHandler(
    value = "priority",
    priority = true
)
public class PriorityCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        // This command is loaded with priority
    }
}
```

Priority commands are loaded first, allowing them to take precedence over other registrations.

## Messaging with Adventure

Velocity uses the [Adventure](https://docs.adventure.kyori.net/) library for text components. This provides rich formatting capabilities.

### Sending Messages

```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

@Override
public void execute(Invocation invocation) {
    CommandSource source = invocation.source();

    // Simple text
    source.sendMessage(Component.text("Hello, world!"));

    // Colored text
    source.sendMessage(Component.text("Success!", NamedTextColor.GREEN));

    // Formatted text
    source.sendMessage(
        Component.text("Bold and Red")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
    );
}
```

### Rich Text Components

```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

@Override
public void execute(Invocation invocation) {
    Component message = Component.text()
        .append(Component.text("Server Status: ", NamedTextColor.GRAY))
        .append(Component.text("Online", NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to refresh")
            ))
            .clickEvent(ClickEvent.runCommand("/server refresh"))
        )
        .build();

    invocation.source().sendMessage(message);
}
```

### Component Builder Pattern

```java
@Override
public void execute(Invocation invocation) {
    Component component = Component.text()
        .append(Component.text("[", NamedTextColor.DARK_GRAY))
        .append(Component.text("MyPlugin", NamedTextColor.GOLD))
        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
        .append(Component.text("Welcome!", NamedTextColor.YELLOW))
        .build();

    invocation.source().sendMessage(component);
}
```

### Interactive Components

```java
@Override
public void execute(Invocation invocation) {
    if (!(invocation.source() instanceof Player)) {
        return;
    }

    Player player = (Player) invocation.source();

    Component teleportMenu = Component.text()
        .append(Component.text("Select a server:", NamedTextColor.YELLOW))
        .append(Component.newline())
        .append(Component.text("[Survival]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/server survival"))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to join Survival")
            ))
        )
        .append(Component.text(" "))
        .append(Component.text("[Creative]", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.runCommand("/server creative"))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to join Creative")
            ))
        )
        .build();

    player.sendMessage(teleportMenu);
}
```

## Argument Handling

### Parsing Arguments

```java
@Override
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();

    if (args.length == 0) {
        invocation.source().sendMessage(
            Component.text("Usage: /mycommand <arg1> [arg2]")
        );
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
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();
    CommandSource source = invocation.source();

    if (args.length < 2) {
        source.sendMessage(
            Component.text("Usage: /transfer <player> <server>", NamedTextColor.RED)
        );
        return;
    }

    String playerName = args[0];
    String serverName = args[1];

    Optional<Player> targetOpt = proxyServer.getPlayer(playerName);
    if (!targetOpt.isPresent()) {
        source.sendMessage(
            Component.text("Player not found: " + playerName, NamedTextColor.RED)
        );
        return;
    }

    Optional<RegisteredServer> serverOpt = proxyServer.getServer(serverName);
    if (!serverOpt.isPresent()) {
        source.sendMessage(
            Component.text("Server not found: " + serverName, NamedTextColor.RED)
        );
        return;
    }

    Player target = targetOpt.get();
    RegisteredServer server = serverOpt.get();

    target.createConnectionRequest(server).fireAndForget();
    source.sendMessage(
        Component.text("Transferred " + playerName + " to " + serverName,
                     NamedTextColor.GREEN)
    );
}
```

### Parsing Numbers

```java
@Override
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();

    if (args.length == 0) {
        invocation.source().sendMessage(
            Component.text("Usage: /setslots <amount>")
        );
        return;
    }

    try {
        int slots = Integer.parseInt(args[0]);

        if (slots < 1 || slots > 1000) {
            invocation.source().sendMessage(
                Component.text("Slots must be between 1 and 1000", NamedTextColor.RED)
            );
            return;
        }

        // Use the slots value
        configureSlots(slots);
        invocation.source().sendMessage(
            Component.text("Set slots to: " + slots, NamedTextColor.GREEN)
        );

    } catch (NumberFormatException e) {
        invocation.source().sendMessage(
            Component.text("Invalid number: " + args[0], NamedTextColor.RED)
        );
    }
}
```

### Combining Arguments

```java
@Override
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();

    if (args.length < 1) {
        invocation.source().sendMessage(
            Component.text("Usage: /announce <message...>")
        );
        return;
    }

    // Join all arguments into a single message
    String message = String.join(" ", args);

    // Or skip the first argument
    String messageWithoutFirst = String.join(" ",
        Arrays.copyOfRange(args, 1, args.length)
    );

    // Broadcast the message
    proxyServer.sendMessage(Component.text(message, NamedTextColor.YELLOW));
}
```

## Error Handling

### Try-Catch Pattern

```java
@Override
public void execute(Invocation invocation) {
    try {
        // Command logic that might fail
        performRiskyOperation(invocation);

    } catch (IllegalArgumentException e) {
        invocation.source().sendMessage(
            Component.text("Invalid argument: " + e.getMessage(), NamedTextColor.RED)
        );
    } catch (IllegalStateException e) {
        invocation.source().sendMessage(
            Component.text("Command unavailable: " + e.getMessage(), NamedTextColor.RED)
        );
    } catch (Exception e) {
        invocation.source().sendMessage(
            Component.text("An error occurred while executing the command",
                         NamedTextColor.RED)
        );
        e.printStackTrace();
    }
}
```

### Custom Exception Handler

Create a service to handle common error patterns:

```java
import be.garagepoort.mcioc.IocBean;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@IocBean
public class CommandExceptionHandler {

    public void handle(CommandSource source, Exception e) {
        if (e instanceof IllegalArgumentException) {
            source.sendMessage(
                Component.text("Invalid input: " + e.getMessage(), NamedTextColor.RED)
            );
        } else if (e instanceof IllegalStateException) {
            source.sendMessage(
                Component.text("Action unavailable: " + e.getMessage(), NamedTextColor.RED)
            );
        } else {
            source.sendMessage(
                Component.text("An unexpected error occurred", NamedTextColor.RED)
            );
            e.printStackTrace();
        }
    }
}

@IocVelocityCommandHandler("mycommand")
public class MyCommand implements SimpleCommand {

    private final CommandExceptionHandler exceptionHandler;

    public MyCommand(CommandExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void execute(Invocation invocation) {
        try {
            // Command logic
        } catch (Exception e) {
            exceptionHandler.handle(invocation.source(), e);
        }
    }
}
```

## Working with Players

### Getting Online Players

```java
@Override
public void execute(Invocation invocation) {
    Collection<Player> players = proxyServer.getAllPlayers();

    invocation.source().sendMessage(
        Component.text("Online players (" + players.size() + "):",
                     NamedTextColor.YELLOW)
    );

    for (Player player : players) {
        invocation.source().sendMessage(
            Component.text("- " + player.getUsername())
        );
    }
}
```

### Finding a Specific Player

```java
@Override
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();

    if (args.length == 0) {
        invocation.source().sendMessage(
            Component.text("Usage: /info <player>")
        );
        return;
    }

    String playerName = args[0];
    Optional<Player> targetOpt = proxyServer.getPlayer(playerName);

    if (!targetOpt.isPresent()) {
        invocation.source().sendMessage(
            Component.text("Player not found: " + playerName, NamedTextColor.RED)
        );
        return;
    }

    Player target = targetOpt.get();

    invocation.source().sendMessage(Component.text("Player: " + target.getUsername()));

    target.getCurrentServer().ifPresent(server -> {
        invocation.source().sendMessage(
            Component.text("Server: " + server.getServerInfo().getName())
        );
    });

    invocation.source().sendMessage(
        Component.text("Ping: " + target.getPing() + "ms")
    );
}
```

### Connecting Players to Servers

```java
@Override
public void execute(Invocation invocation) {
    if (!(invocation.source() instanceof Player)) {
        invocation.source().sendMessage(
            Component.text("Only players can use this command!", NamedTextColor.RED)
        );
        return;
    }

    Player player = (Player) invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
        player.sendMessage(Component.text("Usage: /connect <server>"));
        return;
    }

    String serverName = args[0];
    Optional<RegisteredServer> serverOpt = proxyServer.getServer(serverName);

    if (!serverOpt.isPresent()) {
        player.sendMessage(
            Component.text("Server not found: " + serverName, NamedTextColor.RED)
        );
        return;
    }

    RegisteredServer server = serverOpt.get();
    player.createConnectionRequest(server).fireAndForget();
    player.sendMessage(
        Component.text("Connecting to " + serverName + "...", NamedTextColor.GREEN)
    );
}
```

## Best Practices

### 1. Validate Input Early

```java
@Override
public void execute(Invocation invocation) {
    // Validate all inputs at the start
    if (invocation.arguments().length < 2) {
        invocation.source().sendMessage(
            Component.text("Usage: /mycommand <arg1> <arg2>")
        );
        return;
    }

    if (!(invocation.source() instanceof Player)) {
        invocation.source().sendMessage(
            Component.text("Only players can use this command!")
        );
        return;
    }

    Player player = (Player) invocation.source();
    String[] args = invocation.arguments();

    // Now proceed with validated inputs
    String arg1 = args[0];
    String arg2 = args[1];

    // Command logic
}
```

### 2. Use Dependency Injection

```java
// Good: Use dependency injection
@IocVelocityCommandHandler("good")
public class GoodCommand implements SimpleCommand {

    private final PlayerService playerService;
    private final ConfigService config;

    public GoodCommand(PlayerService playerService, ConfigService config) {
        this.playerService = playerService;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        // Use injected dependencies
        playerService.doSomething();
    }
}

// Bad: Use singletons or static accessors
@IocVelocityCommandHandler("bad")
public class BadCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        // Don't do this
        PlayerService.getInstance().doSomething();
    }
}
```

### 3. Keep Commands Focused

Each command should have a single responsibility:

```java
// Good: Focused command
@IocVelocityCommandHandler("servers")
public class ListServersCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public ListServersCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
        // Only lists servers
        List<String> servers = serverManager.getServerList();
        invocation.source().sendMessage(
            Component.text("Available servers:", NamedTextColor.YELLOW)
        );
        servers.forEach(server -> invocation.source().sendMessage(
            Component.text("- " + server)
        ));
    }
}

// Bad: Command doing too much
@IocVelocityCommandHandler("serveradmin")
public class ServerAdminCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

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
public void execute(Invocation invocation) {
    String[] args = invocation.arguments();
    CommandSource source = invocation.source();

    if (args.length == 0) {
        // Clear usage message
        source.sendMessage(Component.text("Usage: /mycommand <action> [target]",
                                        NamedTextColor.YELLOW));
        source.sendMessage(Component.text("Actions: reload, status, help",
                                        NamedTextColor.YELLOW));
        return;
    }

    String action = args[0];

    switch (action.toLowerCase()) {
        case "reload":
            // Clear success message
            source.sendMessage(
                Component.text("Configuration reloaded successfully!",
                             NamedTextColor.GREEN)
            );
            break;
        case "status":
            source.sendMessage(
                Component.text("Status: All systems operational",
                             NamedTextColor.GREEN)
            );
            break;
        default:
            // Clear error message
            source.sendMessage(
                Component.text("Unknown action: " + action, NamedTextColor.RED)
            );
            source.sendMessage(
                Component.text("Available actions: reload, status, help",
                             NamedTextColor.YELLOW)
            );
    }
}
```

### 5. Handle Asynchronous Operations

Velocity is asynchronous by nature. For long-running operations, use the scheduler:

```java
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;

@IocVelocityCommandHandler("data")
public class DataCommand implements SimpleCommand {

    private final DataService dataService;
    private final ProxyServer proxyServer;
    private final TubingVelocityPlugin plugin;

    public DataCommand(DataService dataService,
                      ProxyServer proxyServer,
                      TubingVelocityPlugin plugin) {
        this.dataService = dataService;
        this.proxyServer = proxyServer;
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        source.sendMessage(Component.text("Loading data..."));

        // Perform async operation
        proxyServer.getScheduler()
            .buildTask(plugin, () -> {
                try {
                    // Long-running operation
                    List<String> data = dataService.loadData();

                    // Send results back to source
                    source.sendMessage(
                        Component.text("Data loaded: " + data.size() + " entries",
                                     NamedTextColor.GREEN)
                    );

                } catch (Exception e) {
                    source.sendMessage(
                        Component.text("Failed to load data: " + e.getMessage(),
                                     NamedTextColor.RED)
                    );
                }
            })
            .schedule();
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

@IocVelocityCommandHandler("mycommand")
public class MyCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (invocation.source().hasPermission(Permissions.ADMIN_COMMAND)) {
            // Admin-only functionality
        }
    }
}
```

### 7. Implement Tab Completion

Always implement tab completion for better user experience:

```java
@IocVelocityCommandHandler("mycommand")
public class MyCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        // Command implementation
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            List<String> options = Arrays.asList("reload", "status", "help");

            if (args.length == 0) {
                return options;
            }

            // Filter based on partial input
            String partial = args[0].toLowerCase();
            return options.stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
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
@IocVelocityCommandHandler(value = "server", aliases = {"hub", "lobby"})
public class ServerCommand implements SimpleCommand {
    // Implementation
}
```

### 9. Use Adventure Components

Always use Adventure `Component` API for text instead of legacy strings:

```java
// Good: Use Adventure Components
source.sendMessage(
    Component.text("Error: ", NamedTextColor.RED)
        .append(Component.text("Player not found"))
);

// Bad: Use legacy color codes
source.sendMessage(Component.text("§cError: Player not found"));
```

### 10. Handle Optional Values Properly

Velocity uses `Optional<T>` extensively. Handle them properly:

```java
// Good: Use Optional methods
proxyServer.getPlayer(name).ifPresentOrElse(
    player -> player.sendMessage(Component.text("Hello!")),
    () -> source.sendMessage(Component.text("Player not found"))
);

// Also good: Check with isPresent()
Optional<Player> playerOpt = proxyServer.getPlayer(name);
if (playerOpt.isPresent()) {
    Player player = playerOpt.get();
    // Use player
} else {
    // Handle not found
}

// Bad: Call get() without checking
Player player = proxyServer.getPlayer(name).get(); // May throw NoSuchElementException!
```

## Migration from Traditional Velocity Commands

### Before: Traditional Registration

```java
@Plugin(id = "myplugin")
public class MyPlugin {

    private final ProxyServer server;

    @Inject
    public MyPlugin(ProxyServer server, CommandManager commandManager) {
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        ServerManager serverManager = new ServerManager();
        commandManager.register("server", new ServerCommand(serverManager));
    }
}

public class ServerCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
        // Command logic
    }
}
```

### After: Tubing Registration

```java
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;

@IocVelocityCommandHandler("server")
public class ServerCommand implements SimpleCommand {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void execute(Invocation invocation) {
        // Command logic
    }
}
```

**Benefits:**
- No manual registration in main plugin class
- Automatic dependency injection
- Cleaner separation of concerns
- Easier testing with mock dependencies
- Built-in support for aliases and conditional registration

## Common Patterns

### Subcommand Pattern

```java
@IocVelocityCommandHandler("admin")
public class AdminCommand implements SimpleCommand {

    private final ReloadHandler reloadHandler;
    private final StatusHandler statusHandler;
    private final ConfigHandler configHandler;

    public AdminCommand(ReloadHandler reloadHandler,
                       StatusHandler statusHandler,
                       ConfigHandler configHandler) {
        this.reloadHandler = reloadHandler;
        this.statusHandler = statusHandler;
        this.configHandler = configHandler;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendHelp(invocation.source());
            return;
        }

        String subcommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case "reload":
                reloadHandler.handle(invocation.source(), subArgs);
                break;
            case "status":
                statusHandler.handle(invocation.source(), subArgs);
                break;
            case "config":
                configHandler.handle(invocation.source(), subArgs);
                break;
            default:
                invocation.source().sendMessage(
                    Component.text("Unknown subcommand: " + subcommand,
                                 NamedTextColor.RED)
                );
                sendHelp(invocation.source());
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return Stream.of("reload", "status", "config")
                .filter(cmd -> cmd.startsWith(partial))
                .collect(Collectors.toList());
        }

        // Delegate to subcommand handlers for further completion
        String subcommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case "config":
                return configHandler.suggest(subArgs);
            default:
                return Collections.emptyList();
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(
            Component.text("Available subcommands:", NamedTextColor.YELLOW)
        );
        source.sendMessage(
            Component.text("  /admin reload - Reload configuration",
                         NamedTextColor.YELLOW)
        );
        source.sendMessage(
            Component.text("  /admin status - Show plugin status",
                         NamedTextColor.YELLOW)
        );
        source.sendMessage(
            Component.text("  /admin config - Configure settings",
                         NamedTextColor.YELLOW)
        );
    }
}
```

### Help Command Pattern

```java
@IocVelocityCommandHandler("help")
public class HelpCommand implements SimpleCommand {

    private final Map<String, String> commandHelp = new HashMap<>();

    public HelpCommand() {
        initializeHelp();
    }

    private void initializeHelp() {
        commandHelp.put("server", "Connect to a different server");
        commandHelp.put("msg", "Send a private message to a player");
        commandHelp.put("list", "Show online players");
        commandHelp.put("help", "Show this help message");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            showAllHelp(invocation.source());
        } else {
            showCommandHelp(invocation.source(), args[0]);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return commandHelp.keySet().stream()
                .filter(cmd -> cmd.startsWith(partial))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private void showAllHelp(CommandSource source) {
        source.sendMessage(
            Component.text("=== Available Commands ===", NamedTextColor.GOLD)
        );

        commandHelp.forEach((cmd, desc) -> {
            source.sendMessage(
                Component.text()
                    .append(Component.text("/" + cmd, NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(desc, NamedTextColor.WHITE))
                    .build()
            );
        });
    }

    private void showCommandHelp(CommandSource source, String command) {
        String help = commandHelp.get(command.toLowerCase());

        if (help != null) {
            source.sendMessage(
                Component.text()
                    .append(Component.text("/" + command, NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(help, NamedTextColor.WHITE))
                    .build()
            );
        } else {
            source.sendMessage(
                Component.text("Unknown command: " + command, NamedTextColor.RED)
            );
        }
    }
}
```

## See Also

- [Velocity Listeners](Listeners.md) - Event handling in Velocity
- [Dependency Injection](../core/Dependency-Injection.md) - Understanding DI in Tubing
- [Configuration](../core/Configuration.md) - Configuration management
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Understanding bean creation and initialization
- [Adventure Documentation](https://docs.adventure.kyori.net/) - Text component library used by Velocity
