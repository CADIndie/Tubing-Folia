# Velocity Listeners

Tubing provides a streamlined approach to Velocity event handling that eliminates manual registration boilerplate and enables automatic dependency injection in listeners. This guide covers everything you need to know about creating and managing event listeners with Tubing on Velocity.

## Overview

The Tubing event listener system for Velocity offers several advantages over traditional listener registration:

- **Automatic Registration**: Listeners are discovered and registered automatically
- **Dependency Injection**: Listeners receive dependencies via constructor injection
- **Conditional Loading**: Enable/disable listeners based on configuration properties
- **Multi-Provider Support**: Organize multiple listeners implementing common interfaces
- **Clean Code**: No manual `EventManager.register()` calls
- **Testability**: Easy to test with constructor injection

## Basic Event Listener

### Simple Listener

The simplest way to create an event listener is to use the `@IocVelocityListener` annotation. Unlike Bukkit, Velocity does not require implementing a marker interface:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;

@IocVelocityListener
public class PlayerJoinListener {

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        event.getPlayer().sendMessage(
            Component.text("Welcome to the server!")
        );
    }
}
```

**What happens:**
1. Tubing scans your plugin's package and finds classes annotated with `@IocVelocityListener`
2. The listener is instantiated as a bean (all dependencies are injected)
3. Tubing automatically calls `EventManager.register(plugin, bean)`
4. Your listener is now active and will receive events

**Note:** Unlike Bukkit, Velocity listeners do not need to implement a marker interface. Any class annotated with `@IocVelocityListener` that has methods annotated with `@Subscribe` will work.

### Listener with Dependencies

The real power of Tubing listeners comes from dependency injection:

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;

@IocVelocityListener
public class PlayerJoinListener {

    private final PlayerService playerService;
    private final MessageService messageService;

    // Dependencies injected via constructor
    public PlayerJoinListener(PlayerService playerService,
                             MessageService messageService) {
        this.playerService = playerService;
        this.messageService = messageService;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
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
import be.garagepoort.mcioc.configuration.ConfigProperty;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

@IocVelocityListener
public class PlayerJoinListener {

    private final PlayerService playerService;

    @ConfigProperty("features.welcome-message.enabled")
    private boolean welcomeEnabled;

    @ConfigProperty("features.welcome-message.text")
    private String welcomeText;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        playerService.loadPlayerData(player);

        if (welcomeEnabled) {
            player.sendMessage(Component.text(welcomeText));
        }
    }
}
```

**Configuration file (config.yml):**
```yaml
features:
  welcome-message:
    enabled: true
    text: "Welcome to the server!"
```

## Velocity's Event System

Velocity uses a different event system than Bukkit. Understanding these differences is important when working with Tubing on Velocity.

### The @Subscribe Annotation

Velocity uses `@Subscribe` instead of Bukkit's `@EventHandler`:

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;

@IocVelocityListener
public class PlayerQuitListener {

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        // Handle player disconnect
    }
}
```

**Key differences from Bukkit:**
- No marker interface required (no equivalent to `Listener`)
- Use `@Subscribe` instead of `@EventHandler`
- Different event classes (e.g., `PostLoginEvent` instead of `PlayerJoinEvent`)
- Uses Adventure components for messages instead of strings

### Event Method Requirements

For a method to receive events:

1. Must be annotated with `@Subscribe`
2. Must have exactly one parameter (the event type)
3. Must be in a class annotated with `@IocVelocityListener`
4. Can have any access modifier (public, private, protected, package-private)

```java
@IocVelocityListener
public class ExampleListener {

    // Valid - public method
    @Subscribe
    public void onLogin(PostLoginEvent event) { }

    // Valid - private method (works in Velocity!)
    @Subscribe
    private void onDisconnect(DisconnectEvent event) { }

    // Valid - package-private method
    @Subscribe
    void onServerSwitch(ServerConnectedEvent event) { }

    // Invalid - no @Subscribe annotation
    public void onSomething(SomeEvent event) { }

    // Invalid - wrong number of parameters
    @Subscribe
    public void onEvent(PostLoginEvent event, String extra) { }

    // Invalid - no parameters
    @Subscribe
    public void onEvent() { }
}
```

## Event Priority with PostOrder

Velocity uses `PostOrder` to control the order in which event handlers are called. Unlike Bukkit's six priority levels, Velocity uses a numeric ordering system.

### Using PostOrder

Specify priority using the `order` parameter in the `@Subscribe` annotation:

```java
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;

@IocVelocityListener
public class ChatListener {

    @Subscribe(order = PostOrder.FIRST)
    public void onChatFirst(PlayerChatEvent event) {
        // Called first - good for early preprocessing
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onChatEarly(PlayerChatEvent event) {
        // Called after FIRST
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onChatNormal(PlayerChatEvent event) {
        // Default priority - most listeners use this
    }

    @Subscribe(order = PostOrder.LATE)
    public void onChatLate(PlayerChatEvent event) {
        // Called after NORMAL
    }

    @Subscribe(order = PostOrder.LAST)
    public void onChatLast(PlayerChatEvent event) {
        // Called last
    }
}
```

### PostOrder Constants

Velocity provides five standard priority constants:

| PostOrder | Numeric Value | Usage |
|-----------|---------------|-------|
| `FIRST` | -10000 | First to run, ideal for preprocessing and early validation |
| `EARLY` | -5000 | Early processing, before most plugins |
| `NORMAL` | 0 | Default priority (used when not specified) |
| `LATE` | 5000 | Late processing, after most plugins |
| `LAST` | 10000 | Last to run, for final modifications and monitoring |

### Custom Numeric Ordering

You can also use custom numeric values for fine-grained control:

```java
@IocVelocityListener
public class PriorityListener {

    // Run before FIRST
    @Subscribe(order = -20000)
    public void onEventVeryEarly(PostLoginEvent event) { }

    // Run between EARLY and NORMAL
    @Subscribe(order = -2500)
    public void onEventCustom(PostLoginEvent event) { }

    // Run after LAST
    @Subscribe(order = 20000)
    public void onEventVeryLate(PostLoginEvent event) { }
}
```

**Lower numbers = higher priority (run first)**

### Priority Guidelines

**FIRST (-10000)** - First to run, ideal for:
- Event preprocessing
- Early validation
- Setting up event context
- Critical modifications that other plugins need

**EARLY (-5000)** - Early processing:
- Priority-based handling
- Initial transformations
- Pre-plugin business logic

**NORMAL (0)** - Standard handling (default):
- Most event handlers should use this
- General business logic
- Default if not specified

**LATE (5000)** - Late processing:
- Overriding default behavior
- Post-processing
- Final validation

**LAST (10000)** - Last chance to modify:
- Final event modifications
- Monitoring and logging
- Statistics collection

### Priority Example: Chat Processing

```java
import com.velocitypowered.api.event.player.PlayerChatEvent;

@IocVelocityListener
public class ChatProcessingListener {

    private final ProfanityFilter profanityFilter;
    private final ChatLogger chatLogger;

    public ChatProcessingListener(ProfanityFilter profanityFilter,
                                 ChatLogger chatLogger) {
        this.profanityFilter = profanityFilter;
        this.chatLogger = chatLogger;
    }

    // Filter chat early - before other plugins process it
    @Subscribe(order = PostOrder.FIRST)
    public void filterChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }

        String message = event.getMessage();
        String filtered = profanityFilter.filter(message);

        if (!filtered.equals(message)) {
            event.setResult(PlayerChatEvent.ChatResult.message(filtered));
        }
    }

    // Log chat after all modifications
    @Subscribe(order = PostOrder.LAST)
    public void logChat(PlayerChatEvent event) {
        if (event.getResult().isAllowed()) {
            chatLogger.log(event.getPlayer(), event.getMessage());
        }
    }
}
```

## Conditional Listeners

Enable or disable listeners based on configuration properties using `conditionalOnProperty`:

### Basic Conditional Loading

```java
@IocVelocityListener(conditionalOnProperty = "features.maintenance.enabled")
public class MaintenanceListener {

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        // Only registered if features.maintenance.enabled = true
        if (!event.getPlayer().hasPermission("myplugin.maintenance.bypass")) {
            event.getPlayer().disconnect(
                Component.text("Server is under maintenance!")
            );
        }
    }
}
```

**Configuration:**
```yaml
features:
  maintenance:
    enabled: false
```

If `features.maintenance.enabled` is `false` or not defined, the listener **will not be created or registered**.

### Multiple Conditions (AND Logic)

Use `&&` to require multiple conditions:

```java
@IocVelocityListener(conditionalOnProperty = "features.proxy.enabled && features.server-switch.enabled")
public class ServerSwitchListener {

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        // Only loaded if BOTH conditions are true
    }
}
```

**Configuration:**
```yaml
features:
  proxy:
    enabled: true
  server-switch:
    enabled: true  # Both must be true
```

### Conditional Examples

**Feature Toggle:**
```java
@IocVelocityListener(conditionalOnProperty = "features.anti-bot.enabled")
public class AntiBotListener {
    // Only active when anti-bot is enabled
}
```

**Environment-Specific:**
```java
@IocVelocityListener(conditionalOnProperty = "environment=production")
public class ProductionMonitoringListener {
    // Only loaded in production environment
}
```

**Complex Conditions:**
```java
@IocVelocityListener(conditionalOnProperty = "features.auth.enabled && features.auth.force-login=true")
public class ForceLoginListener {
    // Loaded when auth is enabled AND force-login is true
}
```

## Dependency Injection in Listeners

Listeners support all Tubing dependency injection patterns.

### Service Dependencies

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;

@IocVelocityListener
public class PlayerDataListener {

    private final PlayerRepository repository;
    private final CacheService cache;
    private final ProxyServer proxyServer;

    public PlayerDataListener(PlayerRepository repository,
                             CacheService cache,
                             ProxyServer proxyServer) {
        this.repository = repository;
        this.cache = cache;
        this.proxyServer = proxyServer;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Try cache first
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {
            // Load from database
            data = repository.load(player.getUniqueId());
            cache.put(player.getUniqueId(), data);
        }
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
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
    void notify(Player player, Component message);
}

@IocBean
public class VelocityNotificationService implements NotificationService {
    @Override
    public void notify(Player player, Component message) {
        player.sendMessage(message);
    }
}

@IocVelocityListener
public class AchievementListener {

    private final NotificationService notifications; // Interface, not implementation

    public AchievementListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        notifications.notify(
            player,
            Component.text("Welcome to " + event.getServer().getServerInfo().getName())
        );
    }
}
```

### Multi-Provider Injection

Inject multiple implementations of an interface:

```java
public interface ProxyEventLogger {
    void logEvent(Player player, String eventType);
}

@IocBean
@IocMultiProvider(ProxyEventLogger.class)
public class FileProxyEventLogger implements ProxyEventLogger {
    @Override
    public void logEvent(Player player, String eventType) {
        // Log to file
    }
}

@IocBean
@IocMultiProvider(ProxyEventLogger.class)
public class DatabaseProxyEventLogger implements ProxyEventLogger {
    @Override
    public void logEvent(Player player, String eventType) {
        // Log to database
    }
}

@IocVelocityListener
public class PlayerEventListener {

    private final List<ProxyEventLogger> loggers;

    public PlayerEventListener(@IocMulti(ProxyEventLogger.class) List<ProxyEventLogger> loggers) {
        this.loggers = loggers;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        // Notify all loggers
        for (ProxyEventLogger logger : loggers) {
            logger.logEvent(event.getPlayer(), "LOGIN");
        }
    }
}
```

### ProxyServer Injection

Velocity's `ProxyServer` instance can be injected as a dependency:

```java
import com.velocitypowered.api.proxy.ProxyServer;

@IocVelocityListener
public class ServerManagementListener {

    private final ProxyServer proxyServer;

    public ServerManagementListener(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();

        // Access all registered servers
        Collection<RegisteredServer> servers = proxyServer.getAllServers();

        // Send available servers message
        player.sendMessage(
            Component.text("Available servers: " + servers.size())
        );
    }
}
```

**Note:** To make `ProxyServer` injectable, you need to provide it via a bean provider in your main plugin:

```java
import be.garagepoort.mcioc.TubingConfiguration;
import be.garagepoort.mcioc.IocBeanProvider;

@TubingConfiguration
public class VelocityConfiguration {

    @IocBeanProvider
    public static ProxyServer provideProxyServer(@InjectTubingPlugin TubingVelocityPlugin plugin) {
        return plugin.getProxyServer();
    }
}
```

### Plugin Instance Injection

Inject the plugin instance when needed:

```java
import be.garagepoort.mcioc.load.InjectTubingPlugin;
import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;

@IocVelocityListener
public class TaskSchedulerListener {

    private final TubingVelocityPlugin plugin;
    private final ProxyServer proxyServer;

    public TaskSchedulerListener(@InjectTubingPlugin TubingVelocityPlugin plugin,
                                 ProxyServer proxyServer) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Schedule delayed task using Velocity's scheduler
        proxyServer.getScheduler()
            .buildTask(plugin, () -> {
                player.sendMessage(
                    Component.text("You've been online for 5 seconds!")
                );
            })
            .delay(5, TimeUnit.SECONDS)
            .schedule();
    }
}
```

## Event Filtering Patterns

Common patterns for filtering and handling events efficiently.

### Pattern: Early Exit

Exit early if conditions aren't met:

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;

@IocVelocityListener
public class ServerAccessListener {

    private final PermissionService permissions;
    private final ServerWhitelistService whitelist;

    public ServerAccessListener(PermissionService permissions,
                               ServerWhitelistService whitelist) {
        this.permissions = permissions;
        this.whitelist = whitelist;
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer targetServer = event.getResult().getServer().orElse(null);

        if (targetServer == null) {
            return;
        }

        String serverName = targetServer.getServerInfo().getName();

        // Early exit - has bypass permission
        if (permissions.hasPermission(player, "server.bypass." + serverName)) {
            return;
        }

        // Early exit - server not whitelisted
        if (!whitelist.isWhitelisted(serverName)) {
            return;
        }

        // Check if player can access
        if (!whitelist.canPlayerAccess(player, serverName)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.sendMessage(
                Component.text("You don't have access to this server!")
            );
        }
    }
}
```

### Pattern: Result Checking

Many Velocity events use result objects that need to be checked:

```java
import com.velocitypowered.api.event.player.PlayerChatEvent;

@IocVelocityListener
public class ChatFilterListener {

    private final ProfanityFilter profanityFilter;

    public ChatFilterListener(ProfanityFilter profanityFilter) {
        this.profanityFilter = profanityFilter;
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        // Check if chat is already denied
        if (!event.getResult().isAllowed()) {
            return;
        }

        String message = event.getMessage();
        String filtered = profanityFilter.filter(message);

        // If message was filtered, update it
        if (!filtered.equals(message)) {
            event.setResult(PlayerChatEvent.ChatResult.message(filtered));
        }
    }
}
```

### Pattern: Server-Specific Handling

Handle events differently per backend server:

```java
import com.velocitypowered.api.event.player.ServerConnectedEvent;

@IocVelocityListener
public class ServerSpecificListener {

    private final ServerConfigService serverConfig;

    public ServerSpecificListener(ServerConfigService serverConfig) {
        this.serverConfig = serverConfig;
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();

        String serverName = server.getServerInfo().getName();

        // Get server-specific configuration
        ServerConfig config = serverConfig.getConfig(serverName);

        // Send server-specific welcome message
        if (config.hasWelcomeMessage()) {
            player.sendMessage(
                Component.text(config.getWelcomeMessage())
            );
        }
    }
}
```

### Pattern: Player State Tracking

Track player state across server switches:

```java
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;

@IocVelocityListener
public class PlayerTrackingListener {

    private final PlayerStateService stateService;

    public PlayerTrackingListener(PlayerStateService stateService) {
        this.stateService = stateService;
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        stateService.createSession(player);
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();

        stateService.updatePlayerServer(player, server.getServerInfo().getName());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        stateService.endSession(player);
    }
}
```

### Pattern: Event Chaining

Chain multiple processors together:

```java
public interface MessageProcessor {
    String process(Player player, String message);
}

@IocVelocityListener
public class ChatProcessingListener {

    private final List<MessageProcessor> processors;

    public ChatProcessingListener(@IocMulti(MessageProcessor.class) List<MessageProcessor> processors) {
        this.processors = processors;
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Process through all processors in order
        for (MessageProcessor processor : processors) {
            message = processor.process(player, message);

            // Stop if processor returns null (indicates message should be denied)
            if (message == null) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                return;
            }
        }

        // Set final processed message
        event.setResult(PlayerChatEvent.ChatResult.message(message));
    }
}

// Processors
@IocBean
@IocMultiProvider(MessageProcessor.class)
public class ProfanityFilterProcessor implements MessageProcessor {
    @Override
    public String process(Player player, String message) {
        return message.replaceAll("badword", "***");
    }
}

@IocBean
@IocMultiProvider(MessageProcessor.class)
public class SpamPreventionProcessor implements MessageProcessor {
    @Override
    public String process(Player player, String message) {
        if (message.length() > 256) {
            return null; // Deny overly long messages
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

@IocVelocityListener(multiproviderClass = SecurityListener.class)
public class LoginSecurityListener implements SecurityListener {

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        // Security check
    }
}

@IocVelocityListener(multiproviderClass = SecurityListener.class)
public class ServerSwitchSecurityListener implements SecurityListener {

    @Subscribe
    public void onServerSwitch(ServerPreConnectEvent event) {
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

## Priority Bean Loading

The `priority` attribute controls when listeners are instantiated during plugin initialization:

```java
@IocVelocityListener(priority = true)
public class CoreListener {
    // This listener is instantiated before other listeners
    // Useful when this listener sets up infrastructure needed by others
}

@IocVelocityListener  // priority defaults to false
public class RegularListener {
    // Normal priority listener
}
```

**When to use priority:**
- Listener needs to be created early in the initialization process
- Listener sets up shared state or caches needed by other components
- Listener performs critical setup operations

**Most listeners should not use priority.** Only use it when you have a specific initialization order requirement.

## Best Practices for Event Handling

### 1. Keep Event Handlers Focused

**Good - Single Responsibility:**
```java
@IocVelocityListener
public class PlayerJoinListener {

    private final PlayerDataService dataService;

    public PlayerJoinListener(PlayerDataService dataService) {
        this.dataService = dataService;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        dataService.loadPlayerData(event.getPlayer());
    }
}
```

**Bad - Doing Too Much:**
```java
@IocVelocityListener
public class EverythingListener {

    @Subscribe
    public void handleEverything(Object event) {
        // Handling multiple unrelated event types
        // Don't do this!
    }
}
```

### 2. Use Appropriate Priority

```java
// Good - Clear intention
@Subscribe(order = PostOrder.FIRST)
public void preprocessChat(PlayerChatEvent event) {
    // Early processing
}

@Subscribe(order = PostOrder.LAST)
public void logChat(PlayerChatEvent event) {
    // Monitoring - runs last
    if (event.getResult().isAllowed()) {
        logger.log(event.getMessage());
    }
}
```

### 3. Check Event Results

Many Velocity events use result objects. Always check them:

```java
@Subscribe
public void onChat(PlayerChatEvent event) {
    // Check if already denied
    if (!event.getResult().isAllowed()) {
        return;
    }

    // Process message
    String message = processMessage(event.getMessage());
    event.setResult(PlayerChatEvent.ChatResult.message(message));
}
```

### 4. Use Services for Complex Logic

**Good - Logic in Service:**
```java
@IocVelocityListener
public class ServerSwitchListener {

    private final ServerManager serverManager;

    public ServerSwitchListener(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        // Delegate to service
        serverManager.handleServerSwitch(event);
    }
}

@IocBean
public class ServerManager {
    public void handleServerSwitch(ServerPreConnectEvent event) {
        // Complex logic here - easier to test and maintain
    }
}
```

### 5. Use Adventure Components Properly

Velocity uses Adventure for text components:

```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@IocVelocityListener
public class MessageListener {

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Use Adventure components, not legacy color codes
        Component welcome = Component.text("Welcome! ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(player.getUsername())
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        player.sendMessage(welcome);
    }
}
```

### 6. Handle Async Operations Carefully

Velocity events are generally async-safe, but be careful with blocking operations:

```java
@IocVelocityListener
public class DataLoadListener {

    private final DatabaseService database;
    private final ProxyServer proxyServer;

    public DataLoadListener(DatabaseService database, ProxyServer proxyServer) {
        this.database = database;
        this.proxyServer = proxyServer;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // If this is a blocking operation, consider offloading it
        CompletableFuture.supplyAsync(() -> database.loadPlayerData(player))
            .thenAccept(data -> {
                // Process loaded data
            })
            .exceptionally(throwable -> {
                player.disconnect(Component.text("Failed to load data"));
                return null;
            });
    }
}
```

### 7. Use Conditional Loading for Features

```java
// Enable/disable features via config
@IocVelocityListener(conditionalOnProperty = "features.anti-vpn.enabled")
public class AntiVpnListener {

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        // Only active when feature is enabled
    }
}
```

### 8. Organize Listeners by Feature

```
src/main/java/com/example/myplugin/
└── listeners/
    ├── connection/
    │   ├── LoginListener.java
    │   └── DisconnectListener.java
    ├── server/
    │   ├── ServerSwitchListener.java
    │   └── ServerKickListener.java
    └── chat/
        └── ChatListener.java
```

### 9. Document Event Handler Logic

```java
@IocVelocityListener
public class AccessControlListener {

    /**
     * Prevents players from connecting to servers unless:
     * - Player has permission: server.connect.<servername>
     * - Player is in whitelist for the server
     * - Server is marked as public
     */
    @Subscribe(order = PostOrder.HIGH)
    public void onServerConnect(ServerPreConnectEvent event) {
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
        PostLoginEvent mockEvent = mock(PostLoginEvent.class);
        when(mockEvent.getPlayer()).thenReturn(mockPlayer);

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
import be.garagepoort.mcioc.tubingvelocity.annotations.IocVelocityListener;
import com.example.myplugin.services.PlayerDataService;
import com.example.myplugin.services.MessageService;
import com.example.myplugin.services.ServerTrackingService;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

@IocVelocityListener(conditionalOnProperty = "features.player-tracking.enabled")
public class PlayerTrackingListener {

    private final PlayerDataService playerDataService;
    private final MessageService messageService;
    private final ServerTrackingService serverTracking;

    @ConfigProperty("features.player-tracking.welcome-message")
    private String welcomeMessage;

    @ConfigProperty("features.player-tracking.log-server-switches")
    private boolean logServerSwitches;

    public PlayerTrackingListener(PlayerDataService playerDataService,
                                 MessageService messageService,
                                 ServerTrackingService serverTracking) {
        this.playerDataService = playerDataService;
        this.messageService = messageService;
        this.serverTracking = serverTracking;
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Load player data
        playerDataService.loadPlayerData(player);

        // Send welcome message if configured
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            messageService.send(player, Component.text(welcomeMessage));
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        // Track server switch
        serverTracking.onPlayerSwitchServer(player, serverName);

        // Log if configured
        if (logServerSwitches) {
            messageService.logServerSwitch(player, serverName);
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Save player data
        playerDataService.savePlayerData(player);

        // Clean up tracking
        serverTracking.onPlayerDisconnect(player);

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
    welcome-message: "Welcome to the network!"
    log-server-switches: true
```

## Lifecycle and Reload Behavior

### Listener Registration

Listeners are registered during plugin initialization:

1. Plugin enables via `@Subscribe` on `ProxyInitializeEvent`
2. IoC container scans for `@IocVelocityListener` classes
3. Container instantiates each listener with dependencies
4. `TubingVelocityBeanLoader.loadListenerBeans()` registers with Velocity's EventManager
5. Events start flowing to your handlers

### Reload Behavior

When you call `plugin.reload()`:

1. `EventManager.unregisterListeners(plugin)` is called - all listeners are unregistered
2. IoC container is recreated
3. All listeners are re-instantiated with fresh dependencies
4. Listeners are re-registered with Velocity's EventManager
5. Events resume with new listener instances

**Important:** Any state stored in listener fields will be lost during reload. Use services for persistent state:

```java
// Bad - state lost on reload
@IocVelocityListener
public class CounterListener {
    private int count = 0; // LOST ON RELOAD!

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        count++;
    }
}

// Good - state persists
@IocVelocityListener
public class CounterListener {
    private final CounterService counterService;

    public CounterListener(CounterService counterService) {
        this.counterService = counterService;
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        counterService.increment(); // Service handles persistence
    }
}
```

## Common Velocity Events

Here are some commonly used Velocity events:

### Connection Events

```java
import com.velocitypowered.api.event.connection.*;

@IocVelocityListener
public class ConnectionListener {

    // Player attempts to connect (can be denied)
    @Subscribe
    public void onLogin(LoginEvent event) { }

    // Player successfully logged in
    @Subscribe
    public void onPostLogin(PostLoginEvent event) { }

    // Player disconnected
    @Subscribe
    public void onDisconnect(DisconnectEvent event) { }
}
```

### Server Events

```java
import com.velocitypowered.api.event.player.*;

@IocVelocityListener
public class ServerListener {

    // Player is about to connect to a server (can be modified/denied)
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) { }

    // Player successfully connected to a server
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) { }

    // Player was kicked from a server
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) { }
}
```

### Chat Events

```java
import com.velocitypowered.api.event.player.PlayerChatEvent;

@IocVelocityListener
public class ChatListener {

    // Player sent a chat message
    @Subscribe
    public void onChat(PlayerChatEvent event) {
        // Check and modify chat
        if (event.getResult().isAllowed()) {
            String message = event.getMessage();
            // Process message
        }
    }
}
```

### Command Events

```java
import com.velocitypowered.api.event.command.CommandExecuteEvent;

@IocVelocityListener
public class CommandListener {

    // Player or console executes a command
    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        String command = event.getCommand();
        // Can modify or deny command execution
    }
}
```

## Next Steps

Now that you understand Velocity listeners with Tubing:

- [Velocity Commands](Commands.md) - Command handling in detail
- [Velocity Setup](Velocity-Setup.md) - Plugin lifecycle and initialization
- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration patterns
- [Multi-Implementation](../core/Multi-Implementation.md) - Multiple listener implementations
- [Dependency Injection](../core/Dependency-Injection.md) - Deep dive into DI patterns

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Build your first plugin
- [Project Structure](../getting-started/Project-Structure.md) - Organize your listeners
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - When listeners are created
