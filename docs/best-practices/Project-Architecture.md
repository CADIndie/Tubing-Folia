# Project Architecture

Building well-architected Minecraft plugins requires more than just organizing files into packages. This guide covers architectural patterns, design decisions, and best practices for creating maintainable, scalable, and extensible plugins with Tubing.

While [Project Structure](../getting-started/Project-Structure.md) focuses on file organization and package layout, this guide focuses on architectural patterns, design decisions, and how to apply software architecture principles to Minecraft plugin development.

## Architectural Principles

Before diving into specific patterns, understand the core principles that guide good architecture with Tubing:

### 1. Separation of Concerns

Each component should have a single, well-defined responsibility. Don't mix business logic with presentation logic, or data access with user interface handling.

**Bad Example:**
```java
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand {
    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;

        // Everything in one place - BAD!
        Connection conn = DriverManager.getConnection("jdbc:mysql://...");
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO homes...");
        stmt.setString(1, player.getUniqueId().toString());
        stmt.setString(2, args[0]);
        stmt.setString(3, player.getLocation().getWorld().getName());
        stmt.execute();

        player.sendMessage("§aHome set!");
        return true;
    }
}
```

**Good Example:**
```java
// Command handles input/output
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand {
    private final HomeService homeService;

    public SetHomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;
        String homeName = args.length > 0 ? args[0] : "default";

        homeService.createHome(player, homeName, player.getLocation());
        return true;
    }
}

// Service handles business logic
@IocBean
public class HomeService {
    private final HomeRepository repository;
    private final MessageService messages;

    public void createHome(Player player, String name, Location location) {
        Home home = new Home(player.getUniqueId(), name, location);
        repository.save(home);
        messages.sendSuccess(player, "homes.created", name);
    }
}

// Repository handles data access
@IocBean
public class HomeRepository {
    private final Database database;

    public void save(Home home) {
        database.execute("INSERT INTO homes...", home);
    }
}
```

### 2. Dependency Inversion Principle

High-level modules should not depend on low-level modules. Both should depend on abstractions (interfaces). This principle is at the heart of Tubing's IoC container.

```java
// Abstraction
public interface TeleportService {
    void teleport(Player player, Location location);
}

// High-level service depends on abstraction
@IocBean
public class HomeService {
    private final TeleportService teleportService; // Interface, not implementation

    public HomeService(TeleportService teleportService) {
        this.teleportService = teleportService;
    }
}

// Low-level implementation
@IocBean
public class BukkitTeleportService implements TeleportService {
    @Override
    public void teleport(Player player, Location location) {
        player.teleport(location);
    }
}
```

### 3. Open/Closed Principle

Classes should be open for extension but closed for modification. Use interfaces and multi-providers to enable extensibility without changing existing code.

```java
// Define extension point
public interface RewardHandler {
    void giveReward(Player player, Reward reward);
}

// Core system uses extension point
@IocBean
public class RewardService {
    private final List<RewardHandler> handlers;

    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers) {
        this.handlers = handlers;
    }

    public void processReward(Player player, Reward reward) {
        for (RewardHandler handler : handlers) {
            handler.giveReward(player, reward);
        }
    }
}

// Extensions added without modifying core
@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler {
    public void giveReward(Player player, Reward reward) {
        // Give money
    }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemRewardHandler implements RewardHandler {
    public void giveReward(Player player, Reward reward) {
        // Give items
    }
}
```

### 4. Loose Coupling

Components should have minimal dependencies on each other. Use dependency injection and interfaces to reduce coupling.

```java
// Tightly coupled - BAD
@IocBean
public class PlayerService {
    public void processPlayer(Player player) {
        DatabaseManager.getInstance().save(player); // Static coupling!
        ConfigManager.getInstance().reload(); // Global state!
    }
}

// Loosely coupled - GOOD
@IocBean
public class PlayerService {
    private final PlayerRepository repository;
    private final ConfigurationService config;

    public PlayerService(PlayerRepository repository, ConfigurationService config) {
        this.repository = repository;
        this.config = config;
    }

    public void processPlayer(Player player) {
        repository.save(player); // Injected dependency
        config.reload(); // Injected dependency
    }
}
```

### 5. High Cohesion

Related functionality should be grouped together. Each class should have a focused purpose, and related classes should be organized in cohesive modules.

```java
// Low cohesion - unrelated functionality mixed together
@IocBean
public class PluginUtils {
    public void teleportPlayer(Player player) { }
    public void saveDatabase() { }
    public void sendMessage(Player player) { }
    public void loadConfig() { }
}

// High cohesion - focused, related functionality
@IocBean
public class TeleportService {
    public void teleport(Player player, Location location) { }
    public void teleportWithEffects(Player player, Location location) { }
}

@IocBean
public class DatabaseService {
    public void save(Object data) { }
    public void load(UUID id) { }
}
```

## Layered Architecture

A layered architecture organizes your plugin into distinct horizontal layers, each with a specific responsibility. This is the recommended approach for most Tubing plugins.

### Standard Layers

```
┌─────────────────────────────────────┐
│     Presentation Layer              │  Commands, Listeners, GUIs
│  (User Interface / API Endpoints)   │
└─────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────┐
│     Service Layer                   │  Business Logic, Orchestration
│    (Business Logic)                 │
└─────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────┐
│     Repository Layer                │  Data Access, Persistence
│    (Data Access)                    │
└─────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────┐
│     Domain Layer                    │  Models, Entities, Value Objects
│    (Domain Models)                  │
└─────────────────────────────────────┘
```

### Presentation Layer

Handles user interaction and external API calls. This layer should be thin - it validates input and delegates to services.

**Components:**
- Command handlers
- Event listeners
- GUI controllers
- REST API endpoints
- Plugin message handlers

**Example:**
```java
@IocBukkitCommandHandler("home")
public class HomeCommand {
    private final HomeService homeService;
    private final MessageService messages;

    public HomeCommand(HomeService homeService, MessageService messages) {
        this.homeService = homeService;
        this.messages = messages;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            messages.sendError(sender, "command.player-only");
            return true;
        }

        Player player = (Player) sender;

        // Input validation only
        if (args.length != 1) {
            messages.sendError(sender, "command.home.usage");
            return true;
        }

        // Delegate to service layer
        homeService.teleportToHome(player, args[0]);
        return true;
    }
}
```

**Guidelines:**
- No business logic in this layer
- Validate input format, not business rules
- Convert platform types to domain types
- Delegate all work to service layer
- Handle presentation concerns (messages, formatting)

### Service Layer

Contains business logic and orchestrates operations across multiple repositories. This is where your plugin's core functionality lives.

**Components:**
- Business services
- Validators
- Coordinators
- Workflow managers

**Example:**
```java
@IocBean
public class HomeService {
    private final HomeRepository homeRepository;
    private final PlayerRepository playerRepository;
    private final PermissionService permissions;
    private final TeleportService teleport;
    private final MessageService messages;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds;

    public HomeService(HomeRepository homeRepository,
                       PlayerRepository playerRepository,
                       PermissionService permissions,
                       TeleportService teleport,
                       MessageService messages) {
        this.homeRepository = homeRepository;
        this.playerRepository = playerRepository;
        this.permissions = permissions;
        this.teleport = teleport;
        this.messages = messages;
    }

    public void createHome(Player player, String name, Location location) {
        // Business rule validation
        if (!permissions.hasPermission(player, "homes.create")) {
            messages.sendError(player, "homes.no-permission");
            return;
        }

        List<Home> existingHomes = homeRepository.findByOwner(player.getUniqueId());
        if (existingHomes.size() >= maxHomes) {
            messages.sendError(player, "homes.limit-reached", maxHomes);
            return;
        }

        if (homeRepository.findByOwnerAndName(player.getUniqueId(), name) != null) {
            messages.sendError(player, "homes.already-exists", name);
            return;
        }

        // Execute business operation
        Home home = new Home(player.getUniqueId(), name, location, Instant.now());
        homeRepository.save(home);

        // Update player data
        PlayerData playerData = playerRepository.findById(player.getUniqueId());
        playerData.incrementHomeCount();
        playerRepository.save(playerData);

        messages.sendSuccess(player, "homes.created", name);
    }

    public void teleportToHome(Player player, String name) {
        // Business logic
        Home home = homeRepository.findByOwnerAndName(player.getUniqueId(), name);

        if (home == null) {
            messages.sendError(player, "homes.not-found", name);
            return;
        }

        PlayerData playerData = playerRepository.findById(player.getUniqueId());
        if (!playerData.isHomeTeleportReady(cooldownSeconds)) {
            messages.sendError(player, "homes.cooldown",
                playerData.getRemainingCooldown(cooldownSeconds));
            return;
        }

        // Perform operation
        teleport.teleport(player, home.getLocation());
        playerData.updateLastHomeTeleport(Instant.now());
        playerRepository.save(playerData);

        messages.sendSuccess(player, "homes.teleported", name);
    }
}
```

**Guidelines:**
- All business logic goes here
- Orchestrate multiple repositories
- Enforce business rules
- Handle transactions
- No direct platform API calls (use adapters)
- Should be testable without Minecraft running

### Repository Layer

Handles data persistence and retrieval. Abstracts away the storage mechanism (database, file, memory, etc.).

**Components:**
- Repositories
- Data access objects (DAOs)
- Query builders

**Example:**
```java
public interface HomeRepository {
    Home findById(UUID id);
    Home findByOwnerAndName(UUID ownerId, String name);
    List<Home> findByOwner(UUID ownerId);
    void save(Home home);
    void delete(Home home);
}

@IocBean
public class DatabaseHomeRepository implements HomeRepository {
    private final Database database;

    public DatabaseHomeRepository(Database database) {
        this.database = database;
    }

    @Override
    public Home findByOwnerAndName(UUID ownerId, String name) {
        return database.queryOne(
            "SELECT * FROM homes WHERE owner_id = ? AND name = ?",
            Home.class,
            ownerId.toString(),
            name
        );
    }

    @Override
    public List<Home> findByOwner(UUID ownerId) {
        return database.queryList(
            "SELECT * FROM homes WHERE owner_id = ?",
            Home.class,
            ownerId.toString()
        );
    }

    @Override
    public void save(Home home) {
        database.execute(
            "INSERT INTO homes (id, owner_id, name, world, x, y, z, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?",
            home.getId().toString(),
            home.getOwnerId().toString(),
            home.getName(),
            home.getLocation().getWorld(),
            home.getLocation().getX(),
            home.getLocation().getY(),
            home.getLocation().getZ(),
            home.getCreatedAt(),
            // Update values
            home.getLocation().getWorld(),
            home.getLocation().getX(),
            home.getLocation().getY(),
            home.getLocation().getZ()
        );
    }

    @Override
    public void delete(Home home) {
        database.execute(
            "DELETE FROM homes WHERE id = ?",
            home.getId().toString()
        );
    }
}
```

**Guidelines:**
- One repository per aggregate root
- Return domain models, not database DTOs
- No business logic in repositories
- Abstract storage implementation
- Use interfaces for repository definitions

### Domain Layer

Contains domain models, entities, and value objects. These represent the core concepts of your plugin.

**Components:**
- Entities (objects with identity)
- Value objects (immutable objects without identity)
- Domain events
- Enums and constants

**Example:**
```java
// Entity - has identity (UUID)
public class Home {
    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final HomeLocation location;
    private final Instant createdAt;

    public Home(UUID ownerId, String name, HomeLocation location, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.location = location;
        this.createdAt = createdAt;
    }

    // Getters only - immutable
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public HomeLocation getLocation() { return location; }
    public Instant getCreatedAt() { return createdAt; }
}

// Value object - immutable, no identity
public class HomeLocation {
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomeLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location toBukkitLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }

    public static HomeLocation fromBukkitLocation(Location location) {
        return new HomeLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    // Getters...
}

// Domain event
public class HomeCreatedEvent {
    private final UUID homeId;
    private final UUID ownerId;
    private final String name;
    private final Instant timestamp;

    public HomeCreatedEvent(UUID homeId, UUID ownerId, String name) {
        this.homeId = homeId;
        this.ownerId = ownerId;
        this.name = name;
        this.timestamp = Instant.now();
    }

    // Getters...
}
```

**Guidelines:**
- No dependencies on other layers
- Prefer immutability
- Rich domain models with behavior, not just data
- Use value objects for concepts without identity
- Domain events for significant state changes

### Layer Dependencies

**Critical Rule:** Dependencies only flow downward. Upper layers can depend on lower layers, but never the reverse.

```
✓ Command → Service → Repository → Domain
✗ Repository → Service (WRONG!)
✗ Domain → Repository (WRONG!)
```

**Example of proper dependencies:**
```java
// Presentation depends on Service - OK
@IocBukkitCommandHandler("home")
public class HomeCommand {
    private final HomeService homeService; // ✓ Depends on service layer
}

// Service depends on Repository - OK
@IocBean
public class HomeService {
    private final HomeRepository repository; // ✓ Depends on repository layer
}

// Repository depends on Domain - OK
@IocBean
public class HomeRepository {
    public Home findById(UUID id) { // ✓ Returns domain model
        // ...
    }
}

// Domain has no dependencies - OK
public class Home {
    // ✓ No dependencies on other layers
}
```

## Domain-Driven Design with Tubing

Domain-Driven Design (DDD) is a software development approach that focuses on modeling the business domain accurately. Tubing's IoC container naturally supports DDD patterns.

### Bounded Contexts

Divide your plugin into bounded contexts - distinct areas of functionality with their own models and logic.

**Example: Large Economy Plugin**
```
com.example.economy/
├── banking/                          # Banking bounded context
│   ├── BankAccount.java             # Domain model
│   ├── BankAccountRepository.java   # Persistence
│   ├── BankingService.java          # Business logic
│   └── BankCommand.java             # Presentation
├── trading/                          # Trading bounded context
│   ├── Trade.java
│   ├── TradeRepository.java
│   ├── TradingService.java
│   └── TradeCommand.java
├── currency/                         # Currency bounded context
│   ├── Currency.java
│   ├── CurrencyRepository.java
│   ├── CurrencyService.java
│   └── CurrencyCommand.java
└── shared/                           # Shared kernel
    ├── Money.java                    # Shared value object
    └── TransactionId.java            # Shared type
```

Each bounded context is independent and can evolve separately. They communicate through well-defined interfaces.

### Aggregates

An aggregate is a cluster of domain objects treated as a single unit. The aggregate root is the only entry point.

```java
// Aggregate Root
public class PlayerAccount {
    private final UUID playerId;
    private final List<Transaction> transactions; // Part of aggregate
    private Money balance; // Part of aggregate

    // Only the root is public - controls access to children
    public PlayerAccount(UUID playerId, Money initialBalance) {
        this.playerId = playerId;
        this.balance = initialBalance;
        this.transactions = new ArrayList<>();
    }

    // Business logic encapsulated in aggregate
    public void deposit(Money amount, String reason) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Cannot deposit negative amount");
        }

        this.balance = this.balance.add(amount);
        this.transactions.add(new Transaction(
            TransactionType.DEPOSIT,
            amount,
            reason,
            Instant.now()
        ));
    }

    public void withdraw(Money amount, String reason) throws InsufficientFundsException {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Cannot withdraw negative amount");
        }

        if (this.balance.isLessThan(amount)) {
            throw new InsufficientFundsException(amount, this.balance);
        }

        this.balance = this.balance.subtract(amount);
        this.transactions.add(new Transaction(
            TransactionType.WITHDRAWAL,
            amount,
            reason,
            Instant.now()
        ));
    }

    // Getters provide read-only access
    public Money getBalance() {
        return balance; // Money is immutable
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }
}

// Part of aggregate - not accessible directly
class Transaction {
    private final TransactionType type;
    private final Money amount;
    private final String reason;
    private final Instant timestamp;

    Transaction(TransactionType type, Money amount, String reason, Instant timestamp) {
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    // Getters...
}

// Repository works with aggregate root only
public interface PlayerAccountRepository {
    PlayerAccount findById(UUID playerId);
    void save(PlayerAccount account); // Saves entire aggregate
}
```

**Benefits:**
- Enforces business rules
- Maintains consistency boundaries
- Simplifies transactions
- Clear ownership

### Repositories as Aggregate Roots

One repository per aggregate root:

```java
// One repository per aggregate
@IocBean
public class PlayerAccountRepository {
    public PlayerAccount findById(UUID playerId) {
        // Loads entire aggregate including transactions
    }

    public void save(PlayerAccount account) {
        // Saves entire aggregate atomically
    }
}

// NOT separate repositories for children
// ✗ public class TransactionRepository { }  // WRONG!
```

### Domain Services

When business logic doesn't naturally belong to any entity, use domain services:

```java
// Domain service - stateless business logic
@IocBean
public class TransferService {
    private final PlayerAccountRepository accountRepository;

    public TransferService(PlayerAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void transfer(UUID fromPlayerId, UUID toPlayerId, Money amount) {
        // Load both aggregates
        PlayerAccount fromAccount = accountRepository.findById(fromPlayerId);
        PlayerAccount toAccount = accountRepository.findById(toPlayerId);

        // Perform transfer using aggregate methods
        fromAccount.withdraw(amount, "Transfer to " + toPlayerId);
        toAccount.deposit(amount, "Transfer from " + fromPlayerId);

        // Save both aggregates
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
}
```

### Value Objects

Value objects represent concepts without identity:

```java
public class Money {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Amount and currency required");
        }
        this.amount = amount;
        this.currency = currency;
    }

    // Value objects are immutable - operations return new instances
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot subtract different currencies");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isLessThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount) < 0;
    }

    // Value objects use value-based equality
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}
```

**Benefits:**
- Encapsulate domain concepts
- Type safety
- Self-validating
- Immutable

## Multi-Module Architecture

For large plugins or cross-platform plugins, organize code into multiple Maven modules.

### Module Structure

```
my-plugin/
├── pom.xml                          # Parent POM
├── my-plugin-api/                   # Public API
│   ├── pom.xml
│   └── src/main/java/
│       └── com/example/api/
│           ├── IHomeService.java
│           ├── IWarpService.java
│           └── events/
│               ├── HomeCreateEvent.java
│               └── WarpCreateEvent.java
├── my-plugin-core/                  # Platform-independent logic
│   ├── pom.xml
│   └── src/main/java/
│       └── com/example/core/
│           ├── services/
│           │   ├── home/
│           │   └── warp/
│           ├── repositories/
│           ├── models/
│           └── config/
├── my-plugin-bukkit/                # Bukkit implementation
│   ├── pom.xml
│   └── src/main/java/
│       └── com/example/bukkit/
│           ├── MyBukkitPlugin.java
│           ├── commands/
│           ├── listeners/
│           ├── adapters/            # Platform adapters
│           └── gui/
├── my-plugin-bungee/                # BungeeCord implementation
│   ├── pom.xml
│   └── src/main/java/
│       └── com/example/bungee/
└── my-plugin-velocity/              # Velocity implementation
    ├── pom.xml
    └── src/main/java/
        └── com/example/velocity/
```

### Module Dependencies

```
my-plugin-api (standalone)
     ↑
my-plugin-core (depends on api)
     ↑
my-plugin-bukkit (depends on core)
my-plugin-bungee (depends on core)
my-plugin-velocity (depends on core)
```

### API Module

Defines public interfaces for other plugins to use:

```java
// my-plugin-api
package com.example.api;

public interface IHomeService {
    /**
     * Create a home for a player.
     *
     * @param playerId Player UUID
     * @param homeName Name of the home
     * @param location Home location
     * @return true if created successfully
     */
    boolean createHome(UUID playerId, String homeName, HomeLocation location);

    /**
     * Get all homes for a player.
     *
     * @param playerId Player UUID
     * @return List of homes
     */
    List<Home> getHomes(UUID playerId);

    /**
     * Delete a home.
     *
     * @param playerId Player UUID
     * @param homeName Name of home to delete
     * @return true if deleted successfully
     */
    boolean deleteHome(UUID playerId, String homeName);
}
```

### Core Module

Contains platform-independent business logic:

```java
// my-plugin-core
package com.example.core.services;

@IocBean
public class HomeServiceImpl implements IHomeService {
    private final HomeRepository repository;
    private final ValidationService validation;

    public HomeServiceImpl(HomeRepository repository, ValidationService validation) {
        this.repository = repository;
        this.validation = validation;
    }

    @Override
    public boolean createHome(UUID playerId, String homeName, HomeLocation location) {
        if (!validation.isValidHomeName(homeName)) {
            return false;
        }

        Home home = new Home(playerId, homeName, location);
        repository.save(home);
        return true;
    }

    // Other methods...
}
```

### Platform Modules

Platform-specific implementations and adapters:

```java
// my-plugin-bukkit
package com.example.bukkit.adapters;

@IocBean
public class BukkitPlayerAdapter {
    private final IHomeService homeService;

    public BukkitPlayerAdapter(IHomeService homeService) {
        this.homeService = homeService;
    }

    public void teleportToHome(Player player, String homeName) {
        List<Home> homes = homeService.getHomes(player.getUniqueId());
        Home home = homes.stream()
            .filter(h -> h.getName().equals(homeName))
            .findFirst()
            .orElse(null);

        if (home != null) {
            Location bukkitLocation = toBukkitLocation(home.getLocation());
            player.teleport(bukkitLocation);
        }
    }

    private Location toBukkitLocation(HomeLocation location) {
        return new Location(
            Bukkit.getWorld(location.getWorld()),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }
}
```

### Benefits of Multi-Module

1. **Clear Separation**: API, core logic, and platform code clearly separated
2. **Reusability**: Core logic shared across platforms
3. **API Stability**: Public API isolated from implementation changes
4. **Parallel Development**: Teams can work on different modules
5. **Testability**: Test core logic without platform dependencies

## API Design for Extensibility

Design your plugin to be extensible by other developers.

### Plugin API Pattern

```java
// Public API interface
public interface MyPluginAPI {
    IHomeService getHomeService();
    IWarpService getWarpService();
    IPlayerDataService getPlayerDataService();
}

// API implementation (not exposed)
@IocBean
class MyPluginAPIImpl implements MyPluginAPI {
    private final IHomeService homeService;
    private final IWarpService warpService;
    private final IPlayerDataService playerDataService;

    public MyPluginAPIImpl(IHomeService homeService,
                           IWarpService warpService,
                           IPlayerDataService playerDataService) {
        this.homeService = homeService;
        this.warpService = warpService;
        this.playerDataService = playerDataService;
    }

    @Override
    public IHomeService getHomeService() {
        return homeService;
    }

    @Override
    public IWarpService getWarpService() {
        return warpService;
    }

    @Override
    public IPlayerDataService getPlayerDataService() {
        return playerDataService;
    }
}

// Static accessor for other plugins
public class MyPluginProvider {
    private static MyPluginAPI instance;

    public static MyPluginAPI get() {
        if (instance == null) {
            throw new IllegalStateException("MyPlugin not loaded");
        }
        return instance;
    }

    static void setInstance(MyPluginAPI api) {
        instance = api;
    }
}

// Main plugin registers API
public class MyBukkitPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        MyPluginAPI api = getIocContainer().get(MyPluginAPIImpl.class);
        MyPluginProvider.setInstance(api);
    }

    @Override
    protected void disable() {
        MyPluginProvider.setInstance(null);
    }
}
```

### Event System for Extensions

Allow other plugins to hook into your plugin's operations:

```java
// Define events in API module
public class HomeCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final Home home;
    private boolean cancelled;

    public HomeCreatedEvent(UUID playerId, Home home) {
        this.playerId = playerId;
        this.home = home;
    }

    // Standard Bukkit event methods...

    public UUID getPlayerId() { return playerId; }
    public Home getHome() { return home; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}

// Fire events in your service
@IocBean
public class HomeService {
    private final Server server;

    public void createHome(UUID playerId, String name, HomeLocation location) {
        Home home = new Home(playerId, name, location);

        // Fire event - other plugins can intercept
        HomeCreatedEvent event = new HomeCreatedEvent(playerId, home);
        server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            repository.save(home);
        }
    }
}

// Other plugins can listen
public class MyExtension extends JavaPlugin implements Listener {
    @EventHandler
    public void onHomeCreated(HomeCreatedEvent event) {
        // React to home creation
        getLogger().info("Home created: " + event.getHome().getName());
    }
}
```

### Extension Points with Multi-Providers

Allow other plugins to register handlers:

```java
// Define extension interface
public interface HomeValidator {
    ValidationResult validate(UUID playerId, String homeName, HomeLocation location);
}

// Core service uses extensions
@IocBean
public class HomeService {
    private final List<HomeValidator> validators;

    public HomeService(@IocMulti(HomeValidator.class) List<HomeValidator> validators) {
        this.validators = validators;
    }

    public boolean createHome(UUID playerId, String name, HomeLocation location) {
        // Run all validators
        for (HomeValidator validator : validators) {
            ValidationResult result = validator.validate(playerId, name, location);
            if (!result.isValid()) {
                return false;
            }
        }

        // Create home...
        return true;
    }
}

// Built-in validator
@IocBean
@IocMultiProvider(HomeValidator.class)
class DefaultHomeValidator implements HomeValidator {
    @Override
    public ValidationResult validate(UUID playerId, String homeName, HomeLocation location) {
        if (homeName.length() > 16) {
            return ValidationResult.invalid("Name too long");
        }
        return ValidationResult.valid();
    }
}

// Extension plugin can add validators
@IocBean
@IocMultiProvider(HomeValidator.class)
public class RegionHomeValidator implements HomeValidator {
    @Override
    public ValidationResult validate(UUID playerId, String homeName, HomeLocation location) {
        // Check if location is in protected region
        if (isInProtectedRegion(location)) {
            return ValidationResult.invalid("Cannot set home in protected region");
        }
        return ValidationResult.valid();
    }
}
```

## Package Organization Strategies

### By Layer

Organize by architectural layer:

```
com.example.myplugin/
├── commands/              # Presentation layer
├── listeners/
├── gui/
├── services/              # Service layer
├── repositories/          # Repository layer
└── models/                # Domain layer
```

**Best for:** Small to medium plugins with clear layering

### By Feature (Vertical Slices)

Organize by feature or bounded context:

```
com.example.myplugin/
├── homes/                 # Home feature - complete vertical slice
│   ├── HomeCommand.java
│   ├── HomeService.java
│   ├── HomeRepository.java
│   └── Home.java
├── warps/                 # Warp feature
│   ├── WarpCommand.java
│   ├── WarpService.java
│   ├── WarpRepository.java
│   └── Warp.java
└── shared/                # Shared infrastructure
    ├── database/
    └── messaging/
```

**Best for:** Large plugins with distinct features

### Hybrid Approach

Combine both strategies:

```
com.example.myplugin/
├── commands/
│   ├── homes/             # Feature grouping within layer
│   ├── warps/
│   └── admin/
├── services/
│   ├── homes/
│   ├── warps/
│   └── player/
├── repositories/
│   ├── HomeRepository.java
│   └── WarpRepository.java
├── models/
│   ├── Home.java
│   └── Warp.java
└── shared/
    ├── config/
    ├── database/
    └── messaging/
```

**Best for:** Medium to large plugins needing both structure and flexibility

## Architectural Patterns

### Hexagonal Architecture (Ports and Adapters)

Separate core business logic from external concerns:

```
┌──────────────────────────────────────────┐
│         External Systems                 │
│  (Bukkit, Database, File System, etc.)   │
└──────────────────────────────────────────┘
                    ↕
┌──────────────────────────────────────────┐
│            Adapters                      │
│  (Convert external data to domain)       │
└──────────────────────────────────────────┘
                    ↕
┌──────────────────────────────────────────┐
│             Ports                        │
│      (Interfaces/Contracts)              │
└──────────────────────────────────────────┘
                    ↕
┌──────────────────────────────────────────┐
│          Core Domain                     │
│    (Business Logic - Pure Java)          │
└──────────────────────────────────────────┘
```

**Implementation:**

```java
// Port (interface)
public interface PlayerDataPort {
    PlayerData loadPlayer(UUID playerId);
    void savePlayer(PlayerData data);
}

// Core domain uses port
@IocBean
public class PlayerService {
    private final PlayerDataPort playerDataPort;

    public PlayerService(PlayerDataPort playerDataPort) {
        this.playerDataPort = playerDataPort;
    }

    public void updatePlayer(UUID playerId, Consumer<PlayerData> updater) {
        PlayerData data = playerDataPort.loadPlayer(playerId);
        updater.accept(data);
        playerDataPort.savePlayer(data);
    }
}

// Adapter implements port
@IocBean
public class DatabasePlayerAdapter implements PlayerDataPort {
    private final Database database;

    public DatabasePlayerAdapter(Database database) {
        this.database = database;
    }

    @Override
    public PlayerData loadPlayer(UUID playerId) {
        // Convert database row to domain model
        return database.queryOne("SELECT * FROM players WHERE id = ?",
            PlayerData.class, playerId);
    }

    @Override
    public void savePlayer(PlayerData data) {
        // Convert domain model to database row
        database.execute("INSERT INTO players...", data);
    }
}
```

**Benefits:**
- Core logic independent of platform
- Easy to swap implementations
- Highly testable
- Platform-agnostic core

### CQRS (Command Query Responsibility Segregation)

Separate read and write operations:

```java
// Commands (writes) - modify state
public class CreateHomeCommand {
    private final UUID playerId;
    private final String homeName;
    private final HomeLocation location;

    public CreateHomeCommand(UUID playerId, String homeName, HomeLocation location) {
        this.playerId = playerId;
        this.homeName = homeName;
        this.location = location;
    }

    // Getters...
}

// Command handler
@IocBean
public class CreateHomeCommandHandler {
    private final HomeRepository repository;
    private final EventBus eventBus;

    public void handle(CreateHomeCommand command) {
        Home home = new Home(
            command.getPlayerId(),
            command.getHomeName(),
            command.getLocation()
        );

        repository.save(home);
        eventBus.publish(new HomeCreatedEvent(home));
    }
}

// Queries (reads) - don't modify state
public class GetPlayerHomesQuery {
    private final UUID playerId;

    public GetPlayerHomesQuery(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}

// Query handler - can use optimized read models
@IocBean
public class GetPlayerHomesQueryHandler {
    private final HomeReadRepository readRepository;

    public List<HomeDTO> handle(GetPlayerHomesQuery query) {
        // Use read-optimized repository
        return readRepository.findByOwner(query.getPlayerId());
    }
}

// Service layer dispatches commands/queries
@IocBean
public class HomeService {
    private final CreateHomeCommandHandler createHandler;
    private final GetPlayerHomesQueryHandler queryHandler;

    public void createHome(UUID playerId, String name, HomeLocation location) {
        createHandler.handle(new CreateHomeCommand(playerId, name, location));
    }

    public List<HomeDTO> getHomes(UUID playerId) {
        return queryHandler.handle(new GetPlayerHomesQuery(playerId));
    }
}
```

**Benefits:**
- Optimized read and write models
- Clear separation of concerns
- Scalable for high-traffic servers
- Supports event sourcing

### Event-Driven Architecture

Use events for decoupling:

```java
// Domain event
public class PlayerJoinedEvent {
    private final UUID playerId;
    private final Instant timestamp;

    public PlayerJoinedEvent(UUID playerId) {
        this.playerId = playerId;
        this.timestamp = Instant.now();
    }

    // Getters...
}

// Event bus
@IocBean
public class EventBus {
    private final List<EventHandler> handlers;

    public EventBus(@IocMulti(EventHandler.class) List<EventHandler> handlers) {
        this.handlers = handlers;
    }

    public void publish(Object event) {
        for (EventHandler handler : handlers) {
            if (handler.canHandle(event)) {
                handler.handle(event);
            }
        }
    }
}

// Event handler interface
public interface EventHandler {
    boolean canHandle(Object event);
    void handle(Object event);
}

// Specific handlers
@IocBean
@IocMultiProvider(EventHandler.class)
public class WelcomeMessageHandler implements EventHandler {
    @Override
    public boolean canHandle(Object event) {
        return event instanceof PlayerJoinedEvent;
    }

    @Override
    public void handle(Object event) {
        PlayerJoinedEvent joinEvent = (PlayerJoinedEvent) event;
        // Send welcome message
    }
}

@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerDataLoaderHandler implements EventHandler {
    private final PlayerRepository repository;

    @Override
    public boolean canHandle(Object event) {
        return event instanceof PlayerJoinedEvent;
    }

    @Override
    public void handle(Object event) {
        PlayerJoinedEvent joinEvent = (PlayerJoinedEvent) event;
        // Load player data
        repository.loadOrCreate(joinEvent.getPlayerId());
    }
}
```

**Benefits:**
- Loose coupling between components
- Easy to add new functionality
- Event history for debugging
- Supports reactive patterns

## Best Practices for Large Projects

### 1. Define Clear Module Boundaries

```
core/           # Business logic - no platform dependencies
bukkit/         # Bukkit-specific code
api/            # Public interfaces
```

### 2. Use Dependency Injection Everywhere

Avoid static managers and singletons:

```java
// BAD
public class PluginManager {
    private static PluginManager instance;

    public static PluginManager getInstance() {
        return instance;
    }
}

// GOOD
@IocBean
public class PluginManager {
    // Injected via constructor
}
```

### 3. Write Tests

```java
public class HomeServiceTest {
    private HomeService service;
    private HomeRepository mockRepository;

    @Before
    public void setup() {
        mockRepository = mock(HomeRepository.class);
        service = new HomeService(mockRepository);
    }

    @Test
    public void testCreateHome() {
        // Test logic...
    }
}
```

### 4. Document Your Architecture

Create an architecture decision record (ADR) for major decisions:

```markdown
# ADR-001: Use Repository Pattern for Data Access

## Context
We need a consistent way to access player data from various sources (database, cache, file).

## Decision
We will use the Repository pattern with interfaces for all data access.

## Consequences
- All services depend on repository interfaces
- Easy to swap data sources
- Clear separation between business logic and persistence
- Additional abstraction layer to maintain
```

### 5. Version Your API

```java
// v1 API
package com.example.api.v1;

public interface IHomeService {
    void createHome(UUID playerId, String name, Location location);
}

// v2 API - breaking changes in new package
package com.example.api.v2;

public interface IHomeService {
    CompletableFuture<Home> createHome(UUID playerId, String name, Location location);
}
```

### 6. Use Feature Toggles

```java
@IocBean(conditionalOnProperty = "features.homes.enabled")
public class HomeService {
    // Only loaded if feature enabled
}
```

### 7. Monitor and Log

```java
@IocBean
public class HomeService {
    private final Logger logger;

    public void createHome(Player player, String name, Location location) {
        logger.info("Creating home {} for player {}", name, player.getName());

        try {
            // Create home...
            logger.info("Successfully created home {}", name);
        } catch (Exception e) {
            logger.error("Failed to create home {}", name, e);
            throw e;
        }
    }
}
```

## Anti-Patterns to Avoid

### God Service

```java
// BAD - does everything
@IocBean
public class PluginManager {
    public void handleCommand() { }
    public void handleEvent() { }
    public void saveToDatabase() { }
    public void loadConfig() { }
    public void sendMessage() { }
    // 50 more methods...
}

// GOOD - focused services
@IocBean
public class HomeService {
    // Only home-related logic
}

@IocBean
public class PlayerService {
    // Only player-related logic
}
```

### Anemic Domain Model

```java
// BAD - just getters/setters
public class Home {
    private String name;
    private Location location;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // No behavior!
}

// Service has all the logic
@IocBean
public class HomeService {
    public void teleportToHome(Player player, Home home) {
        if (home.getLocation() != null) {
            player.teleport(home.getLocation());
        }
    }
}

// GOOD - rich domain model
public class Home {
    private final String name;
    private final HomeLocation location;

    public Home(String name, HomeLocation location) {
        validateName(name);
        validateLocation(location);
        this.name = name;
        this.location = location;
    }

    public boolean canTeleport() {
        return location != null && location.isValid();
    }

    private void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Home name required");
        }
    }
}
```

### Circular Dependencies

```java
// BAD
@IocBean
public class ServiceA {
    public ServiceA(ServiceB b) { } // A depends on B
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA a) { } // B depends on A - CIRCULAR!
}

// GOOD - extract shared logic
@IocBean
public class SharedService {
    // Common functionality
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

### Leaky Abstractions

```java
// BAD - repository exposes database details
public interface HomeRepository {
    ResultSet queryHomes(String sql); // Database leaking through!
}

// GOOD - repository hides implementation
public interface HomeRepository {
    List<Home> findByOwner(UUID ownerId); // Clean domain interface
}
```

## Next Steps

Now that you understand architectural patterns:

- Review [Project Structure](../getting-started/Project-Structure.md) for file organization
- Learn [Dependency Injection](../core/Dependency-Injection.md) patterns in depth
- Study [Multi-Implementation](../core/Multi-Implementation.md) for extensibility
- Explore [Bean Providers](../core/Bean-Providers.md) for complex object creation
- Read about [Configuration Files](../core/Configuration-Files.md) management

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container internals and lifecycle
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - How beans are created
- [Conditional Beans](../core/Conditional-Beans.md) - Feature toggles
- [Testing Best Practices](Testing.md) - Testing strategies
- [Common Patterns](Common-Patterns.md) - Reusable design patterns
