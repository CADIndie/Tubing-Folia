# BungeeCord Event Listeners

Tubing provides a streamlined approach to BungeeCord event handling that eliminates manual registration boilerplate and enables automatic dependency injection in listeners. This guide covers everything you need to know about creating and managing event listeners with Tubing on BungeeCord.

## Overview

The Tubing event listener system offers several advantages over traditional BungeeCord listener registration:

- **Automatic Registration**: Listeners are discovered and registered automatically
- **Dependency Injection**: Listeners receive dependencies via constructor injection
- **Conditional Loading**: Enable/disable listeners based on configuration properties
- **Multi-Provider Support**: Organize multiple listeners implementing common interfaces
- **Clean Code**: No manual `ProxyServer.getInstance().getPluginManager().registerListener()` calls
- **Testability**: Easy to test with constructor injection

## Basic Event Listener

### Simple Listener

The simplest way to create an event listener is to implement BungeeCord's `Listener` interface and use the `@IocBungeeListener` annotation:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeListener;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

@IocBungeeListener
public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        event.getPlayer().sendMessage("Welcome to the proxy!");
    }
}
```

**What happens:**
1. Tubing scans your plugin's package and finds classes annotated with `@IocBungeeListener`
2. The listener is instantiated as a bean (all dependencies are injected)
3. Tubing automatically calls `ProxyServer.getInstance().getPluginManager().registerListener(plugin, bean)`
4. Your listener is now active and will receive events

**Important:** Classes must implement BungeeCord's `Listener` interface. If you annotate a non-Listener class, Tubing will throw an `IocException`:

```
IocException: IocListener annotation can only be used on BungeeCord Listeners. Failing class [com.example.NotAListener]
```

### Listener with Dependencies

The real power of Tubing listeners comes from dependency injection:

```java
@IocBungeeListener
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
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

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
@IocBungeeListener
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
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        playerService.loadPlayerData(player);

        if (welcomeEnabled) {
            player.sendMessage(new TextComponent(welcomeText));
        }
    }
}
```

**Configuration file (config.yml):**
```yaml
features:
  welcome-message:
    enabled: true
    text: "&aWelcome to the proxy!"
```

## Event Priority

BungeeCord event priority controls the order in which event handlers are called. BungeeCord provides priority levels from lowest to highest.

### Using EventPriority

Specify priority using BungeeCord's standard `@EventHandler` annotation:

```java
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

@IocBungeeListener
public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatLowest(ChatEvent event) {
        // Called first - good for preprocessing
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChatLow(ChatEvent event) {
        // Called after LOWEST
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChatNormal(ChatEvent event) {
        // Default priority - most listeners use this
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChatHigh(ChatEvent event) {
        // Called after NORMAL
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatHighest(ChatEvent event) {
        // Called last
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

### Priority Example: Chat Filter

```java
@IocBungeeListener
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
    public void filterChat(ChatEvent event) {
        String message = event.getMessage();
        String filtered = profanityFilter.filter(message);
        event.setMessage(filtered);
    }

    // Log chat after all modifications
    @EventHandler(priority = EventPriority.HIGHEST)
    public void logChat(ChatEvent event) {
        if (!event.isCancelled()) {
            chatLogger.log(event.getSender(), event.getMessage());
        }
    }
}
```

## Conditional Listeners

Enable or disable listeners based on configuration properties using `conditionalOnProperty`:

### Basic Conditional Loading

```java
@IocBungeeListener(conditionalOnProperty = "features.anti-vpn.enabled")
public class AntiVpnListener implements Listener {

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        // Only registered if features.anti-vpn.enabled = true
    }
}
```

**Configuration:**
```yaml
features:
  anti-vpn:
    enabled: true
```

If `features.anti-vpn.enabled` is `false` or not defined, the listener **will not be created or registered**.

### Multiple Conditions (AND Logic)

Use `&&` to require multiple conditions:

```java
@IocBungeeListener(conditionalOnProperty = "features.security.enabled && features.security.ip-check=true")
public class SecurityListener implements Listener {

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        // Only loaded if BOTH conditions are true
    }
}
```

**Configuration:**
```yaml
features:
  security:
    enabled: true
    ip-check: true  # Both must be true
```

### Conditional Examples

**Feature Toggle:**
```java
@IocBungeeListener(conditionalOnProperty = "features.player-tracking.enabled")
public class PlayerTrackingListener implements Listener {
    // Only active when player tracking is enabled
}
```

**Environment-Specific:**
```java
@IocBungeeListener(conditionalOnProperty = "environment=production")
public class ProductionMonitoringListener implements Listener {
    // Only loaded in production environment
}
```

**Complex Conditions:**
```java
@IocBungeeListener(conditionalOnProperty = "features.lobby.enabled && features.lobby.auto-send=true")
public class LobbyListener implements Listener {
    // Loaded when lobby is enabled AND auto-send is enabled
}
```

## Dependency Injection in Listeners

Listeners support all Tubing dependency injection patterns.

### Service Dependencies

```java
@IocBungeeListener
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
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Try cache first
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {
            // Load from database
            data = repository.load(player.getUniqueId());
            cache.put(player.getUniqueId(), data);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

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
    void notify(ProxiedPlayer player, String message);
}

@IocBean
public class BungeeNotificationService implements NotificationService {
    @Override
    public void notify(ProxiedPlayer player, String message) {
        player.sendMessage(new TextComponent(message));
    }
}

@IocBungeeListener
public class ServerSwitchListener implements Listener {

    private final NotificationService notifications; // Interface, not implementation

    public ServerSwitchListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ServerInfo from = event.getFrom();
        ServerInfo to = player.getServer().getInfo();

        notifications.notify(player,
            "You switched from " + from.getName() + " to " + to.getName());
    }
}
```

### Multi-Provider Injection

Inject multiple implementations of an interface:

```java
public interface EventLogger {
    void logEvent(ProxiedPlayer player, String eventType);
}

@IocBean
@IocMultiProvider(EventLogger.class)
public class FileEventLogger implements EventLogger {
    @Override
    public void logEvent(ProxiedPlayer player, String eventType) {
        // Log to file
    }
}

@IocBean
@IocMultiProvider(EventLogger.class)
public class DatabaseEventLogger implements EventLogger {
    @Override
    public void logEvent(ProxiedPlayer player, String eventType) {
        // Log to database
    }
}

@IocBungeeListener
public class PlayerEventListener implements Listener {

    private final List<EventLogger> loggers;

    public PlayerEventListener(@IocMulti(EventLogger.class) List<EventLogger> loggers) {
        this.loggers = loggers;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
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

@IocBungeeListener
public class TaskSchedulerListener implements Listener {

    private final TubingPlugin plugin;

    public TaskSchedulerListener(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Schedule delayed task
        ProxyServer.getInstance().getScheduler().schedule(
            (Plugin) plugin,
            () -> player.sendMessage(new TextComponent("You've been online for 5 seconds!")),
            5, TimeUnit.SECONDS
        );
    }
}
```

## Event Filtering Patterns

Common patterns for filtering and handling events efficiently.

### Pattern: Early Exit

Exit early if conditions aren't met:

```java
@IocBungeeListener
public class ServerAccessListener implements Listener {

    private final PermissionService permissions;
    private final ServerConfigService serverConfig;

    public ServerAccessListener(PermissionService permissions,
                               ServerConfigService serverConfig) {
        this.permissions = permissions;
        this.serverConfig = serverConfig;
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();

        // Early exit - has bypass permission
        if (permissions.hasPermission(player, "server.bypass")) {
            return;
        }

        // Early exit - server is public
        if (!serverConfig.isRestricted(target.getName())) {
            return;
        }

        // Cancel and notify
        event.setCancelled(true);
        player.sendMessage(new TextComponent("You don't have access to this server!"));
    }
}
```

### Pattern: Player Type Filtering

Filter events by connection type:

```java
@IocBungeeListener
public class LoginListener implements Listener {

    private final WhitelistService whitelist;

    public LoginListener(WhitelistService whitelist) {
        this.whitelist = whitelist;
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        PendingConnection connection = event.getConnection();

        // Check if player is whitelisted
        if (!whitelist.isWhitelisted(connection.getUniqueId())) {
            event.setCancelled(true);
            event.setCancelReason(new TextComponent("You are not whitelisted!"));
        }
    }
}
```

### Pattern: Server-Specific Handling

Handle events differently per server:

```java
@IocBungeeListener
public class ServerChatListener implements Listener {

    private final ServerConfigService serverConfig;

    public ServerChatListener(ServerConfigService serverConfig) {
        this.serverConfig = serverConfig;
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        // Only process player messages
        if (!(event.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        ServerInfo server = player.getServer().getInfo();

        // Get server-specific configuration
        ServerConfig config = serverConfig.getConfig(server.getName());

        if (!config.isChatAllowed()) {
            event.setCancelled(true);
            player.sendMessage(new TextComponent("Chat is disabled on this server!"));
        }
    }
}
```

### Pattern: Cooldown Management

Implement cooldowns using services:

```java
@IocBungeeListener
public class ServerSwitchListener implements Listener {

    private final CooldownService cooldowns;
    private final MessageService messages;

    @ConfigProperty("server-switch.cooldown-seconds")
    private int cooldownSeconds;

    public ServerSwitchListener(CooldownService cooldowns,
                               MessageService messages) {
        this.cooldowns = cooldowns;
        this.messages = messages;
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Skip if initial connection
        if (player.getServer() == null) {
            return;
        }

        // Check cooldown
        if (cooldowns.isOnCooldown(player, "server-switch")) {
            long remaining = cooldowns.getRemainingTime(player, "server-switch");
            messages.send(player, "server-switch.cooldown", remaining);
            event.setCancelled(true);
            return;
        }

        // Set cooldown
        cooldowns.setCooldown(player, "server-switch", cooldownSeconds);
    }
}
```

### Pattern: Event Chaining

Chain multiple event handlers together:

```java
@IocBungeeListener
public class ChatProcessorListener implements Listener {

    private final List<ChatProcessor> processors;

    public ChatProcessorListener(@IocMulti(ChatProcessor.class) List<ChatProcessor> processors) {
        this.processors = processors;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(ChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // Only process player chat
        if (!(event.getSender() instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
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
    String process(ProxiedPlayer player, String message);
}

@IocBean
@IocMultiProvider(ChatProcessor.class)
public class ProfanityFilterProcessor implements ChatProcessor {
    @Override
    public String process(ProxiedPlayer player, String message) {
        return message.replaceAll("badword", "***");
    }
}

@IocBean
@IocMultiProvider(ChatProcessor.class)
public class CapitalizationProcessor implements ChatProcessor {
    @Override
    public String process(ProxiedPlayer player, String message) {
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

@IocBungeeListener(multiproviderClass = SecurityListener.class)
public class LoginSecurityListener implements Listener, SecurityListener {

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        // Security check
    }
}

@IocBungeeListener(multiproviderClass = SecurityListener.class)
public class ConnectionSecurityListener implements Listener, SecurityListener {

    @EventHandler
    public void onPlayerHandshake(PlayerHandshakeEvent event) {
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
@IocBungeeListener
public class PlayerJoinListener implements Listener {

    private final PlayerDataService dataService;

    public PlayerJoinListener(PlayerDataService dataService) {
        this.dataService = dataService;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        dataService.loadPlayerData(event.getPlayer());
    }
}
```

**Bad - Doing Too Much:**
```java
@IocBungeeListener
public class GodListener implements Listener {

    @EventHandler
    public void onEverything(Event event) {
        // Handling 50 different event types in one method
        if (event instanceof PostLoginEvent) { }
        else if (event instanceof PlayerDisconnectEvent) { }
        else if (event instanceof ChatEvent) { }
        // ... 47 more
    }
}
```

### 2. Use Appropriate Priority

```java
// Good - Clear intention
@EventHandler(priority = EventPriority.LOWEST)
public void preprocessChat(ChatEvent event) {
    // Early processing
}

@EventHandler(priority = EventPriority.HIGHEST)
public void logChat(ChatEvent event) {
    // Final logging - read-only
    if (!event.isCancelled()) {
        logger.log(event.getMessage());
    }
}
```

### 3. Validate Event Data

```java
@EventHandler
public void onServerConnect(ServerConnectEvent event) {
    // Always validate event data
    ProxiedPlayer player = event.getPlayer();
    if (player == null) {
        return;
    }

    ServerInfo target = event.getTarget();
    if (target == null) {
        return;
    }

    // Process server connection
}
```

### 4. Use Services for Complex Logic

**Good - Logic in Service:**
```java
@IocBungeeListener
public class ServerBalanceListener implements Listener {

    private final LoadBalancingService balancingService;

    public ServerBalanceListener(LoadBalancingService balancingService) {
        this.balancingService = balancingService;
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();

        // Delegate to service
        ServerInfo balanced = balancingService.findBestServer(target, player);
        if (balanced != null && !balanced.equals(target)) {
            event.setTarget(balanced);
        }
    }
}

@IocBean
public class LoadBalancingService {
    public ServerInfo findBestServer(ServerInfo requested, ProxiedPlayer player) {
        // Complex load balancing logic here
        // Easier to test and maintain
        return requested;
    }
}
```

**Bad - Logic in Listener:**
```java
@IocBungeeListener
public class ServerBalanceListener implements Listener {

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        // 200 lines of load balancing logic directly in the event handler
        // Hard to test, hard to maintain
    }
}
```

### 5. Handle Async Events Carefully

```java
@IocBungeeListener
public class AsyncListener implements Listener {

    private final DatabaseService database;

    public AsyncListener(DatabaseService database) {
        this.database = database;
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        // PreLoginEvent supports async operations
        event.registerIntent(plugin);

        ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
            try {
                // Safe - async database operations
                PlayerData data = database.loadPlayerData(event.getConnection().getUniqueId());

                if (data.isBanned()) {
                    event.setCancelled(true);
                    event.setCancelReason(new TextComponent("You are banned!"));
                }
            } finally {
                event.completeIntent(plugin);
            }
        });
    }
}
```

### 6. Use Conditional Loading for Features

```java
// Enable/disable features via config
@IocBungeeListener(conditionalOnProperty = "features.auto-reconnect.enabled")
public class AutoReconnectListener implements Listener {

    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        // Only active when feature is enabled
    }
}
```

### 7. Organize Listeners by Feature

```
src/main/java/com/example/myplugin/
└── listeners/
    ├── player/
    │   ├── PlayerJoinListener.java
    │   ├── PlayerQuitListener.java
    │   └── PlayerChatListener.java
    ├── server/
    │   ├── ServerConnectListener.java
    │   └── ServerSwitchListener.java
    └── security/
        ├── AntiVpnListener.java
        └── WhitelistListener.java
```

### 8. Document Complex Event Logic

```java
@IocBungeeListener
public class ServerAccessListener implements Listener {

    /**
     * Prevents server connections unless:
     * - Player has bypass permission (server.bypass)
     * - Player has specific server permission (server.<name>)
     * - Server is marked as public
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onServerConnect(ServerConnectEvent event) {
        // Implementation with clear logic flow
    }
}
```

### 9. Test Listeners Independently

```java
public class PlayerJoinListenerTest {

    @Test
    public void testPlayerJoinLoadsData() {
        // Mock dependencies
        PlayerDataService mockDataService = mock(PlayerDataService.class);

        // Create listener with mock
        PlayerJoinListener listener = new PlayerJoinListener(mockDataService);

        // Create mock event
        ProxiedPlayer mockPlayer = mock(ProxiedPlayer.class);
        PostLoginEvent mockEvent = mock(PostLoginEvent.class);
        when(mockEvent.getPlayer()).thenReturn(mockPlayer);

        // Test
        listener.onPlayerJoin(mockEvent);

        // Verify
        verify(mockDataService).loadPlayerData(mockPlayer);
    }
}
```

### 10. Handle Cancelled Events Appropriately

```java
@IocBungeeListener
public class LoggingListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void logServerSwitch(ServerSwitchEvent event) {
        // ServerSwitchEvent cannot be cancelled - always fires
        logger.info(event.getPlayer().getName() + " switched servers");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void logChat(ChatEvent event) {
        // Only log if not cancelled by another plugin
        if (!event.isCancelled()) {
            logger.info("Chat: " + event.getMessage());
        }
    }
}
```

## Complete Example

Here's a complete example demonstrating best practices:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.configuration.ConfigProperty;
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeListener;
import com.example.myplugin.services.PlayerDataService;
import com.example.myplugin.services.MessageService;
import com.example.myplugin.services.ServerService;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

@IocBungeeListener(conditionalOnProperty = "features.player-tracking.enabled")
public class PlayerTrackingListener implements Listener {

    private final PlayerDataService playerDataService;
    private final MessageService messageService;
    private final ServerService serverService;

    @ConfigProperty("features.player-tracking.welcome-message")
    private String welcomeMessage;

    @ConfigProperty("features.player-tracking.save-on-quit")
    private boolean saveOnQuit;

    public PlayerTrackingListener(PlayerDataService playerDataService,
                                 MessageService messageService,
                                 ServerService serverService) {
        this.playerDataService = playerDataService;
        this.messageService = messageService;
        this.serverService = serverService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Load player data
        playerDataService.loadPlayerData(player);

        // Send welcome message if configured
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            messageService.send(player, welcomeMessage);
        }

        // Update server tracking
        serverService.onPlayerJoin(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Save player data if configured
        if (saveOnQuit) {
            playerDataService.savePlayerData(player);
        }

        // Update server tracking
        serverService.onPlayerQuit(player);

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
2. IoC container scans for `@IocBungeeListener` classes
3. Container instantiates each listener with dependencies
4. `TubingBungeeBeanLoader.load()` registers listeners with BungeeCord
5. Events start flowing to your handlers

### Reload Behavior

When you call `plugin.reload()`:

1. `ProxyServer.getInstance().getPluginManager().unregisterListeners(plugin)` is called - all listeners are unregistered
2. IoC container is recreated
3. All listeners are re-instantiated with fresh dependencies
4. Listeners are re-registered with BungeeCord
5. Events resume with new listener instances

**Important:** Any state stored in listener fields will be lost during reload. Use services for persistent state:

```java
// Bad - state lost on reload
@IocBungeeListener
public class CounterListener implements Listener {
    private int count = 0; // LOST ON RELOAD!

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        count++;
    }
}

// Good - state persists
@IocBungeeListener
public class CounterListener implements Listener {
    private final CounterService counterService;

    public CounterListener(CounterService counterService) {
        this.counterService = counterService;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        counterService.increment(); // Service handles persistence
    }
}
```

## BungeeCord-Specific Considerations

### Working with ProxiedPlayer

```java
@IocBungeeListener
public class PlayerInfoListener implements Listener {

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // Get player information
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        InetSocketAddress address = player.getAddress();

        // Get current server (may be null on login)
        Server currentServer = player.getServer();
        if (currentServer != null) {
            ServerInfo serverInfo = currentServer.getInfo();
            // Work with server info
        }
    }
}
```

### Server Connection Events

```java
@IocBungeeListener
public class ServerConnectionListener implements Listener {

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        // Before connecting to a server
        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();

        // Can modify target server
        event.setTarget(someOtherServer);

        // Can cancel connection
        event.setCancelled(true);
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        // After successfully connecting
        ServerInfo server = event.getServer().getInfo();
        // Cannot cancel - already connected
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        // After switching from one server to another
        ServerInfo from = event.getFrom(); // Previous server (may be null)
        ServerInfo to = event.getPlayer().getServer().getInfo();
        // Cannot cancel - already switched
    }
}
```

### Plugin Messaging

```java
@IocBungeeListener
public class PluginMessageListener implements Listener {

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("my:channel")) {
            return;
        }

        byte[] data = Arrays.copyOf(event.getData(), event.getData().length);
        ByteArrayDataInput in = ByteStreams.newDataInput(data);

        String subChannel = in.readUTF();
        // Process plugin message
    }
}
```

## Next Steps

Now that you understand BungeeCord event listeners with Tubing:

- [Commands](Commands.md) - Command handling for BungeeCord
- [BungeeCord Setup](BungeeCord-Setup.md) - Plugin lifecycle and initialization
- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration patterns
- [Multi-Implementation](../core/Multi-Implementation.md) - Multiple listener implementations
- [Dependency Injection](../core/Dependency-Injection.md) - Deep dive into DI patterns

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your listeners
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - When listeners are created
- [Migration Guide](../getting-started/Migration-Guide.md) - Migrating existing BungeeCord plugins
