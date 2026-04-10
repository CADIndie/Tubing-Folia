# Configuration Objects

Configuration Objects allow you to map complex YAML structures directly to Java objects (POJOs), providing a clean, type-safe way to work with nested configuration sections and lists of objects. This eliminates manual parsing and provides better code organization for complex configurations.

## Overview

While `@ConfigProperty` works well for simple values, complex configurations benefit from structured objects:

- **Nested configuration sections** - Map YAML sections to dedicated DTOs
- **List of objects** - Parse YAML lists into typed Java object lists
- **Type safety** - Compile-time checking for configuration structure
- **Code organization** - Group related configuration into dedicated classes
- **Reusability** - Share configuration objects across multiple beans

Tubing provides two annotations for mapping YAML to objects:
- `@ConfigEmbeddedObject` - Maps a YAML section to a single object
- `@ConfigObjectList` - Maps a YAML list to a List of objects

## @ConfigEmbeddedObject

The `@ConfigEmbeddedObject` annotation maps a YAML configuration section to a Java object. This is ideal for grouping related configuration properties into a dedicated class.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigEmbeddedObject {
    Class value();  // The class to instantiate and populate
}
```

### Basic Embedded Object

Map a simple configuration section to a POJO:

```java
// Configuration DTO
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;

    @ConfigProperty("database")
    private String database;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}

// Using the embedded object
@IocBean
public class DatabaseService {
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings settings;

    public void connect() {
        String url = "jdbc:mysql://" + settings.getHost() + ":"
                   + settings.getPort() + "/" + settings.getDatabase();
        // Connect using settings...
    }
}
```

**config.yml:**
```yaml
database:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: "secret123"
```

**How it works:**
1. Tubing reads the `database` section from YAML
2. Creates an instance of `DatabaseSettings`
3. Injects each property using `@ConfigProperty` annotations
4. Assigns the populated object to the `settings` field

### Nested Embedded Objects

Embedded objects can contain other embedded objects for deeply nested structures:

```java
// SMTP settings
public class SmtpSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;

    @ConfigProperty("use-tls")
    private boolean useTls = true;

    // Getters...
}

// Email settings containing SMTP settings
public class EmailSettings {
    @ConfigProperty("enabled")
    private boolean enabled;

    @ConfigProperty("from-address")
    private String fromAddress;

    @ConfigProperty("from-name")
    private String fromName;

    @ConfigProperty("smtp")
    @ConfigEmbeddedObject(SmtpSettings.class)
    private SmtpSettings smtp;

    // Getters...
}

// Root configuration
@IocBean
public class NotificationConfig {
    @ConfigProperty("email")
    @ConfigEmbeddedObject(EmailSettings.class)
    private EmailSettings email;

    public void sendEmail(String to, String subject, String body) {
        if (!email.isEnabled()) return;

        SmtpSettings smtp = email.getSmtp();
        // Use smtp settings to send email...
    }
}
```

**config.yml:**
```yaml
email:
  enabled: true
  from-address: "noreply@example.com"
  from-name: "Server Notifications"
  smtp:
    host: "smtp.gmail.com"
    port: 587
    username: "user@gmail.com"
    password: "app-password"
    use-tls: true
```

### Using with @ConfigProperties

Combine `@ConfigProperties` with embedded objects to reduce repetition:

```java
public class ConnectionSettings {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;

    @ConfigProperty("timeout")
    private int timeout = 5000;

    // Getters...
}

public class PoolSettings {
    @ConfigProperty("min-size")
    private int minSize = 5;

    @ConfigProperty("max-size")
    private int maxSize = 20;

    @ConfigProperty("idle-timeout")
    private int idleTimeout = 600000;

    // Getters...
}

@IocBean
@ConfigProperties("database")
public class DatabaseConfig {
    @ConfigProperty("connection")
    @ConfigEmbeddedObject(ConnectionSettings.class)
    private ConnectionSettings connection;

    @ConfigProperty("pool")
    @ConfigEmbeddedObject(PoolSettings.class)
    private PoolSettings pool;

    // Getters...
}
```

**config.yml:**
```yaml
database:
  connection:
    host: "db.example.com"
    port: 3306
    timeout: 10000
  pool:
    min-size: 10
    max-size: 50
    idle-timeout: 300000
```

The `database` prefix is applied to all properties, so `connection` becomes `database.connection`.

### Constructor Parameter Injection

You can use embedded objects in constructor parameters:

```java
@IocBean
public class DatabaseService {
    private final DatabaseSettings settings;

    public DatabaseService(
        @ConfigProperty("database")
        @ConfigEmbeddedObject(DatabaseSettings.class)
        DatabaseSettings settings
    ) {
        this.settings = settings;
    }
}
```

This makes the configuration dependency explicit and allows for immutable fields.

## @ConfigObjectList

The `@ConfigObjectList` annotation maps a YAML list of objects to a Java `List<T>`. Each list item is converted to an instance of the specified class.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigObjectList {
    Class value();  // The class to instantiate for each list item
}
```

### Basic Object List

Map a list of items to Java objects:

```java
// Item DTO
public class ShopItem {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("material")
    private String material;

    @ConfigProperty("price")
    private int price;

    @ConfigProperty("description")
    private List<String> description;

    // Getters...
    public String getName() { return name; }
    public String getMaterial() { return material; }
    public int getPrice() { return price; }
    public List<String> getDescription() { return description; }
}

// Using the object list
@IocBean
public class ShopConfig {
    @ConfigProperty("items")
    @ConfigObjectList(ShopItem.class)
    private List<ShopItem> items;

    public ShopItem findItem(String name) {
        return items.stream()
            .filter(item -> item.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public List<ShopItem> getAllItems() {
        return items;
    }
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
      - "Unbreaking III"

  - name: "Iron Armor Set"
    material: "IRON_CHESTPLATE"
    price: 250
    description:
      - "Full protection set"
      - "Protection IV"

  - name: "Golden Apple"
    material: "GOLDEN_APPLE"
    price: 100
    description:
      - "Restores health quickly"
```

**How it works:**
1. Tubing reads the `items` list from YAML
2. For each item in the list, creates a `ShopItem` instance
3. Injects properties for each item using `@ConfigProperty`
4. Collects all items into a `List<ShopItem>`

### Object List with Nested Objects

List items can contain embedded objects:

```java
// Reward DTO
public class Reward {
    @ConfigProperty("item")
    private String item;

    @ConfigProperty("amount")
    private int amount;

    @ConfigProperty("name")
    private String name;

    // Getters...
}

// Quest DTO with nested rewards
public class Quest {
    @ConfigProperty("id")
    private String id;

    @ConfigProperty("name")
    private String name;

    @ConfigProperty("description")
    private String description;

    @ConfigProperty("required-kills")
    private int requiredKills = 0;

    @ConfigProperty("required-blocks")
    private int requiredBlocks = 0;

    @ConfigProperty("rewards")
    @ConfigObjectList(Reward.class)
    private List<Reward> rewards;

    // Getters...
}

// Quest manager
@IocBean
public class QuestManager {
    @ConfigProperty("quests")
    @ConfigObjectList(Quest.class)
    private List<Quest> quests;

    public Quest getQuest(String id) {
        return quests.stream()
            .filter(q -> q.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public List<Quest> getAllQuests() {
        return quests;
    }
}
```

**config.yml:**
```yaml
quests:
  - id: "zombie_slayer"
    name: "Zombie Slayer"
    description: "Defeat 50 zombies"
    required-kills: 50
    rewards:
      - item: "DIAMOND"
        amount: 5
        name: "Quest Diamonds"
      - item: "GOLD_INGOT"
        amount: 10
        name: "Quest Gold"

  - id: "miner"
    name: "Expert Miner"
    description: "Mine 100 diamond ore"
    required-blocks: 100
    rewards:
      - item: "DIAMOND_PICKAXE"
        amount: 1
        name: "Enchanted Pickaxe"
```

### Object List with Default Values

List items support default values like regular fields:

```java
public class Kit {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("items")
    private List<String> items;

    @ConfigProperty("cooldown")
    private int cooldown = 3600;  // Default: 1 hour

    @ConfigProperty("permission")
    private String permission = "";  // Default: no permission required

    @ConfigProperty("enabled")
    private boolean enabled = true;  // Default: enabled

    // Getters...
}

@IocBean
public class KitManager {
    @ConfigProperty("kits")
    @ConfigObjectList(Kit.class)
    private List<Kit> kits;
}
```

**config.yml:**
```yaml
kits:
  - name: "starter"
    items:
      - "WOODEN_SWORD"
      - "BREAD:10"
    # cooldown, permission, and enabled use defaults

  - name: "vip"
    items:
      - "DIAMOND_SWORD"
      - "GOLDEN_APPLE:5"
    cooldown: 7200
    permission: "kits.vip"
    # enabled uses default (true)

  - name: "maintenance"
    items:
      - "STICK"
    enabled: false
    # cooldown and permission use defaults
```

### Empty Lists

If the configuration property is missing, the field keeps its default value:

```java
@IocBean
public class RewardConfig {
    @ConfigProperty("bonus-rewards")
    @ConfigObjectList(Reward.class)
    private List<Reward> bonusRewards = new ArrayList<>();  // Default: empty list

    public boolean hasBonusRewards() {
        return !bonusRewards.isEmpty();
    }
}
```

If `bonus-rewards` is not in the config, `bonusRewards` remains an empty list.

## Field Mapping and Naming Conventions

### YAML Key to Java Field Mapping

Tubing maps YAML keys to Java fields using the `@ConfigProperty` value:

```java
public class ServerSettings {
    @ConfigProperty("server-name")      // YAML: server-name
    private String serverName;

    @ConfigProperty("max-players")      // YAML: max-players
    private int maxPlayers;

    @ConfigProperty("enable-pvp")       // YAML: enable-pvp
    private boolean enablePvp;
}
```

**config.yml:**
```yaml
server-name: "My Server"
max-players: 100
enable-pvp: true
```

### Naming Conventions

**YAML (kebab-case):**
```yaml
database-host: "localhost"
max-connection-pool: 20
use-ssl-encryption: true
```

**Java (camelCase):**
```java
@ConfigProperty("database-host")
private String databaseHost;

@ConfigProperty("max-connection-pool")
private int maxConnectionPool;

@ConfigProperty("use-ssl-encryption")
private boolean useSslEncryption;
```

**Convention:**
- YAML keys: Use `kebab-case` (lowercase with hyphens)
- Java fields: Use `camelCase`
- `@ConfigProperty` value: Must match YAML key exactly

### Case Sensitivity

YAML keys are case-sensitive:

```yaml
# These are different keys
server-name: "Server 1"
Server-Name: "Server 2"
SERVER-NAME: "Server 3"
```

```java
@ConfigProperty("server-name")  // Matches first key only
private String serverName;
```

## Required vs Optional Fields

### Optional Fields (Default Behavior)

By default, all configuration properties are optional:

```java
public class EmailSettings {
    @ConfigProperty("from-address")
    private String fromAddress = "noreply@example.com";  // Has default

    @ConfigProperty("reply-to")
    private String replyTo;  // null if not configured
}
```

If a property is missing:
- Fields with default values keep their defaults
- Fields without defaults remain `null`
- No error is thrown

### Required Fields

Mark critical properties as required:

```java
public class DatabaseSettings {
    @ConfigProperty(value = "host", required = true)
    private String host;

    @ConfigProperty(value = "database", required = true)
    private String database;

    @ConfigProperty(value = "username", required = true)
    private String username;

    @ConfigProperty(value = "password", required = true)
    private String password;

    @ConfigProperty("port")
    private int port = 3306;  // Optional with default
}
```

If a required property is missing, the plugin fails to start with an error:
```
Configuration not found for database.host
```

### Custom Error Messages

Provide helpful error messages for required properties:

```java
public class LicenseSettings {
    @ConfigProperty(
        value = "license-key",
        required = true,
        error = "License key is required! Please add 'license-key' to your config.yml. Get your key at https://example.com"
    )
    private String licenseKey;
}
```

### Required Fields in Lists

Individual properties within list items can be required:

```java
public class ShopItem {
    @ConfigProperty(value = "name", required = true)
    private String name;

    @ConfigProperty(value = "price", required = true)
    private int price;

    @ConfigProperty("description")
    private List<String> description = Arrays.asList("No description");
}
```

If any item is missing a required property, the plugin fails to start.

## Type Conversion

Tubing automatically converts YAML values to Java types for object properties.

### Supported Types

**Primitives:**
```java
public class TypeExamples {
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

**Strings:**
```java
@ConfigProperty("message")
private String message;

@ConfigProperty("color-code")
private String colorCode;
```

**Collections:**
```java
@ConfigProperty("string-list")
private List<String> stringList;

@ConfigProperty("integer-list")
private List<Integer> integerList;

@ConfigProperty("unique-items")
private Set<String> uniqueItems;

@ConfigProperty("key-value-map")
private Map<String, String> keyValueMap;
```

### Using Transformers with Objects

Apply transformers to object properties:

```java
public enum Difficulty {
    EASY, NORMAL, HARD, EXTREME
}

public class GameSettings {
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;

    @ConfigProperty("allowed-blocks")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> allowedBlocks;

    @ConfigProperty("duration")
    @ConfigTransformer(ToDuration.class)
    private Duration gameDuration;
}

@IocBean
public class GameConfig {
    @ConfigProperty("settings")
    @ConfigEmbeddedObject(GameSettings.class)
    private GameSettings settings;
}
```

**config.yml:**
```yaml
settings:
  difficulty: "HARD"
  allowed-blocks:
    - "STONE"
    - "*_WOOD"
  duration: "30m"
```

### Nested Lists

Objects can contain lists of objects:

```java
public class Permission {
    @ConfigProperty("node")
    private String node;

    @ConfigProperty("value")
    private boolean value = true;
}

public class Rank {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("permissions")
    @ConfigObjectList(Permission.class)
    private List<Permission> permissions;
}

@IocBean
public class PermissionConfig {
    @ConfigProperty("ranks")
    @ConfigObjectList(Rank.class)
    private List<Rank> ranks;
}
```

**config.yml:**
```yaml
ranks:
  - name: "member"
    permissions:
      - node: "chat.use"
        value: true
      - node: "home.set"
        value: true

  - name: "vip"
    permissions:
      - node: "chat.use"
      - node: "chat.color"
      - node: "home.set"
```

## Best Practices for Configuration DTOs

### 1. Create Dedicated DTO Classes

Keep configuration objects separate from business logic:

```java
// Good - dedicated configuration DTO
public class ServerSettings {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("max-players")
    private int maxPlayers;

    // Only getters, no business logic
    public String getName() { return name; }
    public int getMaxPlayers() { return maxPlayers; }
}

@IocBean
public class ServerService {
    @ConfigProperty("server")
    @ConfigEmbeddedObject(ServerSettings.class)
    private ServerSettings settings;

    // Business logic here
    public boolean isServerFull(int currentPlayers) {
        return currentPlayers >= settings.getMaxPlayers();
    }
}

// Bad - mixing config and business logic
public class ServerSettings {
    @ConfigProperty("max-players")
    private int maxPlayers;

    // Business logic in DTO - avoid this
    public boolean isServerFull(int currentPlayers) {
        return currentPlayers >= maxPlayers;
    }
}
```

### 2. Provide Default Values

Always provide sensible defaults for optional properties:

```java
// Good - has defaults
public class GuiSettings {
    @ConfigProperty("title")
    private String title = "Menu";

    @ConfigProperty("size")
    private int size = 27;

    @ConfigProperty("update-interval")
    private int updateInterval = 20;
}

// Bad - no defaults, prone to NPE
public class GuiSettings {
    @ConfigProperty("title")
    private String title;  // null if not configured!

    @ConfigProperty("size")
    private int size;  // 0 if not configured!
}
```

### 3. Use Descriptive Class Names

Name configuration classes clearly:

```java
// Good - clear purpose
public class DatabaseConnectionSettings { }
public class EmailSmtpSettings { }
public class ShopItem { }

// Bad - vague names
public class Config { }
public class Settings { }
public class Data { }
```

### 4. Keep DTOs Simple

Configuration DTOs should be simple data containers:

```java
// Good - simple DTO
public class MessageSettings {
    @ConfigProperty("prefix")
    private String prefix;

    @ConfigProperty("error-color")
    private String errorColor;

    @ConfigProperty("success-color")
    private String successColor;

    public String getPrefix() { return prefix; }
    public String getErrorColor() { return errorColor; }
    public String getSuccessColor() { return successColor; }
}

// Bad - complex logic in DTO
public class MessageSettings {
    @ConfigProperty("prefix")
    private String prefix;

    private final Map<String, String> cache = new HashMap<>();

    public String format(String message, String color) {
        return cache.computeIfAbsent(message,
            m -> ChatColor.translateAlternateColorCodes('&', prefix + color + m));
    }
}
```

### 5. Use Immutable DTOs When Possible

Consider making DTOs immutable using constructor injection:

```java
// Immutable DTO (requires all-args constructor or builder)
public class ServerSettings {
    private final String name;
    private final int maxPlayers;
    private final boolean pvpEnabled;

    public ServerSettings(String name, int maxPlayers, boolean pvpEnabled) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.pvpEnabled = pvpEnabled;
    }

    public String getName() { return name; }
    public int getMaxPlayers() { return maxPlayers; }
    public boolean isPvpEnabled() { return pvpEnabled; }
}
```

**Note:** Tubing requires a no-args constructor for automatic instantiation. For immutable DTOs, you'll need to create them manually via bean providers.

### 6. Document Your DTOs

Add comments explaining the purpose and constraints:

```java
public class DatabaseSettings {
    /**
     * Database host address.
     * Default: localhost
     */
    @ConfigProperty("host")
    private String host = "localhost";

    /**
     * Database port.
     * Must be between 1024 and 65535.
     * Default: 3306 (MySQL default)
     */
    @ConfigProperty("port")
    private int port = 3306;

    /**
     * Maximum number of connections in the pool.
     * Higher values use more memory but support more concurrent operations.
     * Default: 10
     */
    @ConfigProperty("max-connections")
    private int maxConnections = 10;
}
```

### 7. Validate Configuration Objects

Use `@PostConstruct` to validate configuration after injection:

```java
public class PoolSettings {
    @ConfigProperty("min-size")
    private int minSize = 5;

    @ConfigProperty("max-size")
    private int maxSize = 20;

    @PostConstruct
    public void validate() {
        if (minSize <= 0) {
            throw new ConfigurationException("min-size must be positive");
        }
        if (maxSize < minSize) {
            throw new ConfigurationException("max-size must be >= min-size");
        }
    }

    // Getters...
}
```

### 8. Group Related Configuration

Organize complex configurations hierarchically:

```java
// Bad - flat structure
@IocBean
public class AppConfig {
    @ConfigProperty("db-host")
    private String dbHost;

    @ConfigProperty("db-port")
    private int dbPort;

    @ConfigProperty("email-host")
    private String emailHost;

    @ConfigProperty("email-port")
    private int emailPort;
}

// Good - hierarchical structure
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host;

    @ConfigProperty("port")
    private int port;
}

public class EmailSettings {
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

    @ConfigProperty("email")
    @ConfigEmbeddedObject(EmailSettings.class)
    private EmailSettings email;
}
```

### 9. Use Lists for Repeating Structures

When you have multiple similar items, use `@ConfigObjectList`:

```java
// Bad - hardcoded repeated structure
@IocBean
public class KitConfig {
    @ConfigProperty("kit1-name")
    private String kit1Name;

    @ConfigProperty("kit1-items")
    private List<String> kit1Items;

    @ConfigProperty("kit2-name")
    private String kit2Name;

    @ConfigProperty("kit2-items")
    private List<String> kit2Items;
    // Not scalable!
}

// Good - dynamic list
public class Kit {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("items")
    private List<String> items;
}

@IocBean
public class KitConfig {
    @ConfigProperty("kits")
    @ConfigObjectList(Kit.class)
    private List<Kit> kits;
}
```

### 10. Avoid Deep Nesting

Keep nesting to 2-3 levels maximum:

```java
// Bad - too deeply nested
public class Level4 {
    @ConfigProperty("value")
    private String value;
}

public class Level3 {
    @ConfigProperty("level4")
    @ConfigEmbeddedObject(Level4.class)
    private Level4 level4;
}

public class Level2 {
    @ConfigProperty("level3")
    @ConfigEmbeddedObject(Level3.class)
    private Level3 level3;
}

// Good - flattened structure
public class Settings {
    @ConfigProperty("important-value")
    private String importantValue;
}
```

## Common Patterns

### Pattern: Configuration DTO Hierarchy

```java
// Leaf DTOs (no nested objects)
public class ConnectionSettings {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;

    @ConfigProperty("timeout")
    private int timeout = 5000;

    // Getters...
}

public class PoolSettings {
    @ConfigProperty("min-size")
    private int minSize = 5;

    @ConfigProperty("max-size")
    private int maxSize = 20;

    // Getters...
}

// Root configuration bean
@IocBean
@ConfigProperties("database")
public class DatabaseConfig {
    @ConfigProperty("connection")
    @ConfigEmbeddedObject(ConnectionSettings.class)
    private ConnectionSettings connection;

    @ConfigProperty("pool")
    @ConfigEmbeddedObject(PoolSettings.class)
    private PoolSettings pool;

    // Getters for external access
    public ConnectionSettings getConnection() { return connection; }
    public PoolSettings getPool() { return pool; }
}
```

### Pattern: Item Registry

```java
// Item DTO
public class CustomItem {
    @ConfigProperty("id")
    private String id;

    @ConfigProperty("name")
    private String name;

    @ConfigProperty("material")
    @ConfigTransformer(ToEnum.class)
    private Material material;

    @ConfigProperty("lore")
    private List<String> lore;

    // Getters...
}

// Registry bean
@IocBean
public class ItemRegistry {
    @ConfigProperty("custom-items")
    @ConfigObjectList(CustomItem.class)
    private List<CustomItem> items;

    private Map<String, CustomItem> itemMap;

    @PostConstruct
    public void init() {
        // Index items by ID for fast lookup
        itemMap = items.stream()
            .collect(Collectors.toMap(CustomItem::getId, Function.identity()));
    }

    public CustomItem getItem(String id) {
        return itemMap.get(id);
    }

    public Collection<CustomItem> getAllItems() {
        return itemMap.values();
    }
}
```

### Pattern: Feature Configuration

```java
// Feature settings
public class HomeFeature {
    @ConfigProperty("enabled")
    private boolean enabled = true;

    @ConfigProperty("max-homes")
    private int maxHomes = 5;

    @ConfigProperty("cooldown")
    private int cooldown = 60;

    // Getters...
}

public class WarpFeature {
    @ConfigProperty("enabled")
    private boolean enabled = true;

    @ConfigProperty("allow-public")
    private boolean allowPublic = true;

    // Getters...
}

// Feature configuration bean
@IocBean
@ConfigProperties("features")
public class FeatureConfig {
    @ConfigProperty("homes")
    @ConfigEmbeddedObject(HomeFeature.class)
    private HomeFeature homes;

    @ConfigProperty("warps")
    @ConfigEmbeddedObject(WarpFeature.class)
    private WarpFeature warps;

    public HomeFeature getHomes() { return homes; }
    public WarpFeature getWarps() { return warps; }
}
```

### Pattern: Reward Configuration

```java
// Reward DTO
public class Reward {
    @ConfigProperty("type")
    private String type;

    @ConfigProperty("amount")
    private int amount;

    @ConfigProperty("chance")
    private double chance = 1.0;

    // Getters...
}

// Activity with rewards
public class Activity {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("description")
    private String description;

    @ConfigProperty("rewards")
    @ConfigObjectList(Reward.class)
    private List<Reward> rewards;

    public List<Reward> rollRewards() {
        return rewards.stream()
            .filter(r -> Math.random() < r.getChance())
            .collect(Collectors.toList());
    }

    // Getters...
}

@IocBean
public class ActivityConfig {
    @ConfigProperty("activities")
    @ConfigObjectList(Activity.class)
    private List<Activity> activities;
}
```

## Integration with Other Features

### With Bean Providers

Use embedded objects in bean providers:

```java
public class ApiSettings {
    @ConfigProperty("base-url")
    private String baseUrl;

    @ConfigProperty("api-key")
    private String apiKey;

    @ConfigProperty("timeout")
    private int timeout = 30;

    // Getters...
}

@TubingConfiguration
public class ApiConfiguration {
    @IocBeanProvider
    public static ApiClient provideApiClient(
        @ConfigProperty("api")
        @ConfigEmbeddedObject(ApiSettings.class)
        ApiSettings settings
    ) {
        return new ApiClient(
            settings.getBaseUrl(),
            settings.getApiKey(),
            settings.getTimeout()
        );
    }
}
```

### With Conditional Beans

Conditionally create beans based on embedded object properties:

```java
public class FeatureSettings {
    @ConfigProperty("enabled")
    private boolean enabled;

    @ConfigProperty("mode")
    private String mode;

    public boolean isEnabled() { return enabled; }
    public String getMode() { return mode; }
}

@IocBean
@ConfigProperties("features")
public class FeatureConfig {
    @ConfigProperty("advanced")
    @ConfigEmbeddedObject(FeatureSettings.class)
    private FeatureSettings advanced;

    public FeatureSettings getAdvanced() { return advanced; }
}

@IocBean(conditionalOnProperty = "features.advanced.enabled")
public class AdvancedFeatureService {
    private final FeatureConfig config;

    public AdvancedFeatureService(FeatureConfig config) {
        this.config = config;
        // Only created if features.advanced.enabled = true
    }
}
```

### With Multi-File Configuration

Embedded objects work with multi-file configurations:

```java
@IocBean
public class MultiFileConfig {
    // From config.yml
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings database;

    // From messages.yml
    @ConfigProperty("messages.prefix")
    private String messagePrefix;
}
```

## Troubleshooting

### Issue: "Invalid ConfigEmbeddedObject configuration"

**Problem:** The embedded object class cannot be instantiated.

**Solutions:**
1. Ensure the class has a public no-args constructor
2. Check that the class is not abstract
3. Verify the class is public

```java
// Bad - no constructor
public class Settings {
    private Settings(String value) { }  // Only parameterized constructor
}

// Good - has no-args constructor
public class Settings {
    public Settings() { }  // Public no-args constructor

    public Settings(String value) {
        // Additional constructor is fine
    }
}
```

### Issue: "Configuration not found for X"

**Problem:** Required property is missing in the YAML.

**Solution:** Either add the property to your config or make it optional:

```java
// Make optional with default
@ConfigProperty("optional-value")
private String optionalValue = "default";

// Or remove required = true
@ConfigProperty(value = "optional-value", required = false)
private String optionalValue;
```

### Issue: Properties are null despite being in YAML

**Problem:** Property paths don't match YAML structure.

**Solution:** Verify the full path including prefixes:

```java
// With @ConfigProperties("database")
@ConfigProperty("host")  // Looks for: database.host

// Without @ConfigProperties
@ConfigProperty("database.host")  // Looks for: database.host
```

### Issue: List items are not populated

**Problem:** YAML structure doesn't match expected format.

**Solution:** Ensure YAML is a list of objects:

```yaml
# Good - list of objects
items:
  - name: "item1"
    price: 100
  - name: "item2"
    price: 200

# Bad - not a list
items:
  name: "item1"
  price: 100
```

## Next Steps

Now that you understand configuration objects:

- Learn about [Configuration Injection](Configuration-Injection.md) for basic property mapping
- Explore [Bean Providers](Bean-Providers.md) for using objects in factory methods
- Read [Bean Lifecycle](Bean-Lifecycle.md) for understanding when configuration is injected
- Check [Project Structure](../getting-started/Project-Structure.md) for organizing configuration DTOs

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration examples
- [Migration Guide](../getting-started/Migration-Guide.md) - Migrating from manual parsing
- [IoC Container](IoC-Container.md) - Container internals
