# Common Errors

This guide covers the most common errors you'll encounter when using Tubing and provides practical solutions with code examples. Understanding these errors will help you quickly diagnose and fix issues in your plugin.

## Overview

Common error categories in Tubing:

- **ClassNotFoundException / NoClassDefFoundError** - Relocation and dependency issues
- **IocException Errors** - Bean registration and dependency injection problems
- **Configuration Errors** - Property injection and configuration file issues
- **Annotation Issues** - Missing or incorrect annotations
- **Command/Listener Registration Problems** - Platform-specific registration failures

Each section provides the error message, explanation, common causes, and detailed solutions.

## ClassNotFoundException / NoClassDefFoundError

### Error Messages

```
java.lang.ClassNotFoundException: be.garagepoort.mcioc.IocContainer
java.lang.NoClassDefFoundError: be/garagepoort/mcioc/IocBean
```

### What This Means

These errors occur when Java cannot find Tubing classes at runtime. This typically indicates that Tubing wasn't properly shaded (included) into your plugin JAR or the relocation configuration is incorrect.

### Common Causes

1. **Missing Maven Shade Plugin** - Tubing classes weren't included in your JAR
2. **Incorrect Relocation Pattern** - Relocation pattern is malformed
3. **Missing Exclusions** - Transitive dependencies causing conflicts
4. **Wrong Dependency Scope** - Tubing marked as `provided` instead of `implementation`

### Solutions

#### Solution 1: Verify Maven Shade Plugin Configuration

Ensure your `pom.xml` includes the Maven Shade Plugin with proper relocation:

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
                                <shadedPattern>com.yourplugin.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Critical details:**
- Pattern must end with a dot: `be.garagepoort.mcioc.` (not `be.garagepoort.mcioc`)
- ShadedPattern should be unique to your plugin
- The `shade` goal must be bound to the `package` phase

#### Solution 2: Check Dependency Configuration

Verify your Tubing dependency has proper exclusions and is NOT marked as `provided`:

```xml
<!-- Correct -->
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Incorrect - will cause ClassNotFoundException -->
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit</artifactId>
    <version>7.5.6</version>
    <scope>provided</scope> <!-- WRONG! -->
</dependency>
```

#### Solution 3: Verify Shaded JAR Contents

After building, inspect your JAR to confirm Tubing was relocated:

```bash
# Build your plugin
mvn clean package

# Extract and inspect the JAR
unzip -l target/YourPlugin-1.0.0.jar | grep tubing

# You should see your relocated package, e.g.:
# com/yourplugin/tubing/IocContainer.class
# com/yourplugin/tubing/IocBean.class

# You should NOT see:
# be/garagepoort/mcioc/IocContainer.class
```

If you see `be/garagepoort/mcioc` paths, the relocation didn't work. Double-check your shade configuration.

#### Solution 4: Clean and Rebuild

Sometimes Maven's cache causes issues:

```bash
mvn clean install -U
```

The `-U` flag forces Maven to update snapshots and releases from remote repositories.

### Prevention

Always verify your build process:

1. Check `pom.xml` for correct shade configuration
2. Build and inspect the JAR contents
3. Test with a clean server (no other plugins using Tubing)
4. Use a unique relocation package for your plugin

## IocException: Cannot Instantiate Bean

### Error: No Bean Annotation Present

```
IocException: Cannot instantiate bean. No Bean annotation present. [com.example.MyService]
```

#### What This Means

The IoC container tried to create a bean for a class that isn't annotated with `@IocBean` or another bean annotation.

#### Common Causes

1. Missing `@IocBean` annotation
2. Class isn't in the scanned package
3. Trying to inject an interface with no implementation

#### Solutions

**Solution 1: Add @IocBean Annotation**

```java
// Before (error)
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;
    }
}

// After (fixed)
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;
    }
}
```

**Solution 2: Verify Package Structure**

Ensure the class is in your plugin's package tree:

```
com.example.myplugin/         <- Plugin main class here
├── MyPlugin.java             <- Extends TubingBukkitPlugin
├── services/
│   └── PlayerService.java    <- @IocBean classes must be here
└── commands/
    └── MyCommand.java
```

Classes outside `com.example.myplugin` won't be scanned.

**Solution 3: Create Implementation for Interface**

If you're injecting an interface, create an implementation:

```java
public interface StorageService {
    void save(String key, String value);
}

// Create implementation with @IocBean
@IocBean
public class FileStorageService implements StorageService {
    @Override
    public void save(String key, String value) {
        // Implementation
    }
}

// Now injectable
@IocBean
public class DataManager {
    private final StorageService storage;

    public DataManager(StorageService storage) {
        this.storage = storage;
    }
}
```

### Error: Only One Constructor Should Be Defined

```
IocException: Cannot instantiate bean with type com.example.MyService. Only one constructor should be defined
```

#### What This Means

Tubing beans can only have a single constructor. Multiple constructors are not supported because the container needs to know exactly which constructor to use for dependency injection.

#### Solution

Remove extra constructors:

```java
// Before (error - multiple constructors)
@IocBean
public class PlayerService {
    private final Database database;
    private final Logger logger;

    // Constructor 1
    public PlayerService(Database database) {
        this.database = database;
        this.logger = Logger.getLogger("default");
    }

    // Constructor 2 - causes error!
    public PlayerService(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }
}

// After (fixed - single constructor)
@IocBean
public class PlayerService {
    private final Database database;
    private final Logger logger;

    public PlayerService(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }
}
```

**Alternative: Use Bean Provider for Complex Initialization**

If you need conditional logic, use a provider method:

```java
@TubingConfiguration
public class ServiceConfiguration {

    @IocBeanProvider
    public PlayerService providePlayerService(Database database,
                                             @ConfigProperty("logging.enabled") boolean loggingEnabled) {
        if (loggingEnabled) {
            Logger logger = Logger.getLogger("player-service");
            return new PlayerService(database, logger);
        } else {
            return new PlayerService(database, null);
        }
    }
}
```

### Error: Cannot Instantiate Bean (General)

```
IocException: Cannot instantiate bean with type com.example.MyService.
Caused by: java.lang.IllegalArgumentException: Cannot resolve dependency
```

#### What This Means

The container couldn't create the bean, usually because a dependency couldn't be resolved or a constructor parameter is invalid.

#### Common Causes

1. Missing dependency bean
2. Circular dependency
3. Constructor throws exception
4. Primitive types without @ConfigProperty

#### Solutions

**Solution 1: Check Dependency Chain**

Ensure all dependencies are annotated as beans:

```java
@IocBean
public class PlayerService {
    private final Database database;
    private final Cache cache;

    // Make sure Database and Cache are @IocBean classes
    public PlayerService(Database database, Cache cache) {
        this.database = database;
        this.cache = cache;
    }
}

// Verify these exist and have @IocBean:
@IocBean
public class Database { }

@IocBean
public class Cache { }
```

**Solution 2: Break Circular Dependencies**

Circular dependencies cause initialization failures:

```java
// Before (circular dependency)
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { } // A needs B
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { } // B needs A - circular!
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
    // Extract common functionality here
}
```

**Solution 3: Annotate Configuration Parameters**

Primitive types must use `@ConfigProperty`:

```java
// Before (error - can't inject primitive without annotation)
@IocBean
public class CooldownService {
    private final int cooldownSeconds;

    public CooldownService(int cooldownSeconds) { // Error!
        this.cooldownSeconds = cooldownSeconds;
    }
}

// After (fixed - using @ConfigProperty)
@IocBean
public class CooldownService {
    private final int cooldownSeconds;

    public CooldownService(@ConfigProperty("cooldown.seconds") int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
```

**Solution 4: Handle Constructor Exceptions**

Don't throw exceptions in constructors:

```java
// Before (error - exception in constructor)
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null"); // Error!
        }
    }
}

// After (fixed - validate after construction)
@IocBean
public class DatabaseService {
    private final String url;

    public DatabaseService(@ConfigProperty("db.url") String url) {
        this.url = url;
    }

    @PostConstruct
    public void initialize() {
        if (url == null) {
            throw new IllegalStateException("Database URL not configured");
        }
    }
}
```

## IocException: Cannot Retrieve Bean

### Error: Too Many Implementations Registered

```
IocException: Cannot retrieve bean with interface com.example.RewardHandler.
Too many implementations registered. Use `getList` to retrieve a list of all beans
```

#### What This Means

You're trying to inject an interface that has multiple implementations, but you didn't use `@IocMulti` to indicate you want all of them.

#### Solution

Use `@IocMulti` to inject all implementations:

```java
public interface RewardHandler {
    void giveReward(Player player);
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyReward implements RewardHandler {
    public void giveReward(Player player) { }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemReward implements RewardHandler {
    public void giveReward(Player player) { }
}

// Before (error)
@IocBean
public class RewardService {
    public RewardService(RewardHandler handler) { // Error - which one?
        // Cannot determine which implementation to inject
    }
}

// After (fixed - inject list)
@IocBean
public class RewardService {
    private final List<RewardHandler> handlers;

    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers) {
        this.handlers = handlers;
    }

    public void giveAllRewards(Player player) {
        for (RewardHandler handler : handlers) {
            handler.giveReward(player);
        }
    }
}
```

**Alternative: Use Conditional Beans**

If you only want one implementation active at a time:

```java
@IocBean
@IocMultiProvider(RewardHandler.class)
@ConditionalOnProperty(property = "rewards.money.enabled", havingValue = "true")
public class MoneyReward implements RewardHandler { }

@IocBean
@IocMultiProvider(RewardHandler.class)
@ConditionalOnProperty(property = "rewards.items.enabled", havingValue = "true")
public class ItemReward implements RewardHandler { }

// Or use @ConditionalOnMissingBean for defaults
@IocBean
@ConditionalOnMissingBean
public class DefaultReward implements RewardHandler { }
```

### Error: No Implementation Registered

```
IocException: Cannot retrieve bean with interface com.example.StorageService.
No implementation registered
```

#### What This Means

You're injecting an interface but no class implements it, or the implementation isn't annotated with `@IocBean`.

#### Solutions

**Solution 1: Create Implementation**

```java
public interface StorageService {
    void save(String data);
}

// Add this implementation
@IocBean
public class FileStorageService implements StorageService {
    @Override
    public void save(String data) {
        // Implementation
    }
}
```

**Solution 2: Check Package Location**

Ensure implementation is in the scanned package:

```java
// This must be in your plugin's package tree
package com.example.myplugin.storage;

@IocBean
public class FileStorageService implements StorageService { }
```

**Solution 3: Check Conditional Annotations**

The implementation might be conditionally disabled:

```java
@IocBean
@ConditionalOnProperty(property = "storage.enabled", havingValue = "true")
public class FileStorageService implements StorageService { }

// Check your config.yml:
storage:
  enabled: false  # This disables the bean!
```

## Configuration Errors

### Error: Cannot Inject Property

```
IocException: Cannot inject property. Make sure the field is public
IocException: Cannot inject property. Make sure the config property setter is public
```

#### What This Means

The container cannot inject a `@ConfigProperty` value because it can't access the field or setter method.

#### Common Causes

1. Field is private (should be package-private or public)
2. Setter method is private
3. Field is final (cannot be modified after construction)

#### Solutions

**Solution 1: Make Field Package-Private or Public**

```java
// Before (error - private field)
@IocBean
public class MyService {
    @ConfigProperty("max.players")
    private int maxPlayers; // Error!
}

// After (fixed - package-private)
@IocBean
public class MyService {
    @ConfigProperty("max.players")
    int maxPlayers; // No modifier = package-private, works!
}

// Or public
@IocBean
public class MyService {
    @ConfigProperty("max.players")
    public int maxPlayers; // Also works
}
```

**Solution 2: Use Constructor Injection**

For truly private fields, inject via constructor:

```java
@IocBean
public class MyService {
    private final int maxPlayers; // Can be private and final

    public MyService(@ConfigProperty("max.players") int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
```

**Solution 3: Make Setter Public**

If using setter injection:

```java
@IocBean
public class MyService {
    private int maxPlayers;

    @ConfigProperty("max.players")
    public void setMaxPlayers(int maxPlayers) { // Must be public
        this.maxPlayers = maxPlayers;
    }
}
```

### Error: ConditionOnProperty Referencing Unknown Property

```
IocException: ConditionOnProperty referencing an unknown property [feature.enabled]
```

#### What This Means

A bean is conditionally registered based on a configuration property, but that property doesn't exist in your config file.

#### Solution

Add the property to your configuration file:

```java
// Bean definition
@IocBean
@ConditionalOnProperty(property = "feature.enabled", havingValue = "true")
public class FeatureService {
    // Only created if feature.enabled = true
}
```

```yaml
# config.yml - add the property
feature:
  enabled: true
```

**Alternative: Provide Default Value**

```java
// Use conditional that allows missing property
@IocBean
@ConditionalOnProperty(property = "feature.enabled", havingValue = "true", matchIfMissing = false)
public class FeatureService { }
```

### Error: Cannot Create ConfigTransformer

```
IocException: Cannot create configtransformer
IocException: Invalid IConfigTransformer. Invalid constructor
```

#### What This Means

A custom configuration transformer couldn't be instantiated, usually because its constructor is invalid.

#### Solution

Ensure your transformer has a no-args constructor or a constructor accepting `ConfigurationProvider`:

```java
// Correct - no-args constructor
public class CustomTransformer implements IConfigTransformer<CustomType> {

    public CustomTransformer() { }

    @Override
    public CustomType transform(IConfigurationSection section, String path) {
        return new CustomType(section.getString(path));
    }
}

// Also correct - ConfigurationProvider constructor
public class CustomTransformer implements IConfigTransformer<CustomType> {
    private final ConfigurationProvider configProvider;

    public CustomTransformer(ConfigurationProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Override
    public CustomType transform(IConfigurationSection section, String path) {
        return new CustomType(section.getString(path));
    }
}

// Wrong - invalid constructor
public class CustomTransformer implements IConfigTransformer<CustomType> {
    public CustomTransformer(String param, int value) { } // Error!

    @Override
    public CustomType transform(IConfigurationSection section, String path) {
        return new CustomType(section.getString(path));
    }
}
```

## Annotation Issues

### Missing @IocBean on Services

#### Problem

Services aren't being discovered or injected.

#### Solution

Add `@IocBean` to all service classes:

```java
// All these need @IocBean
@IocBean
public class PlayerService { }

@IocBean
public class DatabaseService { }

@IocBean
public class CacheService { }
```

### Mixing Platform Annotations Incorrectly

#### Problem

Using wrong annotation for platform or combining incompatible annotations.

#### Solution

Use platform-specific annotations correctly:

```java
// Bukkit listener - correct
@IocBukkitListener
public class MyListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) { }
}

// Bukkit command - correct
@IocBukkitCommandHandler("mycommand")
public class MyCommand {
    public boolean handle(CommandSender sender, String[] args) {
        return true;
    }
}

// Wrong - don't mix @IocBean with platform annotations
@IocBean // Don't add this
@IocBukkitListener // This already implies @IocBean
public class MyListener implements Listener { }
```

Platform annotations (`@IocBukkitListener`, `@IocBukkitCommandHandler`, etc.) automatically include `@IocBean` behavior.

### Missing @IocMultiProvider for Multiple Implementations

#### Problem

Multiple implementations of an interface exist but aren't marked as multi-providers.

#### Solution

Use `@IocMultiProvider` for all implementations:

```java
public interface Handler {
    void handle();
}

// Mark all implementations
@IocBean
@IocMultiProvider(Handler.class)
public class HandlerA implements Handler { }

@IocBean
@IocMultiProvider(Handler.class)
public class HandlerB implements Handler { }

@IocBean
@IocMultiProvider(Handler.class)
public class HandlerC implements Handler { }

// Inject as list
@IocBean
public class HandlerCoordinator {
    public HandlerCoordinator(@IocMulti(Handler.class) List<Handler> handlers) {
        // Receives all three handlers
    }
}
```

## Command/Listener Registration Problems

### Listeners Not Firing

#### Problem

Event handlers don't get called.

#### Common Causes

1. Missing `@IocBukkitListener` annotation
2. Missing `@EventHandler` annotation
3. Not implementing `Listener` interface
4. Event handler method is private

#### Solutions

```java
// Correct listener setup
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerService.handleQuit(event.getPlayer());
    }
}
```

**Checklist:**
- [ ] Class has `@IocBukkitListener` annotation
- [ ] Class implements `Listener` interface
- [ ] Methods have `@EventHandler` annotation
- [ ] Methods are public
- [ ] Methods have correct event parameter type
- [ ] Class is in scanned package

### Commands Not Registered

#### Problem

Commands don't work or show as "unknown command".

#### Solutions

**Solution 1: Verify Annotation**

```java
@IocBukkitCommandHandler("mycommand") // Command name here
public class MyCommand {

    public boolean handle(CommandSender sender, Command command,
                         String label, String[] args) {
        sender.sendMessage("Command works!");
        return true;
    }
}
```

**Solution 2: Check plugin.yml**

While Tubing auto-registers commands, you should still define them in `plugin.yml` for descriptions:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.MyPlugin
api-version: 1.20

commands:
  mycommand:
    description: My custom command
    usage: /mycommand [args]
    permission: myplugin.mycommand
```

**Solution 3: Use Correct Handler Signature**

```java
// Correct signatures
public boolean handle(CommandSender sender, String[] args)

public boolean handle(CommandSender sender, Command command,
                     String label, String[] args)

// Wrong - won't be recognized
public void handle(CommandSender sender, String[] args) // Wrong return type

public boolean execute(CommandSender sender, String[] args) // Wrong method name
```

### Permission Issues

#### Problem

Players can't execute commands despite having permission.

#### Solution

Verify permission configuration:

```java
@IocBukkitCommandHandler(value = "admin",
                         permission = "myplugin.admin",
                         playerOnly = true)
public class AdminCommand {

    public boolean handle(Player player, String[] args) {
        // Only players with myplugin.admin permission can execute
        return true;
    }
}
```

```yaml
# plugin.yml
permissions:
  myplugin.admin:
    description: Admin command access
    default: op
```

## Debugging Tips

### Enable Debug Logging

Add logging to track bean creation:

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        IocContainer container = getIocContainer();

        // Log all registered beans
        getLogger().info("Registered beans:");
        // You can manually check specific beans
        try {
            PlayerService service = container.get(PlayerService.class);
            getLogger().info("PlayerService loaded successfully");
        } catch (IocException e) {
            getLogger().severe("Failed to load PlayerService: " + e.getMessage());
        }
    }
}
```

### Check Container Initialization

Verify the container is properly initialized:

```java
@TubingConfiguration
public class StartupConfiguration {

    @AfterIocLoad
    public static void onLoad(IocContainer container, @InjectTubingPlugin TubingPlugin plugin) {
        plugin.getLogger().info("IoC container fully loaded!");
        plugin.getLogger().info("Total beans: " + container.getReflections().getAllClasses().size());
    }
}
```

### Validate Bean Dependencies

Create a validation bean that checks dependencies on startup:

```java
@IocBean
public class DependencyValidator {

    public DependencyValidator(PlayerService playerService,
                              DatabaseService database,
                              ConfigManager config,
                              @InjectTubingPlugin TubingPlugin plugin) {
        plugin.getLogger().info("All required dependencies loaded successfully");
    }
}
```

### Use Try-Catch for Diagnostics

Wrap potential failure points:

```java
@IocBean
public class DiagnosticService {

    public DiagnosticService(@InjectTubingPlugin TubingPlugin plugin) {
        try {
            IocContainer container = ((TubingBukkitPlugin) plugin).getIocContainer();

            // Test retrieving beans
            testBean(container, PlayerService.class, plugin);
            testBean(container, DatabaseService.class, plugin);

        } catch (Exception e) {
            plugin.getLogger().severe("Dependency error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private <T> void testBean(IocContainer container, Class<T> clazz, TubingPlugin plugin) {
        try {
            T bean = container.get(clazz);
            plugin.getLogger().info(clazz.getSimpleName() + " - OK");
        } catch (IocException e) {
            plugin.getLogger().warning(clazz.getSimpleName() + " - FAILED: " + e.getMessage());
        }
    }
}
```

## Quick Reference Table

| Error Message | Most Common Cause | Quick Fix |
|---------------|-------------------|-----------|
| ClassNotFoundException | Missing shade configuration | Add Maven Shade Plugin with relocation |
| No Bean annotation present | Missing @IocBean | Add @IocBean to class |
| Only one constructor | Multiple constructors | Remove extra constructors |
| Too many implementations | Missing @IocMulti | Use @IocMulti(Interface.class) |
| No implementation registered | Missing @IocBean on implementation | Add @IocBean to implementation class |
| Cannot inject property | Private field | Make field package-private or use constructor injection |
| Unknown property | Missing config entry | Add property to config.yml |
| Listener not firing | Missing annotation | Add @IocBukkitListener and @EventHandler |
| Command not registered | Missing annotation | Add @IocBukkitCommandHandler |

## Best Practices to Avoid Errors

### 1. Always Use Constructor Injection

```java
// Preferred - constructor injection
@IocBean
public class MyService {
    private final Database database;

    public MyService(Database database) {
        this.database = database;
    }
}
```

### 2. Keep Beans Simple

```java
// Good - simple bean
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;
    }
}

// Bad - too complex, should be split
@IocBean
public class MegaService {
    public MegaService(Dep1 d1, Dep2 d2, Dep3 d3, Dep4 d4,
                      Dep5 d5, Dep6 d6, Dep7 d7) { } // Too many dependencies!
}
```

### 3. Use Interfaces for Abstraction

```java
public interface StorageService {
    void save(String data);
}

@IocBean
public class FileStorage implements StorageService {
    public void save(String data) { }
}

@IocBean
public class DataManager {
    private final StorageService storage; // Depend on interface

    public DataManager(StorageService storage) {
        this.storage = storage;
    }
}
```

### 4. Validate Configuration Early

```java
@IocBean
public class ConfigValidator {

    public ConfigValidator(@ConfigProperty("database.url") String dbUrl,
                          @ConfigProperty("server.port") int port) {
        if (dbUrl == null || dbUrl.isEmpty()) {
            throw new IllegalStateException("Database URL not configured!");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Invalid port: " + port);
        }
    }
}
```

### 5. Document Bean Dependencies

```java
/**
 * Manages player data persistence.
 *
 * Dependencies:
 * - Database: For storing player data
 * - Cache: For in-memory caching
 * - MessageService: For player notifications
 */
@IocBean
public class PlayerDataService {
    private final Database database;
    private final Cache cache;
    private final MessageService messages;

    public PlayerDataService(Database database, Cache cache, MessageService messages) {
        this.database = database;
        this.cache = cache;
        this.messages = messages;
    }
}
```

## Getting Help

If you're still stuck after trying these solutions:

1. **Check the version** - Ensure you're using the correct Tubing version
2. **Enable debug logging** - Add diagnostic beans to trace initialization
3. **Isolate the problem** - Comment out beans until you find the problematic one
4. **Review the documentation** - Check the specific feature guides
5. **Look at examples** - Review working code examples in the documentation

## Next Steps

- [Debugging Guide](Debugging.md) - Advanced debugging techniques
- [FAQ](FAQ.md) - Frequently asked questions
- [IoC Container](../core/IoC-Container.md) - Deep dive into container behavior
- [Testing](../best-practices/Testing.md) - Writing tests to catch errors early

---

**See also:**
- [Installation Guide](../getting-started/Installation.md) - Proper setup prevents many errors
- [Project Structure](../getting-started/Project-Structure.md) - Organize code to avoid issues
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Understanding initialization order
