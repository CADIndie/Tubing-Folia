# Configuration Transformers

Configuration Transformers enable custom type conversion, validation, and normalization of YAML configuration values during injection. They transform raw configuration data into the exact types and formats your application needs.

## What are Configuration Transformers?

Configuration transformers allow you to:
- Convert strings to complex types (enums, UUIDs, durations)
- Normalize configuration values (lowercase, uppercase)
- Parse complex formats (wildcard patterns, location strings)
- Validate configuration during injection
- Chain multiple transformations together
- Inject dependencies into transformers

**Without Transformers:**
```java
@IocBean
public class GameConfig {
    @ConfigProperty("difficulty")
    private String difficultyString;  // "HARD"

    private Difficulty difficulty;

    public GameConfig() {
        // Manual conversion required
        this.difficulty = Difficulty.valueOf(difficultyString);
    }
}
```

**With Transformers:**
```java
@IocBean
public class GameConfig {
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;  // Automatically converted
}
```

## The @ConfigTransformer Annotation

The `@ConfigTransformer` annotation applies one or more transformers to a configuration property.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ConfigTransformer {
    Class<? extends IConfigTransformer>[] value();  // Transformer class(es)
}
```

### Basic Usage

```java
@IocBean
public class ServerConfig {
    @ConfigProperty("default-gamemode")
    @ConfigTransformer(ToEnum.class)
    private GameMode gamemode;
}
```

**config.yml:**
```yaml
default-gamemode: "SURVIVAL"
```

### Multiple Transformers (Chaining)

Transformers can be chained by specifying multiple transformer classes:

```java
@IocBean
public class CommandConfig {
    @ConfigProperty("allowed-commands")
    @ConfigTransformer({ToLowerCase.class, ValidateCommands.class})
    private List<String> allowedCommands;
}
```

**Execution Order:**
1. `ToLowerCase` transforms the list to lowercase
2. `ValidateCommands` validates the lowercase commands
3. Final result is injected into the field

## The IConfigTransformer Interface

All transformers implement the `IConfigTransformer` interface:

```java
public interface IConfigTransformer<T, S> {
    T mapConfig(S config);
}
```

**Type Parameters:**
- `T` - The output type (what you want)
- `S` - The input type (what YAML provides)

**Example:**
```java
public class ToEnum<T extends Enum<T>> implements IConfigTransformer<Enum<T>, String> {
    //                                                                ↑          ↑
    //                                                              Output     Input

    @Override
    public Enum<T> mapConfig(String config) {
        // Transform String → Enum
        return Enum.valueOf(type, config);
    }
}
```

## Built-in Transformers

Tubing provides several ready-to-use transformers for common use cases.

### ToEnum - Enum Conversion

Converts string values to Java enum constants.

#### Implementation

```java
public class ToEnum<T extends Enum<T>> implements IConfigTransformer<Enum<T>, String> {
    private final Class<T> type;

    public ToEnum(Class<T> type) {
        this.type = type;
    }

    @Override
    public Enum<T> mapConfig(String config) {
        return Enum.valueOf(type, config);
    }
}
```

#### Usage

```java
public enum Difficulty {
    EASY, NORMAL, HARD, EXTREME
}

public enum GameMode {
    SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR
}

@IocBean
public class GameConfig {
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;

    @ConfigProperty("default-gamemode")
    @ConfigTransformer(ToEnum.class)
    private GameMode gamemode;
}
```

**config.yml:**
```yaml
difficulty: "HARD"
default-gamemode: "SURVIVAL"
```

#### How It Works

The `ToEnum` transformer:
1. Receives the enum type from the field type via constructor
2. Converts the string value to the enum using `Enum.valueOf()`
3. Throws `IllegalArgumentException` if the value doesn't match any enum constant

### ToLowerCase - String Normalization

Converts strings or string collections to lowercase.

#### Implementation

```java
public class ToLowerCase implements IConfigTransformer<Object, Object> {
    @Override
    public Object mapConfig(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String) {
            return ((String) input).toLowerCase();
        }
        if (input instanceof Collection) {
            return ((Collection<?>) input).stream()
                .map(s -> ((String) s).toLowerCase())
                .collect(Collectors.toList());
        }
        throw new ConfigurationException(
            "ToLowerCase transformer needs String or Collection as input, but ["
            + input.getClass() + "] was given"
        );
    }
}
```

#### Usage - Single String

```java
@IocBean
public class WorldConfig {
    @ConfigProperty("default-world")
    @ConfigTransformer(ToLowerCase.class)
    private String defaultWorld;
}
```

**config.yml:**
```yaml
default-world: "World"
```

**Result:** `"world"` (lowercase)

#### Usage - String List

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

**Result:** `["home", "spawn", "tpa"]` (all lowercase)

#### When to Use

Use `ToLowerCase` when:
- Normalizing user input for case-insensitive comparisons
- Converting command names to a standard format
- Standardizing world names or identifiers
- Preparing values for case-insensitive lookups

### ToMaterials - Bukkit Material Wildcards

Converts material names (with wildcard support) to Bukkit `Material` enums.

#### Implementation

```java
public class ToMaterials implements IConfigTransformer<Object, Object> {
    @Override
    public Object mapConfig(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String) {
            return getMaterials((String) input);
        }
        if (input instanceof Collection) {
            return ((Collection<?>) input).stream()
                .flatMap(s -> getMaterials((String) s).stream())
                .collect(Collectors.toSet());
        }
        throw new ConfigurationException(
            "ToMaterial transformer needs String or Collection as input, but ["
            + input.getClass() + "] was given"
        );
    }

    public Set<Material> getMaterials(String input) {
        String regex = ("\\Q" + input.toUpperCase() + "\\E").replace("*", "\\E.*\\Q");
        return Arrays.stream(Material.values())
            .filter(m -> m.name().matches(regex))
            .collect(Collectors.toSet());
    }
}
```

#### Usage - Single Material

```java
@IocBean
public class BlockConfig {
    @ConfigProperty("break-block")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> breakBlock;
}
```

**config.yml:**
```yaml
break-block: "DIAMOND_ORE"
```

**Result:** `Set.of(Material.DIAMOND_ORE)`

#### Usage - Material List with Wildcards

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
  - "*_WOOD"        # Matches OAK_WOOD, BIRCH_WOOD, SPRUCE_WOOD, etc.
  - "DIAMOND_*"     # Matches DIAMOND_ORE, DIAMOND_BLOCK, DIAMOND_SWORD, etc.
  - "*_SWORD"       # Matches all sword types
```

**Result:** A `Set<Material>` containing all matching materials

#### Wildcard Patterns

- `*_WOOD` - Matches materials ending with `_WOOD`
- `DIAMOND_*` - Matches materials starting with `DIAMOND_`
- `*_ORE` - Matches all ore types
- `*STONE*` - Matches materials containing `STONE`
- `STONE` - Exact match only

#### When to Use

Use `ToMaterials` when:
- Configuring allowed/blocked items or blocks
- Supporting material groups (all ores, all wood types)
- Making configuration more maintainable (avoid listing every material)
- Future-proofing configs (wildcards match new materials automatically)

## Creating Custom Transformers

Custom transformers allow you to handle any type conversion or validation logic.

### Basic Custom Transformer

#### Step 1: Implement IConfigTransformer

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

#### Step 2: Use the Transformer

```java
@IocBean
public class PlayerConfig {
    @ConfigProperty("admin-uuid")
    @ConfigTransformer(ToUUID.class)
    private UUID adminUuid;

    @ConfigProperty("moderator-uuids")
    @ConfigTransformer(ToUUID.class)
    private List<UUID> moderatorUuids;
}
```

**config.yml:**
```yaml
admin-uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5"
moderator-uuids:
  - "d8d5a923-7b1e-4b1e-8c5f-5a5b1e1e1e1e"
  - "f1e2d3c4-b5a6-9788-1011-121314151617"
```

### Transformer with Type Parameter

For generic transformers that need type information, use a constructor parameter:

```java
public class ToValidatedEnum<T extends Enum<T>> implements IConfigTransformer<Enum<T>, String> {
    private final Class<T> enumType;
    private final Set<T> allowedValues;

    public ToValidatedEnum(Class<T> enumType) {
        this.enumType = enumType;
        // Only allow specific values
        this.allowedValues = Set.of(
            Enum.valueOf(enumType, "SURVIVAL"),
            Enum.valueOf(enumType, "CREATIVE")
        );
    }

    @Override
    public Enum<T> mapConfig(String config) {
        Enum<T> value = Enum.valueOf(enumType, config);
        if (!allowedValues.contains(value)) {
            throw new ConfigurationException(
                "Invalid gamemode: " + config + ". Only SURVIVAL and CREATIVE are allowed."
            );
        }
        return value;
    }
}
```

**Note:** The type parameter is automatically provided by Tubing when the transformer is instantiated.

### Transformer with Validation

```java
public class ToPositiveInteger implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer config) {
        if (config == null) {
            throw new ConfigurationException("Value cannot be null");
        }
        if (config <= 0) {
            throw new ConfigurationException(
                "Value must be positive, got: " + config
            );
        }
        return config;
    }
}

@IocBean
public class ServerConfig {
    @ConfigProperty("max-players")
    @ConfigTransformer(ToPositiveInteger.class)
    private int maxPlayers;

    @ConfigProperty("min-players")
    @ConfigTransformer(ToPositiveInteger.class)
    private int minPlayers;
}
```

### Transformer with Complex Parsing

#### Example: Duration Transformer

Parse human-readable duration strings like `"5m"`, `"2h"`, `"30s"`:

```java
public class ToDuration implements IConfigTransformer<Duration, String> {
    @Override
    public Duration mapConfig(String config) {
        if (config == null || config.isEmpty()) {
            throw new ConfigurationException("Duration cannot be empty");
        }

        char unit = config.charAt(config.length() - 1);
        String valueStr = config.substring(0, config.length() - 1);

        try {
            int value = Integer.parseInt(valueStr);

            switch (unit) {
                case 's':
                    return Duration.ofSeconds(value);
                case 'm':
                    return Duration.ofMinutes(value);
                case 'h':
                    return Duration.ofHours(value);
                case 'd':
                    return Duration.ofDays(value);
                default:
                    throw new ConfigurationException(
                        "Invalid duration unit: " + unit + ". Use s, m, h, or d"
                    );
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid duration value: " + config, e);
        }
    }
}

@IocBean
public class CooldownConfig {
    @ConfigProperty("command-cooldown")
    @ConfigTransformer(ToDuration.class)
    private Duration commandCooldown;

    @ConfigProperty("teleport-cooldown")
    @ConfigTransformer(ToDuration.class)
    private Duration teleportCooldown;
}
```

**config.yml:**
```yaml
command-cooldown: "5m"    # 5 minutes
teleport-cooldown: "30s"  # 30 seconds
```

#### Example: Location Transformer

Parse location strings like `"100,64,200,world"`:

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

        if (parts.length < 3) {
            throw new ConfigurationException(
                "Location requires at least x,y,z coordinates: " + config
            );
        }

        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            String worldName = parts.length > 3 ? parts[3].trim() : defaultWorld;

            World world = server.getWorld(worldName);
            if (world == null) {
                throw new ConfigurationException("World not found: " + worldName);
            }

            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid location format: " + config, e);
        }
    }
}

@IocBean
public class SpawnConfig {
    @ConfigProperty("spawn-location")
    @ConfigTransformer(ToLocation.class)
    private Location spawnLocation;
}
```

**config.yml:**
```yaml
spawn-location: "100,64,200,world"
default-world: "world"
```

**Note:** The transformer has access to `@ConfigProperty` injection for the `default-world` property.

### Transformer with Collection Support

Handle both single values and collections:

```java
public class ToUpperCase implements IConfigTransformer<Object, Object> {
    @Override
    public Object mapConfig(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof String) {
            return ((String) input).toUpperCase();
        }
        if (input instanceof Collection) {
            return ((Collection<?>) input).stream()
                .map(s -> ((String) s).toUpperCase())
                .collect(Collectors.toList());
        }
        throw new ConfigurationException(
            "ToUpperCase needs String or Collection, got: " + input.getClass()
        );
    }
}

@IocBean
public class TagConfig {
    @ConfigProperty("player-tag")
    @ConfigTransformer(ToUpperCase.class)
    private String playerTag;  // Single string

    @ConfigProperty("admin-tags")
    @ConfigTransformer(ToUpperCase.class)
    private List<String> adminTags;  // List of strings
}
```

**config.yml:**
```yaml
player-tag: "member"
admin-tags:
  - "admin"
  - "moderator"
```

**Result:**
- `playerTag = "MEMBER"`
- `adminTags = ["ADMIN", "MODERATOR"]`

## Transformer Execution Order

### Single Transformer

When using a single transformer, execution is straightforward:

```java
@ConfigProperty("difficulty")
@ConfigTransformer(ToEnum.class)
private Difficulty difficulty;
```

**Flow:**
1. YAML value retrieved: `"HARD"`
2. `ToEnum.mapConfig("HARD")` called
3. Result injected: `Difficulty.HARD`

### Multiple Transformers (Chaining)

Transformers execute left-to-right, with each transformer receiving the output of the previous one:

```java
@ConfigProperty("commands")
@ConfigTransformer({ToLowerCase.class, RemoveDuplicates.class, SortAlphabetically.class})
private List<String> commands;
```

**Flow:**
1. YAML value: `["HOME", "home", "SPAWN", "TPA"]`
2. `ToLowerCase`: `["home", "home", "spawn", "tpa"]`
3. `RemoveDuplicates`: `["home", "spawn", "tpa"]`
4. `SortAlphabetically`: `["home", "spawn", "tpa"]` (already sorted)
5. Final result injected

### Type Compatibility

Each transformer's input type must match the previous transformer's output type:

```java
// Correct - types are compatible
@ConfigProperty("value")
@ConfigTransformer({StringToInt.class, IntToPositive.class})
//                   String→Int         Int→Int
private int value;

// Incorrect - type mismatch
@ConfigProperty("value")
@ConfigTransformer({ToEnum.class, ToLowerCase.class})
//                   String→Enum      Enum→String (ERROR!)
private String value;
```

### Execution Example

```java
public class TrimTransformer implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        return config.trim();
    }
}

public class ValidateNotEmpty implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        if (config.isEmpty()) {
            throw new ConfigurationException("Value cannot be empty");
        }
        return config;
    }
}

@IocBean
public class Config {
    @ConfigProperty("player-name")
    @ConfigTransformer({TrimTransformer.class, ToLowerCase.class, ValidateNotEmpty.class})
    private String playerName;
}
```

**config.yml:**
```yaml
player-name: "  StEvE  "
```

**Execution:**
1. Raw value: `"  StEvE  "`
2. After `TrimTransformer`: `"StEvE"`
3. After `ToLowerCase`: `"steve"`
4. After `ValidateNotEmpty`: `"steve"` (validation passed)
5. Final injection: `playerName = "steve"`

## Transformers with Dependencies

Transformers can have constructor dependencies and configuration injection.

### Constructor Dependency Injection

```java
public class ToPermission implements IConfigTransformer<Permission, String> {
    private final PermissionManager permissionManager;

    public ToPermission(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public Permission mapConfig(String config) {
        Permission perm = permissionManager.getPermission(config);
        if (perm == null) {
            throw new ConfigurationException("Permission not found: " + config);
        }
        return perm;
    }
}
```

**Note:** Dependencies are automatically injected by Tubing's IoC container.

### Configuration Property Injection

Transformers can have their own `@ConfigProperty` fields:

```java
public class ToLocation implements IConfigTransformer<Location, String> {
    @ConfigProperty("default-world")
    private String defaultWorld = "world";

    @ConfigProperty("default-y")
    private int defaultY = 64;

    private final Server server;

    public ToLocation(Server server) {
        this.server = server;
    }

    @Override
    public Location mapConfig(String config) {
        String[] parts = config.split(",");

        double x = Double.parseDouble(parts[0]);
        double y = parts.length > 1 ? Double.parseDouble(parts[1]) : defaultY;
        double z = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
        String worldName = parts.length > 3 ? parts[3] : defaultWorld;

        World world = server.getWorld(worldName);
        return new Location(world, x, y, z);
    }
}
```

**config.yml:**
```yaml
spawn-location: "100,200"  # y and world use defaults
default-world: "world"
default-y: 64
```

### Sharing Transformers Across Beans

Transformers can be shared across multiple beans and will use the same configuration:

```java
@IocBean
@ConfigProperties("spawns")
public class SpawnConfig {
    @ConfigProperty("lobby")
    @ConfigTransformer(ToLocation.class)
    private Location lobbySpawn;

    @ConfigProperty("arena")
    @ConfigTransformer(ToLocation.class)
    private Location arenaSpawn;
}

@IocBean
@ConfigProperties("warps")
public class WarpConfig {
    @ConfigProperty("home")
    @ConfigTransformer(ToLocation.class)
    private Location homeWarp;
}
```

Both beans use the same `ToLocation` transformer with the same `default-world` configuration.

## Use Cases

### Validation

Validate configuration values during injection to fail fast:

```java
public class ToPortNumber implements IConfigTransformer<Integer, Integer> {
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
    @ConfigTransformer(ToPortNumber.class)
    private int port;
}
```

**Benefits:**
- Plugin fails to load with clear error message
- No need to validate in business logic
- Prevents runtime errors from invalid configuration

### Normalization

Normalize values to a standard format:

```java
public class ToNormalizedUsername implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String username) {
        return username.trim().toLowerCase();
    }
}

@IocBean
public class WhitelistConfig {
    @ConfigProperty("whitelisted-players")
    @ConfigTransformer(ToNormalizedUsername.class)
    private List<String> whitelistedPlayers;
}
```

**config.yml:**
```yaml
whitelisted-players:
  - "  Notch  "
  - "Jeb_"
  - "DINNERBONE"
```

**Result:** `["notch", "jeb_", "dinnerbone"]` (normalized)

### Complex Parsing

Parse complex formats into structured data:

```java
public class ToItemStack implements IConfigTransformer<ItemStack, String> {
    @Override
    public ItemStack mapConfig(String config) {
        // Format: "MATERIAL:AMOUNT:DATA"
        String[] parts = config.split(":");

        Material material = Material.valueOf(parts[0]);
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        short data = parts.length > 2 ? Short.parseShort(parts[2]) : 0;

        ItemStack item = new ItemStack(material, amount);
        item.setDurability(data);
        return item;
    }
}

@IocBean
public class RewardConfig {
    @ConfigProperty("daily-reward")
    @ConfigTransformer(ToItemStack.class)
    private ItemStack dailyReward;
}
```

**config.yml:**
```yaml
daily-reward: "DIAMOND:5:0"
```

**Result:** An `ItemStack` of 5 diamonds

### Type Conversion

Convert between related types:

```java
public class ToColor implements IConfigTransformer<Color, String> {
    @Override
    public Color mapConfig(String config) {
        // Support named colors and hex codes
        switch (config.toLowerCase()) {
            case "red": return Color.RED;
            case "green": return Color.GREEN;
            case "blue": return Color.BLUE;
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            default:
                // Parse hex: "#FF5733"
                if (config.startsWith("#")) {
                    return Color.decode(config);
                }
                throw new ConfigurationException("Invalid color: " + config);
        }
    }
}

@IocBean
public class ParticleConfig {
    @ConfigProperty("particle-color")
    @ConfigTransformer(ToColor.class)
    private Color particleColor;
}
```

**config.yml:**
```yaml
particle-color: "red"
# or
particle-color: "#FF5733"
```

### Default Value Injection

Provide computed default values:

```java
public class ToWorldOrDefault implements IConfigTransformer<World, String> {
    private final Server server;

    public ToWorldOrDefault(Server server) {
        this.server = server;
    }

    @Override
    public World mapConfig(String worldName) {
        World world = server.getWorld(worldName);
        if (world == null) {
            // Fall back to default world
            return server.getWorlds().get(0);
        }
        return world;
    }
}

@IocBean
public class SpawnConfig {
    @ConfigProperty("spawn-world")
    @ConfigTransformer(ToWorldOrDefault.class)
    private World spawnWorld;
}
```

**Behavior:**
- If `spawn-world` exists and is valid, use it
- If `spawn-world` doesn't exist or is invalid, use default world
- No exception thrown, graceful fallback

## Best Practices

### 1. Keep Transformers Simple and Focused

```java
// Good - single responsibility
public class ToUUID implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        return UUID.fromString(config);
    }
}

// Bad - doing too much
public class ToUUIDWithValidationAndLogging implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        logger.info("Parsing UUID: " + config);
        UUID uuid = UUID.fromString(config);
        if (uuid.version() != 4) {
            throw new ConfigurationException("Only UUID v4 supported");
        }
        logger.info("Successfully parsed UUID");
        return uuid;
    }
}
```

**Why:** Single-responsibility transformers are easier to test, reuse, and compose.

### 2. Provide Clear Error Messages

```java
// Good - descriptive error
public class ToPort implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer port) {
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException(
                "Port must be between 1024 and 65535, got: " + port + ". " +
                "Ports below 1024 are reserved for system services."
            );
        }
        return port;
    }
}

// Bad - vague error
public class ToPort implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer port) {
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException("Invalid port");
        }
        return port;
    }
}
```

### 3. Handle Null Values Appropriately

```java
// Good - explicit null handling
public class ToLowerCase implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        if (config == null) {
            return null;  // Or throw exception if nulls aren't allowed
        }
        return config.toLowerCase();
    }
}

// Bad - will throw NullPointerException
public class ToLowerCase implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        return config.toLowerCase();  // Crashes on null!
    }
}
```

### 4. Support Both Single Values and Collections

```java
// Good - handles both
public class ToMaterials implements IConfigTransformer<Object, Object> {
    @Override
    public Object mapConfig(Object input) {
        if (input instanceof String) {
            return getMaterial((String) input);
        }
        if (input instanceof Collection) {
            return ((Collection<?>) input).stream()
                .map(s -> getMaterial((String) s))
                .collect(Collectors.toSet());
        }
        throw new ConfigurationException("Expected String or Collection");
    }
}
```

**Why:** Makes your transformer more versatile and reusable across different configuration scenarios.

### 5. Use Chaining for Complex Transformations

```java
// Good - separate concerns
@ConfigProperty("player-names")
@ConfigTransformer({TrimWhitespace.class, ToLowerCase.class, RemoveDuplicates.class})
private List<String> playerNames;

// Bad - one transformer doing everything
@ConfigProperty("player-names")
@ConfigTransformer(NormalizePlayerNames.class)  // Does trim + lowercase + dedup
private List<String> playerNames;
```

**Why:** Chaining allows reusing individual transformers in different combinations.

### 6. Validate Early, Fail Fast

```java
// Good - validates during injection
public class ToPositiveInt implements IConfigTransformer<Integer, Integer> {
    @Override
    public Integer mapConfig(Integer value) {
        if (value <= 0) {
            throw new ConfigurationException("Value must be positive, got: " + value);
        }
        return value;
    }
}

@IocBean
public class Config {
    @ConfigProperty("max-players")
    @ConfigTransformer(ToPositiveInt.class)
    private int maxPlayers;  // Validated at load time
}

// Bad - validates at runtime
@IocBean
public class Config {
    @ConfigProperty("max-players")
    private int maxPlayers;

    public void setMaxPlayers(Player player, int count) {
        if (maxPlayers <= 0) {  // Check happens during gameplay
            throw new IllegalStateException("Invalid config!");
        }
        // ...
    }
}
```

**Why:** Early validation prevents the plugin from loading with invalid configuration.

### 7. Document Expected Input Formats

```java
/**
 * Transforms location strings into Bukkit Location objects.
 *
 * Supported formats:
 * - "x,y,z" - Uses default world
 * - "x,y,z,world" - Specifies world name
 *
 * Example: "100,64,200,world" or "0,70,0"
 */
public class ToLocation implements IConfigTransformer<Location, String> {
    @Override
    public Location mapConfig(String config) {
        // Implementation...
    }
}
```

### 8. Make Transformers Stateless When Possible

```java
// Good - stateless, thread-safe
public class ToUpperCase implements IConfigTransformer<String, String> {
    @Override
    public String mapConfig(String config) {
        return config.toUpperCase();
    }
}

// Bad - has mutable state
public class ToUpperCase implements IConfigTransformer<String, String> {
    private int transformCount = 0;  // Mutable state!

    @Override
    public String mapConfig(String config) {
        transformCount++;
        return config.toUpperCase();
    }
}
```

**Why:** Stateless transformers are simpler, thread-safe, and can be safely reused.

### 9. Test Your Transformers

```java
public class ToUUIDTest {
    private ToUUID transformer = new ToUUID();

    @Test
    public void testValidUUID() {
        String input = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        UUID result = transformer.mapConfig(input);
        assertEquals(UUID.fromString(input), result);
    }

    @Test(expected = ConfigurationException.class)
    public void testInvalidUUID() {
        transformer.mapConfig("not-a-uuid");
    }

    @Test
    public void testNullUUID() {
        UUID result = transformer.mapConfig(null);
        assertNull(result);
    }
}
```

## Common Patterns

### Pattern: Validation Transformer

```java
public class ToValidatedRange implements IConfigTransformer<Integer, Integer> {
    private final int min;
    private final int max;

    public ToValidatedRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Integer mapConfig(Integer value) {
        if (value < min || value > max) {
            throw new ConfigurationException(
                "Value must be between " + min + " and " + max + ", got: " + value
            );
        }
        return value;
    }
}
```

### Pattern: Parser Transformer

```java
public class ToTime implements IConfigTransformer<LocalTime, String> {
    @Override
    public LocalTime mapConfig(String config) {
        try {
            return LocalTime.parse(config);  // HH:mm:ss
        } catch (DateTimeParseException e) {
            throw new ConfigurationException("Invalid time format: " + config, e);
        }
    }
}
```

### Pattern: Lookup Transformer

```java
public class ToWorld implements IConfigTransformer<World, String> {
    private final Server server;

    public ToWorld(Server server) {
        this.server = server;
    }

    @Override
    public World mapConfig(String worldName) {
        World world = server.getWorld(worldName);
        if (world == null) {
            throw new ConfigurationException("World not found: " + worldName);
        }
        return world;
    }
}
```

### Pattern: Builder Transformer

```java
public class ToItemStack implements IConfigTransformer<ItemStack, Map<String, Object>> {
    @Override
    public ItemStack mapConfig(Map<String, Object> config) {
        Material material = Material.valueOf((String) config.get("material"));
        int amount = (int) config.getOrDefault("amount", 1);
        String name = (String) config.get("name");
        List<String> lore = (List<String>) config.get("lore");

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        if (lore != null) {
            meta.setLore(lore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList()));
        }

        item.setItemMeta(meta);
        return item;
    }
}
```

## Integration with Other Features

### With @ConfigEmbeddedObject

Transformers work seamlessly with embedded objects:

```java
public class RewardSettings {
    @ConfigProperty("item")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> item;

    @ConfigProperty("amount")
    private int amount;
}

@IocBean
public class QuestConfig {
    @ConfigProperty("reward")
    @ConfigEmbeddedObject(RewardSettings.class)
    private RewardSettings reward;
}
```

### With @ConfigObjectList

Transformers apply to fields within list objects:

```java
public class Kit {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("items")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> items;
}

@IocBean
public class KitConfig {
    @ConfigProperty("kits")
    @ConfigObjectList(Kit.class)
    private List<Kit> kits;
}
```

### With Constructor Parameters

Transformers work on constructor parameters:

```java
@IocBean
public class SpawnService {
    private final Location spawnLocation;

    public SpawnService(
        @ConfigProperty("spawn.location")
        @ConfigTransformer(ToLocation.class)
        Location spawnLocation
    ) {
        this.spawnLocation = spawnLocation;
    }
}
```

## Next Steps

Now that you understand configuration transformers:

- Learn about [Configuration Objects](Configuration-Objects.md) for complex mappings
- Explore [Configuration Files](Configuration-Files.md) for multi-file setups
- Read [Configuration Injection](Configuration-Injection.md) for injection fundamentals
- Check [Bean Lifecycle](Bean-Lifecycle.md) for when transformers execute

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration examples
- [Project Structure](../getting-started/Project-Structure.md) - Organizing transformers
- [IoC Container](IoC-Container.md) - Container internals
