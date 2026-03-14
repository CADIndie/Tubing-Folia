# IoC Container

The IoC (Inversion of Control) container is the heart of Tubing. It manages the lifecycle of all components (called "beans") in your plugin, automatically wiring dependencies between them so you can focus on building features instead of managing object creation and configuration.

## What is an IoC Container?

An IoC container is a framework that manages object creation, dependency injection, and lifecycle management. Instead of manually creating objects and passing dependencies, you annotate classes and let the container handle everything automatically.

**Traditional approach (without IoC):**

```java
public class MyPlugin extends JavaPlugin {

    private PlayerDataRepository playerRepository;
    private ConfigManager configManager;
    private PlayerService playerService;

    @Override
    public void onEnable() {
        // Manual creation and wiring
        configManager = new ConfigManager(this);
        playerRepository = new PlayerDataRepository(configManager.getDatabaseUrl());
        playerService = new PlayerService(playerRepository, configManager);

        // Manual event registration
        getServer().getPluginManager().registerEvents(
            new PlayerListener(playerService), this);
    }
}
```

**Tubing approach (with IoC):**

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("Plugin enabled!");
        // That's it! Container handles everything else.
    }
}

@IocBean
public class PlayerService {
    private final PlayerDataRepository playerRepository;
    private final ConfigManager configManager;

    // Dependencies automatically injected
    public PlayerService(PlayerDataRepository playerRepository,
                         ConfigManager configManager) {
        this.playerRepository = playerRepository;
        this.configManager = configManager;
    }
}

@IocBukkitListener
public class PlayerListener implements Listener {
    private final PlayerService playerService;

    // Automatically registered and injected
    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;
    }
}
```

## Why Use an IoC Container?

### 1. Zero Boilerplate

No manual object creation, no singleton patterns, no static managers. Just annotate and go.

### 2. Automatic Dependency Injection

The container automatically resolves and injects dependencies through constructor parameters. You never have to manually wire objects together.

### 3. Testability

Constructor injection makes unit testing trivial. No need to mock static managers or deal with complex initialization chains.

```java
// Testing is easy - just pass in your mocks
@Test
public void testPlayerService() {
    PlayerDataRepository mockRepo = mock(PlayerDataRepository.class);
    ConfigManager mockConfig = mock(ConfigManager.class);

    PlayerService service = new PlayerService(mockRepo, mockConfig);
    // Test your service
}
```

### 4. Loose Coupling

Components depend on abstractions (interfaces) rather than concrete implementations. The container handles finding and injecting the right implementation.

### 5. Configuration Integration

Configuration values are injected directly into fields - no manual YAML parsing needed.

```java
@IocBean
public class CombatService {

    @ConfigProperty("combat.cooldown")
    private int cooldown;

    @ConfigProperty("combat.enabled")
    private boolean enabled;

    // Values automatically loaded from config.yml
}
```

### 6. Centralized Lifecycle

All beans are created in the correct order, with proper dependency resolution. No initialization order bugs.

## Container Lifecycle

Understanding the IoC container's lifecycle helps you know when beans are created and when you can safely access them.

### Phase 1: Plugin Startup

When your plugin enables, the platform-specific Tubing base class (e.g., `TubingBukkitPlugin`) automatically initializes the container:

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        // Container is already initialized by this point
        // All beans are created and ready to use
        getLogger().info("Plugin enabled!");
    }
}
```

### Phase 2: Package Scanning

The container uses ClassGraph to scan your plugin's package and all subpackages for annotated classes:

```java
String pkg = tubingPlugin.getClass().getPackage().getName();
scanResult = new ClassGraph()
    .enableAllInfo()
    .acceptPackages(pkg)
    .scan();
```

**What gets scanned:**
- All classes with `@IocBean` annotation
- All classes with platform-specific annotations (`@IocBukkitListener`, etc.)
- Classes with `@TubingConfiguration` containing provider methods
- Any custom bean annotations registered via `TubingBeanAnnotationRegistrator`

**Performance note:** ClassGraph only scans your plugin's package tree, not the entire classpath, keeping startup fast.

### Phase 3: Bean Discovery

The container discovers all bean candidates:

1. **Annotation discovery**: Finds all classes marked with bean annotations
2. **Provider method discovery**: Finds methods marked with `@IocBeanProvider` in `@TubingConfiguration` classes
3. **Multi-provider discovery**: Identifies classes/methods for multi-bean patterns
4. **Conditional filtering**: Applies `@ConditionalOnProperty` checks based on configuration

### Phase 4: Dependency Resolution

The container analyzes dependencies and determines the correct instantiation order:

```java
@IocBean
public class ServiceA {
    // No dependencies - created first
}

@IocBean
public class ServiceB {
    // Depends on ServiceA - created second
    public ServiceB(ServiceA serviceA) { }
}

@IocBean
public class ServiceC {
    // Depends on both - created last
    public ServiceC(ServiceA serviceA, ServiceB serviceB) { }
}
```

**Creation order:**
1. Beans with no dependencies
2. Beans whose dependencies are satisfied
3. Priority beans (marked with `priority = true`) are created before non-priority beans at the same dependency level

### Phase 5: Bean Instantiation

For each bean in dependency order:

1. **Constructor resolution**: Only one constructor is allowed per bean class
2. **Parameter injection**: Constructor parameters are resolved:
   - Other beans are looked up from the container
   - `@ConfigProperty` parameters are loaded from configuration
   - `@InjectTubingPlugin` parameters receive the plugin instance
   - `@IocMulti` parameters receive a list of implementations
3. **Instance creation**: The bean is instantiated with resolved parameters
4. **Field injection**: `@ConfigProperty` fields are injected after construction
5. **Plugin injection**: `@InjectTubingPlugin` fields are injected after construction
6. **Registration**: Bean is stored in the container by its class type

### Phase 6: Post-Processing

After all beans are created:

1. **AfterIocLoad methods**: Methods annotated with `@AfterIocLoad` in configuration classes are invoked
2. **Platform registration**: Platform-specific beans (listeners, commands) are registered with Bukkit/BungeeCord/Velocity

### Phase 7: Plugin Enable

Finally, your plugin's `enable()` method is called. At this point:
- All beans are created and initialized
- All dependencies are resolved
- Configuration is loaded
- The container is ready for use

## Bean Discovery and Registration

### Automatic Discovery with Annotations

The primary way to register beans is through annotations. Any class in your plugin's package tree with a bean annotation is automatically discovered and registered.

**Core bean annotation:**

```java
@IocBean
public class PlayerService {
    // Automatically discovered and registered
}
```

**Platform-specific annotations:**

```java
// Bukkit
@IocBukkitListener
public class PlayerListener implements Listener { }

@IocBukkitCommandHandler("spawn")
public class SpawnCommand { }

// BungeeCord
@IocBungeeListener
public class ProxyListener implements Listener { }

// Velocity
@IocVelocityListener
public class VelocityListener { }
```

All platform-specific annotations extend the base `@IocBean` functionality and add platform registration.

### Bean Providers

For complex bean creation logic, use provider methods in configuration classes:

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public Database provideDatabase(@ConfigProperty("database.url") String url,
                                    @ConfigProperty("database.username") String username) {
        // Complex initialization logic
        DatabaseConfig config = new DatabaseConfig();
        config.setUrl(url);
        config.setUsername(username);
        config.setConnectionPool(true);
        config.setMaxConnections(10);

        return new HikariDatabase(config);
    }
}
```

**Benefits of providers:**
- Complex initialization logic
- External library integration (not under your control)
- Multiple configuration parameters needed for construction
- Conditional bean creation based on runtime state

### Multi-Providers

When you need multiple implementations of an interface available as a list:

```java
public interface RewardHandler {
    void handleReward(Player player, Reward reward);
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler {
    public void handleReward(Player player, Reward reward) {
        // Give money
    }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemRewardHandler implements RewardHandler {
    public void handleReward(Player player, Reward reward) {
        // Give items
    }
}

@IocBean
public class RewardService {
    private final List<RewardHandler> handlers;

    // Automatically receives all RewardHandler implementations
    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers) {
        this.handlers = handlers;
    }
}
```

### Conditional Beans

Beans can be conditionally registered based on configuration:

```java
@IocBean(conditionalOnProperty = "features.combat.enabled")
public class CombatService {
    // Only created if features.combat.enabled = true in config
}

@IocBean
@ConditionalOnMissingBean
public class DefaultRewardHandler implements RewardHandler {
    // Only created if no other RewardHandler exists
}
```

### Priority Beans

Beans can be marked for priority creation:

```java
@IocBean(priority = true)
public class DatabaseService {
    // Created before non-priority beans at the same dependency level
}
```

This is useful for:
- Database connections needed by many other beans
- Configuration loaders
- Core infrastructure beans

## Container Initialization During Plugin Startup

The container initialization is handled automatically by the Tubing platform base classes. Here's what happens under the hood:

### Bukkit

```java
public abstract class TubingBukkitPlugin extends JavaPlugin implements TubingPlugin {

    private IocContainer iocContainer;

    @Override
    public final void onEnable() {
        iocContainer = new IocContainer();
        iocContainer.init(this);  // Triggers full container lifecycle
        enable();                 // Your plugin code runs here
    }

    public IocContainer getIocContainer() {
        return iocContainer;
    }
}
```

### BungeeCord

```java
public abstract class TubingBungeePlugin extends Plugin implements TubingPlugin {

    private IocContainer iocContainer;

    @Override
    public final void onEnable() {
        iocContainer = new IocContainer();
        iocContainer.init(this);
        enable();
    }
}
```

### Velocity

```java
public abstract class TubingVelocityPlugin implements TubingPlugin {

    private IocContainer iocContainer;

    protected void enable() {
        iocContainer = new IocContainer();
        iocContainer.init(this);
        onEnable();
    }
}
```

**Key points:**
- The base class's `onEnable()` is `final` - you can't override it
- Container initialization happens before your `enable()` method is called
- The container is stored in the plugin instance and accessible via `getIocContainer()`
- You never need to manually initialize the container

## Accessing the Container

While dependency injection is the preferred way to get beans, sometimes you need direct container access.

### Getting the Container Instance

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();
        // Direct access to container
    }
}
```

### Retrieving Beans: get()

Retrieve a single bean by class:

```java
// Get a concrete class
PlayerService playerService = container.get(PlayerService.class);

// Get by interface
RewardHandler handler = container.get(RewardHandler.class);
```

**How it works:**
- For concrete classes: Returns the exact bean of that type
- For interfaces: Returns the implementation if exactly one exists
- Throws `IocException` if multiple implementations exist for an interface
- Throws `IocException` if no implementation exists

**Example with interface:**

```java
public interface TeleportService { }

@IocBean
public class DefaultTeleportService implements TeleportService { }

// Later...
TeleportService service = container.get(TeleportService.class);
// Returns DefaultTeleportService instance
```

**Multiple implementations error:**

```java
public interface Logger { }

@IocBean
public class ConsoleLogger implements Logger { }

@IocBean
public class FileLogger implements Logger { }

// This throws IocException:
Logger logger = container.get(Logger.class);
// "Cannot retrieve bean with interface Logger. Too many implementations registered."

// Use getList() instead:
List<Logger> loggers = container.getList(Logger.class);
```

### Retrieving Bean Lists: getList()

Retrieve all beans of a type or interface:

```java
// Get all implementations of an interface
List<RewardHandler> handlers = container.getList(RewardHandler.class);

// Get all command handlers (if registered as multi-providers)
List<CommandHandler> commands = container.getList(CommandHandler.class);
```

**When to use getList():**
- You need all implementations of an interface
- You registered beans with `@IocMultiProvider`
- You're building a plugin system where extensions register via IoC
- You want to iterate over multiple strategies or handlers

**Example:**

```java
@IocBean
public class NotificationService {

    private final List<NotificationHandler> handlers;

    public NotificationService(IocContainer container) {
        this.handlers = container.getList(NotificationHandler.class);
    }

    public void sendNotification(Player player, String message) {
        for (NotificationHandler handler : handlers) {
            handler.handle(player, message);
        }
    }
}
```

### When to Use Direct Container Access

**Avoid direct access when possible** - prefer constructor injection. Use container access only for:

1. **Dynamic bean lookup**: When the bean type isn't known at compile time
2. **Optional dependencies**: When a dependency may or may not exist
3. **Circular dependency breaking**: When you have an unavoidable circular dependency (though this usually indicates a design issue)
4. **Plugin APIs**: When providing access to your container to external plugins
5. **Late binding**: When you need to resolve a bean after the initial construction phase

**Example of legitimate container usage:**

```java
@IocBean
public class PluginAPI {

    private final IocContainer container;

    public PluginAPI(IocContainer container) {
        this.container = container;
    }

    // Allow other plugins to access specific services
    public <T> T getService(Class<T> serviceClass) {
        return container.get(serviceClass);
    }

    public <T> List<T> getServices(Class<T> serviceClass) {
        return container.getList(serviceClass);
    }
}
```

## Manual Bean Registration

Sometimes you need to register beans manually, typically for:
- External library objects
- Beans created at runtime
- Platform-provided objects (like Bukkit's `Server`)
- Test mocks

### Using registerBean()

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Register external library object
        ExternalService externalService = ExternalLibrary.createService();
        container.registerBean(externalService);

        // Now it's injectable
        // Any bean requesting ExternalService will receive this instance
    }
}
```

**How it works:**
- The bean is registered by its exact class type
- If a bean of that type already exists, it's replaced
- The bean is immediately available for injection
- The bean's lifecycle is not managed - you're responsible for initialization

**Example: Platform objects**

```java
@TubingConfiguration
public class PlatformConfiguration {

    @IocBeanProvider
    public Server provideBukkitServer(@InjectTubingPlugin TubingPlugin plugin) {
        TubingBukkitPlugin bukkitPlugin = (TubingBukkitPlugin) plugin;
        return bukkitPlugin.getServer();
    }
}

// Now Server is injectable
@IocBean
public class WorldService {

    private final Server server;

    public WorldService(Server server) {
        this.server = server;
    }

    public void loadWorld(String name) {
        server.createWorld(new WorldCreator(name));
    }
}
```

**Example: Runtime bean creation**

```java
@IocBean
public class PluginService {

    private final IocContainer container;

    public PluginService(IocContainer container) {
        this.container = container;
    }

    public void enableDynamicFeature(String configPath) {
        // Load configuration
        FeatureConfig config = loadConfig(configPath);

        // Create and register bean at runtime
        DynamicFeature feature = new DynamicFeature(config);
        container.registerBean(feature);

        // Now DynamicFeature is available to other beans
    }
}
```

### Registering Interfaces

To register a bean under an interface, use a provider:

```java
@TubingConfiguration
public class InterfaceConfiguration {

    @IocBeanProvider
    public Logger provideLogger() {
        // Return concrete implementation
        return new ConsoleLogger();
    }
}

// Now injectable as Logger interface
@IocBean
public class LoggingService {

    private final Logger logger;

    public LoggingService(Logger logger) {
        this.logger = logger;
    }
}
```

## Understanding ClassGraph Scanning

Tubing uses ClassGraph for fast, efficient classpath scanning to discover beans.

### What is ClassGraph?

ClassGraph is a classpath and module scanner that can:
- Find all classes with specific annotations
- Find all implementations of interfaces
- Find all subclasses of a class
- Extract annotation metadata
- Work with Java 9+ modules

It's significantly faster than traditional reflection scanning because it reads bytecode directly without loading classes.

### How Tubing Uses ClassGraph

```java
String pkg = tubingPlugin.getClass().getPackage().getName();
scanResult = new ClassGraph()
    .enableAllInfo()           // Enable all metadata scanning
    .acceptPackages(pkg)       // Only scan your plugin's package
    .scan();                   // Perform the scan
```

**Key configuration:**
- `enableAllInfo()`: Enables scanning for classes, annotations, methods, fields, etc.
- `acceptPackages(pkg)`: Restricts scanning to your plugin's package and subpackages
- Only your plugin code is scanned - dependency libraries are not scanned

### What Gets Scanned

The container scans for:

1. **Classes with bean annotations:**
```java
List<Class<?>> beans = scanResult
    .getClassesWithAnnotation(IocBean.class)
    .loadClasses();
```

2. **Classes implementing interfaces:**
```java
Set<Class<?>> implementations = scanResult
    .getClassesImplementing(ServiceInterface.class)
    .loadClasses();
```

3. **Configuration classes:**
```java
List<Class<?>> configs = scanResult
    .getClassesWithAnnotation(TubingConfiguration.class)
    .loadClasses();
```

4. **Custom bean annotation registrators:**
```java
for (Class<? extends TubingBeanAnnotationRegistrator> aClass :
     scanResult.getClassesImplementing(TubingBeanAnnotationRegistrator.class)
              .loadClasses(TubingBeanAnnotationRegistrator.class)) {
    // Register custom bean annotations
}
```

### Scan Results Caching

The scan result is stored in the container and reusable:

```java
public ScanResult getReflections() {
    return scanResult;
}
```

This allows your code to perform additional scanning without re-scanning the classpath:

```java
IocContainer container = getIocContainer();
ScanResult scanResult = container.getReflections();

// Find all classes implementing your API
List<Class<? extends PluginExtension>> extensions =
    scanResult.getClassesImplementing(PluginExtension.class)
              .loadClasses(PluginExtension.class);
```

### Package Organization Impact

ClassGraph only scans packages under your plugin's main package. Consider this structure:

```
com.example.myplugin/          <- Plugin main class here
├── commands/                   <- Scanned ✓
├── services/                   <- Scanned ✓
└── utils/                      <- Scanned ✓

com.example.library/            <- Different package - NOT scanned ✗
└── Helper.java
```

**Best practice:** Keep all your plugin code under a single root package. If you need to include beans from external packages, use manual registration or bean providers.

### Performance Considerations

**ClassGraph is fast:**
- Typical scan time: 10-50ms for small plugins
- Scans bytecode directly - doesn't load classes until needed
- Only scans your plugin's package tree
- Results are cached for the lifetime of your plugin

**Optimization tips:**
1. Keep your package structure shallow (fewer nested packages = faster scanning)
2. Don't use overly broad package names (e.g., `com.example` scans everything under `com.example.*`)
3. Avoid creating unnecessary bean classes (each class adds minimal overhead, but it adds up)

### Custom Bean Annotations

You can register your own bean annotations:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyCustomBean {
    boolean priority() default false;
    Class multiproviderClass() default Object.class;
}

public class MyBeanRegistrator implements TubingBeanAnnotationRegistrator {

    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(MyCustomBean.class);
    }
}
```

ClassGraph will automatically discover your registrator and scan for your custom annotation.

## Common Patterns and Examples

### Factory Pattern

```java
public interface NotificationSender {
    void send(Player player, String message);
}

@TubingConfiguration
public class NotificationConfiguration {

    @IocBeanProvider
    public NotificationSender provideNotificationSender(
            @ConfigProperty("notifications.type") String type) {

        switch (type.toLowerCase()) {
            case "chat":
                return new ChatNotificationSender();
            case "title":
                return new TitleNotificationSender();
            case "actionbar":
                return new ActionBarNotificationSender();
            default:
                throw new IllegalArgumentException("Unknown notification type: " + type);
        }
    }
}
```

### Strategy Pattern

```java
public interface PermissionProvider {
    boolean hasPermission(Player player, String permission);
}

@IocBean
@IocMultiProvider(PermissionProvider.class)
public class VaultPermissionProvider implements PermissionProvider {
    public boolean hasPermission(Player player, String permission) {
        // Vault implementation
    }
}

@IocBean
@IocMultiProvider(PermissionProvider.class)
public class LuckPermsPermissionProvider implements PermissionProvider {
    public boolean hasPermission(Player player, String permission) {
        // LuckPerms implementation
    }
}

@IocBean
public class PermissionService {

    private final List<PermissionProvider> providers;

    public PermissionService(@IocMulti(PermissionProvider.class)
                            List<PermissionProvider> providers) {
        this.providers = providers;
    }

    public boolean hasPermission(Player player, String permission) {
        // Try each provider until one succeeds
        for (PermissionProvider provider : providers) {
            if (provider.hasPermission(player, permission)) {
                return true;
            }
        }
        return false;
    }
}
```

### Repository Pattern

```java
public interface PlayerRepository {
    PlayerData findById(UUID id);
    void save(PlayerData data);
}

@IocBean
public class MySQLPlayerRepository implements PlayerRepository {

    private final Database database;

    public MySQLPlayerRepository(Database database) {
        this.database = database;
    }

    public PlayerData findById(UUID id) {
        // MySQL implementation
    }

    public void save(PlayerData data) {
        // MySQL implementation
    }
}

@IocBean
public class PlayerService {

    private final PlayerRepository repository;

    // Automatically receives MySQLPlayerRepository
    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}
```

### Decorator Pattern

```java
public interface CacheableRepository<T> {
    T findById(String id);
}

@IocBean
public class DatabaseRepository implements CacheableRepository<PlayerData> {

    public PlayerData findById(String id) {
        // Database lookup
    }
}

@TubingConfiguration
public class CacheConfiguration {

    @IocBeanProvider
    public CacheableRepository<PlayerData> provideCachedRepository(
            DatabaseRepository databaseRepository) {

        return new CachedRepositoryDecorator<>(databaseRepository);
    }
}

public class CachedRepositoryDecorator<T> implements CacheableRepository<T> {

    private final CacheableRepository<T> delegate;
    private final Map<String, T> cache = new HashMap<>();

    public CachedRepositoryDecorator(CacheableRepository<T> delegate) {
        this.delegate = delegate;
    }

    public T findById(String id) {
        return cache.computeIfAbsent(id, delegate::findById);
    }
}
```

## Troubleshooting

### "Cannot instantiate bean. No Bean annotation present"

**Problem:** You're trying to inject a class that isn't annotated as a bean.

**Solution:** Add `@IocBean` to the class or register it manually.

```java
// Before (error)
public class MyService { }

// After (fixed)
@IocBean
public class MyService { }
```

### "Cannot instantiate bean. Only one constructor should be defined"

**Problem:** Your bean class has multiple constructors.

**Solution:** Beans can only have one constructor. Remove extra constructors or use a provider method.

```java
// Before (error)
@IocBean
public class MyService {
    public MyService() { }
    public MyService(String param) { }  // Second constructor - error!
}

// After (fixed)
@IocBean
public class MyService {
    private final String param;

    public MyService(@ConfigProperty("service.param") String param) {
        this.param = param;
    }
}
```

### "Cannot retrieve bean with interface X. Too many implementations registered"

**Problem:** Multiple beans implement the same interface, and you're trying to use `get()`.

**Solution:** Use `getList()` to get all implementations, or use `@ConditionalOnMissingBean` to ensure only one is active.

```java
// Option 1: Get all implementations
List<MyInterface> implementations = container.getList(MyInterface.class);

// Option 2: Use conditional beans
@IocBean
@ConditionalOnMissingBean
public class DefaultImplementation implements MyInterface { }
```

### "Cannot instantiate bean. No implementation registered"

**Problem:** You're injecting an interface with no registered implementations.

**Solution:** Create a class that implements the interface and annotate it with `@IocBean`.

```java
public interface MyService { }

@IocBean
public class MyServiceImpl implements MyService { }
```

### Circular Dependencies

**Problem:** Bean A depends on Bean B, and Bean B depends on Bean A.

**Solution:** Circular dependencies indicate a design issue. Refactor to remove the cycle:

```java
// Before (circular dependency)
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }  // Circular!
}

// After (refactored)
@IocBean
public class ServiceA {
    public ServiceA(SharedDependency shared) { }
}

@IocBean
public class ServiceB {
    public ServiceB(SharedDependency shared) { }
}

@IocBean
public class SharedDependency {
    // Common functionality extracted here
}
```

## Next Steps

Now that you understand the IoC container:

- Learn about [Dependency Injection](Dependency-Injection.md) patterns in detail
- Explore [Bean Registration](Bean-Registration.md) options
- Read about [Configuration Injection](Configuration-Injection.md)
- Understand [Bean Lifecycle](Bean-Lifecycle.md) and initialization order

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your code
- [Multi-Implementation](Multi-Implementation.md) - Working with multiple beans
- [Conditional Beans](Conditional-Beans.md) - Conditional registration
