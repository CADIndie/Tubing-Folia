# Custom Annotations

Tubing's IoC container is extensible through custom bean annotations. You can create your own annotations to mark classes as beans, enabling domain-specific abstractions, platform integration, and specialized bean behavior.

## Overview

Custom bean annotations allow you to:

- Create platform-specific bean types (e.g., `@IocBukkitListener`, `@IocVelocityCommandHandler`)
- Add domain-specific semantics to your beans (e.g., `@Repository`, `@Service`, `@Controller`)
- Extend Tubing for custom frameworks or libraries
- Provide automatic registration and wiring for specialized components
- Maintain clean separation between framework code and application code

All built-in platform annotations (`@IocBukkitListener`, `@IocBungeeListener`, etc.) are implemented using the same custom annotation system available to you.

## The TubingBeanAnnotationRegistrator Interface

The `TubingBeanAnnotationRegistrator` interface is the entry point for registering custom bean annotations with the IoC container.

### Interface Definition

```java
package be.garagepoort.mcioc.load;

import java.util.List;

public interface TubingBeanAnnotationRegistrator {
    List<Class> getAnnotations();
}
```

### How It Works

During container initialization, Tubing uses ClassGraph to discover all classes implementing `TubingBeanAnnotationRegistrator`:

```java
for (Class<? extends TubingBeanAnnotationRegistrator> aClass :
     scanResult.getClassesImplementing(TubingBeanAnnotationRegistrator.class)
              .loadClasses(TubingBeanAnnotationRegistrator.class)) {
    Constructor<?> declaredConstructor = aClass.getDeclaredConstructors()[0];
    TubingBeanAnnotationRegistrator registrator =
        (TubingBeanAnnotationRegistrator) declaredConstructor.newInstance();
    beanAnnotations.addAll(registrator.getAnnotations());
}
```

The container:

1. Scans your plugin's package for `TubingBeanAnnotationRegistrator` implementations
2. Instantiates each registrator (using the first constructor)
3. Calls `getAnnotations()` to retrieve the list of annotation classes
4. Adds these annotations to the list of valid bean annotations
5. Later scans for classes marked with these annotations during bean discovery

### Discovery Phase

Custom annotations are discovered in Phase 1 of the bean lifecycle, before any bean instantiation occurs. This ensures all your custom annotations are registered before the container looks for beans to create.

## Creating Custom Bean Annotations

Creating a custom bean annotation involves two steps:

1. Define the annotation with required attributes
2. Create a registrator to register it with the container

### Step 1: Define Your Annotation

Custom bean annotations must follow specific requirements:

**Required:**
- `@Retention(RetentionPolicy.RUNTIME)` - Available at runtime for reflection
- `@Target(ElementType.TYPE)` - Applied to classes
- `boolean priority()` attribute with default value
- `Class multiproviderClass()` attribute with default value
- `String conditionalOnProperty()` attribute with default value (for conditional bean support)

**Example: Basic Custom Annotation**

```java
package com.example.myplugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Repository {

    String conditionalOnProperty() default "";

    boolean priority() default false;

    Class multiproviderClass() default Object.class;
}
```

**Example: Annotation with Custom Attributes**

```java
package com.example.myplugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventHandler {

    // Required Tubing attributes
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    // Custom attributes for your domain
    String eventType() default "all";
    int processingOrder() default 0;
    boolean async() default false;
}
```

### Step 2: Create a Registrator

Implement `TubingBeanAnnotationRegistrator` to register your annotations:

```java
package com.example.myplugin.load;

import be.garagepoort.mcioc.load.TubingBeanAnnotationRegistrator;
import com.example.myplugin.annotations.Repository;
import com.example.myplugin.annotations.EventHandler;

import java.util.Arrays;
import java.util.List;

public class CustomBeanRegistrator implements TubingBeanAnnotationRegistrator {

    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(Repository.class, EventHandler.class);
    }
}
```

### Step 3: Use Your Annotations

Once registered, use your annotations like any built-in bean annotation:

```java
@Repository
public class PlayerDataRepository {

    private final DatabaseService database;

    public PlayerDataRepository(DatabaseService database) {
        this.database = database;
    }

    public PlayerData findById(UUID playerId) {
        // Repository implementation
    }
}
```

```java
@EventHandler(eventType = "player_join", async = true)
public class WelcomeMessageHandler {

    private final MessageService messageService;

    public WelcomeMessageHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    public void handle(Player player) {
        messageService.sendWelcome(player);
    }
}
```

## Registration Process

Understanding the registration lifecycle helps you design effective custom annotations.

### Automatic Discovery

Registrators are discovered automatically - no manual registration needed:

```
Plugin Startup
      |
      v
ClassGraph scans plugin package
      |
      v
Find TubingBeanAnnotationRegistrator implementations
      |
      v
Instantiate each registrator
      |
      v
Call getAnnotations() on each
      |
      v
Collect all annotation classes
      |
      v
Scan for beans with these annotations
      |
      v
Instantiate and wire beans
```

### Package Scanning

Your registrator must be in your plugin's package tree to be discovered:

```
com.example.myplugin/           <- Plugin main class here
├── load/
│   └── CustomBeanRegistrator   <- Scanned and discovered
├── annotations/
│   ├── Repository              <- Your custom annotation
│   └── EventHandler            <- Your custom annotation
└── repositories/
    └── PlayerDataRepository    <- Bean using your annotation
```

If your registrator is outside your plugin's package, it won't be discovered.

### Constructor Requirements

Registrators must have at least one constructor. The container uses the first declared constructor and calls it with no arguments:

```java
public class CustomBeanRegistrator implements TubingBeanAnnotationRegistrator {

    // No-arg constructor works (default if not specified)
    public CustomBeanRegistrator() {
    }

    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(Repository.class);
    }
}
```

If your registrator needs dependencies, you'll need to handle them manually - registrators are instantiated before the IoC container is fully initialized.

## Real-World Examples

### Example 1: Platform-Specific Annotations (Bukkit)

Tubing's own Bukkit module uses custom annotations:

```java
package be.garagepoort.mcioc.tubingbukkit.load;

import be.garagepoort.mcioc.load.TubingBeanAnnotationRegistrator;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitMessageListener;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitSubCommand;

import java.util.Arrays;
import java.util.List;

public class TubingBukkitBeanRegistrator implements TubingBeanAnnotationRegistrator {

    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(
            IocBukkitListener.class,
            IocBukkitMessageListener.class,
            IocBukkitCommandHandler.class,
            IocBukkitSubCommand.class
        );
    }
}
```

The `@IocBukkitListener` annotation:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IocBukkitListener {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;
}
```

Used as:

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Handle join
    }
}
```

### Example 2: Domain Layer Annotations

Create semantic annotations for different layers of your application:

```java
// Service layer
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String value() default "";  // Service name
}

// Repository layer
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Repository {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String dataSource() default "default";
}

// Controller layer
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String path() default "";
}

// Registrator
public class LayeredArchitectureRegistrator implements TubingBeanAnnotationRegistrator {
    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(Service.class, Repository.class, Controller.class);
    }
}
```

Usage:

```java
@Repository(dataSource = "mysql")
public class MySQLPlayerRepository implements PlayerRepository {
    // Implementation
}

@Service("playerService")
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}

@Controller(path = "/api/players")
public class PlayerApiController {
    private final PlayerService playerService;

    public PlayerApiController(PlayerService playerService) {
        this.playerService = playerService;
    }
}
```

### Example 3: Scheduled Tasks

Create an annotation for scheduled tasks:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScheduledTask {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    long intervalTicks() default 20L;
    long delayTicks() default 0L;
    boolean async() default false;
}

public class ScheduledTaskRegistrator implements TubingBeanAnnotationRegistrator {
    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(ScheduledTask.class);
    }
}
```

Use with `@AfterIocLoad` to register tasks:

```java
@ScheduledTask(intervalTicks = 100L, async = true)
public class AutoSaveTask implements Runnable {

    private final PlayerDataService dataService;

    public AutoSaveTask(PlayerDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public void run() {
        dataService.saveAll();
    }
}

@TubingConfiguration
public class TaskConfiguration {

    @AfterIocLoad
    public static void registerTasks(@InjectTubingPlugin TubingBukkitPlugin plugin,
                                     IocContainer container) {
        ScanResult scanResult = container.getReflections();

        for (Class<?> taskClass : scanResult.getClassesWithAnnotation(ScheduledTask.class).loadClasses()) {
            ScheduledTask annotation = taskClass.getAnnotation(ScheduledTask.class);
            Runnable task = (Runnable) container.get(taskClass);

            if (annotation.async()) {
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin, task, annotation.delayTicks(), annotation.intervalTicks()
                );
            } else {
                plugin.getServer().getScheduler().runTaskTimer(
                    plugin, task, annotation.delayTicks(), annotation.intervalTicks()
                );
            }
        }
    }
}
```

### Example 4: Permission-Based Beans

Create beans that are only loaded if a permission system is present:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PermissionHandler {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String requiredPlugin();  // e.g., "Vault", "LuckPerms"
}

public class PermissionHandlerRegistrator implements TubingBeanAnnotationRegistrator {
    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(PermissionHandler.class);
    }
}
```

Combined with conditional loading:

```java
@PermissionHandler(requiredPlugin = "Vault")
@ConditionalOnMissingBean
public class VaultPermissionProvider implements PermissionProvider {

    private final Permission vaultPerms;

    public VaultPermissionProvider() {
        RegisteredServiceProvider<Permission> rsp =
            Bukkit.getServicesManager().getRegistration(Permission.class);
        this.vaultPerms = rsp.getProvider();
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        return vaultPerms.has(player, permission);
    }
}
```

## Use Cases for Custom Annotations

### 1. Framework Integration

Integrate external frameworks or libraries:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GuiController {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String menuId();
}
```

### 2. Aspect-Oriented Programming

Mark beans for aspect weaving or proxy generation:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Transactional {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    IsolationLevel isolation() default IsolationLevel.DEFAULT;
    Propagation propagation() default Propagation.REQUIRED;
}
```

### 3. Validation and Constraints

Mark beans requiring validation:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Validated {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    Class<?>[] groups() default {};
}
```

### 4. Caching Strategies

Annotate beans with caching behavior:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cacheable {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String cacheName();
    long ttlSeconds() default 3600L;
}
```

### 5. Plugin API Exposure

Mark beans that should be exposed to external plugins:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiExposed {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String version() default "1.0";
}
```

## Best Practices

### 1. Include Required Attributes

Always include the three required attributes for compatibility with Tubing's bean lifecycle:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyAnnotation {
    // REQUIRED
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    // Optional custom attributes
    String customAttribute() default "";
}
```

These attributes enable:
- `conditionalOnProperty`: Conditional bean loading based on configuration
- `priority`: Priority instantiation during container startup
- `multiproviderClass`: Multi-provider pattern support

### 2. Use Descriptive Names

Choose annotation names that clearly convey their purpose:

**Good:**
- `@Repository`
- `@EventHandler`
- `@ScheduledTask`
- `@GuiController`

**Avoid:**
- `@MyBean`
- `@Special`
- `@Custom`
- `@Handler` (too generic)

### 3. Document Custom Attributes

If your annotation has custom attributes, document their purpose and usage:

```java
/**
 * Marks a class as a scheduled task that should be automatically registered
 * with the Bukkit scheduler.
 *
 * @see ScheduledTaskRegistrator
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScheduledTask {

    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    /**
     * The interval between task executions, in server ticks (20 ticks = 1 second).
     * Default is 20 ticks (1 second).
     */
    long intervalTicks() default 20L;

    /**
     * The delay before the first execution, in server ticks.
     * Default is 0 (execute immediately after registration).
     */
    long delayTicks() default 0L;

    /**
     * Whether this task should run asynchronously.
     * Default is false (run on main thread).
     */
    boolean async() default false;
}
```

### 4. Keep Registrators Simple

Registrators should only return annotation classes. Avoid complex logic:

```java
// Good
public class SimpleRegistrator implements TubingBeanAnnotationRegistrator {
    @Override
    public List<Class> getAnnotations() {
        return Arrays.asList(Repository.class, Service.class);
    }
}

// Avoid
public class ComplexRegistrator implements TubingBeanAnnotationRegistrator {
    @Override
    public List<Class> getAnnotations() {
        // Don't do complex initialization here
        setupDatabase();
        loadConfiguration();
        validateState();
        return Arrays.asList(Repository.class);
    }
}
```

### 5. Handle Custom Attributes in @AfterIocLoad

Process custom attributes after all beans are instantiated:

```java
@TubingConfiguration
public class CustomAnnotationProcessor {

    @AfterIocLoad
    public static void processCustomAnnotations(IocContainer container,
                                                @InjectTubingPlugin TubingPlugin plugin) {
        ScanResult scanResult = container.getReflections();

        // Process each annotated class
        for (Class<?> clazz : scanResult.getClassesWithAnnotation(MyAnnotation.class).loadClasses()) {
            MyAnnotation annotation = clazz.getAnnotation(MyAnnotation.class);
            Object bean = container.get(clazz);

            // Handle custom attributes
            processBean(bean, annotation);
        }
    }

    private static void processBean(Object bean, MyAnnotation annotation) {
        // Process custom attributes
    }
}
```

### 6. Consider Multi-Provider Support

If your annotation should support multiple implementations, leverage the `multiproviderClass` attribute:

```java
@Repository(multiproviderClass = PlayerRepository.class)
public class MySQLPlayerRepository implements PlayerRepository {
    // Implementation
}

@Repository(multiproviderClass = PlayerRepository.class)
public class MongoPlayerRepository implements PlayerRepository {
    // Implementation
}

@Service
public class PlayerService {
    private final List<PlayerRepository> repositories;

    public PlayerService(@IocMulti(PlayerRepository.class) List<PlayerRepository> repositories) {
        this.repositories = repositories;
    }
}
```

### 7. Namespace Your Annotations

Use package naming to avoid conflicts with other plugins:

```
com.example.myplugin.annotations/
├── Repository.java
├── Service.java
└── Controller.java

com.example.myplugin.load/
└── DomainAnnotationRegistrator.java
```

### 8. Test Your Annotations

Write tests to verify your annotations work correctly:

```java
public class CustomAnnotationTest {

    @Test
    public void testRepositoryAnnotationRegistration() {
        // Verify annotation is discovered
        IocContainer container = createTestContainer();
        PlayerRepository repo = container.get(PlayerRepository.class);
        assertNotNull(repo);
    }

    @Test
    public void testCustomAttributeProcessing() {
        // Verify custom attributes are processed
        // ...
    }
}
```

## Common Pitfalls

### 1. Missing Required Attributes

**Problem:** Annotation doesn't have required attributes.

**Solution:** Always include `conditionalOnProperty`, `priority`, and `multiproviderClass`.

```java
// Wrong - missing required attributes
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyAnnotation {
    String value();
}

// Correct - includes required attributes
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyAnnotation {
    String conditionalOnProperty() default "";
    boolean priority() default false;
    Class multiproviderClass() default Object.class;

    String value();
}
```

### 2. Registrator Not in Plugin Package

**Problem:** Registrator is in a package not scanned by ClassGraph.

**Solution:** Ensure your registrator is in your plugin's package tree.

```
// Wrong - outside plugin package
com.example.library.load.MyRegistrator  <- NOT scanned

// Correct - inside plugin package
com.example.myplugin.load.MyRegistrator  <- Scanned
```

### 3. Constructor with Required Parameters

**Problem:** Registrator has a constructor requiring parameters.

**Solution:** Provide a no-arg constructor or make the first constructor no-arg.

```java
// Wrong - requires parameters
public class MyRegistrator implements TubingBeanAnnotationRegistrator {
    public MyRegistrator(String requiredParam) {
        // Cannot be instantiated by container
    }
}

// Correct - no-arg constructor
public class MyRegistrator implements TubingBeanAnnotationRegistrator {
    public MyRegistrator() {
        // Can be instantiated by container
    }
}
```

### 4. Processing Custom Attributes Too Early

**Problem:** Trying to process custom attributes during bean instantiation.

**Solution:** Use `@AfterIocLoad` to process attributes after all beans are ready.

```java
// Wrong - processing in constructor
@MyAnnotation(customValue = "test")
public class MyBean {
    public MyBean() {
        // Container not ready, can't access other beans
        processAnnotation();
    }
}

// Correct - processing in @AfterIocLoad
@TubingConfiguration
public class AnnotationProcessor {
    @AfterIocLoad
    public static void process(IocContainer container) {
        // All beans ready, safe to process
    }
}
```

### 5. Forgetting @Retention

**Problem:** Annotation doesn't have `@Retention(RetentionPolicy.RUNTIME)`.

**Solution:** Always include runtime retention for reflection.

```java
// Wrong - defaults to RetentionPolicy.CLASS
@Target(ElementType.TYPE)
public @interface MyAnnotation {
}

// Correct - explicitly set to RUNTIME
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyAnnotation {
}
```

## Advanced: Combining with Existing Features

### Integration with Conditional Beans

Your custom annotations work seamlessly with conditional loading:

```java
@Repository(dataSource = "redis", conditionalOnProperty = "storage.redis.enabled")
public class RedisPlayerRepository implements PlayerRepository {
    // Only instantiated if storage.redis.enabled = true
}
```

### Integration with Multi-Providers

Enable multiple implementations to be collected:

```java
@EventHandler(multiproviderClass = GameEventHandler.class)
public class PlayerJoinHandler implements GameEventHandler {
    // Collected in multi-provider list
}

@Service
public class EventDispatcher {
    private final List<GameEventHandler> handlers;

    public EventDispatcher(@IocMulti(GameEventHandler.class) List<GameEventHandler> handlers) {
        this.handlers = handlers;
    }
}
```

### Integration with Priority Beans

Control instantiation order:

```java
@Repository(priority = true)
public class DatabaseConnectionPool {
    // Instantiated before other repositories
}
```

## Summary

Custom annotations in Tubing provide a powerful mechanism for:

- Extending the IoC container with domain-specific abstractions
- Creating platform-specific integrations
- Building framework-level functionality
- Maintaining clean, semantic code organization

Key takeaways:

1. Implement `TubingBeanAnnotationRegistrator` to register your annotations
2. Include required attributes: `conditionalOnProperty`, `priority`, `multiproviderClass`
3. Use `@AfterIocLoad` to process custom attributes after bean initialization
4. Keep registrators simple and focused
5. Document your annotations thoroughly
6. Test your annotations to ensure they work correctly

Custom annotations work seamlessly with Tubing's existing features like conditional loading, multi-providers, and priority instantiation, giving you complete control over your plugin's architecture.

## Next Steps

- Learn about [Bean Lifecycle](../core/Bean-Lifecycle.md) to understand when annotations are processed
- Explore [Post-Initialization](../core/Post-Initialization.md) for processing custom annotations
- Read [Multi-Implementation](../core/Multi-Implementation.md) for multi-provider patterns
- See [Conditional Beans](../core/Conditional-Beans.md) for conditional loading strategies

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Bean Registration](../core/Bean-Registration.md) - Standard bean registration
- [Dependency Injection](../core/Dependency-Injection.md) - Dependency injection patterns
