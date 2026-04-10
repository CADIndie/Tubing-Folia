# Dependency Injection

Dependency Injection (DI) is the cornerstone of Tubing's design philosophy. It eliminates tight coupling, reduces boilerplate, and makes your code testable and maintainable. This guide covers everything you need to know about how Tubing handles dependency injection.

## What is Dependency Injection?

Dependency Injection is a design pattern where objects receive their dependencies from external sources rather than creating them internally. Instead of a class instantiating its own dependencies, those dependencies are "injected" from the outside.

### Without Dependency Injection

```java
public class HomeService {
    // Tight coupling - creates its own dependencies
    private final PlayerRepository repository = new PlayerRepository();
    private final PermissionService permissions = new PermissionService();

    public void createHome(Player player, String name, Location location) {
        if (permissions.hasPermission(player, "homes.create")) {
            repository.save(new Home(player.getUniqueId(), name, location));
        }
    }
}
```

**Problems:**
- Hard to test (can't mock dependencies)
- Tight coupling to concrete implementations
- No flexibility to change implementations
- Dependencies create their own dependencies (cascade of instantiation)
- Difficult to reuse or refactor

### With Dependency Injection (Tubing)

```java
@IocBean
public class HomeService {
    // Dependencies injected via constructor
    private final PlayerRepository repository;
    private final PermissionService permissions;

    public HomeService(PlayerRepository repository, PermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    public void createHome(Player player, String name, Location location) {
        if (permissions.hasPermission(player, "homes.create")) {
            repository.save(new Home(player.getUniqueId(), name, location));
        }
    }
}
```

**Benefits:**
- Easy to test (inject mocks)
- Loose coupling (depend on interfaces)
- Flexible implementations
- Dependencies managed by container
- Clear dependency requirements
- Single Responsibility Principle

## Constructor Injection

Tubing uses **constructor injection** as its primary and recommended DI mechanism. All dependencies are declared as constructor parameters and automatically resolved by the IoC container.

### Basic Constructor Injection

```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository;
    private final MessageService messageService;

    // Single constructor - dependencies automatically injected
    public PlayerService(PlayerRepository repository, MessageService messageService) {
        this.repository = repository;
        this.messageService = messageService;
    }

    public void savePlayer(Player player) {
        repository.save(player);
        messageService.send(player, "data.saved");
    }
}
```

**Key Points:**
- Annotate class with `@IocBean` to mark it as a managed bean
- Declare all dependencies as constructor parameters
- Container automatically finds and injects dependencies
- Dependencies are immutable (final fields)

### Multiple Dependencies

You can inject as many dependencies as needed:

```java
@IocBean
public class EconomyService {
    private final Database database;
    private final PlayerService playerService;
    private final PermissionService permissionService;
    private final MessageService messageService;
    private final EventBus eventBus;

    public EconomyService(Database database,
                         PlayerService playerService,
                         PermissionService permissionService,
                         MessageService messageService,
                         EventBus eventBus) {
        this.database = database;
        this.playerService = playerService;
        this.permissionService = permissionService;
        this.messageService = messageService;
        this.eventBus = eventBus;
    }
}
```

**Note:** If you have many dependencies, consider if your class is doing too much (violating Single Responsibility Principle).

### Mixed Injection (Constructor + Configuration)

Combine constructor injection with configuration property injection:

```java
@IocBean
public class HomeService {
    private final PlayerRepository repository;
    private final PermissionService permissions;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds;

    // Constructor for service dependencies
    public HomeService(PlayerRepository repository, PermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    public boolean canCreateHome(Player player) {
        int currentHomes = repository.countHomes(player);
        return currentHomes < maxHomes;
    }
}
```

**Pattern:**
- Service/component dependencies via constructor
- Configuration values via `@ConfigProperty` fields
- Keeps constructor focused on object dependencies

## Interface Injection for Loose Coupling

One of the most powerful aspects of DI is the ability to depend on abstractions (interfaces) rather than concrete implementations.

### Define Interface

```java
public interface PlayerRepository {
    PlayerData load(UUID playerId);
    void save(PlayerData data);
    void delete(UUID playerId);
}
```

### Implement Interface

```java
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    private final Database database;

    public DatabasePlayerRepository(Database database) {
        this.database = database;
    }

    @Override
    public PlayerData load(UUID playerId) {
        return database.query("SELECT * FROM players WHERE id = ?", playerId);
    }

    @Override
    public void save(PlayerData data) {
        database.execute("INSERT INTO players ...", data);
    }

    @Override
    public void delete(UUID playerId) {
        database.execute("DELETE FROM players WHERE id = ?", playerId);
    }
}
```

### Inject Interface

```java
@IocBean
public class PlayerService {
    private final PlayerRepository repository; // Interface, not implementation!

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }

    public void loadPlayer(UUID playerId) {
        PlayerData data = repository.load(playerId);
        // Use data...
    }
}
```

**How It Works:**
1. Tubing scans for beans implementing `PlayerRepository`
2. Finds `DatabasePlayerRepository` (only implementation)
3. Automatically injects it into `PlayerService`
4. `PlayerService` never knows about the concrete implementation

### Swapping Implementations

The power of interface injection becomes clear when you need to change implementations:

```java
// New implementation - no changes to PlayerService needed!
@IocBean
public class FilePlayerRepository implements PlayerRepository {
    @Override
    public PlayerData load(UUID playerId) {
        // Load from file instead of database
    }

    @Override
    public void save(PlayerData data) {
        // Save to file
    }

    @Override
    public void delete(UUID playerId) {
        // Delete file
    }
}
```

Simply remove `@IocBean` from `DatabasePlayerRepository` and add it to `FilePlayerRepository`. All classes using `PlayerRepository` automatically use the new implementation. No code changes needed!

### Testing with Interfaces

Interfaces make testing trivial:

```java
public class PlayerServiceTest {

    @Test
    public void testLoadPlayer() {
        // Create mock repository
        PlayerRepository mockRepo = mock(PlayerRepository.class);
        PlayerData expectedData = new PlayerData(...);
        when(mockRepo.load(any())).thenReturn(expectedData);

        // Inject mock via constructor
        PlayerService service = new PlayerService(mockRepo);

        // Test
        PlayerData result = service.loadPlayer(UUID.randomUUID());
        assertEquals(expectedData, result);
    }
}
```

No IoC container needed in tests - just pass mocks to the constructor!

## Why Tubing Uses Constructor Injection

Tubing exclusively uses constructor injection rather than field injection or setter injection. Here's why:

### Constructor vs Field Injection

**Field Injection (Tubing does NOT support):**
```java
// NOT SUPPORTED - don't do this
@IocBean
public class BadExample {
    @Inject // This won't work!
    private PlayerRepository repository;

    public void doSomething() {
        repository.save(...); // NPE risk!
    }
}
```

**Constructor Injection (Tubing's approach):**
```java
@IocBean
public class GoodExample {
    private final PlayerRepository repository;

    public GoodExample(PlayerRepository repository) {
        this.repository = repository;
    }

    public void doSomething() {
        repository.save(...); // Safe - guaranteed not null
    }
}
```

### Why Constructor Injection is Superior

**1. Immutability**
```java
@IocBean
public class HomeService {
    private final PlayerRepository repository; // Final - cannot be changed

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }
}
```
Fields can be `final`, ensuring dependencies never change after construction.

**2. Explicit Dependencies**
```java
// Dependencies are clear from constructor signature
public HomeService(PlayerRepository repository,
                  PermissionService permissions,
                  MessageService messages) {
    // Constructor shows exactly what this class needs
}
```

**3. Testability**
```java
// Easy to test - just call constructor with mocks
@Test
public void testHomeService() {
    PlayerRepository mockRepo = mock(PlayerRepository.class);
    PermissionService mockPerms = mock(PermissionService.class);
    MessageService mockMessages = mock(MessageService.class);

    // No framework needed - just plain Java
    HomeService service = new HomeService(mockRepo, mockPerms, mockMessages);
}
```

**4. No Null References**
```java
@IocBean
public class SafeService {
    private final Database database;

    public SafeService(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }

    public void query() {
        database.execute(...); // Never null - validated at construction
    }
}
```
Object is never in an invalid state. All dependencies present before object is used.

**5. Circular Dependency Detection**
```java
// Compiler and container can detect circular dependencies at startup
@IocBean
public class ServiceA {
    public ServiceA(ServiceB b) { } // ServiceA needs ServiceB
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA a) { } // ServiceB needs ServiceA - CIRCULAR!
}
```
Tubing will fail fast on startup with a clear error, preventing runtime issues.

### Limitations of Field/Setter Injection

**Field Injection Problems:**
- Can't make fields `final` (mutability)
- Dependencies not obvious from class signature
- Hard to test (need reflection or framework)
- Object can be in invalid state (nulls)
- No compile-time checking
- Can't enforce required dependencies

**Setter Injection Problems:**
- Optional dependencies unclear
- Object lifecycle complex
- Can be called multiple times
- Mutability issues
- Testing complexity

## Automatic Dependency Resolution

Tubing automatically resolves and injects all dependencies when creating beans. Understanding how this works helps you design effective class hierarchies.

### Resolution Process

1. **Scan Phase**: Tubing scans your package for `@IocBean` classes
2. **Dependency Graph**: Builds a graph of class dependencies
3. **Ordering**: Determines creation order based on dependencies
4. **Instantiation**: Creates beans in correct order
5. **Injection**: Injects dependencies via constructor

### Example: Multi-Level Dependencies

```java
// Level 1 - No dependencies
@IocBean
public class Database {
    public Database() {
        // Initialize connection pool
    }
}

// Level 2 - Depends on Database
@IocBean
public class PlayerRepository {
    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }
}

// Level 3 - Depends on Level 2
@IocBean
public class PlayerService {
    private final PlayerRepository repository;

    public PlayerService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Level 4 - Depends on Level 3
@IocBean
@IocBukkitCommandHandler("player")
public class PlayerCommand {
    private final PlayerService playerService;

    public PlayerCommand(PlayerService playerService) {
        this.playerService = playerService;
    }
}
```

**Creation Order:**
1. `Database` (no dependencies)
2. `PlayerRepository` (needs Database)
3. `PlayerService` (needs PlayerRepository)
4. `PlayerCommand` (needs PlayerService)

Tubing automatically determines the correct order. You don't need to worry about it!

### Complex Dependency Graphs

```java
@IocBean
public class ServiceA {
    public ServiceA(ServiceB b, ServiceC c) { }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceD d) { }
}

@IocBean
public class ServiceC {
    public ServiceC(ServiceD d, ServiceE e) { }
}

@IocBean
public class ServiceD {
    // No dependencies
}

@IocBean
public class ServiceE {
    // No dependencies
}
```

**Resolution:**
1. Create `ServiceD` (no deps)
2. Create `ServiceE` (no deps)
3. Create `ServiceB` (needs D)
4. Create `ServiceC` (needs D and E)
5. Create `ServiceA` (needs B and C)

The container handles this automatically, regardless of package structure or scanning order.

### Configuration Injection During Resolution

Configuration properties are injected after construction:

```java
@IocBean
public class HomeService {
    private final PlayerRepository repository;

    @ConfigProperty("homes.max-homes")
    private int maxHomes; // Injected after constructor runs

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
        // maxHomes is NOT available here yet!
    }

    // maxHomes is available in all methods
    public boolean canCreateHome(Player player) {
        return repository.countHomes(player) < maxHomes; // Available now
    }
}
```

**Lifecycle:**
1. Constructor called with dependencies
2. Configuration properties injected
3. Bean ready for use

## Circular Dependency Detection

Circular dependencies occur when two or more beans depend on each other, creating a dependency cycle. Tubing detects these at startup and fails fast.

### What is a Circular Dependency?

```java
// Direct circular dependency
@IocBean
public class ServiceA {
    private final ServiceB serviceB;

    public ServiceA(ServiceB serviceB) { // ServiceA needs ServiceB
        this.serviceB = serviceB;
    }
}

@IocBean
public class ServiceB {
    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) { // ServiceB needs ServiceA - CYCLE!
        this.serviceA = serviceA;
    }
}
```

**Problem:** Cannot create ServiceA without ServiceB, and cannot create ServiceB without ServiceA.

### Indirect Circular Dependencies

```java
// A → B → C → A
@IocBean
public class ServiceA {
    public ServiceA(ServiceB b) { }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceC c) { }
}

@IocBean
public class ServiceC {
    public ServiceC(ServiceA a) { } // Circular!
}
```

Tubing detects both direct and indirect circular dependencies.

### Detection and Errors

When Tubing detects a circular dependency, it throws an `IocException` at startup:

```
IocException: Cannot instantiate bean with type com.example.ServiceA.
Circular dependency detected: ServiceA -> ServiceB -> ServiceA
```

**This is a feature, not a bug!** It prevents runtime issues and forces you to fix the design problem.

### Resolving Circular Dependencies

Circular dependencies indicate a design problem. Here are solutions:

#### 1. Introduce an Intermediary

```java
// Before: A depends on B, B depends on A
@IocBean
public class ServiceA {
    private final ServiceB serviceB;
    public ServiceA(ServiceB b) { this.serviceB = b; }
}

@IocBean
public class ServiceB {
    private final ServiceA serviceA;
    public ServiceB(ServiceA a) { this.serviceA = a; }
}

// After: Extract common logic to new service
@IocBean
public class SharedService {
    // Common functionality used by both A and B
}

@IocBean
public class ServiceA {
    private final SharedService shared;
    public ServiceA(SharedService shared) { this.shared = shared; }
}

@IocBean
public class ServiceB {
    private final SharedService shared;
    public ServiceB(SharedService shared) { this.shared = shared; }
}
```

#### 2. Use Event-Driven Communication

```java
// Instead of direct dependency, use events
@IocBean
public class ServiceA {
    private final EventBus eventBus;

    public ServiceA(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void doSomething() {
        // Fire event instead of calling ServiceB directly
        eventBus.fire(new SomethingHappenedEvent());
    }
}

@IocBean
@IocBukkitListener
public class ServiceB implements Listener {
    @EventHandler
    public void onSomethingHappened(SomethingHappenedEvent event) {
        // React to event
    }
}
```

#### 3. Refactor Responsibilities

Often circular dependencies indicate classes are doing too much:

```java
// Before: Circular dependency
@IocBean
public class UserService {
    private final OrderService orderService;

    public void createUser(User user) {
        // Create user, then create default order
        orderService.createDefaultOrder(user);
    }
}

@IocBean
public class OrderService {
    private final UserService userService;

    public void createOrder(Order order) {
        // Validate user exists
        userService.getUser(order.getUserId());
    }
}

// After: Extract validation to separate service
@IocBean
public class ValidationService {
    public void validateUser(UUID userId) { }
}

@IocBean
public class UserService {
    public void createUser(User user) { }
}

@IocBean
public class OrderService {
    private final ValidationService validation;

    public void createOrder(Order order) {
        validation.validateUser(order.getUserId());
    }
}
```

## Dependency Injection Best Practices

### 1. Depend on Interfaces

```java
// Good - depends on abstraction
@IocBean
public class HomeService {
    private final PlayerRepository repository; // Interface

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - depends on implementation
@IocBean
public class HomeService {
    private final DatabasePlayerRepository repository; // Concrete class

    public HomeService(DatabasePlayerRepository repository) {
        this.repository = repository;
    }
}
```

**Why:** Interfaces enable flexibility, testing, and future changes.

### 2. Keep Constructors Simple

```java
// Good - constructor only assigns dependencies
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database; // Just assignment
    }
}

// Bad - constructor does work
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;

        // DON'T DO THIS
        database.connect();
        database.createTables();
        loadAllPlayers();
    }
}
```

**Why:** Complex constructors make testing difficult and can cause initialization issues.

### 3. Avoid Too Many Dependencies

```java
// Warning sign - too many dependencies
@IocBean
public class GodService {
    public GodService(ServiceA a, ServiceB b, ServiceC c, ServiceD d,
                     ServiceE e, ServiceF f, ServiceG g, ServiceH h) {
        // 8+ dependencies suggests class does too much
    }
}
```

**Solution:** Split into smaller, focused classes:

```java
@IocBean
public class UserManagementService {
    private final UserService userService;
    private final AuthenticationService authService;

    public UserManagementService(UserService userService,
                                AuthenticationService authService) {
        this.userService = userService;
        this.authService = authService;
    }
}
```

### 4. Use Final Fields

```java
// Good - immutable dependencies
@IocBean
public class HomeService {
    private final PlayerRepository repository;

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }
}

// Bad - mutable dependencies
@IocBean
public class HomeService {
    private PlayerRepository repository; // Not final!

    public HomeService(PlayerRepository repository) {
        this.repository = repository;
    }

    // Why would you ever do this?
    public void setRepository(PlayerRepository repository) {
        this.repository = repository;
    }
}
```

**Why:** Immutability prevents bugs and makes code easier to reason about.

### 5. One Constructor Only

```java
// Good - single constructor
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;
    }
}

// Bad - multiple constructors (Tubing will fail!)
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        this.database = database;
    }

    public PlayerService() { // ERROR!
        this.database = null;
    }
}
```

**Why:** Tubing requires exactly one constructor per bean for unambiguous dependency resolution.

### 6. Constructor Parameter Order

```java
// Good - logical grouping
@IocBean
public class HomeService {
    public HomeService(
        // Required services first
        PlayerRepository repository,
        PermissionService permissions,
        // Then utility services
        MessageService messages,
        ValidationService validation
    ) { }
}
```

**Convention:** Order dependencies by importance or logical grouping for readability.

### 7. Validate Dependencies

```java
@IocBean
public class PlayerService {
    private final Database database;

    public PlayerService(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }
}
```

**Note:** In practice, Tubing never injects null, but validation can help during manual testing.

## Designing Classes for Constructor Injection

### Service Layer Example

```java
// Domain model - no DI needed
public class Home {
    private final UUID ownerId;
    private final String name;
    private final Location location;

    public Home(UUID ownerId, String name, Location location) {
        this.ownerId = ownerId;
        this.name = name;
        this.location = location;
    }

    // Getters...
}

// Repository interface - defines contract
public interface HomeRepository {
    Home findByOwnerAndName(UUID ownerId, String name);
    List<Home> findAllByOwner(UUID ownerId);
    void save(Home home);
    void delete(Home home);
}

// Repository implementation - bean
@IocBean
public class DatabaseHomeRepository implements HomeRepository {
    private final Database database;

    public DatabaseHomeRepository(Database database) {
        this.database = database;
    }

    @Override
    public Home findByOwnerAndName(UUID ownerId, String name) {
        // Implementation...
    }

    // Other methods...
}

// Service layer - orchestrates business logic
@IocBean
public class HomeService {
    private final HomeRepository repository;
    private final PermissionService permissions;
    private final MessageService messages;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    public HomeService(HomeRepository repository,
                      PermissionService permissions,
                      MessageService messages) {
        this.repository = repository;
        this.permissions = permissions;
        this.messages = messages;
    }

    public void createHome(Player player, String name, Location location) {
        // Validation
        if (!permissions.hasPermission(player, "homes.create")) {
            messages.sendError(player, "homes.no-permission");
            return;
        }

        // Business logic
        List<Home> existingHomes = repository.findAllByOwner(player.getUniqueId());
        if (existingHomes.size() >= maxHomes) {
            messages.sendError(player, "homes.max-reached");
            return;
        }

        // Execute
        Home home = new Home(player.getUniqueId(), name, location);
        repository.save(home);
        messages.sendSuccess(player, "homes.created", name);
    }
}

// Command handler - thin wrapper
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand {
    private final HomeService homeService;

    public SetHomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;
        String homeName = args.length > 0 ? args[0] : "default";

        homeService.createHome(player, homeName, player.getLocation());
        return true;
    }
}
```

**Architecture:**
- **Models**: Plain objects, no DI
- **Repositories**: Handle data access, injected dependencies
- **Services**: Business logic, injected repositories and utilities
- **Commands**: Thin wrappers, inject services

### Testing the Design

```java
public class HomeServiceTest {

    private HomeService homeService;
    private HomeRepository mockRepository;
    private PermissionService mockPermissions;
    private MessageService mockMessages;

    @Before
    public void setup() {
        // Create mocks
        mockRepository = mock(HomeRepository.class);
        mockPermissions = mock(PermissionService.class);
        mockMessages = mock(MessageService.class);

        // Inject via constructor - no IoC needed!
        homeService = new HomeService(mockRepository, mockPermissions, mockMessages);

        // Set config property manually for test
        ReflectionTestUtils.setField(homeService, "maxHomes", 5);
    }

    @Test
    public void testCreateHome_Success() {
        // Setup
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockPermissions.hasPermission(player, "homes.create")).thenReturn(true);
        when(mockRepository.findAllByOwner(any())).thenReturn(Collections.emptyList());

        // Execute
        homeService.createHome(player, "home1", mock(Location.class));

        // Verify
        verify(mockRepository).save(any(Home.class));
        verify(mockMessages).sendSuccess(eq(player), eq("homes.created"), eq("home1"));
    }

    @Test
    public void testCreateHome_NoPermission() {
        Player player = mock(Player.class);
        when(mockPermissions.hasPermission(player, "homes.create")).thenReturn(false);

        homeService.createHome(player, "home1", mock(Location.class));

        verify(mockRepository, never()).save(any());
        verify(mockMessages).sendError(player, "homes.no-permission");
    }

    @Test
    public void testCreateHome_MaxHomesReached() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(mockPermissions.hasPermission(player, "homes.create")).thenReturn(true);

        // Player already has 5 homes
        List<Home> existingHomes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            existingHomes.add(new Home(playerId, "home" + i, mock(Location.class)));
        }
        when(mockRepository.findAllByOwner(playerId)).thenReturn(existingHomes);

        homeService.createHome(player, "home6", mock(Location.class));

        verify(mockRepository, never()).save(any());
        verify(mockMessages).sendError(player, "homes.max-reached");
    }
}
```

**Perfect testability** thanks to constructor injection!

## Common Patterns

### Pattern: Service + Repository

```java
// Repository handles data
@IocBean
public interface PlayerDataRepository {
    PlayerData load(UUID playerId);
    void save(PlayerData data);
}

// Service handles business logic
@IocBean
public class PlayerDataService {
    private final PlayerDataRepository repository;

    public PlayerDataService(PlayerDataRepository repository) {
        this.repository = repository;
    }
}
```

### Pattern: Facade Service

```java
// Simplifies complex subsystem
@IocBean
public class PlayerManagementFacade {
    private final PlayerDataService dataService;
    private final PlayerStatsService statsService;
    private final PlayerPermissionService permissionService;

    public PlayerManagementFacade(PlayerDataService dataService,
                                 PlayerStatsService statsService,
                                 PlayerPermissionService permissionService) {
        this.dataService = dataService;
        this.statsService = statsService;
        this.permissionService = permissionService;
    }

    public void setupNewPlayer(Player player) {
        dataService.createPlayerData(player);
        statsService.initializeStats(player);
        permissionService.applyDefaultPermissions(player);
    }
}
```

### Pattern: Strategy Injection

```java
// Different strategies for same operation
public interface BackupStrategy {
    void backup(PlayerData data);
}

@IocBean
public class LocalBackupStrategy implements BackupStrategy {
    public void backup(PlayerData data) { /* local file */ }
}

@IocBean
public class CloudBackupStrategy implements BackupStrategy {
    public void backup(PlayerData data) { /* cloud storage */ }
}

// Service uses strategy
@IocBean
public class BackupService {
    private final BackupStrategy strategy;

    public BackupService(BackupStrategy strategy) {
        this.strategy = strategy; // Tubing injects chosen implementation
    }
}
```

### Pattern: Chain of Responsibility

```java
public interface ValidationHandler {
    boolean validate(Home home);
}

@IocBean
public class NameValidator implements ValidationHandler {
    public boolean validate(Home home) {
        return home.getName().matches("[a-zA-Z0-9]+");
    }
}

@IocBean
public class LocationValidator implements ValidationHandler {
    public boolean validate(Home home) {
        return home.getLocation().getWorld() != null;
    }
}

@IocBean
public class HomeValidationService {
    private final List<ValidationHandler> validators;

    public HomeValidationService(@IocMulti(ValidationHandler.class) List<ValidationHandler> validators) {
        this.validators = validators;
    }

    public boolean validateAll(Home home) {
        return validators.stream().allMatch(v -> v.validate(home));
    }
}
```

## Next Steps

Now that you understand dependency injection:

- Learn about [Bean Lifecycle](Bean-Lifecycle.md) to understand when beans are created
- Explore [Bean Providers](Bean-Providers.md) for factory method patterns
- Read [Multi-Implementation](Multi-Implementation.md) for managing multiple implementations
- Check [Configuration Injection](Configuration-Injection.md) for injecting config values
- Review [Testing Best Practices](../best-practices/Testing.md) for testing strategies

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first DI-based plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize DI components
- [IoC Container](IoC-Container.md) - Container internals and lifecycle
- [Bean Registration](Bean-Registration.md) - Register beans with `@IocBean`
