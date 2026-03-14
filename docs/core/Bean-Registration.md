# Bean Registration

Bean registration is how you tell Tubing's IoC container which classes should be managed as beans. This guide covers the `@IocBean` annotation, bean naming, scopes, priority, and best practices for registering different types of components.

## What is a Bean?

In Tubing, a **bean** is any object managed by the IoC container. Beans are:
- Automatically instantiated when needed
- Have their dependencies automatically injected
- Created as singletons (one instance per type)
- Available for injection into other beans

**Not everything needs to be a bean:**
- **Domain models** (data classes like `Home`, `Player`, `User`) should NOT be beans
- **DTOs** (data transfer objects) should NOT be beans
- **Value objects** (like `Location`, `ItemStack`) should NOT be beans
- **Services, repositories, managers, handlers** SHOULD be beans

## The @IocBean Annotation

The `@IocBean` annotation marks a class for automatic discovery and registration by the container.

### Basic Usage

```java
@IocBean
public class PlayerService {

    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public void savePlayer(Player player) {
        repository.save(player);
    }
}
```

**Key points:**
- Add `@IocBean` to the class declaration
- Tubing will automatically discover this class during startup
- The class must be in your plugin's package or a sub-package
- Dependencies are injected via the constructor

### Annotation Attributes

The `@IocBean` annotation has three attributes:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IocBean {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;
}
```

## When to Use @IocBean

Use `@IocBean` for classes that:

### 1. Provide Services

Services contain business logic and orchestrate operations:

```java
@IocBean
public class HomeService {
    private final HomeRepository repository;
    private final PermissionService permissions;

    public HomeService(HomeRepository repository, PermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    public void createHome(Player player, String name, Location location) {
        if (permissions.hasPermission(player, "homes.create")) {
            repository.save(new Home(player.getUniqueId(), name, location));
        }
    }
}
```

### 2. Implement Repositories

Repositories handle data persistence:

```java
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    private final Database database;

    public DatabasePlayerRepository(Database database) {
        this.database = database;
    }

    @Override
    public PlayerData load(UUID playerId) {
        return database.query("SELECT * FROM players WHERE id = ?", playerId);
    }

    @Override
    public void save(PlayerData data) {
        database.execute("INSERT INTO players ...", data);
    }
}
```

### 3. Manage Resources

Managers coordinate multiple components:

```java
@IocBean
public class CacheManager {
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    @ConfigProperty("cache.ttl-minutes")
    private int ttlMinutes;

    public void put(UUID id, PlayerData data) {
        cache.put(id, data);
    }

    public Optional<PlayerData> get(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }
}
```

### 4. Provide Utilities

Utility classes that need dependencies or configuration:

```java
@IocBean
public class MessageFormatter {
    private final PlaceholderService placeholders;

    @ConfigProperty("messages.prefix")
    private String prefix;

    public MessageFormatter(PlaceholderService placeholders) {
        this.placeholders = placeholders;
    }

    public String format(Player player, String message) {
        return prefix + placeholders.replace(player, message);
    }
}
```

### 5. Handle Events or Commands

Event handlers and command processors (often with platform-specific annotations):

```java
@IocBean
@IocBukkitListener
public class PlayerJoinListener implements Listener {
    private final PlayerService playerService;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerService.loadPlayer(event.getPlayer());
    }
}
```

### When NOT to Use @IocBean

**Don't use @IocBean for:**

**Domain Models:**
```java
// NO @IocBean - this is a data class
public class Home {
    private final UUID ownerId;
    private final String name;
    private final Location location;

    public Home(UUID ownerId, String name, Location location) {
        this.ownerId = ownerId;
        this.name = name;
        this.location = location;
    }

    // Getters...
}
```

**DTOs and Value Objects:**
```java
// NO @IocBean - pure data transfer
public class PlayerStats {
    private int kills;
    private int deaths;
    private double kdr;

    // Constructors, getters, setters...
}
```

**Static Utilities:**
```java
// NO @IocBean - static utilities don't need DI
public class StringUtils {
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
```

**Interfaces:**
```java
// NO @IocBean on interfaces
public interface PlayerRepository {
    PlayerData load(UUID playerId);
    void save(PlayerData data);
}

// @IocBean goes on the implementation
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    // Implementation...
}
```

## Bean Naming and Identification

Beans are identified by their class type, not by a string name.

### Type-Based Identification

```java
@IocBean
public class PlayerService {
    // Identified by PlayerService.class
}

// Inject by type
@IocBean
public class PlayerCommand {
    private final PlayerService playerService;

    public PlayerCommand(PlayerService playerService) {
        // Container finds bean by PlayerService.class
        this.playerService = playerService;
    }
}
```

### Interface-Based Identification

When depending on interfaces, the container finds implementations:

```java
public interface NotificationService {
    void notify(Player player, String message);
}

@IocBean
public class BossBarNotificationService implements NotificationService {
    @Override
    public void notify(Player player, String message) {
        // Implementation...
    }
}

@IocBean
public class PlayerManager {
    private final NotificationService notifications;

    // Container finds BossBarNotificationService (only implementation)
    public PlayerManager(NotificationService notifications) {
        this.notifications = notifications;
    }
}
```

### Multiple Implementations

If multiple beans implement an interface, use `@IocMultiProvider`:

```java
public interface RewardHandler {
    void handleReward(Player player, Reward reward);
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler {
    // Implementation...
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemRewardHandler implements RewardHandler {
    // Implementation...
}

@IocBean
public class RewardService {
    private final List<RewardHandler> handlers;

    // Receives all RewardHandler implementations as a list
    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers) {
        this.handlers = handlers;
    }
}
```

See [Multi-Implementation](Multi-Implementation.md) for more details.

## Bean Scopes (Singleton Behavior)

All beans in Tubing are **singletons** by default. This means:

### Single Instance Per Type

```java
@IocBean
public class CacheService {
    private final Map<String, Object> cache = new HashMap<>();

    public void put(String key, Object value) {
        cache.put(key, value);
    }
}

@IocBean
public class ServiceA {
    private final CacheService cache;

    public ServiceA(CacheService cache) {
        this.cache = cache;  // Same instance
    }
}

@IocBean
public class ServiceB {
    private final CacheService cache;

    public ServiceB(CacheService cache) {
        this.cache = cache;  // Same instance as in ServiceA
    }
}
```

**Both `ServiceA` and `ServiceB` receive the exact same `CacheService` instance.**

### Lifecycle

```
Plugin Startup
      ↓
Bean Discovery
      ↓
Bean Instantiation (singleton created)
      ↓
[Bean available for injection]
      ↓
Plugin Shutdown
      ↓
Bean Garbage Collected
```

Each bean is created exactly once during container initialization and reused throughout the plugin's lifetime.

### Why Singleton Scope?

**1. Performance:**
- No repeated object creation
- Reduced memory usage
- Faster dependency resolution

**2. State Sharing:**
- Shared caches work naturally
- Consistent state across components
- No synchronization needed between injection points

**3. Simplicity:**
- Predictable behavior
- No scope configuration needed
- Thread-safe by default (if beans are thread-safe)

### Thread Safety Considerations

Since beans are singletons shared across threads, make them thread-safe when necessary:

```java
@IocBean
public class PlayerDataCache {
    // Use concurrent collections for thread safety
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public void put(UUID id, PlayerData data) {
        cache.put(id, data);
    }

    public PlayerData get(UUID id) {
        return cache.get(id);
    }
}
```

**Best practices:**
- Use immutable objects when possible
- Use thread-safe collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`)
- Synchronize only when necessary
- Prefer stateless beans for naturally thread-safe code

## Priority Attribute for Ordering

The `priority` attribute controls bean instantiation order.

### Basic Priority

```java
@IocBean(priority = true)
public class DatabaseService {
    // Created before non-priority beans
}

@IocBean  // priority defaults to false
public class PlayerService {
    // Created after priority beans (if dependencies allow)
}
```

### Priority Levels

Tubing uses a simple boolean priority system:

- **`priority = true`**: High priority, instantiated early
- **`priority = false`**: Normal priority (default)

### Real-World Example: ConfigurationLoader

The most important use of priority is `ConfigurationLoader`:

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // Must be created FIRST so config is available for other beans
}

@IocBean
public class MessageService {
    @ConfigProperty("messages.prefix")
    private String prefix;  // ConfigurationLoader must exist first

    // ...
}
```

Without `priority = true`, `ConfigurationLoader` might be created after beans that need configuration, causing errors.

### When to Use Priority

Use `priority = true` for:

**1. Configuration loaders:**
```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // Loads configuration files
}
```

**2. Database connections:**
```java
@IocBean(priority = true)
public class DatabaseConnectionPool {
    // Establishes database connection needed by repositories
}
```

**3. Core infrastructure:**
```java
@IocBean(priority = true)
public class PluginMetrics {
    // Initializes metrics system
}
```

**4. Logging infrastructure:**
```java
@IocBean(priority = true)
public class LoggingService {
    // Sets up logging before other services
}
```

### When NOT to Use Priority

**Don't use priority for:**
- Regular services (let dependency resolution handle order)
- Repositories (they'll be created when needed)
- Command handlers
- Event listeners
- Most application logic

**Why:** The container automatically determines creation order based on dependencies. Priority is only needed when a bean provides infrastructure that other beans use during their own initialization.

### Priority and Dependencies

Priority influences order, but **dependencies always take precedence**:

```java
@IocBean(priority = false)
public class ServiceA {
    // No dependencies
}

@IocBean(priority = true)
public class ServiceB {
    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}
```

**Creation order:**
1. `ServiceA` (required by ServiceB, even though not priority)
2. `ServiceB` (priority bean, but needs ServiceA first)

**Priority affects order among beans at the same dependency level, not absolute order.**

## Examples of Different Bean Types

### Services

Business logic orchestration:

```java
@IocBean
public class HomeService {
    private final HomeRepository repository;
    private final PermissionService permissions;
    private final MessageService messages;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    public HomeService(HomeRepository repository,
                      PermissionService permissions,
                      MessageService messages) {
        this.repository = repository;
        this.permissions = permissions;
        this.messages = messages;
    }

    public void createHome(Player player, String name, Location location) {
        if (!permissions.hasPermission(player, "homes.create")) {
            messages.sendError(player, "homes.no-permission");
            return;
        }

        if (repository.countHomes(player) >= maxHomes) {
            messages.sendError(player, "homes.max-reached");
            return;
        }

        repository.save(new Home(player.getUniqueId(), name, location));
        messages.sendSuccess(player, "homes.created", name);
    }
}
```

### Repositories

Data access layer:

```java
@IocBean
public class MySQLHomeRepository implements HomeRepository {
    private final Database database;

    public MySQLHomeRepository(Database database) {
        this.database = database;
    }

    @Override
    public Home findByOwnerAndName(UUID ownerId, String name) {
        return database.queryOne("SELECT * FROM homes WHERE owner_id = ? AND name = ?",
            rs -> new Home(
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("name"),
                deserializeLocation(rs.getString("location"))
            ),
            ownerId.toString(), name
        );
    }

    @Override
    public List<Home> findAllByOwner(UUID ownerId) {
        return database.queryList("SELECT * FROM homes WHERE owner_id = ?",
            rs -> new Home(
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("name"),
                deserializeLocation(rs.getString("location"))
            ),
            ownerId.toString()
        );
    }

    @Override
    public void save(Home home) {
        database.execute(
            "INSERT INTO homes (owner_id, name, location) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE location = VALUES(location)",
            home.getOwnerId().toString(),
            home.getName(),
            serializeLocation(home.getLocation())
        );
    }
}
```

### Managers

Coordinate multiple components:

```java
@IocBean
public class PluginStateManager {
    private final ConfigurationLoader configLoader;
    private final DatabaseService database;
    private final CacheService cache;
    private boolean initialized = false;

    public PluginStateManager(ConfigurationLoader configLoader,
                             DatabaseService database,
                             CacheService cache) {
        this.configLoader = configLoader;
        this.database = database;
        this.cache = cache;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        database.connect();
        cache.warmUp();
        initialized = true;
    }

    public void shutdown() {
        cache.clear();
        database.disconnect();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
```

### Handlers

Process specific operations:

```java
@IocBean
@IocMultiProvider(CommandHandler.class)
public class HomeCommandHandler implements CommandHandler {
    private final HomeService homeService;
    private final Messages messages;

    public HomeCommandHandler(HomeService homeService, Messages messages) {
        this.homeService = homeService;
        this.messages = messages;
    }

    @Override
    public boolean canHandle(String command) {
        return command.equalsIgnoreCase("sethome") ||
               command.equalsIgnoreCase("delhome");
    }

    @Override
    public void handle(Player player, String command, String[] args) {
        if (command.equalsIgnoreCase("sethome")) {
            String homeName = args.length > 0 ? args[0] : "default";
            homeService.createHome(player, homeName, player.getLocation());
        } else if (command.equalsIgnoreCase("delhome")) {
            String homeName = args.length > 0 ? args[0] : "default";
            homeService.deleteHome(player, homeName);
        }
    }
}
```

### Validators

Validate data or operations:

```java
@IocBean
public class HomeValidator {
    @ConfigProperty("homes.name-pattern")
    private String namePattern;

    @ConfigProperty("homes.min-distance")
    private int minDistance;

    public ValidationResult validateName(String name) {
        if (name == null || name.isEmpty()) {
            return ValidationResult.error("Home name cannot be empty");
        }

        if (!name.matches(namePattern)) {
            return ValidationResult.error("Home name contains invalid characters");
        }

        return ValidationResult.success();
    }

    public ValidationResult validateLocation(Player player, Location location) {
        if (location.getWorld() == null) {
            return ValidationResult.error("Invalid world");
        }

        // Check minimum distance from other homes
        // ...

        return ValidationResult.success();
    }
}
```

### Utilities with Dependencies

Utilities that need other beans or configuration:

```java
@IocBean
public class Messages {
    private final PlaceholderService placeholders;
    private final ConfigurationLoader configLoader;

    @ConfigProperty("messages.prefix")
    private String prefix;

    public Messages(PlaceholderService placeholders,
                   ConfigurationLoader configLoader) {
        this.placeholders = placeholders;
        this.configLoader = configLoader;
    }

    public void send(CommandSender sender, String key, Object... args) {
        String message = configLoader.getConfigStringValue("messages." + key)
            .orElse(key);

        message = String.format(message, args);
        message = placeholders.replace(sender, message);

        sender.sendMessage(prefix + " " + message);
    }

    public void sendError(CommandSender sender, String key, Object... args) {
        send(sender, "error." + key, args);
    }

    public void sendSuccess(CommandSender sender, String key, Object... args) {
        send(sender, "success." + key, args);
    }
}
```

### Factories

Create complex objects:

```java
@IocBean
public class GuiFactory {
    private final InventoryMapper inventoryMapper;
    private final ItemStackMapper itemMapper;

    public GuiFactory(InventoryMapper inventoryMapper, ItemStackMapper itemMapper) {
        this.inventoryMapper = inventoryMapper;
        this.itemMapper = itemMapper;
    }

    public Inventory createHomeGui(Player player, List<Home> homes) {
        Inventory inventory = inventoryMapper.create("Player Homes", 54);

        for (int i = 0; i < homes.size() && i < 45; i++) {
            Home home = homes.get(i);
            ItemStack item = itemMapper.create(Material.BED)
                .name("&e" + home.getName())
                .lore(
                    "&7Location: " + formatLocation(home.getLocation()),
                    "&7Click to teleport"
                )
                .build();

            inventory.setItem(i, item);
        }

        return inventory;
    }
}
```

## Best Practices

### 1. Prefer Interfaces for Dependencies

```java
// Good - depend on interface
@IocBean
public class PlayerService {
    private final PlayerRepository repository;  // Interface

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - depend on implementation
@IocBean
public class PlayerService {
    private final MySQLPlayerRepository repository;  // Concrete class

    public PlayerService(MySQLPlayerRepository repository) {
        this.repository = repository;
    }
}
```

**Why:** Interfaces make code flexible, testable, and maintainable.

### 2. Keep Constructors Simple

```java
// Good - constructor only assigns dependencies
@IocBean
public class DatabaseService {
    private final String url;

    public DatabaseService(@ConfigProperty("database.url") String url) {
        this.url = url;
    }
}

// Bad - constructor does work
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("database.url") String url) {
        connect(url);          // Side effect
        createTables();        // Expensive operation
        loadAllData();         // Blocking I/O
    }
}
```

**Why:** Complex constructors make testing difficult and can cause initialization problems.

### 3. Use Final Fields

```java
// Good - immutable dependencies
@IocBean
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - mutable dependencies
@IocBean
public class PlayerService {
    private PlayerRepository repository;  // Not final

    public void setRepository(PlayerRepository repository) {
        this.repository = repository;  // Can be changed
    }
}
```

**Why:** Immutability prevents bugs and makes code easier to understand.

### 4. Avoid Circular Dependencies

```java
// Bad - circular dependency
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }  // Circular!
}

// Good - extract shared logic
@IocBean
public class SharedService {
    // Common functionality
}

@IocBean
public class ServiceA {
    public ServiceA(SharedService shared) { }
}

@IocBean
public class ServiceB {
    public ServiceB(SharedService shared) { }
}
```

**Why:** Circular dependencies indicate design problems and prevent container initialization.

### 5. Use Priority Sparingly

```java
// Good - only for infrastructure
@IocBean(priority = true)
public class ConfigurationLoader {
    // Loads configuration files
}

// Bad - unnecessary priority
@IocBean(priority = true)  // Not needed!
public class PlayerService {
    // Regular service
}
```

**Why:** Let the container handle dependency order automatically. Priority is only for special cases.

### 6. Document Complex Beans

```java
/**
 * Manages player home locations with database persistence.
 *
 * Features:
 * - Per-player home limits from configuration
 * - Permission-based access control
 * - Database-backed storage
 *
 * Dependencies:
 * - HomeRepository for data access
 * - PermissionService for access control
 * - MessageService for player feedback
 */
@IocBean
public class HomeService {
    // Implementation...
}
```

**Why:** Clear documentation helps other developers understand the bean's purpose and dependencies.

## Common Patterns

### Pattern: Service Layer

```java
// Repository interface
public interface HomeRepository {
    Home find(UUID owner, String name);
    void save(Home home);
    void delete(Home home);
}

// Repository implementation
@IocBean
public class DatabaseHomeRepository implements HomeRepository {
    private final Database database;

    public DatabaseHomeRepository(Database database) {
        this.database = database;
    }

    // Implementation...
}

// Service
@IocBean
public class HomeService {
    private final HomeRepository repository;

    public HomeService(HomeRepository repository) {
        this.repository = repository;
    }

    // Business logic...
}
```

### Pattern: Strategy with Multiple Implementations

```java
public interface StorageStrategy {
    void save(String key, Object value);
    Object load(String key);
}

@IocBean
@IocMultiProvider(StorageStrategy.class)
public class DatabaseStorage implements StorageStrategy {
    // Database implementation
}

@IocBean
@IocMultiProvider(StorageStrategy.class)
public class FileStorage implements StorageStrategy {
    // File implementation
}

@IocBean
public class DataManager {
    private final List<StorageStrategy> strategies;

    public DataManager(@IocMulti(StorageStrategy.class) List<StorageStrategy> strategies) {
        this.strategies = strategies;
    }
}
```

### Pattern: Facade

```java
@IocBean
public class PlayerManagerFacade {
    private final PlayerDataService dataService;
    private final PlayerStatsService statsService;
    private final PlayerPermissionService permissionService;

    public PlayerManagerFacade(PlayerDataService dataService,
                              PlayerStatsService statsService,
                              PlayerPermissionService permissionService) {
        this.dataService = dataService;
        this.statsService = statsService;
        this.permissionService = permissionService;
    }

    public void setupNewPlayer(Player player) {
        dataService.createPlayerData(player);
        statsService.initializeStats(player);
        permissionService.applyDefaults(player);
    }
}
```

## Next Steps

Now that you understand bean registration:

- Learn about [Bean Lifecycle](Bean-Lifecycle.md) to understand when beans are created
- Explore [Dependency Injection](Dependency-Injection.md) for injection patterns
- Read [Bean Providers](Bean-Providers.md) for factory method patterns
- Check [Multi-Implementation](Multi-Implementation.md) for handling multiple beans
- Review [Conditional Beans](Conditional-Beans.md) for conditional registration

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin with beans
- [IoC Container](IoC-Container.md) - Container fundamentals
- [Configuration Injection](Configuration-Injection.md) - Inject configuration values
- [Project Structure](../getting-started/Project-Structure.md) - Organize your beans
