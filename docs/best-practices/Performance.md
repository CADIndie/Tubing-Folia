# Performance

This guide covers performance considerations when working with Tubing. Learn about the IoC container's performance characteristics, optimization strategies, caching patterns, async operations, and best practices for building high-performance Minecraft plugins.

## Overview

Tubing is designed for performance from the ground up. The IoC container initializes beans efficiently during plugin startup, and the framework provides tools for async operations, caching, and optimized configuration loading. Understanding these characteristics helps you build plugins that remain responsive under load.

**Key Performance Topics:**
- IoC container initialization and bean instantiation overhead
- Configuration loading and caching strategies
- Async patterns for non-blocking operations
- GUI framework performance characteristics
- Common performance pitfalls and how to avoid them
- Best practices for production plugins

## IoC Container Performance

### Initialization Performance

The IoC container initializes during plugin startup and completes in milliseconds for most plugins.

**Initialization Phases:**

1. **Package Scanning (10-50ms)**: ClassGraph scans your plugin's package for annotated classes
2. **Bean Discovery (1-5ms)**: Annotations and providers are collected
3. **Bean Instantiation (varies)**: Beans are created with dependency resolution
4. **Configuration Injection (1-10ms)**: Properties are injected into bean fields
5. **Post-initialization (varies)**: `@AfterIocLoad` hooks execute

**Total Typical Startup Time:** 20-100ms for small to medium plugins.

### ClassGraph Scanning Optimization

ClassGraph is highly efficient and only scans your plugin's package tree:

```java
String pkg = tubingPlugin.getClass().getPackage().getName();
scanResult = new ClassGraph()
    .enableAllInfo()
    .acceptPackages(pkg)  // Only your plugin package
    .scan();
```

**Performance Characteristics:**
- Scans bytecode directly without loading classes
- Only scans packages under your plugin's root package
- Results are cached for the lifetime of your plugin
- No rescanning during bean lookups

**Optimization Tips:**

```java
// GOOD - Shallow package structure
com.example.myplugin/
├── commands/
├── services/
└── listeners/

// AVOID - Deep nesting adds scanning time
com.example.myplugin/
├── features/
│   └── homes/
│       └── commands/
│           └── subcommands/
│               └── admin/
```

Keep your package structure reasonably flat. Each additional nesting level adds minimal overhead, but deep hierarchies can accumulate scanning time.

### Bean Instantiation Overhead

Bean instantiation happens once during container initialization. Each bean is created using reflection and cached as a singleton.

**Per-Bean Instantiation Cost:**
- Constructor resolution: ~0.1ms
- Parameter resolution: 0.1-1ms depending on dependencies
- Instance creation via reflection: ~0.5ms
- Configuration injection: 0.1-2ms depending on property count
- Total per bean: ~1-5ms for typical beans

**Dependency Resolution:**

Tubing resolves dependencies recursively. The order matters:

```java
@IocBean
public class ServiceA {
    // Instantiated first (no dependencies)
}

@IocBean
public class ServiceB {
    // Instantiated second (depends on ServiceA)
    public ServiceB(ServiceA serviceA) { }
}

@IocBean
public class ServiceC {
    // Instantiated third (depends on both)
    public ServiceC(ServiceA serviceA, ServiceB serviceB) { }
}
```

Deep dependency chains add minimal overhead since each bean is only instantiated once and cached.

**Avoiding Instantiation Bottlenecks:**

```java
// BAD - Heavy work in constructor
@IocBean
public class DatabaseService {
    public DatabaseService(@ConfigProperty("db.url") String url) {
        connect(url);              // Blocks startup
        loadSchemaFromDisk();      // Expensive I/O
        warmUpConnectionPool();    // Network operations
    }
}

// GOOD - Defer heavy work to post-initialization
@IocBean
public class DatabaseService {
    private final String url;

    public DatabaseService(@ConfigProperty("db.url") String url) {
        this.url = url;  // Just store configuration
    }

    public void connect() {
        // Called explicitly when needed
    }
}

@TubingConfiguration
public class DatabaseConfiguration {
    @AfterIocLoad
    public static void initDatabase(DatabaseService database) {
        database.connect();  // Initialize after all beans ready
    }
}
```

**Rule of Thumb:** Constructors should complete in under 1ms. Defer expensive operations to `@AfterIocLoad` or lazy initialization.

### Bean Lookup Performance

Bean lookups after initialization are highly optimized:

```java
// O(1) lookup from HashMap
PlayerService service = container.get(PlayerService.class);
```

**Lookup Cost:**
- Concrete class lookup: ~0.01ms (HashMap access)
- Interface lookup: ~0.1ms (filter + stream operation)
- Multi-bean list lookup: ~0.01ms (HashMap access)

Bean lookups are negligible in production workloads. The container uses `HashMap` internally for O(1) access.

**Comparison to Traditional Patterns:**

```java
// Singleton pattern - similar performance
PlayerService service = PlayerService.getInstance();  // Static field access

// IoC container - negligible overhead
PlayerService service = container.get(PlayerService.class);  // HashMap lookup

// Manual factory - potentially slower
PlayerService service = ServiceFactory.create();  // May involve object creation
```

**Recommendation:** Don't worry about bean lookup performance. Use constructor injection to avoid lookups entirely in hot paths:

```java
@IocBean
public class PlayerListener {
    private final PlayerService playerService;  // Injected once

    public PlayerListener(PlayerService playerService) {
        this.playerService = playerService;  // Store reference
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Direct field access - no container lookup
        playerService.handleJoin(event.getPlayer());
    }
}
```

### Priority Beans and Instantiation Order

Priority beans are instantiated before non-priority beans:

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // Instantiated early
}

@IocBean
public class PlayerService {
    // Instantiated after priority beans
}
```

**Performance Impact:**
- Priority sorting: ~1ms for typical plugin (one-time cost)
- No runtime performance difference between priority and non-priority beans
- Use priority only for infrastructure beans (ConfigurationLoader, etc.)

Priority affects initialization order but has no impact on runtime performance.

## Configuration Loading Performance

### Configuration File Loading

Configuration files are loaded once during container initialization.

**Loading Process:**
1. **Save from resources** (if missing): ~10-50ms per file (I/O bound)
2. **Load YAML from disk**: ~5-20ms per file
3. **Run migrations**: Varies based on migration complexity
4. **Auto-update**: ~10-30ms per file (comparing and merging)
5. **Parse property references**: ~1-5ms per file

**Total Configuration Loading Time:** 50-200ms for typical plugins with 3-5 config files.

**Optimization: Reduce Configuration File Count**

```java
// AVOID - Many small files increase overhead
new ConfigurationFile("homes.yml"),
new ConfigurationFile("warps.yml"),
new ConfigurationFile("spawns.yml"),
new ConfigurationFile("teleports.yml")
// 4 file loads + 4 auto-updates = ~200ms

// BETTER - Combine related configs
new ConfigurationFile("config.yml"),
new ConfigurationFile("features.yml"),
new ConfigurationFile("messages.yml")
// 3 file loads + 3 auto-updates = ~150ms
```

**Optimization: Disable Auto-Update for Static Files**

```java
// Messages rarely need auto-update
new ConfigurationFile("messages.yml", "messages", true)
//                                                  ^^^^ ignoreUpdater = true
```

This skips the comparison and merge step, saving ~10-30ms per file.

### Configuration Property Injection

Configuration properties are injected after bean instantiation:

**Injection Cost:**
- Per-field injection: ~0.1ms
- Per-setter injection: ~0.1ms
- Complex object injection: ~1-5ms

```java
@IocBean
public class FeatureService {
    @ConfigProperty("features.enabled")
    private boolean enabled;  // ~0.1ms injection cost

    @ConfigProperty("features.settings")
    @ConfigEmbeddedObject(Settings.class)
    private Settings settings;  // ~1-5ms injection cost (object mapping)
}
```

**Recommendation:** Configuration injection happens once at startup. The performance impact is minimal. Use `@ConfigProperty` liberally for clean code.

### Configuration Access Performance

After loading, configuration access is fast:

```java
// Direct field access - no overhead
@ConfigProperty("max-homes")
private int maxHomes;

public void checkLimit(Player player) {
    if (getHomeCount(player) >= maxHomes) {  // Field access
        // ...
    }
}
```

**Access Patterns:**

```java
// BEST - Field injection (no runtime lookup)
@ConfigProperty("value")
private int value;

// GOOD - One-time ConfigurationLoader access
private final int value;
public Service(ConfigurationLoader loader) {
    this.value = loader.getConfigValue("config:value").orElse(0);
}

// AVOID - Repeated ConfigurationLoader lookups
public void method(ConfigurationLoader loader) {
    int value = loader.getConfigValue("config:value").orElse(0);  // Lookup every call
}
```

**Rule:** Access configuration via `@ConfigProperty` fields for hot paths. ConfigurationLoader lookups are acceptable for one-time initialization.

### Property Reference Performance

Property references like `{{config:prefix}}` are resolved once during configuration loading:

```yaml
prefix: "&8[&6MyPlugin&8]"
welcome: "{{config:prefix}} &7Welcome!"  # Resolved at load time
```

**Resolution Cost:**
- ~0.1ms per reference
- All references resolved during initialization
- No runtime overhead

Property references are "compiled out" during loading - the final configuration contains the resolved values.

## Caching Strategies

Caching is critical for high-performance plugins. Tubing doesn't provide built-in caching, but the IoC pattern makes implementing caches straightforward.

### Bean-Level Caching

The simplest caching strategy is to store cached data in singleton beans:

```java
@IocBean
public class PlayerDataCache {
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final PlayerDataRepository repository;

    public PlayerDataCache(PlayerDataRepository repository) {
        this.repository = repository;
    }

    public PlayerData get(UUID playerId) {
        return cache.computeIfAbsent(playerId, repository::load);
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    public void put(UUID playerId, PlayerData data) {
        cache.put(playerId, data);
    }
}
```

**Benefits:**
- Single cache instance (bean singleton)
- Automatic dependency injection
- Easy to test (inject mock repository)
- Clear ownership and lifecycle

### Time-Based Cache Expiration

For data that changes infrequently, use time-based expiration:

```java
@IocBean
public class LeaderboardCache {
    private final LeaderboardService leaderboardService;
    private List<LeaderboardEntry> cachedData;
    private long lastUpdate;

    @ConfigProperty("leaderboard.cache-duration-seconds")
    private int cacheDuration;

    public LeaderboardCache(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public List<LeaderboardEntry> getLeaderboard() {
        long now = System.currentTimeMillis();

        if (cachedData == null || (now - lastUpdate) > (cacheDuration * 1000L)) {
            cachedData = leaderboardService.loadFromDatabase();
            lastUpdate = now;
        }

        return new ArrayList<>(cachedData);  // Return copy
    }

    public void invalidate() {
        cachedData = null;
    }
}
```

**Use Cases:**
- Leaderboards that update every few minutes
- Server statistics
- Global configuration that rarely changes
- Top players/guilds/factions

### Lazy Loading Pattern

Lazy loading defers expensive operations until first access:

```java
@IocBean
public class PermissionService {
    private final PermissionRepository repository;
    private Map<String, Permission> permissionCache;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public Map<String, Permission> getPermissions() {
        if (permissionCache == null) {
            permissionCache = repository.loadAllPermissions();
        }
        return permissionCache;
    }

    public void reload() {
        permissionCache = null;  // Next access will reload
    }
}
```

**Benefits:**
- Faster startup (no loading on bean creation)
- Only pays cost if feature is used
- Simple invalidation strategy

**Trade-off:** First access is slower. Use for data that's not always needed.

### Cache Warming in AfterIocLoad

For critical caches, warm them during initialization:

```java
@TubingConfiguration
public class CacheConfiguration {

    @AfterIocLoad
    public static void warmCaches(PlayerDataCache playerDataCache,
                                   LeaderboardCache leaderboardCache) {
        // Warm critical caches
        leaderboardCache.getLeaderboard();  // Force initial load

        // Pre-load online players
        Bukkit.getOnlinePlayers().forEach(player -> {
            playerDataCache.get(player.getUniqueId());
        });
    }
}
```

**Use When:**
- Cache is accessed frequently
- Initial load is expensive
- Better to pay cost at startup than on first player interaction

### Thread-Safe Caching

For caches accessed from async operations, use thread-safe collections:

```java
@IocBean
public class ThreadSafeCache {
    // Thread-safe cache implementations
    private final Map<UUID, Data> cache = new ConcurrentHashMap<>();
    private final Cache<UUID, Data> guavaCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    public Data get(UUID key) {
        return cache.computeIfAbsent(key, this::loadData);
    }

    private Data loadData(UUID key) {
        // Load from database
        return null;
    }
}
```

**Thread-Safe Options:**
- `ConcurrentHashMap` - Built-in Java, no external dependencies
- Guava Cache - Feature-rich, size limits, expiration
- Caffeine Cache - Modern, high-performance alternative to Guava

### Cache Invalidation Strategies

**Event-Based Invalidation:**

```java
@IocBukkitListener
public class CacheInvalidationListener implements Listener {
    private final PlayerDataCache cache;

    public CacheInvalidationListener(PlayerDataCache cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Invalidate on quit to free memory
        cache.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDataChange(PlayerDataChangeEvent event) {
        // Invalidate on data change
        cache.invalidate(event.getPlayerId());
    }
}
```

**Periodic Invalidation:**

```java
@IocBean
public class CacheCleanupTask {
    private final PlayerDataCache cache;

    @ConfigProperty("cache.cleanup-interval-minutes")
    private int cleanupInterval;

    public CacheCleanupTask(PlayerDataCache cache) {
        this.cache = cache;
    }

    public void startCleanupTask(TubingBukkitPlugin plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            cache::cleanupStaleEntries,
            cleanupInterval * 60 * 20L,  // Convert to ticks
            cleanupInterval * 60 * 20L
        );
    }
}
```

**Manual Invalidation Commands:**

```java
@IocBukkitCommandHandler("clearcache")
public class ClearCacheCommand {
    private final List<Clearable> caches;

    public ClearCacheCommand(@IocMulti(Clearable.class) List<Clearable> caches) {
        this.caches = caches;
    }

    @CommandHandler
    public void execute(Player player) {
        caches.forEach(Clearable::clear);
        player.sendMessage("All caches cleared!");
    }
}
```

## Async Patterns for Performance

Async operations are essential for maintaining server performance. Never block the main thread with slow operations.

### When to Use Async

**Always use async for:**
- Database queries (SELECT, INSERT, UPDATE, DELETE)
- File I/O operations
- HTTP/API requests
- Complex calculations
- External service calls

**Never use async for:**
- Simple in-memory operations
- Bukkit API calls (most are not thread-safe)
- Event handling (already on main thread)
- GUI building (must happen on main thread)

### Async with GUI Framework

Tubing provides `AsyncGui` for non-blocking GUI operations:

```java
@GuiController
public class PlayerStatsController {
    private final StatsService statsService;

    public PlayerStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GuiAction("stats:view")
    public AsyncGui<TubingGui> viewStats(Player player) {
        UUID playerId = player.getUniqueId();  // Capture on main thread

        return AsyncGui.async(() -> {
            // Runs on async thread - doesn't block server
            PlayerStats stats = statsService.loadFromDatabase(playerId);

            // Build and return GUI
            return buildStatsGui(stats);
        });
    }
}
```

**Performance Impact:**
- No main thread blocking during database query
- Other players unaffected by slow queries
- GUI opens immediately when data is ready

**Loading Screen Pattern:**

```java
@GuiAction("leaderboard:view")
public TubingGui showLoadingScreen(Player player) {
    // Show loading GUI immediately (no delay)
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        guiActionService.executeAction(player, "leaderboard:load");
    }, 1L);

    return createLoadingGui();  // Returns instantly
}

@GuiAction("leaderboard:load")
public AsyncGui<TubingGui> loadLeaderboard(Player player) {
    return AsyncGui.async(() -> {
        // Expensive database query on async thread
        List<LeaderboardEntry> entries = database.getTopPlayers(100);
        return buildLeaderboardGui(entries);
    });
}
```

This pattern ensures the player sees instant feedback (loading screen) while data loads in the background.

### Async Database Operations

Database operations should always be async:

```java
@IocBean
public class PlayerDataService {
    private final PlayerDataRepository repository;
    private final ITubingBukkitUtil tubingBukkitUtil;

    public PlayerDataService(PlayerDataRepository repository,
                            ITubingBukkitUtil tubingBukkitUtil) {
        this.repository = repository;
        this.tubingBukkitUtil = tubingBukkitUtil;
    }

    public void saveAsync(UUID playerId, PlayerData data, Consumer<Boolean> callback) {
        tubingBukkitUtil.runAsync(() -> {
            boolean success = repository.save(playerId, data);

            // Callback on main thread
            tubingBukkitUtil.runTask(() -> {
                callback.accept(success);
            });
        });
    }

    public void loadAsync(UUID playerId, Consumer<PlayerData> callback) {
        tubingBukkitUtil.runAsync(() -> {
            PlayerData data = repository.load(playerId);

            // Callback on main thread
            tubingBukkitUtil.runTask(() -> {
                callback.accept(data);
            });
        });
    }
}
```

**Usage:**

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    playerDataService.loadAsync(player.getUniqueId(), data -> {
        // This runs on main thread after loading completes
        applyData(player, data);
    });
}
```

### Batch Operations

For multiple operations, batch them to reduce async thread switches:

```java
// BAD - Multiple async calls
public void saveAllPlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
        playerDataService.saveAsync(player.getUniqueId(), getData(player), result -> {});
    }
    // Creates many async tasks
}

// GOOD - Single async batch operation
public void saveAllPlayers() {
    Map<UUID, PlayerData> allData = new HashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
        allData.put(player.getUniqueId(), getData(player));
    }

    tubingBukkitUtil.runAsync(() -> {
        // Batch save on single async thread
        repository.saveAll(allData);
    });
}
```

Batch operations reduce:
- Thread pool contention
- Database connection overhead
- Context switching costs

### Async Rate Limiting

Prevent overwhelming the async thread pool:

```java
@IocBean
public class RateLimitedAsyncService {
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    @ConfigProperty("async.max-concurrent-tasks")
    private int maxConcurrentTasks;

    public void submitTask(Runnable task) {
        taskQueue.offer(task);
        processQueue();
    }

    private void processQueue() {
        while (activeTaskCount.get() < maxConcurrentTasks && !taskQueue.isEmpty()) {
            Runnable task = taskQueue.poll();
            if (task != null) {
                activeTaskCount.incrementAndGet();

                tubingBukkitUtil.runAsync(() -> {
                    try {
                        task.run();
                    } finally {
                        activeTaskCount.decrementAndGet();
                        processQueue();
                    }
                });
            }
        }
    }
}
```

This prevents creating too many concurrent async tasks, which can degrade performance.

## GUI Framework Performance

### GUI Building Performance

GUI building happens synchronously on the main thread. Keep it fast.

**Building Cost:**
- Empty GUI (no items): ~0.1ms
- GUI with 54 items: ~2-5ms
- GUI with complex lore/NBT: ~10-20ms

**Optimization: Pre-Build Static GUIs**

```java
@IocBean
public class MenuCache {
    private TubingGui mainMenu;

    @PostConstruct
    public void buildMenus() {
        mainMenu = new TubingGui.Builder("Main Menu", 27)
            .addItem(createHomeButton())
            .addItem(createWarpsButton())
            .addItem(createShopButton())
            .build();
    }

    public TubingGui getMainMenu() {
        return mainMenu;  // Instant access
    }
}
```

**Optimization: Use Templates for Dynamic Content**

Templates are optimized for repeated rendering:

```java
@GuiAction("shop:view")
public GuiTemplate viewShop() {
    List<ShopItem> items = shopService.getItems();

    return GuiTemplate.template("shop-listing", Map.of(
        "items", items,
        "page", 1
    ));
}
```

Templates are parsed once and cached. Only dynamic data is processed on each render.

### GUI Action Performance

GUI action execution is highly optimized:

**Execution Cost:**
- Action lookup: ~0.1ms (HashMap)
- Parameter parsing: ~0.1-0.5ms
- Method invocation: ~0.1ms
- Total: ~0.3-0.7ms per action

**Performance Tips:**

```java
// GOOD - Simple action methods
@GuiAction("home:teleport")
public GuiActionReturnType teleport(Player player, @GuiParam("name") String homeName) {
    homeService.teleport(player, homeName);
    return GuiActionReturnType.BACK;
}

// AVOID - Heavy logic in action method
@GuiAction("home:teleport")
public GuiActionReturnType teleport(Player player, @GuiParam("name") String homeName) {
    // Database query on main thread - BAD
    Home home = database.loadHome(player.getUniqueId(), homeName);
    player.teleport(home.getLocation());
    database.recordTeleport(player.getUniqueId(), home);
    return GuiActionReturnType.BACK;
}
```

Keep GUI actions lightweight. Delegate heavy work to services, and use async for database operations.

### Template Caching

GUI templates are automatically cached after first parse:

```java
// First access: ~10-50ms (parse XML/Freemarker)
GuiTemplate.template("complex-menu", params);

// Subsequent accesses: ~1-5ms (cached template + param substitution)
GuiTemplate.template("complex-menu", params);
```

**Performance Tip:** Use templates for complex, frequently accessed GUIs. The caching pays off after 2-3 accesses.

## Common Performance Pitfalls

### Pitfall 1: Database Queries on Main Thread

```java
// BAD - Blocks main thread
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    PlayerData data = database.loadPlayerData(event.getPlayer().getUniqueId());
    applyData(event.getPlayer(), data);
}

// GOOD - Async loading
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    tubingBukkitUtil.runAsync(() -> {
        PlayerData data = database.loadPlayerData(player.getUniqueId());

        tubingBukkitUtil.runTask(() -> {
            applyData(player, data);
        });
    });
}
```

**Impact:** Every player join blocks the server for 50-200ms. With 20 players joining simultaneously, the server freezes for seconds.

### Pitfall 2: Unnecessary Bean Lookups

```java
// BAD - Container lookup in hot path
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    // Lookup on every block break
    BlockService service = plugin.getIocContainer().get(BlockService.class);
    service.handleBreak(event);
}

// GOOD - Inject once in constructor
@IocBukkitListener
public class BlockListener implements Listener {
    private final BlockService blockService;

    public BlockListener(BlockService blockService) {
        this.blockService = blockService;  // Injected once
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        blockService.handleBreak(event);  // Direct field access
    }
}
```

**Impact:** Negligible per-lookup (0.01ms), but adds up with thousands of events per second.

### Pitfall 3: Heavy Work in Constructors

```java
// BAD - Blocks container initialization
@IocBean
public class DataService {
    private final List<Data> allData;

    public DataService(Database database) {
        // Loads 10,000 records during startup
        this.allData = database.loadAll();  // Blocks for seconds
    }
}

// GOOD - Lazy loading or AfterIocLoad
@IocBean
public class DataService {
    private final Database database;
    private List<Data> allData;

    public DataService(Database database) {
        this.database = database;  // Just store reference
    }

    public List<Data> getAllData() {
        if (allData == null) {
            allData = database.loadAll();  // Load on first access
        }
        return allData;
    }
}
```

**Impact:** Plugin takes 5-10 seconds to enable. Players see "Reloading" for extended periods.

### Pitfall 4: Creating New Objects in Hot Paths

```java
// BAD - Creates new list every call
public List<Home> getPlayerHomes(Player player) {
    return new ArrayList<>(homes.values().stream()
        .filter(h -> h.getOwner().equals(player.getUniqueId()))
        .collect(Collectors.toList()));
}

// GOOD - Cache per-player results
@IocBean
public class HomeCache {
    private final Map<UUID, List<Home>> playerHomesCache = new ConcurrentHashMap<>();

    public List<Home> getPlayerHomes(UUID playerId) {
        return playerHomesCache.computeIfAbsent(playerId, this::loadHomes);
    }

    public void invalidate(UUID playerId) {
        playerHomesCache.remove(playerId);
    }
}
```

**Impact:** Creates garbage that triggers frequent GC pauses. With 100 players, this can cause noticeable lag spikes.

### Pitfall 5: N+1 Query Problem

```java
// BAD - N+1 queries
public void loadPlayerData() {
    List<UUID> playerIds = database.getAllPlayerIds();  // 1 query

    for (UUID id : playerIds) {
        PlayerData data = database.loadPlayerData(id);  // N queries
        cache.put(id, data);
    }
    // Total: 1 + N queries
}

// GOOD - Batch loading
public void loadPlayerData() {
    Map<UUID, PlayerData> allData = database.loadAllPlayerData();  // 1 query
    allData.forEach(cache::put);
    // Total: 1 query
}
```

**Impact:** Loading 1,000 players takes 10+ seconds with N+1 queries, vs. under 1 second with batch loading.

### Pitfall 6: Excessive Configuration Access

```java
// BAD - Re-parsing config every call
public boolean canTeleport(Player player) {
    FileConfiguration config = plugin.getConfig();
    int cooldown = config.getInt("teleport.cooldown");
    boolean enabled = config.getBoolean("teleport.enabled");
    // ...
}

// GOOD - Inject once at startup
@IocBean
public class TeleportService {
    @ConfigProperty("teleport.cooldown")
    private int cooldown;

    @ConfigProperty("teleport.enabled")
    private boolean enabled;

    public boolean canTeleport(Player player) {
        // Direct field access
    }
}
```

**Impact:** Config access is relatively fast, but repeated access adds up. Field access is always faster.

## Best Practices for High-Performance Plugins

### 1. Use Constructor Injection

```java
// BEST PRACTICE
@IocBean
public class PlayerService {
    private final PlayerRepository repository;
    private final ConfigurationLoader config;

    public PlayerService(PlayerRepository repository,
                        ConfigurationLoader config) {
        this.repository = repository;
        this.config = config;
    }
}
```

Benefits:
- Dependencies injected once at startup
- No container lookups at runtime
- Clear dependencies in constructor signature
- Easier to test

### 2. Prefer @ConfigProperty Over ConfigurationLoader

```java
// BEST PRACTICE
@IocBean
public class FeatureService {
    @ConfigProperty("features.max-limit")
    private int maxLimit;

    @ConfigProperty("features.enabled")
    private boolean enabled;
}
```

Benefits:
- Direct field access (fastest)
- No HashMap lookups
- Clear configuration dependencies
- Type-safe

### 3. Cache Expensive Computations

```java
@IocBean
public class PermissionService {
    private final Map<String, Boolean> permissionCache = new ConcurrentHashMap<>();

    public boolean hasPermission(Player player, String permission) {
        String key = player.getUniqueId() + ":" + permission;
        return permissionCache.computeIfAbsent(key, k ->
            player.hasPermission(permission)
        );
    }

    public void invalidate(Player player) {
        permissionCache.keySet().removeIf(k -> k.startsWith(player.getUniqueId() + ":"));
    }
}
```

### 4. Use Async for All I/O

```java
// Database queries
tubingBukkitUtil.runAsync(() -> {
    database.save(data);
});

// File operations
tubingBukkitUtil.runAsync(() -> {
    saveToFile(data);
});

// HTTP requests
tubingBukkitUtil.runAsync(() -> {
    apiClient.fetch(url);
});
```

### 5. Batch Similar Operations

```java
// Instead of saving one at a time
for (Player player : players) {
    database.save(player.getUniqueId(), getData(player));
}

// Batch save
Map<UUID, PlayerData> batch = new HashMap<>();
for (Player player : players) {
    batch.put(player.getUniqueId(), getData(player));
}
database.saveAll(batch);
```

### 6. Use Lazy Initialization

```java
@IocBean
public class ResourceIntensiveService {
    private HeavyObject heavyObject;

    public HeavyObject getHeavyObject() {
        if (heavyObject == null) {
            heavyObject = new HeavyObject();  // Only create when needed
        }
        return heavyObject;
    }
}
```

### 7. Limit Collection Sizes

```java
@IocBean
public class ChatHistoryService {
    private final Map<UUID, Deque<String>> chatHistory = new ConcurrentHashMap<>();

    @ConfigProperty("chat.history-size")
    private int maxHistorySize;

    public void addMessage(UUID playerId, String message) {
        Deque<String> history = chatHistory.computeIfAbsent(playerId,
            k -> new ArrayDeque<>());

        history.addLast(message);

        // Limit size to prevent memory leaks
        while (history.size() > maxHistorySize) {
            history.removeFirst();
        }
    }
}
```

### 8. Clean Up on Player Disconnect

```java
@IocBukkitListener
public class CleanupListener implements Listener {
    private final PlayerDataCache cache;

    public CleanupListener(PlayerDataCache cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Free memory by removing cached data
        cache.remove(event.getPlayer().getUniqueId());
    }
}
```

### 9. Use Priority Sparingly

```java
// ONLY use priority for infrastructure beans
@IocBean(priority = true)
public class ConfigurationLoader {
    // Must load before other beans
}

// Normal beans should NOT use priority
@IocBean
public class PlayerService {
    // Normal priority is fine
}
```

### 10. Profile Before Optimizing

Use timing tools to identify bottlenecks:

```java
@IocBean
public class ProfiledService {
    public void expensiveOperation() {
        long start = System.nanoTime();

        // Your operation
        doWork();

        long duration = System.nanoTime() - start;
        if (duration > 1_000_000) {  // > 1ms
            plugin.getLogger().warning("Slow operation: " + duration / 1_000_000.0 + "ms");
        }
    }
}
```

Use plugins like Spark or Timings to identify performance issues.

## Performance Monitoring

### Startup Time Monitoring

```java
@TubingConfiguration
public class PerformanceConfiguration {

    @AfterIocLoad
    public static void logStartupTime(@InjectTubingPlugin TubingPlugin plugin) {
        long startTime = System.currentTimeMillis();
        // Container already initialized at this point
        plugin.getLogger().info("Container initialized in ~" +
            (System.currentTimeMillis() - startTime) + "ms");
    }
}
```

### Runtime Performance Metrics

```java
@IocBean
public class PerformanceMetrics {
    private final AtomicLong databaseQueryCount = new AtomicLong();
    private final AtomicLong totalQueryTime = new AtomicLong();

    public void recordQuery(long durationMs) {
        databaseQueryCount.incrementAndGet();
        totalQueryTime.addAndGet(durationMs);
    }

    public void logMetrics() {
        long count = databaseQueryCount.get();
        long total = totalQueryTime.get();
        double avg = count == 0 ? 0 : (double) total / count;

        plugin.getLogger().info("Database queries: " + count +
            ", avg time: " + avg + "ms");
    }
}
```

## Summary

Performance in Tubing plugins comes down to a few key principles:

1. **Container Overhead is Minimal**: Bean instantiation and lookup are negligible in production
2. **Configuration is Cached**: Access via `@ConfigProperty` for best performance
3. **Use Async for I/O**: Never block the main thread with database or file operations
4. **Cache Intelligently**: Cache expensive computations and database results
5. **Optimize GUI Building**: Pre-build static GUIs and use templates for dynamic content
6. **Avoid Pitfalls**: Don't do heavy work in constructors, avoid N+1 queries, batch operations

The IoC container itself adds negligible overhead compared to manual object management. Focus optimization efforts on database access, async patterns, and caching strategies.

## Next Steps

- **[Bean Lifecycle](../core/Bean-Lifecycle.md)** - Understanding container initialization
- **[GUI Async](../gui/GUI-Async.md)** - Async patterns for GUI operations
- **[Configuration Files](../core/Configuration-Files.md)** - Configuration loading and caching
- **[Quick Start](../getting-started/Quick-Start.md)** - Building your first performant plugin

---

**See also:**
- [IoC Container](../core/IoC-Container.md) - Container fundamentals
- [Dependency Injection](../core/Dependency-Injection.md) - Injection patterns
- [GUI Controllers](../gui/GUI-Controllers.md) - GUI action performance
