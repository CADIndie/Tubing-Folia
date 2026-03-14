# Bukkit Subcommands

Tubing provides a powerful subcommand system for building complex, hierarchical command structures. The `@IocBukkitSubCommand` annotation allows you to create modular, maintainable command handlers with automatic routing, permission checking, and tab completion.

## Overview

Instead of implementing all command logic in a single class, you can split functionality into individual subcommand classes. A parent command (root command) automatically discovers and routes to subcommands based on the first argument.

**Key benefits:**
- **Modularity**: Each subcommand is a separate, focused class
- **Automatic Routing**: Parent command handles dispatch logic
- **Dependency Injection**: Subcommands receive dependencies automatically
- **Permission Control**: Per-subcommand permissions with automatic validation
- **Tab Completion**: Built-in tab completion for subcommand names
- **Nested Structure**: Support for deeply nested command hierarchies

## Basic Subcommand Setup

### Step 1: Create the Root Command

The root command extends `RootCommand` and is annotated with `@IocBukkitCommandHandler`:

```java
package com.example.myplugin.commands;

import be.garagepoort.mcioc.IocMulti;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;
import be.garagepoort.mcioc.tubingbukkit.commands.RootCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.exceptions.CommandExceptionHandler;
import be.garagepoort.mcioc.tubingbukkit.messaging.Messages;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;

import java.util.List;

@IocBukkitCommandHandler("admin")
public class AdminCommand extends RootCommand {

    public AdminCommand(CommandExceptionHandler commandExceptionHandler,
                       @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                       Messages messages,
                       TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }
}
```

**Key points:**
- Extends `RootCommand` for automatic subcommand handling
- `@IocBukkitCommandHandler("admin")` defines the command name
- Constructor receives all available subcommands via `@IocMulti`
- `RootCommand` filters subcommands to only those matching this root

### Step 2: Create Subcommands

Each subcommand implements `SubCommand` and is annotated with `@IocBukkitSubCommand`:

```java
package com.example.myplugin.commands.admin;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import com.example.myplugin.services.PlayerDataService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBukkitSubCommand(
    root = "admin",
    action = "reload"
)
public class ReloadSubCommand implements SubCommand {

    private final PlayerDataService playerDataService;

    public ReloadSubCommand(PlayerDataService playerDataService) {
        this.playerDataService = playerDataService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        sender.sendMessage("§aReloading plugin data...");
        playerDataService.reload();
        sender.sendMessage("§aReload complete!");
        return true;
    }

    @Override
    public String getHelp() {
        return "§e/admin reload §7- Reload plugin configuration";
    }
}
```

**Key points:**
- `root = "admin"` matches the parent command name
- `action = "reload"` defines the subcommand name (executed as `/admin reload`)
- `executeCmd()` contains the command logic
- `getHelp()` provides the help text shown in `/admin help`
- Dependencies injected via constructor

### Step 3: Register Commands in plugin.yml

Add the root command to your `plugin.yml`:

```yaml
commands:
  admin:
    description: Administrative commands
    usage: /admin <reload|stats|clear>
    permission: myplugin.admin
```

**Note:** Only register the root command. Subcommands are discovered automatically.

## The @IocBukkitSubCommand Annotation

The `@IocBukkitSubCommand` annotation defines how a subcommand is registered and validated.

### Annotation Properties

```java
@IocBukkitSubCommand(
    root = "admin",                           // Required: parent command name
    action = "reload",                        // Required: subcommand name
    permission = "myplugin.admin.reload",     // Optional: permission node
    onlyPlayers = false,                      // Optional: console allowed (default: false)
    conditionalOnProperty = "features.admin", // Optional: config-based toggle
    priority = false,                         // Optional: bean priority (default: false)
    multiproviderClass = SubCommand.class     // Advanced: multi-provider class
)
public class ReloadSubCommand implements SubCommand { }
```

### Property Details

#### root (required)

The name of the parent command. Must match the `@IocBukkitCommandHandler` value of the root command:

```java
@IocBukkitCommandHandler("admin")
public class AdminCommand extends RootCommand { }

@IocBukkitSubCommand(root = "admin", action = "reload")
public class ReloadSubCommand implements SubCommand { }
```

#### action (required)

The subcommand name used in the command syntax:

```java
@IocBukkitSubCommand(root = "admin", action = "reload")
// Executed as: /admin reload
```

Action names are case-insensitive and matched automatically.

#### permission (optional)

Permission required to execute this subcommand. Checked automatically before `executeCmd()`:

```java
@IocBukkitSubCommand(
    root = "admin",
    action = "ban",
    permission = "myplugin.admin.ban"
)
public class BanSubCommand implements SubCommand { }
```

If the sender lacks permission, a `NoPermissionException` is thrown and handled by the `CommandExceptionHandler`.

#### onlyPlayers (optional)

If `true`, only players can execute this subcommand. Console execution throws a `TubingBukkitException`:

```java
@IocBukkitSubCommand(
    root = "admin",
    action = "teleport",
    onlyPlayers = true  // Console cannot execute
)
public class TeleportSubCommand implements SubCommand {
    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        Player player = (Player) sender;  // Safe cast
        // teleportation logic
        return true;
    }
}
```

#### conditionalOnProperty (optional)

Enable/disable the subcommand based on a configuration property:

```java
@IocBukkitSubCommand(
    root = "admin",
    action = "experimental",
    conditionalOnProperty = "features.experimental-commands"
)
public class ExperimentalSubCommand implements SubCommand { }
```

**config.yml:**
```yaml
features:
  experimental-commands: false  # Subcommand not registered
```

If the property is `false` or missing, the subcommand bean is not instantiated.

## Parent-Child Command Relationships

Root commands and subcommands form a parent-child relationship through the `root` attribute.

### How Routing Works

When a player executes `/admin reload`:

1. Bukkit routes `/admin` to the `AdminCommand` class
2. `RootCommand` receives `["reload"]` as args
3. `RootCommand` searches for a subcommand where `action = "reload"` and `root = "admin"`
4. `ReloadSubCommand` is found and invoked
5. Args are passed to `executeCmd()` (minus the action argument)

### Multiple Root Commands

You can have multiple root commands, each with their own subcommands:

```java
@IocBukkitCommandHandler("admin")
public class AdminCommand extends RootCommand { }

@IocBukkitCommandHandler("player")
public class PlayerCommand extends RootCommand { }

@IocBukkitSubCommand(root = "admin", action = "reload")
public class AdminReloadSubCommand implements SubCommand { }

@IocBukkitSubCommand(root = "player", action = "stats")
public class PlayerStatsSubCommand implements SubCommand { }
```

Subcommands are automatically associated with their root command based on the `root` attribute.

### Shared Subcommand Logic

Multiple subcommands can share services via dependency injection:

```java
@IocBean
public class PermissionValidator {
    public boolean canModifyPlayer(CommandSender sender, Player target) {
        // Shared validation logic
        return true;
    }
}

@IocBukkitSubCommand(root = "admin", action = "kick")
public class KickSubCommand implements SubCommand {
    private final PermissionValidator validator;

    public KickSubCommand(PermissionValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        // Use shared validator
        return true;
    }
}

@IocBukkitSubCommand(root = "admin", action = "ban")
public class BanSubCommand implements SubCommand {
    private final PermissionValidator validator;

    public BanSubCommand(PermissionValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        // Use shared validator
        return true;
    }
}
```

## Nested Subcommands

Tubing supports deeply nested command hierarchies by implementing custom routing logic.

### Approach 1: Manual Nested Routing

Create a subcommand that acts as a nested root:

```java
@IocBukkitSubCommand(root = "admin", action = "player")
public class PlayerManagementSubCommand implements SubCommand {

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /admin player <kick|ban|mute> <player>");
            return true;
        }

        String action = args[0];
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action.toLowerCase()) {
            case "kick":
                handleKick(sender, subArgs);
                break;
            case "ban":
                handleBan(sender, subArgs);
                break;
            case "mute":
                handleMute(sender, subArgs);
                break;
            default:
                sender.sendMessage("§cUnknown player action: " + action);
        }
        return true;
    }

    private void handleKick(CommandSender sender, String[] args) {
        // /admin player kick <player>
    }

    private void handleBan(CommandSender sender, String[] args) {
        // /admin player ban <player>
    }

    private void handleMute(CommandSender sender, String[] args) {
        // /admin player mute <player>
    }

    @Override
    public String getHelp() {
        return "§e/admin player <action> §7- Manage players";
    }
}
```

### Approach 2: Nested Root Command

Create a dedicated root command for nested structure:

```java
@IocBukkitSubCommand(root = "admin", action = "player")
public class AdminPlayerSubCommand implements SubCommand {
    private final NestedPlayerCommandRouter router;

    public AdminPlayerSubCommand(NestedPlayerCommandRouter router) {
        this.router = router;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        return router.route(sender, args);
    }

    @Override
    public String getHelp() {
        return "§e/admin player §7- Player management commands";
    }
}

@IocBean
public class NestedPlayerCommandRouter {
    private final Map<String, NestedPlayerAction> actions;

    public NestedPlayerCommandRouter(
        @IocMulti(NestedPlayerAction.class) List<NestedPlayerAction> actionList
    ) {
        this.actions = actionList.stream()
            .collect(Collectors.toMap(
                NestedPlayerAction::getAction,
                Function.identity()
            ));
    }

    public boolean route(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Available actions: " + String.join(", ", actions.keySet()));
            return true;
        }

        NestedPlayerAction action = actions.get(args[0].toLowerCase());
        if (action == null) {
            sender.sendMessage("§cUnknown action: " + args[0]);
            return false;
        }

        return action.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}

public interface NestedPlayerAction {
    String getAction();
    boolean execute(CommandSender sender, String[] args);
}

@IocBean
@IocMultiProvider(NestedPlayerAction.class)
public class KickAction implements NestedPlayerAction {
    @Override
    public String getAction() {
        return "kick";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // /admin player kick <player>
        return true;
    }
}
```

This approach provides better separation of concerns and allows each nested action to be independently developed and tested.

## Permissions for Subcommands

Tubing provides automatic permission checking at the subcommand level.

### Defining Permissions

Set permissions in the annotation:

```java
@IocBukkitSubCommand(
    root = "admin",
    action = "reload",
    permission = "myplugin.admin.reload"
)
public class ReloadSubCommand implements SubCommand { }

@IocBukkitSubCommand(
    root = "admin",
    action = "ban",
    permission = "myplugin.admin.ban"
)
public class BanSubCommand implements SubCommand { }
```

### Automatic Validation

Permission validation occurs before `executeCmd()` is called:

```java
public interface SubCommand {
    default void onCommand(CommandSender sender, String[] args,
                          TubingPermissionService permissionService) {
        validateOnlyPlayers(sender);
        validatePermission(sender, permissionService);  // Automatic check
        executeCmd(sender, args);
    }

    default void validatePermission(CommandSender sender,
                                   TubingPermissionService permissionService) {
        if (this.getClass().isAnnotationPresent(IocBukkitSubCommand.class)) {
            String permission = this.getClass()
                .getAnnotation(IocBukkitSubCommand.class)
                .permission();
            if (!permission.isEmpty() && !permissionService.has(sender, permission)) {
                throw new NoPermissionException("You don't have permission to execute this command");
            }
        }
    }

    boolean executeCmd(CommandSender sender, String[] args);
}
```

If permission is denied, a `NoPermissionException` is thrown and handled by the `CommandExceptionHandler`.

### Permission Hierarchies

Use permission hierarchy in your permission plugin:

**permissions.yml:**
```yaml
permissions:
  myplugin.admin.*:
    description: All admin commands
    children:
      myplugin.admin.reload: true
      myplugin.admin.ban: true
      myplugin.admin.kick: true
      myplugin.admin.mute: true

  myplugin.admin.moderation:
    description: Moderation commands only
    children:
      myplugin.admin.ban: true
      myplugin.admin.kick: true
      myplugin.admin.mute: true
```

### Custom Permission Service

Tubing uses `TubingPermissionService` for permission checks. You can provide a custom implementation:

```java
@IocBean
@ConditionalOnMissingBean
public class CustomPermissionService implements TubingPermissionService {
    @Override
    public boolean has(CommandSender sender, String permission) {
        // Custom permission logic (e.g., integrate with external system)
        return sender.hasPermission(permission);
    }
}
```

## Tab Completion with Subcommands

The `RootCommand` provides automatic tab completion for subcommand names.

### Built-in Tab Completion

Tab completion is implemented in `RootCommand`:

```java
@Override
public List<String> onTabComplete(CommandSender commandSender, Command command,
                                  String alias, String[] strings) {
    if (strings.length == 1) {
        // First argument: show all subcommand actions + "help"
        List<String> result = subCommands.stream()
            .map(s -> s.getClass().getAnnotation(IocBukkitSubCommand.class).action())
            .collect(Collectors.toList());
        result.add("help");
        return result;
    }
    return Collections.emptyList();
}
```

**Example:**

When a player types `/admin ` and presses TAB, they see:
```
reload    ban    kick    mute    help
```

### Custom Tab Completion

Override `onTabComplete()` in your root command for custom completion:

```java
@IocBukkitCommandHandler("admin")
public class AdminCommand extends RootCommand {

    private final PlayerDataService playerDataService;

    public AdminCommand(CommandExceptionHandler commandExceptionHandler,
                       @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                       Messages messages,
                       TubingPermissionService permissionService,
                       PlayerDataService playerDataService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
        this.playerDataService = playerDataService;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command,
                                     String alias, String[] args) {
        if (args.length == 1) {
            // First argument: subcommand names (with permission filtering)
            return subCommands.stream()
                .filter(s -> {
                    String permission = s.getClass()
                        .getAnnotation(IocBukkitSubCommand.class)
                        .permission();
                    return permission.isEmpty() || commandSender.hasPermission(permission);
                })
                .map(s -> s.getClass().getAnnotation(IocBukkitSubCommand.class).action())
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second argument: context-specific completion
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("ban") || subCommand.equals("kick")) {
                // Complete with online player names
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
```

### Permission-Based Tab Completion

Filter tab completion based on sender permissions:

```java
@Override
public List<String> onTabComplete(CommandSender sender, Command command,
                                  String alias, String[] args) {
    if (args.length == 1) {
        return subCommands.stream()
            .filter(s -> {
                String permission = s.getClass()
                    .getAnnotation(IocBukkitSubCommand.class)
                    .permission();
                return permission.isEmpty() || sender.hasPermission(permission);
            })
            .map(s -> s.getClass().getAnnotation(IocBukkitSubCommand.class).action())
            .collect(Collectors.toList());
    }
    return Collections.emptyList();
}
```

Players without permission for a subcommand will not see it in tab completion.

## Help System

`RootCommand` provides a built-in help system.

### Default Help Command

When a player executes `/admin help`, the `onHelp()` method is called:

```java
protected void onHelp(CommandSender sender) {
    messages.send(sender, "&2" + getRootId() + " help");
    subCommands.forEach(subCommand -> messages.send(sender, subCommand.getHelp()));
}
```

### Custom Help Implementation

Override `onHelp()` for custom help display:

```java
@IocBukkitCommandHandler("admin")
public class AdminCommand extends RootCommand {

    public AdminCommand(CommandExceptionHandler commandExceptionHandler,
                       @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                       Messages messages,
                       TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }

    @Override
    protected void onHelp(CommandSender sender) {
        sender.sendMessage("§8§m----§r §6Admin Commands §8§m----§r");

        subCommands.stream()
            .filter(s -> {
                String permission = s.getClass()
                    .getAnnotation(IocBukkitSubCommand.class)
                    .permission();
                return permission.isEmpty() || sender.hasPermission(permission);
            })
            .forEach(subCommand -> sender.sendMessage(subCommand.getHelp()));

        sender.sendMessage("§8§m----------------------§r");
    }
}
```

### Zero Arguments Behavior

By default, when a player executes `/admin` with no arguments, `onHelp()` is called:

```java
protected void onZeroArguments(CommandSender sender) {
    onHelp(sender);
}
```

Override this to provide custom zero-argument behavior:

```java
@Override
protected void onZeroArguments(CommandSender sender) {
    sender.sendMessage("§cUsage: /admin <subcommand>");
    sender.sendMessage("§7Use §e/admin help §7for a list of commands");
}
```

### Per-Subcommand Help

Each subcommand provides its own help text via `getHelp()`:

```java
@IocBukkitSubCommand(root = "admin", action = "reload")
public class ReloadSubCommand implements SubCommand {
    @Override
    public String getHelp() {
        return "§e/admin reload §7- Reload plugin configuration";
    }
}

@IocBukkitSubCommand(root = "admin", action = "ban")
public class BanSubCommand implements SubCommand {
    @Override
    public String getHelp() {
        return "§e/admin ban <player> [reason] §7- Ban a player";
    }
}
```

Help text is displayed in `/admin help` and can include usage syntax, descriptions, and examples.

## Complete Example

Here's a full example demonstrating all subcommand features:

### Root Command

```java
package com.example.myplugin.commands;

import be.garagepoort.mcioc.IocMulti;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;
import be.garagepoort.mcioc.tubingbukkit.commands.RootCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.exceptions.CommandExceptionHandler;
import be.garagepoort.mcioc.tubingbukkit.messaging.Messages;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@IocBukkitCommandHandler("stafftools")
public class StaffToolsCommand extends RootCommand {

    public StaffToolsCommand(CommandExceptionHandler commandExceptionHandler,
                            @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                            Messages messages,
                            TubingPermissionService permissionService) {
        super(commandExceptionHandler, subCommands, messages, permissionService);
    }

    @Override
    protected void onHelp(CommandSender sender) {
        sender.sendMessage("§8§m----------§r §6§lStaff Tools§r §8§m----------§r");

        subCommands.stream()
            .filter(s -> {
                String permission = s.getClass()
                    .getAnnotation(IocBukkitSubCommand.class)
                    .permission();
                return permission.isEmpty() || sender.hasPermission(permission);
            })
            .forEach(subCommand -> sender.sendMessage("  " + subCommand.getHelp()));

        sender.sendMessage("§8§m--------------------------------§r");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                     String alias, String[] args) {
        if (args.length == 1) {
            // First arg: subcommand names (filtered by permission)
            return subCommands.stream()
                .filter(s -> {
                    String permission = s.getClass()
                        .getAnnotation(IocBukkitSubCommand.class)
                        .permission();
                    return permission.isEmpty() || sender.hasPermission(permission);
                })
                .map(s -> s.getClass().getAnnotation(IocBukkitSubCommand.class).action())
                .filter(action -> action.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second arg: context-specific completion
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("kick") || subCommand.equals("freeze")) {
                // Complete with online player names
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
```

### Subcommands

```java
package com.example.myplugin.commands.stafftools;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import com.example.myplugin.services.ModerationService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBukkitSubCommand(
    root = "stafftools",
    action = "freeze",
    permission = "stafftools.freeze",
    onlyPlayers = false
)
public class FreezeSubCommand implements SubCommand {

    private final ModerationService moderationService;

    public FreezeSubCommand(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /stafftools freeze <player>");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[0]);
            return false;
        }

        moderationService.toggleFreeze(target);

        if (moderationService.isFrozen(target)) {
            sender.sendMessage("§aFroze player: " + target.getName());
            target.sendMessage("§c§lYou have been frozen by a staff member!");
        } else {
            sender.sendMessage("§aUnfroze player: " + target.getName());
            target.sendMessage("§aYou have been unfrozen.");
        }

        return true;
    }

    @Override
    public String getHelp() {
        return "§e/stafftools freeze <player> §7- Toggle player freeze";
    }
}
```

```java
package com.example.myplugin.commands.stafftools;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import com.example.myplugin.services.ModerationService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBukkitSubCommand(
    root = "stafftools",
    action = "kick",
    permission = "stafftools.kick",
    onlyPlayers = false
)
public class KickSubCommand implements SubCommand {

    private final ModerationService moderationService;

    public KickSubCommand(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /stafftools kick <player> [reason]");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[0]);
            return false;
        }

        String reason = args.length > 1
            ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
            : "Kicked by a staff member";

        moderationService.kickPlayer(target, reason, sender.getName());
        sender.sendMessage("§aKicked " + target.getName() + " for: " + reason);

        return true;
    }

    @Override
    public String getHelp() {
        return "§e/stafftools kick <player> [reason] §7- Kick a player";
    }
}
```

```java
package com.example.myplugin.commands.stafftools;

import be.garagepoort.mcioc.configuration.ConfigProperty;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import org.bukkit.command.CommandSender;

@IocBukkitSubCommand(
    root = "stafftools",
    action = "reload",
    permission = "stafftools.reload",
    onlyPlayers = false
)
public class ReloadSubCommand implements SubCommand {

    @ConfigProperty("plugin.name")
    private String pluginName;

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        sender.sendMessage("§aReloading " + pluginName + "...");

        // Reload logic here

        sender.sendMessage("§aReload complete!");
        return true;
    }

    @Override
    public String getHelp() {
        return "§e/stafftools reload §7- Reload plugin configuration";
    }
}
```

```java
package com.example.myplugin.commands.stafftools;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;
import be.garagepoort.mcioc.tubingbukkit.commands.SubCommand;
import com.example.myplugin.services.StaffChatService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBukkitSubCommand(
    root = "stafftools",
    action = "chat",
    permission = "stafftools.chat",
    onlyPlayers = true,
    conditionalOnProperty = "features.staff-chat"
)
public class StaffChatSubCommand implements SubCommand {

    private final StaffChatService staffChatService;

    public StaffChatSubCommand(StaffChatService staffChatService) {
        this.staffChatService = staffChatService;
    }

    @Override
    public boolean executeCmd(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        if (args.length == 0) {
            // Toggle staff chat mode
            staffChatService.toggleStaffChat(player);
            boolean enabled = staffChatService.isInStaffChat(player);
            player.sendMessage(enabled
                ? "§aStaff chat enabled"
                : "§cStaff chat disabled");
        } else {
            // Send a single staff chat message
            String message = String.join(" ", args);
            staffChatService.sendStaffMessage(player, message);
        }

        return true;
    }

    @Override
    public String getHelp() {
        return "§e/stafftools chat [message] §7- Toggle or send staff chat";
    }
}
```

### plugin.yml

```yaml
commands:
  stafftools:
    description: Staff management tools
    usage: /stafftools <subcommand>
    permission: stafftools.use
    aliases: [st]

permissions:
  stafftools.use:
    description: Access to staff tools commands
    default: op

  stafftools.*:
    description: All staff tools permissions
    children:
      stafftools.freeze: true
      stafftools.kick: true
      stafftools.reload: true
      stafftools.chat: true

  stafftools.freeze:
    description: Freeze players
    default: op

  stafftools.kick:
    description: Kick players
    default: op

  stafftools.reload:
    description: Reload the plugin
    default: op

  stafftools.chat:
    description: Use staff chat
    default: op
```

### config.yml

```yaml
features:
  staff-chat: true  # Enable staff chat subcommand

plugin:
  name: StaffTools
```

## Best Practices

### 1. One Responsibility Per Subcommand

Each subcommand should handle a single, well-defined action:

**Good:**
```java
@IocBukkitSubCommand(root = "admin", action = "reload")
public class ReloadSubCommand implements SubCommand { }

@IocBukkitSubCommand(root = "admin", action = "stats")
public class StatsSubCommand implements SubCommand { }
```

**Bad:**
```java
@IocBukkitSubCommand(root = "admin", action = "manage")
public class ManageEverythingSubCommand implements SubCommand {
    // Handles reload, stats, cleanup, etc. in one class
}
```

### 2. Use Descriptive Action Names

Choose clear, intuitive action names:

**Good:**
```java
@IocBukkitSubCommand(root = "admin", action = "reload")
@IocBukkitSubCommand(root = "admin", action = "cleardata")
@IocBukkitSubCommand(root = "player", action = "teleport")
```

**Bad:**
```java
@IocBukkitSubCommand(root = "admin", action = "r")
@IocBukkitSubCommand(root = "admin", action = "cd")
@IocBukkitSubCommand(root = "player", action = "tp")
```

Short aliases are fine for the root command, but subcommand actions should be clear.

### 3. Provide Helpful Help Text

Include usage syntax and descriptions:

```java
@Override
public String getHelp() {
    return "§e/admin ban <player> [duration] [reason] §7- Ban a player";
}
```

### 4. Validate Arguments Early

Check argument count and validity before processing:

```java
@Override
public boolean executeCmd(CommandSender sender, String[] args) {
    if (args.length < 2) {
        sender.sendMessage("§cUsage: /admin ban <player> <duration>");
        return false;
    }

    Player target = Bukkit.getPlayer(args[0]);
    if (target == null) {
        sender.sendMessage("§cPlayer not found: " + args[0]);
        return false;
    }

    // Proceed with command logic
    return true;
}
```

### 5. Use Permission Hierarchies

Structure permissions hierarchically:

```yaml
myplugin.admin.*
  ├─ myplugin.admin.reload
  ├─ myplugin.admin.moderation.*
  │   ├─ myplugin.admin.moderation.kick
  │   ├─ myplugin.admin.moderation.ban
  │   └─ myplugin.admin.moderation.mute
  └─ myplugin.admin.stats
```

This allows flexible permission assignment.

### 6. Share Services, Not Logic

Use dependency injection to share services between subcommands:

```java
@IocBean
public class ModerationService {
    public void kickPlayer(Player player, String reason) { }
    public void banPlayer(Player player, String reason) { }
}

@IocBukkitSubCommand(root = "admin", action = "kick")
public class KickSubCommand implements SubCommand {
    private final ModerationService moderationService;

    public KickSubCommand(ModerationService moderationService) {
        this.moderationService = moderationService;
    }
}

@IocBukkitSubCommand(root = "admin", action = "ban")
public class BanSubCommand implements SubCommand {
    private final ModerationService moderationService;

    public BanSubCommand(ModerationService moderationService) {
        this.moderationService = moderationService;
    }
}
```

### 7. Handle Exceptions Gracefully

Use the `CommandExceptionHandler` for consistent error handling:

```java
@Override
public boolean executeCmd(CommandSender sender, String[] args) {
    if (args.length < 1) {
        throw new CommandException("Player name required");
    }

    Player target = Bukkit.getPlayer(args[0]);
    if (target == null) {
        throw new CommandException("Player not found: " + args[0]);
    }

    // Command logic
    return true;
}
```

### 8. Test Subcommands Independently

Each subcommand is a separate bean, making unit testing straightforward:

```java
@Test
public void testFreezeCommand() {
    ModerationService mockService = mock(ModerationService.class);
    FreezeSubCommand command = new FreezeSubCommand(mockService);

    CommandSender sender = mock(CommandSender.class);
    boolean result = command.executeCmd(sender, new String[]{"TestPlayer"});

    assertTrue(result);
    verify(mockService).toggleFreeze(any(Player.class));
}
```

### 9. Document Conditional Subcommands

If using `conditionalOnProperty`, document it in the config:

```yaml
features:
  # Enable experimental commands (requires plugin reload)
  experimental-commands: false

  # Enable staff chat system
  staff-chat: true
```

### 10. Consider Tab Completion UX

Provide contextual tab completion for better user experience:

```java
@Override
public List<String> onTabComplete(CommandSender sender, Command command,
                                  String alias, String[] args) {
    if (args.length == 1) {
        // Show subcommands the sender can use
        return getAvailableSubcommands(sender);
    }

    if (args.length == 2) {
        // Context-specific completion
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "kick":
            case "ban":
                return getOnlinePlayerNames();
            case "teleport":
                return getWorldNames();
        }
    }

    return Collections.emptyList();
}
```

## Common Patterns

### Admin Command Hub

Create a centralized admin command with multiple subcommands:

```java
/admin reload      - Reload configuration
/admin stats       - Show server statistics
/admin maintenance - Toggle maintenance mode
/admin backup      - Create a backup
/admin clearcache  - Clear plugin cache
```

Each subcommand is a separate class with its own permissions and logic.

### Player Management Commands

Group player-related commands under a root:

```java
/player teleport <player>
/player inventory <player>
/player health <player> <amount>
/player gamemode <player> <mode>
```

### Moderation Commands

Create moderation command hierarchies:

```java
/mod kick <player> [reason]
/mod ban <player> [duration] [reason]
/mod mute <player> [duration] [reason]
/mod warn <player> <message>
/mod history <player>
```

### Configuration Commands

Commands for managing plugin configuration:

```java
/config reload
/config set <key> <value>
/config get <key>
/config reset <key>
```

## Summary

Tubing's subcommand system provides:

- **Automatic routing** from parent to child commands
- **Per-subcommand permissions** with automatic validation
- **Dependency injection** for subcommand beans
- **Built-in tab completion** for subcommand discovery
- **Flexible nesting** for complex command hierarchies
- **Configuration-based toggling** via `conditionalOnProperty`
- **Clean separation** of command logic into focused classes

By using `@IocBukkitSubCommand` and extending `RootCommand`, you can build maintainable, modular command structures with minimal boilerplate.

## Next Steps

- **[Commands](Commands.md)** - Learn about basic command handling
- **[Permissions](Permissions.md)** - Advanced permission management
- **[Messaging](Messaging.md)** - Message formatting and localization
- **[Dependency Injection](../core/Dependency-Injection.md)** - Master DI patterns

---

**See also:**
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - How subcommand beans are instantiated
- [Multi-Implementation](../core/Multi-Implementation.md) - Understanding `@IocMulti`
- [Conditional Beans](../core/Conditional-Beans.md) - Config-based bean registration
