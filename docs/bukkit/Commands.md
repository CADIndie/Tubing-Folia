# Bukkit Commands

Tubing provides a powerful command handling system for Bukkit that eliminates boilerplate, automatically handles dependency injection, and provides built-in permission checking, argument validation, and error handling. This guide covers everything you need to know about creating commands in Tubing.

## Overview

The Tubing command system offers several advantages over traditional Bukkit command handling:

- **Automatic Registration**: Commands are discovered and registered automatically
- **Dependency Injection**: Command handlers receive dependencies via constructor injection
- **Permission Management**: Built-in permission checking with `@IocBukkitCommandHandler`
- **Player-Only Commands**: Enforce player-only execution with a single attribute
- **Subcommand Support**: Create complex command hierarchies with root and subcommands
- **Tab Completion**: Implement tab completion with the `TabCompleter` interface
- **Exception Handling**: Centralized error handling with customizable handlers
- **Conditional Registration**: Register commands based on configuration properties

## Basic Command Handler

### Simple Command

The simplest way to create a command is to extend `AbstractCmd` and use the `@IocBukkitCommandHandler` annotation:

```java
@IocBukkitCommandHandler("hello")
public class HelloCommand extends AbstractCmd {

    public HelloCommand(CommandExceptionHandler exceptionHandler,
                       TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        sender.sendMessage("Hello, world!");
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0; // No arguments required
    }
}
```

**Key Points:**
- The `@IocBukkitCommandHandler` annotation takes the command name as its value
- Extend `AbstractCmd` for built-in validation and error handling
- Dependencies are injected via constructor
- Override `executeCmd()` to implement command logic
- Override `getMinimumArguments()` to specify argument requirements

### plugin.yml Registration

While Tubing registers the command executor automatically, you still need to declare the command in `plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin

commands:
  hello:
    description: Says hello to the world
    usage: /hello
```

**Important**: Tubing sets the `CommandExecutor` automatically, but the command must be declared in `plugin.yml` for Bukkit to recognize it.

## @IocBukkitCommandHandler Annotation

The `@IocBukkitCommandHandler` annotation is the foundation of the Tubing command system. It marks a class as a command handler and configures its behavior.

### Annotation Attributes

```java
@IocBukkitCommandHandler(
    value = "mycommand",              // Command name (required)
    permission = "myplugin.command",  // Required permission (optional)
    onlyPlayers = true,                // Restrict to players only (default: false)
    conditionalOnProperty = "features.mycommand.enabled", // Conditional registration (optional)
    multiproviderClass = Object.class, // Multi-provider class (advanced)
    priority = false                   // Load priority (default: false)
)
```

### value (Required)

The command name that will be executed. Must match the command name in `plugin.yml`.

```java
@IocBukkitCommandHandler("teleport")
public class TeleportCommand extends AbstractCmd {
    // Handles /teleport
}
```

### permission (Optional)

Specifies a permission node required to execute the command. The permission check is performed automatically before `executeCmd()` is called.

```java
@IocBukkitCommandHandler(value = "admin", permission = "myplugin.admin")
public class AdminCommand extends AbstractCmd {
    // Only players with myplugin.admin permission can execute this
}
```

If the sender lacks the permission, a `NoPermissionException` is thrown and handled by the `CommandExceptionHandler`.

### onlyPlayers (Optional)

When set to `true`, the command can only be executed by players. Console execution will be rejected automatically.

```java
@IocBukkitCommandHandler(value = "heal", onlyPlayers = true)
public class HealCommand extends AbstractCmd {
    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender; // Safe cast - already validated
        player.setHealth(20.0);
        player.sendMessage("You have been healed!");
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0;
    }
}
```

**Without `onlyPlayers`**, you would need to manually check:
```java
if (!(sender instanceof Player)) {
    sender.sendMessage("This command can only be used by players!");
    return true;
}
```

### conditionalOnProperty (Optional)

Conditionally registers the command based on a configuration property. If the property is false or missing, the command is not registered.

```java
@IocBukkitCommandHandler(
    value = "debug",
    conditionalOnProperty = "debug-mode.enabled"
)
public class DebugCommand extends AbstractCmd {
    // Only registered if config.yml has: debug-mode.enabled: true
}
```

**config.yml:**
```yaml
debug-mode:
  enabled: true
```

This is useful for:
- Feature toggles
- Optional modules
- Debug commands
- Premium features

### priority (Optional)

When set to `true`, this command handler is loaded with higher priority. Rarely needed for commands, more commonly used with services.

```java
@IocBukkitCommandHandler(value = "core", priority = true)
public class CoreCommand extends AbstractCmd {
    // Loaded before other commands
}
```

## Command Method Signatures

### AbstractCmd Pattern

When extending `AbstractCmd`, you must implement two methods:

```java
@IocBukkitCommandHandler("example")
public class ExampleCommand extends AbstractCmd {

    public ExampleCommand(CommandExceptionHandler exceptionHandler,
                         TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        // Your command logic here
        return true; // Return true if command was handled successfully
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0; // Return minimum number of required arguments
    }
}
```

### executeCmd Method

```java
protected boolean executeCmd(CommandSender sender, String alias, String[] args)
```

**Parameters:**
- `sender`: The command sender (Player, ConsoleCommandSender, etc.)
- `alias`: The command alias used (if multiple aliases exist)
- `args`: Command arguments as a string array

**Returns:**
- `true` if the command executed successfully
- `false` if there was an error (optional - exceptions are preferred)

**Example with Arguments:**
```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    // /give <player> <item> <amount>
    Player target = Bukkit.getPlayer(args[0]);
    Material item = Material.valueOf(args[1].toUpperCase());
    int amount = Integer.parseInt(args[2]);

    target.getInventory().addItem(new ItemStack(item, amount));
    sender.sendMessage("Gave " + amount + " " + item + " to " + target.getName());

    return true;
}
```

### getMinimumArguments Method

```java
protected int getMinimumArguments(CommandSender sender, String[] args)
```

**Parameters:**
- `sender`: The command sender (can be used for permission-based argument requirements)
- `args`: The arguments array (can be used for dynamic validation)

**Returns:**
- The minimum number of arguments required

**Validation:**
If `args.length < getMinimumArguments()`, a `CommandException` is thrown automatically before `executeCmd()` is called.

**Examples:**

```java
// No arguments required
@Override
protected int getMinimumArguments(CommandSender sender, String[] args) {
    return 0;
}

// Exactly 1 argument required
@Override
protected int getMinimumArguments(CommandSender sender, String[] args) {
    return 1;
}

// Different requirements based on sender
@Override
protected int getMinimumArguments(CommandSender sender, String[] args) {
    // Console must specify player name, players default to self
    return sender instanceof Player ? 0 : 1;
}
```

## Dependency Injection in Command Handlers

Commands are beans like any other Tubing component, so they support full constructor injection.

### Injecting Services

```java
@IocBukkitCommandHandler("home")
public class HomeCommand extends AbstractCmd {

    private final HomeService homeService;
    private final MessageService messageService;

    // Dependencies automatically injected
    public HomeCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService,
                      HomeService homeService,
                      MessageService messageService) {
        super(exceptionHandler, permissionService);
        this.homeService = homeService;
        this.messageService = messageService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        String homeName = args.length > 0 ? args[0] : "default";

        Home home = homeService.getHome(player, homeName);
        if (home == null) {
            messageService.send(player, "home.not-found", homeName);
            return true;
        }

        player.teleport(home.getLocation());
        messageService.send(player, "home.teleported", homeName);
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0; // Home name is optional (defaults to "default")
    }
}
```

### Injecting Configuration

Combine constructor injection with configuration property injection:

```java
@IocBukkitCommandHandler("teleport")
public class TeleportCommand extends AbstractCmd {

    private final TeleportService teleportService;

    @ConfigProperty("teleport.cooldown")
    private int cooldownSeconds;

    @ConfigProperty("teleport.cost")
    private double teleportCost;

    public TeleportCommand(CommandExceptionHandler exceptionHandler,
                          TubingPermissionService permissionService,
                          TeleportService teleportService) {
        super(exceptionHandler, permissionService);
        this.teleportService = teleportService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (!teleportService.canAfford(player, teleportCost)) {
            player.sendMessage("You can't afford this teleport!");
            return true;
        }

        if (teleportService.hasCooldown(player, cooldownSeconds)) {
            player.sendMessage("You must wait before teleporting again!");
            return true;
        }

        teleportService.teleport(player, target.getLocation());
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 1;
    }
}
```

### Required Dependencies

All command handlers extending `AbstractCmd` **must** inject:
- `CommandExceptionHandler`: Handles exceptions thrown during command execution
- `TubingPermissionService`: Handles permission checks

These are passed to the `super()` constructor:

```java
public MyCommand(CommandExceptionHandler exceptionHandler,
                TubingPermissionService permissionService) {
    super(exceptionHandler, permissionService);
}
```

## Subcommands with RootCommand

For complex command structures with subcommands, extend `RootCommand` instead of `AbstractCmd`.

### Creating a Root Command

```java
@IocBukkitCommandHandler("admin")
public class AdminRootCommand extends RootCommand {

    public AdminRootCommand(CommandExceptionHandler commandExceptionHandler,
                           @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                           Messages messages,
                           TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }
}
```

**Key Points:**
- Extend `RootCommand` for subcommand support
- Inject `List<SubCommand>` with `@IocMulti(SubCommand.class)`
- Inject `Messages` for help message formatting
- The root command automatically manages subcommand routing

### Creating Subcommands

Implement the `SubCommand` interface and annotate with `@IocBukkitSubCommand`:

```java
@IocBean
@IocBukkitSubCommand(root = "admin", action = "reload")
public class AdminReloadSubCommand implements SubCommand {

    private final ConfigurationLoader configLoader;

    public AdminReloadSubCommand(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        configLoader.reload();
        sender.sendMessage("Configuration reloaded!");
        return true;
    }

    @Override
    public String getHelp() {
        return "&8/admin reload &7- Reload the configuration";
    }
}
```

### @IocBukkitSubCommand Annotation

```java
@IocBukkitSubCommand(
    root = "admin",                    // Parent command name (required)
    action = "reload",                 // Subcommand name (required)
    permission = "myplugin.admin.reload", // Permission (optional)
    onlyPlayers = false,               // Players only (default: false)
    conditionalOnProperty = "",        // Conditional registration (optional)
    priority = false                   // Load priority (default: false)
)
```

**Attributes:**
- `root`: The name of the root command (must match `@IocBukkitCommandHandler` value)
- `action`: The subcommand name (e.g., `/admin reload` → action = "reload")
- Other attributes work the same as `@IocBukkitCommandHandler`

### Complete Subcommand Example

```java
// Root command
@IocBukkitCommandHandler("player")
public class PlayerRootCommand extends RootCommand {

    public PlayerRootCommand(CommandExceptionHandler commandExceptionHandler,
                            @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                            Messages messages,
                            TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }
}

// Subcommand 1: /player heal <player>
@IocBean
@IocBukkitSubCommand(
    root = "player",
    action = "heal",
    permission = "myplugin.player.heal"
)
public class PlayerHealSubCommand implements SubCommand {

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /player heal <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found!");
            return true;
        }

        target.setHealth(20.0);
        sender.sendMessage("Healed " + target.getName());
        return true;
    }

    @Override
    public String getHelp() {
        return "&8/player heal <player> &7- Heal a player to full health";
    }
}

// Subcommand 2: /player feed <player>
@IocBean
@IocBukkitSubCommand(
    root = "player",
    action = "feed",
    permission = "myplugin.player.feed"
)
public class PlayerFeedSubCommand implements SubCommand {

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /player feed <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found!");
            return true;
        }

        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        sender.sendMessage("Fed " + target.getName());
        return true;
    }

    @Override
    public String getHelp() {
        return "&8/player feed <player> &7- Feed a player to full hunger";
    }
}
```

**Usage:**
```
/player              → Shows help with all subcommands
/player help         → Shows help with all subcommands
/player heal Steve   → Executes heal subcommand
/player feed Steve   → Executes feed subcommand
```

### Subcommand Help

The `RootCommand` automatically provides help functionality:

```java
@Override
protected void onHelp(CommandSender sender) {
    messages.send(sender, "&2" + getRootId() + " help");
    subCommands.forEach(subCommand ->
        messages.send(sender, subCommand.getHelp())
    );
}
```

You can override `onHelp()` to customize the help display:

```java
@IocBukkitCommandHandler("admin")
public class AdminRootCommand extends RootCommand {

    public AdminRootCommand(CommandExceptionHandler commandExceptionHandler,
                           @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                           Messages messages,
                           TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }

    @Override
    protected void onHelp(CommandSender sender) {
        messages.send(sender, "&6==== Admin Commands ====");
        subCommands.forEach(subCommand ->
            messages.send(sender, subCommand.getHelp())
        );
        messages.send(sender, "&6========================");
    }
}
```

### Zero Arguments Handling

Override `onZeroArguments()` to customize behavior when no subcommand is provided:

```java
@Override
protected void onZeroArguments(CommandSender sender) {
    // Default behavior: show help
    onHelp(sender);

    // Alternative: show custom message
    sender.sendMessage("Please specify a subcommand! Use /admin help");

    // Alternative: execute default action
    sender.sendMessage("Admin panel opened!"); // Then open GUI, etc.
}
```

## Tab Completion

Implement tab completion by implementing `TabCompleter` in your command class.

### Basic Tab Completion

```java
@IocBukkitCommandHandler("teleport")
public class TeleportCommand extends AbstractCmd implements TabCompleter {

    public TeleportCommand(CommandExceptionHandler exceptionHandler,
                          TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /teleport <player>");
            return true;
        }

        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);

        if (target != null) {
            player.teleport(target.getLocation());
            player.sendMessage("Teleported to " + target.getName());
        }

        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            // Return list of online player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
```

### Multi-Argument Tab Completion

```java
@IocBukkitCommandHandler("give")
public class GiveCommand extends AbstractCmd implements TabCompleter {

    public GiveCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        // /give <player> <item> <amount>
        Player target = Bukkit.getPlayer(args[0]);
        Material item = Material.valueOf(args[1].toUpperCase());
        int amount = Integer.parseInt(args[2]);

        target.getInventory().addItem(new ItemStack(item, amount));
        sender.sendMessage("Gave " + amount + " " + item + " to " + target.getName());

        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 3;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            // First argument: player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second argument: material names
            return Arrays.stream(Material.values())
                .map(Material::name)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3) {
            // Third argument: amount suggestions
            return Arrays.asList("1", "16", "32", "64");
        }

        return Collections.emptyList();
    }
}
```

### RootCommand Tab Completion

`RootCommand` automatically provides tab completion for subcommands:

```java
@Override
public List<String> onTabComplete(CommandSender sender, Command command,
                                  String alias, String[] args) {
    if (args.length == 1) {
        // Returns all subcommand actions + "help"
        List<String> result = subCommands.stream()
            .map(s -> s.getClass().getAnnotation(IocBukkitSubCommand.class).action())
            .collect(Collectors.toList());
        result.add("help");
        return result;
    }
    return Collections.emptyList();
}
```

You can override this to add custom tab completion:

```java
@IocBukkitCommandHandler("admin")
public class AdminRootCommand extends RootCommand {

    public AdminRootCommand(CommandExceptionHandler commandExceptionHandler,
                           @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                           Messages messages,
                           TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            // Get subcommands from parent implementation
            List<String> completions = super.onTabComplete(sender, command, alias, args);

            // Filter based on permissions
            return completions.stream()
                .filter(action -> hasPermission(sender, action))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("teleport")) {
            // Custom completion for specific subcommand
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private boolean hasPermission(CommandSender sender, String action) {
        // Check if sender has permission for this subcommand
        return sender.hasPermission("myplugin.admin." + action);
    }
}
```

## Exception Handling

Tubing provides centralized exception handling for commands through the `CommandExceptionHandler` interface.

### Default Exception Handler

Tubing includes a `DefaultCommandExceptionHandler` that:
- Catches all exceptions thrown during command execution
- Looks up error messages from configuration
- Sends formatted messages to the command sender

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultCommandExceptionHandler implements CommandExceptionHandler {

    private final ConfigurationLoader configurationLoader;
    private final Messages messages;

    public DefaultCommandExceptionHandler(ConfigurationLoader configurationLoader,
                                         Messages messages) {
        this.configurationLoader = configurationLoader;
        this.messages = messages;
    }

    @Override
    public void handle(CommandSender sender, Throwable exception) {
        String message = configurationLoader
            .getConfigStringValue(exception.getMessage())
            .orElse(exception.getMessage());
        messages.send(sender, message);
    }
}
```

### Throwing Exceptions in Commands

Use `CommandException` to signal user errors:

```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    Player target = Bukkit.getPlayer(args[0]);

    if (target == null) {
        throw new CommandException("player.not-found");
    }

    if (!target.isOnline()) {
        throw new CommandException("player.not-online");
    }

    // Execute command logic
    return true;
}
```

**config.yml:**
```yaml
player:
  not-found: "&cPlayer not found!"
  not-online: "&cThat player is not online!"
```

### Custom Exception Handler

Create your own exception handler by implementing `CommandExceptionHandler`:

```java
@IocBean
public class CustomCommandExceptionHandler implements CommandExceptionHandler {

    private final Logger logger;
    private final Messages messages;

    public CustomCommandExceptionHandler(Logger logger, Messages messages) {
        this.logger = logger;
        this.messages = messages;
    }

    @Override
    public void handle(CommandSender sender, Throwable exception) {
        if (exception instanceof CommandException) {
            // User error - show message
            messages.sendError(sender, exception.getMessage());
        } else if (exception instanceof NoPermissionException) {
            // Permission error
            messages.sendError(sender, "&cYou don't have permission!");
        } else {
            // Unexpected error - log and notify
            logger.severe("Command error: " + exception.getMessage());
            exception.printStackTrace();
            messages.sendError(sender, "&cAn error occurred!");

            // Notify admins
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("myplugin.admin"))
                .forEach(p -> p.sendMessage("&c[Admin] Command error: " + exception.getMessage()));
        }
    }
}
```

**Note:** When you create a custom `CommandExceptionHandler` bean, it replaces the default handler (which has `@ConditionalOnMissingBean`).

## Command Permissions

Tubing provides multiple ways to handle permissions in commands.

### Annotation-Based Permissions

Use the `permission` attribute in `@IocBukkitCommandHandler`:

```java
@IocBukkitCommandHandler(
    value = "fly",
    permission = "myplugin.fly"
)
public class FlyCommand extends AbstractCmd {
    // Permission checked automatically before executeCmd()
}
```

### TubingPermissionService

For dynamic permission checks, use the injected `TubingPermissionService`:

```java
@IocBukkitCommandHandler("admin")
public class AdminCommand extends AbstractCmd {

    private final TubingPermissionService permissionService;

    public AdminCommand(CommandExceptionHandler exceptionHandler,
                       TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
        this.permissionService = permissionService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        // Check permission dynamically
        if (permissionService.has(sender, "myplugin.admin.advanced")) {
            sender.sendMessage("You have advanced admin access!");
        } else {
            sender.sendMessage("You have basic admin access!");
        }

        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0;
    }
}
```

### Subcommand Permissions

Each subcommand can have its own permission:

```java
@IocBean
@IocBukkitSubCommand(
    root = "admin",
    action = "reload",
    permission = "myplugin.admin.reload"
)
public class AdminReloadSubCommand implements SubCommand {
    // Permission checked before execution
}

@IocBean
@IocBukkitSubCommand(
    root = "admin",
    action = "debug",
    permission = "myplugin.admin.debug"
)
public class AdminDebugSubCommand implements SubCommand {
    // Different permission for this subcommand
}
```

### Permission-Based Argument Requirements

Vary minimum arguments based on permissions:

```java
@Override
protected int getMinimumArguments(CommandSender sender, String[] args) {
    // Admins can use /heal without arguments (heals self)
    // Non-admins must specify target: /heal <player>
    return permissionService.has(sender, "myplugin.admin") ? 0 : 1;
}
```

## Command Aliases

Command aliases are configured in `plugin.yml`, not in Java code:

```yaml
commands:
  teleport:
    description: Teleport to a player
    usage: /teleport <player>
    aliases: [tp, warp]
```

The same `CommandExecutor` handles all aliases. You can check which alias was used:

```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    if (alias.equalsIgnoreCase("tp")) {
        sender.sendMessage("You used the 'tp' alias!");
    } else {
        sender.sendMessage("You used the 'teleport' command!");
    }
    return true;
}
```

## Best Practices

### 1. Keep Command Classes Thin

Commands should be thin wrappers around services. Put business logic in service classes, not commands.

**Bad:**
```java
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand extends AbstractCmd {

    private final Database database;

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        String homeName = args[0];
        Location location = player.getLocation();

        // BAD: Business logic in command
        if (database.countHomes(player) >= 5) {
            player.sendMessage("You have too many homes!");
            return true;
        }

        if (homeName.length() > 16) {
            player.sendMessage("Home name too long!");
            return true;
        }

        database.saveHome(player, homeName, location);
        player.sendMessage("Home set!");
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 1;
    }
}
```

**Good:**
```java
@IocBean
public class HomeService {

    private final HomeRepository repository;
    private final MessageService messages;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    public HomeService(HomeRepository repository, MessageService messages) {
        this.repository = repository;
        this.messages = messages;
    }

    public void createHome(Player player, String homeName, Location location) {
        // Validation
        if (repository.countHomes(player) >= maxHomes) {
            throw new CommandException("homes.max-reached");
        }

        if (homeName.length() > 16) {
            throw new CommandException("homes.name-too-long");
        }

        // Business logic
        Home home = new Home(player.getUniqueId(), homeName, location);
        repository.save(home);

        messages.sendSuccess(player, "homes.created", homeName);
    }
}

@IocBukkitCommandHandler("sethome")
public class SetHomeCommand extends AbstractCmd {

    private final HomeService homeService;

    public SetHomeCommand(CommandExceptionHandler exceptionHandler,
                         TubingPermissionService permissionService,
                         HomeService homeService) {
        super(exceptionHandler, permissionService);
        this.homeService = homeService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        String homeName = args[0];

        // Thin wrapper - delegates to service
        homeService.createHome(player, homeName, player.getLocation());
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 1;
    }
}
```

### 2. Use Exceptions for Error Handling

Throw exceptions instead of returning false or sending error messages directly:

**Bad:**
```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    if (!validate(args)) {
        sender.sendMessage("Invalid arguments!");
        return false;
    }
    // ...
}
```

**Good:**
```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    if (!validate(args)) {
        throw new CommandException("command.invalid-arguments");
    }
    // ...
}
```

### 3. Validate Arguments Properly

Use `getMinimumArguments()` for simple validation, custom logic for complex validation:

```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    // getMinimumArguments() already ensures args.length >= 2

    // Validate argument types
    int amount;
    try {
        amount = Integer.parseInt(args[1]);
    } catch (NumberFormatException e) {
        throw new CommandException("command.invalid-number");
    }

    // Validate argument ranges
    if (amount < 1 || amount > 64) {
        throw new CommandException("command.amount-out-of-range");
    }

    // Execute command
    return true;
}

@Override
protected int getMinimumArguments(CommandSender sender, String[] args) {
    return 2;
}
```

### 4. Use Configuration for Messages

Store all user-facing messages in configuration files:

**config.yml or messages.yml:**
```yaml
commands:
  home:
    teleported: "&aYou have been teleported to {0}!"
    not-found: "&cHome '{0}' not found!"
    created: "&aHome '{0}' created!"
    deleted: "&aHome '{0}' deleted!"
  errors:
    player-only: "&cThis command can only be used by players!"
    no-permission: "&cYou don't have permission to use this command!"
    invalid-arguments: "&cInvalid arguments! Use: {0}"
```

**Command:**
```java
@Override
protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
    Player player = (Player) sender;
    String homeName = args[0];

    Home home = homeService.getHome(player, homeName);
    if (home == null) {
        messages.send(player, "commands.home.not-found", homeName);
        return true;
    }

    player.teleport(home.getLocation());
    messages.send(player, "commands.home.teleported", homeName);
    return true;
}
```

### 5. Organize Commands by Feature

Group related commands using subcommands:

```
commands/
├── admin/
│   ├── AdminRootCommand.java
│   ├── AdminReloadSubCommand.java
│   ├── AdminDebugSubCommand.java
│   └── AdminMigrateSubCommand.java
├── home/
│   ├── HomeRootCommand.java
│   ├── HomeSetSubCommand.java
│   ├── HomeDeleteSubCommand.java
│   ├── HomeListSubCommand.java
│   └── HomeTeleportSubCommand.java
└── player/
    ├── PlayerRootCommand.java
    ├── PlayerHealSubCommand.java
    ├── PlayerFeedSubCommand.java
    └── PlayerTeleportSubCommand.java
```

### 6. Implement Tab Completion

Always implement tab completion for better user experience:

```java
@Override
public List<String> onTabComplete(CommandSender sender, Command command,
                                  String alias, String[] args) {
    if (args.length == 1) {
        return getCompletions(args[0], getAvailableOptions());
    }
    return Collections.emptyList();
}

private List<String> getCompletions(String input, List<String> options) {
    return options.stream()
        .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
        .collect(Collectors.toList());
}
```

### 7. Document Command Usage

Provide clear help messages and usage information:

```java
@Override
public String getHelp() {
    return "&8/admin reload &7- Reload plugin configuration\n" +
           "&8Usage: &f/admin reload [module]\n" +
           "&8Modules: &fconfig, messages, database";
}
```

### 8. Use Player-Only When Appropriate

Set `onlyPlayers = true` for commands that require a player context:

```java
@IocBukkitCommandHandler(
    value = "heal",
    onlyPlayers = true  // Requires player - can't heal console
)
public class HealCommand extends AbstractCmd {
    // Safe to cast sender to Player
}
```

But allow console execution when possible:

```java
@IocBukkitCommandHandler("broadcast")
public class BroadcastCommand extends AbstractCmd {
    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        // Works for both players and console
        String message = String.join(" ", args);
        Bukkit.broadcastMessage(message);
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 1;
    }
}
```

### 9. Test Commands

Commands are easy to test because of constructor injection:

```java
public class SetHomeCommandTest {

    private SetHomeCommand command;
    private HomeService mockHomeService;
    private CommandExceptionHandler mockExceptionHandler;
    private TubingPermissionService mockPermissionService;

    @Before
    public void setup() {
        mockHomeService = mock(HomeService.class);
        mockExceptionHandler = mock(CommandExceptionHandler.class);
        mockPermissionService = mock(TubingPermissionService.class);

        command = new SetHomeCommand(
            mockExceptionHandler,
            mockPermissionService,
            mockHomeService
        );
    }

    @Test
    public void testSetHome() {
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        command.executeCmd(player, "sethome", new String[]{"home1"});

        verify(mockHomeService).createHome(player, "home1", location);
    }
}
```

## Complete Example

Here's a complete example demonstrating all concepts:

```java
// Service layer
@IocBean
public class WarpService {

    private final WarpRepository repository;
    private final MessageService messages;
    private final PermissionService permissions;

    @ConfigProperty("warps.cooldown")
    private int cooldownSeconds;

    public WarpService(WarpRepository repository,
                      MessageService messages,
                      PermissionService permissions) {
        this.repository = repository;
        this.messages = messages;
        this.permissions = permissions;
    }

    public void teleportToWarp(Player player, String warpName) {
        Warp warp = repository.findByName(warpName);
        if (warp == null) {
            throw new CommandException("warps.not-found");
        }

        if (!permissions.has(player, "warps.use." + warpName)) {
            throw new NoPermissionException("warps.no-permission");
        }

        player.teleport(warp.getLocation());
        messages.sendSuccess(player, "warps.teleported", warpName);
    }

    public void createWarp(Player player, String warpName) {
        if (repository.exists(warpName)) {
            throw new CommandException("warps.already-exists");
        }

        Warp warp = new Warp(warpName, player.getLocation());
        repository.save(warp);
        messages.sendSuccess(player, "warps.created", warpName);
    }

    public List<String> listWarps() {
        return repository.findAll().stream()
            .map(Warp::getName)
            .collect(Collectors.toList());
    }
}

// Root command
@IocBukkitCommandHandler("warp")
public class WarpRootCommand extends RootCommand {

    public WarpRootCommand(CommandExceptionHandler commandExceptionHandler,
                          @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                          Messages messages,
                          TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }
}

// Subcommand: teleport to warp
@IocBean
@IocBukkitSubCommand(
    root = "warp",
    action = "tp",
    permission = "myplugin.warp.use",
    onlyPlayers = true
)
public class WarpTeleportSubCommand implements SubCommand {

    private final WarpService warpService;

    public WarpTeleportSubCommand(WarpService warpService) {
        this.warpService = warpService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new CommandException("warps.usage.tp");
        }

        Player player = (Player) sender;
        warpService.teleportToWarp(player, args[0]);
        return true;
    }

    @Override
    public String getHelp() {
        return "&8/warp tp <name> &7- Teleport to a warp";
    }
}

// Subcommand: create warp
@IocBean
@IocBukkitSubCommand(
    root = "warp",
    action = "create",
    permission = "myplugin.warp.admin",
    onlyPlayers = true
)
public class WarpCreateSubCommand implements SubCommand {

    private final WarpService warpService;

    public WarpCreateSubCommand(WarpService warpService) {
        this.warpService = warpService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new CommandException("warps.usage.create");
        }

        Player player = (Player) sender;
        warpService.createWarp(player, args[0]);
        return true;
    }

    @Override
    public String getHelp() {
        return "&8/warp create <name> &7- Create a new warp";
    }
}

// Subcommand: list warps
@IocBean
@IocBukkitSubCommand(
    root = "warp",
    action = "list",
    permission = "myplugin.warp.list"
)
public class WarpListSubCommand implements SubCommand {

    private final WarpService warpService;
    private final Messages messages;

    public WarpListSubCommand(WarpService warpService, Messages messages) {
        this.warpService = warpService;
        this.messages = messages;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        List<String> warps = warpService.listWarps();

        if (warps.isEmpty()) {
            messages.send(sender, "warps.none-available");
            return true;
        }

        messages.send(sender, "warps.list-header");
        warps.forEach(warp ->
            messages.send(sender, "warps.list-entry", warp)
        );

        return true;
    }

    @Override
    public String getHelp() {
        return "&8/warp list &7- List all available warps";
    }
}
```

**plugin.yml:**
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin

commands:
  warp:
    description: Warp management
    usage: /warp <tp|create|list>
    aliases: [warps]
```

**config.yml:**
```yaml
warps:
  cooldown: 5

messages:
  warps:
    teleported: "&aYou have been teleported to {0}!"
    not-found: "&cWarp '{0}' not found!"
    created: "&aWarp '{0}' created!"
    already-exists: "&cWarp '{0}' already exists!"
    no-permission: "&cYou don't have permission to use this warp!"
    none-available: "&eNo warps are currently available."
    list-header: "&6Available warps:"
    list-entry: "&8- &f{0}"
    usage:
      tp: "&cUsage: /warp tp <name>"
      create: "&cUsage: /warp create <name>"
```

## Next Steps

Now that you understand Bukkit commands:

- Learn about [Event Listeners](Event-Listeners.md) for handling Bukkit events
- Explore [GUI Framework](../gui/Overview.md) for creating inventory menus
- Read [Configuration Injection](../core/Configuration-Injection.md) for advanced config usage
- Check [Best Practices](../best-practices/Command-Design.md) for command design patterns

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first command
- [Dependency Injection](../core/Dependency-Injection.md) - Understanding DI in commands
- [Multi-Implementation](../core/Multi-Implementation.md) - Using @IocMulti for subcommands
- [Testing](../best-practices/Testing.md) - Testing command handlers
