# Manual Bean Registration

Manual bean registration allows you to register beans with the IoC container at runtime, outside of the automatic annotation-based discovery process. This is essential when working with external libraries, creating beans dynamically based on runtime conditions, or integrating platform-provided objects.

## Overview

Tubing provides three primary ways to register beans:

1. **Automatic registration** - Using `@IocBean` and platform-specific annotations (preferred)
2. **Bean providers** - Using `@IocBeanProvider` in configuration classes (for complex initialization)
3. **Manual registration** - Using `IocContainer.registerBean()` (for runtime and external objects)

Manual registration is the most flexible approach but should be used sparingly. It gives you full control over when and how beans are registered, but bypasses the automatic dependency resolution and lifecycle management that makes Tubing powerful.

## The registerBean() Method

The `IocContainer.registerBean()` method allows you to manually register any object as a bean in the container.

### Method Signature

```java
public void registerBean(Object o) {
    beans.put(o.getClass(), o);
}
```

### How It Works

When you call `registerBean()`:

1. The object's exact class type is used as the key
2. The object is stored in the container's internal bean map
3. If a bean of that type already exists, it is replaced
4. The bean becomes immediately available for injection
5. The bean's lifecycle is NOT managed - you're responsible for initialization

**Important:** The bean is registered by its concrete class type, not by interfaces it implements. To make it injectable via an interface, use a bean provider instead.

### Basic Usage

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Create and configure an external library object
        ExternalService service = new ExternalService();
        service.setApiKey("your-api-key");
        service.initialize();

        // Register it with the container
        container.registerBean(service);

        // Now it's available for injection into other beans
    }
}
```

## When to Use Manual Registration

Manual registration is appropriate in specific scenarios where automatic discovery or bean providers don't fit.

### 1. External Library Instances

When integrating third-party libraries that you can't annotate:

```java
@Override
protected void enable() {
    IocContainer container = getIocContainer();

    // External library you don't control
    RedisClient redisClient = RedisClient.create("redis://localhost:6379");
    redisClient.connect();

    container.registerBean(redisClient);
}
```

**Note:** For most external library cases, bean providers are preferred:

```java
@TubingConfiguration
public class RedisConfiguration {

    @IocBeanProvider
    public static RedisClient provideRedisClient(
            @ConfigProperty("redis.url") String url) {
        return RedisClient.create(url);
    }
}
```

Bean providers integrate better with the container lifecycle and allow dependency injection into the provider method.

### 2. Platform-Provided Objects

Register platform-specific objects that aren't created by your plugin:

```java
// Bukkit example
@Override
protected void enable() {
    IocContainer container = getIocContainer();

    // Register Bukkit's Server instance
    container.registerBean(getServer());

    // Register PluginManager
    container.registerBean(getServer().getPluginManager());
}
```

Now these platform objects are injectable:

```java
@IocBean
public class WorldService {
    private final Server server;
    private final PluginManager pluginManager;

    public WorldService(Server server, PluginManager pluginManager) {
        this.server = server;
        this.pluginManager = pluginManager;
    }
}
```

**Note:** Again, bean providers are often cleaner:

```java
@TubingConfiguration
public class BukkitConfiguration {

    @IocBeanProvider
    public static Server provideServer(@InjectTubingPlugin TubingPlugin plugin) {
        return ((TubingBukkitPlugin) plugin).getServer();
    }
}
```

### 3. Runtime Bean Creation

Create and register beans based on runtime conditions or loaded data:

```java
@IocBean
public class PluginExtensionLoader {
    private final IocContainer container;

    public PluginExtensionLoader(IocContainer container) {
        this.container = container;
    }

    public void loadExtension(String extensionPath) {
        // Load extension configuration
        ExtensionConfig config = loadConfig(extensionPath);

        // Create extension instance based on loaded data
        PluginExtension extension = new PluginExtension(config);
        extension.initialize();

        // Register with container
        container.registerBean(extension);

        getLogger().info("Loaded extension: " + config.getName());
    }
}
```

### 4. Post-Initialization Registration

Register beans after the initial container initialization is complete:

```java
@TubingConfiguration
public class DynamicConfiguration {

    @AfterIocLoad
    public static void registerDynamicBeans(
            IocContainer container,
            @ConfigProperty("features.enabled") List<String> enabledFeatures) {

        for (String feature : enabledFeatures) {
            FeatureHandler handler = createFeatureHandler(feature);
            container.registerBean(handler);
        }
    }

    private static FeatureHandler createFeatureHandler(String feature) {
        switch (feature) {
            case "pvp": return new PvPFeatureHandler();
            case "economy": return new EconomyFeatureHandler();
            case "trading": return new TradingFeatureHandler();
            default: throw new IllegalArgumentException("Unknown feature: " + feature);
        }
    }
}
```

### 5. Testing and Mocking

Replace beans with mocks or test doubles during testing:

```java
@Test
public void testPlayerService() {
    IocContainer container = new IocContainer();

    // Register mock dependencies
    PlayerRepository mockRepo = mock(PlayerRepository.class);
    container.registerBean(mockRepo);

    MessageService mockMessages = mock(MessageService.class);
    container.registerBean(mockMessages);

    // Now create service under test
    PlayerService service = container.get(PlayerService.class);

    // Test with mocked dependencies
    when(mockRepo.findById(any())).thenReturn(testPlayerData);
    service.savePlayer(testPlayer);

    verify(mockRepo).save(any());
}
```

### 6. Dynamic Plugin Integration

Allow other plugins to register services into your container:

```java
// Your plugin's API
@IocBean
public class MyPluginAPI {
    private final IocContainer container;

    public MyPluginAPI(IocContainer container) {
        this.container = container;
    }

    public void registerExtension(Object extension) {
        container.registerBean(extension);
        getLogger().info("Registered external extension: " +
            extension.getClass().getSimpleName());
    }
}

// Other plugin using your API
public class OtherPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        MyPluginAPI api = getMyPluginAPI();
        api.registerExtension(new MyCustomExtension());
    }
}
```

## Limitations and Caveats

Manual bean registration has several important limitations to be aware of.

### 1. No Dependency Injection on Registration

When you register a bean manually, the container does NOT:

- Inject `@ConfigProperty` fields
- Inject `@InjectTubingPlugin` fields
- Call any initialization methods
- Validate the bean's dependencies

You must handle all initialization before registration:

```java
// Wrong - dependencies not satisfied
ExternalService service = new ExternalService();
container.registerBean(service);  // Service may not work correctly

// Correct - fully initialize first
ExternalService service = new ExternalService();
service.setApiKey(config.getApiKey());
service.setEndpoint(config.getEndpoint());
service.initialize();
container.registerBean(service);  // Now it's ready
```

### 2. Registered by Concrete Class Only

Beans registered with `registerBean()` are stored by their exact class type, not by interfaces:

```java
public interface NotificationService {
    void notify(String message);
}

public class EmailNotificationService implements NotificationService {
    public void notify(String message) { /* ... */ }
}

// Register the implementation
EmailNotificationService service = new EmailNotificationService();
container.registerBean(service);

// This works - concrete class
EmailNotificationService retrieved = container.get(EmailNotificationService.class);

// This FAILS - interface not registered
NotificationService retrieved = container.get(NotificationService.class);
// IocException: Cannot retrieve bean with interface NotificationService
```

**Solution:** Use a bean provider to register by interface:

```java
@TubingConfiguration
public class NotificationConfiguration {

    @IocBeanProvider
    public static NotificationService provideNotificationService() {
        return new EmailNotificationService();
    }
}
```

### 3. No Lifecycle Management

Manually registered beans don't participate in the standard lifecycle:

- Not created in dependency order
- Not eligible for `@AfterIocLoad` processing
- Not automatically cleaned up on plugin disable

You're responsible for managing the bean's complete lifecycle:

```java
private ExternalService externalService;

@Override
protected void enable() {
    // Initialize
    externalService = new ExternalService();
    externalService.connect();
    container.registerBean(externalService);
}

@Override
protected void disable() {
    // Must clean up manually
    if (externalService != null) {
        externalService.disconnect();
        externalService = null;
    }
}
```

### 4. Late Registration Issues

Beans registered after container initialization may be missed by components that were already initialized:

```java
@IocBean
public class ServiceA {
    private final ServiceB serviceB;

    // ServiceB must exist when ServiceA is created
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

// This FAILS if ServiceA is already created
container.registerBean(new ServiceB());  // Too late!
```

**Solution:** Register beans before container initialization, or use `@AfterIocLoad` for post-initialization registration:

```java
@TubingConfiguration
public class LateConfiguration {

    @AfterIocLoad
    public static void registerLateBean(IocContainer container) {
        // Safe to register here - before platform registration
        container.registerBean(new ServiceB());
    }
}
```

### 5. Singleton Replacement

Registering a bean of a type that already exists replaces the existing bean:

```java
// Original bean
@IocBean
public class ConfigService {
    // Original implementation
}

// Later, in enable()
ConfigService newService = new ConfigService();
container.registerBean(newService);  // Replaces the original!
```

This can break beans that already received the original instance via constructor injection. Use with extreme caution.

### 6. No Multi-Provider Support

`registerBean()` doesn't support the multi-provider pattern directly. If you need to add to a list of implementations, you must manually manage the list:

```java
// This doesn't work as expected
Handler handler1 = new Handler();
Handler handler2 = new Handler();
container.registerBean(handler1);  // Stored
container.registerBean(handler2);  // Replaces handler1!

List<Handler> handlers = container.getList(Handler.class);
// Only contains handler2
```

**Solution:** Use `@IocMultiProvider` annotation on classes or provider methods instead.

## Best Practices

Follow these guidelines when using manual bean registration.

### 1. Prefer Annotation-Based Registration

Always start with automatic registration:

```java
// Preferred - automatic registration
@IocBean
public class MyService {
    private final MyRepository repository;

    public MyService(MyRepository repository) {
        this.repository = repository;
    }
}
```

Only use manual registration when automatic registration is impossible or impractical.

### 2. Use Bean Providers for External Libraries

Bean providers are cleaner and integrate better than manual registration:

```java
// Good - bean provider
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static HikariDataSource provideDataSource(
            @ConfigProperty("database.url") String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        return new HikariDataSource(config);
    }
}

// Avoid - manual registration
@Override
protected void enable() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(getConfig().getString("database.url"));
    HikariDataSource ds = new HikariDataSource(config);
    getIocContainer().registerBean(ds);
}
```

Bean providers support dependency injection, are called during proper lifecycle phases, and make code more testable.

### 3. Initialize Beans Completely Before Registration

Ensure beans are fully configured and ready to use:

```java
// Good - fully initialized
@Override
protected void enable() {
    ExternalService service = new ExternalService();
    service.setConfig(loadConfiguration());
    service.authenticate(apiKey);
    service.connect();

    if (!service.isConnected()) {
        throw new RuntimeException("Failed to connect to external service");
    }

    container.registerBean(service);
}

// Bad - incomplete initialization
@Override
protected void enable() {
    ExternalService service = new ExternalService();
    container.registerBean(service);  // Not ready!
    service.setConfig(loadConfiguration());
}
```

### 4. Document Manual Registrations

Make it clear why manual registration is necessary:

```java
@Override
protected void enable() {
    IocContainer container = getIocContainer();

    // Manual registration required: ExternalLibrary is from a third-party
    // JAR we don't control, and it requires complex initialization that
    // would be awkward in a bean provider.
    ExternalLibrary library = ExternalLibrary.builder()
        .apiKey(config.getString("api-key"))
        .timeout(config.getInt("timeout"))
        .retries(config.getInt("retries"))
        .build();

    library.initialize();
    container.registerBean(library);
}
```

### 5. Handle Cleanup in disable()

Manually registered beans often need manual cleanup:

```java
private DatabaseConnection connection;

@Override
protected void enable() {
    connection = new DatabaseConnection(config);
    connection.connect();
    getIocContainer().registerBean(connection);
}

@Override
protected void disable() {
    if (connection != null) {
        try {
            connection.close();
        } catch (Exception e) {
            getLogger().warning("Error closing database: " + e.getMessage());
        }
    }
}
```

### 6. Register Early in Lifecycle

Register beans as early as possible, ideally in your `enable()` method before other initialization:

```java
@Override
protected void enable() {
    // Register beans first
    registerPlatformBeans();
    registerExternalServices();

    // Then perform other initialization
    initializeFeatures();
    startScheduledTasks();
}

private void registerPlatformBeans() {
    IocContainer container = getIocContainer();
    container.registerBean(getServer());
    container.registerBean(getServer().getPluginManager());
}
```

### 7. Consider Thread Safety

If registering beans from async tasks or other threads, ensure thread safety:

```java
// Potentially unsafe if called from multiple threads
public void loadFeatureAsync(String feature) {
    CompletableFuture.runAsync(() -> {
        FeatureHandler handler = createHandler(feature);
        container.registerBean(handler);  // Race condition risk
    });
}

// Better - synchronize or use proper lifecycle hooks
public void loadFeatureAsync(String feature) {
    CompletableFuture.runAsync(() -> {
        FeatureHandler handler = createHandler(feature);

        // Schedule registration on main thread during next tick
        Bukkit.getScheduler().runTask(this, () -> {
            container.registerBean(handler);
        });
    });
}
```

### 8. Validate Before Registration

Check that beans are valid before registering:

```java
public void registerExternalService(ExternalService service) {
    if (service == null) {
        throw new IllegalArgumentException("Service cannot be null");
    }

    if (!service.isInitialized()) {
        throw new IllegalStateException("Service must be initialized before registration");
    }

    if (!service.isConnected()) {
        getLogger().warning("Registering disconnected service - may not work correctly");
    }

    container.registerBean(service);
}
```

## Common Patterns

### Pattern: Platform Object Registration

Register platform-provided objects for injection:

```java
@Override
protected void enable() {
    IocContainer container = getIocContainer();

    // Register Bukkit platform objects
    container.registerBean(getServer());
    container.registerBean(getServer().getPluginManager());
    container.registerBean(getServer().getScheduler());

    // Register this plugin instance (for non-Tubing beans)
    container.registerBean(this);
}
```

### Pattern: Plugin Extension System

Allow other plugins to extend your plugin through the container:

```java
@IocBean
public class ExtensionManager {
    private final IocContainer container;
    private final List<PluginExtension> extensions = new ArrayList<>();

    public ExtensionManager(IocContainer container) {
        this.container = container;
    }

    public void registerExtension(PluginExtension extension) {
        extension.initialize();
        extensions.add(extension);
        container.registerBean(extension);

        getLogger().info("Registered extension: " + extension.getName());
    }

    public List<PluginExtension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }
}
```

### Pattern: Configuration-Based Registration

Register beans based on configuration:

```java
@TubingConfiguration
public class DynamicFeatureConfiguration {

    @AfterIocLoad
    public static void registerFeatures(
            IocContainer container,
            @ConfigProperty("features") List<String> features) {

        for (String feature : features) {
            FeatureHandler handler = createFeature(feature);
            if (handler != null) {
                container.registerBean(handler);
            }
        }
    }

    private static FeatureHandler createFeature(String name) {
        switch (name) {
            case "economy": return new EconomyFeature();
            case "permissions": return new PermissionsFeature();
            case "chat": return new ChatFeature();
            default:
                getLogger().warning("Unknown feature: " + name);
                return null;
        }
    }
}
```

### Pattern: Lazy Bean Registration

Register beans only when needed:

```java
@IocBean
public class FeatureService {
    private final IocContainer container;
    private HeavyFeature heavyFeature;

    public FeatureService(IocContainer container) {
        this.container = container;
    }

    public void enableHeavyFeature() {
        if (heavyFeature == null) {
            getLogger().info("Loading heavy feature...");
            heavyFeature = new HeavyFeature();
            heavyFeature.initialize();
            container.registerBean(heavyFeature);
        }
    }

    public boolean isHeavyFeatureEnabled() {
        return heavyFeature != null;
    }
}
```

### Pattern: Conditional Runtime Registration

Register different implementations based on runtime conditions:

```java
@Override
protected void enable() {
    IocContainer container = getIocContainer();

    // Check if optional dependency is available
    if (hasVaultPlugin()) {
        VaultEconomy economy = new VaultEconomy();
        economy.hookVault();
        container.registerBean(economy);
    } else if (hasEssentialsPlugin()) {
        EssentialsEconomy economy = new EssentialsEconomy();
        economy.hookEssentials();
        container.registerBean(economy);
    } else {
        DefaultEconomy economy = new DefaultEconomy();
        container.registerBean(economy);
    }
}

private boolean hasVaultPlugin() {
    return getServer().getPluginManager().getPlugin("Vault") != null;
}
```

## Comparison with Other Registration Methods

Understanding when to use each registration method:

### @IocBean (Automatic)

**When to use:**
- You control the class source code
- Simple constructor-based dependency injection is sufficient
- Standard singleton lifecycle is appropriate

**Pros:**
- Zero boilerplate
- Automatic dependency resolution
- Integrated lifecycle management
- Easy to test

**Cons:**
- Requires annotation on the class
- Limited to classes in your package
- Single constructor only

**Example:**
```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}
```

### @IocBeanProvider (Configuration Method)

**When to use:**
- Complex initialization logic required
- External library classes
- Multiple configuration parameters needed
- Factory pattern implementation
- Interface-based injection preferred

**Pros:**
- Full control over initialization
- Supports dependency injection in provider method
- Cleaner than manual registration
- Integrated with container lifecycle
- Can return interface types

**Cons:**
- More verbose than @IocBean
- Static methods required
- Must be in @TubingConfiguration class

**Example:**
```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static DataSource provideDataSource(
            @ConfigProperty("database.url") String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        return new HikariDataSource(config);
    }
}
```

### registerBean() (Manual)

**When to use:**
- Runtime bean creation needed
- Platform-provided objects
- Post-initialization registration
- Testing with mocks
- Dynamic plugin integration

**Pros:**
- Maximum flexibility
- Can register any object at any time
- No annotations required
- Works with any object

**Cons:**
- No automatic dependency injection
- No lifecycle management
- Registered by concrete class only
- Must handle initialization manually
- Easy to misuse

**Example:**
```java
@Override
protected void enable() {
    ExternalService service = new ExternalService();
    service.initialize();
    getIocContainer().registerBean(service);
}
```

## Troubleshooting

### Issue: Bean Not Injectable by Interface

**Problem:** Manually registered bean can't be injected by its interface type.

```java
NotificationService service = new EmailNotificationService();
container.registerBean(service);

// This fails
NotificationService retrieved = container.get(NotificationService.class);
```

**Solution:** Use a bean provider instead:

```java
@TubingConfiguration
public class NotificationConfiguration {
    @IocBeanProvider
    public static NotificationService provideNotificationService() {
        return new EmailNotificationService();
    }
}
```

### Issue: Dependencies Not Injected

**Problem:** Fields annotated with `@ConfigProperty` or `@InjectTubingPlugin` are null after manual registration.

```java
ExternalService service = new ExternalService();
container.registerBean(service);
// service's @ConfigProperty fields are null!
```

**Solution:** Manual registration doesn't trigger injection. Initialize manually or use a bean provider:

```java
// Option 1: Initialize manually
ExternalService service = new ExternalService();
service.setConfig(config.getString("key"));
container.registerBean(service);

// Option 2: Use bean provider (automatic injection)
@IocBeanProvider
public static ExternalService provideExternalService(
        @ConfigProperty("key") String key) {
    return new ExternalService(key);
}
```

### Issue: Late Registration Causes Errors

**Problem:** Bean registered after other beans tried to use it.

```java
@Override
protected void enable() {
    // ServiceA is created during container init
    // ServiceA constructor tries to get ServiceB
    // ServiceB doesn't exist yet - error!

    ServiceB serviceB = new ServiceB();
    container.registerBean(serviceB);  // Too late
}
```

**Solution:** Register early, or use `@AfterIocLoad`:

```java
@TubingConfiguration
public class Configuration {
    @AfterIocLoad
    public static void registerLateBeans(IocContainer container) {
        ServiceB serviceB = new ServiceB();
        container.registerBean(serviceB);
    }
}
```

### Issue: Bean Replaced Unexpectedly

**Problem:** Registering a bean replaces an existing bean of the same type.

```java
@IocBean
public class CacheService { /* ... */ }

// Later
CacheService newCache = new CacheService();
container.registerBean(newCache);  // Replaces original!
```

**Solution:** Don't manually register types that are already managed by the container. If you need multiple instances, use the multi-provider pattern or manage them outside the container.

## Next Steps

Now that you understand manual bean registration:

- Review [Bean Providers](../core/Bean-Providers.md) for cleaner external library integration
- Learn about [Post-Initialization](../core/Post-Initialization.md) for proper timing of registration
- Explore [IoC Container](../core/IoC-Container.md) for container fundamentals
- Check [Bean Lifecycle](../core/Bean-Lifecycle.md) to understand when beans are created

---

**See also:**
- [Bean Registration](../core/Bean-Registration.md) - Annotation-based registration
- [Dependency Injection](../core/Dependency-Injection.md) - Injection patterns
- [Configuration Injection](../core/Configuration-Injection.md) - Injecting configuration values
- [Conditional Beans](../core/Conditional-Beans.md) - Conditional registration
