# Bean Providers

Bean providers are factory methods that create and configure beans when you need more control than simple annotation-based registration. They allow you to encapsulate complex initialization logic, integrate external libraries, and conditionally create beans based on runtime conditions.

## Overview

While `@IocBean` works great for most cases, sometimes you need more flexibility:

- **Complex initialization logic** - Multiple configuration steps or builder patterns
- **External libraries** - Classes you don't control and can't annotate
- **Multiple configuration sources** - Beans that need values from multiple config files
- **Conditional creation** - Runtime decisions about which implementation to create
- **Factory patterns** - Creating different implementations based on configuration

Bean providers give you this flexibility through annotated methods in configuration classes.

## @IocBeanProvider

The `@IocBeanProvider` annotation marks a method as a bean factory. When the IoC container initializes, it calls provider methods to create beans.

### Basic Provider

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static Database provideDatabase() {
        // Create and configure the bean
        Database db = new Database();
        db.initialize();
        return db;
    }
}
```

**Key requirements:**

- Provider methods must be **static**
- Must be in a class annotated with `@TubingConfiguration`
- Return type determines the bean type registered in the container
- Method name can be anything (convention is `provide<BeanName>`)

### Provider with Dependencies

Provider methods can accept parameters, which are resolved by the container just like constructor parameters:

```java
@TubingConfiguration
public class ServiceConfiguration {

    @IocBeanProvider
    public static NotificationService provideNotificationService(
            DatabaseService database,
            @ConfigProperty("notifications.enabled") boolean enabled) {

        NotificationService service = new NotificationService(database);
        service.setEnabled(enabled);
        return service;
    }
}
```

**Supported parameter types:**

- **Other beans** - Any bean registered in the container
- **Configuration properties** - Values from config files with `@ConfigProperty`
- **Plugin instance** - The plugin instance with `@InjectTubingPlugin`
- **Multi-implementations** - Lists of implementations with `@IocMulti`

### Provider with Complex Configuration

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static HikariDataSource provideDataSource(
            @ConfigProperty("database.host") String host,
            @ConfigProperty("database.port") int port,
            @ConfigProperty("database.name") String database,
            @ConfigProperty("database.username") String username,
            @ConfigProperty("database.password") String password,
            @ConfigProperty("database.pool.size") int poolSize) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}
```

This pattern is cleaner than injecting multiple config properties into a constructor.

### Factory Pattern Provider

Providers are perfect for implementing the factory pattern:

```java
@TubingConfiguration
public class StorageConfiguration {

    @IocBeanProvider
    public static StorageService provideStorageService(
            @ConfigProperty("storage.type") String type,
            DatabaseService database) {

        switch (type.toLowerCase()) {
            case "mysql":
                return new MySQLStorageService(database);
            case "sqlite":
                return new SQLiteStorageService(database);
            case "json":
                return new JsonStorageService();
            case "yaml":
                return new YamlStorageService();
            default:
                throw new IllegalArgumentException("Unknown storage type: " + type);
        }
    }
}
```

The returned implementation can be injected anywhere `StorageService` is needed:

```java
@IocBean
public class DataManager {
    private final StorageService storage;

    // Receives the implementation created by the provider
    public DataManager(StorageService storage) {
        this.storage = storage;
    }
}
```

### External Library Integration

When integrating third-party libraries, you often can't annotate their classes. Providers solve this:

```java
@TubingConfiguration
public class RedisConfiguration {

    @IocBeanProvider
    public static JedisPool provideRedisPool(
            @ConfigProperty("redis.host") String host,
            @ConfigProperty("redis.port") int port,
            @ConfigProperty("redis.password") String password) {

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);

        return new JedisPool(poolConfig, host, port, 2000, password);
    }
}
```

Now `JedisPool` is available for injection:

```java
@IocBean
public class CacheService {
    private final JedisPool redisPool;

    public CacheService(JedisPool redisPool) {
        this.redisPool = redisPool;
    }

    public void set(String key, String value) {
        try (Jedis jedis = redisPool.getResource()) {
            jedis.set(key, value);
        }
    }
}
```

## @IocBeanMultiProvider

The `@IocBeanMultiProvider` annotation creates multiple beans of the same type. Use it when you want to provide a collection of implementations through a provider method instead of individual class annotations.

### Basic Multi-Provider

```java
@TubingConfiguration
public class RewardConfiguration {

    @IocBeanMultiProvider(RewardHandler.class)
    public static List<RewardHandler> provideRewardHandlers(
            EconomyService economy,
            @ConfigProperty("rewards.enabled") List<String> enabledRewards) {

        List<RewardHandler> handlers = new ArrayList<>();

        if (enabledRewards.contains("money")) {
            handlers.add(new MoneyRewardHandler(economy));
        }
        if (enabledRewards.contains("items")) {
            handlers.add(new ItemRewardHandler());
        }
        if (enabledRewards.contains("experience")) {
            handlers.add(new ExperienceRewardHandler());
        }

        return handlers;
    }
}
```

The returned list is registered in the container and can be injected with `@IocMulti`:

```java
@IocBean
public class RewardService {
    private final List<RewardHandler> handlers;

    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers) {
        this.handlers = handlers;
    }

    public void giveReward(Player player, Reward reward) {
        for (RewardHandler handler : handlers) {
            handler.handle(player, reward);
        }
    }
}
```

### Multi-Provider vs @IocMultiProvider Annotation

There are two ways to create multiple implementations:

**Option 1: Annotate classes** (decentralized):

```java
@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerJoinHandler implements EventHandler {
    // Implementation
}

@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerQuitHandler implements EventHandler {
    // Implementation
}
```

**Option 2: Provider method** (centralized):

```java
@TubingConfiguration
public class EventConfiguration {

    @IocBeanMultiProvider(EventHandler.class)
    public static List<EventHandler> provideEventHandlers(
            PlayerService playerService,
            LoggingService logger) {

        return Arrays.asList(
            new PlayerJoinHandler(playerService),
            new PlayerQuitHandler(playerService),
            new ErrorLoggingHandler(logger)
        );
    }
}
```

**When to use each approach:**

- **Class annotation**: Implementations are in your codebase and independent
- **Provider method**: Implementations need shared configuration or complex setup

### Dynamic Multi-Provider

Create implementations dynamically based on configuration:

```java
@TubingConfiguration
public class CommandConfiguration {

    @IocBeanMultiProvider(CustomCommand.class)
    public static List<CustomCommand> provideCustomCommands(
            @ConfigProperty("custom-commands")
            @ConfigObjectList(CommandConfig.class)
            List<CommandConfig> commandConfigs,
            PermissionService permissions) {

        List<CustomCommand> commands = new ArrayList<>();

        for (CommandConfig config : commandConfigs) {
            CustomCommand command = new CustomCommand(
                config.getName(),
                config.getPermission(),
                config.getActions(),
                permissions
            );
            commands.add(command);
        }

        return commands;
    }
}
```

This pattern allows users to define commands in configuration:

```yaml
custom-commands:
  - name: "shop"
    permission: "myplugin.shop"
    actions:
      - "[MESSAGE] Opening shop..."
      - "[GUI] shop_menu"
  - name: "spawn"
    permission: "myplugin.spawn"
    actions:
      - "[TELEPORT] spawn"
```

## @TubingConfiguration

The `@TubingConfiguration` annotation marks a class as a configuration class containing provider methods.

### Basic Configuration Class

```java
@TubingConfiguration
public class AppConfiguration {

    @IocBeanProvider
    public static ServiceA provideServiceA() {
        return new ServiceA();
    }

    @IocBeanProvider
    public static ServiceB provideServiceB(ServiceA serviceA) {
        return new ServiceB(serviceA);
    }
}
```

**Requirements:**

- Class must be annotated with `@TubingConfiguration`
- Must be in your plugin's package (scanned by ClassGraph)
- Provider methods must be static
- Can contain multiple provider methods

### Organizing Providers

Group related providers into logical configuration classes:

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static DataSource provideDataSource(...) { }

    @IocBeanProvider
    public static DatabaseMigrator provideMigrator(...) { }

    @IocBeanProvider
    public static ConnectionPool provideConnectionPool(...) { }
}

@TubingConfiguration
public class SecurityConfiguration {

    @IocBeanProvider
    public static PasswordEncoder providePasswordEncoder() { }

    @IocBeanProvider
    public static AuthenticationService provideAuthService(...) { }

    @IocBeanProvider
    public static PermissionService providePermissions(...) { }
}

@TubingConfiguration
public class MessagingConfiguration {

    @IocBeanProvider
    public static MessageFormatter provideFormatter(...) { }

    @IocBeanProvider
    public static TranslationService provideTranslations(...) { }
}
```

### Multiple Configuration Classes

You can have as many configuration classes as needed. The container discovers all of them during startup:

```
com.example.myplugin/
├── config/
│   ├── DatabaseConfiguration.java
│   ├── CacheConfiguration.java
│   ├── SecurityConfiguration.java
│   └── IntegrationConfiguration.java
├── services/
├── commands/
└── MyPlugin.java
```

## When to Use Providers vs @IocBean

Choosing between `@IocBean` and providers depends on your use case:

### Use @IocBean When:

**1. You control the class**

```java
@IocBean
public class PlayerService {
    private final DatabaseService database;

    public PlayerService(DatabaseService database) {
        this.database = database;
    }
}
```

**2. Initialization is straightforward**

```java
@IocBean
public class MessageFormatter {
    @ConfigProperty("messages.prefix")
    private String prefix;

    public String format(String message) {
        return prefix + message;
    }
}
```

**3. The class is a service, listener, or command**

```java
@IocBukkitListener
public class PlayerListener implements Listener {
    // Bukkit listener
}

@IocBukkitCommandHandler("spawn")
public class SpawnCommand {
    // Command handler
}
```

### Use Providers When:

**1. External libraries (can't annotate)**

```java
@IocBeanProvider
public static Gson provideGson() {
    return new GsonBuilder()
        .setPrettyPrinting()
        .create();
}
```

**2. Complex initialization logic**

```java
@IocBeanProvider
public static HikariDataSource provideDataSource(...) {
    HikariConfig config = new HikariConfig();
    // 15 lines of configuration
    return new HikariDataSource(config);
}
```

**3. Factory patterns**

```java
@IocBeanProvider
public static StorageService provideStorage(
        @ConfigProperty("storage.type") String type) {
    switch (type) {
        case "mysql": return new MySQLStorage();
        case "sqlite": return new SQLiteStorage();
        default: return new JsonStorage();
    }
}
```

**4. Conditional creation**

```java
@IocBeanProvider
public static CacheService provideCache(
        @ConfigProperty("cache.enabled") boolean enabled,
        @ConfigProperty("cache.type") String type) {

    if (!enabled) {
        return new NoOpCacheService();
    }

    if (type.equals("redis")) {
        return new RedisCacheService();
    }

    return new InMemoryCacheService();
}
```

**5. Multiple configuration sources**

```java
@IocBeanProvider
public static EmailService provideEmailService(
        @ConfigProperty("email.host") String host,
        @ConfigProperty("email.port") int port,
        @ConfigProperty("email.username") String username,
        @ConfigProperty("email.password") String password,
        @ConfigProperty("email.from") String from,
        @ConfigProperty("email.tls.enabled") boolean tls) {

    EmailConfig config = EmailConfig.builder()
        .host(host)
        .port(port)
        .credentials(username, password)
        .from(from)
        .useTLS(tls)
        .build();

    return new EmailService(config);
}
```

**Rule of thumb:** If initialization takes more than 3 lines or uses external classes, use a provider.

## Provider Method Parameters

Provider methods support the same parameter injection as constructors:

### Bean Dependencies

Inject other beans by declaring them as parameters:

```java
@IocBeanProvider
public static PlayerService providePlayerService(
        DatabaseService database,
        CacheService cache,
        PermissionService permissions) {

    return new PlayerService(database, cache, permissions);
}
```

Dependencies are resolved automatically. The container ensures beans are created in the correct order.

### Configuration Properties

Inject configuration values with `@ConfigProperty`:

```java
@IocBeanProvider
public static ServerConnection provideServerConnection(
        @ConfigProperty("server.host") String host,
        @ConfigProperty("server.port") int port,
        @ConfigProperty("server.timeout") int timeout) {

    return new ServerConnection(host, port, timeout);
}
```

Supports all config property types:
- Primitives (`int`, `boolean`, `double`, etc.)
- Strings
- Lists and Maps
- Complex objects with `@ConfigEmbeddedObject`
- Object lists with `@ConfigObjectList`

### Plugin Instance

Inject the plugin instance with `@InjectTubingPlugin`:

```java
@IocBeanProvider
public static BukkitSchedulerService provideScheduler(
        @InjectTubingPlugin TubingBukkitPlugin plugin) {

    return new BukkitSchedulerService(plugin.getServer().getScheduler(), plugin);
}
```

### Multi-Implementation Lists

Inject all implementations of an interface with `@IocMulti`:

```java
@IocBeanProvider
public static CommandRegistry provideCommandRegistry(
        @IocMulti(CommandHandler.class) List<CommandHandler> handlers,
        @InjectTubingPlugin TubingPlugin plugin) {

    CommandRegistry registry = new CommandRegistry(plugin);
    handlers.forEach(registry::register);
    return registry;
}
```

### Mixed Parameters

Combine all parameter types:

```java
@IocBeanProvider
public static AdvancedService provideAdvancedService(
        DatabaseService database,                           // Bean dependency
        @ConfigProperty("service.enabled") boolean enabled,  // Config value
        @InjectTubingPlugin TubingPlugin plugin,            // Plugin instance
        @IocMulti(Plugin.class) List<Plugin> plugins) {     // Multiple implementations

    return new AdvancedService(database, enabled, plugin, plugins);
}
```

## Conditional Providers

Providers can create beans conditionally using standard Java control flow:

### Configuration-Based Conditions

```java
@IocBeanProvider
public static CacheService provideCacheService(
        @ConfigProperty("cache.enabled") boolean enabled) {

    if (!enabled) {
        return new NoOpCacheService(); // Disabled cache
    }

    return new InMemoryCacheService(); // Enabled cache
}
```

### Type-Based Conditions

```java
@IocBeanProvider
public static StorageService provideStorageService(
        @ConfigProperty("storage.backend") String backend,
        DatabaseService database) {

    switch (backend.toLowerCase()) {
        case "mysql":
            return new MySQLStorageService(database);
        case "sqlite":
            return new SQLiteStorageService(database);
        case "mongodb":
            return new MongoDBStorageService();
        case "redis":
            return new RedisStorageService();
        default:
            throw new IllegalArgumentException("Unknown storage backend: " + backend);
    }
}
```

### Null Return (Bean Not Created)

Providers can return `null` to skip bean creation:

```java
@IocBeanProvider
public static OptionalFeature provideOptionalFeature(
        @ConfigProperty("features.optional.enabled") boolean enabled) {

    if (!enabled) {
        return null; // Bean not created
    }

    return new OptionalFeature();
}
```

However, this can cause issues if other beans depend on it. Consider using a no-op implementation instead:

```java
@IocBeanProvider
public static OptionalFeature provideOptionalFeature(
        @ConfigProperty("features.optional.enabled") boolean enabled) {

    if (!enabled) {
        return new DisabledOptionalFeature(); // No-op implementation
    }

    return new EnabledOptionalFeature();
}
```

### Combining with @ConditionalOnProperty

For simple on/off conditions, use `@IocBean` with `conditionalOnProperty` instead:

```java
// Instead of this provider:
@IocBeanProvider
public static FeatureService provideFeature(
        @ConfigProperty("feature.enabled") boolean enabled) {
    if (!enabled) return null;
    return new FeatureService();
}

// Use this:
@IocBean(conditionalOnProperty = "feature.enabled")
public class FeatureService {
    // Only created if feature.enabled = true
}
```

Reserve providers for complex conditions:

```java
@IocBeanProvider
public static PaymentProcessor providePaymentProcessor(
        @ConfigProperty("payment.provider") String provider,
        @ConfigProperty("payment.api-key") String apiKey,
        @ConfigProperty("payment.sandbox") boolean sandbox) {

    // Complex condition combining multiple properties
    if (apiKey == null || apiKey.isEmpty()) {
        return new DisabledPaymentProcessor();
    }

    switch (provider) {
        case "stripe":
            return new StripeProcessor(apiKey, sandbox);
        case "paypal":
            return new PayPalProcessor(apiKey, sandbox);
        default:
            return new DisabledPaymentProcessor();
    }
}
```

## Common Provider Patterns

### Builder Pattern Integration

Providers work well with builder patterns:

```java
@IocBeanProvider
public static EmailService provideEmailService(
        @ConfigProperty("email.host") String host,
        @ConfigProperty("email.port") int port,
        @ConfigProperty("email.username") String username,
        @ConfigProperty("email.password") String password) {

    return EmailService.builder()
        .host(host)
        .port(port)
        .authentication(username, password)
        .enableTLS(true)
        .connectionTimeout(30)
        .build();
}
```

### Decorator Pattern

Wrap beans with additional functionality:

```java
@IocBeanProvider
public static PlayerService providePlayerService(
        DatabaseService database,
        @ConfigProperty("cache.enabled") boolean cacheEnabled) {

    PlayerService service = new PlayerServiceImpl(database);

    if (cacheEnabled) {
        service = new CachedPlayerService(service);
    }

    return service;
}
```

### Proxy Pattern

Create proxies for cross-cutting concerns:

```java
@IocBeanProvider
public static DataService provideDataService(
        DatabaseService database,
        LoggingService logger) {

    DataService service = new DataServiceImpl(database);

    // Wrap with logging proxy
    return new LoggingDataServiceProxy(service, logger);
}
```

### Singleton Wrapping

Wrap third-party singletons:

```java
@IocBeanProvider
public static VaultEconomy provideVaultEconomy() {
    // Vault uses a singleton pattern
    RegisteredServiceProvider<Economy> rsp =
        Bukkit.getServicesManager().getRegistration(Economy.class);

    if (rsp == null) {
        throw new IllegalStateException("Vault economy not found");
    }

    return new VaultEconomy(rsp.getProvider());
}
```

### Lazy Initialization Wrapper

Create beans that initialize on first use:

```java
@IocBeanProvider
public static ExpensiveService provideExpensiveService(
        @ConfigProperty("service.enabled") boolean enabled,
        DatabaseService database) {

    if (!enabled) {
        return new DisabledExpensiveService();
    }

    // Return lazy wrapper
    return new LazyExpensiveService(() -> {
        ExpensiveService service = new ExpensiveServiceImpl(database);
        service.loadAllData(); // Heavy operation deferred
        return service;
    });
}
```

### Platform Abstraction

Abstract platform differences:

```java
@IocBeanProvider
public static SchedulerService provideScheduler(
        @InjectTubingPlugin TubingPlugin plugin) {

    if (plugin instanceof TubingBukkitPlugin) {
        TubingBukkitPlugin bukkit = (TubingBukkitPlugin) plugin;
        return new BukkitSchedulerService(bukkit.getServer().getScheduler(), bukkit);
    }

    if (plugin instanceof TubingBungeePlugin) {
        TubingBungeePlugin bungee = (TubingBungeePlugin) plugin;
        return new BungeeSchedulerService(bungee.getProxy().getScheduler());
    }

    throw new UnsupportedOperationException("Platform not supported");
}
```

### Multi-Provider with Filtering

Create filtered lists:

```java
@IocBeanMultiProvider(Plugin.class)
public static List<Plugin> providePlugins(
        @IocMulti(Plugin.class) List<Plugin> allPlugins,
        @ConfigProperty("plugins.enabled") List<String> enabledPlugins) {

    return allPlugins.stream()
        .filter(p -> enabledPlugins.contains(p.getName()))
        .collect(Collectors.toList());
}
```

## Best Practices

### 1. Keep Providers Simple

Providers should focus on bean creation, not business logic:

**Bad:**
```java
@IocBeanProvider
public static DataService provideDataService(DatabaseService db) {
    DataService service = new DataService(db);
    service.loadAllData();        // Side effect
    service.startBackgroundSync(); // Side effect
    return service;
}
```

**Good:**
```java
@IocBeanProvider
public static DataService provideDataService(DatabaseService db) {
    return new DataService(db);
}

@TubingConfiguration
public class DataConfiguration {
    @AfterIocLoad
    public static void initializeData(DataService service) {
        service.loadAllData();
        service.startBackgroundSync();
    }
}
```

### 2. Use Descriptive Names

Follow the `provide<BeanType>` naming convention:

```java
@IocBeanProvider
public static HikariDataSource provideDataSource(...) { }

@IocBeanProvider
public static Redis provideRedisClient(...) { }

@IocBeanProvider
public static Gson provideGsonSerializer(...) { }
```

### 3. Group Related Providers

Organize providers into logical configuration classes:

```java
@TubingConfiguration
public class DatabaseConfiguration {
    // All database-related providers
}

@TubingConfiguration
public class CacheConfiguration {
    // All cache-related providers
}
```

### 4. Document Complex Logic

Add comments for non-obvious provider logic:

```java
@IocBeanProvider
public static StorageService provideStorageService(
        @ConfigProperty("storage.type") String type,
        DatabaseService database) {

    // MySQL and PostgreSQL both use the SQL storage implementation
    // but with different connection pools
    if (type.equals("mysql") || type.equals("postgresql")) {
        return new SQLStorageService(database, type);
    }

    // NoSQL databases use document-based storage
    if (type.equals("mongodb") || type.equals("couchdb")) {
        return new DocumentStorageService(type);
    }

    throw new IllegalArgumentException("Unsupported storage type: " + type);
}
```

### 5. Validate Configuration

Validate config values in providers:

```java
@IocBeanProvider
public static ServerConnection provideConnection(
        @ConfigProperty("server.host") String host,
        @ConfigProperty("server.port") int port) {

    if (host == null || host.isEmpty()) {
        throw new IllegalArgumentException("server.host must be configured");
    }

    if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("server.port must be between 1 and 65535");
    }

    return new ServerConnection(host, port);
}
```

### 6. Use No-Op Implementations

Instead of returning `null`, use no-op implementations:

```java
@IocBeanProvider
public static MetricsService provideMetrics(
        @ConfigProperty("metrics.enabled") boolean enabled) {

    if (enabled) {
        return new BStatsMetrics();
    }

    return new DisabledMetrics(); // No-op implementation
}
```

### 7. Avoid Provider Chains

Don't create providers that only call other providers:

**Bad:**
```java
@IocBeanProvider
public static ServiceA provideServiceA() {
    return new ServiceA();
}

@IocBeanProvider
public static ServiceB provideServiceB(ServiceA serviceA) {
    return serviceA; // Just returns the parameter!
}
```

**Good:**
```java
@IocBeanProvider
public static ServiceA provideServiceA() {
    return new ServiceA();
}

// ServiceB uses ServiceA directly through injection
@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }
}
```

## Summary

Bean providers are a powerful tool for flexible bean creation in Tubing:

- **`@IocBeanProvider`** - Creates a single bean with factory logic
- **`@IocBeanMultiProvider`** - Creates multiple beans as a list
- **`@TubingConfiguration`** - Marks classes containing provider methods
- **Provider parameters** - Support all injection types (beans, config, plugin, multi)
- **Conditional providers** - Use Java control flow for complex conditions

Use providers when you need more control than `@IocBean` provides, especially for external libraries, complex initialization, or factory patterns.

## Next Steps

- **[Bean Lifecycle](Bean-Lifecycle.md)** - Understand when providers are called
- **[Configuration Injection](Configuration-Injection.md)** - Master `@ConfigProperty` usage
- **[Post-Initialization](Post-Initialization.md)** - Use `@AfterIocLoad` for setup logic
- **[Multi-Implementation](Multi-Implementation.md)** - Working with lists of beans
- **[Conditional Beans](Conditional-Beans.md)** - Advanced conditional registration

---

**See also:**
- [IoC Container](IoC-Container.md) - Container fundamentals
- [Dependency Injection](Dependency-Injection.md) - Injection patterns
- [Bean Registration](Bean-Registration.md) - Basic bean registration with `@IocBean`
