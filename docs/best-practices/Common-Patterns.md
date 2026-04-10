# Common Patterns

Design patterns are proven solutions to recurring problems in software design. This guide covers the most useful patterns for Minecraft plugin development with Tubing, showing how the framework's IoC container and dependency injection features make implementing these patterns natural and straightforward.

## Overview

Tubing's architecture naturally supports several key design patterns:

- **Service Layer Pattern** - Organize business logic in injectable services
- **Repository Pattern** - Abstract data access behind interfaces
- **Factory Pattern** - Create objects dynamically with bean providers
- **Strategy Pattern** - Swap algorithms using multi-providers
- **Command Pattern** - Encapsulate actions as objects
- **Observer Pattern** - React to events with listener beans
- **Facade Pattern** - Simplify complex subsystems

Each pattern leverages Tubing's features to reduce boilerplate and improve code quality.

## Service Layer Pattern

The service layer pattern organizes business logic into reusable service classes that handle core functionality. This separates business logic from infrastructure concerns like commands, listeners, and data access.

### Basic Service

```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository;
    private final MessageService messages;

    public PlayerService(PlayerRepository repository,
                        MessageService messages) {
        this.repository = repository;
        this.messages = messages;
    }

    public void teleportHome(Player player, String homeName) {
        Home home = repository.getHome(player, homeName);

        if (home == null) {
            messages.sendError(player, "home.not-found", homeName);
            return;
        }

        player.teleport(home.getLocation());
        messages.sendSuccess(player, "home.teleported", homeName);
    }

    public void setHome(Player player, String homeName) {
        if (repository.getHomeCount(player) >= getMaxHomes(player)) {
            messages.sendError(player, "home.max-reached");
            return;
        }

        Home home = new Home(player.getUniqueId(), homeName, player.getLocation());
        repository.saveHome(home);
        messages.sendSuccess(player, "home.set", homeName);
    }

    private int getMaxHomes(Player player) {
        if (player.hasPermission("homes.vip")) return 10;
        return 3;
    }
}
```

### Service with Configuration

```java
@IocBean
public class EconomyService {
    private final VaultEconomy vault;

    @ConfigProperty("economy.starting-balance")
    private double startingBalance;

    @ConfigProperty("economy.max-balance")
    private double maxBalance;

    public EconomyService(VaultEconomy vault) {
        this.vault = vault;
    }

    public void deposit(Player player, double amount) {
        double currentBalance = vault.getBalance(player);

        if (currentBalance + amount > maxBalance) {
            amount = maxBalance - currentBalance;
        }

        vault.depositPlayer(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!vault.has(player, amount)) {
            return false;
        }

        vault.withdrawPlayer(player, amount);
        return true;
    }

    public void initializeAccount(Player player) {
        if (vault.getBalance(player) == 0) {
            vault.depositPlayer(player, startingBalance);
        }
    }
}
```

### Service Layer Best Practices

**1. Keep services focused on a single domain**

```java
// Good - focused responsibility
@IocBean
public class HomeService { }

@IocBean
public class WarpService { }

@IocBean
public class TeleportService { }

// Bad - doing too much
@IocBean
public class TeleportationManager {
    // Handles homes, warps, teleports, cooldowns, permissions...
}
```

**2. Depend on interfaces, not implementations**

```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository; // Interface
    private final NotificationService notifications; // Interface

    public PlayerService(PlayerRepository repository,
                        NotificationService notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }
}
```

**3. Services should not depend on Bukkit events or commands**

```java
// Good - service is platform-agnostic
@IocBean
public class CombatService {
    public void handlePvpDamage(UUID attacker, UUID victim, double damage) {
        // Pure business logic
    }
}

// Command uses the service
@IocBukkitCommandHandler("pvp")
public class PvpCommand {
    private final CombatService combatService;

    public boolean handle(CommandSender sender, Command cmd, String label, String[] args) {
        // Extract data, call service
        combatService.handlePvpDamage(attackerId, victimId, damage);
        return true;
    }
}
```

**4. Use services for testability**

```java
public class PlayerServiceTest {
    @Test
    public void testSetHome_MaxHomesReached() {
        // Mock dependencies
        PlayerRepository mockRepo = mock(PlayerRepository.class);
        MessageService mockMessages = mock(MessageService.class);
        Player mockPlayer = mock(Player.class);

        when(mockRepo.getHomeCount(mockPlayer)).thenReturn(3);

        // Create service with mocks
        PlayerService service = new PlayerService(mockRepo, mockMessages);

        // Test
        service.setHome(mockPlayer, "home1");

        // Verify error message was sent
        verify(mockMessages).sendError(mockPlayer, "home.max-reached");
        verify(mockRepo, never()).saveHome(any());
    }
}
```

## Repository Pattern

The repository pattern abstracts data storage behind interfaces, allowing you to swap storage implementations without changing business logic.

### Repository Interface

```java
public interface PlayerRepository {
    PlayerData load(UUID playerId);
    void save(PlayerData data);
    void delete(UUID playerId);
    boolean exists(UUID playerId);
}
```

### File-Based Implementation

```java
@IocBean
public class FilePlayerRepository implements PlayerRepository {
    private final File dataFolder;

    public FilePlayerRepository(@InjectTubingPlugin TubingPlugin plugin) {
        this.dataFolder = new File(plugin.getDataFolder(), "players");
        dataFolder.mkdirs();
    }

    @Override
    public PlayerData load(UUID playerId) {
        File file = new File(dataFolder, playerId + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return PlayerData.fromYaml(config);
    }

    @Override
    public void save(PlayerData data) {
        File file = new File(dataFolder, data.getPlayerId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        data.toYaml(config);

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(UUID playerId) {
        File file = new File(dataFolder, playerId + ".yml");
        file.delete();
    }

    @Override
    public boolean exists(UUID playerId) {
        File file = new File(dataFolder, playerId + ".yml");
        return file.exists();
    }
}
```

### Database Implementation

```java
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    private final HikariDataSource dataSource;

    public DatabasePlayerRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PlayerData load(UUID playerId) {
        String sql = "SELECT * FROM player_data WHERE player_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return PlayerData.fromResultSet(rs);
            }

            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load player data", e);
        }
    }

    @Override
    public void save(PlayerData data) {
        String sql = "INSERT INTO player_data (player_id, data) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE data = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String json = data.toJson();
            stmt.setString(1, data.getPlayerId().toString());
            stmt.setString(2, json);
            stmt.setString(3, json);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save player data", e);
        }
    }

    @Override
    public void delete(UUID playerId) {
        String sql = "DELETE FROM player_data WHERE player_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player data", e);
        }
    }

    @Override
    public boolean exists(UUID playerId) {
        String sql = "SELECT 1 FROM player_data WHERE player_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }
}
```

### Choosing Implementation with Configuration

Use bean providers to select the implementation based on configuration:

```java
@TubingConfiguration
public class StorageConfiguration {

    @IocBeanProvider
    public static PlayerRepository providePlayerRepository(
            @ConfigProperty("storage.type") String storageType,
            @InjectTubingPlugin TubingPlugin plugin,
            HikariDataSource dataSource) {

        switch (storageType.toLowerCase()) {
            case "mysql":
                return new DatabasePlayerRepository(dataSource);
            case "file":
            case "yaml":
                return new FilePlayerRepository(plugin);
            default:
                throw new IllegalArgumentException("Unknown storage type: " + storageType);
        }
    }
}
```

**Configuration (config.yml):**
```yaml
storage:
  type: mysql  # or 'file'
```

### Repository with Caching

```java
@IocBean
public class CachedPlayerRepository implements PlayerRepository {
    private final PlayerRepository delegate;
    private final Map<UUID, PlayerData> cache;

    @ConfigProperty("cache.ttl-seconds")
    private int ttlSeconds;

    public CachedPlayerRepository(PlayerRepository delegate) {
        this.delegate = delegate;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public PlayerData load(UUID playerId) {
        PlayerData cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        PlayerData data = delegate.load(playerId);
        if (data != null) {
            cache.put(playerId, data);
        }

        return data;
    }

    @Override
    public void save(PlayerData data) {
        delegate.save(data);
        cache.put(data.getPlayerId(), data);
    }

    @Override
    public void delete(UUID playerId) {
        delegate.delete(playerId);
        cache.remove(playerId);
    }

    @Override
    public boolean exists(UUID playerId) {
        if (cache.containsKey(playerId)) {
            return true;
        }
        return delegate.exists(playerId);
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }
}
```

### Repository Best Practices

**1. Keep repository interfaces simple and focused**

```java
// Good - clear, focused interface
public interface HomeRepository {
    Home getHome(UUID playerId, String homeName);
    List<Home> getHomes(UUID playerId);
    void saveHome(Home home);
    void deleteHome(UUID playerId, String homeName);
}

// Bad - doing too much
public interface DataRepository {
    // Handles homes, warps, player data, economy, etc.
}
```

**2. Use DTOs (Data Transfer Objects) for complex data**

```java
public class PlayerData {
    private final UUID playerId;
    private final String lastKnownName;
    private final long lastSeen;
    private final Map<String, Object> metadata;

    // Constructor, getters, serialization methods
}
```

**3. Handle errors gracefully**

```java
@Override
public PlayerData load(UUID playerId) {
    try {
        return loadFromDatabase(playerId);
    } catch (SQLException e) {
        plugin.getLogger().severe("Failed to load player " + playerId + ": " + e.getMessage());
        return null; // or throw a custom exception
    }
}
```

## Factory Pattern

The factory pattern uses bean providers to create objects dynamically based on runtime conditions. This is particularly useful when you need to instantiate different implementations based on configuration.

### Simple Factory

```java
@TubingConfiguration
public class StorageFactory {

    @IocBeanProvider
    public static StorageService provideStorageService(
            @ConfigProperty("storage.backend") String backend,
            DatabaseService database) {

        switch (backend.toLowerCase()) {
            case "mysql":
                return new MySQLStorageService(database);
            case "sqlite":
                return new SQLiteStorageService(database);
            case "json":
                return new JsonStorageService();
            default:
                throw new IllegalArgumentException("Unknown backend: " + backend);
        }
    }
}
```

### Factory with Complex Configuration

```java
@TubingConfiguration
public class DatabaseFactory {

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

        // Additional configuration
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }
}
```

**Configuration:**
```yaml
database:
  host: localhost
  port: 3306
  name: minecraft
  username: root
  password: secret
  pool:
    size: 10
```

### Factory with Validation

```java
@TubingConfiguration
public class PaymentFactory {

    @IocBeanProvider
    public static PaymentProcessor providePaymentProcessor(
            @ConfigProperty("payment.provider") String provider,
            @ConfigProperty("payment.api-key") String apiKey,
            @ConfigProperty("payment.sandbox") boolean sandbox) {

        // Validate configuration
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("payment.api-key must be configured");
        }

        // Create appropriate implementation
        switch (provider.toLowerCase()) {
            case "stripe":
                return new StripeProcessor(apiKey, sandbox);
            case "paypal":
                return new PayPalProcessor(apiKey, sandbox);
            case "disabled":
                return new DisabledPaymentProcessor();
            default:
                throw new IllegalArgumentException("Unknown payment provider: " + provider);
        }
    }
}
```

### Factory Best Practices

**1. Validate inputs in factory methods**

```java
@IocBeanProvider
public static EmailService provideEmailService(
        @ConfigProperty("email.host") String host,
        @ConfigProperty("email.port") int port) {

    if (host == null || host.isEmpty()) {
        throw new IllegalArgumentException("email.host must be configured");
    }

    if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("email.port must be between 1 and 65535");
    }

    return new SmtpEmailService(host, port);
}
```

**2. Provide sensible defaults for optional features**

```java
@IocBeanProvider
public static CacheService provideCacheService(
        @ConfigProperty("cache.enabled") boolean enabled) {

    if (!enabled) {
        return new NoOpCacheService(); // No-op implementation
    }

    return new RedisCacheService();
}
```

**3. Use builder pattern in factories**

```java
@IocBeanProvider
public static RestClient provideRestClient(
        @ConfigProperty("api.base-url") String baseUrl,
        @ConfigProperty("api.timeout") int timeout,
        @ConfigProperty("api.api-key") String apiKey) {

    return RestClient.builder()
        .baseUrl(baseUrl)
        .timeout(Duration.ofSeconds(timeout))
        .header("Authorization", "Bearer " + apiKey)
        .header("User-Agent", "MyPlugin/1.0")
        .build();
}
```

## Strategy Pattern

The strategy pattern allows you to swap algorithms at runtime. With Tubing, you can use multi-providers to implement this pattern elegantly.

### Basic Strategy Pattern

```java
public interface CompressionStrategy {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);
    String getName();
}

@IocBean
@IocMultiProvider(CompressionStrategy.class)
public class GzipCompression implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        // GZIP compression logic
        return compressed;
    }

    @Override
    public byte[] decompress(byte[] data) {
        // GZIP decompression logic
        return decompressed;
    }

    @Override
    public String getName() {
        return "gzip";
    }
}

@IocBean
@IocMultiProvider(CompressionStrategy.class)
public class ZlibCompression implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        // ZLIB compression logic
        return compressed;
    }

    @Override
    public byte[] decompress(byte[] data) {
        // ZLIB decompression logic
        return decompressed;
    }

    @Override
    public String getName() {
        return "zlib";
    }
}

@IocBean
public class DataCompressor {
    private final Map<String, CompressionStrategy> strategies;

    @ConfigProperty("compression.default")
    private String defaultStrategy;

    public DataCompressor(@IocMulti(CompressionStrategy.class)
                         List<CompressionStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(
                CompressionStrategy::getName,
                strategy -> strategy
            ));
    }

    public byte[] compress(byte[] data) {
        CompressionStrategy strategy = strategies.get(defaultStrategy);
        if (strategy == null) {
            throw new IllegalStateException("Unknown compression strategy: " + defaultStrategy);
        }
        return strategy.compress(data);
    }

    public byte[] compress(byte[] data, String strategyName) {
        CompressionStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown compression strategy: " + strategyName);
        }
        return strategy.compress(data);
    }
}
```

### Strategy Pattern for Damage Calculation

```java
public interface DamageCalculator {
    double calculateDamage(Player attacker, Player victim, double baseDamage);
    String getType();
}

@IocBean
@IocMultiProvider(DamageCalculator.class)
public class StandardDamageCalculator implements DamageCalculator {
    @Override
    public double calculateDamage(Player attacker, Player victim, double baseDamage) {
        // Standard damage calculation
        return baseDamage;
    }

    @Override
    public String getType() {
        return "standard";
    }
}

@IocBean
@IocMultiProvider(DamageCalculator.class)
public class ArmorScaledDamageCalculator implements DamageCalculator {
    @Override
    public double calculateDamage(Player attacker, Player victim, double baseDamage) {
        // Reduce damage based on armor
        double armorPoints = getArmorPoints(victim);
        double reduction = armorPoints / 100.0;
        return baseDamage * (1.0 - reduction);
    }

    @Override
    public String getType() {
        return "armor-scaled";
    }

    private double getArmorPoints(Player player) {
        // Calculate total armor points
        return 20.0;
    }
}

@IocBean
@IocMultiProvider(DamageCalculator.class)
public class CriticalHitCalculator implements DamageCalculator {
    @Override
    public double calculateDamage(Player attacker, Player victim, double baseDamage) {
        // Random critical hits
        if (Math.random() < 0.1) { // 10% crit chance
            return baseDamage * 2.0;
        }
        return baseDamage;
    }

    @Override
    public String getType() {
        return "critical-hit";
    }
}

@IocBean
public class CombatService {
    private final Map<String, DamageCalculator> calculators;

    @ConfigProperty("combat.damage-type")
    private String damageType;

    public CombatService(@IocMulti(DamageCalculator.class)
                        List<DamageCalculator> calculators) {
        this.calculators = calculators.stream()
            .collect(Collectors.toMap(
                DamageCalculator::getType,
                calc -> calc
            ));
    }

    public void handlePvpDamage(Player attacker, Player victim, double baseDamage) {
        DamageCalculator calculator = calculators.get(damageType);
        if (calculator == null) {
            calculator = calculators.get("standard");
        }

        double finalDamage = calculator.calculateDamage(attacker, victim, baseDamage);
        victim.damage(finalDamage);
    }
}
```

### Strategy with Priority

```java
public interface ValidationStrategy {
    ValidationResult validate(Player player, String input);
    int getPriority(); // Lower = higher priority
}

@IocBean
@IocMultiProvider(ValidationStrategy.class)
public class LengthValidation implements ValidationStrategy {
    @Override
    public ValidationResult validate(Player player, String input) {
        if (input.length() < 3 || input.length() > 16) {
            return ValidationResult.failure("Name must be 3-16 characters");
        }
        return ValidationResult.success();
    }

    @Override
    public int getPriority() {
        return 100;
    }
}

@IocBean
@IocMultiProvider(ValidationStrategy.class)
public class ProfanityValidation implements ValidationStrategy {
    private final List<String> bannedWords;

    public ProfanityValidation() {
        this.bannedWords = Arrays.asList("badword1", "badword2");
    }

    @Override
    public ValidationResult validate(Player player, String input) {
        for (String word : bannedWords) {
            if (input.toLowerCase().contains(word)) {
                return ValidationResult.failure("Name contains inappropriate language");
            }
        }
        return ValidationResult.success();
    }

    @Override
    public int getPriority() {
        return 200;
    }
}

@IocBean
public class ValidationService {
    private final List<ValidationStrategy> strategies;

    public ValidationService(@IocMulti(ValidationStrategy.class)
                            List<ValidationStrategy> strategies) {
        // Sort by priority
        this.strategies = strategies.stream()
            .sorted(Comparator.comparingInt(ValidationStrategy::getPriority))
            .collect(Collectors.toList());
    }

    public ValidationResult validateAll(Player player, String input) {
        for (ValidationStrategy strategy : strategies) {
            ValidationResult result = strategy.validate(player, input);
            if (!result.isValid()) {
                return result; // Fail fast
            }
        }
        return ValidationResult.success();
    }
}
```

### Strategy Best Practices

**1. Provide a default strategy**

```java
public byte[] compress(byte[] data) {
    CompressionStrategy strategy = strategies.getOrDefault(
        defaultStrategy,
        strategies.get("none") // Fallback to no compression
    );
    return strategy.compress(data);
}
```

**2. Use enums for strategy selection when possible**

```java
public enum CompressionType {
    GZIP, ZLIB, NONE
}

@ConfigProperty("compression.type")
private CompressionType compressionType;
```

**3. Document strategy interfaces clearly**

```java
/**
 * Strategy for calculating player damage.
 *
 * Multiple implementations can be registered to provide different damage
 * calculation algorithms. The active strategy is selected via configuration.
 *
 * @see StandardDamageCalculator
 * @see ArmorScaledDamageCalculator
 */
public interface DamageCalculator {
    double calculateDamage(Player attacker, Player victim, double baseDamage);
    String getType();
}
```

## Command Pattern

The command pattern encapsulates actions as objects, making them easier to parameterize, queue, and undo. In Minecraft plugins, this is useful for action systems, quest tasks, and macro commands.

### Basic Command Pattern

```java
public interface Action {
    void execute(Player player);
    String getName();
}

@IocBean
@IocMultiProvider(Action.class)
public class TeleportAction implements Action {

    @ConfigProperty("spawn.location")
    @ConfigEmbeddedObject(Location.class)
    private Location spawnLocation;

    @Override
    public void execute(Player player) {
        player.teleport(spawnLocation);
    }

    @Override
    public String getName() {
        return "teleport_spawn";
    }
}

@IocBean
@IocMultiProvider(Action.class)
public class GiveItemAction implements Action {

    @Override
    public void execute(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        player.getInventory().addItem(item);
    }

    @Override
    public String getName() {
        return "give_diamond";
    }
}

@IocBean
@IocMultiProvider(Action.class)
public class SendMessageAction implements Action {
    private final MessageService messages;

    public SendMessageAction(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public void execute(Player player) {
        messages.send(player, "quest.welcome");
    }

    @Override
    public String getName() {
        return "send_welcome";
    }
}

@IocBean
public class ActionExecutor {
    private final Map<String, Action> actions;

    public ActionExecutor(@IocMulti(Action.class) List<Action> actions) {
        this.actions = actions.stream()
            .collect(Collectors.toMap(Action::getName, a -> a));
    }

    public void execute(Player player, String actionName) {
        Action action = actions.get(actionName);
        if (action != null) {
            action.execute(player);
        }
    }

    public void executeAll(Player player, List<String> actionNames) {
        for (String name : actionNames) {
            execute(player, name);
        }
    }
}
```

### Parameterized Commands

```java
public interface ParameterizedAction {
    void execute(Player player, Map<String, String> parameters);
    String getName();
}

@IocBean
@IocMultiProvider(ParameterizedAction.class)
public class TeleportToAction implements ParameterizedAction {

    @Override
    public void execute(Player player, Map<String, String> params) {
        String worldName = params.get("world");
        double x = Double.parseDouble(params.get("x"));
        double y = Double.parseDouble(params.get("y"));
        double z = Double.parseDouble(params.get("z"));

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location location = new Location(world, x, y, z);
            player.teleport(location);
        }
    }

    @Override
    public String getName() {
        return "teleport_to";
    }
}

@IocBean
@IocMultiProvider(ParameterizedAction.class)
public class GiveMoneyAction implements ParameterizedAction {
    private final EconomyService economy;

    public GiveMoneyAction(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public void execute(Player player, Map<String, String> params) {
        double amount = Double.parseDouble(params.getOrDefault("amount", "100"));
        economy.deposit(player, amount);
    }

    @Override
    public String getName() {
        return "give_money";
    }
}
```

### Command with Undo

```java
public interface UndoableAction {
    void execute(Player player);
    void undo(Player player);
    String getName();
}

@IocBean
public class BlockChangeAction implements UndoableAction {
    private final Map<UUID, BlockState> previousStates = new HashMap<>();

    @Override
    public void execute(Player player) {
        Block block = player.getTargetBlock(null, 5);

        // Save previous state
        previousStates.put(player.getUniqueId(), block.getState());

        // Change block
        block.setType(Material.DIAMOND_BLOCK);
    }

    @Override
    public void undo(Player player) {
        BlockState previous = previousStates.get(player.getUniqueId());
        if (previous != null) {
            previous.update(true);
            previousStates.remove(player.getUniqueId());
        }
    }

    @Override
    public String getName() {
        return "change_block";
    }
}
```

### Macro Commands

```java
public class MacroAction implements Action {
    private final List<Action> actions;
    private final String name;

    public MacroAction(String name, List<Action> actions) {
        this.name = name;
        this.actions = actions;
    }

    @Override
    public void execute(Player player) {
        for (Action action : actions) {
            action.execute(player);
        }
    }

    @Override
    public String getName() {
        return name;
    }
}

@IocBean
public class MacroService {
    private final ActionExecutor executor;

    public MacroService(ActionExecutor executor) {
        this.executor = executor;
    }

    public void executeMacro(Player player, String... actionNames) {
        for (String name : actionNames) {
            executor.execute(player, name);
        }
    }
}
```

### Command Pattern Best Practices

**1. Keep commands small and focused**

```java
// Good - single responsibility
public class TeleportHomeAction implements Action {
    public void execute(Player player) {
        player.teleport(getHome(player));
    }
}

// Bad - doing too much
public class ComplexAction implements Action {
    public void execute(Player player) {
        // 200 lines of complex logic
    }
}
```

**2. Validate parameters**

```java
@Override
public void execute(Player player, Map<String, String> params) {
    if (!params.containsKey("amount")) {
        throw new IllegalArgumentException("amount parameter required");
    }

    double amount = Double.parseDouble(params.get("amount"));

    if (amount <= 0) {
        throw new IllegalArgumentException("amount must be positive");
    }

    // Execute action
}
```

**3. Handle errors gracefully**

```java
@Override
public void execute(Player player) {
    try {
        player.teleport(location);
    } catch (Exception e) {
        plugin.getLogger().warning("Failed to execute action: " + e.getMessage());
        player.sendMessage("Action failed!");
    }
}
```

## Observer Pattern

The observer pattern allows objects to notify subscribers when events occur. Tubing's listener system is a natural implementation of this pattern.

### Custom Event System

```java
public interface GameEventListener {
    void onGameEvent(GameEvent event);
}

public class GameEvent {
    private final String type;
    private final Player player;
    private final Map<String, Object> data;

    public GameEvent(String type, Player player) {
        this.type = type;
        this.player = player;
        this.data = new HashMap<>();
    }

    public String getType() { return type; }
    public Player getPlayer() { return player; }
    public Map<String, Object> getData() { return data; }
}

@IocBean
@IocMultiProvider(GameEventListener.class)
public class AchievementListener implements GameEventListener {
    private final AchievementService achievements;

    public AchievementListener(AchievementService achievements) {
        this.achievements = achievements;
    }

    @Override
    public void onGameEvent(GameEvent event) {
        if (event.getType().equals("PLAYER_KILL")) {
            achievements.checkKillAchievements(event.getPlayer());
        }
    }
}

@IocBean
@IocMultiProvider(GameEventListener.class)
public class StatisticsListener implements GameEventListener {
    private final StatisticsService statistics;

    public StatisticsListener(StatisticsService statistics) {
        this.statistics = statistics;
    }

    @Override
    public void onGameEvent(GameEvent event) {
        statistics.recordEvent(event);
    }
}

@IocBean
public class GameEventBus {
    private final List<GameEventListener> listeners;

    public GameEventBus(@IocMulti(GameEventListener.class)
                       List<GameEventListener> listeners) {
        this.listeners = listeners;
    }

    public void fire(GameEvent event) {
        for (GameEventListener listener : listeners) {
            try {
                listener.onGameEvent(event);
            } catch (Exception e) {
                // Log error but continue notifying other listeners
                e.printStackTrace();
            }
        }
    }

    public void fireAsync(GameEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fire(event));
    }
}
```

### Type-Safe Observer Pattern

```java
public interface Observer<T> {
    void update(T data);
}

@IocBean
public class PlayerStateObserver implements Observer<PlayerState> {
    private final MessageService messages;

    public PlayerStateObserver(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public void update(PlayerState state) {
        if (state.getHealth() < 5.0) {
            messages.send(state.getPlayer(), "warning.low-health");
        }
    }
}

@IocBean
public class Observable<T> {
    private final List<Observer<T>> observers = new ArrayList<>();

    public void subscribe(Observer<T> observer) {
        observers.add(observer);
    }

    public void unsubscribe(Observer<T> observer) {
        observers.remove(observer);
    }

    public void notify(T data) {
        for (Observer<T> observer : observers) {
            observer.update(data);
        }
    }
}
```

### Observer Best Practices

**1. Handle exceptions in observers**

```java
public void fire(GameEvent event) {
    for (GameEventListener listener : listeners) {
        try {
            listener.onGameEvent(event);
        } catch (Exception e) {
            plugin.getLogger().severe("Error in event listener: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

**2. Consider async notification for long-running observers**

```java
public void fireAsync(GameEvent event) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        for (GameEventListener listener : listeners) {
            listener.onGameEvent(event);
        }
    });
}
```

**3. Use specific event types**

```java
// Good - specific event types
public class PlayerKillEvent extends GameEvent { }
public class PlayerDeathEvent extends GameEvent { }

// Bad - generic string-based types
event.getType().equals("PLAYER_KILL")
```

## Facade Pattern

The facade pattern provides a simplified interface to a complex subsystem. This is useful for hiding complexity and providing a cleaner API.

### API Facade

```java
@IocBean
public class PlayerAPI {
    private final PlayerDataService dataService;
    private final PlayerRepository repository;
    private final EconomyService economy;
    private final PermissionService permissions;
    private final MessageService messages;

    public PlayerAPI(PlayerDataService dataService,
                    PlayerRepository repository,
                    EconomyService economy,
                    PermissionService permissions,
                    MessageService messages) {
        this.dataService = dataService;
        this.repository = repository;
        this.economy = economy;
        this.permissions = permissions;
        this.messages = messages;
    }

    // Simplified methods that coordinate multiple services
    public void initializePlayer(Player player) {
        dataService.loadPlayerData(player);
        economy.initializeAccount(player);
        permissions.loadPermissions(player);
        messages.sendWelcome(player);
    }

    public void cleanupPlayer(Player player) {
        dataService.savePlayerData(player);
        dataService.unloadPlayerData(player);
    }

    public boolean canAfford(Player player, double cost) {
        return economy.getBalance(player) >= cost;
    }

    public void purchase(Player player, String itemName, double cost) {
        if (!canAfford(player, cost)) {
            messages.sendError(player, "economy.insufficient-funds");
            return;
        }

        economy.withdraw(player, cost);
        messages.sendSuccess(player, "economy.purchased", itemName, cost);
    }
}
```

### Subsystem Facade

```java
@IocBean
public class CombatFacade {
    private final DamageCalculator damageCalc;
    private final CombatLogger logger;
    private final CombatEffects effects;
    private final CombatRewards rewards;

    public CombatFacade(DamageCalculator damageCalc,
                       CombatLogger logger,
                       CombatEffects effects,
                       CombatRewards rewards) {
        this.damageCalc = damageCalc;
        this.logger = logger;
        this.effects = effects;
        this.rewards = rewards;
    }

    public void handlePvpCombat(Player attacker, Player victim, double baseDamage) {
        // Calculate damage
        double finalDamage = damageCalc.calculateDamage(attacker, victim, baseDamage);

        // Apply damage
        victim.damage(finalDamage);

        // Log combat
        logger.logPvpDamage(attacker, victim, finalDamage);

        // Play effects
        effects.playHitEffect(victim.getLocation());

        // Check for kill
        if (victim.getHealth() <= 0) {
            handleKill(attacker, victim);
        }
    }

    private void handleKill(Player killer, Player victim) {
        logger.logKill(killer, victim);
        rewards.giveKillReward(killer);
        effects.playDeathEffect(victim.getLocation());
    }
}
```

### Configuration Facade

```java
@IocBean
public class PluginConfig {

    @ConfigProperty("features.pvp.enabled")
    private boolean pvpEnabled;

    @ConfigProperty("features.economy.enabled")
    private boolean economyEnabled;

    @ConfigProperty("features.permissions.enabled")
    private boolean permissionsEnabled;

    // Simplified access methods
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    public boolean isPermissionsEnabled() {
        return permissionsEnabled;
    }

    public boolean isFeatureEnabled(String feature) {
        switch (feature.toLowerCase()) {
            case "pvp": return pvpEnabled;
            case "economy": return economyEnabled;
            case "permissions": return permissionsEnabled;
            default: return false;
        }
    }
}
```

### Facade Best Practices

**1. Keep facade methods simple**

```java
// Good - simple coordination
public void initializePlayer(Player player) {
    dataService.load(player);
    economy.initialize(player);
}

// Bad - complex logic in facade
public void initializePlayer(Player player) {
    // 100 lines of complex initialization logic
}
```

**2. Facade should not have business logic**

```java
// Good - delegates to services
public void purchase(Player player, String item, double cost) {
    if (!economy.canAfford(player, cost)) {
        messages.sendError(player, "insufficient-funds");
        return;
    }
    economy.withdraw(player, cost);
}

// Bad - business logic in facade
public void purchase(Player player, String item, double cost) {
    // Complex purchase validation and processing logic
}
```

**3. Use facades for external APIs**

```java
// Provide a clean API for other plugins
@IocBean
public class MyPluginAPI {
    // Public API methods that other plugins can use
    public void teleportPlayer(Player player, String locationName) {
        // Implementation
    }

    public boolean hasPermission(Player player, String permission) {
        // Implementation
    }
}
```

## Pattern Combinations

In real-world plugins, you'll often combine multiple patterns. Here's a complete example:

```java
// Repository Pattern - Data access
public interface QuestRepository {
    Quest getQuest(String questId);
    List<Quest> getAllQuests();
    void saveQuest(Quest quest);
}

@IocBean
public class FileQuestRepository implements QuestRepository {
    // Implementation
}

// Strategy Pattern - Quest objectives
public interface QuestObjective {
    boolean isComplete(Player player);
    String getDescription();
}

@IocBean
@IocMultiProvider(QuestObjective.class)
public class KillMobsObjective implements QuestObjective {
    private final int requiredKills = 10;

    @Override
    public boolean isComplete(Player player) {
        // Check kill count
        return true;
    }

    @Override
    public String getDescription() {
        return "Kill 10 zombies";
    }
}

// Command Pattern - Quest rewards
public interface QuestReward {
    void give(Player player);
}

@IocBean
@IocMultiProvider(QuestReward.class)
public class MoneyReward implements QuestReward {
    private final EconomyService economy;
    private final double amount = 100.0;

    public MoneyReward(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public void give(Player player) {
        economy.deposit(player, amount);
    }
}

// Service Layer - Quest logic
@IocBean
public class QuestService {
    private final QuestRepository repository;

    public QuestService(QuestRepository repository) {
        this.repository = repository;
    }

    public void startQuest(Player player, String questId) {
        Quest quest = repository.getQuest(questId);

        if (quest == null) {
            player.sendMessage("Quest not found!");
            return;
        }

        quest.start(player);
        player.sendMessage("Started quest: " + quest.getName());
    }

    public void completeQuest(Player player, Quest quest) {
        // Give rewards
        for (QuestReward reward : quest.getRewards()) {
            reward.give(player);
        }

        player.sendMessage("Quest completed!");
    }
}

// Facade Pattern - Simple API
@IocBean
public class QuestAPI {
    private final QuestService questService;
    private final QuestRepository repository;

    public QuestAPI(QuestService questService,
                   QuestRepository repository) {
        this.questService = questService;
        this.repository = repository;
    }

    public void startQuest(Player player, String questId) {
        questService.startQuest(player, questId);
    }

    public List<String> getAvailableQuests() {
        return repository.getAllQuests().stream()
            .map(Quest::getId)
            .collect(Collectors.toList());
    }
}

// Observer Pattern - Quest events
@IocBukkitListener
public class QuestEventListener implements Listener {
    private final QuestService questService;

    public QuestEventListener(QuestService questService) {
        this.questService = questService;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            // Notify quest system
            questService.onMobKilled(killer, event.getEntity());
        }
    }
}
```

## Summary

Design patterns with Tubing:

- **Service Layer** - Use `@IocBean` to create injectable service classes
- **Repository** - Abstract data access with interfaces and bean providers
- **Factory** - Use `@IocBeanProvider` for dynamic object creation
- **Strategy** - Use `@IocMultiProvider` for swappable algorithms
- **Command** - Encapsulate actions with multi-provider lists
- **Observer** - Use listeners and custom event buses
- **Facade** - Coordinate multiple services with simple APIs

**Key Principles:**

1. Depend on interfaces, not implementations
2. Use dependency injection for all dependencies
3. Keep classes focused on single responsibilities
4. Leverage configuration for flexibility
5. Combine patterns when appropriate

## Next Steps

- **[Project Architecture](Project-Architecture.md)** - Organize large plugins effectively
- **[Testing](Testing.md)** - Test pattern implementations
- **[Bean Providers](../core/Bean-Providers.md)** - Master factory patterns
- **[Multi-Implementation](../core/Multi-Implementation.md)** - Strategy and observer patterns
- **[Dependency Injection](../core/Dependency-Injection.md)** - DI best practices

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Configuration Injection](../core/Configuration-Injection.md) - Configuration patterns
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
