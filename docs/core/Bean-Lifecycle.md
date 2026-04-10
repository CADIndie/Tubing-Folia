# Bean Lifecycle

Understanding the bean lifecycle is crucial for working effectively with Tubing. This guide provides a comprehensive look at how beans are discovered, sorted, instantiated, and initialized during the IoC container's startup and reload processes.

## Overview

The bean lifecycle in Tubing follows a well-defined sequence of phases:

1. **Discovery Phase**: ClassGraph scans packages to find annotated classes
2. **Collection Phase**: Beans and providers are collected and catalogued
3. **Sorting Phase**: Beans are sorted by priority
4. **Validation Phase**: Conditional beans are evaluated
5. **Instantiation Phase**: Beans are created with dependency resolution
6. **Configuration Injection Phase**: Configuration properties are injected
7. **Post-Initialization Phase**: `@AfterIocLoad` hooks are executed

## Lifecycle Diagram

```
Plugin Startup
      |
      v
┌─────────────────────────────────────────────┐
│  1. DISCOVERY PHASE                         │
│  - ClassGraph scans plugin package          │
│  - Find @IocBean, @IocBeanProvider, etc.    │
│  - Find @TubingConfiguration classes        │
│  - Collect bean annotation types            │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  2. COLLECTION PHASE                        │
│  - Collect all classes with bean annot.    │
│  - Collect @IocBeanProvider methods         │
│  - Collect @IocMultiProvider methods        │
│  - Build complete bean registry             │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  3. SORTING PHASE                           │
│  - Sort beans by priority attribute         │
│  - Priority beans instantiated first        │
│  - ConfigurationLoader always first         │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  4. VALIDATION PHASE                        │
│  - Evaluate @ConditionalOnProperty          │
│  - ConfigurationLoader available            │
│  - Filter out beans with unmet conditions   │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  5. INSTANTIATION PHASE                     │
│  For each valid bean:                       │
│    a. Resolve constructor parameters        │
│    b. Resolve dependencies recursively      │
│    c. Call constructor                      │
│    d. Store bean in container               │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  6. CONFIGURATION INJECTION PHASE           │
│  For each instantiated bean:                │
│    - Inject @ConfigProperty fields          │
│    - Inject @InjectTubingPlugin fields      │
│    - Bean is now fully configured           │
└─────────────────┬───────────────────────────┘
                  |
                  v
┌─────────────────────────────────────────────┐
│  7. POST-INITIALIZATION PHASE               │
│  - Find all @AfterIocLoad methods           │
│  - Resolve method parameters                │
│  - Execute methods in configuration classes │
└─────────────────┬───────────────────────────┘
                  |
                  v
            Container Ready
            Plugin enable() called
```

## Phase 1: Discovery Phase

The discovery phase uses [ClassGraph](https://github.com/classgraph/classgraph) to scan your plugin's package and find all annotated classes.

### What is Scanned

```java
String pkg = tubingPlugin.getClass().getPackage().getName();
scanResult = new ClassGraph()
    .enableAllInfo()
    .acceptPackages(pkg)
    .scan();
```

Tubing scans:
- Your main plugin class's package and all sub-packages
- All classes with bean annotations (`@IocBean`, `@IocBukkitListener`, etc.)
- All classes with `@TubingConfiguration`
- All classes implementing `TubingBeanAnnotationRegistrator`

### Bean Annotation Types

Tubing discovers which annotations mark a class as a bean by finding all `TubingBeanAnnotationRegistrator` implementations:

```java
for (Class<? extends TubingBeanAnnotationRegistrator> aClass :
     scanResult.getClassesImplementing(TubingBeanAnnotationRegistrator.class).loadClasses(...)) {
    Constructor<?> declaredConstructor = aClass.getDeclaredConstructors()[0];
    TubingBeanAnnotationRegistrator registrator =
        (TubingBeanAnnotationRegistrator) declaredConstructor.newInstance();
    beanAnnotations.addAll(registrator.getAnnotations());
}
```

This makes the system extensible - platform-specific modules can register their own bean annotations.

**Default bean annotations:**
- `@IocBean` - Core bean annotation
- `@IocBukkitListener` - Bukkit event listener
- `@IocBukkitCommandHandler` - Bukkit command
- `@IocBukkitSubCommand` - Bukkit subcommand
- `@IocBukkitMessageListener` - Bukkit plugin message listener
- `@IocBungeeListener` - BungeeCord listener
- `@IocBungeeCommandHandler` - BungeeCord command
- `@IocVelocityListener` - Velocity listener
- `@IocVelocityCommandHandler` - Velocity command

### Discovery Performance

ClassGraph is highly optimized and caches scan results. The discovery phase typically completes in milliseconds, even for large plugins.

## Phase 2: Collection Phase

After discovery, Tubing collects all beans and providers into organized collections.

### Bean Classes

All classes annotated with any bean annotation are collected:

```java
Set<Class<?>> allBeans = new HashSet<>();
for (Class beanAnnotation : beanAnnotations) {
    allBeans.addAll(scanResult.getClassesWithAnnotation(beanAnnotation).loadClasses());
}
```

### Bean Providers

Provider methods in `@TubingConfiguration` classes are collected:

```java
List<Class<?>> configurationClasses =
    scanResult.getClassesWithAnnotation(TubingConfiguration.class).loadClasses();

List<Method> providers = configurationClasses.stream()
    .flatMap(c -> ReflectionUtils.getMethodsAnnotatedWith(c, IocBeanProvider.class).stream())
    .collect(Collectors.toList());

List<Method> multiProviders = configurationClasses.stream()
    .flatMap(c -> ReflectionUtils.getMethodsAnnotatedWith(c, IocMultiProvider.class).stream())
    .collect(Collectors.toList());
```

### Valid Beans Set

The container builds a set of all valid bean types - both direct beans and provided beans:

```java
Set<Class<?>> providedBeans = providers.stream()
    .map(Method::getReturnType)
    .collect(Collectors.toCollection(LinkedHashSet::new));

Set<Class<?>> validBeans = Stream.concat(
    classesWithBeanAnnotations.stream(),
    providedBeans.stream()
).collect(Collectors.toCollection(LinkedHashSet::new));
```

This set is used during dependency resolution to ensure only valid dependencies are injected.

## Phase 3: Sorting Phase

Beans are sorted by their `priority` attribute to control instantiation order.

### Priority Sorting

```java
List<Class<?>> classesWithBeanAnnotations = allBeans.stream()
    .sorted((o1, o2) -> {
        Annotation annotation1 = Arrays.stream(o1.getAnnotations())
            .filter(a -> beanAnnotations.contains(a.annotationType()))
            .findFirst().get();
        Annotation annotation2 = Arrays.stream(o2.getAnnotations())
            .filter(a -> beanAnnotations.contains(a.annotationType()))
            .findFirst().get();

        boolean priority1 = (boolean) annotation1.annotationType()
            .getMethod("priority").invoke(annotation1);
        boolean priority2 = (boolean) annotation2.annotationType()
            .getMethod("priority").invoke(annotation2);

        return Boolean.compare(priority2, priority1);
    })
    .collect(Collectors.toList());
```

### Priority Levels

Tubing uses a simple boolean priority system:

- **`priority = true`**: High priority, instantiated first
- **`priority = false`**: Normal priority (default)

**Example:**

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // This bean is instantiated before all normal priority beans
}

@IocBean  // priority defaults to false
public class PlayerService {
    // Normal priority bean
}
```

### ConfigurationLoader Priority

The `ConfigurationLoader` is always marked with `priority = true` to ensure configuration files are loaded before other beans:

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // ...
}
```

This allows other beans to use `@ConfigProperty` in their constructors, as the configuration is guaranteed to be available.

### When to Use Priority

Use `priority = true` when:
- Your bean provides infrastructure needed by other beans during instantiation
- Your bean must be initialized before configuration injection in other beans
- Your bean performs setup that affects how other beans are created

**Do not use priority** for:
- Normal application logic
- Services that only run after initialization
- Beans with no special initialization requirements

## Phase 4: Validation Phase

After sorting, beans with conditional annotations are evaluated.

### Conditional Property Evaluation

The `ConfigurationLoader` is instantiated first (due to priority), then conditional beans are filtered:

```java
configurationLoader = (ConfigurationLoader) instantiateBean(
    scanResult, ConfigurationLoader.class, validBeans, providers, multiProviders, false
);

classesWithBeanAnnotations = classesWithBeanAnnotations.stream()
    .filter(a -> iocConditionalPropertyFilter.isValidBean(
        beanAnnotations, a, getConfigurationFiles()
    ))
    .collect(Collectors.toList());
```

### Conditional Annotations

**`@ConditionalOnProperty`** (via `@IocBean.conditionalOnProperty`):

```java
@IocBean(conditionalOnProperty = "features.advanced-mode")
public class AdvancedFeatureService {
    // Only instantiated if features.advanced-mode = true
}
```

**`@ConditionalOnMissingBean`**:

This annotation is evaluated during dependency resolution (Phase 5), not in the validation phase. See [Interface Resolution](#interface-resolution) below.

### Why Validation Happens After Sorting

Configuration must be loaded before conditions can be evaluated. By instantiating `ConfigurationLoader` first (via priority), the validation phase has access to configuration files:

```
Priority Sorting → ConfigurationLoader instantiation → Validation → Other beans
```

## Phase 5: Instantiation Phase

The instantiation phase is the heart of the bean lifecycle. Each valid bean is instantiated with its dependencies recursively resolved.

### Instantiation Order

Beans are instantiated in the order they appear in the sorted, validated list. However, dependency resolution can change the effective order:

```java
for (Class<?> aClass : validBeans) {
    instantiateBean(scanResult, aClass, validBeans, providers, multiProviders, false);
}
```

**Example:**

If the sorted list is `[ServiceA, ServiceB, ServiceC]` but `ServiceB` depends on `ServiceC`, the actual instantiation order becomes:

1. `ServiceA` (no dependencies)
2. `ServiceC` (required by ServiceB)
3. `ServiceB` (depends on ServiceC)

### Bean Creation Process

For each bean, the container follows this process:

```java
private Object createBean(ScanResult scanResult, Class<?> aClass,
                         Set<Class<?>> validBeans, List<Method> providers,
                         List<Method> multiProviders) {
    // 1. Check if bean already exists
    if (beans.containsKey(aClass)) {
        return beans.get(aClass);
    }

    // 2. Check if bean is provided by a provider method
    Optional<Method> beanProvider = providers.stream()
        .filter(p -> p.getReturnType() == aClass)
        .findFirst();
    if (beanProvider.isPresent()) {
        return getProvidedBean(...);
    }

    // 3. Validate bean has annotation
    if (Arrays.stream(aClass.getAnnotations())
            .noneMatch(a -> beanAnnotations.contains(a.annotationType()))) {
        throw new IocException("No Bean annotation present");
    }

    // 4. Ensure single constructor
    Constructor<?>[] declaredConstructors = aClass.getDeclaredConstructors();
    if (declaredConstructors.length > 1) {
        throw new IocException("Only one constructor should be defined");
    }

    // 5. Build constructor parameters
    Constructor<?> declaredConstructor = aClass.getDeclaredConstructors()[0];
    List<Object> constructorParams = buildParams(
        aClass, scanResult, validBeans, providers, multiProviders,
        declaredConstructor.getParameterTypes(),
        declaredConstructor.getParameterAnnotations()
    );

    // 6. Instantiate bean
    Object bean = declaredConstructor.newInstance(constructorParams.toArray());

    // 7. Register bean
    beans.putIfAbsent(aClass, bean);
    return bean;
}
```

Note: Configuration property injection happens **after** this method returns (see Phase 6).

### Dependency Resolution

Constructor parameters are resolved recursively through `buildParams`:

```java
private List<Object> buildParams(Class<?> aClass, ScanResult scanResult,
                                 Set<Class<?>> validBeans, List<Method> providedBeans,
                                 List<Method> multiProviders, Class<?>[] parameterTypes,
                                 Annotation[][] parameterAnnotations) {
    List<Object> constructorParams = new ArrayList<>();

    for (int i = 0; i < parameterTypes.length; i++) {
        Class<?> classParam = parameterTypes[i];
        Annotation[] annotations = parameterAnnotations[i];

        // Check for @IocMulti
        Optional<Annotation> multiAnnotation = Arrays.stream(annotations)
            .filter(a -> a.annotationType().equals(IocMulti.class))
            .findFirst();

        // Check for @ConfigProperty
        Optional<ConfigProperty> configAnnotation = Arrays.stream(annotations)
            .filter(a -> a.annotationType().equals(ConfigProperty.class))
            .map(a -> (ConfigProperty) a)
            .findFirst();

        // Check for @InjectTubingPlugin
        Optional<InjectTubingPlugin> tubingPluginAnnotation = Arrays.stream(annotations)
            .filter(a -> a.annotationType().equals(InjectTubingPlugin.class))
            .map(a -> (InjectTubingPlugin) a)
            .findFirst();

        if (tubingPluginAnnotation.isPresent()) {
            constructorParams.add(tubingPlugin);
        } else if (configAnnotation.isPresent()) {
            Object property = PropertyInjector.getConstructorConfigurationProperty(
                aClass, classParam, annotations, getConfigurationFiles()
            );
            constructorParams.add(property);
        } else if (multiAnnotation.isPresent()) {
            IocMulti iocMulti = (IocMulti) multiAnnotation.get();
            constructorParams.add(instantiateBean(
                scanResult, iocMulti.value(), validBeans, providedBeans,
                multiProviders, true
            ));
        } else {
            // Regular dependency injection
            Object o = instantiateBean(
                scanResult, classParam, validBeans, providedBeans,
                multiProviders, false
            );
            constructorParams.add(o);
        }
    }
    return constructorParams;
}
```

### Constructor Parameter Types

The container supports multiple parameter types:

**1. Regular Bean Dependency:**

```java
@IocBean
public class PlayerService {
    public PlayerService(DatabaseService database) {
        // database is resolved and injected
    }
}
```

**2. Configuration Property:**

```java
@IocBean
public class MessageService {
    public MessageService(@ConfigProperty("messages.prefix") String prefix) {
        // prefix is loaded from config and injected
    }
}
```

**3. Plugin Instance:**

```java
@IocBean
public class SchedulerService {
    public SchedulerService(@InjectTubingPlugin TubingPlugin plugin) {
        // plugin instance is injected
    }
}
```

**4. Multi-Implementation List:**

```java
@IocBean
public class HandlerCoordinator {
    public HandlerCoordinator(@IocMulti(EventHandler.class) List<EventHandler> handlers) {
        // All implementations of EventHandler are injected as a list
    }
}
```

### Interface Resolution

When a constructor parameter is an interface, Tubing follows a specific resolution strategy:

```java
if (aClass.isInterface()) {
    // 1. Check if existing bean implements interface
    Optional<Object> existingBean = beans.keySet().stream()
        .filter(aClass::isAssignableFrom)
        .map(beans::get)
        .findFirst();
    if (existingBean.isPresent()) {
        return existingBean.get();
    }

    // 2. Check if provider can handle it
    List<Method> currentProviders = providedBeans.stream()
        .filter(p -> p.getReturnType() == aClass)
        .collect(Collectors.toList());
    if (currentProviders.size() == 1) {
        return createBean(...);
    }
    if (currentProviders.size() > 1) {
        throw new IocException("Multiple bean providers found for interface");
    }

    // 3. Find implementations
    Set<Class<?>> subTypes = scanResult.getClassesImplementing(aClass).loadClasses()
        .stream()
        .filter(validBeans::contains)
        .collect(Collectors.toSet());

    Set<Class<?>> subtypeNonConditional = subTypes.stream()
        .filter(a -> !a.isAnnotationPresent(ConditionalOnMissingBean.class))
        .collect(Collectors.toSet());

    Set<Class<?>> subtypesOnMissing = subTypes.stream()
        .filter(a -> a.isAnnotationPresent(ConditionalOnMissingBean.class))
        .collect(Collectors.toSet());

    // Validation
    if (subTypes.isEmpty()) {
        throw new IocException("No classes implementing this interface");
    }
    if (subtypeNonConditional.size() > 1) {
        throw new IocException("Multiple beans found. Use @IocMultiProvider");
    }
    if (subtypeNonConditional.isEmpty() && subtypesOnMissing.size() > 1) {
        throw new IocException("Too many @ConditionalOnMissingBean");
    }

    // Select implementation
    Class<?> beanToCreate = subtypeNonConditional.isEmpty()
        ? subtypesOnMissing.iterator().next()
        : subtypeNonConditional.iterator().next();
    return createBean(scanResult, beanToCreate, ...);
}
```

**Resolution order:**

1. **Check existing beans**: If a bean already exists that implements the interface, use it
2. **Check providers**: If a provider method returns the interface, use it
3. **Find implementations**: Scan for classes implementing the interface
4. **Prefer non-conditional**: Choose non-conditional implementations over `@ConditionalOnMissingBean`
5. **Fall back to conditional**: If no non-conditional exists, use the conditional bean

**Example:**

```java
public interface StorageService {
    void save(String key, String value);
}

@IocBean
@ConditionalOnMissingBean
public class DefaultStorageService implements StorageService {
    // Used if no other implementation exists
}

// If user provides this, DefaultStorageService is skipped
@IocBean
public class CustomStorageService implements StorageService {
    // This implementation is preferred
}
```

### Bean Provider Methods

Beans can also be created by provider methods in `@TubingConfiguration` classes:

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static DatabaseConnection createConnection(
        @ConfigProperty("database.url") String url,
        @ConfigProperty("database.username") String username,
        @ConfigProperty("database.password") String password
    ) {
        return new DatabaseConnection(url, username, password);
    }
}
```

Provider methods:
- Must be `static`
- Can have parameters (resolved like constructor parameters)
- Return the bean instance
- Provide fine-grained control over bean creation

When instantiating a bean, if a provider method exists for that type, it's used instead of the constructor:

```java
Optional<Method> beanProvider = providers.stream()
    .filter(p -> p.getReturnType() == aClass)
    .findFirst();
if (beanProvider.isPresent()) {
    List<Object> params = buildParams(...);
    Object invoke = beanProvider.get().invoke(null, params.toArray());
    beans.putIfAbsent(beanProvider.get().getReturnType(), invoke);
    return invoke;
}
```

### Multi-Provider Patterns

Multi-providers create lists of beans implementing an interface:

```java
@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerJoinHandler implements EventHandler {
    // Added to List<EventHandler>
}

@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerQuitHandler implements EventHandler {
    // Added to List<EventHandler>
}

@IocBean
public class EventDispatcher {
    private final List<EventHandler> handlers;

    public EventDispatcher(@IocMulti(EventHandler.class) List<EventHandler> handlers) {
        // All EventHandler implementations are injected
        this.handlers = handlers;
    }
}
```

The container:
1. Finds all classes with `@IocMultiProvider(EventHandler.class)`
2. Instantiates each one
3. Collects them into a list
4. Stores the list in the bean registry under `EventHandler.class`
5. Injects the list when requested with `@IocMulti(EventHandler.class)`

### Singleton Pattern

All beans are singletons by default. Once a bean is instantiated, it's cached:

```java
if (beans.containsKey(aClass)) {
    return beans.get(aClass);
}
```

This means:
- Each bean type has exactly one instance
- Dependencies always receive the same instance
- No need for manual singleton patterns
- Thread-safe (instantiation happens during plugin startup, before concurrent access)

## Phase 6: Configuration Injection Phase

After a bean is instantiated, configuration properties are injected into fields and setter methods.

### Field Injection

Configuration properties annotated with `@ConfigProperty` are injected into fields:

```java
Object bean = declaredConstructor.newInstance(constructorParams.toArray());
PropertyInjector.injectConfigurationProperties(bean, getConfigurationFiles());
beans.putIfAbsent(aClass, bean);
```

The `PropertyInjector` processes:

**1. Fields with `@ConfigProperty`:**

```java
@IocBean
public class MessageService {
    @ConfigProperty("messages.prefix")
    private String prefix;

    @ConfigProperty("messages.suffix")
    private String suffix;

    @ConfigProperty("messages.colors.enabled")
    private boolean colorsEnabled;

    // Fields are injected after constructor runs
}
```

**2. Setter methods with `@ConfigProperty`:**

```java
@IocBean
public class FeatureService {
    private boolean enabled;

    @ConfigProperty("features.enabled")
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            initialize();
        }
    }
}
```

**3. Complex configuration objects:**

```java
@IocBean
public class ShopService {
    @ConfigProperty("shop.items")
    @ConfigObjectList(ShopItem.class)
    private List<ShopItem> items;

    @ConfigProperty("shop.settings")
    @ConfigEmbeddedObject(ShopSettings.class)
    private ShopSettings settings;
}
```

### Injection Timing

Configuration injection happens **after** the constructor runs but **before** the bean is returned to any dependents:

```
Constructor runs → Configuration injected → Bean stored in registry → Bean available to dependents
```

This means:
- Constructor parameters can include `@ConfigProperty` values
- Fields with `@ConfigProperty` are injected after the constructor
- By the time any other bean receives this bean as a dependency, all configuration is injected

### Why Separate Phases?

The separation of instantiation and configuration injection allows:

**1. Circular dependency support (limited):**

If Bean A and Bean B depend on each other through fields (not constructors), the container can:
- Instantiate Bean A (fields are null)
- Instantiate Bean B (receives Bean A with null fields)
- Inject configuration into Bean A
- Inject configuration into Bean B

**2. Cleaner constructor logic:**

Constructors can focus on initialization logic, while configuration values are cleanly separated into field injection.

**3. Late configuration binding:**

Configuration can be evaluated after the object is created, allowing for more complex scenarios like configuration transformers that need other beans.

### Configuration Injection Process

The `PropertyInjector` follows this process:

```java
public static void injectConfigurationProperties(Object bean,
                                                 Map<String, FileConfiguration> configs) {
    ConfigProperties configProperties = null;
    if (bean.getClass().isAnnotationPresent(ConfigProperties.class)) {
        configProperties = bean.getClass().getAnnotation(ConfigProperties.class);
    }

    // 1. Inject setter methods
    List<Method> configMethods = ReflectionUtils.getMethodsAnnotatedWith(
        bean.getClass(), ConfigProperty.class
    );
    for (Method configMethod : configMethods) {
        Optional<Object> parsedConfigValue = parseConfig(...);
        if (parsedConfigValue.isPresent()) {
            configMethod.invoke(bean, parsedConfigValue.get());
        }
    }

    // 2. Inject fields
    for (Field f : getAllFields(new LinkedList<>(), bean.getClass())) {
        if (!f.isAnnotationPresent(ConfigProperty.class)) {
            continue;
        }

        ConfigProperty annotation = f.getAnnotation(ConfigProperty.class);
        Optional<Object> parsedConfigValue = parseConfig(...);
        if (parsedConfigValue.isPresent()) {
            f.setAccessible(true);
            f.set(bean, parsedConfigValue.get());
        }
    }
}
```

### Configuration Property Resolution

Configuration properties are resolved from the loaded configuration files:

```java
String configProperty = prefix + configAnnotation.value();
Optional configValue = configRetrievalFunction.apply(configProperty);

if (!configValue.isPresent()) {
    if (configAnnotation.required()) {
        throw new ConfigurationException("Configuration not found for " + configProperty);
    }
    return Optional.empty();
}
```

The resolution supports:
- **Property prefixes** via `@ConfigProperties` on the class
- **Required properties** via `@ConfigProperty(required = true)`
- **Default values** (property not set = field keeps its default)
- **Type transformers** via `@ConfigTransformer`
- **Complex objects** via `@ConfigEmbeddedObject` and `@ConfigObjectList`

### TubingPlugin Injection

In addition to configuration properties, the container injects the plugin instance:

```java
Object bean = declaredConstructor.newInstance(constructorParams.toArray());
PropertyInjector.injectConfigurationProperties(bean, getConfigurationFiles());
TubingPluginInjector.inject(bean, tubingPlugin);
beans.putIfAbsent(aClass, bean);
```

This allows beans to access the plugin instance via field injection:

```java
@IocBean
public class TaskService {
    @InjectTubingPlugin
    private TubingBukkitPlugin plugin;

    public void scheduleTask(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
```

## Phase 7: Post-Initialization Phase

After all beans are instantiated and configured, `@AfterIocLoad` hooks are executed.

### AfterIocLoad Hooks

Methods annotated with `@AfterIocLoad` in `@TubingConfiguration` classes are invoked:

```java
for (Class<?> configurationClass : configurationClasses) {
    List<Method> afterMethods = ReflectionUtils.getMethodsAnnotatedWith(
        configurationClass, AfterIocLoad.class
    );
    for (Method afterMethod : afterMethods) {
        List<Object> params = buildParams(
            configurationClass, scanResult, validBeans, providers,
            multiProviders, afterMethod.getParameterTypes(),
            afterMethod.getParameterAnnotations()
        );
        afterMethod.invoke(null, params.toArray());
    }
}
```

### Purpose of AfterIocLoad

Use `@AfterIocLoad` for:

**1. Post-initialization logic that requires all beans to be ready:**

```java
@TubingConfiguration
public class DataConfiguration {

    @AfterIocLoad
    public static void initializeData(DatabaseService database, CacheService cache) {
        // All beans are instantiated and configured
        // Now we can perform cross-bean initialization
        database.connect();
        cache.warmUp(database.getAllKeys());
    }
}
```

**2. Validation that requires the full container:**

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateConfiguration(ConfigurationLoader config,
                                             Logger logger) {
        if (config.getConfigValue("feature.x").isEmpty() &&
            config.getConfigValue("feature.y").isEmpty()) {
            logger.severe("At least one feature must be enabled!");
            throw new IllegalStateException("Invalid configuration");
        }
    }
}
```

**3. Registering beans with external systems:**

```java
@TubingConfiguration
public class MetricsConfiguration {

    @AfterIocLoad
    public static void registerMetrics(List<MetricsProvider> providers,
                                      MetricsService metrics) {
        for (MetricsProvider provider : providers) {
            metrics.register(provider);
        }
    }
}
```

**4. Starting background tasks:**

```java
@TubingConfiguration
public class TaskConfiguration {

    @AfterIocLoad
    public static void startBackgroundTasks(TaskScheduler scheduler,
                                           @InjectTubingPlugin TubingPlugin plugin) {
        scheduler.startAll();
        plugin.getLogger().info("Background tasks started");
    }
}
```

### AfterIocLoad Method Requirements

`@AfterIocLoad` methods must be:
- **Static**: They're invoked on the class, not an instance
- **In a `@TubingConfiguration` class**: Only configuration classes are scanned for these methods
- **Public**: Must be accessible

They can have:
- **Parameters**: Any beans from the container (resolved via dependency injection)
- **No return value**: `void` methods (return value is ignored)

### Execution Order

`@AfterIocLoad` methods are executed in the order the configuration classes are discovered. If you need a specific order, use multiple methods and order them by their parameters:

```java
@TubingConfiguration
public class InitializationConfiguration {

    @AfterIocLoad
    public static void initializeDatabase(DatabaseService database) {
        // Runs first (no dependencies)
        database.connect();
    }

    @AfterIocLoad
    public static void initializeCache(CacheService cache, DatabaseService database) {
        // Runs after database is initialized (depends on database)
        cache.warmUp(database);
    }

    @AfterIocLoad
    public static void startServices(List<Service> services, CacheService cache) {
        // Runs after cache is initialized (depends on cache)
        services.forEach(Service::start);
    }
}
```

However, this is not guaranteed. For critical ordering, consider using explicit initialization in your main plugin class's `enable()` method.

## Bean Lifecycle During Reload

Some platforms support reloading plugins without restarting the server. Understanding how beans behave during reload is important.

### Container Recreation

When a plugin reloads, the **entire container is recreated**:

1. Old container is discarded (all beans are garbage collected)
2. New container is created from scratch
3. Full lifecycle runs again (discovery → instantiation → initialization)

This means:
- All beans are new instances
- All state in beans is lost
- Configuration is reloaded from files
- Hooks like `@AfterIocLoad` run again

### Persisting State Across Reloads

If you need state to survive reloads, persist it outside the bean:

**Option 1: Save to database/file:**

```java
@IocBean
public class PlayerDataService {
    private Map<UUID, PlayerData> cache;
    private final DatabaseService database;

    @AfterIocLoad
    public static void loadData(PlayerDataService service) {
        service.cache = service.database.loadAll();
    }

    public void saveAll() {
        database.saveAll(cache);
    }
}
```

**Option 2: Use static storage (not recommended):**

```java
@IocBean
public class CacheService {
    private static final Map<String, Object> PERSISTENT_CACHE = new HashMap<>();

    public Object get(String key) {
        return PERSISTENT_CACHE.get(key);
    }
}
```

Static storage survives reload, but it breaks proper encapsulation and can cause memory leaks.

### Reload Best Practices

**1. Design beans to be stateless when possible:**

```java
@IocBean
public class MessageService {
    @ConfigProperty("messages.prefix")
    private String prefix;

    public String format(String message) {
        return prefix + message;  // No state, just configuration
    }
}
```

**2. Use `@AfterIocLoad` to load state:**

```java
@TubingConfiguration
public class DataConfiguration {

    @AfterIocLoad
    public static void loadState(DataService dataService) {
        dataService.loadFromDisk();
    }
}
```

**3. Provide cleanup hooks:**

If your plugin supports reload, add a cleanup method:

```java
@IocBean
public class ConnectionPoolService {
    private HikariDataSource dataSource;

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
```

Call it in your plugin's `disable()` method:

```java
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void disable() {
        getIocContainer().get(ConnectionPoolService.class).shutdown();
    }
}
```

### Platform-Specific Reload Behavior

**Bukkit:**
- `/reload` recreates the container
- Listeners are unregistered automatically
- Commands remain registered (Bukkit limitation)

**BungeeCord:**
- `/greload` recreates the container
- Listeners are unregistered automatically

**Velocity:**
- No built-in reload mechanism
- Manual reload requires careful cleanup

## Lifecycle Best Practices

### Constructor vs. Field Injection

**Use constructor injection for:**
- Required dependencies
- Immutable dependencies
- Dependencies needed for initialization logic

```java
@IocBean
public class PlayerService {
    private final DatabaseService database;  // Required, immutable

    public PlayerService(DatabaseService database) {
        this.database = database;
    }
}
```

**Use field injection for:**
- Optional dependencies
- Configuration properties
- Plugin instance

```java
@IocBean
public class FeatureService {
    @ConfigProperty("features.enabled")
    private boolean enabled;  // Configuration, not a dependency

    @InjectTubingPlugin
    private TubingBukkitPlugin plugin;  // Platform instance
}
```

### Avoid Circular Dependencies

Circular constructor dependencies will cause initialization to fail:

```java
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { }  // Needs ServiceB
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }  // Needs ServiceA
}
// ERROR: Cannot instantiate, circular dependency!
```

**Solution 1: Refactor to eliminate the cycle:**

```java
@IocBean
public class SharedService {
    // Common logic extracted
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

**Solution 2: Use field injection for one side:**

```java
@IocBean
public class ServiceA {
    private ServiceB serviceB;

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
public class WiringConfiguration {
    @AfterIocLoad
    public static void wireCircular(ServiceA serviceA, ServiceB serviceB) {
        serviceA.setServiceB(serviceB);
    }
}
```

### Use Priority Sparingly

Most beans should not specify priority. Reserve `priority = true` for:
- Infrastructure beans (like `ConfigurationLoader`)
- Beans that register global handlers
- Beans that modify the container itself

### Leverage AfterIocLoad for Initialization

Instead of complex constructor logic, use `@AfterIocLoad` for:
- Multi-bean initialization
- External system registration
- Validation
- Startup tasks

This keeps constructors simple and makes initialization order explicit.

### Minimize Side Effects in Constructors

Constructors should focus on wiring dependencies, not performing work:

**Bad:**

```java
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        connect(url);  // Side effect in constructor
        loadAllData(); // Expensive operation
    }
}
```

**Good:**

```java
@IocBean
public class DatabaseService {
    private final String url;

    public DatabaseService(@ConfigProperty("db.url") String url) {
        this.url = url;  // Just store the dependency
    }

    public void connect() {
        // Explicit method for side effects
    }
}

@TubingConfiguration
public class DatabaseConfiguration {
    @AfterIocLoad
    public static void initDatabase(DatabaseService database) {
        database.connect();  // Explicit initialization
    }
}
```

This makes testing easier and initialization order clearer.

## Summary

The bean lifecycle in Tubing follows a well-defined, predictable sequence:

1. **Discovery**: ClassGraph scans for annotated classes
2. **Collection**: Beans and providers are catalogued
3. **Sorting**: Priority beans are identified
4. **Validation**: Conditional beans are evaluated
5. **Instantiation**: Beans are created with dependency resolution
6. **Configuration Injection**: Properties are injected into fields
7. **Post-Initialization**: `@AfterIocLoad` hooks execute

Understanding this lifecycle helps you:
- Write beans that initialize cleanly
- Avoid circular dependency issues
- Use priority effectively
- Leverage `@AfterIocLoad` for complex initialization
- Handle plugin reloads gracefully

## Next Steps

- **[Bean Registration](Bean-Registration.md)** - Learn about `@IocBean` in detail
- **[Bean Providers](Bean-Providers.md)** - Create beans with factory methods
- **[Dependency Injection](Dependency-Injection.md)** - Master dependency injection patterns
- **[Configuration Injection](Configuration-Injection.md)** - Inject configuration properties
- **[Post-Initialization](Post-Initialization.md)** - Deep dive into `@AfterIocLoad`

---

**See also:**
- [IoC Container](IoC-Container.md) - Container fundamentals
- [Multi-Implementation](Multi-Implementation.md) - Working with lists of beans
- [Conditional Beans](Conditional-Beans.md) - Conditional registration strategies
