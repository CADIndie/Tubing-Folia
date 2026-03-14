# Bukkit Setup

This guide covers setting up a Bukkit plugin with Tubing, including plugin lifecycle management, IoC container access, and platform-specific configuration.

## Overview

Tubing provides the `TubingBukkitPlugin` base class which extends Bukkit's `JavaPlugin` and automatically initializes the IoC container. By extending this class, you get:

- Automatic IoC container initialization
- Dependency injection for all beans
- Configuration management
- Platform-specific bean registration (listeners, commands, plugin messages)
- Plugin reload support
- Lifecycle hooks for initialization and cleanup

## Extending TubingBukkitPlugin

Instead of extending Bukkit's `JavaPlugin` directly, extend `TubingBukkitPlugin`:

```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled with Tubing!");
        // Your plugin initialization code here
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled");
        // Your plugin cleanup code here
    }
}
```

### What TubingBukkitPlugin Provides

The base class handles:

1. **IoC Container Initialization**: Creates and initializes the container before `enable()` is called
2. **Bean Scanning**: Automatically discovers all `@IocBean` annotated classes in your plugin's package
3. **Configuration Loading**: Loads configuration files before beans are created
4. **Platform Registration**: Registers commands, listeners, and plugin message handlers
5. **Static Access**: Provides `TubingBukkitPlugin.getPlugin()` for accessing the plugin instance
6. **Reload Support**: Built-in reload mechanism with proper cleanup

## Plugin Lifecycle

Understanding the plugin lifecycle helps you know when to perform specific initialization tasks.

### 1. beforeEnable()

Called before the IoC container is initialized. Use this for early setup that must happen before any beans are created.

```java
@Override
protected void beforeEnable() {
    // Early initialization before IoC container loads
    // Example: Setting up external library configurations
    getLogger().info("Preparing to load plugin...");
}
```

**When to use:**
- Initializing external libraries that beans might depend on
- Setting up logging or monitoring systems
- Registering custom bean annotations
- Preparing data directories

**Important:** At this point:
- No beans exist yet
- Configuration files are not loaded
- IoC container is not available

### 2. Container Initialization (Automatic)

After `beforeEnable()` returns, Tubing automatically:

1. Creates the IoC container
2. Scans your plugin's package for annotated classes
3. Loads all configuration files
4. Creates all beans in dependency order
5. Injects `@ConfigProperty` values
6. Registers platform-specific beans (listeners, commands)

This happens automatically - you don't call any methods.

**If configuration loading fails**, the plugin is disabled and an error is logged:

```java
try {
    iocContainer = initIocContainer();
    TubingBukkitBeanLoader.load(this);
} catch (ConfigurationException e) {
    this.getLogger().severe(e.getLocalizedMessage());
    this.getPluginLoader().disablePlugin(this);
    return;
}
```

### 3. enable()

Called after the container is fully initialized and all beans are ready.

```java
@Override
protected void enable() {
    // Container is fully initialized
    // All beans are created and ready
    // Configuration is loaded

    getLogger().info("MyPlugin enabled!");

    // Access beans if needed
    IocContainer container = getIocContainer();
    PlayerService playerService = container.get(PlayerService.class);

    // Perform post-initialization tasks
    playerService.loadAllPlayerData();
}
```

**When to use:**
- Final initialization after all beans are ready
- Starting background tasks or schedulers
- Performing one-time data migrations
- Logging startup information
- Accessing beans that need manual initialization

**Available at this point:**
- All beans are created and injected
- Configuration is loaded
- Commands and listeners are registered
- IoC container is fully accessible

### 4. disable()

Called when the plugin is disabled. Use this to clean up resources.

```java
@Override
protected void disable() {
    getLogger().info("MyPlugin disabled");

    // Clean up resources
    // Close database connections
    // Cancel tasks
    // Save data
}
```

**When to use:**
- Closing database connections
- Canceling Bukkit tasks
- Saving player data
- Releasing external resources
- Cleanup operations

**Note:** Bean lifecycle is not managed during disable. You're responsible for cleanup. The IoC container is still accessible if you need to retrieve beans for cleanup.

### 5. beforeReload() (Optional)

Called before a plugin reload. Override this to perform pre-reload cleanup.

```java
@Override
protected void beforeReload() {
    getLogger().info("Preparing to reload...");

    // Clean up state that should not persist across reload
    // Cancel tasks
    // Clear caches
}
```

### 6. reload() (Built-in)

The `reload()` method is built into `TubingBukkitPlugin` and handles:

1. Executing all `BeforeTubingReload` hooks
2. Calling your `beforeReload()` method
3. Reloading configuration files via `reloadConfig()`
4. Unregistering all event handlers
5. Unregistering all plugin message channels
6. Reinitializing the IoC container
7. Recreating all beans with new configuration

You can call it from a command:

```java
@IocBukkitCommandHandler("myreload")
public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TubingBukkitPlugin.getPlugin().reload();
        sender.sendMessage("Plugin reloaded!");
        return true;
    }
}
```

**The reload mechanism:**

```java
public void reload() {
    // 1. Execute BeforeTubingReload hooks
    List<BeforeTubingReload> beforeTubingReloads = iocContainer.getList(BeforeTubingReload.class);
    if (beforeTubingReloads != null) {
        beforeTubingReloads.forEach(onLoad -> onLoad.execute(this));
    }

    // 2. Call your beforeReload() hook
    beforeReload();

    // 3. Reload config files
    reloadConfig();

    // 4. Unregister event handlers and plugin channels
    HandlerList.unregisterAll(this);
    getServer().getMessenger().unregisterIncomingPluginChannel(this);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this);

    // 5. Reinitialize container
    try {
        iocContainer = initIocContainer();
        TubingBukkitBeanLoader.load(this);
    } catch (ConfigurationException e) {
        this.getLogger().severe(e.getLocalizedMessage());
        this.getPluginLoader().disablePlugin(this);
    }
}
```

### BeforeTubingReload Hooks

You can create beans that implement `BeforeTubingReload` to execute cleanup before reload:

```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.annotations.BeforeTubingReload;
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

@IocBean
public class DatabaseCleanupHook implements BeforeTubingReload {

    private final DatabaseService databaseService;

    public DatabaseCleanupHook(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        tubingPlugin.getLogger().info("Closing database connections before reload...");
        databaseService.closeConnections();
    }
}
```

All beans implementing `BeforeTubingReload` are automatically discovered and executed during reload.

## Accessing the IoC Container

The IoC container is accessible from your main plugin class after initialization.

### Getting the Container

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Use the container
        PlayerService service = container.get(PlayerService.class);
    }
}
```

### Static Access to Plugin Instance

`TubingBukkitPlugin` provides static access to the plugin instance:

```java
// From anywhere in your code
TubingBukkitPlugin plugin = TubingBukkitPlugin.getPlugin();
IocContainer container = plugin.getIocContainer();
```

**Use this sparingly** - prefer dependency injection through constructors. Static access is useful for:
- Integration with external libraries that don't support DI
- Bukkit runnable tasks
- Utility methods that need plugin access

### Container Methods

**Get a single bean:**
```java
PlayerService service = container.get(PlayerService.class);
```

**Get all implementations of an interface:**
```java
List<RewardHandler> handlers = container.getList(RewardHandler.class);
```

**Access ClassGraph scan results:**
```java
ScanResult scanResult = container.getReflections();
List<Class<?>> customClasses = scanResult
    .getClassesImplementing(MyInterface.class)
    .loadClasses();
```

**Manually register a bean:**
```java
ExternalService externalService = ExternalLibrary.createService();
container.registerBean(externalService);
```

See [IoC Container](../core/IoC-Container.md) for detailed container documentation.

## Plugin.yml Configuration

Your `plugin.yml` should reference your `TubingBukkitPlugin` subclass as the main class:

### Minimal Configuration

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: 1.20
```

### Full Configuration Example

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: 1.20
author: YourName
description: A plugin powered by Tubing IoC framework
website: https://example.com

# Commands (still recommended for descriptions/usage)
commands:
  mycommand:
    description: Main plugin command
    usage: /mycommand <args>
    permission: myplugin.command
    aliases: [mc, mycmd]

# Permissions
permissions:
  myplugin.admin:
    description: Administrative access
    default: op
  myplugin.use:
    description: Basic plugin usage
    default: true

# Plugin dependencies
depend: []
softdepend: [Vault, PlaceholderAPI]
loadbefore: []

# Other settings
prefix: MyPlugin
```

### Command Registration Notes

While Tubing's `@IocBukkitCommandHandler` annotation automatically registers command executors, you should still define commands in `plugin.yml` for:

1. **Command descriptions** - Shown in `/help`
2. **Usage information** - Shown when command fails
3. **Permission nodes** - Base permission for the command
4. **Aliases** - Alternative command names

Example:

```yaml
commands:
  home:
    description: Teleport to your home
    usage: /home [name]
    permission: myplugin.home
    aliases: [h]
```

Then use the annotation to wire it up:

```java
@IocBukkitCommandHandler("home")
public class HomeCommand implements CommandExecutor {
    // Command logic with automatic dependency injection
}
```

### API Version

The `api-version` field specifies the Bukkit API version your plugin targets:

- `1.13` - For Minecraft 1.13-1.15
- `1.16` - For Minecraft 1.16
- `1.17` - For Minecraft 1.17
- `1.18` - For Minecraft 1.18
- `1.19` - For Minecraft 1.19
- `1.20` - For Minecraft 1.20

Tubing is compatible with all these versions. Choose the version matching your target server.

## Maven Dependencies for Bukkit

Here's the complete Maven setup for a Bukkit plugin with Tubing.

### Repository

```xml
<repositories>
    <!-- Spigot repository -->
    <repository>
        <id>spigot-repo</id>
        <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
    </repository>

    <!-- Tubing repository -->
    <repository>
        <id>staffplusplus-repo</id>
        <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
    </repository>
</repositories>
```

### Dependencies

```xml
<dependencies>
    <!-- Spigot API (provided by server) -->
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>1.20.1-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>

    <!-- Tubing Bukkit (shaded into your plugin) -->
    <dependency>
        <groupId>be.garagepoort.mcioc</groupId>
        <artifactId>tubing-bukkit</artifactId>
        <version>7.5.6</version>
        <exclusions>
            <exclusion>
                <groupId>*</groupId>
                <artifactId>*</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

### Maven Shade Plugin (Required)

You **must** shade and relocate Tubing to avoid conflicts with other plugins:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <relocations>
                            <relocation>
                                <pattern>be.garagepoort.mcioc.</pattern>
                                <shadedPattern>com.example.myplugin.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Critical:** Replace `com.example.myplugin.tubing` with your own unique package path.

### What Tubing Bukkit Includes

The `tubing-bukkit` artifact includes:
- `tubing-core` - Core IoC container and configuration framework
- ClassGraph - For package scanning
- Commons IO - For file operations
- PlaceholderAPI integration (optional, provided scope)

Dependencies are shaded and relocated when you build your plugin.

### Optional: GUI Module

To use Tubing's GUI framework, add the GUI dependency:

```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit-gui</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

This includes Freemarker template engine for building dynamic GUIs.

## Platform Compatibility

Tubing Bukkit is designed for maximum compatibility across Minecraft versions.

### Supported Platforms

| Platform | Versions | Notes |
|----------|----------|-------|
| Spigot | 1.8 - 1.20.x | Fully supported |
| Paper | 1.8 - 1.20.x | Fully supported, recommended |
| Purpur | 1.14 - 1.20.x | Fully supported |
| Airplane | 1.17 - 1.19.x | Fully supported |
| Folia | 1.20.x | Experimental - no guarantees on thread safety |

### Java Versions

| Tubing Version | Minimum Java | Recommended Java |
|----------------|-------------|------------------|
| 7.5.6 | Java 8 | Java 11+ |
| 7.x.x | Java 8 | Java 11+ |

Tubing is compiled with Java 8 for maximum compatibility but works with newer Java versions.

### API Version Targeting

Choose your `api-version` in `plugin.yml` based on your minimum supported Minecraft version:

```yaml
# For 1.13+ (recommended)
api-version: 1.13

# For 1.20+ (latest)
api-version: 1.20
```

Setting `api-version` prevents legacy behavior from being applied to your plugin.

### Version-Specific Features

Some Bukkit APIs changed between versions. Tubing handles this transparently, but be aware:

**1.8 - 1.12:** Legacy material names, older event signatures
**1.13+:** Flattened material system, new event system
**1.17+:** New world height limits
**1.20+:** Adventure API support in Paper

Test your plugin on the oldest and newest versions you support.

## Best Practices for Bukkit Plugins

### 1. Use Dependency Injection

Avoid static managers and singleton patterns. Let Tubing inject dependencies:

**Bad:**
```java
public class PlayerManager {
    private static PlayerManager instance;

    public static PlayerManager getInstance() {
        return instance;
    }
}
```

**Good:**
```java
@IocBean
public class PlayerManager {
    // Constructor injection
    public PlayerManager(DatabaseService database) { }
}
```

### 2. Separate Concerns

Organize your code into layers:

```
src/main/java/com/example/myplugin/
├── MyPlugin.java                    # Main plugin class
├── commands/                        # Command handlers
│   ├── HomeCommand.java
│   └── admin/
│       └── ReloadCommand.java
├── listeners/                       # Event listeners
│   ├── PlayerJoinListener.java
│   └── BlockBreakListener.java
├── services/                        # Business logic
│   ├── PlayerService.java
│   ├── HomeService.java
│   └── TeleportService.java
├── repositories/                    # Data access
│   ├── PlayerRepository.java
│   └── HomeRepository.java
├── models/                          # Data models
│   ├── PlayerData.java
│   └── Home.java
└── config/                          # Configuration classes
    └── SettingsConfig.java
```

### 3. Leverage Configuration Injection

Use `@ConfigProperty` instead of manually parsing YAML:

**Bad:**
```java
FileConfiguration config = getConfig();
int maxHomes = config.getInt("player.max-homes");
```

**Good:**
```java
@IocBean
public class HomeService {
    @ConfigProperty("player.max-homes")
    private int maxHomes;
}
```

### 4. Use Annotations for Platform Registration

Let Tubing register listeners and commands:

**Listeners:**
```java
@IocBukkitListener
public class PlayerListener implements Listener {
    // Automatically registered
}
```

**Commands:**
```java
@IocBukkitCommandHandler("home")
public class HomeCommand implements CommandExecutor {
    // Automatically registered
}
```

### 5. Handle Reload Properly

Implement `beforeReload()` to clean up state:

```java
@Override
protected void beforeReload() {
    // Cancel tasks
    Bukkit.getScheduler().cancelTasks(this);

    // Clear caches
    PlayerCache.clear();
}
```

Register `BeforeTubingReload` hooks for bean-specific cleanup:

```java
@IocBean
public class TaskManager implements BeforeTubingReload {

    @Override
    public void execute(TubingBukkitPlugin plugin) {
        cancelAllTasks();
    }
}
```

### 6. Test Your Plugin

Tubing makes testing easy with constructor injection:

```java
@Test
public void testHomeService() {
    // Mock dependencies
    HomeRepository mockRepo = mock(HomeRepository.class);
    TeleportService mockTeleport = mock(TeleportService.class);

    // Create service with mocks
    HomeService service = new HomeService(mockRepo, mockTeleport);

    // Test behavior
    service.teleportHome(player, "home1");
    verify(mockRepo).getHome(player, "home1");
}
```

### 7. Use Conditional Beans

Enable/disable features via configuration:

```java
@IocBean(conditionalOnProperty = "features.combat.enabled")
public class CombatListener implements Listener {
    // Only loaded if features.combat.enabled = true
}
```

### 8. Leverage Multi-Providers

Create extensible plugin APIs:

```java
public interface RewardHandler {
    void handle(Player player, Reward reward);
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler { }

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemRewardHandler implements RewardHandler { }

@IocBean
public class RewardService {
    public RewardService(@IocMulti(RewardHandler.class)
                        List<RewardHandler> handlers) {
        // Automatically gets all handlers
    }
}
```

### 9. Keep enable() Simple

Do minimal work in `enable()` - let beans handle initialization:

**Bad:**
```java
@Override
protected void enable() {
    loadConfig();
    connectDatabase();
    loadPlayers();
    registerCommands();
    registerListeners();
    startTasks();
    // ... 100 lines of setup
}
```

**Good:**
```java
@Override
protected void enable() {
    getLogger().info("MyPlugin enabled!");
    // Beans handle their own initialization
}
```

### 10. Document Your Configuration

Provide clear configuration examples:

```yaml
# config.yml with comments
database:
  # Database type: mysql, sqlite, h2
  type: sqlite

  # Connection settings
  host: localhost
  port: 3306
  database: myplugin
  username: root
  password: ""

features:
  # Enable combat logging
  combat:
    enabled: true
    cooldown: 30
```

## Complete Example

Here's a complete minimal Bukkit plugin with Tubing:

**MyPlugin.java:**
```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled with Tubing!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled");
    }
}
```

**PlayerService.java:**
```java
package com.example.myplugin.services;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import org.bukkit.entity.Player;

@IocBean
public class PlayerService {

    @ConfigProperty("features.enabled")
    private boolean enabled;

    public void greetPlayer(Player player) {
        if (!enabled) return;
        player.sendMessage("Welcome to the server!");
    }
}
```

**PlayerListener.java:**
```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import com.example.myplugin.services.PlayerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@IocBukkitListener
public class PlayerListener implements Listener {

    private final PlayerService playerService;

    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerService.greetPlayer(event.getPlayer());
    }
}
```

**plugin.yml:**
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: 1.20
author: YourName
```

**config.yml:**
```yaml
features:
  enabled: true
```

That's it! A complete, working plugin with dependency injection, configuration, and event handling.

## Next Steps

Now that you understand Bukkit setup with Tubing:

- [Commands](Commands.md) - Command handling in detail
- [Event Listeners](Event-Listeners.md) - Event listener patterns
- [Plugin Messages](Plugin-Messages.md) - Cross-server communication
- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your code
