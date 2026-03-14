# Post-Initialization

Post-initialization hooks allow you to execute code after all beans have been instantiated and configured. This is essential for complex initialization logic that requires multiple beans to be available, cross-bean wiring, validation, and system registration.

## Overview

Tubing provides several mechanisms for post-initialization:

- **`@AfterIocLoad`**: Methods executed after all beans are created (Phase 7 of lifecycle)
- **`OnLoad` interface**: Beans that execute custom logic after container initialization
- **`BeforeTubingReload`**: Platform-specific hooks executed before a plugin reload

These hooks execute at different points in the plugin lifecycle and serve different purposes.

## @AfterIocLoad Annotation

The `@AfterIocLoad` annotation marks static methods in `@TubingConfiguration` classes that should be executed after all beans are instantiated and configured.

### Basic Usage

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @AfterIocLoad
    public static void initializeDatabase(DatabaseService database) {
        // All beans are created and configured
        // Now we can perform initialization
        database.connect();
        database.runMigrations();
    }
}
```

### When @AfterIocLoad Executes

`@AfterIocLoad` methods run in **Phase 7** of the bean lifecycle, after:

1. All beans are discovered and collected
2. All beans are instantiated with dependencies resolved
3. All `@ConfigProperty` fields are injected
4. All `@InjectTubingPlugin` fields are injected

But before:
- Platform-specific registration (listeners, commands)
- Your plugin's `enable()` method is called

This guarantees that all beans are fully initialized and ready to use.

### Method Requirements

`@AfterIocLoad` methods must follow these rules:

**Required:**
- Must be `static`
- Must be in a `@TubingConfiguration` class
- Must be `public`

**Optional:**
- Can have parameters (any beans from the container)
- Can have `void` return type (or any type, which will be ignored)
- Can have `@ConfigProperty` parameters
- Can have `@InjectTubingPlugin` parameters
- Can have `@IocMulti` parameters for lists of beans

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initialize(
        DatabaseService database,              // Bean injection
        CacheService cache,                    // Bean injection
        @ConfigProperty("debug") boolean debug, // Config injection
        @InjectTubingPlugin TubingPlugin plugin // Plugin injection
    ) {
        if (debug) {
            plugin.getLogger().info("Starting database connection...");
        }
        database.connect();
        cache.warmUp(database.getAllKeys());
    }
}
```

### Use Cases for @AfterIocLoad

#### 1. Cross-Bean Initialization

When initialization logic requires multiple beans to coordinate:

```java
@TubingConfiguration
public class DataConfiguration {

    @AfterIocLoad
    public static void initializeData(DatabaseService database, CacheService cache) {
        // Connect to database
        database.connect();

        // Load all data into cache
        cache.warmUp(database.getAllKeys());

        // Start background sync
        database.enableAutoSync(cache);
    }
}
```

#### 2. Validation

Validate configuration or state after all beans are created:

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateConfiguration(
        ConfigurationLoader config,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        // Ensure at least one feature is enabled
        boolean homesEnabled = config.getConfigValue("features.homes").orElse(false);
        boolean warpsEnabled = config.getConfigValue("features.warps").orElse(false);

        if (!homesEnabled && !warpsEnabled) {
            plugin.getLogger().severe("At least one feature must be enabled!");
            throw new IllegalStateException("Invalid configuration");
        }
    }
}
```

#### 3. System Registration

Register beans with external systems or frameworks:

```java
@TubingConfiguration
public class MetricsConfiguration {

    @AfterIocLoad
    public static void registerMetrics(
        @IocMulti(MetricsProvider.class) List<MetricsProvider> providers,
        MetricsService metrics
    ) {
        // Register all metrics providers
        for (MetricsProvider provider : providers) {
            metrics.register(provider);
        }

        metrics.start();
    }
}
```

#### 4. Background Task Startup

Start scheduled tasks or background threads:

```java
@TubingConfiguration
public class TaskConfiguration {

    @AfterIocLoad
    public static void startBackgroundTasks(
        TaskScheduler scheduler,
        AutoSaveService autoSave,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        // Schedule auto-save every 5 minutes
        scheduler.schedule(autoSave::save, 300);

        // Start cleanup task
        scheduler.startCleanupTask();

        plugin.getLogger().info("Background tasks started");
    }
}
```

#### 5. External Library Integration

Initialize external libraries that need multiple configuration values:

```java
@TubingConfiguration
public class LibraryConfiguration {

    @AfterIocLoad
    public static void initializeLibrary(
        @ConfigProperty("library.api-key") String apiKey,
        @ConfigProperty("library.endpoint") String endpoint,
        @ConfigProperty("library.timeout") int timeout
    ) {
        // Initialize external library with all configuration
        ExternalLibrary.configure(builder -> builder
            .apiKey(apiKey)
            .endpoint(endpoint)
            .timeout(timeout)
            .build()
        );
    }
}
```

#### 6. Breaking Circular Dependencies

When two beans need references to each other (not recommended, but sometimes necessary):

```java
@TubingConfiguration
public class WiringConfiguration {

    @AfterIocLoad
    public static void wireCircularDependencies(ServiceA serviceA, ServiceB serviceB) {
        // Both beans are created
        // Now we can wire the circular reference
        serviceA.setServiceB(serviceB);
        serviceB.setServiceA(serviceA);
    }
}
```

Note: This is a code smell. Consider refactoring to eliminate the circular dependency.

### Execution Order

`@AfterIocLoad` methods are executed in the order the configuration classes are discovered by ClassGraph. This order is not guaranteed.

**If you need specific ordering**, use method parameters to create dependencies:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void step1_database(DatabaseService database) {
        // Runs first (no dependencies on other initialization)
        database.connect();
    }

    @AfterIocLoad
    public static void step2_cache(CacheService cache, DatabaseService database) {
        // Runs after step1 because it depends on database
        cache.warmUp(database.getAllKeys());
    }

    @AfterIocLoad
    public static void step3_services(
        @IocMulti(Service.class) List<Service> services,
        CacheService cache
    ) {
        // Runs after step2 because it depends on cache
        services.forEach(Service::start);
    }
}
```

However, this ordering is not strictly guaranteed. For critical ordering requirements, consider using a single `@AfterIocLoad` method that explicitly sequences operations:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initializeInOrder(
        DatabaseService database,
        CacheService cache,
        @IocMulti(Service.class) List<Service> services
    ) {
        // Explicit ordering
        database.connect();
        cache.warmUp(database.getAllKeys());
        services.forEach(Service::start);
    }
}
```

### Multiple @AfterIocLoad Methods

You can have multiple `@AfterIocLoad` methods across different configuration classes:

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @AfterIocLoad
    public static void initDatabase(DatabaseService database) {
        database.connect();
    }
}

@TubingConfiguration
public class CacheConfiguration {

    @AfterIocLoad
    public static void initCache(CacheService cache) {
        cache.initialize();
    }
}
```

All methods will be executed, but the order is not guaranteed unless you use parameter dependencies.

### Error Handling

If an `@AfterIocLoad` method throws an exception, the plugin will fail to enable:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initialize(DatabaseService database) {
        try {
            database.connect();
        } catch (DatabaseException e) {
            throw new IocException("Failed to connect to database", e);
        }
    }
}
```

The exception will propagate up to the container initialization, and the plugin will be disabled by the platform.

## OnLoad Interface

The `OnLoad` interface provides an alternative post-initialization mechanism. It's executed immediately after container initialization but before platform-specific registration.

### Interface Definition

```java
public interface OnLoad {
    void load(IocContainer iocContainer);
}
```

### Basic Usage

Implement `OnLoad` in a bean and register it as a multi-provider:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class MyCustomOnLoad implements OnLoad {

    private final GuiService guiService;

    public MyCustomOnLoad(GuiService guiService) {
        this.guiService = guiService;
    }

    @Override
    public void load(IocContainer iocContainer) {
        // Access container directly
        ScanResult scanResult = iocContainer.getReflections();

        // Register GUI components
        guiService.registerComponents(scanResult);
    }
}
```

### When OnLoad Executes

`OnLoad` beans execute **immediately after** the container is initialized, in the `initIocContainer()` method:

```java
default IocContainer initIocContainer() {
    IocContainer iocContainer = new IocContainer();
    iocContainer.init(this);

    // OnLoad beans execute here
    List<OnLoad> onloads = iocContainer.getList(OnLoad.class);
    if (onloads != null) {
        onloads.forEach(onLoad -> onLoad.load(iocContainer));
    }

    return iocContainer;
}
```

This happens **after** `@AfterIocLoad` methods but **before** platform-specific registration.

### OnLoad vs @AfterIocLoad

| Feature | @AfterIocLoad | OnLoad |
|---------|---------------|--------|
| Execution time | Phase 7 of lifecycle | After container init |
| Container access | No direct access | Direct access via parameter |
| Registration | Static method in config | Bean implementing interface |
| Parameters | Any beans + config | Just IocContainer |
| Use case | Simple initialization | Advanced container manipulation |

**Use `@AfterIocLoad` for:**
- Simple initialization logic
- Cross-bean coordination
- Validation
- Starting tasks

**Use `OnLoad` for:**
- Advanced container manipulation
- Dynamic bean registration
- Reflection-based discovery
- Framework integration

### Use Cases for OnLoad

#### 1. Dynamic Registration

Register beans dynamically based on classpath scanning:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class PluginExtensionLoader implements OnLoad {

    @Override
    public void load(IocContainer iocContainer) {
        ScanResult scanResult = iocContainer.getReflections();

        // Find all plugin extensions
        List<Class<? extends PluginExtension>> extensions =
            scanResult.getClassesImplementing(PluginExtension.class)
                .loadClasses(PluginExtension.class);

        // Register each extension
        for (Class<? extends PluginExtension> extensionClass : extensions) {
            try {
                PluginExtension extension = extensionClass.getDeclaredConstructor().newInstance();
                iocContainer.registerBean(extension);
            } catch (Exception e) {
                throw new IocException("Failed to load extension: " + extensionClass.getName(), e);
            }
        }
    }
}
```

#### 2. Framework Integration

Integrate with frameworks that need container access:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class GuiFrameworkIntegration implements OnLoad {

    private final GuiActionService guiActionService;

    public GuiFrameworkIntegration(GuiActionService guiActionService) {
        this.guiActionService = guiActionService;
    }

    @Override
    public void load(IocContainer iocContainer) {
        ScanResult scanResult = iocContainer.getReflections();

        // Find all GUI action handlers
        List<Class<?>> actionHandlers = scanResult
            .getClassesWithAnnotation(GuiActionHandler.class)
            .loadClasses();

        // Register with GUI framework
        for (Class<?> handler : actionHandlers) {
            Object bean = iocContainer.get(handler);
            guiActionService.registerHandler(bean);
        }
    }
}
```

#### 3. Custom Bean Processing

Process beans after creation for cross-cutting concerns:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class BeanPostProcessor implements OnLoad {

    @Override
    public void load(IocContainer iocContainer) {
        // Get all beans that need special processing
        List<Cacheable> cacheables = iocContainer.getList(Cacheable.class);

        for (Cacheable cacheable : cacheables) {
            cacheable.initializeCache();
        }
    }
}
```

## BeforeTubingReload Interface

`BeforeTubingReload` is a platform-specific hook executed **before** a plugin reload. It allows you to clean up resources, save state, or perform other pre-reload tasks.

### Platform Implementations

The interface exists in three platform-specific variants:

**Bukkit:**
```java
public interface BeforeTubingReload {
    void execute(TubingBukkitPlugin tubingPlugin);
}
```

**BungeeCord:**
```java
public interface BeforeTubingReload {
    void execute(TubingBungeePlugin tubingPlugin);
}
```

**Velocity:**
```java
public interface BeforeTubingReload {
    void execute(TubingVelocityPlugin tubingPlugin);
}
```

### Basic Usage

Implement the interface in a bean (no special annotation needed):

```java
@IocBean
public class DatabaseCleanup implements BeforeTubingReload {

    private final DatabaseService database;

    public DatabaseCleanup(DatabaseService database) {
        this.database = database;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Save any pending data
        database.flush();

        // Close connections
        database.disconnect();

        tubingPlugin.getLogger().info("Database cleaned up for reload");
    }
}
```

### When BeforeTubingReload Executes

The hook executes when `reload()` is called on the plugin:

```java
public void reload() {
    // BeforeTubingReload hooks execute here
    List<BeforeTubingReload> beforeTubingReloads = iocContainer.getList(BeforeTubingReload.class);
    if (beforeTubingReloads != null) {
        beforeTubingReloads.forEach(onLoad -> onLoad.execute(this));
    }

    // Then your plugin's beforeReload() method
    beforeReload();

    // Then platform cleanup (unregister listeners, commands)
    HandlerList.unregisterAll(this);

    // Then container recreation
    iocContainer = initIocContainer();

    // Then your plugin's enable() method
    enable();
}
```

This happens:
1. Before your plugin's `beforeReload()` method
2. Before listeners and commands are unregistered
3. Before the container is recreated

### Use Cases for BeforeTubingReload

#### 1. Resource Cleanup

Clean up resources that might leak across reloads:

```java
@IocBean
public class ResourceCleanup implements BeforeTubingReload {

    private final ConnectionPoolService connectionPool;
    private final CacheService cache;

    public ResourceCleanup(ConnectionPoolService connectionPool, CacheService cache) {
        this.connectionPool = connectionPool;
        this.cache = cache;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Close all connections
        connectionPool.closeAll();

        // Clear cache
        cache.clear();

        tubingPlugin.getLogger().info("Resources cleaned up");
    }
}
```

#### 2. State Persistence

Save state before reload so it can be restored:

```java
@IocBean
public class StatePersistence implements BeforeTubingReload {

    private final PlayerDataService playerData;

    public StatePersistence(PlayerDataService playerData) {
        this.playerData = playerData;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Save all player data
        playerData.saveAll();

        tubingPlugin.getLogger().info("State persisted for reload");
    }
}
```

#### 3. Task Cancellation

Cancel scheduled tasks before reload:

```java
@IocBean
public class TaskCancellation implements BeforeTubingReload {

    private final TaskScheduler scheduler;

    public TaskCancellation(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Cancel all scheduled tasks
        scheduler.cancelAll();

        tubingPlugin.getLogger().info("Tasks cancelled for reload");
    }
}
```

#### 4. Metrics Cleanup

Stop metrics collection before reload:

```java
@IocBean
public class MetricsCleanup implements BeforeTubingReload {

    private final MetricsService metrics;

    public MetricsCleanup(MetricsService metrics) {
        this.metrics = metrics;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Stop metrics collection
        metrics.stop();

        // Clear registered providers
        metrics.clearProviders();

        tubingPlugin.getLogger().info("Metrics cleaned up for reload");
    }
}
```

### Multiple BeforeTubingReload Implementations

You can have multiple beans implementing `BeforeTubingReload`. They will all execute before reload:

```java
@IocBean
public class DatabaseCleanup implements BeforeTubingReload {
    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Database cleanup
    }
}

@IocBean
public class CacheCleanup implements BeforeTubingReload {
    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Cache cleanup
    }
}

@IocBean
public class TaskCleanup implements BeforeTubingReload {
    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Task cleanup
    }
}
```

The execution order is not guaranteed.

## Execution Timeline

Understanding when each post-initialization mechanism executes is crucial:

```
Plugin Startup
     |
     v
┌─────────────────────────────────────────┐
│  Container Initialization (IocContainer) │
│  - Discovery                            │
│  - Sorting                              │
│  - Validation                           │
│  - Instantiation                        │
│  - Configuration Injection              │
│  - @AfterIocLoad execution              │
└────────────┬────────────────────────────┘
             |
             v
┌─────────────────────────────────────────┐
│  OnLoad Execution                       │
│  - Called from initIocContainer()       │
│  - Access to IocContainer               │
└────────────┬────────────────────────────┘
             |
             v
┌─────────────────────────────────────────┐
│  Platform Registration                  │
│  - Listeners registered                 │
│  - Commands registered                  │
└────────────┬────────────────────────────┘
             |
             v
┌─────────────────────────────────────────┐
│  Plugin enable() called                 │
│  - Your custom initialization           │
└─────────────────────────────────────────┘


Plugin Reload (if supported)
     |
     v
┌─────────────────────────────────────────┐
│  BeforeTubingReload Execution           │
│  - Cleanup hooks execute                │
└────────────┬────────────────────────────┘
             |
             v
┌─────────────────────────────────────────┐
│  Plugin beforeReload() called           │
└────────────┬────────────────────────────┘
             |
             v
┌─────────────────────────────────────────┐
│  Platform Cleanup                       │
│  - Listeners unregistered               │
│  - Commands unregistered                │
└────────────┬────────────────────────────┘
             |
             v
     (Container recreated - full lifecycle runs again)
```

## Accessing Beans in Post-Initialization

All post-initialization hooks have full access to beans in the container.

### In @AfterIocLoad

Beans are injected as method parameters:

```java
@TubingConfiguration
public class InitConfig {

    @AfterIocLoad
    public static void init(
        DatabaseService database,              // Inject any bean
        CacheService cache,                    // Inject any bean
        @IocMulti(Handler.class) List<Handler> handlers, // Inject lists
        @ConfigProperty("debug") boolean debug, // Inject config
        @InjectTubingPlugin TubingPlugin plugin // Inject plugin
    ) {
        // Use all beans and config
    }
}
```

### In OnLoad

Access beans via the container:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class MyOnLoad implements OnLoad {

    private final SomeService service; // Constructor injection

    public MyOnLoad(SomeService service) {
        this.service = service;
    }

    @Override
    public void load(IocContainer iocContainer) {
        // Use constructor-injected beans
        service.doSomething();

        // Or get beans from container
        OtherService other = iocContainer.get(OtherService.class);
        List<Handler> handlers = iocContainer.getList(Handler.class);
    }
}
```

### In BeforeTubingReload

Beans are injected via constructor:

```java
@IocBean
public class MyCleanup implements BeforeTubingReload {

    private final DatabaseService database;
    private final CacheService cache;

    public MyCleanup(DatabaseService database, CacheService cache) {
        this.database = database;
        this.cache = cache;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Use all injected beans
        database.flush();
        cache.clear();
    }
}
```

## Best Practices

### 1. Prefer @AfterIocLoad for Simple Initialization

Use `@AfterIocLoad` for most post-initialization needs:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initialize(DatabaseService database) {
        database.connect();
    }
}
```

It's simpler, more declarative, and easier to understand.

### 2. Use OnLoad for Container Manipulation

Only use `OnLoad` when you need direct container access:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class DynamicBeanLoader implements OnLoad {

    @Override
    public void load(IocContainer iocContainer) {
        // Need container for dynamic registration
        ScanResult scanResult = iocContainer.getReflections();
        // ... dynamic registration logic
    }
}
```

### 3. Minimize Side Effects in @AfterIocLoad

Keep `@AfterIocLoad` methods focused and side-effect-free when possible:

**Bad:**
```java
@AfterIocLoad
public static void init(DatabaseService database, @InjectTubingPlugin TubingPlugin plugin) {
    database.connect();
    database.loadAllData();
    plugin.getServer().broadcastMessage("Loading...");
    Thread.sleep(5000); // Bad!
}
```

**Good:**
```java
@AfterIocLoad
public static void init(DatabaseService database) {
    database.connect();
    // Let the database service handle data loading in its own methods
}
```

### 4. Handle Errors Gracefully

Wrap critical initialization in try-catch blocks:

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @AfterIocLoad
    public static void initDatabase(
        DatabaseService database,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        try {
            database.connect();
        } catch (DatabaseException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            throw new IocException("Database initialization failed", e);
        }
    }
}
```

### 5. Use Explicit Ordering When Needed

Don't rely on implicit ordering. Use parameters to enforce dependencies:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initDatabase(DatabaseService database) {
        database.connect();
    }

    @AfterIocLoad
    public static void initCache(CacheService cache, DatabaseService database) {
        // This depends on database, so it will run after
        cache.warmUp(database);
    }
}
```

### 6. Clean Up in BeforeTubingReload

Always implement `BeforeTubingReload` for resources that need cleanup:

```java
@IocBean
public class ResourceManager implements BeforeTubingReload {

    private final ConnectionPool pool;

    public ResourceManager(ConnectionPool pool) {
        this.pool = pool;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Always clean up before reload
        pool.closeAll();
    }
}
```

### 7. Log Initialization Progress

Use logging to track initialization:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initialize(
        DatabaseService database,
        CacheService cache,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        plugin.getLogger().info("Connecting to database...");
        database.connect();

        plugin.getLogger().info("Warming up cache...");
        cache.warmUp(database.getAllKeys());

        plugin.getLogger().info("Initialization complete!");
    }
}
```

### 8. Avoid Blocking Operations

Don't perform long-running operations in post-initialization:

**Bad:**
```java
@AfterIocLoad
public static void init(DataService data) {
    data.loadAllData(); // Blocks plugin startup for seconds/minutes
}
```

**Good:**
```java
@AfterIocLoad
public static void init(DataService data, TaskScheduler scheduler) {
    // Start async loading
    scheduler.runAsync(() -> data.loadAllData());
}
```

### 9. Keep Configuration Classes Focused

Organize `@AfterIocLoad` methods by concern:

```java
@TubingConfiguration
public class DatabaseConfiguration {
    @AfterIocLoad
    public static void initDatabase(DatabaseService database) {
        database.connect();
    }
}

@TubingConfiguration
public class CacheConfiguration {
    @AfterIocLoad
    public static void initCache(CacheService cache) {
        cache.initialize();
    }
}

@TubingConfiguration
public class TaskConfiguration {
    @AfterIocLoad
    public static void startTasks(TaskScheduler scheduler) {
        scheduler.startAll();
    }
}
```

### 10. Document Initialization Dependencies

Document why post-initialization is needed:

```java
@TubingConfiguration
public class InitializationConfiguration {

    /**
     * Initialize the database connection after all beans are created.
     * This must happen in @AfterIocLoad because:
     * - ConnectionPool needs DatabaseConfig which is injected via @ConfigProperty
     * - Other services depend on an active connection
     * - Migrations must run before any data access
     */
    @AfterIocLoad
    public static void initializeDatabase(DatabaseService database) {
        database.connect();
        database.runMigrations();
    }
}
```

## Common Patterns

### Pattern 1: Multi-Stage Initialization

Initialize systems in stages with clear dependencies:

```java
@TubingConfiguration
public class MultiStageInitialization {

    @AfterIocLoad
    public static void stage1_database(DatabaseService database) {
        database.connect();
        database.runMigrations();
    }

    @AfterIocLoad
    public static void stage2_cache(CacheService cache, DatabaseService database) {
        cache.warmUp(database.getAllKeys());
    }

    @AfterIocLoad
    public static void stage3_services(
        @IocMulti(Service.class) List<Service> services,
        CacheService cache
    ) {
        services.forEach(service -> service.initialize(cache));
    }
}
```

### Pattern 2: Conditional Initialization

Initialize different systems based on configuration:

```java
@TubingConfiguration
public class ConditionalInitialization {

    @AfterIocLoad
    public static void initialize(
        @ConfigProperty("features.database-enabled") boolean databaseEnabled,
        DatabaseService database,
        @ConfigProperty("features.cache-enabled") boolean cacheEnabled,
        CacheService cache
    ) {
        if (databaseEnabled) {
            database.connect();
        }

        if (cacheEnabled && databaseEnabled) {
            cache.warmUp(database.getAllKeys());
        }
    }
}
```

### Pattern 3: Framework Integration

Integrate with external frameworks:

```java
@IocBean
@IocMultiProvider(OnLoad.class)
public class PlaceholderAPIIntegration implements OnLoad {

    @Override
    public void load(IocContainer iocContainer) {
        // Check if PlaceholderAPI is available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            // Register placeholder expansion
            PlaceholderExpansion expansion = iocContainer.get(MyPlaceholderExpansion.class);
            expansion.register();
        }
    }
}
```

### Pattern 4: Graceful Degradation

Initialize with fallbacks for missing dependencies:

```java
@TubingConfiguration
public class GracefulInitialization {

    @AfterIocLoad
    public static void initialize(
        DatabaseService database,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        try {
            database.connect();
            plugin.getLogger().info("Database connected");
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Database unavailable, using file storage");
            database.useFallbackStorage();
        }
    }
}
```

### Pattern 5: State Restoration

Restore state after reload:

```java
@IocBean
public class StateManager implements BeforeTubingReload {

    private final PlayerDataService playerData;
    private static Map<UUID, PlayerState> persistentState = new HashMap<>();

    public StateManager(PlayerDataService playerData) {
        this.playerData = playerData;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // Save state before reload
        persistentState = playerData.saveToMemory();
    }
}

@TubingConfiguration
public class StateRestoration {

    @AfterIocLoad
    public static void restoreState(PlayerDataService playerData) {
        // Restore state after reload
        if (StateManager.persistentState != null) {
            playerData.restoreFromMemory(StateManager.persistentState);
        }
    }
}
```

## Troubleshooting

### "@AfterIocLoad method not executing"

**Problem:** Your `@AfterIocLoad` method isn't being called.

**Possible causes:**
1. Method is not `static`
2. Method is not in a `@TubingConfiguration` class
3. Method is not `public`
4. Exception in a previous `@AfterIocLoad` method

**Solution:**
```java
@TubingConfiguration  // Must be in config class
public class MyConfiguration {

    @AfterIocLoad
    public static void init(/* params */) {  // Must be static and public
        // ...
    }
}
```

### "OnLoad bean not found"

**Problem:** Your `OnLoad` implementation isn't executing.

**Possible causes:**
1. Bean is not annotated with `@IocBean`
2. Bean is not registered as `@IocMultiProvider(OnLoad.class)`
3. Bean is in a package not scanned by ClassGraph

**Solution:**
```java
@IocBean
@IocMultiProvider(OnLoad.class)  // Must register as multi-provider
public class MyOnLoad implements OnLoad {
    @Override
    public void load(IocContainer iocContainer) {
        // ...
    }
}
```

### "BeforeTubingReload not executing"

**Problem:** Your cleanup hook isn't running before reload.

**Possible causes:**
1. Bean is not registered (missing `@IocBean`)
2. Using wrong platform-specific interface
3. Plugin doesn't support reload

**Solution:**
```java
@IocBean  // Must be a bean
public class MyCleanup implements BeforeTubingReload {  // Platform-specific interface
    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        // ...
    }
}
```

### "Null pointer in @AfterIocLoad"

**Problem:** A bean parameter is null.

**Possible causes:**
1. Bean doesn't exist in container
2. Bean is conditional and condition not met
3. Bean failed to instantiate

**Solution:** Check that the bean exists and is properly registered:
```java
@AfterIocLoad
public static void init(
    DatabaseService database,  // Ensure this bean exists
    @InjectTubingPlugin TubingPlugin plugin
) {
    if (database == null) {
        plugin.getLogger().severe("DatabaseService not available!");
        return;
    }
    database.connect();
}
```

## Summary

Post-initialization hooks in Tubing provide powerful mechanisms for executing code after beans are created:

- **`@AfterIocLoad`**: Simple static methods for initialization after all beans are ready
- **`OnLoad`**: Advanced hook with container access for dynamic manipulation
- **`BeforeTubingReload`**: Platform-specific cleanup before plugin reload

**Choose the right mechanism:**
- Use `@AfterIocLoad` for 90% of initialization needs
- Use `OnLoad` only when you need container access
- Use `BeforeTubingReload` for cleanup before reload

**Follow best practices:**
- Keep initialization simple and focused
- Use explicit ordering through parameters
- Handle errors gracefully
- Clean up resources properly
- Document initialization dependencies

## Next Steps

- **[Bean Lifecycle](Bean-Lifecycle.md)** - Understand the full lifecycle
- **[Bean Providers](Bean-Providers.md)** - Create beans with factory methods
- **[Configuration Injection](Configuration-Injection.md)** - Inject configuration
- **[IoC Container](IoC-Container.md)** - Master container access

---

**See also:**
- [Multi-Implementation](Multi-Implementation.md) - Working with lists of beans
- [Conditional Beans](Conditional-Beans.md) - Conditional registration
- [Bean Registration](Bean-Registration.md) - Register beans properly
