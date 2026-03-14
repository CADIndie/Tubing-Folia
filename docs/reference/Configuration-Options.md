# Configuration Options Reference

This reference document provides comprehensive details about all configuration-related features in Tubing, including supported property types, type conversion rules, configuration transformers, property references, and ConfigurationFile options.

## Table of Contents

- [Annotations Reference](#annotations-reference)
- [Supported Property Types](#supported-property-types)
- [Type Conversion Rules](#type-conversion-rules)
- [Configuration Transformers](#configuration-transformers)
- [Property Reference Syntax](#property-reference-syntax)
- [ConfigurationFile Options](#configurationfile-options)
- [Best Practices](#best-practices)

## Annotations Reference

### @ConfigProperty

Injects a configuration value from YAML into a field, constructor parameter, or setter method.

**Location:** `be.garagepoort.mcioc.configuration.ConfigProperty`

**Target:** Field, Parameter, Method

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | String | Yes | - | Property path in YAML (e.g., "database.host") |
| `required` | boolean | No | false | Whether the property must exist in configuration |
| `error` | String | No | "" | Custom error message when required property is missing |

**Examples:**

```java
// Basic field injection
@ConfigProperty("server.max-players")
private int maxPlayers;

// Required property with custom error
@ConfigProperty(
    value = "database.password",
    required = true,
    error = "Database password is required in config.yml"
)
private String password;

// Constructor parameter injection
public MyService(@ConfigProperty("timeout") int timeout) {
    this.timeout = timeout;
}

// Setter method injection
@ConfigProperty("debug-mode")
public void setDebugMode(boolean debug) {
    this.debugMode = debug;
}
```

### @ConfigProperties

Defines a common prefix for all @ConfigProperty annotations in a class.

**Location:** `be.garagepoort.mcioc.configuration.ConfigProperties`

**Target:** Type (class)

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | String | Yes | - | Common prefix for all properties in the class |

**Examples:**

```java
@IocBean
@ConfigProperties("database")
public class DatabaseConfig {
    @ConfigProperty("host")        // Resolves to: database.host
    private String host;

    @ConfigProperty("port")        // Resolves to: database.port
    private int port;

    @ConfigProperty("name")        // Resolves to: database.name
    private String database;
}

// Multi-level prefix
@ConfigProperties("features.homes")
public class HomeConfig {
    @ConfigProperty("max-homes")   // Resolves to: features.homes.max-homes
    private int maxHomes;
}
```

### @ConfigTransformer

Applies one or more transformers to convert configuration values before injection.

**Location:** `be.garagepoort.mcioc.configuration.ConfigTransformer`

**Target:** Field, Parameter

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | Class<? extends IConfigTransformer>[] | Yes | - | Transformer class(es) to apply |

**Examples:**

```java
// Single transformer
@ConfigProperty("difficulty")
@ConfigTransformer(ToEnum.class)
private Difficulty difficulty;

// Multiple transformers (chained left-to-right)
@ConfigProperty("commands")
@ConfigTransformer({ToLowerCase.class, RemoveDuplicates.class})
private List<String> commands;

// With embedded objects
public class GameSettings {
    @ConfigProperty("materials")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> materials;
}
```

### @ConfigEmbeddedObject

Maps a YAML configuration section to a Java object.

**Location:** `be.garagepoort.mcioc.configuration.ConfigEmbeddedObject`

**Target:** Field, Parameter

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | Class | Yes | - | The class to instantiate and populate |

**Examples:**

```java
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;
}

@IocBean
public class AppConfig {
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings database;
}
```

**YAML:**
```yaml
database:
  host: "localhost"
  port: 3306
```

### @ConfigObjectList

Maps a YAML list of objects to a Java List.

**Location:** `be.garagepoort.mcioc.configuration.ConfigObjectList`

**Target:** Field, Parameter

**Attributes:**

| Attribute | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `value` | Class | Yes | - | The class to instantiate for each list item |

**Examples:**

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

**YAML:**
```yaml
items:
  - name: "Diamond Sword"
    price: 500
  - name: "Iron Armor"
    price: 250
```

## Supported Property Types

### Primitive Types

| Java Type | YAML Example | Default Value | Notes |
|-----------|--------------|---------------|-------|
| `boolean` | `true` | `false` | Case-insensitive: true/false, yes/no |
| `byte` | `127` | `0` | Range: -128 to 127 |
| `short` | `32000` | `0` | Range: -32,768 to 32,767 |
| `int` | `42` | `0` | Range: -2,147,483,648 to 2,147,483,647 |
| `long` | `9223372036854775807` | `0` | Range: -2^63 to 2^63-1 |
| `float` | `3.14` | `0.0` | Single-precision floating point |
| `double` | `3.14159` | `0.0` | Double-precision floating point |
| `char` | `'A'` | `'\u0000'` | Single character |

**Example:**
```java
@ConfigProperty("debug")
private boolean debug = false;

@ConfigProperty("max-players")
private int maxPlayers = 100;

@ConfigProperty("spawn-radius")
private double spawnRadius = 50.0;
```

### String Type

| Java Type | YAML Example | Default Value | Notes |
|-----------|--------------|---------------|-------|
| `String` | `"Hello World"` | `null` | UTF-8 encoded, supports color codes |

**Examples:**
```yaml
message: "Welcome to the server!"
prefix: "&8[&6Server&8]&r "
multiline: |
  This is a
  multiline string
quoted: "String with 'quotes'"
```

```java
@ConfigProperty("message")
private String message;

@ConfigProperty("prefix")
private String prefix = "&8[&aServer&8]&r ";
```

### Collection Types

#### List

| Java Type | YAML Example | Default Value | Notes |
|-----------|--------------|---------------|-------|
| `List<String>` | `["a", "b"]` | `null` or empty list | Ordered, allows duplicates |
| `List<Integer>` | `[1, 2, 3]` | `null` or empty list | Type-safe integer list |
| `List<T>` | See YAML | `null` or empty list | Generic type support |

**YAML Examples:**
```yaml
# String list
worlds:
  - "world"
  - "world_nether"
  - "world_the_end"

# Integer list
rewards:
  - 100
  - 250
  - 500

# Inline format
commands: ["home", "spawn", "tpa"]
```

**Java Examples:**
```java
@ConfigProperty("worlds")
private List<String> worlds = new ArrayList<>();

@ConfigProperty("rewards")
private List<Integer> rewards;

@ConfigProperty("commands")
private List<String> commands = Arrays.asList("help");
```

#### Set

| Java Type | YAML Example | Default Value | Notes |
|-----------|--------------|---------------|-------|
| `Set<String>` | `["a", "b"]` | `null` or empty set | Unordered, no duplicates |
| `Set<T>` | See YAML | `null` or empty set | Generic type support |

**Example:**
```yaml
unique-items:
  - "DIAMOND"
  - "EMERALD"
  - "DIAMOND"  # Duplicate automatically removed in Set
```

```java
@ConfigProperty("unique-items")
private Set<String> uniqueItems = new HashSet<>();
```

#### Map

| Java Type | YAML Example | Default Value | Notes |
|-----------|--------------|---------------|-------|
| `Map<String, String>` | `{key: "value"}` | `null` or empty map | String key-value pairs |
| `Map<String, Integer>` | `{key: 123}` | `null` or empty map | String keys, integer values |
| `Map<K, V>` | See YAML | `null` or empty map | Generic type support |

**YAML Examples:**
```yaml
world-names:
  world: "Overworld"
  world_nether: "The Nether"
  world_the_end: "The End"

price-list:
  diamond_sword: 500
  iron_sword: 100
  stone_sword: 25
```

**Java Examples:**
```java
@ConfigProperty("world-names")
private Map<String, String> worldNames;

@ConfigProperty("price-list")
private Map<String, Integer> priceList = new HashMap<>();
```

### Enum Types (with Transformer)

Enums require the `@ConfigTransformer(ToEnum.class)` annotation.

**Example:**
```java
public enum GameMode {
    SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
}

@ConfigProperty("default-gamemode")
@ConfigTransformer(ToEnum.class)
private GameMode gamemode;
```

**YAML:**
```yaml
default-gamemode: "SURVIVAL"
```

### Complex Types (with Custom Transformers)

| Type | Transformer | YAML Example | Notes |
|------|-------------|--------------|-------|
| `UUID` | Custom | `"069a79f4-44e9-4726-a5be-fca90e38aaf5"` | Requires UUID transformer |
| `Duration` | Custom | `"5m"`, `"30s"`, `"2h"` | Requires duration parser |
| `LocalTime` | Custom | `"14:30:00"` | ISO-8601 time format |
| `LocalDate` | Custom | `"2025-01-15"` | ISO-8601 date format |
| `Location` | Custom | `"100,64,200,world"` | Custom format parser |
| `Material` (Bukkit) | `ToMaterials` | `"STONE"`, `"*_WOOD"` | Wildcard support |

## Type Conversion Rules

### Automatic Conversions

Tubing automatically converts between compatible types:

| From | To | Rule | Example |
|------|----|----|---------|
| String | int | Parse integer | `"42"` → `42` |
| String | double | Parse decimal | `"3.14"` → `3.14` |
| String | boolean | Parse boolean | `"true"` → `true` |
| int | double | Widen | `42` → `42.0` |
| int | String | Convert | `42` → `"42"` |
| List | Set | Deduplicate | `[1,2,2]` → `{1,2}` |

### String to Boolean

Boolean values are case-insensitive:

| YAML Value | Java Result |
|------------|-------------|
| `true`, `yes`, `on`, `1` | `true` |
| `false`, `no`, `off`, `0` | `false` |

**Example:**
```yaml
# All these are valid booleans
enabled: true
debug: yes
pvp: on
fly: 1
disabled: false
```

### Number Conversions

**Widening (automatic):**
- byte → short → int → long → float → double

**Narrowing (may lose precision):**
- Requires explicit transformer or validation

**Example:**
```yaml
# Integer to double (automatic)
value: 42  # Can be injected into double field

# Double to integer (truncates)
value: 3.14  # Injected as 3 into int field
```

### Collection Conversions

**List to Set:**
```java
@ConfigProperty("items")
private Set<String> items;  // Duplicates automatically removed
```

**YAML:**
```yaml
items:
  - "A"
  - "B"
  - "A"  # Duplicate removed → {"A", "B"}
```

**Array to List:**
```yaml
# Inline array format
values: [1, 2, 3]
```

```java
@ConfigProperty("values")
private List<Integer> values;  // [1, 2, 3]
```

### Null and Missing Values

| Scenario | Behavior |
|----------|----------|
| Property exists, value is `null` | Field is set to `null` |
| Property missing, field has default | Default value is kept |
| Property missing, field has no default | Field is `null` (or 0 for primitives) |
| Property required but missing | Plugin fails to load with error |

**Example:**
```java
// Property: "optional-value: null"
@ConfigProperty("optional-value")
private String value;  // null

// Property missing
@ConfigProperty("missing")
private String missing = "default";  // "default" (kept)

// Property required
@ConfigProperty(value = "required", required = true)
private String required;  // Error if missing
```

## Configuration Transformers

### Built-in Transformers

#### ToEnum

Converts strings to enum constants.

**Interface:** `IConfigTransformer<Enum<T>, String>`

**Location:** `be.garagepoort.mcioc.configuration.transformers.ToEnum`

**Example:**
```java
public enum Difficulty {
    EASY, NORMAL, HARD, EXTREME
}

@ConfigProperty("difficulty")
@ConfigTransformer(ToEnum.class)
private Difficulty difficulty;
```

**YAML:**
```yaml
difficulty: "HARD"
```

#### ToLowerCase

Converts strings or string collections to lowercase.

**Interface:** `IConfigTransformer<Object, Object>`

**Location:** `be.garagepoort.mcioc.configuration.transformers.ToLowerCase`

**Supports:** String, Collection<String>

**Example:**
```java
@ConfigProperty("commands")
@ConfigTransformer(ToLowerCase.class)
private List<String> commands;
```

**YAML:**
```yaml
commands:
  - "HOME"
  - "SPAWN"
```

**Result:** `["home", "spawn"]`

#### ToMaterials (Bukkit)

Converts material names to Bukkit Material enums with wildcard support.

**Interface:** `IConfigTransformer<Object, Object>`

**Location:** `be.garagepoort.mcioc.tubingbukkit.config.transformers.ToMaterials`

**Supports:** String (with wildcards), Collection<String>

**Wildcard Patterns:**

| Pattern | Matches | Example |
|---------|---------|---------|
| `*_WOOD` | Materials ending with `_WOOD` | OAK_WOOD, BIRCH_WOOD |
| `DIAMOND_*` | Materials starting with `DIAMOND_` | DIAMOND_SWORD, DIAMOND_ORE |
| `*_ORE` | All ores | IRON_ORE, GOLD_ORE |
| `*STONE*` | Materials containing `STONE` | STONE, COBBLESTONE |

**Example:**
```java
@ConfigProperty("allowed-blocks")
@ConfigTransformer(ToMaterials.class)
private Set<Material> allowedBlocks;
```

**YAML:**
```yaml
allowed-blocks:
  - "STONE"
  - "*_WOOD"
  - "DIAMOND_*"
```

### Custom Transformer Interface

```java
public interface IConfigTransformer<T, S> {
    T mapConfig(S config);
}
```

**Type Parameters:**
- `T` - Output type (what you want)
- `S` - Input type (what YAML provides)

### Creating Custom Transformers

**Basic Transformer:**
```java
public class ToUUID implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        try {
            return UUID.fromString(config);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid UUID: " + config, e);
        }
    }
}
```

**Transformer with Validation:**
```java
public class ToPositiveInt implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer value) {
        if (value <= 0) {
            throw new ConfigurationException(
                "Value must be positive, got: " + value
            );
        }
        return value;
    }
}
```

**Transformer with Dependencies:**
```java
public class ToLocation implements IConfigTransformer<Location, String> {
    private final Server server;

    @ConfigProperty("default-world")
    private String defaultWorld = "world";

    public ToLocation(Server server) {
        this.server = server;
    }

    @Override
    public Location mapConfig(String config) {
        String[] parts = config.split(",");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        String worldName = parts.length > 3 ? parts[3] : defaultWorld;

        World world = server.getWorld(worldName);
        return new Location(world, x, y, z);
    }
}
```

### Transformer Execution Order

When multiple transformers are specified, they execute left-to-right:

```java
@ConfigProperty("values")
@ConfigTransformer({TrimWhitespace.class, ToLowerCase.class, ValidateNotEmpty.class})
private List<String> values;
```

**Execution:**
1. `TrimWhitespace` transforms input
2. `ToLowerCase` transforms result of step 1
3. `ValidateNotEmpty` validates result of step 2
4. Final result injected into field

## Property Reference Syntax

Property references allow values from one configuration file to be used in another.

### Syntax

```
{{identifier:path.to.property}}
```

**Components:**
- `{{` - Opening delimiter
- `identifier` - Configuration file identifier (usually filename without .yml)
- `:` - Separator
- `path.to.property` - Property path using dot notation
- `}}` - Closing delimiter

### Examples

**Cross-file references:**
```yaml
# config.yml
server:
  name: "Awesome Server"
  prefix: "&8[&6Server&8]&r "

# messages.yml
welcome: "{{config:server.prefix}} Welcome to {{config:server.name}}!"
error: "{{config:server.prefix}} &cAn error occurred!"
```

**Result in messages.yml:**
```yaml
welcome: "&8[&6Server&8]&r  Welcome to Awesome Server!"
error: "&8[&6Server&8]&r  &cAn error occurred!"
```

**Same-file references:**
```yaml
# config.yml
branding:
  name: "MyPlugin"
  version: "1.0.0"

messages:
  header: "{{config:branding.name}} v{{config:branding.version}}"
```

### Reference Resolution

References are resolved during configuration loading:

1. All configuration files are loaded
2. Configuration migrations run
3. Auto-updater adds new properties
4. Property references are parsed and replaced
5. Final configurations are cached

### Limitations

| Limitation | Reason |
|------------|--------|
| References resolve to strings | All YAML values are treated as strings during replacement |
| No circular reference detection | Creating circular references causes infinite loops |
| Single pass evaluation | References are not re-evaluated after initial resolution |
| Case-sensitive | Property paths and identifiers are case-sensitive |

### Best Practices

**DO:**
- Use references for truly shared values (prefix, server name)
- Keep references simple and readable
- Document referenced properties

**DON'T:**
- Over-use references (makes configs hard to read)
- Create circular references
- Reference properties that might not exist

**Good Example:**
```yaml
# config.yml
prefix: "&8[&6MyPlugin&8]&r "

# messages.yml
success: "{{config:prefix}} &aSuccess!"
error: "{{config:prefix}} &cError!"
```

**Bad Example:**
```yaml
# Too many references - hard to read
message: "{{config:colors.primary}}[{{config:text.prefix}}]{{config:colors.secondary}} {{config:text.welcome}}"
```

## ConfigurationFile Options

The `ConfigurationFile` class represents a single YAML configuration file.

### Constructors

```java
public ConfigurationFile(String path)
public ConfigurationFile(String path, String identifier)
public ConfigurationFile(String path, String identifier, boolean ignoreUpdater)
```

### Constructor Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | String | Yes | Relative path to YAML file in plugin data folder |
| `identifier` | String | No | Unique identifier for property references (defaults to filename without .yml) |
| `ignoreUpdater` | boolean | No | If true, auto-updater skips this file (defaults to false) |

### Examples

**Basic usage (path only):**
```java
new ConfigurationFile("config.yml")
// Identifier: "config"
// Auto-update: enabled
```

**Custom identifier:**
```java
new ConfigurationFile("features/homes.yml", "homes")
// Path: "features/homes.yml"
// Identifier: "homes" (instead of "features-homes")
// Auto-update: enabled
```

**Ignore auto-updater:**
```java
new ConfigurationFile("messages.yml", "messages", true)
// Path: "messages.yml"
// Identifier: "messages"
// Auto-update: disabled (user-customized file)
```

### Identifier Derivation

When identifier is not specified, it's derived from the path:

| Path | Derived Identifier |
|------|-------------------|
| `config.yml` | `config` |
| `homes.yml` | `homes` |
| `subdir/feature.yml` | `subdir-feature` |
| `data/settings.yml` | `data-settings` |

**Derivation Rules:**
1. Remove `.yml` extension
2. Replace `/` with `-`

### Auto-Updater Behavior

| ignoreUpdater | Behavior |
|---------------|----------|
| `false` (default) | File is auto-updated when new properties exist in JAR |
| `true` | File is loaded but never modified by auto-updater |

**When to ignore updater:**
- Message files that users heavily customize
- Files where preserving exact formatting is important
- Files that don't have defaults in the JAR

### Configuration Provider

Register configuration files via `TubingConfigurationProvider`:

```java
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public MyConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml"),
            new ConfigurationFile("homes.yml"),
            new ConfigurationFile("features/warps.yml", "warps"),
            new ConfigurationFile("messages.yml", "messages", true)
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

### File Discovery Process

When the container initializes, for each `ConfigurationFile`:

1. **Save from resources** - If file doesn't exist, copy from JAR
2. **Load configuration** - Load YAML from disk
3. **Run migrations** - Execute all ConfigMigrator instances
4. **Auto-update** - Add new properties from JAR defaults (unless ignoreUpdater)
5. **Parse references** - Replace {{identifier:path}} patterns
6. **Cache** - Store in ConfigurationLoader for injection

### Accessing FileConfiguration

```java
@IocBean
public class MyService {

    private final ConfigurationLoader configLoader;

    public MyService(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void doSomething() {
        // Get by identifier
        Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();
        FileConfiguration config = configs.get("config");
        FileConfiguration homes = configs.get("homes");

        // Access values
        String host = config.getString("database.host");
        int maxHomes = homes.getInt("max-homes", 5);
    }
}
```

## Best Practices

### Naming Conventions

**YAML Keys:**
```yaml
# Use kebab-case (lowercase with hyphens)
max-players: 100
database-host: "localhost"
enable-debug-mode: false
```

**Java Fields:**
```java
// Use camelCase
@ConfigProperty("max-players")
private int maxPlayers;

@ConfigProperty("database-host")
private String databaseHost;

@ConfigProperty("enable-debug-mode")
private boolean enableDebugMode;
```

### Default Values

**Always provide defaults for optional properties:**
```java
// Good
@ConfigProperty("max-homes")
private int maxHomes = 5;

// Bad (may cause issues if missing)
@ConfigProperty("max-homes")
private int maxHomes;  // 0 if missing!
```

### Required Properties

**Use sparingly, only for critical values:**
```java
// Good - essential property
@ConfigProperty(value = "database.password", required = true)
private String password;

// Bad - has sensible default, shouldn't be required
@ConfigProperty(value = "max-players", required = true)
private int maxPlayers;  // Better with default value
```

### Validation

**Validate after injection using @PostConstruct:**
```java
@IocBean
public class ServerConfig {
    @ConfigProperty("port")
    private int port = 25565;

    @PostConstruct
    public void validate() {
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException(
                "Port must be between 1024 and 65535, got: " + port
            );
        }
    }
}
```

### Organization

**Separate configuration from business logic:**
```java
// Good - dedicated config class
@IocBean
@ConfigProperties("homes")
public class HomeConfig {
    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    public int getMaxHomes() { return maxHomes; }
}

@IocBean
public class HomeService {
    private final HomeConfig config;

    public HomeService(HomeConfig config) {
        this.config = config;
    }
}
```

### Documentation

**Document your configuration properties:**
```java
public class DatabaseConfig {
    /**
     * Database host address.
     * Default: localhost
     */
    @ConfigProperty("host")
    private String host = "localhost";

    /**
     * Database port (1024-65535).
     * Default: 3306 (MySQL)
     */
    @ConfigProperty("port")
    private int port = 3306;
}
```

**Provide commented YAML defaults:**
```yaml
# homes.yml
homes:
  # Maximum number of homes a player can set
  max-homes: 5

  # Cooldown between home teleports (in seconds)
  cooldown-seconds: 60

  # Whether to allow cross-world home teleportation
  allow-cross-world: false
```

### File Structure

**Organize configuration by feature:**
```
resources/
├── config.yml          # Core plugin settings
├── database.yml        # Database configuration
├── features/
│   ├── homes.yml       # Homes feature settings
│   ├── warps.yml       # Warps feature settings
│   └── economy.yml     # Economy settings
└── messages.yml        # User-facing messages
```

### Transformers

**Keep transformers simple and focused:**
```java
// Good - single responsibility
public class ToUUID implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        return UUID.fromString(config);
    }
}

// Bad - doing too much
public class ToUUIDWithValidation implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        logger.info("Parsing: " + config);
        UUID uuid = UUID.fromString(config);
        if (uuid.version() != 4) {
            throw new IllegalArgumentException("Only v4 supported");
        }
        logger.info("Success");
        return uuid;
    }
}
```

### Type Safety

**Use specific types, avoid Object:**
```java
// Good - specific types
@ConfigProperty("worlds")
private List<String> worlds;

@ConfigProperty("rewards")
private Map<String, Integer> rewards;

// Bad - loses type safety
@ConfigProperty("worlds")
private Object worlds;
```

## See Also

- [Configuration Injection](../core/Configuration-Injection.md) - Injecting configuration values
- [Configuration Files](../core/Configuration-Files.md) - Managing multiple configuration files
- [Configuration Transformers](../core/Configuration-Transformers.md) - Custom type conversions
- [Configuration Objects](../core/Configuration-Objects.md) - Complex object mapping
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration examples
