# BungeeCord Setup

This guide covers setting up a BungeeCord plugin with Tubing, including plugin lifecycle management, IoC container access, and platform-specific configuration.

## Overview

Tubing provides the `TubingBungeePlugin` base class which extends BungeeCord's `Plugin` and automatically initializes the IoC container. By extending this class, you get:

- Automatic IoC container initialization
- Dependency injection for all beans
- Configuration management
- Platform-specific bean registration (listeners, commands)
- Plugin reload support
- Lifecycle hooks for initialization and cleanup

## Extending TubingBungeePlugin

Instead of extending BungeeCord's `Plugin` directly, extend `TubingBungeePlugin`:

```java
package com.example.myproxy;

import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyProxyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        getLogger().info("MyProxyPlugin enabled with Tubing!");
        // Your plugin initialization code here
    }

    @Override
    protected void disable() {
        getLogger().info("MyProxyPlugin disabled");
        // Your plugin cleanup code here
    }
}
```

### What TubingBungeePlugin Provides

The base class handles:

1. **IoC Container Initialization**: Creates and initializes the container before `enable()` is called
2. **Bean Scanning**: Automatically discovers all `@IocBean` annotated classes in your plugin's package
3. **Configuration Loading**: Loads configuration files before beans are created
4. **Platform Registration**: Registers commands and listeners
5. **Static Access**: Provides `TubingBungeePlugin.getPlugin()` for accessing the plugin instance
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

```java
@Override
public void onEnable() {
    tubingBungeePlugin = this;
    beforeEnable();
    iocContainer = initIocContainer();
    TubingBungeeBeanLoader.load(this);
    enable();
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

    getLogger().info("MyProxyPlugin enabled!");

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
    getLogger().info("MyProxyPlugin disabled");

    // Clean up resources
    // Close database connections
    // Cancel tasks
    // Save data
}
```

**When to use:**
- Closing database connections
- Canceling scheduled tasks
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

The `reload()` method is built into `TubingBungeePlugin` and handles:

1. Executing all `BeforeTubingReload` hooks
2. Calling your `beforeReload()` method
3. Unregistering all commands
4. Unregistering all event listeners
5. Reinitializing the IoC container
6. Recreating all beans with new configuration

You can call it from a command:

```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

@IocBungeeCommandHandler
public class ReloadCommand extends Command {

    public ReloadCommand() {
        super("myreload", "myproxy.admin.reload");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        TubingBungeePlugin.getPlugin().reload();
        sender.sendMessage("Plugin reloaded!");
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

    // 3. Unregister commands and listeners
    ProxyServer.getInstance().getPluginManager().unregisterCommands(this);
    ProxyServer.getInstance().getPluginManager().unregisterListeners(this);

    // 4. Reinitialize container
    iocContainer = initIocContainer();
    TubingBungeeBeanLoader.load(this);
}
```

### BeforeTubingReload Hooks

You can create beans that implement `BeforeTubingReload` to execute cleanup before reload:

```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbungee.annotations.BeforeTubingReload;
import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

@IocBean
public class DatabaseCleanupHook implements BeforeTubingReload {

    private final DatabaseService databaseService;

    public DatabaseCleanupHook(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void execute(TubingBungeePlugin tubingPlugin) {
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
public class MyProxyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Use the container
        PlayerService service = container.get(PlayerService.class);
    }
}
```

### Static Access to Plugin Instance

`TubingBungeePlugin` provides static access to the plugin instance:

```java
// From anywhere in your code
TubingBungeePlugin plugin = TubingBungeePlugin.getPlugin();
IocContainer container = plugin.getIocContainer();
```

**Use this sparingly** - prefer dependency injection through constructors. Static access is useful for:
- Integration with external libraries that don't support DI
- BungeeCord scheduler tasks
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

## bungee.yml Configuration

Your `bungee.yml` should reference your `TubingBungeePlugin` subclass as the main class:

### Minimal Configuration

```yaml
name: MyProxyPlugin
version: 1.0.0
main: com.example.myproxy.MyProxyPlugin
author: YourName
```

### Full Configuration Example

```yaml
name: MyProxyPlugin
version: 1.0.0
main: com.example.myproxy.MyProxyPlugin
author: YourName
description: A BungeeCord plugin powered by Tubing IoC framework

# Plugin dependencies
depends: []
softDepends: []
```

### Command and Listener Registration

Unlike Bukkit plugins, BungeeCord doesn't require command definitions in `bungee.yml`. Commands are registered programmatically:

**Commands:** Use `@IocBungeeCommandHandler` annotation and extend BungeeCord's `Command` class:

```java
@IocBungeeCommandHandler
public class HomeCommand extends Command {

    public HomeCommand() {
        super("home", "myproxy.home", "h");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Command logic with automatic dependency injection
    }
}
```

**Listeners:** Use `@IocBungeeListener` annotation and implement BungeeCord's `Listener` interface:

```java
@IocBungeeListener
public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerConnect(PostLoginEvent event) {
        // Event handling with automatic dependency injection
    }
}
```

Both annotations support:
- `conditionalOnProperty` - Only load if a config property is enabled
- `multiproviderClass` - Register as a multi-provider implementation
- `priority` - Mark as a priority bean

## Maven Dependencies for BungeeCord

Here's the complete Maven setup for a BungeeCord plugin with Tubing.

### Repository

```xml
<repositories>
    <!-- BungeeCord repository -->
    <repository>
        <id>bungeecord-repo</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
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
    <!-- BungeeCord API (provided by proxy) -->
    <dependency>
        <groupId>net.md-5</groupId>
        <artifactId>bungeecord-api</artifactId>
        <version>1.16-R0.4</version>
        <type>jar</type>
        <scope>provided</scope>
    </dependency>

    <!-- Tubing BungeeCord (shaded into your plugin) -->
    <dependency>
        <groupId>be.garagepoort.mcioc</groupId>
        <artifactId>tubing-bungee</artifactId>
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
                                <shadedPattern>com.example.myproxy.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Critical:** Replace `com.example.myproxy.tubing` with your own unique package path.

### What Tubing BungeeCord Includes

The `tubing-bungee` artifact includes:
- `tubing-core` - Core IoC container and configuration framework
- ClassGraph - For package scanning
- Commons IO - For file operations

Dependencies are shaded and relocated when you build your plugin.

### Compiler Configuration

Ensure you're targeting Java 8 for maximum compatibility:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Platform Compatibility

Tubing BungeeCord is designed for maximum compatibility across proxy versions.

### Supported Platforms

| Platform | Versions | Notes |
|----------|----------|-------|
| BungeeCord | All versions | Fully supported |
| Waterfall | All versions | Fully supported, recommended |
| FlameCord | Recent versions | Compatible |
| Travertine | All versions | Compatible |

### Java Versions

| Tubing Version | Minimum Java | Recommended Java |
|----------------|-------------|------------------|
| 7.5.6 | Java 8 | Java 11+ |
| 7.x.x | Java 8 | Java 11+ |

Tubing is compiled with Java 8 for maximum compatibility but works with newer Java versions.

### BungeeCord API Version

The BungeeCord API version in your `pom.xml` can be updated to match your target proxy version:

```xml
<!-- Stable release -->
<dependency>
    <groupId>net.md-5</groupId>
    <artifactId>bungeecord-api</artifactId>
    <version>1.16-R0.4</version>
    <scope>provided</scope>
</dependency>

<!-- Or latest snapshot -->
<dependency>
    <groupId>net.md-5</groupId>
    <artifactId>bungeecord-api</artifactId>
    <version>1.20-R0.2-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

BungeeCord maintains backward compatibility, so plugins built against older API versions work on newer proxies.

### Version-Specific Features

BungeeCord's API is relatively stable. Be aware of:

**Older versions:** May lack newer event types or API methods
**Waterfall:** Adds performance improvements and bug fixes
**1.16+:** Support for newer Minecraft versions and features

Test your plugin on the oldest and newest proxy versions you support.

## Best Practices for BungeeCord Plugins

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
src/main/java/com/example/myproxy/
├── MyProxyPlugin.java              # Main plugin class
├── commands/                       # Command handlers
│   ├── HomeCommand.java
│   └── admin/
│       └── ReloadCommand.java
├── listeners/                      # Event listeners
│   ├── PlayerConnectListener.java
│   └── ServerSwitchListener.java
├── services/                       # Business logic
│   ├── PlayerService.java
│   ├── ServerBalancer.java
│   └── MessagingService.java
├── repositories/                   # Data access
│   ├── PlayerRepository.java
│   └── ServerRepository.java
├── models/                         # Data models
│   ├── PlayerData.java
│   └── ServerGroup.java
└── config/                         # Configuration classes
    └── SettingsConfig.java
```

### 3. Leverage Configuration Injection

Use `@ConfigProperty` instead of manually parsing YAML:

**Bad:**
```java
Configuration config = getConfig();
int maxPlayers = config.getInt("server.max-players");
```

**Good:**
```java
@IocBean
public class ServerBalancer {
    @ConfigProperty("server.max-players")
    private int maxPlayers;
}
```

### 4. Use Annotations for Platform Registration

Let Tubing register listeners and commands:

**Listeners:**
```java
@IocBungeeListener
public class PlayerListener implements Listener {
    // Automatically registered
}
```

**Commands:**
```java
@IocBungeeCommandHandler
public class HomeCommand extends Command {
    public HomeCommand() {
        super("home", "myproxy.home");
    }
    // Automatically registered
}
```

### 5. Handle Reload Properly

Implement `beforeReload()` to clean up state:

```java
@Override
protected void beforeReload() {
    // Cancel tasks
    getProxy().getScheduler().cancel(this);

    // Clear caches
    PlayerCache.clear();
}
```

Register `BeforeTubingReload` hooks for bean-specific cleanup:

```java
@IocBean
public class TaskManager implements BeforeTubingReload {

    @Override
    public void execute(TubingBungeePlugin plugin) {
        cancelAllTasks();
    }
}
```

### 6. Test Your Plugin

Tubing makes testing easy with constructor injection:

```java
@Test
public void testServerBalancer() {
    // Mock dependencies
    ServerRepository mockRepo = mock(ServerRepository.class);
    PlayerService mockPlayers = mock(PlayerService.class);

    // Create service with mocks
    ServerBalancer balancer = new ServerBalancer(mockRepo, mockPlayers);

    // Test behavior
    ServerInfo best = balancer.getBestServer();
    verify(mockRepo).getAvailableServers();
}
```

### 7. Use Conditional Beans

Enable/disable features via configuration:

```java
@IocBean(conditionalOnProperty = "features.motd.enabled")
public class MotdListener implements Listener {
    // Only loaded if features.motd.enabled = true
}
```

### 8. Leverage Multi-Providers

Create extensible plugin APIs:

```java
public interface ServerSelector {
    ServerInfo select(Collection<ServerInfo> servers);
}

@IocBean
@IocMultiProvider(ServerSelector.class)
public class RoundRobinSelector implements ServerSelector { }

@IocBean
@IocMultiProvider(ServerSelector.class)
public class LeastPlayersSelector implements ServerSelector { }

@IocBean
public class ServerBalancer {
    public ServerBalancer(@IocMulti(ServerSelector.class)
                         List<ServerSelector> selectors) {
        // Automatically gets all selectors
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
    getLogger().info("MyProxyPlugin enabled!");
    // Beans handle their own initialization
}
```

### 10. Handle Cross-Server Communication

BungeeCord plugins often need to communicate between servers:

```java
@IocBean
public class MessagingService {

    private final TubingBungeePlugin plugin;

    public MessagingService(TubingBungeePlugin plugin) {
        this.plugin = plugin;
    }

    public void sendToServer(String serverName, String channel, byte[] data) {
        ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
        if (server != null) {
            server.sendData(channel, data);
        }
    }
}
```

### 11. Document Your Configuration

Provide clear configuration examples:

```yaml
# config.yml with comments
database:
  # Database type: mysql, sqlite, h2
  type: mysql

  # Connection settings
  host: localhost
  port: 3306
  database: myproxy
  username: root
  password: ""

server-balancing:
  # Load balancing strategy: round-robin, least-players, random
  strategy: least-players

  # Server groups for organized balancing
  groups:
    lobby:
      - lobby-1
      - lobby-2
      - lobby-3
```

## Complete Example

Here's a complete minimal BungeeCord plugin with Tubing:

**MyProxyPlugin.java:**
```java
package com.example.myproxy;

import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyProxyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        getLogger().info("MyProxyPlugin enabled with Tubing!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyProxyPlugin disabled");
    }
}
```

**PlayerService.java:**
```java
package com.example.myproxy.services;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import net.md_5.bungee.api.connection.ProxiedPlayer;

@IocBean
public class PlayerService {

    @ConfigProperty("features.enabled")
    private boolean enabled;

    public void greetPlayer(ProxiedPlayer player) {
        if (!enabled) return;
        player.sendMessage("Welcome to the network!");
    }
}
```

**PlayerListener.java:**
```java
package com.example.myproxy.listeners;

import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeListener;
import com.example.myproxy.services.PlayerService;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

@IocBungeeListener
public class PlayerListener implements Listener {

    private final PlayerService playerService;

    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        playerService.greetPlayer(event.getPlayer());
    }
}
```

**HomeCommand.java:**
```java
package com.example.myproxy.commands;

import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

@IocBungeeCommandHandler
public class HomeCommand extends Command {

    public HomeCommand() {
        super("home", "myproxy.home", "h");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Teleporting home...");
    }
}
```

**bungee.yml:**
```yaml
name: MyProxyPlugin
version: 1.0.0
main: com.example.myproxy.MyProxyPlugin
author: YourName
```

**config.yml:**
```yaml
features:
  enabled: true
```

That's it! A complete, working BungeeCord plugin with dependency injection, configuration, event handling, and commands.

## Next Steps

Now that you understand BungeeCord setup with Tubing:

- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Understanding bean creation and destruction
- [Multi-Providers](../core/Multi-Providers.md) - Creating extensible APIs

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your code
