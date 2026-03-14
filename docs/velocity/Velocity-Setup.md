# Velocity Setup

This guide covers setting up a Velocity plugin with Tubing, including plugin lifecycle management, IoC container access, and platform-specific configuration.

## Overview

Tubing provides the `TubingVelocityPlugin` base class which implements Velocity's plugin structure and automatically initializes the IoC container. By extending this class, you get:

- Automatic IoC container initialization
- Dependency injection for all beans
- Configuration management
- Platform-specific bean registration (listeners, commands)
- Plugin reload support
- Lifecycle hooks for initialization and cleanup

## Extending TubingVelocityPlugin

Instead of creating a standalone Velocity plugin class, extend `TubingVelocityPlugin`:

```java
package com.example.myvelocityplugin;

import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;

@Plugin(
    id = "myvelocityplugin",
    name = "MyVelocityPlugin",
    version = "1.0.0",
    description = "A Velocity plugin powered by Tubing",
    authors = {"YourName"}
)
public class MyVelocityPlugin extends TubingVelocityPlugin {

    @Inject
    public MyVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        super(server, dataDirectory);
    }

    @Override
    protected void enable() {
        getLogger().info("MyVelocityPlugin enabled with Tubing!");
        // Your plugin initialization code here
    }
}
```

### What TubingVelocityPlugin Provides

The base class handles:

1. **IoC Container Initialization**: Creates and initializes the container before `enable()` is called
2. **Bean Scanning**: Automatically discovers all `@IocBean` annotated classes in your plugin's package
3. **Configuration Loading**: Loads configuration files before beans are created
4. **Platform Registration**: Registers commands and listeners
5. **Static Access**: Provides `TubingVelocityPlugin.getPlugin()` for accessing the plugin instance
6. **Reload Support**: Built-in reload mechanism with proper cleanup
7. **ProxyServer Access**: Direct access to Velocity's ProxyServer instance

## @Plugin Annotation Requirements

Velocity requires the `@Plugin` annotation on your main class. This annotation defines your plugin's metadata:

### Required Fields

```java
@Plugin(
    id = "myvelocityplugin",           // Unique plugin identifier (lowercase, no spaces)
    name = "MyVelocityPlugin",         // Human-readable plugin name
    version = "1.0.0",                 // Plugin version
    authors = {"YourName"}             // Plugin authors
)
```

### Full Annotation Example

```java
@Plugin(
    id = "myvelocityplugin",
    name = "MyVelocityPlugin",
    version = "1.0.0",
    description = "A comprehensive proxy plugin with advanced features",
    url = "https://example.com",
    authors = {"YourName", "Contributor"},
    dependencies = {
        @Dependency(id = "luckperms", optional = true)
    }
)
public class MyVelocityPlugin extends TubingVelocityPlugin {
    // Plugin implementation
}
```

### Important Rules

- **Plugin ID**: Must be lowercase, use only alphanumeric characters and hyphens
- **Version**: Follow semantic versioning (e.g., "1.0.0", "2.1.3")
- **Dependencies**: Optional plugins won't prevent loading if missing
- **Unique ID**: Each plugin on the server must have a unique ID

### Constructor Injection

Velocity uses Google Guice for dependency injection. Your plugin must have a constructor that accepts:

```java
@Inject
public MyVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
    super(server, dataDirectory);
}
```

Additional Velocity objects can be injected:

```java
@Inject
public MyVelocityPlugin(ProxyServer server,
                        @DataDirectory Path dataDirectory,
                        org.slf4j.Logger logger) {
    super(server, dataDirectory);
    // Use Velocity's SLF4J logger if needed
}
```

## Plugin Lifecycle

Understanding the plugin lifecycle helps you know when to perform specific initialization tasks.

### 1. Constructor

The constructor is called when Velocity loads your plugin. The `TubingVelocityPlugin` constructor stores the ProxyServer instance and data directory.

```java
@Inject
public MyVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
    super(server, dataDirectory);
    // Constructor code here - keep it minimal
}
```

**When to use:**
- Storing injected dependencies
- Minimal initialization that doesn't require the proxy to be ready

**Important:** At this point:
- The proxy is not fully initialized
- No beans exist yet
- IoC container is not available

### 2. beforeEnable()

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

### 3. Container Initialization (Automatic)

After `beforeEnable()` returns, Tubing automatically:

1. Creates the IoC container
2. Scans your plugin's package for annotated classes
3. Loads all configuration files
4. Creates all beans in dependency order
5. Injects `@ConfigProperty` values
6. Registers platform-specific beans (listeners, commands)

This happens automatically when Velocity fires the `ProxyInitializeEvent`:

```java
@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    tubingBungeePlugin = this;
    beforeEnable();
    iocContainer = initIocContainer();
    TubingVelocityBeanLoader.load(this);
    enable();
}
```

### 4. enable()

Called after the container is fully initialized and all beans are ready.

```java
@Override
protected void enable() {
    // Container is fully initialized
    // All beans are created and ready
    // Configuration is loaded

    getLogger().info("MyVelocityPlugin enabled!");

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
- ProxyServer is fully initialized

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

The `reload()` method is built into `TubingVelocityPlugin` and handles:

1. Executing all `BeforeTubingReload` hooks
2. Calling your `beforeReload()` method
3. Unregistering all commands
4. Unregistering all event listeners
5. Reinitializing the IoC container
6. Recreating all beans with new configuration

You can call it from a command:

```java
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;

@IocVelocityCommandHandler(value = "myreload")
public class ReloadCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        TubingVelocityPlugin.getPlugin().reload();
        source.sendMessage(Component.text("Plugin reloaded!"));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("myvelocityplugin.admin.reload");
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
    for (String alias : this.proxyServer.getCommandManager().getAliases()) {
        this.proxyServer.getCommandManager().unregister(alias);
    }
    this.proxyServer.getEventManager().unregisterListeners(this);

    // 4. Reinitialize container
    iocContainer = initIocContainer();
    TubingVelocityBeanLoader.load(this);
}
```

### BeforeTubingReload Hooks

You can create beans that implement `BeforeTubingReload` to execute cleanup before reload:

```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingvelocity.annotations.BeforeTubingReload;
import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;

@IocBean
public class DatabaseCleanupHook implements BeforeTubingReload {

    private final DatabaseService databaseService;

    public DatabaseCleanupHook(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void execute(TubingVelocityPlugin tubingPlugin) {
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
public class MyVelocityPlugin extends TubingVelocityPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Use the container
        PlayerService service = container.get(PlayerService.class);
    }
}
```

### Static Access to Plugin Instance

`TubingVelocityPlugin` provides static access to the plugin instance:

```java
// From anywhere in your code
TubingVelocityPlugin plugin = TubingVelocityPlugin.getPlugin();
IocContainer container = plugin.getIocContainer();
ProxyServer server = plugin.getProxyServer();
```

**Use this sparingly** - prefer dependency injection through constructors. Static access is useful for:
- Integration with external libraries that don't support DI
- Velocity scheduler tasks
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

### Accessing Velocity Components

The plugin provides direct access to key Velocity components:

```java
// Get ProxyServer instance
ProxyServer server = getProxyServer();

// Get CommandManager
CommandManager commandManager = getCommandManager();

// Get EventManager
EventManager eventManager = getEventManager();

// Get data folder
File dataFolder = getDataFolder();
```

## Maven Dependencies

Here's the complete Maven setup for a Velocity plugin with Tubing.

### Repository

```xml
<repositories>
    <!-- Velocity repository -->
    <repository>
        <id>velocity</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
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
    <!-- Velocity API (provided by proxy) -->
    <dependency>
        <groupId>com.velocitypowered</groupId>
        <artifactId>velocity-api</artifactId>
        <version>3.1.1</version>
        <scope>provided</scope>
    </dependency>

    <!-- Tubing Velocity (shaded into your plugin) -->
    <dependency>
        <groupId>be.garagepoort.mcioc</groupId>
        <artifactId>tubing-velocity</artifactId>
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

### Annotation Processor (Required)

Velocity requires an annotation processor to generate plugin metadata:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version>
            <configuration>
                <source>11</source>
                <target>11</target>
                <!-- Annotation processor for Velocity -->
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.velocitypowered</groupId>
                        <artifactId>velocity-api</artifactId>
                        <version>3.1.1</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

The annotation processor generates `velocity-plugin.json` from your `@Plugin` annotation.

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
                                <shadedPattern>com.example.myvelocityplugin.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Critical:** Replace `com.example.myvelocityplugin.tubing` with your own unique package path.

### What Tubing Velocity Includes

The `tubing-velocity` artifact includes:
- `tubing-core` - Core IoC container and configuration framework
- ClassGraph - For package scanning
- Commons IO - For file operations

Dependencies are shaded and relocated when you build your plugin.

### Complete POM Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>myvelocityplugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>

    <repositories>
        <repository>
            <id>velocity</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <repository>
            <id>staffplusplus-repo</id>
            <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.velocitypowered</groupId>
            <artifactId>velocity-api</artifactId>
            <version>3.1.1</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>be.garagepoort.mcioc</groupId>
            <artifactId>tubing-velocity</artifactId>
            <version>7.5.6</version>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration>
                    <source>11</source>
                    <target>11</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>com.velocitypowered</groupId>
                            <artifactId>velocity-api</artifactId>
                            <version>3.1.1</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
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
                                    <shadedPattern>com.example.myvelocityplugin.tubing.</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Platform Compatibility

Tubing Velocity is designed for maximum compatibility across Velocity versions.

### Supported Platforms

| Platform | Versions | Notes |
|----------|----------|-------|
| Velocity | 3.1.0+ | Fully supported, recommended |
| Velocity | 3.0.0+ | Compatible |

Velocity maintains strong API stability, so plugins built against older versions typically work on newer proxies.

### Java Versions

| Tubing Version | Minimum Java | Recommended Java | Notes |
|----------------|-------------|------------------|-------|
| 7.5.6 | Java 11 | Java 17+ | Velocity requires Java 11+ |
| 7.x.x | Java 11 | Java 17+ | Velocity requires Java 11+ |

**Important:** Unlike Bukkit and BungeeCord which support Java 8, Velocity requires **Java 11 or higher**. Tubing Velocity is compiled for Java 8 compatibility but you must build and run with Java 11+.

### Velocity API Version

The Velocity API version in your `pom.xml` should match your target proxy version:

```xml
<!-- Velocity 3.1.1 (stable) -->
<dependency>
    <groupId>com.velocitypowered</groupId>
    <artifactId>velocity-api</artifactId>
    <version>3.1.1</version>
    <scope>provided</scope>
</dependency>

<!-- Velocity 3.2.0+ (if available) -->
<dependency>
    <groupId>com.velocitypowered</groupId>
    <artifactId>velocity-api</artifactId>
    <version>3.2.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Version-Specific Features

Velocity's API is relatively stable. Key considerations:

**Velocity 3.0+:** Modern API with Adventure text components
**Velocity 3.1+:** Enhanced performance and bug fixes
**Velocity 3.2+:** Additional features and improvements

Always test your plugin on the oldest and newest Velocity versions you support.

## Best Practices

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
src/main/java/com/example/myvelocityplugin/
├── MyVelocityPlugin.java           # Main plugin class
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
// Manually loading config
ConfigurationNode config = loadConfig();
int maxPlayers = config.node("server", "max-players").getInt();
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
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;

@IocVelocityListener
public class PlayerListener {

    @Subscribe
    public void onPlayerConnect(PostLoginEvent event) {
        // Event handling with automatic dependency injection
    }
}
```

**Commands:**
```java
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;
import com.velocitypowered.api.command.SimpleCommand;

@IocVelocityCommandHandler(value = "home", aliases = {"h"})
public class HomeCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        // Command logic with automatic dependency injection
    }
}
```

### 5. Handle Reload Properly

Implement `beforeReload()` to clean up state:

```java
@Override
protected void beforeReload() {
    // Cancel tasks using Velocity's scheduler
    getProxyServer().getScheduler()
        .tasksByPlugin(this)
        .forEach(ScheduledTask::cancel);

    // Clear caches
    PlayerCache.clear();
}
```

Register `BeforeTubingReload` hooks for bean-specific cleanup:

```java
@IocBean
public class TaskManager implements BeforeTubingReload {

    @Override
    public void execute(TubingVelocityPlugin plugin) {
        cancelAllTasks();
    }
}
```

### 6. Use Adventure Components

Velocity uses Adventure for text components. Avoid legacy formatting:

**Bad:**
```java
player.sendMessage("§aWelcome!");
```

**Good:**
```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

player.sendMessage(Component.text("Welcome!", NamedTextColor.GREEN));
```

### 7. Test Your Plugin

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
    RegisteredServer best = balancer.getBestServer();
    verify(mockRepo).getAvailableServers();
}
```

### 8. Use Conditional Beans

Enable/disable features via configuration:

```java
@IocBean(conditionalOnProperty = "features.motd.enabled")
@IocVelocityListener
public class MotdListener {
    // Only loaded if features.motd.enabled = true
}
```

### 9. Leverage Multi-Providers

Create extensible plugin APIs:

```java
public interface ServerSelector {
    RegisteredServer select(Collection<RegisteredServer> servers);
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

### 10. Keep enable() Simple

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
    getLogger().info("MyVelocityPlugin enabled!");
    // Beans handle their own initialization
}
```

### 11. Handle Async Operations Properly

Velocity is highly asynchronous. Use proper async handling:

```java
@IocBean
public class DatabaseService {

    private final TubingVelocityPlugin plugin;

    public DatabaseService(TubingVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<PlayerData> loadPlayerData(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            // Database query
            return fetchFromDatabase(playerId);
        }, plugin.getProxyServer().getScheduler()
            .buildTask(plugin, () -> {})
            .schedule()
            .executorService());
    }
}
```

### 12. Use Velocity's Modern Features

Take advantage of Velocity's modern architecture:

**Plugin Messaging:**
```java
@IocBean
public class MessagingService {

    private final TubingVelocityPlugin plugin;
    private static final MinecraftChannelIdentifier CHANNEL =
        MinecraftChannelIdentifier.from("myplugin:main");

    public MessagingService(TubingVelocityPlugin plugin) {
        this.plugin = plugin;
        plugin.getProxyServer().getChannelRegistrar().register(CHANNEL);
    }

    public void sendToServer(RegisteredServer server, byte[] data) {
        server.sendPluginMessage(CHANNEL, data);
    }
}
```

### 13. Document Your Configuration

Provide clear configuration examples:

```yaml
# config.yml with comments
database:
  # Database type: mysql, postgresql, sqlite, h2
  type: postgresql

  # Connection settings
  host: localhost
  port: 5432
  database: myvelocityplugin
  username: velocity
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
    survival:
      - survival-1
      - survival-2
```

## Complete Example

Here's a complete minimal Velocity plugin with Tubing:

**MyVelocityPlugin.java:**
```java
package com.example.myvelocityplugin;

import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;

@Plugin(
    id = "myvelocityplugin",
    name = "MyVelocityPlugin",
    version = "1.0.0",
    description = "A Velocity plugin powered by Tubing",
    authors = {"YourName"}
)
public class MyVelocityPlugin extends TubingVelocityPlugin {

    @Inject
    public MyVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        super(server, dataDirectory);
    }

    @Override
    protected void enable() {
        getLogger().info("MyVelocityPlugin enabled with Tubing!");
    }
}
```

**PlayerService.java:**
```java
package com.example.myvelocityplugin.services;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

@IocBean
public class PlayerService {

    @ConfigProperty("features.enabled")
    private boolean enabled;

    public void greetPlayer(Player player) {
        if (!enabled) return;
        player.sendMessage(Component.text("Welcome to the network!"));
    }
}
```

**PlayerListener.java:**
```java
package com.example.myvelocityplugin.listeners;

import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityListener;
import com.example.myvelocityplugin.services.PlayerService;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;

@IocVelocityListener
public class PlayerListener {

    private final PlayerService playerService;

    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        playerService.greetPlayer(event.getPlayer());
    }
}
```

**HomeCommand.java:**
```java
package com.example.myvelocityplugin.commands;

import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityCommandHandler;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

@IocVelocityCommandHandler(value = "home", aliases = {"h"})
public class HomeCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        invocation.source().sendMessage(Component.text("Teleporting home..."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("myvelocityplugin.home");
    }
}
```

**config.yml:**
```yaml
features:
  enabled: true
```

That's it! A complete, working Velocity plugin with dependency injection, configuration, event handling, and commands.

## Next Steps

Now that you understand Velocity setup with Tubing:

- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Understanding bean creation and destruction
- [Multi-Providers](../core/Multi-Providers.md) - Creating extensible APIs

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your code
