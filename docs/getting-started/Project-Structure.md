# Project Structure

Organizing your Tubing project properly is crucial for maintainability, testability, and scalability. This guide covers recommended package structures, separation of concerns, and best practices for projects of all sizes.

## Core Principles

Tubing's IoC container encourages clean architecture through:

1. **Separation of Concerns**: Each class has a single, well-defined responsibility
2. **Dependency Inversion**: Depend on abstractions (interfaces) rather than concrete implementations
3. **Loose Coupling**: Components interact through injected dependencies
4. **High Cohesion**: Related functionality grouped together in logical packages

These principles guide how you should organize your code.

## Basic Package Structure

### Small Plugin (Single Module)

For simple plugins with 5-20 classes, a flat package structure works well:

```
src/main/java/com/example/myplugin/
├── MyPlugin.java                    # Main plugin class
├── commands/
│   ├── HomeCommand.java             # Command handlers
│   └── TeleportCommand.java
├── listeners/
│   ├── PlayerListener.java          # Event listeners
│   └── EntityListener.java
├── services/
│   ├── PlayerDataService.java       # Business logic
│   ├── TeleportService.java
│   └── PermissionService.java
└── config/
    └── PluginConfig.java            # Configuration holder

src/main/resources/
├── plugin.yml
└── config.yml
```

**Key Points:**
- Commands handle user input, delegate to services
- Services contain business logic and state
- Listeners react to events, delegate to services
- Configuration isolated in its own package

### Medium Plugin (Single Module)

For plugins with 20-100 classes, add more structure:

```
src/main/java/com/example/myplugin/
├── MyPlugin.java
├── commands/
│   ├── admin/                       # Group related commands
│   │   ├── ReloadCommand.java
│   │   └── DebugCommand.java
│   ├── home/
│   │   ├── HomeCommand.java
│   │   ├── SetHomeCommand.java
│   │   └── DeleteHomeCommand.java
│   └── CommandConstants.java        # Shared command constants
├── listeners/
│   ├── player/
│   │   ├── PlayerJoinListener.java
│   │   └── PlayerQuitListener.java
│   └── protection/
│       └── BlockBreakListener.java
├── services/
│   ├── home/
│   │   ├── HomeService.java         # Domain-specific services
│   │   └── HomeValidator.java
│   ├── player/
│   │   ├── PlayerDataService.java
│   │   └── PlayerCacheService.java
│   └── messaging/
│       └── MessageService.java
├── models/                          # Data models
│   ├── Home.java
│   ├── PlayerData.java
│   └── Location.java
├── repositories/                    # Data access layer
│   ├── HomeRepository.java
│   └── PlayerRepository.java
├── config/
│   ├── PluginConfig.java
│   ├── MessagesConfig.java
│   └── transformers/                # Custom config transformers
│       └── LocationTransformer.java
├── utils/                           # Utility classes
│   ├── LocationUtil.java
│   └── MessageUtil.java
└── exceptions/                      # Custom exceptions
    └── HomeNotFoundException.java

src/main/resources/
├── plugin.yml
├── config.yml
└── messages.yml
```

**Key Points:**
- Commands grouped by feature domain
- Services organized by concern
- Models represent domain objects
- Repositories handle data persistence
- Utilities are stateless helpers

### Large Plugin (Multi-Module)

For complex plugins with 100+ classes or multiple deployment targets, use Maven multi-module structure:

```
my-plugin/
├── pom.xml                          # Parent POM
├── my-plugin-core/                  # Core logic (platform-independent)
│   ├── pom.xml
│   └── src/main/java/com/example/myplugin/core/
│       ├── services/
│       │   ├── home/
│       │   │   ├── HomeService.java
│       │   │   └── HomeValidator.java
│       │   ├── player/
│       │   │   └── PlayerService.java
│       │   └── economy/
│       │       └── EconomyService.java
│       ├── models/
│       │   ├── Home.java
│       │   ├── Player.java
│       │   └── Transaction.java
│       ├── repositories/
│       │   ├── HomeRepository.java
│       │   └── PlayerRepository.java
│       ├── api/                     # Public API for other plugins
│       │   ├── IHomeService.java
│       │   └── IPlayerService.java
│       ├── config/
│       │   └── CoreConfig.java
│       └── exceptions/
│           └── PluginException.java
│
├── my-plugin-bukkit/                # Bukkit-specific implementation
│   ├── pom.xml
│   └── src/main/java/com/example/myplugin/bukkit/
│       ├── MyBukkitPlugin.java
│       ├── commands/
│       │   ├── home/
│       │   │   └── HomeCommand.java
│       │   └── admin/
│       │       └── AdminCommands.java
│       ├── listeners/
│       │   ├── PlayerListener.java
│       │   └── ProtectionListener.java
│       ├── adapters/                # Platform adapters
│       │   ├── BukkitPlayerAdapter.java
│       │   └── BukkitLocationAdapter.java
│       ├── gui/                     # GUI controllers
│       │   ├── HomeListGui.java
│       │   └── AdminGui.java
│       └── config/
│           └── BukkitConfig.java
│
├── my-plugin-bungee/                # BungeeCord implementation
│   ├── pom.xml
│   └── src/main/java/com/example/myplugin/bungee/
│       ├── MyBungeePlugin.java
│       ├── commands/
│       │   └── NetworkCommands.java
│       └── listeners/
│           └── ProxyListener.java
│
└── my-plugin-api/                   # Public API module (optional)
    ├── pom.xml
    └── src/main/java/com/example/myplugin/api/
        ├── IHomeService.java
        ├── IPlayerService.java
        └── events/
            ├── HomeCreateEvent.java
            └── HomeDeleteEvent.java
```

**Module Dependencies:**
- `my-plugin-bukkit` depends on `my-plugin-core`
- `my-plugin-bungee` depends on `my-plugin-core`
- `my-plugin-api` is standalone (other plugins depend on it)
- `my-plugin-core` depends on `my-plugin-api` (if API module exists)

## Package Organization Patterns

### By Layer (Traditional)

Organize by technical layer:

```
com.example.myplugin/
├── commands/        # All commands
├── listeners/       # All listeners
├── services/        # All services
├── repositories/    # All repositories
└── models/          # All models
```

**Pros:**
- Simple and intuitive
- Easy to find classes by type
- Good for small to medium plugins

**Cons:**
- Scales poorly for large projects
- Difficult to identify feature boundaries
- Related classes scattered across packages

### By Feature (Domain-Driven)

Organize by business feature:

```
com.example.myplugin/
├── home/                            # Home feature
│   ├── HomeCommand.java
│   ├── HomeService.java
│   ├── HomeRepository.java
│   ├── Home.java
│   └── HomeValidator.java
├── warp/                            # Warp feature
│   ├── WarpCommand.java
│   ├── WarpService.java
│   ├── WarpRepository.java
│   └── Warp.java
├── player/                          # Player management
│   ├── PlayerListener.java
│   ├── PlayerService.java
│   └── PlayerData.java
└── common/                          # Shared utilities
    ├── config/
    ├── messaging/
    └── utils/
```

**Pros:**
- High cohesion - related code together
- Easy to understand feature scope
- Scales well for large projects
- Facilitates modular development

**Cons:**
- Less obvious where cross-cutting concerns go
- May duplicate utilities if not careful

### Hybrid Approach (Recommended)

Combine both approaches for the best of both worlds:

```
com.example.myplugin/
├── commands/
│   ├── home/                        # Feature-grouped commands
│   ├── warp/
│   └── admin/
├── services/
│   ├── home/                        # Feature-grouped services
│   ├── warp/
│   └── player/
├── models/                          # All models (layer-based)
├── repositories/                    # All repositories (layer-based)
├── listeners/
│   ├── player/                      # Feature-grouped listeners
│   └── protection/
└── common/                          # Shared infrastructure
    ├── config/
    ├── messaging/
    └── utils/
```

This structure provides clear technical boundaries while grouping related functionality.

## Bean Organization Best Practices

### Service Layer

Services contain your business logic. Keep them focused:

```java
@IocBean
public class HomeService {

    private final HomeRepository homeRepository;
    private final PermissionService permissionService;
    private final MessageService messageService;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    public HomeService(HomeRepository homeRepository,
                       PermissionService permissionService,
                       MessageService messageService) {
        this.homeRepository = homeRepository;
        this.permissionService = permissionService;
        this.messageService = messageService;
    }

    public void createHome(Player player, String name, Location location) {
        // Business logic here
    }

    public void deleteHome(Player player, String name) {
        // Business logic here
    }
}
```

**Guidelines:**
- One service per domain concept (HomeService, WarpService, etc.)
- Services should not directly call Bukkit/Bungee APIs - use adapters
- Services should be testable without Minecraft running
- Inject dependencies, never use static access

### Repository Layer

Repositories handle data persistence:

```java
@IocBean
public class HomeRepository {

    private final Database database;

    public HomeRepository(Database database) {
        this.database = database;
    }

    public Home findByPlayerAndName(UUID playerId, String name) {
        // Database access here
    }

    public List<Home> findAllByPlayer(UUID playerId) {
        // Database access here
    }

    public void save(Home home) {
        // Database access here
    }

    public void delete(Home home) {
        // Database access here
    }
}
```

**Guidelines:**
- Repositories abstract data storage mechanism
- No business logic in repositories
- Return domain models, not database DTOs
- One repository per aggregate root

### Command Layer

Commands handle user interaction:

```java
@IocBukkitCommandHandler("home")
public class HomeCommand {

    private final HomeService homeService;
    private final MessageService messageService;

    public HomeCommand(HomeService homeService, MessageService messageService) {
        this.homeService = homeService;
        this.messageService = messageService;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messageService.sendError(sender, "command.player-only");
            return true;
        }

        Player player = (Player) sender;

        // Input validation
        if (args.length != 1) {
            messageService.sendError(sender, "command.home.usage");
            return true;
        }

        // Delegate to service
        homeService.teleportToHome(player, args[0]);
        return true;
    }
}
```

**Guidelines:**
- Commands validate input, then delegate to services
- No business logic in commands
- Return proper command result (true/false)
- Use message service for user feedback

### Listener Layer

Listeners react to platform events:

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    private final PlayerService playerService;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Minimal logic, delegate to service
        playerService.handlePlayerJoin(event.getPlayer());
    }
}
```

**Guidelines:**
- Listeners should be thin wrappers
- Immediately delegate to services
- One listener class per event category (player, entity, block, etc.)
- Avoid business logic in event handlers

### Model Layer

Models represent your domain objects:

```java
public class Home {

    private final UUID ownerId;
    private final String name;
    private final Location location;
    private final LocalDateTime createdAt;

    public Home(UUID ownerId, String name, Location location, LocalDateTime createdAt) {
        this.ownerId = ownerId;
        this.name = name;
        this.location = location;
        this.createdAt = createdAt;
    }

    // Getters only - immutable
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Location getLocation() { return location; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**Guidelines:**
- Models are NOT beans (no `@IocBean` annotation)
- Prefer immutability when possible
- Models contain data, not behavior
- Use builder pattern for complex models

## Configuration Organization

### Multiple Configuration Files

For larger plugins, split configuration by concern:

```java
@IocBean
@ConfigurationFile(value = "config.yml", autosave = true)
public class PluginConfig {

    @ConfigProperty("plugin.debug")
    private boolean debug;

    @ConfigProperty("plugin.language")
    private String language;
}

@IocBean
@ConfigurationFile("homes.yml")
public class HomesConfig {

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown")
    private int cooldown;
}

@IocBean
@ConfigurationFile("messages.yml")
public class MessagesConfig {

    @ConfigProperty("messages.prefix")
    private String prefix;

    @ConfigEmbeddedObject(path = "messages.errors")
    private ErrorMessages errors;
}
```

**File Structure:**
```
src/main/resources/
├── config.yml          # Main plugin config
├── homes.yml           # Feature-specific config
├── warps.yml           # Feature-specific config
└── messages.yml        # All user messages
```

### Configuration Transformers

For complex types, create custom transformers:

```
com.example.myplugin/
└── config/
    ├── PluginConfig.java
    ├── HomesConfig.java
    └── transformers/
        ├── LocationTransformer.java
        ├── ItemStackTransformer.java
        └── DateTimeTransformer.java
```

Each transformer converts between YAML and Java objects:

```java
@IocBean
public class LocationTransformer extends ConfigTransformer<Location> {

    @Override
    public Location mapConfig(String value, String path) {
        // Parse "world,x,y,z" to Location
    }
}
```

## Testing Structure

Organize tests to mirror your main code:

```
src/test/java/com/example/myplugin/
├── services/
│   ├── home/
│   │   ├── HomeServiceTest.java
│   │   └── HomeValidatorTest.java
│   └── player/
│       └── PlayerServiceTest.java
├── repositories/
│   └── HomeRepositoryTest.java
├── models/
│   └── HomeTest.java
└── testutil/                        # Test utilities
    ├── MockFactory.java
    ├── TestData.java
    └── TestPlugin.java
```

**Testing with Tubing:**

```java
public class HomeServiceTest {

    private HomeService homeService;
    private HomeRepository mockRepository;
    private PermissionService mockPermissions;
    private MessageService mockMessages;

    @Before
    public void setup() {
        // Create mocks
        mockRepository = mock(HomeRepository.class);
        mockPermissions = mock(PermissionService.class);
        mockMessages = mock(MessageService.class);

        // Manual construction for testing (no IoC needed in tests)
        homeService = new HomeService(mockRepository, mockPermissions, mockMessages);
    }

    @Test
    public void testCreateHome() {
        // Test logic here
    }
}
```

Tubing's constructor injection makes testing easy - no framework needed in unit tests!

## Multi-Module Example

Here's a complete multi-module POM structure:

**Parent pom.xml:**
```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>my-plugin-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>my-plugin-api</module>
        <module>my-plugin-core</module>
        <module>my-plugin-bukkit</module>
        <module>my-plugin-bungee</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>be.garagepoort.mcioc</groupId>
                <artifactId>tubing-bukkit</artifactId>
                <version>7.5.6</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

**Core module pom.xml:**
```xml
<project>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-plugin-parent</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>my-plugin-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-plugin-api</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

**Bukkit module pom.xml:**
```xml
<project>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-plugin-parent</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>my-plugin-bukkit</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>my-plugin-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>be.garagepoort.mcioc</groupId>
            <artifactId>tubing-bukkit</artifactId>
        </dependency>
    </dependencies>
</project>
```

## Common Patterns

### Feature Toggle Pattern

Organize optional features that can be enabled/disabled:

```
com.example.myplugin/
├── features/
│   ├── homes/
│   │   ├── HomesFeature.java        # Feature entry point
│   │   ├── HomeCommand.java
│   │   └── HomeService.java
│   ├── warps/
│   │   ├── WarpsFeature.java
│   │   ├── WarpCommand.java
│   │   └── WarpService.java
│   └── economy/
│       ├── EconomyFeature.java
│       └── EconomyService.java
└── core/
    └── FeatureManager.java          # Manages feature lifecycle
```

Use conditional beans:

```java
@IocBean(conditionalOnProperty = "features.homes.enabled")
public class HomesFeature {
    // Feature implementation
}
```

### Plugin API Pattern

For plugins that expose an API to other plugins:

```
my-plugin-api/
└── com/example/myplugin/api/
    ├── MyPluginAPI.java             # Main API interface
    ├── services/
    │   ├── IHomeService.java
    │   └── IWarpService.java
    ├── models/
    │   ├── Home.java
    │   └── Warp.java
    └── events/
        ├── HomeCreateEvent.java
        └── WarpCreateEvent.java

my-plugin-core/
└── com/example/myplugin/core/
    └── api/
        └── MyPluginAPIImpl.java     # Implementation
```

Register the API as a bean:

```java
@IocBeanProvider
public class APIProvider {

    private final HomeService homeService;
    private final WarpService warpService;

    public APIProvider(HomeService homeService, WarpService warpService) {
        this.homeService = homeService;
        this.warpService = warpService;
    }

    @IocBean
    public MyPluginAPI provideAPI() {
        return new MyPluginAPIImpl(homeService, warpService);
    }
}
```

## Anti-Patterns to Avoid

### Don't: Static Utility Hell

```java
// BAD - Static access everywhere
public class PlayerUtils {
    public static void heal(Player player) {
        player.setHealth(20);
        DatabaseManager.getInstance().savePlayer(player);
    }
}
```

**Instead:** Use proper dependency injection:

```java
// GOOD - Injectable service
@IocBean
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public void heal(Player player) {
        player.setHealth(20);
        repository.save(player);
    }
}
```

### Don't: God Classes

```java
// BAD - One class does everything
@IocBean
public class PluginManager {
    public void handleCommand() { }
    public void handleEvent() { }
    public void saveData() { }
    public void loadConfig() { }
    // 50 more methods...
}
```

**Instead:** Split responsibilities:

```java
// GOOD - Focused classes
@IocBean
public class CommandHandler { }

@IocBean
public class EventHandler { }

@IocBean
public class DataManager { }

@IocBean
public class ConfigManager { }
```

### Don't: Direct Platform Dependencies in Core

```java
// BAD - Bukkit API in core logic
@IocBean
public class HomeService {
    public void teleport(Player player, String home) {
        Location loc = getHomeLocation(home);
        player.teleport(loc); // Direct Bukkit dependency
    }
}
```

**Instead:** Use abstraction:

```java
// GOOD - Platform-independent core
@IocBean
public class HomeService {
    private final TeleportAdapter teleportAdapter;

    public void teleport(UUID playerId, String home) {
        HomeLocation loc = getHomeLocation(home);
        teleportAdapter.teleport(playerId, loc);
    }
}

// Platform-specific adapter
@IocBean
public class BukkitTeleportAdapter implements TeleportAdapter {
    public void teleport(UUID playerId, HomeLocation loc) {
        Player player = Bukkit.getPlayer(playerId);
        Location bukkitLoc = toBukkitLocation(loc);
        player.teleport(bukkitLoc);
    }
}
```

## Migration Path

### From Small to Medium

When your plugin outgrows flat structure:

1. Create feature-based subpackages
2. Split large services into smaller ones
3. Add repository layer for data access
4. Extract models into separate package
5. Add configuration transformers for complex types

### From Medium to Large (Multi-Module)

When managing a single module becomes difficult:

1. Create parent POM project
2. Extract platform-independent logic to `core` module
3. Create platform-specific modules (`bukkit`, `bungee`, etc.)
4. Optionally create public `api` module
5. Update dependencies between modules
6. Move adapters to platform modules

## Next Steps

Now that you understand project organization:

- Learn about [Dependency Injection](../core/Dependency-Injection.md) patterns in depth
- Explore [Bean Registration](../core/Bean-Registration.md) options
- Read about [Configuration Files](../core/Configuration-Files.md) management
- Check [Testing Best Practices](../best-practices/Testing.md)

---

**See also:**
- [Quick Start](Quick-Start.md) - Build your first plugin
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - How beans are created
- [Multi-Implementation](../core/Multi-Implementation.md) - Managing multiple implementations
- [Project Architecture](../best-practices/Project-Architecture.md) - Advanced architectural patterns
