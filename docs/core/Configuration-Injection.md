# Configuration Injection

Configuration Injection is Tubing's powerful system for automatically loading and injecting configuration values from YAML files directly into your beans. This eliminates manual configuration parsing and provides type-safe, declarative configuration management.

## What is Configuration Injection?

Configuration Injection allows you to:
- Inject YAML configuration values directly into bean fields
- Use type-safe properties with automatic type conversion
- Define default values and required properties
- Map complex YAML structures to Java objects
- Apply custom transformations to configuration values
- Group related properties with prefixes

**Without Configuration Injection:**
```java
@IocBean
public class HomeService {
    private final int maxHomes;
    private final int cooldownSeconds;

    public HomeService(FileConfiguration config) {
        this.maxHomes = config.getInt("homes.max-homes", 3);
        this.cooldownSeconds = config.getInt("homes.cooldown-seconds", 60);
    }
}
```

**With Configuration Injection:**
```java
@IocBean
@ConfigProperties("homes")
public class HomeService {
    @ConfigProperty("max-homes")
    private int maxHomes = 3;

    @ConfigProperty("cooldown-seconds")
    private int cooldownSeconds = 60;

    // No constructor needed - values automatically injected
}
```

## The @ConfigProperty Annotation

The `@ConfigProperty` annotation injects configuration values from your YAML files into bean fields or constructor parameters.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface ConfigProperty {
    String value();              // Property path in YAML
    boolean required() default false;  // Is this property required?
    String error() default "";   // Custom error message if required property is missing
}
```

### Basic Field Injection

The simplest way to inject configuration is using field annotations:

```java
@IocBean
public class MessagesService {
    @ConfigProperty("messages.prefix")
    private String prefix;

    @ConfigProperty("messages.enabled")
    private boolean enabled;

    @ConfigProperty("messages.broadcast-range")
    private int broadcastRange;

    public void sendMessage(Player player, String message) {
        if (!enabled) return;
        player.sendMessage(prefix + message);
    }
}
```

**config.yml:**
```yaml
messages:
  prefix: "&8[&aServer&8] &7"
  enabled: true
  broadcast-range: 100
```

**Key Points:**
- Fields are injected after construction
- Fields should be private (not public)
- Fields can be final or non-final
- Values are injected before the bean is used

### Constructor Parameter Injection

You can also inject configuration values via constructor parameters:

```java
@IocBean
public class HomeService {
    private final int maxHomes;
    private final PlayerRepository repository;

    public HomeService(
        PlayerRepository repository,
        @ConfigProperty("homes.max-homes") int maxHomes
    ) {
        this.repository = repository;
        this.maxHomes = maxHomes;
    }
}
```

**Benefits:**
- Configuration values available immediately in constructor
- Immutable fields (can be final)
- Explicit dependencies in constructor signature

**Limitations:**
- More verbose than field injection
- Mixes configuration with service dependencies

**Recommendation:** Use field injection for configuration, constructor injection for service dependencies.

### Setter Method Injection

Configuration can be injected via setter methods:

```java
@IocBean
public class CooldownService {
    private int cooldownDuration;

    @ConfigProperty("cooldown.duration")
    public void setCooldownDuration(int duration) {
        this.cooldownDuration = duration;
    }
}
```

**When to use:**
- When you need validation logic during injection
- When the property should trigger side effects
- When working with legacy code

## Property Paths and YAML Structure

Configuration property paths map directly to YAML structure using dot notation.

### Simple Properties

```java
@IocBean
public class ServerConfig {
    @ConfigProperty("server.name")
    private String serverName;

    @ConfigProperty("server.max-players")
    private int maxPlayers;
}
```

**config.yml:**
```yaml
server:
  name: "My Server"
  max-players: 100
```

### Nested Paths

```java
@IocBean
public class DatabaseConfig {
    @ConfigProperty("database.connection.host")
    private String host;

    @ConfigProperty("database.connection.port")
    private int port;

    @ConfigProperty("database.connection.database")
    private String database;
}
```

**config.yml:**
```yaml
database:
  connection:
    host: "localhost"
    port: 3306
    database: "minecraft"
```

### Root-Level Properties

```java
@IocBean
public class SimpleConfig {
    @ConfigProperty("debug")
    private boolean debug;

    @ConfigProperty("version")
    private String version;
}
```

**config.yml:**
```yaml
debug: false
version: "1.0.0"
```

## Default Values

Default values are set by initializing fields before injection. If the property is not found in the configuration, the default value is kept.

### Field Default Values

```java
@IocBean
public class HomeService {
    @ConfigProperty("homes.max-homes")
    private int maxHomes = 3;  // Default: 3

    @ConfigProperty("homes.enabled")
    private boolean enabled = true;  // Default: true

    @ConfigProperty("homes.cooldown")
    private int cooldown = 60;  // Default: 60
}
```

**Behavior:**
- If `homes.max-homes` exists in config, use that value
- If `homes.max-homes` is missing, keep default value of 3
- No error is thrown for missing properties with defaults

### Collection Defaults

```java
@IocBean
public class KitService {
    @ConfigProperty("kits.disabled-worlds")
    private List<String> disabledWorlds = Arrays.asList("world_nether", "world_the_end");

    @ConfigProperty("kits.allowed-types")
    private Set<String> allowedTypes = new HashSet<>(Arrays.asList("STARTER", "VIP"));
}
```

### Null as Default

```java
@IocBean
public class OptionalConfig {
    @ConfigProperty("optional.message")
    private String message = null;  // Explicitly null if not configured

    public void sendMessage(Player player) {
        if (message != null) {
            player.sendMessage(message);
        }
    }
}
```

## Type Conversion

Tubing automatically converts YAML values to Java types. The conversion is handled by the underlying YAML parser and Tubing's type system.

### Primitive Types

```java
@IocBean
public class PrimitiveConfig {
    @ConfigProperty("int-value")
    private int intValue;

    @ConfigProperty("long-value")
    private long longValue;

    @ConfigProperty("double-value")
    private double doubleValue;

    @ConfigProperty("float-value")
    private float floatValue;

    @ConfigProperty("boolean-value")
    private boolean booleanValue;
}
```

**config.yml:**
```yaml
int-value: 42
long-value: 9223372036854775807
double-value: 3.14159
float-value: 2.71
boolean-value: true
```

### String Type

```java
@IocBean
public class StringConfig {
    @ConfigProperty("message")
    private String message;

    @ConfigProperty("color-code")
    private String colorCode;
}
```

**config.yml:**
```yaml
message: "Welcome to the server!"
color-code: "&a"
```

**Note:** Strings support Minecraft color codes (`&a`, `&c`, etc.) if your message handler processes them.

### Lists

```java
@IocBean
public class ListConfig {
    @ConfigProperty("allowed-commands")
    private List<String> allowedCommands;

    @ConfigProperty("disabled-worlds")
    private List<String> disabledWorlds;

    @ConfigProperty("reward-amounts")
    private List<Integer> rewardAmounts;
}
```

**config.yml:**
```yaml
allowed-commands:
  - "home"
  - "spawn"
  - "tpa"

disabled-worlds:
  - "world_nether"
  - "world_the_end"

reward-amounts:
  - 100
  - 250
  - 500
```

### Sets

```java
@IocBean
public class SetConfig {
    @ConfigProperty("unique-items")
    private Set<String> uniqueItems;

    @ConfigProperty("admin-uuids")
    private Set<UUID> adminUuids;
}
```

**config.yml:**
```yaml
unique-items:
  - "DIAMOND"
  - "EMERALD"
  - "DIAMOND"  # Duplicate - will be deduplicated in Set

admin-uuids:
  - "069a79f4-44e9-4726-a5be-fca90e38aaf5"
  - "d8d5a923-7b1e-4b1e-8c5f-5a5b1e1e1e1e"
```

### Maps

```java
@IocBean
public class MapConfig {
    @ConfigProperty("world-names")
    private Map<String, String> worldNames;

    @ConfigProperty("price-list")
    private Map<String, Integer> priceList;
}
```

**config.yml:**
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

### Enums (with Transformer)

For enum types, use the `@ConfigTransformer` annotation with the `ToEnum` transformer:

```java
public enum GameMode {
    SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
}

@IocBean
public class ServerConfig {
    @ConfigProperty("default-gamemode")
    @ConfigTransformer(ToEnum.class)
    private GameMode defaultGamemode;
}
```

**config.yml:**
```yaml
default-gamemode: "SURVIVAL"
```

### Bukkit/Spigot Types

```java
@IocBean
public class MaterialConfig {
    @ConfigProperty("allowed-blocks")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> allowedBlocks;
}
```

**config.yml:**
```yaml
allowed-blocks:
  - "STONE"
  - "DIRT"
  - "GRASS_BLOCK"
```

## The @ConfigProperties Annotation

The `@ConfigProperties` annotation allows you to define a common prefix for all configuration properties in a class, reducing repetition.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ConfigProperties {
    String value();  // Common prefix for all properties
}
```

### Basic Usage

**Without @ConfigProperties:**
```java
@IocBean
public class HomeService {
    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds;

    @ConfigProperty("homes.enabled")
    private boolean enabled;

    @ConfigProperty("homes.allow-nether")
    private boolean allowNether;
}
```

**With @ConfigProperties:**
```java
@IocBean
@ConfigProperties("homes")
public class HomeService {
    @ConfigProperty("max-homes")
    private int maxHomes;

    @ConfigProperty("cooldown-seconds")
    private int cooldownSeconds;

    @ConfigProperty("enabled")
    private boolean enabled;

    @ConfigProperty("allow-nether")
    private boolean allowNether;
}
```

**config.yml (same for both):**
```yaml
homes:
  max-homes: 5
  cooldown-seconds: 120
  enabled: true
  allow-nether: false
```

### Multi-Level Prefixes

```java
@IocBean
@ConfigProperties("database.connection")
public class DatabaseConnectionConfig {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;

    @ConfigProperty("database")
    private String database;
}
```

**config.yml:**
```yaml
database:
  connection:
    host: "localhost"
    port: 3306
    username: "minecraft"
    password: "secret123"
    database: "server_data"
```

**Result:** The prefix `database.connection` is prepended to all `@ConfigProperty` values.

## Nested Property Paths

You can map nested YAML structures using either flat property paths or embedded objects.

### Flat Path Approach

```java
@IocBean
@ConfigProperties("shop")
public class ShopConfig {
    @ConfigProperty("gui.title")
    private String guiTitle;

    @ConfigProperty("gui.size")
    private int guiSize;

    @ConfigProperty("currency.name")
    private String currencyName;

    @ConfigProperty("currency.symbol")
    private String currencySymbol;
}
```

**config.yml:**
```yaml
shop:
  gui:
    title: "Server Shop"
    size: 54
  currency:
    name: "coins"
    symbol: "$"
```

### Embedded Object Approach

For more complex nested structures, use `@ConfigEmbeddedObject`:

```java
public class GuiSettings {
    @ConfigProperty("title")
    private String title;

    @ConfigProperty("size")
    private int size;

    // Getters...
}

public class CurrencySettings {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("symbol")
    private String symbol;

    // Getters...
}

@IocBean
@ConfigProperties("shop")
public class ShopConfig {
    @ConfigProperty("gui")
    @ConfigEmbeddedObject(GuiSettings.class)
    private GuiSettings gui;

    @ConfigProperty("currency")
    @ConfigEmbeddedObject(CurrencySettings.class)
    private CurrencySettings currency;
}
```

**Benefits:**
- Type-safe access to nested structures
- Reusable configuration objects
- Better organization for complex configs

## Complex Object Mapping

Tubing provides annotations for mapping complex YAML structures to Java objects.

### @ConfigEmbeddedObject

Maps a YAML section to a Java object.

#### Simple Embedded Object

```java
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;

    @ConfigProperty("database")
    private String database;

    // Getters and setters...
}

@IocBean
public class AppConfig {
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings database;
}
```

**config.yml:**
```yaml
database:
  host: "localhost"
  port: 3306
  database: "minecraft"
```

#### Nested Embedded Objects

```java
public class SmtpSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;
}

public class EmailSettings {
    @ConfigProperty("enabled")
    private boolean enabled;

    @ConfigProperty("from-address")
    private String fromAddress;

    @ConfigProperty("smtp")
    @ConfigEmbeddedObject(SmtpSettings.class)
    private SmtpSettings smtp;
}

@IocBean
public class NotificationConfig {
    @ConfigProperty("email")
    @ConfigEmbeddedObject(EmailSettings.class)
    private EmailSettings email;
}
```

**config.yml:**
```yaml
email:
  enabled: true
  from-address: "noreply@example.com"
  smtp:
    host: "smtp.gmail.com"
    port: 587
    username: "user@gmail.com"
    password: "secret"
```

### @ConfigObjectList

Maps a YAML list of objects to a Java List of objects.

#### Basic Object List

```java
public class ShopItem {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("material")
    private String material;

    @ConfigProperty("price")
    private int price;

    @ConfigProperty("description")
    private List<String> description;
}

@IocBean
public class ShopConfig {
    @ConfigProperty("items")
    @ConfigObjectList(ShopItem.class)
    private List<ShopItem> items;
}
```

**config.yml:**
```yaml
items:
  - name: "Diamond Sword"
    material: "DIAMOND_SWORD"
    price: 500
    description:
      - "A powerful weapon"
      - "Sharpness V"

  - name: "Iron Armor Set"
    material: "IRON_CHESTPLATE"
    price: 250
    description:
      - "Full protection"

  - name: "Golden Apple"
    material: "GOLDEN_APPLE"
    price: 100
    description:
      - "Restores health"
```

#### Object List with Nested Objects

```java
public class Reward {
    @ConfigProperty("item")
    private String item;

    @ConfigProperty("amount")
    private int amount;
}

public class Quest {
    @ConfigProperty("id")
    private String id;

    @ConfigProperty("name")
    private String name;

    @ConfigProperty("description")
    private String description;

    @ConfigProperty("rewards")
    @ConfigObjectList(Reward.class)
    private List<Reward> rewards;
}

@IocBean
public class QuestConfig {
    @ConfigProperty("quests")
    @ConfigObjectList(Quest.class)
    private List<Quest> quests;
}
```

**config.yml:**
```yaml
quests:
  - id: "quest_1"
    name: "Kill 10 Zombies"
    description: "Defeat 10 zombies"
    rewards:
      - item: "DIAMOND"
        amount: 5
      - item: "GOLD_INGOT"
        amount: 10

  - id: "quest_2"
    name: "Mine 50 Diamonds"
    description: "Collect 50 diamonds"
    rewards:
      - item: "DIAMOND_PICKAXE"
        amount: 1
```

## Configuration Transformers

Configuration transformers allow you to apply custom transformations to configuration values before injection.

### Built-in Transformers

#### ToEnum Transformer

Converts strings to enum values:

```java
public enum Difficulty {
    EASY, NORMAL, HARD, EXTREME
}

@IocBean
public class GameConfig {
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;
}
```

**config.yml:**
```yaml
difficulty: "HARD"
```

#### ToLowerCase Transformer

Converts strings to lowercase:

```java
@IocBean
public class CommandConfig {
    @ConfigProperty("allowed-commands")
    @ConfigTransformer(ToLowerCase.class)
    private List<String> allowedCommands;
}
```

**config.yml:**
```yaml
allowed-commands:
  - "HOME"
  - "SPAWN"
  - "TPA"
```

**Result:** List contains `["home", "spawn", "tpa"]`

#### ToMaterials Transformer (Bukkit)

Converts material names to Bukkit Material enums with wildcard support:

```java
@IocBean
public class BlockConfig {
    @ConfigProperty("allowed-blocks")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> allowedBlocks;
}
```

**config.yml:**
```yaml
allowed-blocks:
  - "STONE"
  - "*_WOOD"  # Matches all wood types
  - "DIAMOND_*"  # Matches all diamond items
```

### Custom Transformers

Create custom transformers by implementing `IConfigTransformer`:

```java
public interface IConfigTransformer<T, S> {
    T mapConfig(S config);
}
```

#### Example: UUID Transformer

```java
public class ToUUID implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        return UUID.fromString(config);
    }
}

@IocBean
public class PlayerConfig {
    @ConfigProperty("admin-uuid")
    @ConfigTransformer(ToUUID.class)
    private UUID adminUuid;
}
```

**config.yml:**
```yaml
admin-uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5"
```

#### Example: Duration Transformer

```java
public class ToDuration implements IConfigTransformer<Duration, String> {
    @Override
    public Duration mapConfig(String config) {
        // Parse "5m", "2h", "30s" format
        char unit = config.charAt(config.length() - 1);
        int value = Integer.parseInt(config.substring(0, config.length() - 1));

        switch (unit) {
            case 's': return Duration.ofSeconds(value);
            case 'm': return Duration.ofMinutes(value);
            case 'h': return Duration.ofHours(value);
            case 'd': return Duration.ofDays(value);
            default: throw new IllegalArgumentException("Invalid duration: " + config);
        }
    }
}

@IocBean
public class CooldownConfig {
    @ConfigProperty("command-cooldown")
    @ConfigTransformer(ToDuration.class)
    private Duration commandCooldown;
}
```

**config.yml:**
```yaml
command-cooldown: "5m"
```

#### Example: Color Code Transformer

```java
public class ToColoredString implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        return ChatColor.translateAlternateColorCodes('&', config);
    }
}

@IocBean
public class MessageConfig {
    @ConfigProperty("welcome-message")
    @ConfigTransformer(ToColoredString.class)
    private String welcomeMessage;
}
```

**config.yml:**
```yaml
welcome-message: "&aWelcome &e{player} &ato the server!"
```

#### Chaining Transformers

Multiple transformers can be chained:

```java
@IocBean
public class CommandConfig {
    @ConfigProperty("allowed-commands")
    @ConfigTransformer({ToLowerCase.class, ValidateCommands.class})
    private List<String> allowedCommands;
}
```

Transformers are applied in order: `ToLowerCase` → `ValidateCommands`

### Transformer with Dependencies

Transformers can have constructor dependencies, and configuration properties can be injected:

```java
public class ToLocation implements IConfigTransformer<Location, String> {
    @ConfigProperty("default-world")
    private String defaultWorld;

    private final Server server;

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

## Required Properties

Mark properties as required to enforce their presence in configuration files.

### Basic Required Property

```java
@IocBean
public class DatabaseConfig {
    @ConfigProperty(value = "database.host", required = true)
    private String host;

    @ConfigProperty(value = "database.port", required = true)
    private int port;

    @ConfigProperty(value = "database.password", required = true)
    private String password;
}
```

**Behavior:**
- If `database.host` is missing, plugin fails to load
- Error message: "Configuration not found for database.host"

### Custom Error Messages

```java
@IocBean
public class LicenseConfig {
    @ConfigProperty(
        value = "license.key",
        required = true,
        error = "License key is required! Please add 'license.key' to your config.yml"
    )
    private String licenseKey;

    @ConfigProperty(
        value = "license.email",
        required = true,
        error = "License email is required! Please add 'license.email' to your config.yml"
    )
    private String licenseEmail;
}
```

**Behavior:**
- If `license.key` is missing, error message shows custom error
- Plugin startup fails immediately with clear message

### When to Use Required Properties

**Use `required = true` for:**
- Database credentials
- API keys and tokens
- Essential configuration that breaks functionality if missing
- License or authentication information

**Don't use `required = true` for:**
- Properties with sensible defaults
- Optional features
- UI messages (use defaults)
- Toggleable features

## Configuration Validation

Tubing doesn't provide built-in validation annotations, but you can validate configuration after injection.

### Constructor Validation

```java
@IocBean
public class ServerConfig {
    @ConfigProperty("server.max-players")
    private int maxPlayers;

    @ConfigProperty("server.port")
    private int port;

    public ServerConfig() {
        // Constructor called after configuration injection
    }

    @PostConstruct
    public void validate() {
        if (maxPlayers < 1 || maxPlayers > 1000) {
            throw new ConfigurationException("max-players must be between 1 and 1000");
        }

        if (port < 1024 || port > 65535) {
            throw new ConfigurationException("port must be between 1024 and 65535");
        }
    }
}
```

### Validation Service

Create a dedicated validation service:

```java
public class ConfigValidator {
    public static void validatePort(int port) {
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException("Invalid port: " + port);
        }
    }

    public static void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ConfigurationException(fieldName + " must be positive");
        }
    }

    public static void validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new ConfigurationException(
                fieldName + " must be between " + min + " and " + max
            );
        }
    }
}

@IocBean
public class GameConfig {
    @ConfigProperty("max-players")
    private int maxPlayers;

    @ConfigProperty("min-players")
    private int minPlayers;

    @PostConstruct
    public void validate() {
        ConfigValidator.validateRange(maxPlayers, 1, 100, "max-players");
        ConfigValidator.validateRange(minPlayers, 1, maxPlayers, "min-players");
    }
}
```

### Transformer Validation

Validate during transformation:

```java
public class ToValidatedPort implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer port) {
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException(
                "Port must be between 1024 and 65535, got: " + port
            );
        }
        return port;
    }
}

@IocBean
public class ServerConfig {
    @ConfigProperty("server.port")
    @ConfigTransformer(ToValidatedPort.class)
    private int port;
}
```

## Best Practices

### 1. Use Field Injection for Configuration

```java
// Good - clean and simple
@IocBean
@ConfigProperties("homes")
public class HomeService {
    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    private final PlayerRepository repository;

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - mixing config and services in constructor
@IocBean
public class HomeService {
    private final int maxHomes;
    private final PlayerRepository repository;

    public HomeService(
        PlayerRepository repository,
        @ConfigProperty("homes.max-homes") int maxHomes
    ) {
        this.repository = repository;
        this.maxHomes = maxHomes;
    }
}
```

**Why:** Field injection keeps constructor focused on service dependencies.

### 2. Group Related Properties with @ConfigProperties

```java
// Good - uses prefix
@IocBean
@ConfigProperties("shop")
public class ShopConfig {
    @ConfigProperty("enabled")
    private boolean enabled;

    @ConfigProperty("currency-name")
    private String currencyName;

    @ConfigProperty("tax-rate")
    private double taxRate;
}

// Bad - repetitive paths
@IocBean
public class ShopConfig {
    @ConfigProperty("shop.enabled")
    private boolean enabled;

    @ConfigProperty("shop.currency-name")
    private String currencyName;

    @ConfigProperty("shop.tax-rate")
    private double taxRate;
}
```

### 3. Provide Sensible Defaults

```java
// Good - has defaults
@IocBean
public class MessageConfig {
    @ConfigProperty("prefix")
    private String prefix = "&8[&aServer&8]&r ";

    @ConfigProperty("error-color")
    private String errorColor = "&c";

    @ConfigProperty("success-color")
    private String successColor = "&a";
}

// Bad - no defaults, may cause NPEs
@IocBean
public class MessageConfig {
    @ConfigProperty("prefix")
    private String prefix;  // null if not configured!

    @ConfigProperty("error-color")
    private String errorColor;  // null if not configured!
}
```

### 4. Use Required Only When Necessary

```java
// Good - required only for critical values
@IocBean
public class DatabaseConfig {
    @ConfigProperty(value = "host", required = true)
    private String host;

    @ConfigProperty("port")
    private int port = 3306;  // Has default

    @ConfigProperty("pool-size")
    private int poolSize = 10;  // Has default
}

// Bad - everything required, no flexibility
@IocBean
public class DatabaseConfig {
    @ConfigProperty(value = "host", required = true)
    private String host;

    @ConfigProperty(value = "port", required = true)
    private int port;

    @ConfigProperty(value = "pool-size", required = true)
    private int poolSize;
}
```

### 5. Use Embedded Objects for Complex Structures

```java
// Good - structured with embedded objects
public class DatabaseConnection {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;
}

public class DatabasePool {
    @ConfigProperty("min-size")
    private int minSize = 5;

    @ConfigProperty("max-size")
    private int maxSize = 20;
}

@IocBean
@ConfigProperties("database")
public class DatabaseConfig {
    @ConfigProperty("connection")
    @ConfigEmbeddedObject(DatabaseConnection.class)
    private DatabaseConnection connection;

    @ConfigProperty("pool")
    @ConfigEmbeddedObject(DatabasePool.class)
    private DatabasePool pool;
}

// Bad - flat structure for complex config
@IocBean
public class DatabaseConfig {
    @ConfigProperty("database.connection.host")
    private String host;

    @ConfigProperty("database.connection.port")
    private int port;

    @ConfigProperty("database.pool.min-size")
    private int minPoolSize;

    @ConfigProperty("database.pool.max-size")
    private int maxPoolSize;
}
```

### 6. Validate Configuration

```java
// Good - validates after injection
@IocBean
public class RangeConfig {
    @ConfigProperty("min-value")
    private int minValue = 0;

    @ConfigProperty("max-value")
    private int maxValue = 100;

    @PostConstruct
    public void validate() {
        if (minValue >= maxValue) {
            throw new ConfigurationException("min-value must be less than max-value");
        }
    }
}
```

### 7. Document Your Configuration

```java
@IocBean
@ConfigProperties("homes")
public class HomeConfig {
    // Maximum number of homes a player can set (default: 5)
    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    // Cooldown in seconds between home teleports (default: 60)
    @ConfigProperty("cooldown-seconds")
    private int cooldownSeconds = 60;

    // Whether homes are enabled (default: true)
    @ConfigProperty("enabled")
    private boolean enabled = true;
}
```

### 8. Keep Configuration Classes Separate

```java
// Good - dedicated config class
@IocBean
@ConfigProperties("homes")
public class HomeConfig {
    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    @ConfigProperty("cooldown-seconds")
    private int cooldownSeconds = 60;

    public int getMaxHomes() { return maxHomes; }
    public int getCooldownSeconds() { return cooldownSeconds; }
}

@IocBean
public class HomeService {
    private final HomeConfig config;
    private final HomeRepository repository;

    public HomeService(HomeConfig config, HomeRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public boolean canCreateHome(Player player) {
        return repository.countHomes(player) < config.getMaxHomes();
    }
}

// Acceptable - small services with minimal config
@IocBean
public class SimpleMessageService {
    @ConfigProperty("messages.prefix")
    private String prefix = "&8[&aServer&8]&r ";

    public void send(Player player, String message) {
        player.sendMessage(prefix + message);
    }
}
```

## Common Patterns

### Pattern: Configuration DTO

```java
@IocBean
@ConfigProperties("database")
public class DatabaseConfig {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;

    @ConfigProperty("database")
    private String database;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;

    // Getters for external access
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}

@IocBean
public class DatabaseService {
    private final DatabaseConfig config;

    public DatabaseService(DatabaseConfig config) {
        this.config = config;
    }

    public Connection connect() {
        return DriverManager.getConnection(
            "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase(),
            config.getUsername(),
            config.getPassword()
        );
    }
}
```

### Pattern: Feature Toggle

```java
@IocBean
@ConfigProperties("features")
public class FeatureConfig {
    @ConfigProperty("homes.enabled")
    private boolean homesEnabled = true;

    @ConfigProperty("warps.enabled")
    private boolean warpsEnabled = true;

    @ConfigProperty("economy.enabled")
    private boolean economyEnabled = false;

    public boolean isHomesEnabled() { return homesEnabled; }
    public boolean isWarpsEnabled() { return warpsEnabled; }
    public boolean isEconomyEnabled() { return economyEnabled; }
}

@IocBean
public class HomeService {
    private final FeatureConfig features;

    public HomeService(FeatureConfig features) {
        this.features = features;
    }

    public void teleportHome(Player player, String homeName) {
        if (!features.isHomesEnabled()) {
            player.sendMessage("Homes are currently disabled");
            return;
        }
        // Teleport logic...
    }
}
```

### Pattern: List Configuration

```java
@IocBean
@ConfigProperties("security")
public class SecurityConfig {
    @ConfigProperty("blocked-commands")
    private List<String> blockedCommands = Arrays.asList("op", "deop", "stop");

    @ConfigProperty("allowed-ips")
    private Set<String> allowedIps = new HashSet<>();

    @ConfigProperty("admin-uuids")
    private List<String> adminUuids = new ArrayList<>();

    public boolean isCommandBlocked(String command) {
        return blockedCommands.contains(command.toLowerCase());
    }

    public boolean isIpAllowed(String ip) {
        return allowedIps.isEmpty() || allowedIps.contains(ip);
    }
}
```

### Pattern: Typed Configuration Lists

```java
public class Kit {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("items")
    private List<String> items;

    @ConfigProperty("cooldown")
    private int cooldown;

    @ConfigProperty("permission")
    private String permission;

    // Getters...
}

@IocBean
public class KitManager {
    @ConfigProperty("kits")
    @ConfigObjectList(Kit.class)
    private List<Kit> kits;

    public Kit getKit(String name) {
        return kits.stream()
            .filter(k -> k.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
```

## Integration with Other Features

### Configuration in Bean Providers

```java
@IocBean
public class DatabaseProvider {
    @IocBeanProvider
    public Database provideDatabase(
        @ConfigProperty("database.host") String host,
        @ConfigProperty("database.port") int port,
        @ConfigProperty("database.name") String name
    ) {
        return new Database(host, port, name);
    }
}
```

### Configuration with Conditional Beans

```java
@IocBean(conditionalOnProperty = "features.economy.enabled")
public class EconomyService {
    @ConfigProperty("features.economy.starting-balance")
    private double startingBalance = 1000.0;

    @ConfigProperty("features.economy.currency-name")
    private String currencyName = "coins";
}
```

## Next Steps

Now that you understand configuration injection:

- Learn about [Bean Lifecycle](Bean-Lifecycle.md) for configuration timing
- Explore [Bean Providers](Bean-Providers.md) for factory-based configuration
- Read [Dependency Injection](Dependency-Injection.md) for constructor patterns
- Check [Project Structure](../getting-started/Project-Structure.md) for config organization

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration examples
- [Migration Guide](../getting-started/Migration-Guide.md) - Migrating from manual config loading
- [IoC Container](IoC-Container.md) - Container internals
