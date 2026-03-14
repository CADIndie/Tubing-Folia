# Bukkit Event Listeners

Tubing provides a streamlined approach to Bukkit event handling that eliminates manual registration boilerplate and enables automatic dependency injection in listeners. This guide covers everything you need to know about creating and managing event listeners with Tubing.

## Overview

The Tubing event listener system offers several advantages over traditional Bukkit listener registration:

- **Automatic Registration**: Listeners are discovered and registered automatically
- **Dependency Injection**: Listeners receive dependencies via constructor injection
- **Conditional Loading**: Enable/disable listeners based on configuration properties
- **Multi-Provider Support**: Organize multiple listeners implementing common interfaces
- **Clean Code**: No manual `Bukkit.getPluginManager().registerEvents()` calls
- **Testability**: Easy to test with constructor injection

## Basic Event Listener

### Simple Listener

The simplest way to create an event listener is to implement Bukkit's `Listener` interface and use the `@IocBukkitListener` annotation:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@IocBukkitListener
public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome to the server!");
    }
}
```

**What happens:**
1. Tubing scans your plugin's package and finds classes annotated with `@IocBukkitListener`
2. The listener is instantiated as a bean (all dependencies are injected)
3. Tubing automatically calls `Bukkit.getPluginManager().registerEvents(bean, plugin)`
4. Your listener is now active and will receive events

**Important:** Classes must implement Bukkit's `Listener` interface. If you annotate a non-Listener class, Tubing will throw an `IocException`:

```
IocException: IocListener annotation can only be used on bukkit Listeners. Failing class [com.example.NotAListener]
```

### Listener with Dependencies

The real power of Tubing listeners comes from dependency injection:

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    private final PlayerService playerService;
    private final MessageService messageService;

    // Dependencies injected via constructor
    public PlayerJoinListener(PlayerService playerService,
                             MessageService messageService) {
        this.playerService = playerService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Use injected services
        playerService.loadPlayerData(player);
        messageService.sendWelcomeMessage(player);
    }
}
```

All dependencies are automatically resolved and injected by the IoC container. No manual instantiation needed!

### Listener with Configuration

Combine constructor injection with configuration property injection:

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    private final PlayerService playerService;

    @ConfigProperty("features.welcome-message.enabled")
    private boolean welcomeEnabled;

    @ConfigProperty("features.welcome-message.text")
    private String welcomeText;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        playerService.loadPlayerData(player);

        if (welcomeEnabled) {
            player.sendMessage(welcomeText);
        }
    }
}
```

**Configuration file (config.yml):**
```yaml
features:
  welcome-message:
    enabled: true
    text: "&aWelcome to the server!"
```

## Event Priority

Event priority controls the order in which event handlers are called. Bukkit provides six priority levels, from lowest to highest:

### Using EventPriority

Specify priority using Bukkit's standard `@EventHandler` annotation:

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@IocBukkitListener
public class PlayerChatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatLowest(AsyncPlayerChatEvent event) {
        // Called first - good for preprocessing
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChatLow(AsyncPlayerChatEvent event) {
        // Called after LOWEST
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChatNormal(AsyncPlayerChatEvent event) {
        // Default priority - most listeners use this
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChatHigh(AsyncPlayerChatEvent event) {
        // Called after NORMAL
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatHighest(AsyncPlayerChatEvent event) {
        // Called last before MONITOR
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMonitor(AsyncPlayerChatEvent event) {
        // Called last - for read-only monitoring
        // NEVER modify the event here!
    }
}
```

### Priority Guidelines

**LOWEST** - First to run, ideal for:
- Event preprocessing
- Early validation
- Setting up event context

**LOW** - Early processing:
- Initial transformations
- Priority-based handling

**NORMAL** (default) - Standard handling:
- Most event handlers should use this
- General business logic
- Default if not specified

**HIGH** - Late processing:
- Overriding default behavior
- Final validation

**HIGHEST** - Last chance to modify:
- Final event modifications
- Critical overrides

**MONITOR** - Read-only observation:
- Logging events
- Statistics collection
- **NEVER modify the event at this priority**

### Priority Example: Chat Filter

```java
@IocBukkitListener
public class ChatFilterListener implements Listener {

    private final ProfanityFilter profanityFilter;
    private final ChatLogger chatLogger;

    public ChatFilterListener(ProfanityFilter profanityFilter,
                             ChatLogger chatLogger) {
        this.profanityFilter = profanityFilter;
        this.chatLogger = chatLogger;
    }

    // Filter chat early - before other plugins process it
    @EventHandler(priority = EventPriority.LOWEST)
    public void filterChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        String filtered = profanityFilter.filter(message);
        event.setMessage(filtered);
    }

    // Log chat after all modifications - read-only
    @EventHandler(priority = EventPriority.MONITOR)
    public void logChat(AsyncPlayerChatEvent event) {
        if (!event.isCancelled()) {
            chatLogger.log(event.getPlayer(), event.getMessage());
        }
    }
}
```

### Ignoring Cancelled Events

Use `ignoreCancelled = true` to skip handling events that have been cancelled by other plugins:

```java
@IocBukkitListener
public class BlockBreakListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Only called if event is not cancelled
        // Won't run if another plugin cancelled the event
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockBreakAlways(BlockBreakEvent event) {
        // Called even if event is cancelled
        // Useful for monitoring/logging
    }
}
```

## Conditional Listeners

Enable or disable listeners based on configuration properties using `conditionalOnProperty`:

### Basic Conditional Loading

```java
@IocBukkitListener(conditionalOnProperty = "features.pvp.enabled")
public class PvpListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only registered if features.pvp.enabled = true
    }
}
```

**Configuration:**
```yaml
features:
  pvp:
    enabled: true
```

If `features.pvp.enabled` is `false` or not defined, the listener **will not be created or registered**.

### Multiple Conditions (AND Logic)

Use `&&` to require multiple conditions:

```java
@IocBukkitListener(conditionalOnProperty = "features.economy.enabled && features.shops.enabled")
public class ShopListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Only loaded if BOTH conditions are true
    }
}
```

**Configuration:**
```yaml
features:
  economy:
    enabled: true
  shops:
    enabled: true  # Both must be true
```

### Conditional Examples

**Feature Toggle:**
```java
@IocBukkitListener(conditionalOnProperty = "features.anti-cheat.enabled")
public class AntiCheatListener implements Listener {
    // Only active when anti-cheat is enabled
}
```

**Environment-Specific:**
```java
@IocBukkitListener(conditionalOnProperty = "environment=production")
public class ProductionMonitoringListener implements Listener {
    // Only loaded in production environment
}
```

**Complex Conditions:**
```java
@IocBukkitListener(conditionalOnProperty = "features.combat.enabled && features.combat.damage-indicator=true")
public class DamageIndicatorListener implements Listener {
    // Loaded when combat is enabled AND damage indicators are enabled
}
```

## Dependency Injection in Listeners

Listeners support all Tubing dependency injection patterns.

### Service Dependencies

```java
@IocBukkitListener
public class PlayerDataListener implements Listener {

    private final PlayerRepository repository;
    private final DatabaseService database;
    private final CacheService cache;

    public PlayerDataListener(PlayerRepository repository,
                             DatabaseService database,
                             CacheService cache) {
        this.repository = repository;
        this.database = database;
        this.cache = cache;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Try cache first
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {
            // Load from database
            data = repository.load(player.getUniqueId());
            cache.put(player.getUniqueId(), data);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Save to database
        PlayerData data = cache.get(player.getUniqueId());
        if (data != null) {
            repository.save(data);
            cache.remove(player.getUniqueId());
        }
    }
}
```

### Interface Injection

Depend on interfaces for loose coupling:

```java
public interface NotificationService {
    void notify(Player player, String message);
}

@IocBean
public class BukkitNotificationService implements NotificationService {
    @Override
    public void notify(Player player, String message) {
        player.sendMessage(message);
    }
}

@IocBukkitListener
public class AchievementListener implements Listener {

    private final NotificationService notifications; // Interface, not implementation

    public AchievementListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        notifications.notify(player, "You broke a block!");
    }
}
```

### Multi-Provider Injection

Inject multiple implementations of an interface:

```java
public interface EventLogger {
    void logEvent(Player player, String eventType);
}

@IocBean
@IocMultiProvider(EventLogger.class)
public class FileEventLogger implements EventLogger {
    @Override
    public void logEvent(Player player, String eventType) {
        // Log to file
    }
}

@IocBean
@IocMultiProvider(EventLogger.class)
public class DatabaseEventLogger implements EventLogger {
    @Override
    public void logEvent(Player player, String eventType) {
        // Log to database
    }
}

@IocBukkitListener
public class PlayerEventListener implements Listener {

    private final List<EventLogger> loggers;

    public PlayerEventListener(@IocMulti(EventLogger.class) List<EventLogger> loggers) {
        this.loggers = loggers;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Notify all loggers
        for (EventLogger logger : loggers) {
            logger.logEvent(event.getPlayer(), "JOIN");
        }
    }
}
```

### Plugin Instance Injection

Inject the plugin instance when needed:

```java
import be.garagepoort.mcioc.load.InjectTubingPlugin;
import be.garagepoort.mcioc.TubingPlugin;

@IocBukkitListener
public class TaskSchedulerListener implements Listener {

    private final TubingPlugin plugin;

    public TaskSchedulerListener(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule delayed task
        Bukkit.getScheduler().runTaskLater(
            (Plugin) plugin,
            () -> player.sendMessage("You've been online for 5 seconds!"),
            100L  // 5 seconds
        );
    }
}
```

## Event Filtering Patterns

Common patterns for filtering and handling events efficiently.

### Pattern: Early Exit

Exit early if conditions aren't met:

```java
@IocBukkitListener
public class BuildProtectionListener implements Listener {

    private final PermissionService permissions;
    private final RegionService regions;

    public BuildProtectionListener(PermissionService permissions,
                                  RegionService regions) {
        this.permissions = permissions;
        this.regions = regions;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Early exit - has permission
        if (permissions.hasPermission(player, "build.bypass")) {
            return;
        }

        // Early exit - not in protected region
        if (!regions.isProtected(block.getLocation())) {
            return;
        }

        // Cancel and notify
        event.setCancelled(true);
        player.sendMessage("You cannot build here!");
    }
}
```

### Pattern: Entity Type Filtering

Filter events by entity type:

```java
@IocBukkitListener
public class MobDamageListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only handle player damage
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();

        // Only handle damage from another player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();

        // Handle PvP damage
        handlePvpDamage(attacker, victim, event.getDamage());
    }

    private void handlePvpDamage(Player attacker, Player victim, double damage) {
        // PvP-specific logic
    }
}
```

### Pattern: World-Specific Handling

Handle events differently per world:

```java
@IocBukkitListener
public class WorldListener implements Listener {

    private final WorldConfigService worldConfig;

    public WorldListener(WorldConfigService worldConfig) {
        this.worldConfig = worldConfig;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        World world = event.getBlock().getWorld();
        Player player = event.getPlayer();

        // Get world-specific configuration
        WorldConfig config = worldConfig.getConfig(world);

        if (!config.isBlockBreakAllowed()) {
            event.setCancelled(true);
            player.sendMessage("Block breaking is disabled in this world!");
        }
    }
}
```

### Pattern: Cooldown Management

Implement cooldowns using services:

```java
@IocBukkitListener
public class TeleportListener implements Listener {

    private final CooldownService cooldowns;
    private final MessageService messages;

    @ConfigProperty("teleport.cooldown-seconds")
    private int cooldownSeconds;

    public TeleportListener(CooldownService cooldowns,
                           MessageService messages) {
        this.cooldowns = cooldowns;
        this.messages = messages;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Skip for certain teleport causes
        if (event.getCause() == TeleportCause.PLUGIN ||
            event.getCause() == TeleportCause.COMMAND) {
            return;
        }

        // Check cooldown
        if (cooldowns.isOnCooldown(player, "teleport")) {
            long remaining = cooldowns.getRemainingTime(player, "teleport");
            messages.send(player, "teleport.cooldown", remaining);
            event.setCancelled(true);
            return;
        }

        // Set cooldown
        cooldowns.setCooldown(player, "teleport", cooldownSeconds);
    }
}
```

### Pattern: Event Chaining

Chain multiple event handlers together:

```java
@IocBukkitListener
public class ChatEventListener implements Listener {

    private final List<ChatProcessor> processors;

    public ChatEventListener(@IocMulti(ChatProcessor.class) List<ChatProcessor> processors) {
        this.processors = processors;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Process through all processors in order
        for (ChatProcessor processor : processors) {
            message = processor.process(player, message);

            // Stop if processor returns null (indicates event should be cancelled)
            if (message == null) {
                event.setCancelled(true);
                return;
            }
        }

        // Set final processed message
        event.setMessage(message);
    }
}

// Processors
public interface ChatProcessor {
    String process(Player player, String message);
}

@IocBean
@IocMultiProvider(ChatProcessor.class)
public class ProfanityFilterProcessor implements ChatProcessor {
    @Override
    public String process(Player player, String message) {
        return message.replaceAll("badword", "***");
    }
}

@IocBean
@IocMultiProvider(ChatProcessor.class)
public class CapitalizationProcessor implements ChatProcessor {
    @Override
    public String process(Player player, String message) {
        if (message.equals(message.toUpperCase())) {
            return message.toLowerCase(); // No all caps
        }
        return message;
    }
}
```

## Multi-Provider Listeners

The `multiproviderClass` attribute allows listeners to be organized into groups:

```java
public interface SecurityListener {
    // Marker interface for security-related listeners
}

@IocBukkitListener(multiproviderClass = SecurityListener.class)
public class LoginSecurityListener implements Listener, SecurityListener {

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Security check
    }
}

@IocBukkitListener(multiproviderClass = SecurityListener.class)
public class CommandSecurityListener implements Listener, SecurityListener {

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Security check
    }
}

// Access all security listeners
@IocBean
public class SecurityManager {

    private final List<SecurityListener> securityListeners;

    public SecurityManager(@IocMulti(SecurityListener.class) List<SecurityListener> securityListeners) {
        this.securityListeners = securityListeners;
    }

    public void enableAllSecurityFeatures() {
        // All security listeners are accessible
    }
}
```

This is useful for:
- Organizing related listeners
- Plugin APIs where extensions provide listeners
- Dynamic enabling/disabling of listener groups

## Best Practices for Event Handling

### 1. Keep Event Handlers Focused

**Good - Single Responsibility:**
```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    private final PlayerDataService dataService;

    public PlayerJoinListener(PlayerDataService dataService) {
        this.dataService = dataService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        dataService.loadPlayerData(event.getPlayer());
    }
}
```

**Bad - Doing Too Much:**
```java
@IocBukkitListener
public class GodListener implements Listener {

    @EventHandler
    public void onEverything(Event event) {
        // Handling 50 different event types in one method
        if (event instanceof PlayerJoinEvent) { }
        else if (event instanceof PlayerQuitEvent) { }
        else if (event instanceof BlockBreakEvent) { }
        // ... 47 more
    }
}
```

### 2. Use Appropriate Priority

```java
// Good - Clear intention
@EventHandler(priority = EventPriority.LOWEST)
public void preprocessChat(AsyncPlayerChatEvent event) {
    // Early processing
}

@EventHandler(priority = EventPriority.MONITOR)
public void logChat(AsyncPlayerChatEvent event) {
    // Read-only logging - never modify!
    if (!event.isCancelled()) {
        logger.log(event.getMessage());
    }
}
```

### 3. Ignore Cancelled Events When Appropriate

```java
@EventHandler(ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    // Don't waste CPU if another plugin already cancelled this
}
```

### 4. Validate Event Data

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    // Always validate event data
    if (event.getClickedBlock() == null) {
        return;
    }

    Block block = event.getClickedBlock();

    if (block.getType() != Material.CHEST) {
        return;
    }

    // Process chest interaction
}
```

### 5. Use Services for Complex Logic

**Good - Logic in Service:**
```java
@IocBukkitListener
public class CombatListener implements Listener {

    private final CombatService combatService;

    public CombatListener(CombatService combatService) {
        this.combatService = combatService;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Delegate to service
        combatService.handlePvpDamage(attacker, victim, event);
    }
}

@IocBean
public class CombatService {
    public void handlePvpDamage(Player attacker, Player victim, EntityDamageByEntityEvent event) {
        // Complex combat logic here
        // Easier to test and maintain
    }
}
```

**Bad - Logic in Listener:**
```java
@IocBukkitListener
public class CombatListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 200 lines of combat logic directly in the event handler
        // Hard to test, hard to maintain
    }
}
```

### 6. Handle Async Events Carefully

```java
@IocBukkitListener
public class ChatListener implements Listener {

    private final DatabaseService database;

    public ChatListener(DatabaseService database) {
        this.database = database;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        // This event is ASYNC - be careful with Bukkit API calls!

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Safe - reading player data
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        // Safe - database operations (async-safe)
        database.logChatMessage(playerId, message);

        // UNSAFE - modifying Bukkit state from async thread!
        // player.setHealth(20.0); // DON'T DO THIS

        // Correct way - schedule sync task
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setHealth(20.0); // Now safe
        });
    }
}
```

### 7. Use Conditional Loading for Features

```java
// Enable/disable features via config
@IocBukkitListener(conditionalOnProperty = "features.auto-respawn.enabled")
public class AutoRespawnListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Only active when feature is enabled
    }
}
```

### 8. Organize Listeners by Feature

```
src/main/java/com/example/myplugin/
└── listeners/
    ├── player/
    │   ├── PlayerJoinListener.java
    │   ├── PlayerQuitListener.java
    │   └── PlayerChatListener.java
    ├── protection/
    │   ├── BlockBreakListener.java
    │   └── BlockPlaceListener.java
    └── combat/
        ├── PvpListener.java
        └── DamageListener.java
```

### 9. Document Complex Event Logic

```java
@IocBukkitListener
public class RegionProtectionListener implements Listener {

    /**
     * Prevents block breaking in protected regions unless:
     * - Player has bypass permission (build.bypass)
     * - Player is region owner
     * - Block is in unprotected area
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Implementation with clear logic flow
    }
}
```

### 10. Test Listeners Independently

```java
public class PlayerJoinListenerTest {

    @Test
    public void testPlayerJoinLoadsData() {
        // Mock dependencies
        PlayerDataService mockDataService = mock(PlayerDataService.class);

        // Create listener with mock
        PlayerJoinListener listener = new PlayerJoinListener(mockDataService);

        // Create mock event
        Player mockPlayer = mock(Player.class);
        PlayerJoinEvent mockEvent = new PlayerJoinEvent(mockPlayer, "Welcome");

        // Test
        listener.onPlayerJoin(mockEvent);

        // Verify
        verify(mockDataService).loadPlayerData(mockPlayer);
    }
}
```

## Complete Example

Here's a complete example demonstrating best practices:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.configuration.ConfigProperty;
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import com.example.myplugin.services.PlayerDataService;
import com.example.myplugin.services.MessageService;
import com.example.myplugin.services.RegionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@IocBukkitListener(conditionalOnProperty = "features.player-tracking.enabled")
public class PlayerTrackingListener implements Listener {

    private final PlayerDataService playerDataService;
    private final MessageService messageService;
    private final RegionService regionService;

    @ConfigProperty("features.player-tracking.welcome-message")
    private String welcomeMessage;

    @ConfigProperty("features.player-tracking.save-on-quit")
    private boolean saveOnQuit;

    public PlayerTrackingListener(PlayerDataService playerDataService,
                                 MessageService messageService,
                                 RegionService regionService) {
        this.playerDataService = playerDataService;
        this.messageService = messageService;
        this.regionService = regionService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data
        playerDataService.loadPlayerData(player);

        // Send welcome message if configured
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            messageService.send(player, welcomeMessage);
        }

        // Update region tracking
        regionService.onPlayerEnterWorld(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Save player data if configured
        if (saveOnQuit) {
            playerDataService.savePlayerData(player);
        }

        // Update region tracking
        regionService.onPlayerLeaveWorld(player);

        // Clean up cached data
        playerDataService.unloadPlayerData(player);
    }
}
```

**Configuration (config.yml):**
```yaml
features:
  player-tracking:
    enabled: true
    welcome-message: "&aWelcome back!"
    save-on-quit: true
```

## Lifecycle and Reload Behavior

### Listener Registration

Listeners are registered during plugin initialization:

1. Plugin enables
2. IoC container scans for `@IocBukkitListener` classes
3. Container instantiates each listener with dependencies
4. `TubingBukkitBeanLoader.loadListenerBeans()` registers with Bukkit
5. Events start flowing to your handlers

### Reload Behavior

When you call `plugin.reload()`:

1. `HandlerList.unregisterAll(plugin)` is called - all listeners are unregistered
2. IoC container is recreated
3. All listeners are re-instantiated with fresh dependencies
4. Listeners are re-registered with Bukkit
5. Events resume with new listener instances

**Important:** Any state stored in listener fields will be lost during reload. Use services for persistent state:

```java
// Bad - state lost on reload
@IocBukkitListener
public class CounterListener implements Listener {
    private int count = 0; // LOST ON RELOAD!

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        count++;
    }
}

// Good - state persists
@IocBukkitListener
public class CounterListener implements Listener {
    private final CounterService counterService;

    public CounterListener(CounterService counterService) {
        this.counterService = counterService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        counterService.increment(); // Service handles persistence
    }
}
```

## Next Steps

Now that you understand event listeners with Tubing:

- [Commands](Commands.md) - Command handling in detail
- [Bukkit Setup](Bukkit-Setup.md) - Plugin lifecycle and initialization
- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration patterns
- [Multi-Implementation](../core/Multi-Implementation.md) - Multiple listener implementations
- [Dependency Injection](../core/Dependency-Injection.md) - Deep dive into DI patterns

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your listeners
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - When listeners are created
