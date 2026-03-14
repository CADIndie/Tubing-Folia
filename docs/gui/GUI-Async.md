# GUI Async

This guide covers asynchronous GUI operations in Tubing, allowing you to perform long-running operations without blocking the main server thread. Learn how to use AsyncGui for database queries, API calls, and other blocking operations while showing loading states and handling errors properly.

## Overview

The Tubing GUI framework provides AsyncGui to handle operations that take time to complete, such as:

- Database queries and updates
- File I/O operations
- HTTP/API requests
- Complex calculations
- External service calls

AsyncGui allows you to execute these operations on a background thread while maintaining a responsive server. The framework automatically:

- Executes your async code on a separate thread
- Returns control to the main thread for GUI operations
- Handles errors that occur during async execution
- Integrates with the exception handling system

## Why Use AsyncGui

### The Problem: Main Thread Blocking

Without async operations, blocking calls freeze the entire server:

```java
// BAD - Blocks the main thread
@GuiAction("stats:view")
public TubingGui viewStats(Player player) {
    // This blocks the main thread for 2+ seconds!
    PlayerStats stats = database.loadStatsFromDatabase(player.getUniqueId());

    return buildStatsGui(stats);
}
```

**Problems:**
- Server freezes for all players during the database query
- Server tick rate drops, causing lag
- Other plugins and operations are delayed
- Poor user experience for all players

### The Solution: AsyncGui

AsyncGui moves blocking operations off the main thread:

```java
// GOOD - Runs asynchronously
@GuiAction("stats:view")
public AsyncGui<TubingGui> viewStats(Player player) {
    return AsyncGui.async(() -> {
        // Runs on async thread - doesn't block server
        PlayerStats stats = database.loadStatsFromDatabase(player.getUniqueId());

        // GUI building happens here, result processed on main thread
        return buildStatsGui(stats);
    });
}
```

**Benefits:**
- Server remains responsive during operation
- Only the requesting player waits
- Other players unaffected
- Professional user experience

## Basic AsyncGui Usage

### Creating an AsyncGui

The basic pattern uses `AsyncGui.async()` with a lambda:

```java
@GuiController
public class DataViewController {
    private final DatabaseService database;

    public DataViewController(DatabaseService database) {
        this.database = database;
    }

    @GuiAction("data:load")
    public AsyncGui<TubingGui> loadData(Player player, @GuiParam("id") String dataId) {
        return AsyncGui.async(() -> {
            // This code runs asynchronously
            DataModel data = database.loadData(dataId);

            // Build and return the GUI
            return new TubingGui.Builder("Data View", 54)
                .addItem(buildDataItem(data))
                .build();
        });
    }

    private TubingGuiItem buildDataItem(DataModel data) {
        // Build item from data
        // ...
    }
}
```

### How It Works

When you return an `AsyncGui`:

1. **Async Execution Phase**
   - Framework detects `AsyncGui` return type
   - Executes the lambda on an async thread via `ITubingBukkitUtil.runAsync()`
   - Player's current GUI remains open during execution

2. **Result Processing Phase**
   - When lambda completes, result is captured
   - Framework schedules result processing on main thread (1 tick delay)
   - Result is processed as if returned directly (opens GUI, redirects, etc.)

3. **Error Handling Phase**
   - Any exceptions in async code are caught
   - Exceptions are routed to registered `GuiExceptionHandler`
   - GUI is closed and history is cleared on error

### Generic Type Parameter

AsyncGui is generic and can wrap any valid return type:

```java
// Wrap TubingGui
AsyncGui<TubingGui>

// Wrap GuiTemplate
AsyncGui<GuiTemplate>

// Wrap String (redirect)
AsyncGui<String>

// Wrap GuiActionReturnType
AsyncGui<GuiActionReturnType>
```

The inner type determines what happens when the async operation completes.

## Loading GUI Pattern

While async work executes, you typically want to show a loading GUI to provide feedback to the player.

### Two-Action Pattern

The recommended approach uses two actions: one that shows the loading GUI, and one that performs the async work.

```java
@GuiController
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GuiAction("leaderboard:view")
    public TubingGui showLoadingScreen(Player player) {
        // Show loading GUI immediately
        TubingGui.Builder builder = new TubingGui.Builder("Leaderboard", 27);

        // Add loading indicator
        builder.addItem(new TubingGuiItem.Builder(null, 13)
            .withItemStack(createLoadingItem())
            .withLeftClickAction("leaderboard:load") // Trigger async load
            .build());

        return builder.build();
    }

    @GuiAction("leaderboard:load")
    public AsyncGui<TubingGui> loadLeaderboard(Player player) {
        return AsyncGui.async(() -> {
            // Load data asynchronously
            List<LeaderboardEntry> entries = leaderboardService.getTopPlayers(10);

            // Build final GUI with data
            return buildLeaderboardGui(entries);
        });
    }

    private TubingGuiItemStack createLoadingItem() {
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart("Loading...", "&e"));

        List<TubingGuiText> lore = new ArrayList<>();
        TubingGuiText loreLine = new TubingGuiText();
        loreLine.addPart(new TubingGuiTextPart("Please wait", "&7"));
        lore.add(loreLine);

        return new TubingGuiItemStack(
            1,
            Material.HOPPER,
            name,
            false,
            lore
        );
    }

    private TubingGui buildLeaderboardGui(List<LeaderboardEntry> entries) {
        TubingGui.Builder builder = new TubingGui.Builder("Leaderboard", 54);

        int slot = 0;
        for (LeaderboardEntry entry : entries) {
            builder.addItem(createEntryItem(entry, slot++));
        }

        return builder.build();
    }
}
```

**Flow:**
1. Player clicks to view leaderboard
2. `leaderboard:view` action executes immediately, showing loading GUI
3. Loading GUI has an item that triggers `leaderboard:load`
4. `leaderboard:load` executes async, loads data
5. When complete, loading GUI is replaced with data GUI

### Auto-Trigger Pattern

For automatic loading without requiring a click, trigger the async action programmatically:

```java
@GuiController
public class StatsController {
    private final GuiActionService guiActionService;
    private final StatsService statsService;

    public StatsController(GuiActionService guiActionService,
                          StatsService statsService) {
        this.guiActionService = guiActionService;
        this.statsService = statsService;
    }

    @GuiAction("stats:view")
    public TubingGui showLoadingAndLoad(Player player) {
        // Schedule async load after 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            guiActionService.executeAction(player, "stats:load");
        }, 1L);

        // Return loading GUI immediately
        return createLoadingGui();
    }

    @GuiAction("stats:load")
    public AsyncGui<TubingGui> loadStats(Player player) {
        return AsyncGui.async(() -> {
            PlayerStats stats = statsService.loadStats(player.getUniqueId());
            return buildStatsGui(stats);
        });
    }
}
```

**Flow:**
1. Player opens stats GUI
2. Loading GUI shown immediately
3. Async load scheduled for next tick
4. Data loads in background
5. Loading GUI replaced when data ready

### Loading GUI Best Practices

**Visual Feedback:**
```java
private TubingGui createLoadingGui() {
    TubingGui.Builder builder = new TubingGui.Builder("Loading...", 27);

    // Centered loading indicator
    builder.addItem(new TubingGuiItem.Builder(null, 13)
        .withItemStack(createAnimatedLoadingItem())
        .withLeftClickAction(TubingGuiActions.NOOP)
        .build());

    // Optional: Cancel button
    builder.addItem(new TubingGuiItem.Builder(null, 18)
        .withItemStack(createCancelItem())
        .withLeftClickAction(TubingGuiActions.BACK)
        .build());

    return builder.build();
}
```

**Tips:**
- Use materials that suggest loading: HOPPER, CLOCK, COMPASS
- Add descriptive text: "Loading data...", "Fetching results..."
- Consider a cancel/back button for long operations
- Keep the loading GUI simple and lightweight

## Thread Safety Considerations

When working with async operations, thread safety is critical to avoid crashes and data corruption.

### Safe: Reading Player Data

Reading player data asynchronously is generally safe:

```java
@GuiAction("profile:view")
public AsyncGui<TubingGui> viewProfile(Player player) {
    return AsyncGui.async(() -> {
        UUID uuid = player.getUniqueId();        // SAFE - immutable
        String name = player.getName();          // SAFE - returns copy

        // Load from database
        ProfileData profile = database.loadProfile(uuid);

        return buildProfileGui(profile);
    });
}
```

**Safe Operations:**
- `player.getUniqueId()` - Returns immutable UUID
- `player.getName()` - Returns string copy
- Reading from thread-safe collections
- Accessing final fields
- Database queries
- File I/O
- HTTP requests

### Unsafe: Bukkit API Calls

Most Bukkit API methods are NOT thread-safe and will throw exceptions if called from async threads:

```java
@GuiAction("inventory:scan")
public AsyncGui<TubingGui> scanInventory(Player player) {
    return AsyncGui.async(() -> {
        // UNSAFE - Will throw IllegalStateException!
        ItemStack[] contents = player.getInventory().getContents();

        // UNSAFE - Bukkit world access
        Location loc = player.getLocation();

        // UNSAFE - Modifying player state
        player.sendMessage("Scanning...");

        return buildGui();
    });
}
```

**Common Unsafe Operations:**
- `player.getInventory()` - Inventory access
- `player.getLocation()` - Location/world access
- `player.sendMessage()` - Chat/messaging
- `player.teleport()` - Movement
- `Bukkit.getPlayer()` - Player lookup
- `world.getBlockAt()` - World access
- Modifying entities or blocks

### Solution: Collect Data First

Collect necessary data before entering async context:

```java
@GuiAction("location:save")
public AsyncGui<GuiActionReturnType> saveLocation(Player player) {
    // Collect data on main thread
    UUID playerId = player.getUniqueId();
    Location location = player.getLocation().clone(); // Clone for safety
    String playerName = player.getName();

    return AsyncGui.async(() -> {
        // Now safe to use in async context
        database.saveLocation(playerId, location);

        // Build result
        return GuiActionReturnType.BACK;
    });
}
```

### Solution: Sync Callbacks

For operations requiring Bukkit API, use sync callbacks:

```java
@GuiAction("reward:claim")
public AsyncGui<TubingGui> claimReward(Player player, @GuiParam("id") String rewardId) {
    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        // Async: Load reward data
        RewardData reward = database.loadReward(rewardId);

        // Async: Validate and mark claimed
        if (reward.canClaim(playerId)) {
            database.markClaimed(rewardId, playerId);

            // Sync: Give items to player
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    p.getInventory().addItem(reward.getItems());
                    p.sendMessage("Reward claimed!");
                }
            });
        }

        return buildRewardGui();
    });
}
```

**Pattern:**
1. Async: Load/process data
2. Async: Update database
3. Sync callback: Interact with Bukkit API
4. Async: Build GUI result

## Error Handling in Async Operations

Exceptions thrown in async operations are automatically caught and routed to exception handlers.

### Basic Error Handling

Any exception in async code is caught:

```java
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData(Player player, @GuiParam("id") String id) {
    return AsyncGui.async(() -> {
        // If this throws an exception:
        DataModel data = database.loadData(id);

        // Exception is caught, player's GUI is closed,
        // and exception handlers are invoked
        return buildDataGui(data);
    });
}
```

**Automatic Behavior:**
1. Exception is caught by `processAsyncGuiAction`
2. Exception is passed to `handleException`
3. Matching `GuiExceptionHandler` is invoked (if registered)
4. Player's inventory is closed
5. Navigation history is cleared

### Creating Exception Handlers

Register exception handlers to provide custom error messages:

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {

    private final MessageService messageService;

    public DatabaseExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void accept(Player player, SQLException exception) {
        // Log the error
        plugin.getLogger().log(Level.SEVERE, "Database error for player " + player.getName(), exception);

        // Send user-friendly message
        messageService.send(player, "&cDatabase error! Please try again later.");

        // Optionally notify admins
        notifyAdmins("Database error: " + exception.getMessage());
    }
}
```

**Key Points:**
- Use `@GuiExceptionHandlerProvider` with exception types
- Implement `GuiExceptionHandler<T>` interface
- Exception type `T` is the exception you're handling
- Handler automatically registered on plugin load

### Multiple Exception Types

Handle multiple exception types with one handler:

```java
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {IOException.class, TimeoutException.class, SQLException.class}
)
public class DataLoadExceptionHandler implements GuiExceptionHandler<Exception> {

    @Override
    public void accept(Player player, Exception exception) {
        if (exception instanceof IOException) {
            player.sendMessage("&cFile error! Please contact an admin.");
        } else if (exception instanceof TimeoutException) {
            player.sendMessage("&cOperation timed out. Please try again.");
        } else if (exception instanceof SQLException) {
            player.sendMessage("&cDatabase error! Please try again later.");
        }

        // Log all errors
        logError(player, exception);
    }
}
```

### Exception Hierarchy

Handlers support exception inheritance:

```java
// This handler catches IOException and all subclasses
@GuiExceptionHandlerProvider(exceptions = {IOException.class})
public class IOExceptionHandler implements GuiExceptionHandler<IOException> {
    @Override
    public void accept(Player player, IOException exception) {
        // Handles IOException, FileNotFoundException, etc.
        player.sendMessage("&cFile operation failed!");
    }
}
```

The framework searches for handlers in this order:
1. Exact exception type match
2. Parent exception type (via `isAssignableFrom`)
3. If no handler found, exception is re-thrown

### Graceful Degradation

Handle errors gracefully by returning fallback GUIs:

```java
@GuiAction("leaderboard:view")
public AsyncGui<TubingGui> viewLeaderboard(Player player) {
    return AsyncGui.async(() -> {
        try {
            List<LeaderboardEntry> entries = database.getLeaderboard();
            return buildLeaderboardGui(entries);

        } catch (SQLException e) {
            // Log error
            plugin.getLogger().log(Level.WARNING, "Failed to load leaderboard", e);

            // Return error GUI instead of crashing
            return buildErrorGui("Failed to load leaderboard. Please try again later.");
        }
    });
}

private TubingGui buildErrorGui(String message) {
    return new TubingGui.Builder("Error", 27)
        .addItem(new TubingGuiItem.Builder(null, 13)
            .withItemStack(createErrorItem(message))
            .withLeftClickAction(TubingGuiActions.BACK)
            .build())
        .build();
}
```

**Benefits:**
- User sees error message in GUI context
- Can retry or navigate back
- Error is logged for admins
- Better UX than sudden GUI close

## Best Practices

### 1. Always Use AsyncGui for Blocking Operations

```java
// GOOD - Database query in async
@GuiAction("stats:view")
public AsyncGui<TubingGui> viewStats(Player player) {
    return AsyncGui.async(() -> {
        PlayerStats stats = database.loadStats(player.getUniqueId());
        return buildStatsGui(stats);
    });
}

// BAD - Database query blocks main thread
@GuiAction("stats:view")
public TubingGui viewStats(Player player) {
    PlayerStats stats = database.loadStats(player.getUniqueId());
    return buildStatsGui(stats);
}
```

**Use AsyncGui for:**
- Database queries (SELECT, INSERT, UPDATE, DELETE)
- File reading/writing
- HTTP/API requests
- Complex computations (pathfinding, analysis)
- External service calls

**Don't use AsyncGui for:**
- Simple data formatting
- Basic object creation
- In-memory operations
- Cached data access

### 2. Show Loading States

```java
// GOOD - Shows loading feedback
@GuiAction("data:view")
public TubingGui showData(Player player) {
    // Show loading GUI
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        guiActionService.executeAction(player, "data:load-async");
    }, 1L);

    return createLoadingGui("Loading data...");
}

@GuiAction("data:load-async")
public AsyncGui<TubingGui> loadData(Player player) {
    return AsyncGui.async(() -> {
        Data data = database.load();
        return buildDataGui(data);
    });
}

// BAD - No feedback during load
@GuiAction("data:view")
public AsyncGui<TubingGui> showData(Player player) {
    return AsyncGui.async(() -> {
        // Player sees nothing while this loads
        Data data = database.load();
        return buildDataGui(data);
    });
}
```

### 3. Collect Bukkit Data Before Async

```java
// GOOD - Collect data first
@GuiAction("location:analyze")
public AsyncGui<TubingGui> analyzeLocation(Player player) {
    // Collect on main thread
    Location loc = player.getLocation().clone();
    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        // Use collected data in async
        LocationData analysis = analyzer.analyze(loc);
        database.saveAnalysis(playerId, analysis);
        return buildAnalysisGui(analysis);
    });
}

// BAD - Bukkit API in async
@GuiAction("location:analyze")
public AsyncGui<TubingGui> analyzeLocation(Player player) {
    return AsyncGui.async(() -> {
        // CRASH! Can't call getLocation() in async
        Location loc = player.getLocation();
        return buildGui();
    });
}
```

### 4. Handle Errors Gracefully

```java
// GOOD - Comprehensive error handling
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData(Player player) {
    return AsyncGui.async(() -> {
        try {
            Data data = database.load();
            return buildDataGui(data);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error", e);
            return buildErrorGui("Database error. Please try again.");

        } catch (TimeoutException e) {
            plugin.getLogger().log(Level.WARNING, "Database timeout", e);
            return buildErrorGui("Operation timed out. Please try again.");
        }
    });
}

// Also register exception handlers
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class SqlExceptionHandler implements GuiExceptionHandler<SQLException> {
    @Override
    public void accept(Player player, SQLException e) {
        player.sendMessage("&cDatabase error! Please contact an admin.");
    }
}
```

### 5. Avoid Long-Running Operations

```java
// GOOD - Reasonable timeout
@GuiAction("api:fetch")
public AsyncGui<TubingGui> fetchFromApi(Player player) {
    return AsyncGui.async(() -> {
        ApiData data = apiClient.fetch(
            5, TimeUnit.SECONDS // Reasonable timeout
        );
        return buildDataGui(data);
    });
}

// BAD - Could hang for minutes
@GuiAction("api:fetch")
public AsyncGui<TubingGui> fetchFromApi(Player player) {
    return AsyncGui.async(() -> {
        // No timeout - could hang indefinitely
        ApiData data = apiClient.fetch();
        return buildDataGui(data);
    });
}
```

**Guidelines:**
- Set reasonable timeouts (5-30 seconds)
- Cancel long operations if player disconnects
- Consider pagination for large data sets
- Cache frequently accessed data

### 6. Use Appropriate Return Types

```java
// Return TubingGui for new GUI
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData() {
    return AsyncGui.async(() -> {
        Data data = loadFromDb();
        return buildGui(data);
    });
}

// Return GuiTemplate for template-based GUI
@GuiAction("shop:load")
public AsyncGui<GuiTemplate> loadShop() {
    return AsyncGui.async(() -> {
        List<Item> items = loadItems();
        Map<String, Object> params = Map.of("items", items);
        return GuiTemplate.template("shop-main", params);
    });
}

// Return String for redirect
@GuiAction("validate:entry")
public AsyncGui<String> validateEntry(@GuiParam("id") String id) {
    return AsyncGui.async(() -> {
        if (database.exists(id)) {
            return "entry:view?id=" + id;
        } else {
            return "entry:not-found";
        }
    });
}

// Return GuiActionReturnType for close/back
@GuiAction("data:save")
public AsyncGui<GuiActionReturnType> saveData(Player player) {
    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        database.save(playerId);
        return GuiActionReturnType.BACK;
    });
}
```

### 7. Consider Caching

```java
// GOOD - Cache frequently accessed data
@IocBean
public class LeaderboardController {
    private final LeaderboardCache cache;

    @GuiAction("leaderboard:view")
    public TubingGui viewLeaderboard(Player player) {
        // Check cache first
        if (cache.isValid()) {
            return buildLeaderboardGui(cache.getData());
        }

        // Cache miss - load async
        guiActionService.executeAction(player, "leaderboard:load");
        return createLoadingGui();
    }

    @GuiAction("leaderboard:load")
    public AsyncGui<TubingGui> loadLeaderboard(Player player) {
        return AsyncGui.async(() -> {
            List<Entry> data = database.loadLeaderboard();
            cache.update(data); // Update cache
            return buildLeaderboardGui(data);
        });
    }
}
```

### 8. Validate Input Before Async

```java
// GOOD - Validate synchronously
@GuiAction("player:lookup")
public AsyncGui<TubingGui> lookupPlayer(Player viewer, @GuiParam("name") String playerName) {
    // Validate input first
    if (playerName == null || playerName.isEmpty()) {
        viewer.sendMessage("&cInvalid player name!");
        return AsyncGui.async(() -> GuiActionReturnType.KEEP_OPEN);
    }

    if (!playerName.matches("^[a-zA-Z0-9_]{3,16}$")) {
        viewer.sendMessage("&cInvalid player name format!");
        return AsyncGui.async(() -> GuiActionReturnType.KEEP_OPEN);
    }

    // Valid input - proceed with async lookup
    return AsyncGui.async(() -> {
        PlayerData data = database.lookupPlayer(playerName);
        return buildPlayerGui(data);
    });
}
```

## Common Patterns

### Pattern: Paginated Async Loading

```java
@GuiAction("logs:view")
public AsyncGui<GuiTemplate> viewLogs(
        Player player,
        @GuiParam(value = "page", defaultValue = "1") int page) {

    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        int pageSize = 45;
        List<LogEntry> logs = database.loadLogs(playerId, page, pageSize);
        int totalPages = database.countLogPages(playerId, pageSize);

        Map<String, Object> params = new HashMap<>();
        params.put("logs", logs);
        params.put("page", page);
        params.put("totalPages", totalPages);
        params.put("hasNext", page < totalPages);
        params.put("hasPrevious", page > 1);

        return GuiTemplate.template("logs-view", params);
    });
}
```

### Pattern: Async Confirmation

```java
@GuiAction("item:delete")
public TubingGui confirmDelete(@GuiParam("id") String itemId) {
    return new TubingGui.Builder("Confirm Delete", 27)
        .addItem(confirmButton("item:delete-async?id=" + itemId))
        .addItem(cancelButton())
        .build();
}

@GuiAction("item:delete-async")
public AsyncGui<GuiActionReturnType> deleteItem(
        Player player,
        @GuiParam("id") String itemId) {

    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        database.deleteItem(playerId, itemId);

        // Sync callback to notify player
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage("&aItem deleted successfully!");
        });

        return GuiActionReturnType.BACK;
    });
}
```

### Pattern: Async Search

```java
@GuiAction("search:execute")
public AsyncGui<TubingGui> search(
        Player player,
        @GuiParam("query") String query) {

    // Validate query
    if (query == null || query.length() < 3) {
        player.sendMessage("&cSearch query must be at least 3 characters!");
        return AsyncGui.async(() -> GuiActionReturnType.BACK);
    }

    return AsyncGui.async(() -> {
        List<SearchResult> results = searchService.search(query, 50);

        if (results.isEmpty()) {
            return buildNoResultsGui(query);
        }

        return buildSearchResultsGui(query, results);
    });
}
```

### Pattern: Async Batch Operations

```java
@GuiAction("batch:process")
public AsyncGui<TubingGui> batchProcess(
        Player player,
        @InteractableItems List<ItemStack> items) {

    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        List<ProcessResult> results = new ArrayList<>();

        for (ItemStack item : items) {
            ProcessResult result = processor.process(item);
            results.add(result);
        }

        database.saveResults(playerId, results);

        return buildResultsGui(results);
    });
}
```

## Related Topics

- **[GUI Controllers](GUI-Controllers.md)** - Learn about action routing and parameters
- **[GUI Setup](GUI-Setup.md)** - Understanding the GUI framework fundamentals
- **[GUI Building](GUI-Building.md)** - Programmatically building GUIs
- **[Exception Handling](../core/Exception-Handling.md)** - Exception handling across Tubing
