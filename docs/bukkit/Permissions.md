# Bukkit Permissions

Tubing provides a flexible permission system through the `TubingPermissionService` interface. This abstraction layer allows you to use Bukkit's built-in permission system, integrate with third-party permission plugins like Vault, LuckPerms, or PermissionsEx, or implement completely custom permission logic.

## Overview

The Tubing permission system offers:

- **Abstraction Layer**: Single interface for all permission checks
- **Default Implementation**: Uses Bukkit's native permission system out of the box
- **Easy Override**: Replace the default with custom implementations via dependency injection
- **Automatic Command Integration**: Commands and subcommands use the service automatically
- **Vault Support**: Simple integration with Vault and other permission plugins
- **Testable**: Mock permission checks easily in unit tests

The permission system is used throughout Tubing's command framework, but you can also inject and use it directly in your services and listeners.

## TubingPermissionService Interface

The core of the permission system is the `TubingPermissionService` interface:

```java
package be.garagepoort.mcioc.tubingbukkit.permissions;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface TubingPermissionService {

    boolean has(Player player, String permission);

    boolean has(CommandSender sender, String permission);
}
```

### Methods

#### has(Player player, String permission)

Checks if a player has a specific permission.

**Parameters:**
- `player` - The player to check
- `permission` - The permission node (e.g., `"myplugin.admin.ban"`)

**Returns:** `true` if the player has the permission, `false` otherwise

**Example:**
```java
@IocBean
public class ModerationService {
    private final TubingPermissionService permissionService;

    public ModerationService(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void banPlayer(Player moderator, Player target) {
        if (!permissionService.has(moderator, "myplugin.admin.ban")) {
            moderator.sendMessage("§cYou don't have permission to ban players!");
            return;
        }

        // Ban logic
        target.kickPlayer("§cYou have been banned");
        moderator.sendMessage("§aBanned " + target.getName());
    }
}
```

#### has(CommandSender sender, String permission)

Checks if a command sender (player or console) has a specific permission.

**Parameters:**
- `sender` - The command sender to check
- `permission` - The permission node

**Returns:** `true` if the sender has the permission, `false` otherwise

**Example:**
```java
@IocBukkitListener
public class BlockBreakListener implements Listener {
    private final TubingPermissionService permissionService;

    public BlockBreakListener(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Protected blocks require special permission
        if (isProtectedBlock(event.getBlock())) {
            if (!permissionService.has(player, "myplugin.break.protected")) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot break protected blocks!");
            }
        }
    }
}
```

## Default Permission Implementation

Tubing provides a default implementation that uses Bukkit's native permission system:

```java
package be.garagepoort.mcioc.tubingbukkit.permissions;

import be.garagepoort.mcioc.ConditionalOnMissingBean;
import be.garagepoort.mcioc.IocBean;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
@ConditionalOnMissingBean
public class DefaultTubingPermissionService implements TubingPermissionService {

    @Override
    public boolean has(Player player, String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }
}
```

### Key Features

- **`@ConditionalOnMissingBean`**: This bean is only registered if no other `TubingPermissionService` implementation exists
- **Native Integration**: Uses Bukkit's `hasPermission()` methods directly
- **Works Out of the Box**: No configuration needed for basic permission checks
- **Compatible**: Works with any permission plugin that integrates with Bukkit's permission API (LuckPerms, PermissionsEx, GroupManager, etc.)

The default implementation means you don't need to do anything special - just use the service and it works with whatever permission plugin your server uses.

## Creating Custom Permission Providers

You can replace the default permission service with your own implementation for custom logic, external integrations, or advanced permission checks.

### Basic Custom Provider

To override the default, create a bean that implements `TubingPermissionService`:

```java
package com.example.myplugin.permissions;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
public class CustomPermissionService implements TubingPermissionService {

    @Override
    public boolean has(Player player, String permission) {
        // Custom permission logic for players
        if (permission.startsWith("myplugin.vip.")) {
            return checkVipPermission(player, permission);
        }
        return player.hasPermission(permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        // Console always has all permissions
        if (!(sender instanceof Player)) {
            return true;
        }
        return has((Player) sender, permission);
    }

    private boolean checkVipPermission(Player player, String permission) {
        // Custom VIP permission logic
        return player.hasPermission(permission) || isVipPlayer(player);
    }

    private boolean isVipPlayer(Player player) {
        // Your VIP check logic
        return false;
    }
}
```

**Important:** Because the default implementation has `@ConditionalOnMissingBean`, your custom implementation will automatically replace it. You don't need any special annotations.

### Vault Integration

Here's how to integrate with Vault for advanced permission features:

```java
package com.example.myplugin.permissions;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

@IocBean
public class VaultPermissionService implements TubingPermissionService {

    private Permission vaultPermission;

    public VaultPermissionService() {
        setupVault();
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Bukkit.getLogger().warning("Vault not found, falling back to basic permissions");
            return;
        }

        RegisteredServiceProvider<Permission> rsp =
            Bukkit.getServicesManager().getRegistration(Permission.class);

        if (rsp == null) {
            Bukkit.getLogger().warning("No permission provider found in Vault");
            return;
        }

        vaultPermission = rsp.getProvider();
        Bukkit.getLogger().info("Vault permissions enabled");
    }

    @Override
    public boolean has(Player player, String permission) {
        if (vaultPermission != null) {
            return vaultPermission.has(player, permission);
        }
        // Fallback to Bukkit's native system
        return player.hasPermission(permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            // Console has all permissions
            return true;
        }

        Player player = (Player) sender;
        if (vaultPermission != null) {
            return vaultPermission.has(player, permission);
        }
        return sender.hasPermission(permission);
    }

    // Additional Vault-specific methods
    public boolean playerInGroup(Player player, String group) {
        if (vaultPermission != null) {
            return vaultPermission.playerInGroup(player, group);
        }
        return false;
    }

    public String getPrimaryGroup(Player player) {
        if (vaultPermission != null) {
            return vaultPermission.getPrimaryGroup(player);
        }
        return null;
    }
}
```

**Add Vault dependency to your pom.xml:**
```xml
<dependency>
    <groupId>com.github.MilkBowl</groupId>
    <artifactId>VaultAPI</artifactId>
    <version>1.7</version>
    <scope>provided</scope>
</dependency>
```

### Database-Based Permissions

Create a permission service that checks against a database:

```java
package com.example.myplugin.permissions;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import com.example.myplugin.database.PermissionRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@IocBean
public class DatabasePermissionService implements TubingPermissionService {

    private final PermissionRepository repository;
    private final ConcurrentHashMap<UUID, Set<String>> cache;

    public DatabasePermissionService(PermissionRepository repository) {
        this.repository = repository;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public boolean has(Player player, String permission) {
        // Check cache first
        Set<String> permissions = cache.get(player.getUniqueId());
        if (permissions == null) {
            // Load from database and cache
            permissions = repository.getPlayerPermissions(player.getUniqueId());
            cache.put(player.getUniqueId(), permissions);
        }

        // Check exact match
        if (permissions.contains(permission)) {
            return true;
        }

        // Check wildcard permissions
        return checkWildcards(permissions, permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true; // Console has all permissions
        }
        return has((Player) sender, permission);
    }

    private boolean checkWildcards(Set<String> permissions, String permission) {
        // Check for wildcards like "myplugin.admin.*"
        String[] parts = permission.split("\\.");
        StringBuilder wildcard = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) wildcard.append(".");
            wildcard.append(parts[i]);

            if (permissions.contains(wildcard + ".*")) {
                return true;
            }
        }

        return permissions.contains("*");
    }

    public void clearCache(Player player) {
        cache.remove(player.getUniqueId());
    }

    public void clearCache() {
        cache.clear();
    }
}
```

### Conditional Permission Provider

Use configuration to determine which permission provider to load:

```java
package com.example.myplugin.permissions;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBean
public class ConfigurablePermissionService implements TubingPermissionService {

    @ConfigProperty("permissions.provider")
    private String provider; // "default", "vault", "database"

    private final TubingPermissionService delegate;

    public ConfigurablePermissionService(
            VaultPermissionService vaultService,
            DatabasePermissionService databaseService) {

        switch (provider.toLowerCase()) {
            case "vault":
                this.delegate = vaultService;
                Bukkit.getLogger().info("Using Vault permission provider");
                break;
            case "database":
                this.delegate = databaseService;
                Bukkit.getLogger().info("Using database permission provider");
                break;
            default:
                this.delegate = new DefaultPermissionService();
                Bukkit.getLogger().info("Using default Bukkit permission provider");
                break;
        }
    }

    @Override
    public boolean has(Player player, String permission) {
        return delegate.has(player, permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return delegate.has(sender, permission);
    }

    private static class DefaultPermissionService implements TubingPermissionService {
        @Override
        public boolean has(Player player, String permission) {
            return player.hasPermission(permission);
        }

        @Override
        public boolean has(CommandSender sender, String permission) {
            return sender.hasPermission(permission);
        }
    }
}
```

**config.yml:**
```yaml
permissions:
  provider: vault  # Options: default, vault, database
```

## Permission Checking Patterns

### Pattern 1: Automatic Command Permission Checks

The simplest way to use permissions is through command annotations:

```java
@IocBukkitCommandHandler(
    value = "admin",
    permission = "myplugin.admin"
)
public class AdminCommand extends AbstractCmd {
    // Permission checked automatically before executeCmd()
}
```

For subcommands:

```java
@IocBukkitSubCommand(
    root = "admin",
    action = "ban",
    permission = "myplugin.admin.ban"
)
public class BanSubCommand implements SubCommand {
    // Permission checked automatically before executeCmd()
}
```

**See:** [Commands](Commands.md) and [Subcommands](Subcommands.md) for more details.

### Pattern 2: Manual Permission Checks in Commands

Check permissions manually inside command logic:

```java
@IocBukkitCommandHandler("heal")
public class HealCommand extends AbstractCmd {

    private final TubingPermissionService permissionService;

    public HealCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
        this.permissionService = permissionService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command");
            return false;
        }

        Player player = (Player) sender;

        // Heal self if no args
        if (args.length == 0) {
            player.setHealth(20.0);
            player.sendMessage("§aYou have been healed!");
            return true;
        }

        // Heal other player - requires additional permission
        if (!permissionService.has(sender, "myplugin.heal.others")) {
            sender.sendMessage("§cYou don't have permission to heal other players!");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[0]);
            return false;
        }

        target.setHealth(20.0);
        target.sendMessage("§aYou have been healed by " + sender.getName());
        sender.sendMessage("§aHealed " + target.getName());
        return true;
    }

    @Override
    protected int getMinimumArguments(CommandSender sender, String[] args) {
        return 0;
    }
}
```

### Pattern 3: Service-Level Permission Checks

Inject the permission service into your services and check permissions there:

```java
@IocBean
public class TeleportService {

    private final TubingPermissionService permissionService;

    public TeleportService(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void teleportPlayer(Player player, Location location) {
        if (!permissionService.has(player, "myplugin.teleport")) {
            player.sendMessage("§cYou don't have permission to teleport!");
            return;
        }

        player.teleport(location);
        player.sendMessage("§aTeleported!");
    }

    public void teleportToPlayer(Player player, Player target) {
        if (!permissionService.has(player, "myplugin.teleport.player")) {
            player.sendMessage("§cYou don't have permission to teleport to players!");
            return;
        }

        player.teleport(target.getLocation());
        player.sendMessage("§aTeleported to " + target.getName());
    }

    public boolean canBypassDelay(Player player) {
        return permissionService.has(player, "myplugin.teleport.nodelay");
    }
}
```

### Pattern 4: Event-Based Permission Checks

Use permissions in event listeners:

```java
@IocBukkitListener
public class InteractionListener implements Listener {

    private final TubingPermissionService permissionService;

    public InteractionListener(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        // Prevent non-admins from using certain blocks
        if (block.getType() == Material.COMMAND_BLOCK) {
            if (!permissionService.has(player, "myplugin.admin.commandblock")) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot use command blocks!");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Restricted block types
        if (isRestrictedBlock(block.getType())) {
            if (!permissionService.has(player, "myplugin.build.restricted")) {
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to place this block!");
            }
        }
    }

    private boolean isRestrictedBlock(Material material) {
        return material == Material.TNT ||
               material == Material.LAVA_BUCKET ||
               material == Material.BEDROCK;
    }
}
```

### Pattern 5: Conditional Features Based on Permissions

Enable/disable features based on permissions:

```java
@IocBean
public class PlayerManager {

    private final TubingPermissionService permissionService;

    public PlayerManager(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public int getMaxHomes(Player player) {
        // Check permissions in order of priority
        if (permissionService.has(player, "myplugin.homes.unlimited")) {
            return Integer.MAX_VALUE;
        }
        if (permissionService.has(player, "myplugin.homes.vip")) {
            return 10;
        }
        if (permissionService.has(player, "myplugin.homes.premium")) {
            return 5;
        }
        return 1; // Default
    }

    public boolean canUseColorCodes(Player player) {
        return permissionService.has(player, "myplugin.chat.colors");
    }

    public boolean canFly(Player player) {
        return permissionService.has(player, "myplugin.fly");
    }

    public int getCooldownSeconds(Player player, String action) {
        // VIPs have reduced cooldowns
        if (permissionService.has(player, "myplugin.vip.nocooldown")) {
            return 0;
        }
        if (permissionService.has(player, "myplugin.vip.reducedcooldown")) {
            return 30;
        }
        return 120; // Default cooldown
    }
}
```

### Pattern 6: Permission-Based GUI Access

Control GUI menu access with permissions:

```java
@IocBean
public class MenuService {

    private final TubingPermissionService permissionService;

    public MenuService(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void openMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "§6Main Menu");

        // Always visible
        menu.setItem(10, createItem(Material.BOOK, "§eHelp"));

        // Permission-based items
        if (permissionService.has(player, "myplugin.shop")) {
            menu.setItem(12, createItem(Material.EMERALD, "§aShop"));
        }

        if (permissionService.has(player, "myplugin.warps")) {
            menu.setItem(14, createItem(Material.COMPASS, "§bWarps"));
        }

        if (permissionService.has(player, "myplugin.admin")) {
            menu.setItem(16, createItem(Material.REDSTONE, "§cAdmin Panel"));
        }

        player.openInventory(menu);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
```

## Integration with Vault and Permission Plugins

### LuckPerms Direct Integration

If you want to use LuckPerms API directly without Vault:

```java
package com.example.myplugin.permissions;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.permissions.TubingPermissionService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

@IocBean
public class LuckPermsPermissionService implements TubingPermissionService {

    private final LuckPerms luckPerms;

    public LuckPermsPermissionService() {
        RegisteredServiceProvider<LuckPerms> provider =
            Bukkit.getServicesManager().getRegistration(LuckPerms.class);

        if (provider == null) {
            throw new IllegalStateException("LuckPerms not found!");
        }

        this.luckPerms = provider.getProvider();
    }

    @Override
    public boolean has(Player player, String permission) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return false;
        }

        return user.getCachedData()
            .getPermissionData()
            .checkPermission(permission)
            .asBoolean();
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return has((Player) sender, permission);
    }

    // Additional LuckPerms-specific functionality
    public String getPrimaryGroup(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return null;
        }
        return user.getPrimaryGroup();
    }
}
```

**Add LuckPerms dependency:**
```xml
<dependency>
    <groupId>net.luckperms</groupId>
    <artifactId>api</artifactId>
    <version>5.4</version>
    <scope>provided</scope>
</dependency>
```

### Soft-Depend Configuration

If your permission provider depends on external plugins, configure soft dependencies in `plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: 1.20

# Soft dependencies - plugin works without these but can integrate with them
softdepend: [Vault, LuckPerms]

commands:
  myplugin:
    description: Main plugin command
    usage: /myplugin <args>

permissions:
  myplugin.admin:
    description: Admin access
    default: op
  myplugin.admin.*:
    description: All admin permissions
    children:
      myplugin.admin.ban: true
      myplugin.admin.kick: true
      myplugin.admin.mute: true
```

## NoPermissionException

When permission checks fail, Tubing throws a `NoPermissionException`:

```java
package be.garagepoort.mcioc.tubingbukkit.permissions;

import be.garagepoort.mcioc.tubingbukkit.exceptions.TubingBukkitException;

public class NoPermissionException extends TubingBukkitException {
    public NoPermissionException(String message) {
        super(message);
    }
}
```

This exception is automatically caught by the `CommandExceptionHandler` and can be customized:

```java
@IocBean
public class CustomCommandExceptionHandler implements CommandExceptionHandler {

    @Override
    public void handle(CommandSender sender, Throwable throwable) {
        if (throwable instanceof NoPermissionException) {
            sender.sendMessage("§c§l⚠ §cInsufficient Permissions");
            sender.sendMessage("§7" + throwable.getMessage());

            // Log for admins
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Bukkit.getLogger().warning(
                    player.getName() + " attempted action without permission: " +
                    throwable.getMessage()
                );
            }
            return;
        }

        // Handle other exceptions
        sender.sendMessage("§cAn error occurred: " + throwable.getMessage());
    }
}
```

## Best Practices

### 1. Use Hierarchical Permission Nodes

Structure your permissions in a logical hierarchy:

```yaml
myplugin.*
  ├─ myplugin.admin.*
  │   ├─ myplugin.admin.reload
  │   ├─ myplugin.admin.moderation.*
  │   │   ├─ myplugin.admin.moderation.kick
  │   │   ├─ myplugin.admin.moderation.ban
  │   │   └─ myplugin.admin.moderation.mute
  │   └─ myplugin.admin.config
  ├─ myplugin.user.*
  │   ├─ myplugin.user.homes
  │   ├─ myplugin.user.warps
  │   └─ myplugin.user.teleport
  └─ myplugin.vip.*
      ├─ myplugin.vip.fly
      └─ myplugin.vip.colors
```

This allows administrators to grant permissions at different levels of granularity.

### 2. Always Provide Fallback Behavior

Never let permission checks cause null pointer exceptions:

```java
// Good - handles null gracefully
public boolean hasPermission(Player player, String permission) {
    if (player == null || permission == null) {
        return false;
    }
    return permissionService.has(player, permission);
}

// Bad - can throw NullPointerException
public boolean hasPermission(Player player, String permission) {
    return permissionService.has(player, permission); // NPE if player is null
}
```

### 3. Document All Permissions in plugin.yml

Always document your permissions, even if you use an external permission plugin:

```yaml
permissions:
  myplugin.admin:
    description: Main admin permission
    default: op
    children:
      myplugin.admin.reload: true
      myplugin.admin.ban: true

  myplugin.admin.reload:
    description: Reload plugin configuration
    default: op

  myplugin.admin.ban:
    description: Ban players
    default: op

  myplugin.user:
    description: Basic user permissions
    default: true
    children:
      myplugin.user.homes: true
      myplugin.user.warps: true
```

### 4. Cache Permission Checks for Performance

If you check the same permission repeatedly, cache the result:

```java
@IocBean
public class PermissionCacheService {

    private final TubingPermissionService permissionService;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public PermissionCacheService(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean has(Player player, String permission) {
        Map<String, Boolean> playerCache = cache.get(player.getUniqueId());

        if (playerCache == null) {
            playerCache = new ConcurrentHashMap<>();
            cache.put(player.getUniqueId(), playerCache);
        }

        return playerCache.computeIfAbsent(permission,
            perm -> permissionService.has(player, perm));
    }

    public void clearCache(Player player) {
        cache.remove(player.getUniqueId());
    }

    public void clearCache() {
        cache.clear();
    }
}
```

Clear the cache when players join/leave or permissions are updated:

```java
@IocBukkitListener
public class PermissionCacheListener implements Listener {

    private final PermissionCacheService cacheService;

    public PermissionCacheListener(PermissionCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        cacheService.clearCache(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cacheService.clearCache(event.getPlayer());
    }
}
```

### 5. Use Permission-Based Feature Flags

Control feature availability with permissions rather than hard-coded checks:

```java
@IocBean
public class FeatureManager {

    private final TubingPermissionService permissionService;

    public FeatureManager(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean canUseFeature(Player player, String feature) {
        return permissionService.has(player, "myplugin.feature." + feature);
    }

    public List<String> getAvailableFeatures(Player player) {
        List<String> features = new ArrayList<>();

        if (permissionService.has(player, "myplugin.feature.homes")) {
            features.add("homes");
        }
        if (permissionService.has(player, "myplugin.feature.warps")) {
            features.add("warps");
        }
        if (permissionService.has(player, "myplugin.feature.teleport")) {
            features.add("teleport");
        }

        return features;
    }
}
```

### 6. Test with Mock Permission Services

Use mocks in unit tests to verify permission logic:

```java
@Test
public void testBanCommand_withoutPermission() {
    // Arrange
    TubingPermissionService mockPermissions = mock(TubingPermissionService.class);
    when(mockPermissions.has(any(Player.class), eq("myplugin.admin.ban")))
        .thenReturn(false);

    ModerationService service = new ModerationService(mockPermissions);
    Player moderator = mock(Player.class);
    Player target = mock(Player.class);

    // Act
    service.banPlayer(moderator, target);

    // Assert
    verify(moderator).sendMessage(contains("don't have permission"));
    verify(target, never()).kickPlayer(any());
}
```

### 7. Handle Console Gracefully

Always handle console senders properly:

```java
@Override
public boolean has(CommandSender sender, String permission) {
    // Console typically has all permissions
    if (!(sender instanceof Player)) {
        return true;
    }

    return has((Player) sender, permission);
}
```

Or deny console for player-only permissions:

```java
public boolean canTeleport(CommandSender sender) {
    if (!(sender instanceof Player)) {
        sender.sendMessage("§cOnly players can teleport");
        return false;
    }

    return permissionService.has(sender, "myplugin.teleport");
}
```

### 8. Use Descriptive Permission Messages

Provide clear messages when permission checks fail:

```java
if (!permissionService.has(player, "myplugin.admin.ban")) {
    player.sendMessage("§c§l⚠ Insufficient Permissions");
    player.sendMessage("§7Required permission: §emyplugin.admin.ban");
    player.sendMessage("§7Contact an administrator for access.");
    return;
}
```

## Summary

The Tubing permission system provides:

- **Simple Interface**: Two methods handle all permission checks
- **Default Implementation**: Works with Bukkit's native permissions out of the box
- **Easy Customization**: Replace with custom implementations via dependency injection
- **Command Integration**: Automatic permission checks for commands and subcommands
- **Vault Support**: Simple integration with Vault and other permission plugins
- **Testability**: Mock-friendly design for unit testing

By using `TubingPermissionService`, you get a clean abstraction layer that works with any permission plugin while keeping your code simple and testable.

## Next Steps

- **[Commands](Commands.md)** - Learn how permissions integrate with commands
- **[Subcommands](Subcommands.md)** - Per-subcommand permission control
- **[Conditional Beans](../core/Conditional-Beans.md)** - Use `@ConditionalOnMissingBean` for custom providers
- **[Dependency Injection](../core/Dependency-Injection.md)** - Understanding how services are injected

---

**See also:**
- [Bean Registration](../core/Bean-Registration.md) - How `@IocBean` works
- [Bukkit Setup](Bukkit-Setup.md) - Plugin initialization and lifecycle
- [Exception Handling](Commands.md#exception-handling) - Customizing `NoPermissionException` handling
