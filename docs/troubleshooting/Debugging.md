# Debugging

Debugging IoC containers can seem daunting at first, but with the right techniques and understanding of how Tubing works, most issues can be quickly identified and resolved. This comprehensive guide covers debugging strategies, common errors, logging approaches, and best practices for troubleshooting Tubing applications.

## Overview

Debugging Tubing applications typically falls into several categories:

1. **Bean Instantiation Issues**: Beans fail to create during container initialization
2. **Dependency Resolution Problems**: Dependencies can't be found or are ambiguous
3. **Configuration Errors**: Configuration values are missing, incorrect, or fail to inject
4. **Lifecycle Issues**: Beans initialize in the wrong order or at the wrong time
5. **Runtime Errors**: Issues that occur after initialization completes

This guide provides systematic approaches to identify and fix each category.

## Understanding IoC Container Errors

When the IoC container encounters an error during initialization, it throws an `IocException` with a descriptive message. Understanding these errors is the first step to debugging.

### Common IocException Messages

#### "No Bean annotation present"

**What it means:**
You're trying to inject a class that isn't registered as a bean.

**Example error:**
```
Mc-Ioc bean exception: [Cannot instantiate bean [com.example.MyService]. No Bean annotation present]
```

**Common causes:**
- Missing `@IocBean` annotation on the class
- Class is in a package outside your plugin's root package
- Typo in the class name or wrong class being referenced

**How to fix:**

```java
// Before (error)
public class MyService {
    // Missing @IocBean annotation
}

// After (fixed)
@IocBean
public class MyService {
    // Now properly registered
}
```

**Debug strategy:**
1. Verify the class has a bean annotation (`@IocBean`, `@IocBukkitListener`, etc.)
2. Check the class is in your plugin's package tree
3. Ensure no typos in class references
4. Use the container to verify: `container.get(MyService.class)` - this will give you a clearer error

#### "Only one constructor should be defined"

**What it means:**
Your bean class has multiple constructors, which violates IoC requirements.

**Example error:**
```
Mc-Ioc bean exception: [Cannot instantiate bean [com.example.MyService]. Only one constructor should be defined]
```

**Common causes:**
- Multiple constructors for different initialization scenarios
- Default constructor plus a parameterized constructor
- Generated constructors from Lombok or other tools

**How to fix:**

```java
// Before (error)
@IocBean
public class MyService {
    private String config;

    public MyService() {
        this.config = "default";
    }

    public MyService(String config) {  // Multiple constructors not allowed
        this.config = config;
    }
}

// After (fixed with constructor injection)
@IocBean
public class MyService {
    private final String config;

    public MyService(@ConfigProperty("service.config") String config) {
        this.config = config;
    }
}

// Alternative (fixed with field injection)
@IocBean
public class MyService {
    @ConfigProperty("service.config")
    private String config;

    public MyService() {
        // Single constructor only
    }
}
```

**Debug strategy:**
1. Check for multiple constructors in the class
2. If using Lombok, ensure it's not generating conflicting constructors
3. Use configuration injection instead of constructor overloading
4. Consider using a provider method if complex initialization is needed

#### "Cannot retrieve bean with interface X. Too many implementations registered"

**What it means:**
You're trying to inject an interface that has multiple implementations, and the container doesn't know which one to use.

**Example error:**
```
Mc-Ioc bean exception: [Cannot retrieve bean with interface [com.example.StorageService]. Too many implementations registered: [2]]
```

**Common causes:**
- Multiple beans implement the same interface
- Trying to use `container.get()` instead of `container.getList()`
- Forgetting to use `@ConditionalOnMissingBean` for default implementations

**How to fix:**

```java
// Before (error)
public interface StorageService { }

@IocBean
public class DatabaseStorage implements StorageService { }

@IocBean
public class FileStorage implements StorageService { }

@IocBean
public class MyService {
    public MyService(StorageService storage) {  // Ambiguous - which one?
        // Error: Too many implementations
    }
}

// Solution 1: Use multi-provider pattern
@IocBean
@IocMultiProvider(StorageService.class)
public class DatabaseStorage implements StorageService { }

@IocBean
@IocMultiProvider(StorageService.class)
public class FileStorage implements StorageService { }

@IocBean
public class MyService {
    public MyService(@IocMulti(StorageService.class) List<StorageService> storages) {
        // Receives all implementations
    }
}

// Solution 2: Use conditional beans
@IocBean
public class DatabaseStorage implements StorageService { }

@IocBean
@ConditionalOnMissingBean
public class FileStorage implements StorageService { }
// FileStorage only created if DatabaseStorage doesn't exist

// Solution 3: Remove one implementation
// Just pick one storage implementation to register
```

**Debug strategy:**
1. Search your codebase for classes implementing the interface
2. Decide if you need all implementations (use `@IocMulti`)
3. Use `@ConditionalOnMissingBean` for default/fallback implementations
4. Consider using configuration to conditionally enable implementations

#### "No implementation registered for interface X"

**What it means:**
You're trying to inject an interface, but no class implements it.

**Example error:**
```
Mc-Ioc bean exception: [Cannot instantiate bean. No classes implementing this interface [com.example.PaymentService]]
```

**Common causes:**
- Forgot to create an implementation
- Implementation exists but isn't annotated with `@IocBean`
- Implementation is in wrong package (outside scan range)
- Typo in interface name

**How to fix:**

```java
// Before (error)
public interface PaymentService {
    void processPayment(double amount);
}

@IocBean
public class ShopService {
    public ShopService(PaymentService payment) {  // No implementation exists!
        // Error
    }
}

// After (fixed)
public interface PaymentService {
    void processPayment(double amount);
}

@IocBean  // Add implementation
public class StripePaymentService implements PaymentService {
    public void processPayment(double amount) {
        // Implementation
    }
}

@IocBean
public class ShopService {
    public ShopService(PaymentService payment) {  // Now works
        // Receives StripePaymentService
    }
}
```

**Debug strategy:**
1. Search for classes implementing the interface
2. Verify implementations have bean annotations
3. Check implementations are in scanned packages
4. Use IDE navigation to find all implementations

#### "Cannot instantiate bean. Circular dependency detected"

**What it means:**
Two or more beans depend on each other through constructors, creating an impossible instantiation order.

**Example error:**
```
Mc-Ioc bean exception: [Cannot instantiate bean [com.example.ServiceA]. Circular dependency detected]
```

**Common causes:**
- ServiceA depends on ServiceB in constructor
- ServiceB depends on ServiceA in constructor
- Longer circular chains (A → B → C → A)

**How to fix:**

```java
// Before (circular dependency)
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { }  // A needs B
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }  // B needs A - circular!
}

// Solution 1: Extract shared dependency
@IocBean
public class SharedLogic {
    // Common functionality
}

@IocBean
public class ServiceA {
    public ServiceA(SharedLogic logic) { }
}

@IocBean
public class ServiceB {
    public ServiceB(SharedLogic logic) { }
}

// Solution 2: Use field injection + @AfterIocLoad
@IocBean
public class ServiceA {
    private ServiceB serviceB;

    public ServiceA() { }

    public void setServiceB(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

@IocBean
public class ServiceB {
    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }
}

@TubingConfiguration
public class WiringConfig {
    @AfterIocLoad
    public static void wireCircular(ServiceA serviceA, ServiceB serviceB) {
        serviceA.setServiceB(serviceB);
    }
}

// Solution 3: Rethink design (best)
// Usually circular dependencies indicate a design problem
// Refactor to eliminate the cycle
```

**Debug strategy:**
1. Map out dependency graph on paper
2. Identify the cycle
3. Refactor to break the cycle (extract shared logic, inverse control flow)
4. As a last resort, use field injection with `@AfterIocLoad`

## Debugging Bean Instantiation Issues

Bean instantiation issues occur during container initialization. Here's a systematic approach to debug them.

### Enable Debug Output

First, enable debug logging in your plugin to see what's happening during initialization:

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("=== Plugin Initialization Started ===");
        getLogger().info("Container initialized with " +
            getIocContainer().get(ConfigurationLoader.class)
                .getConfigurationFiles().size() + " configuration files");

        // Log all registered beans
        getLogger().info("Registered beans:");
        // Note: There's no direct API to list all beans, but you can query specific ones

        getLogger().info("=== Plugin Initialization Complete ===");
    }
}
```

### Add Logging to Bean Constructors

Add logging to your bean constructors to track instantiation:

```java
@IocBean
public class PlayerService {

    public PlayerService(DatabaseService database, ConfigService config) {
        System.out.println("[DEBUG] PlayerService constructor called");
        System.out.println("[DEBUG] - database: " + (database != null ? "present" : "null"));
        System.out.println("[DEBUG] - config: " + (config != null ? "present" : "null"));
    }
}
```

**Important:** Use `System.out.println()` instead of plugin logger in constructors, as the logger may not be available during early initialization.

### Track Dependency Chain

When debugging complex dependency issues, trace the full chain:

```java
@IocBean
public class ServiceA {
    public ServiceA() {
        System.out.println("[INIT] ServiceA instantiated");
    }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) {
        System.out.println("[INIT] ServiceB instantiated (depends on ServiceA)");
    }
}

@IocBean
public class ServiceC {
    public ServiceC(ServiceB serviceB) {
        System.out.println("[INIT] ServiceC instantiated (depends on ServiceB)");
    }
}
```

Output will show:
```
[INIT] ServiceA instantiated
[INIT] ServiceB instantiated (depends on ServiceA)
[INIT] ServiceC instantiated (depends on ServiceB)
```

### Isolate the Problem Bean

If initialization fails, narrow down which bean is causing the issue:

1. **Binary search approach**: Comment out half your beans, test, repeat
2. **Comment out dependencies**: Remove constructor parameters one by one
3. **Test with minimal configuration**: Start with only essential beans

```java
// Temporarily simplify to isolate issue
@IocBean
public class ProblematicService {
    // Comment out dependencies one by one
    public ProblematicService(
        ServiceA serviceA,
        // ServiceB serviceB,  // Commented out to test
        // ServiceC serviceC   // Commented out to test
    ) {
        System.out.println("ProblematicService created with fewer deps");
    }
}
```

### Verify Package Scanning

Ensure your beans are in the correct package:

```java
@TubingConfiguration
public class DebugConfiguration {

    @AfterIocLoad
    public static void debugPackageScanning(
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        plugin.getLogger().info("Plugin main class: " + plugin.getClass().getName());
        plugin.getLogger().info("Scanned package: " +
            plugin.getClass().getPackage().getName());

        // Check if specific class is in scanned package
        String myClass = "com.example.myplugin.MyService";
        boolean inPackage = myClass.startsWith(
            plugin.getClass().getPackage().getName()
        );
        plugin.getLogger().info("MyService in scanned package: " + inPackage);
    }
}
```

## Understanding Configuration Errors

Configuration errors occur when `@ConfigProperty` values can't be loaded or are invalid.

### ConfigurationException Messages

#### "Configuration not found"

**What it means:**
A required configuration property doesn't exist in your YAML files.

**Example error:**
```
Invalid MyPlugin configuration: [Configuration not found for [database.url]]
```

**How to fix:**

```java
// Bean with required property
@IocBean
public class DatabaseService {
    @ConfigProperty(value = "database.url", required = true)
    private String url;  // Will throw exception if missing
}

// Solution 1: Add the property to config.yml
// database:
//   url: "jdbc:mysql://localhost:3306/mydb"

// Solution 2: Make it optional
@IocBean
public class DatabaseService {
    @ConfigProperty(value = "database.url", required = false)
    private String url;  // Optional, will be null if missing

    public void connect() {
        if (url == null) {
            // Use default or handle gracefully
            url = "jdbc:mysql://localhost:3306/default";
        }
    }
}

// Solution 3: Provide default in YAML
@IocBean
public class DatabaseService {
    @ConfigProperty("database.url")
    private String url = "jdbc:mysql://localhost:3306/default";
    // Field default used if property missing
}
```

**Debug strategy:**
1. Check the exact property path in the error message
2. Verify YAML indentation and syntax
3. Ensure the config file is loaded (check ConfigurationLoader)
4. Print all loaded config keys to debug

### Debug Configuration Loading

Print all configuration keys and values during initialization:

```java
@TubingConfiguration
public class ConfigDebugConfiguration {

    @AfterIocLoad
    public static void debugConfiguration(ConfigurationLoader configLoader) {
        System.out.println("=== Loaded Configuration ===");

        Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();
        for (Map.Entry<String, FileConfiguration> entry : configs.entrySet()) {
            System.out.println("Config file: " + entry.getKey());
            FileConfiguration config = entry.getValue();

            // Print all keys
            for (String key : config.getKeys(true)) {
                System.out.println("  " + key + " = " + config.get(key));
            }
        }
        System.out.println("=== End Configuration ===");
    }
}
```

### Verify Configuration Injection

Test that configuration values are injected correctly:

```java
@IocBean
public class ConfigTestService {

    @ConfigProperty("test.value")
    private String testValue;

    @ConfigProperty("test.number")
    private int testNumber;

    @ConfigProperty("test.enabled")
    private boolean enabled;

    public void printConfig() {
        System.out.println("testValue: " + testValue);
        System.out.println("testNumber: " + testNumber);
        System.out.println("enabled: " + enabled);
    }
}

@TubingConfiguration
public class ConfigTestConfiguration {
    @AfterIocLoad
    public static void testConfig(ConfigTestService service) {
        service.printConfig();
    }
}
```

### Handle Type Conversion Issues

Configuration values must be convertible to the target type:

```java
// config.yml
// server:
//   port: "not a number"  # This will fail

@IocBean
public class ServerService {
    @ConfigProperty("server.port")
    private int port;  // Expects integer, gets string - error!
}

// Solution: Validate configuration types
@TubingConfiguration
public class ValidationConfiguration {
    @AfterIocLoad
    public static void validate(ConfigurationLoader loader) {
        Object portValue = loader.getConfigurationFiles()
            .get("config")
            .get("server.port");

        if (!(portValue instanceof Integer)) {
            throw new ConfigurationException(
                "server.port must be an integer, got: " + portValue.getClass()
            );
        }
    }
}
```

## Tracing Dependency Resolution

Understanding how the container resolves dependencies helps debug complex issues.

### Manual Dependency Tracing

Print the dependency resolution process:

```java
@IocBean
public class TracedService {

    public TracedService(ServiceA serviceA, ServiceB serviceB) {
        System.out.println("[TRACE] TracedService constructor called");
        System.out.println("[TRACE] - ServiceA type: " + serviceA.getClass().getName());
        System.out.println("[TRACE] - ServiceB type: " + serviceB.getClass().getName());
    }
}
```

### Verify Interface Resolution

When injecting interfaces, verify which implementation is chosen:

```java
public interface StorageService {
    String getType();
}

@IocBean
public class DatabaseStorage implements StorageService {
    public String getType() { return "database"; }
}

@IocBean
@ConditionalOnMissingBean
public class FileStorage implements StorageService {
    public String getType() { return "file"; }
}

@IocBean
public class StorageUser {
    public StorageUser(StorageService storage) {
        System.out.println("[TRACE] Using storage type: " + storage.getType());
        // Will print: [TRACE] Using storage type: database
    }
}
```

### Debug Multi-Provider Injection

Verify all implementations are injected for multi-provider patterns:

```java
@IocBean
@IocMultiProvider(Plugin.class)
public class PluginA implements Plugin { }

@IocBean
@IocMultiProvider(Plugin.class)
public class PluginB implements Plugin { }

@IocBean
public class PluginManager {
    public PluginManager(@IocMulti(Plugin.class) List<Plugin> plugins) {
        System.out.println("[TRACE] Loaded " + plugins.size() + " plugins");
        for (Plugin plugin : plugins) {
            System.out.println("[TRACE] - " + plugin.getClass().getSimpleName());
        }
    }
}
```

### Check Priority Ordering

Verify beans are created in the correct order:

```java
@IocBean(priority = true)
public class PriorityService {
    public PriorityService() {
        System.out.println("[PRIORITY] PriorityService created FIRST");
    }
}

@IocBean
public class NormalService {
    public NormalService() {
        System.out.println("[NORMAL] NormalService created SECOND");
    }
}

@IocBean
public class DependentService {
    public DependentService(PriorityService priority, NormalService normal) {
        System.out.println("[DEPENDENT] DependentService created LAST");
    }
}
```

## Logging Strategies

Effective logging is crucial for debugging complex IoC issues.

### Container Initialization Logging

Log the container initialization process:

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    public final void onEnable() {
        long startTime = System.currentTimeMillis();
        getLogger().info("Starting IoC container initialization...");

        try {
            super.onEnable();
            long duration = System.currentTimeMillis() - startTime;
            getLogger().info("Container initialized in " + duration + "ms");
        } catch (Exception e) {
            getLogger().severe("Container initialization failed!");
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    protected void enable() {
        getLogger().info("Plugin enable() called - container is ready");
    }
}
```

**Note:** You can't override `onEnable()` in Tubing plugins as it's final. Use `enable()` instead. The above is for illustration of the internal process.

### Bean Lifecycle Logging

Log key lifecycle events:

```java
@IocBean
public class LoggedService {

    public LoggedService(DependencyService dependency) {
        System.out.println("[LIFECYCLE] Constructor: LoggedService");
    }

    @ConfigProperty("service.enabled")
    private boolean enabled;

    // Add a method called from @AfterIocLoad to verify field injection
}

@TubingConfiguration
public class LifecycleLogger {

    @AfterIocLoad
    public static void logAfterInit(LoggedService service) {
        System.out.println("[LIFECYCLE] AfterIocLoad: LoggedService initialized");
    }
}
```

### Conditional Bean Logging

Log when conditional beans are created or skipped:

```java
@IocBean(conditionalOnProperty = "features.advanced")
public class AdvancedFeature {
    public AdvancedFeature() {
        System.out.println("[CONDITIONAL] AdvancedFeature created (condition met)");
    }
}

@TubingConfiguration
public class ConditionalDebugger {

    @AfterIocLoad
    public static void checkConditional(
        ConfigurationLoader config,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        boolean enabled = config.getConfigurationFiles()
            .get("config")
            .getBoolean("features.advanced", false);

        plugin.getLogger().info("features.advanced = " + enabled);

        if (enabled) {
            plugin.getLogger().info("AdvancedFeature should be loaded");
        } else {
            plugin.getLogger().info("AdvancedFeature should be skipped");
        }
    }
}
```

### Structured Logging Pattern

Use a consistent logging format for easier debugging:

```java
@IocBean
public class LoggingService {

    private static final String PREFIX = "[MyPlugin]";

    public void info(String component, String message) {
        System.out.println(PREFIX + " [INFO] [" + component + "] " + message);
    }

    public void debug(String component, String message) {
        System.out.println(PREFIX + " [DEBUG] [" + component + "] " + message);
    }

    public void error(String component, String message, Throwable t) {
        System.err.println(PREFIX + " [ERROR] [" + component + "] " + message);
        if (t != null) {
            t.printStackTrace();
        }
    }
}

// Usage
@IocBean
public class PlayerService {
    private final LoggingService logger;

    public PlayerService(LoggingService logger) {
        this.logger = logger;
        logger.info("PlayerService", "Initialized");
    }

    public void savePlayer(Player player) {
        logger.debug("PlayerService", "Saving player: " + player.getName());
        try {
            // Save logic
        } catch (Exception e) {
            logger.error("PlayerService", "Failed to save player", e);
        }
    }
}
```

## Using Debuggers with IoC

Modern IDEs provide powerful debugging tools that work perfectly with Tubing.

### IntelliJ IDEA Debugging Setup

**1. Set up remote debugging:**

Start your server with remote debugging enabled:
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar server.jar
```

**2. Configure IntelliJ:**
- Run → Edit Configurations
- Add New Configuration → Remote JVM Debug
- Host: localhost, Port: 5005
- Click OK

**3. Attach debugger:**
- Run → Debug 'Remote Debug'

### Strategic Breakpoints

Place breakpoints at key IoC lifecycle points:

**Bean constructor:**
```java
@IocBean
public class MyService {
    public MyService(DependencyService dep) {
        // Set breakpoint here to inspect dependency injection
        System.out.println("Break");
    }
}
```

**Configuration injection:**
```java
@IocBean
public class ConfigService {
    @ConfigProperty("important.value")
    private String value;

    public void useValue() {
        // Set breakpoint here to inspect injected value
        System.out.println(value);
    }
}
```

**Container access:**
```java
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        // Set breakpoint here to inspect container state
        MyService service = getIocContainer().get(MyService.class);
    }
}
```

### Conditional Breakpoints

Use conditional breakpoints to catch specific scenarios:

```java
@IocBean
public class PlayerService {
    public void handlePlayer(Player player) {
        // Conditional breakpoint: player.getName().equals("Notch")
        // Only breaks for specific player
        processPlayer(player);
    }
}
```

### Evaluate Expressions

Use the debugger's expression evaluator:

When stopped at a breakpoint:
- Check container state: `getIocContainer().get(MyService.class)`
- Inspect configuration: `getIocContainer().get(ConfigurationLoader.class).getConfigurationFiles()`
- Verify bean existence: `getIocContainer().get(SomeService.class) != null`

### Exception Breakpoints

Set exception breakpoints for IoC errors:

In IntelliJ:
- Run → View Breakpoints
- Add Java Exception Breakpoint
- Enter: `be.garagepoort.mcioc.IocException`
- Check "Any exception"

Now the debugger will pause whenever an IocException is thrown, even if it's caught.

## Best Practices for Debugging

### 1. Keep Beans Simple

Simple beans are easier to debug:

```java
// Hard to debug - complex initialization
@IocBean
public class ComplexService {
    public ComplexService(A a, B b, C c, D d, E e) {
        // Complex initialization logic
        // Many possible failure points
    }
}

// Easy to debug - simple initialization
@IocBean
public class SimpleService {
    private final CoreDependency core;

    public SimpleService(CoreDependency core) {
        this.core = core;
    }

    @AfterIocLoad
    public void initialize() {
        // Complex initialization in explicit method
        // Easier to debug and test
    }
}
```

### 2. Fail Fast and Loud

Don't swallow errors during initialization:

```java
// Bad - silent failure
@IocBean
public class DatabaseService {
    public DatabaseService() {
        try {
            connect();
        } catch (Exception e) {
            // Silent - hard to debug!
        }
    }
}

// Good - explicit failure
@IocBean
public class DatabaseService {
    public DatabaseService() {
        // Let constructor be simple
    }
}

@TubingConfiguration
public class DatabaseConfig {
    @AfterIocLoad
    public static void initDatabase(DatabaseService db,
                                    @InjectTubingPlugin TubingPlugin plugin) {
        try {
            db.connect();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to database!");
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
```

### 3. Validate Early

Validate configuration and dependencies during initialization:

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateConfiguration(
        ConfigurationLoader config,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        FileConfiguration main = config.getConfigurationFiles().get("config");

        // Validate required properties
        if (!main.contains("database.url")) {
            throw new ConfigurationException("Missing required property: database.url");
        }

        // Validate values
        int port = main.getInt("server.port");
        if (port < 1 || port > 65535) {
            throw new ConfigurationException("Invalid port: " + port);
        }

        plugin.getLogger().info("Configuration validation passed");
    }

    @AfterIocLoad
    public static void validateBeans(
        DatabaseService database,
        StorageService storage,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        // Verify beans are properly initialized
        if (database == null) {
            throw new IllegalStateException("DatabaseService not initialized");
        }

        plugin.getLogger().info("Bean validation passed");
    }
}
```

### 4. Use Descriptive Names

Clear names make debugging easier:

```java
// Unclear
@IocBean
public class ServiceImpl {
    public ServiceImpl(Repository repo) { }
}

// Clear
@IocBean
public class PlayerDataService {
    public PlayerDataService(PlayerRepository playerRepository) { }
}
```

### 5. Document Complex Dependencies

Document why dependencies exist:

```java
@IocBean
public class RewardService {
    private final EconomyService economy;
    private final PermissionService permissions;
    private final DatabaseService database;

    /**
     * @param economy Required for monetary rewards
     * @param permissions Required for permission-based rewards
     * @param database Required for reward history tracking
     */
    public RewardService(
        EconomyService economy,
        PermissionService permissions,
        DatabaseService database
    ) {
        this.economy = economy;
        this.permissions = permissions;
        this.database = database;
    }
}
```

### 6. Create Debug Commands

Add commands to inspect container state at runtime:

```java
@IocBukkitCommandHandler("debugcontainer")
public class DebugContainerCommand {

    private final IocContainer container;

    public DebugContainerCommand(IocContainer container) {
        this.container = container;
    }

    public boolean handle(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("myplugin.debug")) {
            sender.sendMessage("No permission");
            return true;
        }

        sender.sendMessage("=== Container Debug Info ===");

        // Test specific beans
        try {
            PlayerService ps = container.get(PlayerService.class);
            sender.sendMessage("PlayerService: " + (ps != null ? "OK" : "NULL"));
        } catch (Exception e) {
            sender.sendMessage("PlayerService: ERROR - " + e.getMessage());
        }

        try {
            List<EventHandler> handlers = container.getList(EventHandler.class);
            sender.sendMessage("Event handlers: " + handlers.size());
        } catch (Exception e) {
            sender.sendMessage("Event handlers: ERROR - " + e.getMessage());
        }

        return true;
    }
}
```

### 7. Test in Isolation

Create test configurations to isolate problems:

```java
// src/test/java
public class BeanInstantiationTest {

    @Test
    public void testPlayerServiceCreation() {
        // Test bean creation without full container
        PlayerRepository mockRepo = mock(PlayerRepository.class);
        ConfigService mockConfig = mock(ConfigService.class);

        PlayerService service = new PlayerService(mockRepo, mockConfig);
        assertNotNull(service);
    }

    @Test
    public void testDependencyChain() {
        // Test full dependency chain
        DatabaseService db = new DatabaseService("jdbc:test");
        PlayerRepository repo = new PlayerRepository(db);
        ConfigService config = new ConfigService();
        PlayerService service = new PlayerService(repo, config);

        assertNotNull(service);
        // Verify initialization
    }
}
```

### 8. Use Version Control for Configuration

Track configuration changes:

```yaml
# config.yml
# Version: 1.2.0
# Last modified: 2024-01-15
# Changes: Added database.pool-size property

database:
  url: "jdbc:mysql://localhost:3306/mydb"
  pool-size: 10  # Added in 1.2.0
```

### 9. Create Diagnostic Reports

Generate diagnostic information on errors:

```java
@TubingConfiguration
public class DiagnosticConfiguration {

    @AfterIocLoad
    public static void generateDiagnostics(@InjectTubingPlugin TubingPlugin plugin) {
        try {
            // Initialization logic
        } catch (Exception e) {
            generateDiagnosticReport(plugin, e);
            throw e;
        }
    }

    private static void generateDiagnosticReport(TubingPlugin plugin, Exception e) {
        File reportFile = new File(plugin.getDataFolder(), "diagnostic-report.txt");
        try (PrintWriter writer = new PrintWriter(reportFile)) {
            writer.println("=== Diagnostic Report ===");
            writer.println("Plugin: " + plugin.getName());
            writer.println("Version: " + plugin.getVersion());
            writer.println("Error: " + e.getMessage());
            writer.println();
            writer.println("Stack trace:");
            e.printStackTrace(writer);
            writer.println();
            writer.println("Configuration:");
            // Dump configuration
            writer.println();
            writer.println("System info:");
            writer.println("Java version: " + System.getProperty("java.version"));
            writer.println("OS: " + System.getProperty("os.name"));
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to write diagnostic report");
        }
    }
}
```

## Common Debugging Scenarios

### Scenario 1: Plugin Won't Load

**Symptoms:**
- Plugin doesn't appear in `/plugins` list
- Server logs show errors during plugin load
- Container initialization fails

**Debug steps:**

1. Check server logs for `IocException` or `ConfigurationException`
2. Verify plugin.yml has correct main class
3. Ensure main class extends correct Tubing base class
4. Check for errors in static initializers
5. Verify all dependencies are on classpath

```java
// Debug: Add logging to main class
public class MyPlugin extends TubingBukkitPlugin {

    static {
        System.out.println("[DEBUG] MyPlugin class loading...");
    }

    public MyPlugin() {
        System.out.println("[DEBUG] MyPlugin constructor called");
    }

    @Override
    protected void enable() {
        System.out.println("[DEBUG] MyPlugin enable() called");
        getLogger().info("Plugin enabled successfully");
    }
}
```

### Scenario 2: Dependency Not Found

**Symptoms:**
- `IocException`: "No implementation registered"
- NullPointerException when using dependency
- Bean reports null constructor parameter

**Debug steps:**

1. Verify the dependency class has a bean annotation
2. Check the dependency is in your plugin's package
3. Verify no typos in class name or package
4. Test container directly: `container.get(DependencyClass.class)`

```java
@TubingConfiguration
public class DependencyDebugger {

    @AfterIocLoad
    public static void debugDependencies(
        IocContainer container,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        // Test each dependency
        try {
            DatabaseService db = container.get(DatabaseService.class);
            plugin.getLogger().info("DatabaseService: found");
        } catch (Exception e) {
            plugin.getLogger().severe("DatabaseService: MISSING - " + e.getMessage());
        }

        try {
            PlayerService ps = container.get(PlayerService.class);
            plugin.getLogger().info("PlayerService: found");
        } catch (Exception e) {
            plugin.getLogger().severe("PlayerService: MISSING - " + e.getMessage());
        }
    }
}
```

### Scenario 3: Configuration Not Loading

**Symptoms:**
- `@ConfigProperty` fields are null
- Default values are used instead of config values
- ConfigurationException thrown

**Debug steps:**

1. Verify config file exists in plugin data folder
2. Check YAML syntax (indentation, colons, quotes)
3. Verify property path matches YAML structure
4. Check file is loaded by ConfigurationLoader

```java
@TubingConfiguration
public class ConfigDebugger {

    @AfterIocLoad
    public static void debugConfig(
        ConfigurationLoader loader,
        @InjectTubingPlugin TubingPlugin plugin
    ) {
        plugin.getLogger().info("=== Configuration Debug ===");

        Map<String, FileConfiguration> configs = loader.getConfigurationFiles();
        plugin.getLogger().info("Loaded " + configs.size() + " config files");

        for (String name : configs.keySet()) {
            plugin.getLogger().info("Config: " + name);
            FileConfiguration config = configs.get(name);

            // Test specific property
            String testPath = "database.url";
            if (config.contains(testPath)) {
                plugin.getLogger().info("  " + testPath + " = " + config.get(testPath));
            } else {
                plugin.getLogger().warning("  " + testPath + " NOT FOUND");
            }
        }
    }
}
```

### Scenario 4: Wrong Bean Implementation Used

**Symptoms:**
- Wrong implementation of interface is injected
- Conditional bean not working as expected
- Unexpected behavior from dependency

**Debug steps:**

1. Print the actual class type of injected dependency
2. Check for multiple implementations
3. Verify conditional annotations
4. Check priority ordering

```java
@IocBean
public class ServiceUser {

    public ServiceUser(StorageService storage) {
        // Debug: Print actual implementation
        System.out.println("[DEBUG] StorageService implementation: " +
            storage.getClass().getName());
        System.out.println("[DEBUG] Is DatabaseStorage? " +
            (storage instanceof DatabaseStorage));
        System.out.println("[DEBUG] Is FileStorage? " +
            (storage instanceof FileStorage));
    }
}
```

### Scenario 5: Lifecycle Order Issues

**Symptoms:**
- NullPointerException in @AfterIocLoad
- Bean used before it's fully initialized
- Race conditions during startup

**Debug steps:**

1. Add logging to constructors and @AfterIocLoad methods
2. Verify priority settings
3. Check dependency order
4. Ensure initialization in @AfterIocLoad, not constructors

```java
@IocBean(priority = true)
public class CoreService {
    public CoreService() {
        System.out.println("[LIFECYCLE] 1. CoreService constructor");
    }
}

@IocBean
public class DependentService {
    public DependentService(CoreService core) {
        System.out.println("[LIFECYCLE] 2. DependentService constructor");
    }
}

@TubingConfiguration
public class InitializationConfig {
    @AfterIocLoad
    public static void initialize(DependentService service) {
        System.out.println("[LIFECYCLE] 3. AfterIocLoad called");
    }
}
```

## Summary

Effective debugging of Tubing applications requires:

1. **Understanding error messages**: Know what each IocException means
2. **Strategic logging**: Add logging at key lifecycle points
3. **Systematic isolation**: Narrow down problems through binary search
4. **Configuration validation**: Verify config files load correctly
5. **Dependency tracing**: Understand how dependencies are resolved
6. **IDE debugging**: Use breakpoints and expression evaluation
7. **Best practices**: Keep beans simple, fail fast, validate early

Most IoC issues fall into a few categories and can be quickly resolved with the right debugging approach.

## Next Steps

- **[Common Errors](Common-Errors.md)** - Reference guide to common error messages
- **[FAQ](FAQ.md)** - Frequently asked questions about Tubing
- **[Testing](../best-practices/Testing.md)** - Test your beans in isolation
- **[Bean Lifecycle](../core/Bean-Lifecycle.md)** - Understand the full lifecycle

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Dependency Injection](../core/Dependency-Injection.md) - DI patterns
- [Configuration Injection](../core/Configuration-Injection.md) - Config debugging
