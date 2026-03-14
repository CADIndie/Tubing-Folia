# Frequently Asked Questions

This FAQ covers common questions about Tubing, from basic setup and usage to advanced topics like dependency injection, configuration management, and performance optimization.

## Table of Contents

- [Basic Setup and Usage](#basic-setup-and-usage)
- [Dependency Injection Concepts](#dependency-injection-concepts)
- [Configuration Management](#configuration-management)
- [Commands and Listeners](#commands-and-listeners)
- [GUI Framework](#gui-framework)
- [Performance Concerns](#performance-concerns)
- [Migration from Traditional Plugins](#migration-from-traditional-plugins)
- [Best Practices](#best-practices)

## Basic Setup and Usage

### Q: What is Tubing?

**A:** Tubing is an Inversion of Control (IoC) and Dependency Injection framework for Minecraft server plugins. It provides annotation-based dependency injection, configuration management, and platform-specific integrations for Bukkit, BungeeCord, and Velocity.

### Q: Which platforms does Tubing support?

**A:** Tubing supports three major Minecraft server platforms:
- **Bukkit/Spigot/Paper** - For traditional Minecraft servers
- **BungeeCord/Waterfall** - For proxy servers
- **Velocity** - For modern proxy servers

### Q: How do I add Tubing to my project?

**A:** Add the StaffPlusPlus repository and Tubing dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>staffplusplus-repo</id>
        <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
    </repository>
</repositories>

<dependencies>
    <!-- For Bukkit -->
    <dependency>
        <groupId>be.garagepoort.mcioc</groupId>
        <artifactId>tubing-bukkit</artifactId>
        <version>7.5.6</version>
    </dependency>
</dependencies>
```

See the [Installation Guide](../getting-started/Installation.md) for complete setup instructions.

### Q: What's the minimal code needed to start using Tubing?

**A:** Just extend the platform-specific Tubing class:

```java
// Bukkit
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        getLogger().info("Plugin enabled!");
    }
}
```

That's it! The IoC container is automatically initialized. Add `@IocBean` to your classes and Tubing handles the rest.

### Q: Do I need to manually register my beans, commands, or listeners?

**A:** No! Tubing automatically discovers and registers:
- Beans annotated with `@IocBean`
- Commands annotated with `@IocBukkitCommandHandler`
- Event listeners annotated with `@IocBukkitListener`
- GUI controllers annotated with `@GuiController`

Just add the annotations and Tubing does the rest.

### Q: How long does container initialization take?

**A:** Container initialization typically completes in 20-100ms for small to medium plugins:
- Package scanning: 10-50ms
- Bean discovery: 1-5ms
- Bean instantiation: 1-5ms per bean
- Configuration injection: 1-10ms

The overhead is negligible compared to plugin functionality.

## Dependency Injection Concepts

### Q: What is Dependency Injection and why should I use it?

**A:** Dependency Injection (DI) is a pattern where objects receive their dependencies from external sources rather than creating them internally.

**Without DI:**
```java
public class HomeService {
    private final PlayerRepository repository = new PlayerRepository(); // Tight coupling
}
```

**With DI:**
```java
@IocBean
public class HomeService {
    private final PlayerRepository repository;

    public HomeService(PlayerRepository repository) { // Injected
        this.repository = repository;
    }
}
```

**Benefits:**
- Loose coupling between components
- Easy to test (inject mocks)
- Flexible implementations
- Clear dependencies
- No singleton patterns needed

### Q: How does constructor injection work?

**A:** Tubing uses constructor injection as its primary DI mechanism:

```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository;
    private final MessageService messages;

    // Dependencies automatically injected
    public PlayerService(PlayerRepository repository, MessageService messages) {
        this.repository = repository;
        this.messages = messages;
    }
}
```

The container automatically:
1. Discovers the class needs `PlayerRepository` and `MessageService`
2. Creates or retrieves those beans
3. Calls the constructor with the dependencies
4. Caches the instance as a singleton

### Q: Can I inject interfaces instead of concrete classes?

**A:** Yes! This is recommended for loose coupling:

```java
// Define interface
public interface PlayerRepository {
    PlayerData load(UUID playerId);
}

// Implement interface
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    @Override
    public PlayerData load(UUID playerId) { /* ... */ }
}

// Inject interface
@IocBean
public class PlayerService {
    private final PlayerRepository repository; // Interface!

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}
```

Tubing automatically finds the implementation and injects it. Swapping implementations requires no code changes.

### Q: What happens if I have a circular dependency?

**A:** Tubing detects circular dependencies at startup and fails with a clear error message:

```java
@IocBean
public class ServiceA {
    public ServiceA(ServiceB b) { } // A needs B
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA a) { } // B needs A - CIRCULAR!
}
```

**Error:**
```
IocException: Circular dependency detected: ServiceA -> ServiceB -> ServiceA
```

**Solution:** Refactor to break the cycle:
- Extract common logic to a third service
- Use event-driven communication
- Rethink responsibilities

See [Dependency Injection](../core/Dependency-Injection.md) for resolution strategies.

### Q: How do I inject multiple implementations of the same interface?

**A:** Use the `@IocMulti` annotation:

```java
public interface PermissionProvider {
    boolean hasPermission(Player player, String permission);
}

@IocBean
public class DefaultPermissionProvider implements PermissionProvider { }

@IocBean
public class VaultPermissionProvider implements PermissionProvider { }

@IocBean
public class PermissionService {
    private final List<PermissionProvider> providers;

    public PermissionService(@IocMulti(PermissionProvider.class)
                            List<PermissionProvider> providers) {
        this.providers = providers;
    }
}
```

Tubing injects a list containing all implementations.

### Q: Does Tubing support field injection?

**A:** No, Tubing only supports constructor injection for service dependencies. This is intentional:

**Why constructor injection is better:**
- Fields can be `final` (immutable)
- Dependencies are explicit
- Easy to test (no framework needed)
- No null references
- Circular dependencies detected early

For configuration, use `@ConfigProperty` field injection (different from service injection).

## Configuration Management

### Q: How do I inject configuration values into my beans?

**A:** Use the `@ConfigProperty` annotation:

```java
@IocBean
public class HomeService {
    @ConfigProperty("homes.max-homes")
    private int maxHomes = 5; // Default value

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds = 60;
}
```

**config.yml:**
```yaml
homes:
  max-homes: 10
  cooldown-seconds: 120
```

Values are automatically injected after bean construction.

### Q: Can I use a prefix to avoid repeating paths?

**A:** Yes! Use `@ConfigProperties` on the class:

```java
@IocBean
@ConfigProperties("homes")
public class HomeService {
    @ConfigProperty("max-homes") // Becomes "homes.max-homes"
    private int maxHomes = 5;

    @ConfigProperty("cooldown-seconds") // Becomes "homes.cooldown-seconds"
    private int cooldownSeconds = 60;
}
```

### Q: How do I handle optional configuration values?

**A:** Provide default values:

```java
@IocBean
public class FeatureService {
    @ConfigProperty("feature.enabled")
    private boolean enabled = true; // Default if not configured

    @ConfigProperty("feature.max-uses")
    private Integer maxUses = null; // null if not configured (use wrapper type!)
}
```

If the property doesn't exist in the config, the default value is kept.

### Q: How do I make a configuration property required?

**A:** Use `required = true`:

```java
@IocBean
public class DatabaseConfig {
    @ConfigProperty(value = "database.host", required = true)
    private String host;

    @ConfigProperty(
        value = "license.key",
        required = true,
        error = "License key is required! Add 'license.key' to config.yml"
    )
    private String licenseKey;
}
```

Plugin startup fails if required properties are missing.

### Q: Can I inject complex objects from YAML?

**A:** Yes! Use `@ConfigEmbeddedObject`:

```java
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;
}

@IocBean
public class AppConfig {
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings database;
}
```

**config.yml:**
```yaml
database:
  host: "localhost"
  port: 3306
```

### Q: How do I inject lists of objects?

**A:** Use `@ConfigObjectList`:

```java
public class ShopItem {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("price")
    private int price;
}

@IocBean
public class ShopConfig {
    @ConfigProperty("items")
    @ConfigObjectList(ShopItem.class)
    private List<ShopItem> items;
}
```

**config.yml:**
```yaml
items:
  - name: "Diamond Sword"
    price: 500
  - name: "Iron Armor"
    price: 250
```

### Q: Can I transform configuration values during injection?

**A:** Yes! Use `@ConfigTransformer`:

```java
public enum Difficulty {
    EASY, NORMAL, HARD
}

@IocBean
public class GameConfig {
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;
}
```

**config.yml:**
```yaml
difficulty: "HARD"
```

Built-in transformers include `ToEnum`, `ToLowerCase`, and `ToMaterials`. Create custom transformers by implementing `IConfigTransformer`.

### Q: How do I reload configuration at runtime?

**A:** Configuration is loaded once at startup. To reload:

```java
@IocBean
public class ReloadService {
    private final ITubingPlugin plugin;

    public void reload() {
        plugin.reload(); // Reloads container and reinjects all config
    }
}
```

Note: This recreates all beans, so use sparingly.

## Commands and Listeners

### Q: How do I create a command with Tubing?

**A:** Use the `@IocBukkitCommandHandler` annotation:

```java
@IocBukkitCommandHandler("home")
public class HomeCommand extends AbstractCmd {
    private final HomeService homeService;

    public HomeCommand(HomeService homeService,
                      CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService) {
        super(exceptionHandler, permissionService);
        this.homeService = homeService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;
        homeService.teleportHome(player, args.length > 0 ? args[0] : "default");
        return true;
    }
}
```

The command is automatically registered with Bukkit.

### Q: Do I still need to add commands to plugin.yml?

**A:** It's optional. Commands work without `plugin.yml` entries, but you can add them for descriptions and aliases:

```yaml
commands:
  home:
    description: "Teleport to your home"
    usage: "/home [name]"
    aliases: [h]
```

### Q: How do I create subcommands?

**A:** Use `@IocBukkitSubCommand`:

```java
@IocBukkitCommandHandler("admin")
public class AdminRootCommand extends AbstractCmd { /* ... */ }

@IocBukkitSubCommand(
    parentCommand = "admin",
    value = "reload",
    description = "Reload the plugin"
)
public class AdminReloadSubCommand extends AbstractCmd {
    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        plugin.reload();
        sender.sendMessage("Reloaded!");
        return true;
    }
}
```

This creates `/admin reload` automatically.

### Q: How do I add tab completion?

**A:** Implement the `TabCompleter` interface:

```java
@IocBukkitCommandHandler("home")
public class HomeCommand extends AbstractCmd implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                     String alias, String[] args) {
        if (args.length == 1) {
            return homeService.getPlayerHomes((Player) sender)
                .stream()
                .map(Home::getName)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
```

### Q: How do I register event listeners?

**A:** Use the `@IocBukkitListener` annotation:

```java
@IocBukkitListener
public class PlayerListener implements Listener {
    private final PlayerService playerService;

    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerService.handleJoin(event.getPlayer());
    }
}
```

The listener is automatically registered with Bukkit.

### Q: Can I conditionally register commands or listeners?

**A:** Yes! Use `conditionalOnProperty`:

```java
@IocBukkitCommandHandler(value = "premium", conditionalOnProperty = "features.premium-commands")
public class PremiumCommand extends AbstractCmd { /* ... */ }

@IocBukkitListener(conditionalOnProperty = "features.advanced-tracking")
public class TrackingListener implements Listener { /* ... */ }
```

The command/listener is only registered if the property is set to `true` in the config.

## GUI Framework

### Q: What is the Tubing GUI framework?

**A:** A powerful MVC-style GUI system for Bukkit that provides:
- Controller-based action handling
- Template support with Freemarker
- Automatic action routing and parameter binding
- Async operation support
- Built-in navigation history

### Q: How do I create a GUI controller?

**A:** Use the `@GuiController` annotation:

```java
@GuiController
public class ShopController {
    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GuiAction("shop:main")
    public TubingGui showShop(Player player) {
        return new TubingGui.Builder("Shop", 54)
            .addItem(/* ... */)
            .build();
    }
}
```

### Q: How do I handle button clicks?

**A:** Create an action method and reference it in your GUI items:

```java
@GuiController
public class ShopController {

    @GuiAction("shop:buy")
    public TubingGui buyItem(Player player,
                            @GuiParam("item") String itemId,
                            @GuiParam("price") int price) {
        // Handle purchase
        if (economyService.withdraw(player, price)) {
            giveItem(player, itemId);
            player.sendMessage("Purchase successful!");
        }
        return showShop(player); // Return to shop GUI
    }
}

// In GUI builder
.withLeftClickAction("shop:buy?item=diamond_sword&price=500")
```

### Q: How do I pass parameters to actions?

**A:** Use query string syntax:

```java
// In GUI item
.withLeftClickAction("shop:buy?item=diamond&quantity=5")

// In controller
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                        @GuiParam("item") String item,
                        @GuiParam("quantity") int quantity) {
    // item = "diamond"
    // quantity = 5 (automatically converted to int)
}
```

Parameters are automatically type-converted.

### Q: How do I load data asynchronously in a GUI?

**A:** Use `AsyncGui`:

```java
@GuiAction("stats:view")
public AsyncGui<TubingGui> viewStats(Player player) {
    return AsyncGui.async(() -> {
        // This runs on async thread - no server blocking!
        PlayerStats stats = database.loadStats(player.getUniqueId());

        // Build GUI with loaded data
        return new TubingGui.Builder("Your Stats", 27)
            .addItem(/* ... */)
            .build();
    });
}
```

The GUI opens automatically when data is ready.

### Q: How does the back button work?

**A:** Tubing maintains a navigation history. Use the built-in `BACK` action:

```java
// In GUI
.withLeftClickAction(TubingGuiActions.BACK)

// Or return from controller
@GuiAction("purchase:confirm")
public GuiActionReturnType confirmPurchase(Player player) {
    // Process purchase
    return GuiActionReturnType.BACK; // Navigate to previous GUI
}
```

### Q: How do I use GUI templates?

**A:** Create a Freemarker template file and return `GuiTemplate`:

**templates/shop.xml:**
```xml
<gui title="Shop" size="54">
    <#list items as item>
        <item slot="${item_index}"
              leftClickAction="shop:buy?item=${item.id}">
            <itemStack material="${item.material}" name="${item.name}"/>
        </item>
    </#list>
</gui>
```

**Controller:**
```java
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player) {
    List<ShopItem> items = shopService.getItems();
    return GuiTemplate.template("shop", Map.of("items", items));
}
```

Templates are cached for performance.

## Performance Concerns

### Q: Does Tubing add significant overhead?

**A:** No. Container initialization is 20-100ms, bean lookups are O(1) HashMap access (~0.01ms), and runtime overhead is negligible. Using constructor injection eliminates even that small overhead.

### Q: Should I worry about bean lookup performance?

**A:** No. Bean lookups use HashMap internally (O(1)) and take ~0.01ms. However, best practice is to use constructor injection rather than repeated lookups:

```java
// Good - inject once
@IocBukkitListener
public class PlayerListener implements Listener {
    private final PlayerService service; // Injected once

    public PlayerListener(PlayerService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer()); // Direct field access
    }
}

// Avoid - repeated lookups
@EventHandler
public void onJoin(PlayerJoinEvent event) {
    PlayerService service = container.get(PlayerService.class); // Unnecessary
    service.handleJoin(event.getPlayer());
}
```

### Q: How can I optimize configuration loading?

**A:** Configuration loading is already optimized, but you can:

1. Combine related config files (fewer files = less I/O)
2. Disable auto-update for static files:
   ```java
   new ConfigurationFile("messages.yml", "messages", true) // ignoreUpdater = true
   ```
3. Use `@ConfigProperty` field injection (faster than ConfigurationLoader lookups)

### Q: How do I prevent blocking the main thread?

**A:** Use async operations for blocking tasks:

```java
// Always async for I/O
@IocBean
public class DataService {
    private final ITubingBukkitUtil tubingUtil;

    public void saveAsync(PlayerData data) {
        tubingUtil.runAsync(() -> {
            database.save(data); // Async thread
        });
    }
}

// Async GUI actions
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData(Player player) {
    return AsyncGui.async(() -> {
        Data data = database.load(player); // Async thread
        return buildGui(data);
    });
}
```

**Never** do database queries, file I/O, or HTTP requests on the main thread.

### Q: Should I do expensive work in constructors?

**A:** No! Constructors should be fast (<1ms):

```java
// Bad - blocks container initialization
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        connect(url);          // Blocks startup
        loadSchema();          // Expensive I/O
    }
}

// Good - defer to AfterIocLoad
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        this.url = url; // Just store config
    }

    public void connect() { /* called explicitly when needed */ }
}

@TubingConfiguration
public class StartupConfig {
    @AfterIocLoad
    public static void init(DatabaseService db) {
        db.connect(); // After all beans ready
    }
}
```

### Q: How do I implement caching in Tubing?

**A:** Store cached data in singleton beans:

```java
@IocBean
public class PlayerDataCache {
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final PlayerDataRepository repository;

    public PlayerDataCache(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public PlayerData get(UUID playerId) {
        return cache.computeIfAbsent(playerId, repository::load);
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }
}

// Inject into other beans
@IocBean
public class PlayerService {
    private final PlayerDataCache cache;

    public PlayerService(PlayerDataCache cache) {
        this.cache = cache;
    }
}
```

The bean is a singleton, so the cache is shared across all uses.

## Migration from Traditional Plugins

### Q: How hard is it to migrate my existing plugin to Tubing?

**A:** Migration is straightforward and can be done incrementally:

1. Add Tubing dependencies
2. Extend `TubingBukkitPlugin` instead of `JavaPlugin`
3. Convert singletons to `@IocBean` classes
4. Replace manual config loading with `@ConfigProperty`
5. Convert commands to `@IocBukkitCommandHandler`
6. Convert listeners to `@IocBukkitListener`

You can migrate one feature at a time - old and new code coexist.

### Q: How do I replace singleton patterns?

**A:** Remove `getInstance()` methods and use dependency injection:

**Before:**
```java
public class PlayerManager {
    private static PlayerManager instance;

    public static PlayerManager getInstance() {
        return instance;
    }

    public void init() {
        instance = this;
    }
}

// Usage
PlayerManager.getInstance().doSomething();
```

**After:**
```java
@IocBean
public class PlayerManager {
    // No singleton code needed!
}

// Usage - inject into constructor
@IocBean
public class SomeService {
    private final PlayerManager manager;

    public SomeService(PlayerManager manager) {
        this.manager = manager;
    }

    public void method() {
        manager.doSomething();
    }
}
```

### Q: How do I replace manual configuration loading?

**A:** Replace manual loading with `@ConfigProperty`:

**Before:**
```java
public class HomeService {
    private int maxHomes;

    public void loadConfig(FileConfiguration config) {
        this.maxHomes = config.getInt("homes.max-homes", 5);
    }
}
```

**After:**
```java
@IocBean
public class HomeService {
    @ConfigProperty("homes.max-homes")
    private int maxHomes = 5;

    // Automatically injected - no manual loading!
}
```

### Q: Can I gradually migrate my plugin?

**A:** Yes! You can keep existing code and gradually introduce Tubing:

```java
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        // Old code still works
        getServer().getPluginManager().registerEvents(new OldListener(), this);

        // New Tubing beans automatically registered
        // (listeners with @IocBukkitListener, etc.)

        // Access Tubing beans from old code
        PlayerService service = getIocContainer().get(PlayerService.class);
    }
}
```

### Q: What if I have static utility classes?

**A:** Convert them to injected services:

**Before:**
```java
public class MessageUtil {
    public static void send(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}

// Usage
MessageUtil.send(player, "&aHello!");
```

**After:**
```java
@IocBean
public class MessageService {
    @ConfigProperty("messages.prefix")
    private String prefix = "&8[&aServer&8]&r ";

    public void send(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }
}

// Usage - inject into constructor
@IocBean
public class SomeClass {
    private final MessageService messages;

    public SomeClass(MessageService messages) {
        this.messages = messages;
    }

    public void method(Player player) {
        messages.send(player, "&aHello!");
    }
}
```

## Best Practices

### Q: Should I use constructor injection or field injection?

**A:** Use **constructor injection for services**, **field injection for configuration**:

```java
@IocBean
@ConfigProperties("homes")
public class HomeService {
    // Field injection for configuration
    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    // Constructor injection for services
    private final PlayerRepository repository;
    private final MessageService messages;

    public HomeService(PlayerRepository repository, MessageService messages) {
        this.repository = repository;
        this.messages = messages;
    }
}
```

### Q: Should I depend on interfaces or concrete classes?

**A:** Always depend on interfaces when possible:

```java
// Good - depends on abstraction
@IocBean
public class HomeService {
    private final PlayerRepository repository; // Interface

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - depends on implementation
@IocBean
public class HomeService {
    private final DatabasePlayerRepository repository; // Concrete class

    public HomeService(DatabasePlayerRepository repository) {
        this.repository = repository;
    }
}
```

Interfaces enable testing, flexibility, and future changes.

### Q: How should I organize my project structure?

**A:** Organize by feature/layer:

```
src/main/java/com/example/myplugin/
├── MyPlugin.java
├── commands/
│   ├── HomeCommand.java
│   └── WarpCommand.java
├── listeners/
│   ├── PlayerListener.java
│   └── BlockListener.java
├── services/
│   ├── HomeService.java
│   ├── WarpService.java
│   └── PlayerDataService.java
├── repositories/
│   ├── HomeRepository.java
│   └── PlayerDataRepository.java
├── models/
│   ├── Home.java
│   └── PlayerData.java
└── gui/
    ├── controllers/
    │   └── MenuController.java
    └── templates/
        └── menu.xml
```

See [Project Structure](../getting-started/Project-Structure.md) for details.

### Q: How many dependencies is too many?

**A:** If a class has more than 5-7 dependencies, it likely violates Single Responsibility Principle:

```java
// Warning sign - too many dependencies
@IocBean
public class MegaService {
    public MegaService(ServiceA a, ServiceB b, ServiceC c, ServiceD d,
                      ServiceE e, ServiceF f, ServiceG g, ServiceH h) {
        // Consider splitting into smaller, focused classes
    }
}
```

Split into smaller services with clear responsibilities.

### Q: Should I validate configuration values?

**A:** Yes! Add validation in a `@PostConstruct` method or constructor:

```java
@IocBean
public class RangeConfig {
    @ConfigProperty("min-value")
    private int minValue = 0;

    @ConfigProperty("max-value")
    private int maxValue = 100;

    @PostConstruct
    public void validate() {
        if (minValue >= maxValue) {
            throw new ConfigurationException("min-value must be less than max-value");
        }
        if (minValue < 0) {
            throw new ConfigurationException("min-value must be positive");
        }
    }
}
```

### Q: How do I write testable code with Tubing?

**A:** Constructor injection makes testing trivial:

```java
// Production code
@IocBean
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public void savePlayer(Player player) {
        repository.save(player.getUniqueId(), getData(player));
    }
}

// Test code - no Tubing needed!
public class PlayerServiceTest {
    @Test
    public void testSavePlayer() {
        // Create mock
        PlayerRepository mockRepo = mock(PlayerRepository.class);

        // Inject via constructor
        PlayerService service = new PlayerService(mockRepo);

        // Test
        Player player = mock(Player.class);
        service.savePlayer(player);

        // Verify
        verify(mockRepo).save(any(), any());
    }
}
```

No IoC container needed in tests - just pass mocks to constructors!

### Q: What should I avoid doing in constructors?

**A:** Keep constructors simple - only assign dependencies:

```java
// Good - simple constructor
@IocBean
public class DatabaseService {
    private final String url;

    public DatabaseService(@ConfigProperty("db.url") String url) {
        this.url = url; // Just assignment
    }
}

// Bad - complex constructor
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        connect(url);              // Network I/O - slow!
        createTables();            // Database operations - slow!
        loadData();                // Expensive - slow!
        scheduleCleanupTask();     // Side effects - risky!
    }
}
```

Defer expensive operations to `@AfterIocLoad` or explicit initialization methods.

## Next Steps

For more detailed information, see:

- **[Quick Start](../getting-started/Quick-Start.md)** - Build your first Tubing plugin
- **[Migration Guide](../getting-started/Migration-Guide.md)** - Convert existing plugins
- **[Dependency Injection](../core/Dependency-Injection.md)** - Deep dive into DI
- **[Configuration Injection](../core/Configuration-Injection.md)** - Configuration management
- **[Best Practices](../best-practices/Common-Patterns.md)** - Design patterns and best practices
- **[Performance](../best-practices/Performance.md)** - Performance optimization
- **[GUI Controllers](../gui/GUI-Controllers.md)** - GUI framework guide

---

**Still have questions?** Check the [Common Errors](Common-Errors.md) and [Debugging](Debugging.md) guides, or open an issue on [GitHub](https://github.com/garagepoort/Tubing/issues).
