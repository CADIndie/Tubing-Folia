# Testing

Testing is a critical part of building maintainable Minecraft plugins. Tubing's dependency injection architecture makes testing remarkably simple by enabling constructor injection and dependency mocking. This guide covers everything you need to know about testing Tubing-based plugins.

## Overview

Tubing's design philosophy centers on testability:

- **Constructor Injection**: All dependencies are passed through constructors, making mocking trivial
- **Interface-Based Design**: Depend on abstractions, not concrete implementations
- **No Static Dependencies**: No singleton managers or static state to mock
- **Pure Business Logic**: Services contain testable business logic separate from framework concerns
- **IoC-Free Testing**: Most tests don't need the IoC container at all

## Why Testing with Tubing is Easy

### Traditional Plugin Testing (Without DI)

```java
// Hard to test - static dependencies
public class PlayerService {
    public void savePlayer(Player player) {
        Database db = DatabaseManager.getInstance(); // Static singleton
        Config config = MyPlugin.getInstance().getConfig(); // Plugin instance
        db.save(player, config.getString("table-name"));
    }
}

// Testing requires complex mocking of static methods
@Test
public void testSavePlayer() {
    // How do you mock DatabaseManager.getInstance()?
    // How do you mock MyPlugin.getInstance()?
    // Very difficult!
}
```

### Tubing Approach (With DI)

```java
// Easy to test - constructor injection
@IocBean
public class PlayerService {
    private final Database database;
    private final String tableName;

    public PlayerService(Database database,
                        @ConfigProperty("table-name") String tableName) {
        this.database = database;
        this.tableName = tableName;
    }

    public void savePlayer(Player player) {
        database.save(player, tableName);
    }
}

// Testing is trivial - just pass mocks to constructor
@Test
public void testSavePlayer() {
    Database mockDb = mock(Database.class);
    PlayerService service = new PlayerService(mockDb, "players");

    Player mockPlayer = mock(Player.class);
    service.savePlayer(mockPlayer);

    verify(mockDb).save(mockPlayer, "players");
}
```

**The difference:** Constructor injection makes dependencies explicit and mockable. No framework magic needed in tests!

## Unit Testing Basics

### Test Dependencies

Add JUnit 5 and Mockito to your `pom.xml`:

```xml
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-engine</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>

    <!-- Bukkit/Spigot test mocks -->
    <dependency>
        <groupId>com.github.seeseemelk</groupId>
        <artifactId>MockBukkit-v1.20</artifactId>
        <version>3.9.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Basic Test Structure

```java
package com.example.myplugin.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository mockRepository;

    @Mock
    private MessageService mockMessages;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        // Create service with mocked dependencies
        playerService = new PlayerService(mockRepository, mockMessages);
    }

    @Test
    void testLoadPlayer() {
        // Test implementation
    }
}
```

**Key components:**
- `@ExtendWith(MockitoExtension.class)`: Enables Mockito annotations
- `@Mock`: Creates mock objects automatically
- `@BeforeEach`: Runs before each test to set up fresh state
- Constructor injection: Pass mocks to service constructor

## Testing Services

Services contain your business logic and should be thoroughly tested.

### Simple Service Test

```java
@IocBean
public class HomeService {
    private final HomeRepository repository;

    public HomeService(HomeRepository repository) {
        this.repository = repository;
    }

    public Home getHome(UUID playerId, String name) {
        return repository.findByOwnerAndName(playerId, name);
    }

    public void deleteHome(Home home) {
        repository.delete(home);
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private HomeRepository mockRepository;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(mockRepository);
    }

    @Test
    void getHome_shouldReturnHomeFromRepository() {
        // Given
        UUID playerId = UUID.randomUUID();
        String homeName = "home1";
        Home expectedHome = new Home(playerId, homeName, mock(Location.class));
        when(mockRepository.findByOwnerAndName(playerId, homeName))
            .thenReturn(expectedHome);

        // When
        Home actualHome = homeService.getHome(playerId, homeName);

        // Then
        assertEquals(expectedHome, actualHome);
        verify(mockRepository).findByOwnerAndName(playerId, homeName);
    }

    @Test
    void deleteHome_shouldCallRepositoryDelete() {
        // Given
        Home home = new Home(UUID.randomUUID(), "home1", mock(Location.class));

        // When
        homeService.deleteHome(home);

        // Then
        verify(mockRepository).delete(home);
    }
}
```

### Service with Multiple Dependencies

```java
@IocBean
public class CombatService {
    private final PlayerRepository playerRepository;
    private final CooldownService cooldownService;
    private final MessageService messageService;

    public CombatService(PlayerRepository playerRepository,
                        CooldownService cooldownService,
                        MessageService messageService) {
        this.playerRepository = playerRepository;
        this.cooldownService = cooldownService;
        this.messageService = messageService;
    }

    public boolean canAttack(Player attacker, Player target) {
        if (cooldownService.isOnCooldown(attacker, "combat")) {
            messageService.sendError(attacker, "combat.cooldown");
            return false;
        }

        PlayerData attackerData = playerRepository.load(attacker.getUniqueId());
        if (attackerData.isCombatDisabled()) {
            messageService.sendError(attacker, "combat.disabled");
            return false;
        }

        return true;
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class CombatServiceTest {

    @Mock
    private PlayerRepository mockRepository;

    @Mock
    private CooldownService mockCooldownService;

    @Mock
    private MessageService mockMessageService;

    @Mock
    private Player mockAttacker;

    @Mock
    private Player mockTarget;

    private CombatService combatService;

    @BeforeEach
    void setUp() {
        combatService = new CombatService(
            mockRepository,
            mockCooldownService,
            mockMessageService
        );

        when(mockAttacker.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void canAttack_whenOnCooldown_shouldReturnFalse() {
        // Given
        when(mockCooldownService.isOnCooldown(mockAttacker, "combat"))
            .thenReturn(true);

        // When
        boolean result = combatService.canAttack(mockAttacker, mockTarget);

        // Then
        assertFalse(result);
        verify(mockMessageService).sendError(mockAttacker, "combat.cooldown");
        verify(mockRepository, never()).load(any());
    }

    @Test
    void canAttack_whenCombatDisabled_shouldReturnFalse() {
        // Given
        when(mockCooldownService.isOnCooldown(mockAttacker, "combat"))
            .thenReturn(false);

        PlayerData attackerData = new PlayerData();
        attackerData.setCombatDisabled(true);
        when(mockRepository.load(mockAttacker.getUniqueId()))
            .thenReturn(attackerData);

        // When
        boolean result = combatService.canAttack(mockAttacker, mockTarget);

        // Then
        assertFalse(result);
        verify(mockMessageService).sendError(mockAttacker, "combat.disabled");
    }

    @Test
    void canAttack_whenAllChecksPass_shouldReturnTrue() {
        // Given
        when(mockCooldownService.isOnCooldown(mockAttacker, "combat"))
            .thenReturn(false);

        PlayerData attackerData = new PlayerData();
        attackerData.setCombatDisabled(false);
        when(mockRepository.load(mockAttacker.getUniqueId()))
            .thenReturn(attackerData);

        // When
        boolean result = combatService.canAttack(mockAttacker, mockTarget);

        // Then
        assertTrue(result);
        verify(mockMessageService, never()).sendError(any(), anyString());
    }
}
```

### Testing Services with Configuration

Services with `@ConfigProperty` fields need special handling:

```java
@IocBean
public class HomeService {
    private final HomeRepository repository;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds;

    public HomeService(HomeRepository repository) {
        this.repository = repository;
    }

    public boolean canCreateHome(Player player) {
        List<Home> homes = repository.findAllByOwner(player.getUniqueId());
        return homes.size() < maxHomes;
    }
}
```

**Test using reflection to set config values:**

```java
@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private HomeRepository mockRepository;

    @Mock
    private Player mockPlayer;

    private HomeService homeService;

    @BeforeEach
    void setUp() throws Exception {
        homeService = new HomeService(mockRepository);

        // Set config property values using reflection
        setField(homeService, "maxHomes", 5);
        setField(homeService, "cooldownSeconds", 30);

        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void canCreateHome_whenUnderLimit_shouldReturnTrue() {
        // Given
        List<Home> existingHomes = List.of(
            new Home(mockPlayer.getUniqueId(), "home1", mock(Location.class))
        );
        when(mockRepository.findAllByOwner(mockPlayer.getUniqueId()))
            .thenReturn(existingHomes);

        // When
        boolean result = homeService.canCreateHome(mockPlayer);

        // Then
        assertTrue(result);
    }

    @Test
    void canCreateHome_whenAtLimit_shouldReturnFalse() {
        // Given - player has 5 homes (maxHomes = 5)
        List<Home> existingHomes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            existingHomes.add(
                new Home(mockPlayer.getUniqueId(), "home" + i, mock(Location.class))
            );
        }
        when(mockRepository.findAllByOwner(mockPlayer.getUniqueId()))
            .thenReturn(existingHomes);

        // When
        boolean result = homeService.canCreateHome(mockPlayer);

        // Then
        assertFalse(result);
    }

    // Helper method to set private fields via reflection
    private void setField(Object target, String fieldName, Object value)
            throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
```

**Alternative: Constructor injection for configuration values:**

```java
// Better design for testability
@IocBean
public class HomeService {
    private final HomeRepository repository;
    private final int maxHomes;
    private final int cooldownSeconds;

    public HomeService(HomeRepository repository,
                      @ConfigProperty("homes.max-homes") int maxHomes,
                      @ConfigProperty("homes.cooldown-seconds") int cooldownSeconds) {
        this.repository = repository;
        this.maxHomes = maxHomes;
        this.cooldownSeconds = cooldownSeconds;
    }
}

// Test - just pass values to constructor
@Test
void testWithConstructorInjection() {
    HomeService service = new HomeService(mockRepository, 5, 30);
    // No reflection needed!
}
```

## Testing Repositories

Repositories handle data access and should be tested for data transformation logic.

### Simple Repository

```java
@IocBean
public class DatabasePlayerRepository implements PlayerRepository {
    private final Database database;

    public DatabasePlayerRepository(Database database) {
        this.database = database;
    }

    @Override
    public PlayerData load(UUID playerId) {
        return database.query(
            "SELECT * FROM players WHERE id = ?",
            playerId
        );
    }

    @Override
    public void save(PlayerData data) {
        database.execute(
            "INSERT INTO players (id, name, points) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE name = ?, points = ?",
            data.getId(),
            data.getName(),
            data.getPoints(),
            data.getName(),
            data.getPoints()
        );
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class DatabasePlayerRepositoryTest {

    @Mock
    private Database mockDatabase;

    private DatabasePlayerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DatabasePlayerRepository(mockDatabase);
    }

    @Test
    void load_shouldQueryDatabaseWithPlayerId() {
        // Given
        UUID playerId = UUID.randomUUID();
        PlayerData expectedData = new PlayerData(playerId, "TestPlayer", 100);
        when(mockDatabase.query(anyString(), eq(playerId)))
            .thenReturn(expectedData);

        // When
        PlayerData result = repository.load(playerId);

        // Then
        assertEquals(expectedData, result);
        verify(mockDatabase).query(
            "SELECT * FROM players WHERE id = ?",
            playerId
        );
    }

    @Test
    void save_shouldExecuteInsertOrUpdateQuery() {
        // Given
        PlayerData data = new PlayerData(UUID.randomUUID(), "TestPlayer", 100);

        // When
        repository.save(data);

        // Then
        verify(mockDatabase).execute(
            contains("INSERT INTO players"),
            eq(data.getId()),
            eq(data.getName()),
            eq(data.getPoints()),
            eq(data.getName()),
            eq(data.getPoints())
        );
    }
}
```

### Repository with Data Transformation

```java
@IocBean
public class FileHomeRepository implements HomeRepository {
    private final FileManager fileManager;

    public FileHomeRepository(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    @Override
    public List<Home> findAllByOwner(UUID ownerId) {
        FileConfiguration config = fileManager.loadPlayerFile(ownerId);

        List<Home> homes = new ArrayList<>();
        for (String key : config.getConfigurationSection("homes").getKeys(false)) {
            String path = "homes." + key;

            Home home = new Home(
                ownerId,
                key,
                deserializeLocation(config.getString(path + ".location"))
            );
            homes.add(home);
        }

        return homes;
    }

    private Location deserializeLocation(String serialized) {
        // Parse "world,x,y,z" format
        String[] parts = serialized.split(",");
        World world = Bukkit.getWorld(parts[0]);
        return new Location(
            world,
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3])
        );
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class FileHomeRepositoryTest {

    @Mock
    private FileManager mockFileManager;

    @Mock
    private FileConfiguration mockConfig;

    @Mock
    private ConfigurationSection mockHomesSection;

    private FileHomeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FileHomeRepository(mockFileManager);
    }

    @Test
    void findAllByOwner_shouldDeserializeHomesFromFile() {
        // Given
        UUID ownerId = UUID.randomUUID();

        when(mockFileManager.loadPlayerFile(ownerId)).thenReturn(mockConfig);
        when(mockConfig.getConfigurationSection("homes")).thenReturn(mockHomesSection);
        when(mockHomesSection.getKeys(false)).thenReturn(Set.of("home1", "home2"));

        when(mockConfig.getString("homes.home1.location"))
            .thenReturn("world,100,64,200");
        when(mockConfig.getString("homes.home2.location"))
            .thenReturn("world_nether,50,32,100");

        // When
        List<Home> homes = repository.findAllByOwner(ownerId);

        // Then
        assertEquals(2, homes.size());

        Home home1 = homes.stream()
            .filter(h -> h.getName().equals("home1"))
            .findFirst()
            .orElseThrow();
        assertEquals(ownerId, home1.getOwnerId());
        assertEquals(100.0, home1.getLocation().getX());
        assertEquals(64.0, home1.getLocation().getY());
        assertEquals(200.0, home1.getLocation().getZ());
    }

    @Test
    void findAllByOwner_whenNoHomes_shouldReturnEmptyList() {
        // Given
        UUID ownerId = UUID.randomUUID();

        when(mockFileManager.loadPlayerFile(ownerId)).thenReturn(mockConfig);
        when(mockConfig.getConfigurationSection("homes")).thenReturn(mockHomesSection);
        when(mockHomesSection.getKeys(false)).thenReturn(Collections.emptySet());

        // When
        List<Home> homes = repository.findAllByOwner(ownerId);

        // Then
        assertTrue(homes.isEmpty());
    }
}
```

## Testing Command Handlers

Command handlers are thin wrappers that delegate to services. Test both command parsing and service delegation.

### Simple Command

```java
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand extends AbstractCmd {
    private final HomeService homeService;

    public SetHomeCommand(CommandExceptionHandler exceptionHandler,
                         TubingPermissionService permissionService,
                         HomeService homeService) {
        super(exceptionHandler, permissionService);
        this.homeService = homeService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
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

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class SetHomeCommandTest {

    @Mock
    private CommandExceptionHandler mockExceptionHandler;

    @Mock
    private TubingPermissionService mockPermissionService;

    @Mock
    private HomeService mockHomeService;

    @Mock
    private Player mockPlayer;

    @Mock
    private Location mockLocation;

    private SetHomeCommand command;

    @BeforeEach
    void setUp() {
        command = new SetHomeCommand(
            mockExceptionHandler,
            mockPermissionService,
            mockHomeService
        );

        when(mockPlayer.getLocation()).thenReturn(mockLocation);
    }

    @Test
    void execute_withPlayerSender_shouldCreateHome() {
        // Given
        String homeName = "home1";
        String[] args = {homeName};

        // When
        boolean result = command.executeCmd(mockPlayer, "sethome", args);

        // Then
        assertTrue(result);
        verify(mockHomeService).createHome(mockPlayer, homeName, mockLocation);
    }

    @Test
    void execute_withDefaultName_shouldCreateHomeWithDefaultName() {
        // Given
        String[] args = {};

        // When
        boolean result = command.executeCmd(mockPlayer, "sethome", args);

        // Then
        assertTrue(result);
        verify(mockHomeService).createHome(mockPlayer, "default", mockLocation);
    }

    @Test
    void execute_withConsoleSender_shouldSendErrorMessage() {
        // Given
        CommandSender consoleSender = mock(CommandSender.class);
        String[] args = {};

        // When
        boolean result = command.executeCmd(consoleSender, "sethome", args);

        // Then
        assertTrue(result);
        verify(consoleSender).sendMessage("Players only!");
        verify(mockHomeService, never()).createHome(any(), any(), any());
    }
}
```

### Command with Subcommands

```java
@IocBukkitCommandHandler("home")
public class HomeCommand extends AbstractCmd {
    private final HomeService homeService;

    public HomeCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService,
                      HomeService homeService) {
        super(exceptionHandler, permissionService);
        this.homeService = homeService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // /home - teleport to default home
            homeService.teleportToHome(player, "default");
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "list":
                homeService.listHomes(player);
                return true;
            case "delete":
                if (args.length < 2) {
                    player.sendMessage("Usage: /home delete <name>");
                    return true;
                }
                homeService.deleteHome(player, args[1]);
                return true;
            default:
                // /home <name> - teleport to named home
                homeService.teleportToHome(player, subcommand);
                return true;
        }
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class HomeCommandTest {

    @Mock
    private CommandExceptionHandler mockExceptionHandler;

    @Mock
    private TubingPermissionService mockPermissionService;

    @Mock
    private HomeService mockHomeService;

    @Mock
    private Player mockPlayer;

    private HomeCommand command;

    @BeforeEach
    void setUp() {
        command = new HomeCommand(
            mockExceptionHandler,
            mockPermissionService,
            mockHomeService
        );
    }

    @Test
    void execute_withNoArgs_shouldTeleportToDefaultHome() {
        // When
        command.executeCmd(mockPlayer, "home", new String[]{});

        // Then
        verify(mockHomeService).teleportToHome(mockPlayer, "default");
    }

    @Test
    void execute_withList_shouldCallListHomes() {
        // When
        command.executeCmd(mockPlayer, "home", new String[]{"list"});

        // Then
        verify(mockHomeService).listHomes(mockPlayer);
    }

    @Test
    void execute_withDelete_shouldCallDeleteHome() {
        // When
        command.executeCmd(mockPlayer, "home", new String[]{"delete", "home1"});

        // Then
        verify(mockHomeService).deleteHome(mockPlayer, "home1");
    }

    @Test
    void execute_withDeleteNoName_shouldShowUsage() {
        // When
        command.executeCmd(mockPlayer, "home", new String[]{"delete"});

        // Then
        verify(mockPlayer).sendMessage("Usage: /home delete <name>");
        verify(mockHomeService, never()).deleteHome(any(), any());
    }

    @Test
    void execute_withHomeName_shouldTeleportToNamedHome() {
        // When
        command.executeCmd(mockPlayer, "home", new String[]{"home1"});

        // Then
        verify(mockHomeService).teleportToHome(mockPlayer, "home1");
    }
}
```

## Testing Event Listeners

Event listeners delegate to services. Test event handling logic and service calls.

### Simple Listener

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {
    private final PlayerDataService playerDataService;
    private final MessageService messageService;

    public PlayerJoinListener(PlayerDataService playerDataService,
                             MessageService messageService) {
        this.playerDataService = playerDataService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        playerDataService.loadPlayerData(player);
        messageService.sendWelcomeMessage(player);
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class PlayerJoinListenerTest {

    @Mock
    private PlayerDataService mockPlayerDataService;

    @Mock
    private MessageService mockMessageService;

    @Mock
    private Player mockPlayer;

    private PlayerJoinListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlayerJoinListener(
            mockPlayerDataService,
            mockMessageService
        );
    }

    @Test
    void onPlayerJoin_shouldLoadDataAndSendWelcome() {
        // Given
        PlayerJoinEvent event = new PlayerJoinEvent(mockPlayer, "Welcome!");

        // When
        listener.onPlayerJoin(event);

        // Then
        verify(mockPlayerDataService).loadPlayerData(mockPlayer);
        verify(mockMessageService).sendWelcomeMessage(mockPlayer);
    }
}
```

### Listener with Complex Logic

```java
@IocBukkitListener
public class BlockBreakListener implements Listener {
    private final PermissionService permissions;
    private final RegionService regions;
    private final MessageService messages;

    @ConfigProperty("protection.enabled")
    private boolean protectionEnabled;

    public BlockBreakListener(PermissionService permissions,
                             RegionService regions,
                             MessageService messages) {
        this.permissions = permissions;
        this.regions = regions;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectionEnabled) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check bypass permission
        if (permissions.hasPermission(player, "build.bypass")) {
            return;
        }

        // Check if in protected region
        if (!regions.isProtected(block.getLocation())) {
            return;
        }

        // Cancel and notify
        event.setCancelled(true);
        messages.sendError(player, "protection.cannot-build");
    }
}
```

**Test:**

```java
@ExtendWith(MockitoExtension.class)
class BlockBreakListenerTest {

    @Mock
    private PermissionService mockPermissions;

    @Mock
    private RegionService mockRegions;

    @Mock
    private MessageService mockMessages;

    @Mock
    private Player mockPlayer;

    @Mock
    private Block mockBlock;

    @Mock
    private Location mockLocation;

    private BlockBreakListener listener;

    @BeforeEach
    void setUp() throws Exception {
        listener = new BlockBreakListener(
            mockPermissions,
            mockRegions,
            mockMessages
        );

        // Enable protection
        setField(listener, "protectionEnabled", true);

        when(mockBlock.getLocation()).thenReturn(mockLocation);
    }

    @Test
    void onBlockBreak_whenProtectionDisabled_shouldNotCancelEvent() {
        // Given
        setField(listener, "protectionEnabled", false);
        BlockBreakEvent event = new BlockBreakEvent(mockBlock, mockPlayer);

        // When
        listener.onBlockBreak(event);

        // Then
        assertFalse(event.isCancelled());
        verify(mockPermissions, never()).hasPermission(any(), any());
    }

    @Test
    void onBlockBreak_whenPlayerHasBypass_shouldAllowBreak() {
        // Given
        when(mockPermissions.hasPermission(mockPlayer, "build.bypass"))
            .thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(mockBlock, mockPlayer);

        // When
        listener.onBlockBreak(event);

        // Then
        assertFalse(event.isCancelled());
        verify(mockRegions, never()).isProtected(any());
    }

    @Test
    void onBlockBreak_whenNotProtected_shouldAllowBreak() {
        // Given
        when(mockPermissions.hasPermission(mockPlayer, "build.bypass"))
            .thenReturn(false);
        when(mockRegions.isProtected(mockLocation)).thenReturn(false);
        BlockBreakEvent event = new BlockBreakEvent(mockBlock, mockPlayer);

        // When
        listener.onBlockBreak(event);

        // Then
        assertFalse(event.isCancelled());
        verify(mockMessages, never()).sendError(any(), any());
    }

    @Test
    void onBlockBreak_whenProtected_shouldCancelAndNotify() {
        // Given
        when(mockPermissions.hasPermission(mockPlayer, "build.bypass"))
            .thenReturn(false);
        when(mockRegions.isProtected(mockLocation)).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(mockBlock, mockPlayer);

        // When
        listener.onBlockBreak(event);

        // Then
        assertTrue(event.isCancelled());
        verify(mockMessages).sendError(mockPlayer, "protection.cannot-build");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

## Mocking Dependencies

### Basic Mocking with Mockito

```java
// Create mock
Database mockDatabase = mock(Database.class);

// Configure mock behavior
when(mockDatabase.query(anyString(), any())).thenReturn(expectedData);

// Verify mock was called
verify(mockDatabase).query(eq("SELECT * FROM players"), any());

// Verify mock was NOT called
verify(mockDatabase, never()).execute(any());

// Verify number of calls
verify(mockDatabase, times(2)).query(any(), any());
```

### Mocking Interface Dependencies

```java
@IocBean
public class HomeService {
    private final HomeRepository repository;

    public HomeService(HomeRepository repository) {
        this.repository = repository;
    }
}

// Test - mock the interface, not the implementation
@Test
void testHomeService() {
    HomeRepository mockRepo = mock(HomeRepository.class);
    HomeService service = new HomeService(mockRepo);

    // Configure mock
    when(mockRepo.findByOwnerAndName(any(), eq("home1")))
        .thenReturn(new Home(...));

    // Test service
    Home result = service.getHome(playerId, "home1");

    // Verify
    verify(mockRepo).findByOwnerAndName(playerId, "home1");
}
```

### Mocking Bukkit Objects

```java
// Mock Player
Player mockPlayer = mock(Player.class);
when(mockPlayer.getName()).thenReturn("TestPlayer");
when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
when(mockPlayer.hasPermission("some.permission")).thenReturn(true);

// Mock Location
Location mockLocation = mock(Location.class);
World mockWorld = mock(World.class);
when(mockLocation.getWorld()).thenReturn(mockWorld);
when(mockLocation.getX()).thenReturn(100.0);
when(mockLocation.getY()).thenReturn(64.0);
when(mockLocation.getZ()).thenReturn(200.0);

// Mock ItemStack
ItemStack mockItem = mock(ItemStack.class);
when(mockItem.getType()).thenReturn(Material.DIAMOND_SWORD);
when(mockItem.getAmount()).thenReturn(1);
```

### Argument Captors

Capture arguments passed to mocks for verification:

```java
@Test
void testArgumentCapture() {
    // Create captor
    ArgumentCaptor<PlayerData> captor = ArgumentCaptor.forClass(PlayerData.class);

    // Run test
    playerService.savePlayer(mockPlayer);

    // Capture argument
    verify(mockRepository).save(captor.capture());

    // Verify captured argument
    PlayerData captured = captor.getValue();
    assertEquals("TestPlayer", captured.getName());
    assertEquals(100, captured.getPoints());
}
```

### Mocking Void Methods

```java
// Mock void method that throws exception
doThrow(new DatabaseException("Connection failed"))
    .when(mockDatabase).execute(any());

// Mock void method with no action (default)
doNothing().when(mockMessageService).sendMessage(any(), any());

// Mock void method with custom action
doAnswer(invocation -> {
    Player player = invocation.getArgument(0);
    String message = invocation.getArgument(1);
    System.out.println("Sending to " + player.getName() + ": " + message);
    return null;
}).when(mockMessageService).sendMessage(any(), any());
```

## Integration Testing with IoC

Sometimes you need to test with the actual IoC container for integration tests.

### Setting Up Integration Tests

```java
@ExtendWith(MockitoExtension.class)
class HomePluginIntegrationTest {

    private TubingBukkitPlugin plugin;
    private IocContainer container;

    @BeforeEach
    void setUp() {
        // Create mock plugin
        plugin = mock(TubingBukkitPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getClass().getPackage().getName())
            .thenReturn("com.example.myplugin");

        // Initialize container
        container = new IocContainer();
        when(plugin.getIocContainer()).thenReturn(container);

        // Register plugin instance
        container.registerBean(plugin);

        // Initialize (scans and creates beans)
        container.init(plugin);
    }

    @Test
    void testBeanWiring() {
        // Get bean from container
        HomeService homeService = container.get(HomeService.class);

        // Verify it was created with dependencies
        assertNotNull(homeService);

        // Test actual behavior
        // ...
    }

    @Test
    void testMultipleBeans() {
        // Get multiple beans
        HomeService homeService = container.get(HomeService.class);
        HomeRepository repository = container.get(HomeRepository.class);

        // Verify they're wired together correctly
        assertNotNull(homeService);
        assertNotNull(repository);
    }
}
```

### Testing with Mock Configuration

```java
@BeforeEach
void setUp() {
    plugin = mock(TubingBukkitPlugin.class, RETURNS_DEEP_STUBS);

    // Mock configuration
    FileConfiguration mockConfig = new YamlConfiguration();
    mockConfig.set("homes.max-homes", 5);
    mockConfig.set("homes.cooldown-seconds", 30);

    Map<String, FileConfiguration> configs = Map.of("config", mockConfig);

    ConfigurationLoader configLoader = mock(ConfigurationLoader.class);
    when(configLoader.getConfigurationFiles()).thenReturn(configs);

    container = new IocContainer();
    container.registerBean(configLoader);
    container.init(plugin);
}

@Test
void testConfigurationInjection() {
    HomeService homeService = container.get(HomeService.class);

    // Configuration values should be injected
    assertTrue(homeService.canCreateHome(playerWith4Homes()));
    assertFalse(homeService.canCreateHome(playerWith5Homes()));
}
```

### Testing Bean Providers

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public Database provideDatabase(@ConfigProperty("database.url") String url) {
        return new HikariDatabase(url);
    }
}

// Integration test
@Test
void testDatabaseProvider() {
    // Setup config
    mockConfig.set("database.url", "jdbc:mysql://localhost:3306/test");

    // Initialize container
    container.init(plugin);

    // Get provided bean
    Database database = container.get(Database.class);

    // Verify it was created correctly
    assertNotNull(database);
    assertInstanceOf(HikariDatabase.class, database);
}
```

## Test Organization

### Directory Structure

```
src/
├── main/java/com/example/myplugin/
│   ├── services/
│   │   ├── HomeService.java
│   │   ├── PlayerService.java
│   │   └── CombatService.java
│   ├── repositories/
│   │   ├── HomeRepository.java
│   │   └── PlayerRepository.java
│   ├── commands/
│   │   ├── HomeCommand.java
│   │   └── SetHomeCommand.java
│   └── listeners/
│       ├── PlayerJoinListener.java
│       └── BlockBreakListener.java
└── test/java/com/example/myplugin/
    ├── services/
    │   ├── HomeServiceTest.java
    │   ├── PlayerServiceTest.java
    │   └── CombatServiceTest.java
    ├── repositories/
    │   ├── HomeRepositoryTest.java
    │   └── PlayerRepositoryTest.java
    ├── commands/
    │   ├── HomeCommandTest.java
    │   └── SetHomeCommandTest.java
    ├── listeners/
    │   ├── PlayerJoinListenerTest.java
    │   └── BlockBreakListenerTest.java
    └── integration/
        └── HomePluginIntegrationTest.java
```

### Test Naming Conventions

**Test Class Names:**
- `{ClassName}Test` - Unit test for the class
- `{Feature}IntegrationTest` - Integration test for a feature

**Test Method Names:**
- Use descriptive names that explain what is being tested
- Format: `methodName_condition_expectedResult`

```java
@Test
void createHome_whenPlayerHasSpace_shouldSaveHome() { }

@Test
void createHome_whenPlayerAtLimit_shouldThrowException() { }

@Test
void deleteHome_whenHomeExists_shouldRemoveFromRepository() { }

@Test
void deleteHome_whenHomeDoesNotExist_shouldDoNothing() { }
```

### Test Helpers and Utilities

Create helper classes for common test setup:

```java
// TestData.java
public class TestData {

    public static Player createMockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    public static Home createHome(UUID ownerId, String name) {
        Location location = mock(Location.class);
        return new Home(ownerId, name, location);
    }

    public static PlayerData createPlayerData(UUID id, String name, int points) {
        return new PlayerData(id, name, points);
    }
}

// Use in tests
@Test
void test() {
    Player player = TestData.createMockPlayer("TestPlayer");
    Home home = TestData.createHome(player.getUniqueId(), "home1");
    PlayerData data = TestData.createPlayerData(UUID.randomUUID(), "Test", 100);
}
```

### Base Test Classes

Create base classes for common setup:

```java
public abstract class ServiceTestBase {

    @Mock
    protected MessageService mockMessageService;

    @BeforeEach
    void baseSetUp() {
        // Common setup for all service tests
        doNothing().when(mockMessageService).sendError(any(), any());
        doNothing().when(mockMessageService).sendSuccess(any(), any());
    }

    protected void setConfigField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

// Use in tests
@ExtendWith(MockitoExtension.class)
class HomeServiceTest extends ServiceTestBase {

    @Mock
    private HomeRepository mockRepository;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(mockRepository, mockMessageService);
        setConfigField(homeService, "maxHomes", 5);
    }

    @Test
    void test() {
        // MessageService is already set up from base class
    }
}
```

## Testing Best Practices

### 1. Test One Thing Per Test

```java
// Good - tests one specific behavior
@Test
void canCreateHome_whenUnderLimit_shouldReturnTrue() {
    // Single assertion about one behavior
}

// Bad - tests multiple things
@Test
void testHomeService() {
    // Creates home
    // Lists homes
    // Deletes home
    // Checks limits
    // Too much in one test!
}
```

### 2. Use Descriptive Test Names

```java
// Good
@Test
void deleteHome_whenHomeDoesNotExist_shouldThrowException() { }

// Bad
@Test
void test1() { }
```

### 3. Follow AAA Pattern

```java
@Test
void testExample() {
    // Arrange - set up test data and mocks
    Player player = TestData.createMockPlayer("Test");
    when(mockRepository.findAllByOwner(any())).thenReturn(List.of());

    // Act - execute the method being tested
    boolean result = homeService.canCreateHome(player);

    // Assert - verify the result
    assertTrue(result);
    verify(mockRepository).findAllByOwner(player.getUniqueId());
}
```

### 4. Don't Mock What You Don't Own

```java
// Bad - mocking value objects
Home mockHome = mock(Home.class);
when(mockHome.getName()).thenReturn("home1");

// Good - use real objects
Home home = new Home(playerId, "home1", location);
```

### 5. Keep Tests Independent

```java
// Bad - tests depend on execution order
private static int counter = 0;

@Test
void test1() {
    counter++;
    assertEquals(1, counter);
}

@Test
void test2() {
    counter++;
    assertEquals(2, counter); // Fails if test1 doesn't run first!
}

// Good - tests are independent
@Test
void test1() {
    int counter = 0;
    counter++;
    assertEquals(1, counter);
}

@Test
void test2() {
    int counter = 0;
    counter++;
    assertEquals(1, counter);
}
```

### 6. Use Verify Sparingly

```java
// Good - verify important interactions
verify(mockRepository).save(any());

// Bad - over-verifying
verify(mockPlayer).getName();
verify(mockPlayer).getUniqueId();
verify(mockPlayer).getLocation();
verify(mockRepository).save(any());
verify(mockMessageService).sendSuccess(any(), any());
// Too much verification!
```

### 7. Test Edge Cases

```java
@Test
void canCreateHome_whenAtLimit_shouldReturnFalse() {
    // Test boundary condition
}

@Test
void canCreateHome_whenNullPlayer_shouldThrowException() {
    // Test null input
}

@Test
void canCreateHome_whenEmptyHomeList_shouldReturnTrue() {
    // Test empty collection
}
```

### 8. Don't Test Framework Code

```java
// Don't test Tubing's dependency injection
@Test
void testDependencyInjection() {
    HomeService service = container.get(HomeService.class);
    assertNotNull(service); // Don't do this
}

// Test your business logic
@Test
void canCreateHome_whenUnderLimit_shouldReturnTrue() {
    // Test your code, not the framework
}
```

### 9. Use Test Doubles Appropriately

- **Mock**: For dependencies with behavior to verify
- **Stub**: For dependencies that return data
- **Fake**: For lightweight implementations (e.g., in-memory repository)
- **Spy**: For partial mocking (use sparingly)

```java
// Mock - verify behavior
Database mockDb = mock(Database.class);
verify(mockDb).save(any());

// Stub - return data
PlayerRepository stub = mock(PlayerRepository.class);
when(stub.load(any())).thenReturn(playerData);

// Fake - real implementation
class FakeHomeRepository implements HomeRepository {
    private Map<UUID, List<Home>> homes = new HashMap<>();

    public List<Home> findAllByOwner(UUID ownerId) {
        return homes.getOrDefault(ownerId, new ArrayList<>());
    }

    public void save(Home home) {
        homes.computeIfAbsent(home.getOwnerId(), k -> new ArrayList<>()).add(home);
    }
}
```

### 10. Write Maintainable Tests

```java
// Extract common setup to helper methods
private HomeService createHomeService(int maxHomes) {
    HomeService service = new HomeService(mockRepository);
    setConfigField(service, "maxHomes", maxHomes);
    return service;
}

// Use builders for complex objects
Home home = Home.builder()
    .ownerId(playerId)
    .name("home1")
    .location(location)
    .build();

// Use constants for test data
private static final int DEFAULT_MAX_HOMES = 5;
private static final String DEFAULT_HOME_NAME = "default";
```

## Running Tests

### Maven Commands

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=HomeServiceTest

# Run specific test method
mvn test -Dtest=HomeServiceTest#canCreateHome_whenUnderLimit_shouldReturnTrue

# Run tests with coverage
mvn test jacoco:report

# Skip tests during build
mvn package -DskipTests
```

### CI/CD Integration

```yaml
# GitHub Actions example
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: mvn test

      - name: Generate coverage report
        run: mvn jacoco:report

      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Summary

Testing Tubing plugins is straightforward thanks to:

**Constructor Injection:**
- All dependencies passed through constructors
- Easy to mock and inject test doubles
- No framework needed in tests

**Interface-Based Design:**
- Depend on abstractions
- Swap implementations easily
- Mock interfaces, not implementations

**Separation of Concerns:**
- Services contain business logic (highly testable)
- Commands/listeners are thin wrappers (simple to test)
- Repositories handle data access (mockable)

**Testing Strategy:**
- Unit test services and repositories
- Test command handlers for parsing logic
- Test listeners for event handling
- Integration test with IoC when needed
- Mock dependencies, use real value objects

**Best Practices:**
- One test per behavior
- Descriptive test names
- Follow AAA pattern
- Keep tests independent
- Test edge cases
- Avoid over-mocking

## Next Steps

- **[Dependency Injection](../core/Dependency-Injection.md)** - Deep dive into DI patterns
- **[Project Structure](../getting-started/Project-Structure.md)** - Organize code for testability
- **[IoC Container](../core/IoC-Container.md)** - Understanding the container
- **[Commands](../bukkit/Commands.md)** - Command handling patterns
- **[Event Listeners](../bukkit/Event-Listeners.md)** - Listener patterns

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - Bean creation and initialization
- [Configuration Injection](../core/Configuration-Injection.md) - Testing with config values
