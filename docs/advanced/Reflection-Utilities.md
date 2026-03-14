# Reflection Utilities

Tubing provides the `ReflectionUtils` class, a utility for common reflection operations used internally by the IoC container. While primarily used internally, understanding these utilities can help you work with annotations and configuration in advanced scenarios.

## Overview

The `ReflectionUtils` class is located in the `be.garagepoort.mcioc` package and provides static utility methods for:
- Finding methods annotated with specific annotations
- Retrieving configuration values from loaded configuration files
- Resolving nested configuration placeholders

These utilities power Tubing's annotation scanning, configuration injection, and bean lifecycle management.

## Class Methods

### getMethodsAnnotatedWith()

Finds all methods in a class (including inherited methods) that are annotated with specific annotations.

```java
public static List<Method> getMethodsAnnotatedWith(
    final Class<?> type,
    final Class<? extends Annotation>... annotations
)
```

**Parameters:**
- `type`: The class to search for annotated methods
- `annotations`: One or more annotation classes to match (methods must have ALL specified annotations)

**Returns:** A list of `Method` objects that have all the specified annotations

**Behavior:**
- Searches the class hierarchy up to (but not including) `Object.class`
- Includes private, protected, package-private, and public methods
- Returns methods that have **all** specified annotations (AND condition, not OR)
- Returns methods in the order they appear in the class hierarchy

**Example Usage:**

```java
// Find all methods annotated with @IocBeanProvider
List<Method> providers = ReflectionUtils.getMethodsAnnotatedWith(
    MyConfiguration.class,
    IocBeanProvider.class
);

// Find methods with multiple annotations
List<Method> configMethods = ReflectionUtils.getMethodsAnnotatedWith(
    MyService.class,
    ConfigProperty.class,
    Required.class
);
```

**Internal Usage:**

The IoC container uses this method extensively during bean discovery:

```java
// Find provider methods in configuration classes
List<Method> providers = configurationClasses.stream()
    .flatMap(c -> ReflectionUtils.getMethodsAnnotatedWith(c, IocBeanProvider.class).stream())
    .collect(Collectors.toList());

// Find multi-provider methods
List<Method> multiProviders = configurationClasses.stream()
    .flatMap(c -> ReflectionUtils.getMethodsAnnotatedWith(c, IocMultiProvider.class).stream())
    .collect(Collectors.toList());

// Find post-initialization hooks
List<Method> afterMethods = ReflectionUtils.getMethodsAnnotatedWith(
    configurationClass,
    AfterIocLoad.class
);

// Find configuration property setter methods
List<Method> configMethods = ReflectionUtils.getMethodsAnnotatedWith(
    bean.getClass(),
    ConfigProperty.class
);
```

### getConfigValue()

Retrieves a configuration value from loaded configuration files with support for nested placeholders.

```java
public static <T> Optional<T> getConfigValue(
    String identifier,
    Map<String, FileConfiguration> configs
)
```

**Parameters:**
- `identifier`: The configuration path (supports file selectors and nested placeholders)
- `configs`: Map of configuration file IDs to `FileConfiguration` objects

**Returns:** `Optional<T>` containing the configuration value, or empty if not found

**Configuration Path Format:**

**Simple path (from default `config.yml`):**
```java
Optional<Object> value = ReflectionUtils.getConfigValue("database.host", configs);
```

**File selector (specify which config file):**
```java
// Syntax: "fileId:path.to.property"
Optional<Object> value = ReflectionUtils.getConfigValue("messages:prefix", configs);
```

**With nested placeholders:**
```java
// config.yml:
// environment: "production"
// database:
//   production-host: "db.example.com"
//   host: "%environment%-host"

Optional<Object> host = ReflectionUtils.getConfigValue("database.host", configs);
// Returns: "production-host" (after resolving %environment%)
```

**Nested Placeholder Syntax:**

Placeholders use `%placeholder-key%` syntax and are recursively resolved:

```yaml
# config.yml
server-type: "production"
database:
  host: "db-%server-type%.example.com"
  port: 5432
  connection-string: "jdbc:postgresql://%database.host%:%database.port%/mydb"
```

```java
// Resolves to: "jdbc:postgresql://db-production.example.com:5432/mydb"
Optional<Object> connectionString = ReflectionUtils.getConfigValue(
    "database.connection-string",
    configs
);
```

**File Selector Examples:**

```yaml
# config.yml
default-message: "Hello"

# messages.yml
greeting: "Welcome"
farewell: "Goodbye"
```

```java
// From default config.yml
Optional<Object> msg1 = ReflectionUtils.getConfigValue("default-message", configs);

// From messages.yml
Optional<Object> msg2 = ReflectionUtils.getConfigValue("messages:greeting", configs);
Optional<Object> msg3 = ReflectionUtils.getConfigValue("messages:farewell", configs);
```

### getConfigStringValue()

Similar to `getConfigValue()`, but specifically retrieves string values using `FileConfiguration.getString()`.

```java
public static Optional<String> getConfigStringValue(
    String identifier,
    Map<String, FileConfiguration> configs
)
```

**Parameters:**
- `identifier`: The configuration path (supports file selectors and nested placeholders)
- `configs`: Map of configuration file IDs to `FileConfiguration` objects

**Returns:** `Optional<String>` containing the string value, or empty if not found

**Difference from getConfigValue():**

- `getConfigValue()`: Uses `FileConfiguration.get()` - returns `Object` (may be List, Map, etc.)
- `getConfigStringValue()`: Uses `FileConfiguration.getString()` - always returns `String`

**Example:**

```yaml
# config.yml
message: "Hello World"
count: 42
items:
  - apple
  - banana
```

```java
// Returns: Optional<String>("Hello World")
Optional<String> message = ReflectionUtils.getConfigStringValue("message", configs);

// Returns: Optional<String>("42") - number converted to string
Optional<String> count = ReflectionUtils.getConfigStringValue("count", configs);

// Returns: Optional<String>("[apple, banana]") - list converted to string representation
Optional<String> items = ReflectionUtils.getConfigStringValue("items", configs);
```

**When to Use:**

- Use `getConfigStringValue()` when you need string representation of values
- Use `getConfigValue()` when you need the actual type (numbers, lists, maps)

## Common Use Cases

### Finding Custom Annotation Methods

If you're building a plugin extension system or custom annotation processor:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    String priority() default "NORMAL";
}

public class EventScanner {

    public List<Method> findEventHandlers(Class<?> listenerClass) {
        return ReflectionUtils.getMethodsAnnotatedWith(
            listenerClass,
            EventHandler.class
        );
    }

    public void registerHandlers(Object listener) {
        List<Method> handlers = findEventHandlers(listener.getClass());

        for (Method handler : handlers) {
            EventHandler annotation = handler.getAnnotation(EventHandler.class);
            String priority = annotation.priority();

            // Register the handler method
            registerHandler(listener, handler, priority);
        }
    }
}
```

### Building Configuration Objects

Create configuration objects using reflection utilities:

```java
public class DatabaseConfig {

    private final String host;
    private final int port;
    private final String username;

    public static DatabaseConfig fromConfigFiles(
            Map<String, FileConfiguration> configs) {

        String host = ReflectionUtils.getConfigStringValue("database.host", configs)
            .orElse("localhost");

        Integer port = ReflectionUtils.<Integer>getConfigValue("database.port", configs)
            .orElse(3306);

        String username = ReflectionUtils.getConfigStringValue("database.username", configs)
            .orElse("root");

        return new DatabaseConfig(host, port, username);
    }

    private DatabaseConfig(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }
}
```

### Dynamic Configuration Resolution

Resolve configuration dynamically based on runtime conditions:

```java
@IocBean
public class EnvironmentService {

    private final Map<String, FileConfiguration> configs;

    public EnvironmentService(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigs();
    }

    public String getEnvironmentSpecificValue(String key) {
        // Get environment from config
        String env = ReflectionUtils.getConfigStringValue("environment", configs)
            .orElse("development");

        // Build environment-specific key
        String envKey = env + "." + key;

        // Try environment-specific first, fall back to default
        return ReflectionUtils.getConfigStringValue(envKey, configs)
            .orElseGet(() -> ReflectionUtils.getConfigStringValue(key, configs)
                .orElse(""));
    }
}
```

**config.yml:**
```yaml
environment: "production"

production.database.host: "prod-db.example.com"
development.database.host: "localhost"
database.host: "default-db.example.com"
```

### Multi-Annotation Scanning

Find methods that must have multiple annotations:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Authorized { }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited { }

public class SecurityScanner {

    public List<Method> findSecureEndpoints(Class<?> controllerClass) {
        // Find methods with BOTH @Authorized AND @RateLimited
        return ReflectionUtils.getMethodsAnnotatedWith(
            controllerClass,
            Authorized.class,
            RateLimited.class
        );
    }
}
```

### Configuration Validation

Validate that required configuration values exist:

```java
@IocBean
public class ConfigValidator {

    private final Map<String, FileConfiguration> configs;

    public ConfigValidator(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigs();
    }

    public void validateRequiredKeys(String... requiredKeys) {
        List<String> missing = new ArrayList<>();

        for (String key : requiredKeys) {
            Optional<Object> value = ReflectionUtils.getConfigValue(key, configs);
            if (!value.isPresent()) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required configuration keys: " + missing
            );
        }
    }
}
```

**Usage:**

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateConfig(ConfigValidator validator) {
        validator.validateRequiredKeys(
            "database.host",
            "database.port",
            "database.username",
            "server.name"
        );
    }
}
```

## Best Practices

### When to Use ReflectionUtils

**Use ReflectionUtils when:**
- Building custom annotation processors or scanners
- Creating configuration utilities that need to work with Tubing's configuration system
- Building plugin extension systems that scan for custom annotations
- Implementing dynamic configuration resolution
- Creating development tools or debugging utilities

**Don't use ReflectionUtils for:**
- Normal bean dependency injection (use constructor parameters)
- Standard configuration injection (use `@ConfigProperty`)
- Regular bean discovery (let the IoC container handle it)
- Performance-critical code paths (reflection is slower than direct access)

### Performance Considerations

**Method Scanning:**
- `getMethodsAnnotatedWith()` walks the class hierarchy, which can be slow for deep hierarchies
- Cache results when scanning the same class multiple times
- Prefer scanning during initialization, not per-request

```java
// Bad: Scanning every time
public void handleRequest(Object handler) {
    List<Method> methods = ReflectionUtils.getMethodsAnnotatedWith(
        handler.getClass(),
        RequestHandler.class
    );
    // Process methods...
}

// Good: Cache scanned methods
private final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();

public void handleRequest(Object handler) {
    List<Method> methods = methodCache.computeIfAbsent(
        handler.getClass(),
        clazz -> ReflectionUtils.getMethodsAnnotatedWith(clazz, RequestHandler.class)
    );
    // Process methods...
}
```

**Configuration Access:**
- Configuration files are already loaded in memory, so lookups are fast
- Nested placeholder resolution is recursive and may be expensive for deeply nested values
- Cache frequently accessed configuration values

```java
// Bad: Looking up every time
public void sendMessage(Player player) {
    String prefix = ReflectionUtils.getConfigStringValue("messages.prefix", configs)
        .orElse("");
    player.sendMessage(prefix + "Hello!");
}

// Good: Cache during initialization
@IocBean
public class MessageService {
    @ConfigProperty("messages.prefix")
    private String prefix;

    public void sendMessage(Player player) {
        player.sendMessage(prefix + "Hello!");
    }
}
```

### Thread Safety

**ReflectionUtils methods are thread-safe:**
- All methods are stateless
- Configuration maps passed as parameters should be immutable
- Method objects returned are safe to use across threads

**Configuration access is thread-safe:**
- `FileConfiguration` objects are thread-safe for reads
- Don't modify configuration at runtime without synchronization
- The IoC container loads configuration once during initialization

### Error Handling

**getMethodsAnnotatedWith():**
- Never returns null, always returns a list (may be empty)
- No exceptions thrown during normal operation
- Methods from all class hierarchy levels are included

**getConfigValue() and getConfigStringValue():**
- Return `Optional` - never null
- Empty Optional if configuration key doesn't exist
- Empty Optional if placeholder cannot be resolved
- Type mismatches may throw `ClassCastException` when extracting value

```java
// Always check Optional
Optional<String> value = ReflectionUtils.getConfigStringValue("some.key", configs);
if (value.isPresent()) {
    String str = value.get();
    // Use str safely
} else {
    // Handle missing configuration
}

// Or use orElse/orElseGet for defaults
String value = ReflectionUtils.getConfigStringValue("some.key", configs)
    .orElse("default-value");
```

### Placeholder Resolution

**Nested placeholders are resolved recursively:**
```yaml
a: "valueA"
b: "%a%_suffix"
c: "%b%_final"
```

```java
// Returns: "valueA_suffix_final"
Optional<String> c = ReflectionUtils.getConfigStringValue("c", configs);
```

**Circular references are not detected:**
```yaml
# Don't do this - causes infinite loop
a: "%b%"
b: "%a%"
```

**Best practices:**
- Keep placeholder depth shallow (1-2 levels maximum)
- Avoid circular references in configuration
- Use meaningful placeholder names
- Document placeholder usage in configuration comments

### Annotation Requirements

When using `getMethodsAnnotatedWith()` with multiple annotations:

```java
@Retention(RetentionPolicy.RUNTIME)  // Required for runtime reflection
@Target(ElementType.METHOD)           // Must target methods
public @interface MyAnnotation { }
```

**Requirements:**
- Annotations must have `@Retention(RetentionPolicy.RUNTIME)`
- For method scanning, use `@Target(ElementType.METHOD)`
- Annotations must be visible to the reflection API
- All specified annotations must be present on the method (AND condition)

## Internal Implementation Details

### How getMethodsAnnotatedWith() Works

```java
public static List<Method> getMethodsAnnotatedWith(
        final Class<?> type,
        final Class<? extends Annotation>... annotations) {

    final List<Method> methods = new ArrayList<>();
    Class<?> klass = type;

    // Walk up the class hierarchy
    while (klass != Object.class) {
        // Get all declared methods (including private)
        for (final Method method : klass.getDeclaredMethods()) {
            // Check if method has ALL specified annotations
            if (Arrays.stream(annotations).allMatch(method::isAnnotationPresent)) {
                methods.add(method);
            }
        }
        klass = klass.getSuperclass();
    }

    return methods;
}
```

**Key behaviors:**
- Uses `getDeclaredMethods()` to include private methods
- Walks superclass hierarchy up to (not including) `Object.class`
- Uses `allMatch()` - method must have ALL annotations (AND condition)
- Returns methods in hierarchy order (child class first, then parent)

### How getConfigValue() Works

```java
public static <T> Optional<T> getConfigValue(
        String identifier,
        Map<String, FileConfiguration> configs) {

    // 1. Resolve nested placeholders first
    identifier = replaceNestedValues(identifier, configs);

    // 2. Default to "config.yml"
    String configFileId = "config";
    String path = identifier;

    // 3. Parse file selector if present
    String[] fileSelectors = identifier.split(":", 2);
    if (fileSelectors.length == 2) {
        configFileId = fileSelectors[0];
        path = fileSelectors[1];
    }

    // 4. Retrieve from appropriate config file
    return Optional.ofNullable((T) configs.get(configFileId).get(path));
}
```

**Nested placeholder resolution:**
```java
private static String replaceNestedValues(
        String identifier,
        Map<String, FileConfiguration> configs) {

    // Find all %placeholder% patterns
    String regexString = Pattern.quote("%") + "(.*?)" + Pattern.quote("%");
    Pattern pattern = Pattern.compile(regexString);
    Matcher matcher = pattern.matcher(identifier);

    // Replace each placeholder with its value
    while (matcher.find()) {
        String nestedConfig = matcher.group(1);
        Optional<String> configValue = getConfigValue(nestedConfig, configs);
        if (configValue.isPresent()) {
            identifier = identifier.replace("%" + nestedConfig + "%", configValue.get());
        }
    }

    return identifier;
}
```

**Process:**
1. Find all `%key%` patterns using regex
2. Recursively resolve each placeholder
3. Replace placeholder with resolved value
4. Continue until no placeholders remain

## Advanced Patterns

### Custom Configuration Loader

Build a custom configuration loader using ReflectionUtils:

```java
@IocBean
public class CustomConfigLoader {

    private final Map<String, FileConfiguration> configs;

    public CustomConfigLoader(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigs();
    }

    public <T> T loadConfigObject(String basePath, Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            T instance = constructor.newInstance();

            // Find all @ConfigProperty methods
            List<Method> configMethods = ReflectionUtils.getMethodsAnnotatedWith(
                clazz,
                ConfigProperty.class
            );

            for (Method method : configMethods) {
                ConfigProperty annotation = method.getAnnotation(ConfigProperty.class);
                String fullPath = basePath + "." + annotation.value();

                Optional<Object> value = ReflectionUtils.getConfigValue(fullPath, configs);
                if (value.isPresent()) {
                    method.setAccessible(true);
                    method.invoke(instance, value.get());
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config object", e);
        }
    }
}
```

### Plugin Extension Discovery

Discover and register plugin extensions:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Extension {
    String name();
    int priority() default 0;
}

@IocBean
public class ExtensionRegistry {

    private final Map<String, List<Method>> extensions = new HashMap<>();

    public void registerExtensions(Object provider) {
        List<Method> extensionMethods = ReflectionUtils.getMethodsAnnotatedWith(
            provider.getClass(),
            Extension.class
        );

        for (Method method : extensionMethods) {
            Extension annotation = method.getAnnotation(Extension.class);
            String name = annotation.name();

            extensions.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
        }
    }

    public List<Method> getExtensions(String name) {
        return extensions.getOrDefault(name, Collections.emptyList());
    }
}
```

### Dynamic Feature Flags

Implement feature flags using configuration:

```java
@IocBean
public class FeatureFlags {

    private final Map<String, FileConfiguration> configs;

    public FeatureFlags(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigs();
    }

    public boolean isEnabled(String featureName) {
        String key = "features." + featureName + ".enabled";
        return ReflectionUtils.<Boolean>getConfigValue(key, configs)
            .orElse(false);
    }

    public <T> T getFeatureConfig(String featureName, String configKey, T defaultValue) {
        String key = "features." + featureName + "." + configKey;
        return ReflectionUtils.<T>getConfigValue(key, configs)
            .orElse(defaultValue);
    }
}
```

**config.yml:**
```yaml
features:
  pvp:
    enabled: true
    cooldown: 30
  trading:
    enabled: false
    max-distance: 10
```

**Usage:**
```java
@IocBean
public class PvPHandler {

    private final FeatureFlags features;

    public PvPHandler(FeatureFlags features) {
        this.features = features;
    }

    public void handlePvP(Player attacker, Player target) {
        if (!features.isEnabled("pvp")) {
            attacker.sendMessage("PvP is disabled!");
            return;
        }

        int cooldown = features.getFeatureConfig("pvp", "cooldown", 30);
        // Handle PvP with cooldown...
    }
}
```

## Limitations

### What ReflectionUtils Cannot Do

**Cannot find:**
- Fields (only methods are supported by `getMethodsAnnotatedWith()`)
- Constructors
- Classes (use ClassGraph via `IocContainer.getReflections()`)
- Annotations on method parameters

**Cannot modify:**
- Configuration files at runtime
- Method implementations
- Annotation values

**Performance limitations:**
- Reflection is slower than direct access
- Method scanning walks entire class hierarchy
- No caching built into ReflectionUtils (you must implement caching)

### When to Use ClassGraph Instead

For more complex scanning needs, use ClassGraph directly:

```java
IocContainer container = getIocContainer();
ScanResult scanResult = container.getReflections();

// Find all classes implementing an interface
List<Class<? extends Plugin>> plugins = scanResult
    .getClassesImplementing(Plugin.class)
    .loadClasses(Plugin.class);

// Find all classes with an annotation
List<Class<?>> controllers = scanResult
    .getClassesWithAnnotation(Controller.class)
    .loadClasses();

// Find all subclasses
List<Class<? extends Command>> commands = scanResult
    .getSubclasses(Command.class)
    .loadClasses(Command.class);
```

See [IoC Container](../core/IoC-Container.md#understanding-classgraph-scanning) for more details on ClassGraph usage.

## Summary

The `ReflectionUtils` class provides essential utilities for:

**Method Scanning:**
- Find methods with specific annotations
- Support for multiple annotations (AND condition)
- Search class hierarchy automatically

**Configuration Access:**
- Retrieve values from loaded configuration files
- Support for file selectors (`fileId:path`)
- Automatic nested placeholder resolution (`%key%`)
- Type-safe Optional returns

**Best Practices:**
- Use for custom annotation processors and plugin extensions
- Cache scanning results for performance
- Prefer `@ConfigProperty` injection over manual configuration access
- Handle Optional values properly
- Avoid circular placeholder references

**Integration:**
- Works seamlessly with Tubing's IoC container
- Used internally for bean discovery and configuration injection
- Thread-safe for concurrent access
- Compatible with all Tubing platforms

## Next Steps

- **[Bean Lifecycle](../core/Bean-Lifecycle.md)** - See how ReflectionUtils is used internally
- **[Configuration Injection](../core/Configuration-Injection.md)** - Understand configuration loading
- **[IoC Container](../core/IoC-Container.md)** - Learn about ClassGraph scanning
- **[Bean Providers](../core/Bean-Providers.md)** - Provider method discovery

---

**See also:**
- [Post-Initialization](../core/Post-Initialization.md) - `@AfterIocLoad` method discovery
- [Configuration Files](../core/Configuration-Files.md) - FileConfiguration API
- [Project Structure](../getting-started/Project-Structure.md) - Organizing plugin code
