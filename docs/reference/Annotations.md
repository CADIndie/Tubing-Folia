# Annotations Reference

This comprehensive reference lists all Tubing annotations with their purposes, attributes, targets, usage examples, and related annotations. Annotations are organized by category for easy lookup.

## Table of Contents

- [Core Annotations](#core-annotations)
- [Bukkit Annotations](#bukkit-annotations)
- [BungeeCord Annotations](#bungeecord-annotations)
- [Velocity Annotations](#velocity-annotations)
- [GUI Annotations](#gui-annotations)
- [Configuration Annotations](#configuration-annotations)

## Core Annotations

These annotations are part of the `tubing-core` module and provide the fundamental IoC container functionality.

### @IocBean

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Class

Marks a class for automatic discovery and registration as a bean in the IoC container.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Only register bean if property is set in configuration |
| `priority` | boolean | `false` | Load bean with priority (instantiated early) |
| `multiproviderClass` | Class | `Object.class` | Register bean as multi-provider for specified interface |

**Example:**

```java
// Basic bean
@IocBean
public class PlayerService {
    // Implementation
}

// With priority
@IocBean(priority = true)
public class ConfigurationLoader {
    // Loaded early
}

// Conditional registration
@IocBean(conditionalOnProperty = "features.homes.enabled")
public class HomeService {
    // Only registered if features.homes.enabled = true
}

// Multi-provider shorthand
@IocBean(multiproviderClass = RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler {
    // Registered as multi-provider
}
```

**Related:**
- [@IocMultiProvider](#iocmultiprovider) - Alternative multi-provider syntax
- [@ConditionalOnMissingBean](#conditionalonmissingbean) - Conditional default beans
- [@TubingConfiguration](#tubingconfiguration) - Configuration classes

---

### @IocBeanProvider

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Method

Marks a method as a factory method that produces a bean. The method must be static and located in a `@TubingConfiguration` class.

**Attributes:** None

**Example:**

```java
@TubingConfiguration
public class DatabaseConfiguration {

    @IocBeanProvider
    public static Database provideDatabase(
            @ConfigProperty("database.host") String host,
            @ConfigProperty("database.port") int port) {
        return new Database(host, port);
    }
}
```

**Related:**
- [@TubingConfiguration](#tubingconfiguration) - Configuration class annotation
- [@IocBeanMultiProvider](#iocbeanmultiprovider) - Factory for multiple beans

---

### @IocBeanMultiProvider

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Method

Marks a method as a factory that produces multiple beans as a collection. The method must be static, return a `Collection`, and be in a `@TubingConfiguration` class.

**Attributes:** None

**Example:**

```java
@TubingConfiguration
public class HandlerConfiguration {

    @IocBeanMultiProvider
    public static Collection<CommandHandler> provideHandlers(
            DatabaseService database) {
        List<CommandHandler> handlers = new ArrayList<>();
        handlers.add(new AdminHandler(database));
        handlers.add(new PlayerHandler(database));
        return handlers;
    }
}
```

**Related:**
- [@IocBeanProvider](#iocbeanprovider) - Single bean factory
- [@IocMultiProvider](#iocmultiprovider) - Multi-provider class annotation

---

### @IocMulti

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Parameter

Injects all implementations of an interface as a `List`. Used in constructor parameters to receive multiple bean implementations.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | Class | (required) | Interface class to inject implementations for |

**Example:**

```java
@IocBean
public class NotificationService {
    private final List<NotificationHandler> handlers;

    public NotificationService(
            @IocMulti(NotificationHandler.class) List<NotificationHandler> handlers) {
        this.handlers = handlers;
    }

    public void notifyAll(Player player, String message) {
        for (NotificationHandler handler : handlers) {
            handler.notify(player, message);
        }
    }
}
```

**Related:**
- [@IocMultiProvider](#iocmultiprovider) - Register beans as multi-providers
- [@IocBean](#iocbean).multiproviderClass - Alternative syntax

---

### @IocMultiProvider

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Class, Method

Registers a bean as a provider for one or more interfaces, allowing multiple implementations to be injected as a list.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | Class[] | (required) | Interface(s) to provide implementations for |

**Example:**

```java
// Single interface
@IocBean
@IocMultiProvider(NotificationHandler.class)
public class ChatNotificationHandler implements NotificationHandler {
    @Override
    public void notify(Player player, String message) {
        player.sendMessage(message);
    }
}

// Multiple interfaces
@IocBean
@IocMultiProvider({EventHandler.class, Validator.class})
public class PlayerJoinHandler implements EventHandler, Validator {
    @Override
    public void handle(Event event) { /* ... */ }

    @Override
    public boolean validate(Object obj) { /* ... */ }
}
```

**Related:**
- [@IocMulti](#iocmulti) - Inject multi-provider list
- [@IocBean](#iocbean).multiproviderClass - Shorthand syntax

---

### @ConditionalOnMissingBean

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Class

Registers a bean only if no other bean of the same type exists. Useful for providing default implementations that can be overridden.

**Attributes:** None

**Example:**

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultStorageProvider implements StorageProvider {
    // Only used if no other StorageProvider is registered
}
```

**Related:**
- [@IocBean](#iocbean).conditionalOnProperty - Conditional on configuration

---

### @AfterIocLoad

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Method

Marks a method to be executed after all beans have been loaded. The method must be static and in a `@TubingConfiguration` class. Useful for validation or initialization logic.

**Attributes:** None

**Example:**

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateConfiguration(
            DatabaseService database,
            ConfigService config) {
        if (!database.isConnected()) {
            throw new IllegalStateException("Database not connected");
        }
    }
}
```

**Related:**
- [@TubingConfiguration](#tubingconfiguration) - Configuration class annotation

---

### @TubingConfiguration

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc`
**Target:** Class

Marks a class as a configuration class that can contain bean provider methods and initialization methods.

**Attributes:** None

**Example:**

```java
@TubingConfiguration
public class AppConfiguration {

    @IocBeanProvider
    public static Database provideDatabase() {
        return new Database();
    }

    @AfterIocLoad
    public static void initialize() {
        // Initialization logic
    }
}
```

**Related:**
- [@IocBeanProvider](#iocbeanprovider) - Factory methods
- [@AfterIocLoad](#afteriocload) - Initialization methods

---

### @InjectTubingPlugin

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.load`
**Target:** Field, Parameter

Injects the plugin instance into a bean. Works with Bukkit, BungeeCord, and Velocity plugins.

**Attributes:** None

**Example:**

```java
@IocBean
public class TaskScheduler {
    private final TubingPlugin plugin;

    public TaskScheduler(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    public void scheduleTask(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater((Plugin) plugin, task, delayTicks);
    }
}
```

**Related:** None

---

## Bukkit Annotations

These annotations are part of the `tubing-bukkit` module and provide Bukkit-specific functionality.

### @IocBukkitListener

**Module:** `tubing-bukkit`
**Package:** `be.garagepoort.mcioc.tubingbukkit.annotations`
**Target:** Class

Marks a class as a Bukkit event listener. The class must implement Bukkit's `Listener` interface. Listeners are automatically registered with Bukkit.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Only register if property is set |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {

    private final PlayerService playerService;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerService.loadPlayerData(event.getPlayer());
    }
}

// Conditional listener
@IocBukkitListener(conditionalOnProperty = "features.pvp.enabled")
public class PvpListener implements Listener {
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // PvP handling
    }
}
```

**Related:**
- [@IocBukkitCommandHandler](#iocbukkitcommandhandler) - Command handlers
- [@IocBukkitMessageListener](#iocbukkitmessagelistener) - Plugin message listeners

---

### @IocBukkitCommandHandler

**Module:** `tubing-bukkit`
**Package:** `be.garagepoort.mcioc.tubingbukkit.annotations`
**Target:** Class

Marks a class as a Bukkit command handler. Commands are automatically registered in plugin.yml and with Bukkit.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Command name |
| `permission` | String | `""` | Required permission to execute |
| `onlyPlayers` | boolean | `false` | Restrict to player execution only |
| `conditionalOnProperty` | String | `""` | Only register if property is set |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
// Basic command
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
        Player player = (Player) sender;
        homeService.teleportHome(player, args.length > 0 ? args[0] : "default");
        return true;
    }
}

// With permission and player-only
@IocBukkitCommandHandler(
    value = "admin",
    permission = "myplugin.admin",
    onlyPlayers = false
)
public class AdminCommand extends AbstractCmd {
    // Console can execute
}
```

**Related:**
- [@IocBukkitSubCommand](#iocbukkitsubcommand) - Subcommand handlers
- [@IocBukkitListener](#iocbukkitlistener) - Event listeners

---

### @IocBukkitSubCommand

**Module:** `tubing-bukkit`
**Package:** `be.garagepoort.mcioc.tubingbukkit.annotations`
**Target:** Class

Marks a class as a subcommand handler. Subcommands are dispatched by root commands.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `root` | String | (required) | Root command name |
| `action` | String | (required) | Subcommand action name |
| `permission` | String | `""` | Required permission |
| `onlyPlayers` | boolean | `false` | Player-only execution |
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `SubCommand.class` | Register as multi-provider (default: SubCommand.class) |

**Example:**

```java
@IocBukkitSubCommand(
    root = "home",
    action = "set",
    permission = "myplugin.home.set",
    onlyPlayers = true
)
public class SetHomeSubCommand extends SubCommand {

    private final HomeService homeService;

    public SetHomeSubCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        String homeName = args.length > 0 ? args[0] : "default";
        homeService.setHome(player, homeName, player.getLocation());
        return true;
    }
}
```

**Related:**
- [@IocBukkitCommandHandler](#iocbukkitcommandhandler) - Root command handlers

---

### @IocBukkitMessageListener

**Module:** `tubing-bukkit`
**Package:** `be.garagepoort.mcioc.tubingbukkit.annotations`
**Target:** Class

Marks a class as a plugin message channel listener. The class must implement `PluginMessageListener`.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `channel` | String | (required) | Plugin message channel name |
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocBukkitMessageListener(channel = "myplugin:data")
public class DataChannelListener implements PluginMessageListener {

    private final DataService dataService;

    public DataChannelListener(DataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        dataService.processMessage(player, message);
    }
}
```

**Related:**
- [@IocBukkitListener](#iocbukkitlistener) - Event listeners

---

### BeforeTubingReload (Interface)

**Module:** `tubing-bukkit`
**Package:** `be.garagepoort.mcioc.tubingbukkit.annotations`
**Target:** Class (interface implementation)

Interface for beans that need to execute logic before plugin reload. Implement this interface and register as a bean to receive reload callbacks.

**Methods:**

```java
void execute(TubingBukkitPlugin tubingPlugin);
```

**Example:**

```java
@IocBean
public class CacheCleanupService implements BeforeTubingReload {

    private final CacheService cache;

    public CacheCleanupService(CacheService cache) {
        this.cache = cache;
    }

    @Override
    public void execute(TubingBukkitPlugin tubingPlugin) {
        cache.clear();
        System.out.println("Cache cleared before reload");
    }
}
```

**Related:** None

---

## BungeeCord Annotations

These annotations are part of the `tubing-bungee` module and provide BungeeCord-specific functionality.

### @IocBungeeListener

**Module:** `tubing-bungee`
**Package:** `be.garagepoort.mcioc.tubingbungee.annotations`
**Target:** Class

Marks a class as a BungeeCord event listener. The class must implement BungeeCord's `Listener` interface.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocBungeeListener
public class PlayerJoinListener implements Listener {

    private final PlayerService playerService;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        playerService.loadPlayerData(event.getPlayer());
    }
}
```

**Related:**
- [@IocBungeeCommandHandler](#iocbungeecommandhandler) - Command handlers

---

### @IocBungeeCommandHandler

**Module:** `tubing-bungee`
**Package:** `be.garagepoort.mcioc.tubingbungee.annotations`
**Target:** Class

Marks a class as a BungeeCord command handler. The class must extend BungeeCord's `Command`.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocBungeeCommandHandler
public class ServerCommand extends Command {

    private final ServerService serverService;

    public ServerCommand(ServerService serverService) {
        super("server", "bungeecord.command.server", "hub");
        this.serverService = serverService;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /server <name>");
            return;
        }
        serverService.sendToServer((ProxiedPlayer) sender, args[0]);
    }
}
```

**Related:**
- [@IocBungeeListener](#iocbungeelistener) - Event listeners

---

### BeforeTubingReload (Interface)

**Module:** `tubing-bungee`
**Package:** `be.garagepoort.mcioc.tubingbungee.annotations`
**Target:** Class (interface implementation)

BungeeCord version of the reload callback interface.

**Methods:**

```java
void execute(TubingBungeePlugin tubingPlugin);
```

**Example:**

```java
@IocBean
public class CacheCleanupService implements BeforeTubingReload {

    @Override
    public void execute(TubingBungeePlugin tubingPlugin) {
        // Cleanup before reload
    }
}
```

**Related:** None

---

## Velocity Annotations

These annotations are part of the `tubing-velocity` module and provide Velocity-specific functionality.

### @IocVelocityListener

**Module:** `tubing-velocity`
**Package:** `be.garagepoort.mcioc.tubingvelocity.annotations`
**Target:** Class

Marks a class as a Velocity event listener. Methods should use Velocity's `@Subscribe` annotation.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocVelocityListener
public class PlayerJoinListener {

    private final PlayerService playerService;

    public PlayerJoinListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent event) {
        playerService.loadPlayerData(event.getPlayer());
    }
}
```

**Related:**
- [@IocVelocityCommandHandler](#iocvelocitycommandhandler) - Command handlers

---

### @IocVelocityCommandHandler

**Module:** `tubing-velocity`
**Package:** `be.garagepoort.mcioc.tubingvelocity.annotations`
**Target:** Class

Marks a class as a Velocity command handler. The class must implement Velocity's `SimpleCommand`.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Command name |
| `aliases` | String[] | `""` | Command aliases |
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@IocVelocityCommandHandler(value = "hub", aliases = {"lobby"})
public class HubCommand implements SimpleCommand {

    private final ServerService serverService;

    public HubCommand(ServerService serverService) {
        this.serverService = serverService;
    }

    @Override
    public void execute(Invocation invocation) {
        Player player = (Player) invocation.source();
        serverService.sendToHub(player);
    }
}
```

**Related:**
- [@IocVelocityListener](#iocvelocitylistener) - Event listeners

---

### BeforeTubingReload (Interface)

**Module:** `tubing-velocity`
**Package:** `be.garagepoort.mcioc.tubingvelocity.annotations`
**Target:** Class (interface implementation)

Velocity version of the reload callback interface.

**Methods:**

```java
void execute(TubingVelocityPlugin tubingPlugin);
```

**Example:**

```java
@IocBean
public class CacheCleanupService implements BeforeTubingReload {

    @Override
    public void execute(TubingVelocityPlugin tubingPlugin) {
        // Cleanup before reload
    }
}
```

**Related:** None

---

## GUI Annotations

These annotations are part of the `tubing-bukkit-gui` module and provide GUI framework functionality.

### @GuiController

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Class

Marks a class as a GUI controller. Controllers handle GUI actions and return views.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@GuiController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GuiAction("shop:browse")
    public TubingGui browseShop(Player player) {
        return new TubingGui.Builder("Shop", 54)
            .addItem(/* ... */)
            .build();
    }

    @GuiAction("shop:purchase")
    public TubingGui purchaseItem(Player player,
                                   @GuiParam("item") String itemId) {
        shopService.purchase(player, itemId);
        return browseShop(player);
    }
}
```

**Related:**
- [@GuiAction](#guiaction) - Action methods
- [@GuiParam](#guiparam) - Action parameters

---

### @GuiAction

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Method

Maps an action identifier to a controller method. When a player interacts with a GUI, the framework executes the corresponding action.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Action identifier (e.g., "shop:buy") |
| `overrideHistory` | boolean | `true` | Replace last history entry |
| `skipHistory` | boolean | `false` | Don't add to history |

**Example:**

```java
@GuiController
public class MenuController {

    // Basic action
    @GuiAction("menu:main")
    public TubingGui showMain(Player player) {
        return buildMainMenu();
    }

    // With parameters
    @GuiAction("menu:category")
    public TubingGui showCategory(Player player,
                                   @GuiParam("category") String category) {
        return buildCategoryMenu(category);
    }

    // Skip history (refresh action)
    @GuiAction(value = "menu:refresh", skipHistory = true)
    public TubingGui refresh(Player player) {
        return showMain(player);
    }

    // Override history (pagination)
    @GuiAction(value = "menu:page", overrideHistory = true)
    public TubingGui changePage(Player player,
                                @GuiParam("page") int page) {
        return buildPagedMenu(page);
    }
}
```

**Related:**
- [@GuiController](#guicontroller) - Controller class
- [@GuiParam](#guiparam) - Extract parameters
- [@CurrentAction](#currentaction) - Get full action query

---

### @GuiParam

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Parameter

Extracts a parameter from the action query string and injects it into the method parameter.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Parameter name |
| `defaultValue` | String | `""` | Default value if parameter is missing |

**Example:**

```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String itemId,
                         @GuiParam(value = "quantity", defaultValue = "1") int quantity,
                         @GuiParam(value = "confirm", defaultValue = "false") boolean confirm) {
    // Action: "shop:buy?item=diamond_sword&quantity=5&confirm=true"
    // itemId = "diamond_sword"
    // quantity = 5
    // confirm = true
}
```

**Supported Types:**
- String
- int / Integer
- long / Long
- boolean / Boolean
- double / Double
- float / Float
- byte / Byte
- short / Short

**Related:**
- [@GuiAction](#guiaction) - Action definition
- [@GuiParams](#guiparams) - Get all parameters as map

---

### @GuiParams

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Parameter

Injects all action parameters as a `Map<String, String>`.

**Attributes:** None

**Example:**

```java
@GuiAction("shop:filter")
public TubingGui filterShop(Player player,
                            @GuiParams Map<String, String> params) {
    String category = params.get("category");
    String minPrice = params.get("minPrice");
    String maxPrice = params.get("maxPrice");

    // Build filtered view
    return buildFilteredShop(category, minPrice, maxPrice);
}
```

**Related:**
- [@GuiParam](#guiparam) - Extract single parameter
- [@CurrentAction](#currentaction) - Get full action query

---

### @CurrentAction

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Parameter

Injects the complete action query string (including parameters) as a String.

**Attributes:** None

**Example:**

```java
@GuiAction("menu:view")
public TubingGui viewMenu(Player player,
                          @CurrentAction String currentAction) {
    // currentAction = "menu:view?page=2&category=weapons"

    // Useful for building back buttons or saving state
    return new TubingGui.Builder("Menu", 54)
        .addItem(new TubingGuiItem.Builder(null, 0)
            .withLeftClickAction(TubingGuiActions.BACK)
            .build())
        .build();
}
```

**Related:**
- [@GuiAction](#guiaction) - Action definition
- [@GuiParams](#guiparams) - Get parameters as map

---

### @InteractableItems

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui`
**Target:** Parameter

Injects items from interactable slots in the current GUI as a `List<ItemStack>`.

**Attributes:** None

**Example:**

```java
@GuiAction("auction:create")
public TubingGui createAuction(Player player,
                               @InteractableItems List<ItemStack> items) {
    if (items.isEmpty()) {
        player.sendMessage("Please place items to auction");
        return GuiActionReturnType.KEEP_OPEN;
    }

    auctionService.createAuction(player, items);
    player.sendMessage("Auction created!");
    return null; // Close GUI
}
```

**Related:**
- [@GuiAction](#guiaction) - Action definition

---

### @GuiExceptionHandlerProvider

**Module:** `tubing-bukkit-gui`
**Package:** `be.garagepoort.mcioc.tubinggui.exceptions`
**Target:** Class

Marks a class as a GUI exception handler. Handles exceptions thrown during GUI action execution.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `exceptions` | Class[] | (required) | Exception types to handle |
| `conditionalOnProperty` | String | `""` | Conditional registration |
| `priority` | boolean | `false` | Load with priority |
| `multiproviderClass` | Class | `Object.class` | Register as multi-provider |

**Example:**

```java
@GuiExceptionHandlerProvider(exceptions = {InsufficientFundsException.class})
public class InsufficientFundsExceptionHandler implements GuiExceptionHandler {

    @Override
    public TubingGuiActionResult handleException(Exception exception,
                                                  Player player,
                                                  String action) {
        InsufficientFundsException ex = (InsufficientFundsException) exception;
        player.sendMessage("Insufficient funds! Need: " + ex.getRequired());

        return TubingGuiActionResult.keepOpen();
    }
}

// Handle multiple exception types
@GuiExceptionHandlerProvider(exceptions = {
    NullPointerException.class,
    IllegalArgumentException.class
})
public class CommonExceptionHandler implements GuiExceptionHandler {

    @Override
    public TubingGuiActionResult handleException(Exception exception,
                                                  Player player,
                                                  String action) {
        player.sendMessage("An error occurred: " + exception.getMessage());
        return TubingGuiActionResult.back();
    }
}
```

**Related:**
- [@GuiController](#guicontroller) - GUI controllers
- [@GuiAction](#guiaction) - Actions that may throw exceptions

---

## Configuration Annotations

These annotations are part of the `tubing-core` module and provide configuration injection functionality.

### @ConfigProperty

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.configuration`
**Target:** Field, Parameter, Method

Injects a configuration value from YAML files into a field, constructor parameter, or setter method.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Property path in YAML (dot notation) |
| `required` | boolean | `false` | Throw error if property is missing |
| `error` | String | `""` | Custom error message if required property is missing |

**Example:**

```java
@IocBean
public class HomeService {
    // Field injection
    @ConfigProperty("homes.max-homes")
    private int maxHomes = 3; // Default value

    @ConfigProperty(value = "homes.cooldown-seconds", required = true)
    private int cooldownSeconds;

    @ConfigProperty(
        value = "homes.database.url",
        required = true,
        error = "Database URL is required! Please add 'homes.database.url' to config.yml"
    )
    private String databaseUrl;

    private final PlayerRepository repository;

    // Constructor parameter injection
    public HomeService(PlayerRepository repository,
                      @ConfigProperty("homes.enabled") boolean enabled) {
        this.repository = repository;
    }

    // Setter injection
    @ConfigProperty("homes.message")
    public void setMessage(String message) {
        this.message = message;
    }
}
```

**config.yml:**
```yaml
homes:
  max-homes: 5
  cooldown-seconds: 120
  enabled: true
  message: "Home set!"
  database:
    url: "jdbc:mysql://localhost:3306/minecraft"
```

**Related:**
- [@ConfigProperties](#configproperties) - Common property prefix
- [@ConfigTransformer](#configtransformer) - Transform configuration values
- [@ConfigEmbeddedObject](#configembeddedobject) - Nested objects
- [@ConfigObjectList](#configobjectlist) - Lists of objects

---

### @ConfigProperties

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.configuration`
**Target:** Class

Defines a common prefix for all `@ConfigProperty` annotations in a class. Reduces repetition for related properties.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | (required) | Common property path prefix |

**Example:**

```java
// Without @ConfigProperties
@IocBean
public class HomeService {
    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldownSeconds;

    @ConfigProperty("homes.enabled")
    private boolean enabled;
}

// With @ConfigProperties - cleaner!
@IocBean
@ConfigProperties("homes")
public class HomeService {
    @ConfigProperty("max-homes")
    private int maxHomes;

    @ConfigProperty("cooldown-seconds")
    private int cooldownSeconds;

    @ConfigProperty("enabled")
    private boolean enabled;
}
```

**config.yml:**
```yaml
homes:
  max-homes: 5
  cooldown-seconds: 120
  enabled: true
```

**Related:**
- [@ConfigProperty](#configproperty) - Individual property injection

---

### @ConfigTransformer

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.configuration`
**Target:** Field, Parameter

Applies custom transformations to configuration values before injection. Transformers implement `IConfigTransformer<T, S>`.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | Class<? extends IConfigTransformer>[] | (required) | Transformer class(es) to apply |

**Example:**

```java
@IocBean
public class GameConfig {
    // Transform string to enum
    @ConfigProperty("difficulty")
    @ConfigTransformer(ToEnum.class)
    private Difficulty difficulty;

    // Transform strings to lowercase
    @ConfigProperty("allowed-commands")
    @ConfigTransformer(ToLowerCase.class)
    private List<String> allowedCommands;

    // Transform material names with wildcards
    @ConfigProperty("allowed-blocks")
    @ConfigTransformer(ToMaterials.class)
    private Set<Material> allowedBlocks;

    // Chain multiple transformers (applied in order)
    @ConfigProperty("command-list")
    @ConfigTransformer({ToLowerCase.class, ValidateCommands.class})
    private List<String> commands;
}

// Custom transformer
public class ToUUID implements IConfigTransformer<UUID, String> {
    @Override
    public UUID mapConfig(String config) {
        return UUID.fromString(config);
    }
}

@IocBean
public class PlayerConfig {
    @ConfigProperty("admin-uuid")
    @ConfigTransformer(ToUUID.class)
    private UUID adminUuid;
}
```

**config.yml:**
```yaml
difficulty: "HARD"
allowed-commands:
  - "HOME"
  - "SPAWN"
  - "TPA"
allowed-blocks:
  - "STONE"
  - "*_WOOD"
  - "DIAMOND_*"
admin-uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5"
```

**Related:**
- [@ConfigProperty](#configproperty) - Property injection

---

### @ConfigEmbeddedObject

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.configuration`
**Target:** Field, Parameter

Maps a YAML section to a Java object. The target class should have `@ConfigProperty` annotated fields.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | Class | (required) | Class to map YAML section to |

**Example:**

```java
// Embedded object class
public class DatabaseSettings {
    @ConfigProperty("host")
    private String host = "localhost";

    @ConfigProperty("port")
    private int port = 3306;

    @ConfigProperty("username")
    private String username;

    @ConfigProperty("password")
    private String password;

    // Getters...
}

// Bean using embedded object
@IocBean
@ConfigProperties("app")
public class AppConfig {
    @ConfigProperty("database")
    @ConfigEmbeddedObject(DatabaseSettings.class)
    private DatabaseSettings database;

    public DatabaseSettings getDatabase() {
        return database;
    }
}
```

**config.yml:**
```yaml
app:
  database:
    host: "localhost"
    port: 3306
    username: "minecraft"
    password: "secret123"
```

**Related:**
- [@ConfigProperty](#configproperty) - Property injection
- [@ConfigObjectList](#configobjectlist) - Lists of objects

---

### @ConfigObjectList

**Module:** `tubing-core`
**Package:** `be.garagepoort.mcioc.configuration`
**Target:** Field, Parameter

Maps a YAML list of objects to a Java `List` of objects. Each object in the list is mapped to the specified class.

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | Class | (required) | Class to map each list item to |

**Example:**

```java
// Object class
public class ShopItem {
    @ConfigProperty("name")
    private String name;

    @ConfigProperty("material")
    private String material;

    @ConfigProperty("price")
    private int price;

    @ConfigProperty("description")
    private List<String> description;

    // Getters...
}

// Bean using object list
@IocBean
public class ShopConfig {
    @ConfigProperty("items")
    @ConfigObjectList(ShopItem.class)
    private List<ShopItem> items;

    public List<ShopItem> getItems() {
        return items;
    }
}
```

**config.yml:**
```yaml
items:
  - name: "Diamond Sword"
    material: "DIAMOND_SWORD"
    price: 500
    description:
      - "A powerful weapon"
      - "Sharpness V"

  - name: "Iron Armor Set"
    material: "IRON_CHESTPLATE"
    price: 250
    description:
      - "Full protection"

  - name: "Golden Apple"
    material: "GOLDEN_APPLE"
    price: 100
    description:
      - "Restores health"
```

**Related:**
- [@ConfigProperty](#configproperty) - Property injection
- [@ConfigEmbeddedObject](#configembeddedobject) - Single embedded object

---

## Quick Reference Table

### Bean Registration

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@IocBean](#iocbean) | Class | Register class as bean |
| [@IocBeanProvider](#iocbeanprovider) | Method | Factory method for bean |
| [@IocBeanMultiProvider](#iocbeanmultiprovider) | Method | Factory for multiple beans |
| [@ConditionalOnMissingBean](#conditionalonmissingbean) | Class | Register only if no other bean exists |
| [@TubingConfiguration](#tubingconfiguration) | Class | Configuration class |

### Multi-Implementation

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@IocMulti](#iocmulti) | Parameter | Inject list of implementations |
| [@IocMultiProvider](#iocmultiprovider) | Class, Method | Register as multi-provider |

### Lifecycle

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@AfterIocLoad](#afteriocload) | Method | Execute after all beans loaded |
| [BeforeTubingReload](#beforetubingreload-interface) | Interface | Execute before plugin reload |

### Bukkit Platform

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@IocBukkitListener](#iocbukkitlistener) | Class | Event listener |
| [@IocBukkitCommandHandler](#iocbukkitcommandhandler) | Class | Command handler |
| [@IocBukkitSubCommand](#iocbukkitsubcommand) | Class | Subcommand handler |
| [@IocBukkitMessageListener](#iocbukkitmessagelistener) | Class | Plugin message listener |

### BungeeCord Platform

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@IocBungeeListener](#iocbungeelistener) | Class | Event listener |
| [@IocBungeeCommandHandler](#iocbungeecommandhandler) | Class | Command handler |

### Velocity Platform

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@IocVelocityListener](#iocvelocitylistener) | Class | Event listener |
| [@IocVelocityCommandHandler](#iocvelocitycommandhandler) | Class | Command handler |

### GUI Framework

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@GuiController](#guicontroller) | Class | GUI controller |
| [@GuiAction](#guiaction) | Method | Action handler method |
| [@GuiParam](#guiparam) | Parameter | Extract action parameter |
| [@GuiParams](#guiparams) | Parameter | Get all parameters as map |
| [@CurrentAction](#currentaction) | Parameter | Get full action query |
| [@InteractableItems](#interactableitems) | Parameter | Get items from GUI slots |
| [@GuiExceptionHandlerProvider](#guiexceptionhandlerprovider) | Class | GUI exception handler |

### Configuration

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@ConfigProperty](#configproperty) | Field, Parameter, Method | Inject configuration value |
| [@ConfigProperties](#configproperties) | Class | Common property prefix |
| [@ConfigTransformer](#configtransformer) | Field, Parameter | Transform configuration value |
| [@ConfigEmbeddedObject](#configembeddedobject) | Field, Parameter | Map YAML section to object |
| [@ConfigObjectList](#configobjectlist) | Field, Parameter | Map YAML list to object list |

### Other

| Annotation | Target | Purpose |
|------------|--------|---------|
| [@InjectTubingPlugin](#injecttubingplugin) | Field, Parameter | Inject plugin instance |

---

## See Also

- [Bean Registration](../core/Bean-Registration.md) - Detailed guide on registering beans
- [Configuration Injection](../core/Configuration-Injection.md) - Configuration system guide
- [Multi-Implementation](../core/Multi-Implementation.md) - Multi-provider pattern guide
- [Event Listeners](../bukkit/Event-Listeners.md) - Bukkit event handling
- [Commands](../bukkit/Commands.md) - Bukkit command system
- [GUI Controllers](../gui/GUI-Controllers.md) - GUI framework guide
