# Core Classes Reference

This reference guide provides detailed information about the key classes in the Tubing framework. These classes form the foundation of the IoC container, configuration management, platform integration, GUI system, and utility functionality.

## IocContainer

**Package:** `be.garagepoort.mcioc`

The `IocContainer` class is the heart of Tubing's dependency injection framework. It manages the lifecycle of all beans (components) in your plugin, automatically resolving dependencies and providing access to registered services.

### Purpose

The IoC container:
- Scans your plugin's packages for annotated classes
- Resolves dependencies between beans
- Instantiates beans in the correct order
- Manages bean lifecycle and registration
- Provides access to registered beans

### Key Methods

#### `void init(TubingPlugin tubingPlugin)`

Initializes the IoC container. This method is called automatically by Tubing platform base classes during plugin startup.

**Parameters:**
- `tubingPlugin` - The Tubing plugin instance

**Behavior:**
1. Scans the plugin's package for annotated classes using ClassGraph
2. Discovers bean annotations and configuration classes
3. Loads and instantiates all beans in dependency order
4. Injects configuration properties and plugin references
5. Executes post-initialization hooks (`@AfterIocLoad`)

**Example:**
```java
// Automatically called by TubingBukkitPlugin
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        // Container already initialized at this point
        IocContainer container = getIocContainer();
    }
}
```

#### `<T> T get(Class<T> clazz)`

Retrieves a single bean by its class type.

**Parameters:**
- `clazz` - The class type of the bean to retrieve

**Returns:** The bean instance of the specified type

**Throws:**
- `IocException` - If the bean is not found or multiple implementations exist for an interface

**Example:**
```java
// Retrieve a concrete class
PlayerService playerService = container.get(PlayerService.class);

// Retrieve by interface (if only one implementation exists)
DatabaseService database = container.get(DatabaseService.class);
```

**Interface Resolution:**
- For concrete classes: Returns the exact bean of that type
- For interfaces: Returns the implementation if exactly one exists
- Throws exception if multiple implementations exist (use `getList()` instead)
- Throws exception if no implementation exists

#### `<T> List<T> getList(Class<T> clazz)`

Retrieves all beans of a specific type or interface.

**Parameters:**
- `clazz` - The class type or interface to retrieve

**Returns:** A list of all bean instances matching the specified type

**Example:**
```java
// Get all implementations of an interface
List<RewardHandler> handlers = container.getList(RewardHandler.class);

// Iterate through all implementations
for (RewardHandler handler : handlers) {
    handler.processReward(player, reward);
}
```

**Use Cases:**
- Multi-provider pattern with `@IocMultiProvider`
- Plugin systems where multiple implementations are expected
- Strategy pattern implementations
- Event handler collections

#### `void registerBean(Object bean)`

Manually registers a bean in the container.

**Parameters:**
- `bean` - The bean instance to register

**Behavior:**
- Registers the bean by its exact class type
- Replaces any existing bean of the same type
- Bean is immediately available for injection
- Lifecycle is not managed by the container

**Example:**
```java
// Register an external library object
ExternalService externalService = ExternalLibrary.createService();
container.registerBean(externalService);

// Register a runtime-created bean
DynamicFeature feature = new DynamicFeature(config);
container.registerBean(feature);
```

**Use Cases:**
- Integrating external library objects
- Runtime bean creation
- Platform-provided objects (like Bukkit's Server)
- Test mocks

#### `ScanResult getReflections()`

Returns the ClassGraph scan result for advanced classpath scanning.

**Returns:** The `ScanResult` from ClassGraph

**Example:**
```java
ScanResult scanResult = container.getReflections();

// Find all classes implementing an interface
List<Class<? extends Plugin>> plugins = scanResult
    .getClassesImplementing(Plugin.class)
    .loadClasses(Plugin.class);

// Find all classes with an annotation
List<Class<?>> controllers = scanResult
    .getClassesWithAnnotation(Controller.class)
    .loadClasses();
```

### Usage Patterns

#### Pattern 1: Constructor Injection (Recommended)

```java
@IocBean
public class PlayerManager {
    private final PlayerRepository repository;
    private final PermissionService permissions;

    // Constructor injection - automatic
    public PlayerManager(PlayerRepository repository,
                        PermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }
}
```

#### Pattern 2: Direct Container Access

```java
@IocBean
public class PluginAPI {
    private final IocContainer container;

    public PluginAPI(IocContainer container) {
        this.container = container;
    }

    // Provide dynamic bean lookup
    public <T> T getService(Class<T> serviceClass) {
        return container.get(serviceClass);
    }

    public <T> List<T> getServices(Class<T> serviceClass) {
        return container.getList(serviceClass);
    }
}
```

#### Pattern 3: Manual Bean Registration

```java
@TubingConfiguration
public class PlatformConfiguration {

    @IocBeanProvider
    public Server provideBukkitServer(@InjectTubingPlugin TubingPlugin plugin) {
        TubingBukkitPlugin bukkitPlugin = (TubingBukkitPlugin) plugin;
        return bukkitPlugin.getServer();
    }
}
```

### See Also
- [IoC Container](../core/IoC-Container.md) - Complete container documentation
- [Dependency Injection](../core/Dependency-Injection.md) - DI patterns and best practices
- [Bean Registration](../core/Bean-Registration.md) - Using `@IocBean`

---

## ConfigurationLoader

**Package:** `be.garagepoort.mcioc.configuration`

The `ConfigurationLoader` manages loading, parsing, migrating, and accessing YAML configuration files. It handles automatic configuration updates, nested property references, and provides type-safe access to configuration values.

### Purpose

The ConfigurationLoader:
- Loads multiple YAML configuration files
- Automatically updates configuration files with new options
- Runs configuration migrations
- Resolves nested configuration placeholders (`{{key}}`)
- Provides type-safe configuration value access
- Integrates with `@ConfigProperty` injection

### Key Methods

#### `Map<String, FileConfiguration> getConfigurationFiles()`

Returns all loaded configuration files mapped by their identifiers.

**Returns:** A map of configuration file IDs to `FileConfiguration` objects

**Example:**
```java
Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();
FileConfiguration mainConfig = configs.get("config");
FileConfiguration messagesConfig = configs.get("messages");
```

#### `<T> Optional<T> getConfigValue(String identifier)`

Retrieves a configuration value with support for file selectors and nested placeholders.

**Parameters:**
- `identifier` - The configuration path (format: `"fileId:path.to.property"` or `"path.to.property"`)

**Returns:** `Optional<T>` containing the value, or empty if not found

**Configuration Path Format:**

Simple path (from default `config.yml`):
```java
Optional<String> host = configLoader.getConfigValue("database.host");
```

With file selector:
```java
// Retrieve from messages.yml
Optional<String> prefix = configLoader.getConfigValue("messages:prefix");
```

With nested placeholders:
```yaml
# config.yml
environment: "production"
database:
  host: "db-%environment%.example.com"
```

```java
// Resolves to: "db-production.example.com"
Optional<String> host = configLoader.getConfigValue("database.host");
```

#### `Optional<String> getConfigStringValue(String identifier)`

Similar to `getConfigValue()`, but specifically retrieves string values.

**Parameters:**
- `identifier` - The configuration path

**Returns:** `Optional<String>` containing the string value, or empty if not found

**Example:**
```java
String message = configLoader.getConfigStringValue("messages.welcome")
    .orElse("Welcome!");

String prefix = configLoader.getConfigStringValue("messages:prefix")
    .orElse("[Plugin]");
```

### Usage Patterns

#### Pattern 1: Field Injection (Recommended)

```java
@IocBean
public class DatabaseService {
    @ConfigProperty("database.host")
    private String host;

    @ConfigProperty("database.port")
    private int port;

    // Values automatically injected before constructor
}
```

#### Pattern 2: Constructor Injection

```java
@IocBean
public class ServerConfig {
    private final String serverName;
    private final int maxPlayers;

    public ServerConfig(@ConfigProperty("server.name") String serverName,
                       @ConfigProperty("server.max-players") int maxPlayers) {
        this.serverName = serverName;
        this.maxPlayers = maxPlayers;
    }
}
```

#### Pattern 3: Direct Access

```java
@IocBean
public class DynamicConfigService {
    private final ConfigurationLoader configLoader;

    public DynamicConfigService(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    public String getEnvironmentValue(String key) {
        String env = configLoader.getConfigStringValue("environment")
            .orElse("development");
        return configLoader.getConfigStringValue(env + "." + key)
            .orElse("");
    }
}
```

#### Pattern 4: Multiple Configuration Files

```java
@IocBean
public class TubingConfigurationProvider extends ConfigurationProviderSupplier {

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml", "config", "1.0"),
            new ConfigurationFile("messages.yml", "messages", "1.0"),
            new ConfigurationFile("database.yml", "database", "1.0")
        );
    }
}
```

### Configuration Placeholder Syntax

The ConfigurationLoader supports two types of placeholders:

**Nested Placeholders (`{{key}}`):**
```yaml
# config.yml
server-type: "production"
api:
  url: "https://api-{{server-type}}.example.com"
  key: "{{api:credentials.key}}"
```

**Value Placeholders (`%key%`):**
```yaml
# config.yml
database:
  host: "localhost"
  port: 5432
  url: "jdbc:postgresql://%database.host%:%database.port%/mydb"
```

Both are resolved recursively during configuration loading.

### See Also
- [Configuration Files](../core/Configuration-Files.md) - Multiple configs, migrations, auto-update
- [Configuration Injection](../core/Configuration-Injection.md) - Inject config values
- [Configuration Objects](../core/Configuration-Objects.md) - Map complex objects from YAML

---

## TubingBukkitPlugin / TubingBungeePlugin / TubingVelocityPlugin

**Packages:**
- `be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin`
- `be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin`
- `be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin`

These are the base classes for creating Tubing plugins on different Minecraft platforms. They handle IoC container initialization, bean loading, and plugin lifecycle management.

### Purpose

Platform base classes:
- Initialize the IoC container automatically
- Scan and load annotated beans
- Register platform-specific components (commands, listeners)
- Manage plugin lifecycle (enable, disable, reload)
- Provide access to the container and platform APIs

### TubingBukkitPlugin

**Extends:** `org.bukkit.plugin.java.JavaPlugin`
**Implements:** `TubingPlugin`

#### Key Methods

##### `protected abstract void enable()`

Called after the IoC container is initialized and all beans are loaded.

**Example:**
```java
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        getLogger().info("Plugin enabled with Tubing!");
        // Container is fully initialized at this point
    }

    @Override
    protected void disable() {
        getLogger().info("Plugin disabled");
    }
}
```

##### `protected void beforeEnable()`

Called before IoC container initialization. Use for early setup that must happen before bean loading.

**Example:**
```java
@Override
protected void beforeEnable() {
    // Load external dependencies
    // Initialize early systems
    getLogger().info("Preparing to load...");
}
```

##### `public IocContainer getIocContainer()`

Returns the IoC container instance.

**Returns:** The `IocContainer`

**Example:**
```java
@Override
protected void enable() {
    PlayerService playerService = getIocContainer().get(PlayerService.class);
    playerService.loadAllPlayers();
}
```

##### `public void reload()`

Reloads the plugin by reinitializing the container and reloading all beans.

**Behavior:**
1. Executes `@BeforeTubingReload` hooks
2. Calls `beforeReload()` if overridden
3. Reloads configuration files
4. Unregisters all listeners and commands
5. Reinitializes the IoC container
6. Reloads all beans

**Example:**
```java
@IocBukkitCommandHandler("myplugin")
public class MainCommand {
    public boolean handle(CommandSender sender, String[] args) {
        if (args[0].equals("reload")) {
            TubingBukkitPlugin.getPlugin().reload();
            sender.sendMessage("§aPlugin reloaded!");
            return true;
        }
        return false;
    }
}
```

##### `public static TubingBukkitPlugin getPlugin()`

Returns the singleton plugin instance.

**Returns:** The `TubingBukkitPlugin` instance

**Example:**
```java
// Access from anywhere
TubingBukkitPlugin plugin = TubingBukkitPlugin.getPlugin();
plugin.getLogger().info("Logging from anywhere");
```

#### Usage Example

```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void beforeEnable() {
        getLogger().info("Pre-initialization...");
    }

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled!");

        // Access beans from container
        PlayerManager playerManager = getIocContainer().get(PlayerManager.class);
        playerManager.initialize();
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled");
    }

    @Override
    protected void beforeReload() {
        getLogger().info("Preparing to reload...");
    }
}
```

### TubingBungeePlugin

**Extends:** `net.md_5.bungee.api.plugin.Plugin`
**Implements:** `TubingPlugin`

Similar to `TubingBukkitPlugin` but for BungeeCord proxies.

#### Key Differences

- Uses BungeeCord's `Plugin` base class
- Manages proxy-specific components
- Unregisters commands and listeners on reload

**Example:**
```java
package com.example.myproxyplugin;

import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyProxyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        getLogger().info("Proxy plugin enabled!");
    }

    @Override
    protected void disable() {
        getLogger().info("Proxy plugin disabled");
    }
}
```

### TubingVelocityPlugin

**Implements:** `TubingPlugin`

Velocity-specific base class with modern proxy support.

#### Key Features

- Constructor-based dependency injection for `ProxyServer` and data directory
- Uses Velocity's event-driven initialization
- Supports command aliases and modern event system

**Example:**
```java
package com.example.myvelocityplugin;

import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;

@Plugin(
    id = "myvelocityplugin",
    name = "MyVelocityPlugin",
    version = "1.0.0"
)
public class MyVelocityPlugin extends TubingVelocityPlugin {

    @Inject
    public MyVelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        super(server, dataDirectory);
    }

    @Override
    protected void enable() {
        getLogger().info("Velocity plugin enabled!");
    }

    @Override
    public String getName() {
        return "MyVelocityPlugin";
    }
}
```

### See Also
- [Bukkit Setup](../bukkit/Bukkit-Setup.md) - Complete Bukkit integration guide
- [BungeeCord Setup](../bungee/Bungee-Setup.md) - BungeeCord integration
- [Velocity Setup](../velocity/Velocity-Setup.md) - Velocity integration

---

## GuiActionService

**Package:** `be.garagepoort.mcioc.tubinggui`

The `GuiActionService` manages the Tubing GUI framework, handling GUI controller registration, action execution, inventory management, and GUI navigation history.

### Purpose

The GuiActionService:
- Registers GUI controllers and actions
- Executes GUI actions based on player input
- Manages player inventories and GUI state
- Handles async GUI operations
- Manages GUI navigation history (back button support)
- Processes GUI templates with Freemarker
- Handles GUI exceptions

### Key Methods

#### `void loadGuiControllers(IocContainer iocContainer)`

Scans for and registers all GUI controllers in the container.

**Parameters:**
- `iocContainer` - The IoC container

**Behavior:**
- Finds all classes annotated with `@GuiController`
- Scans each controller for `@GuiAction` methods
- Registers action routes and their handler methods

**Example:**
```java
// Called automatically by Tubing during initialization
guiActionService.loadGuiControllers(iocContainer);
```

#### `void executeAction(Player player, String actionQuery)`

Executes a GUI action based on an action query string.

**Parameters:**
- `player` - The player executing the action
- `actionQuery` - The action query (format: `"route?param1=value1&param2=value2"`)

**Behavior:**
1. Parses the action query
2. Finds the registered action handler
3. Resolves method parameters from the query
4. Invokes the action method
5. Processes the return value (GUI, template, redirect, etc.)
6. Updates navigation history

**Example:**
```java
// Execute an action from code
guiActionService.executeAction(player, "shop/category?category=weapons&page=1");

// From a GUI template with clickable items
guiActionService.executeAction(player, "home/manage?homeId=5");
```

**Special Actions:**
- `"BACK"` - Navigate to previous GUI in history
- `"CLOSE"` - Close the current GUI

#### `void showGui(Player player, TubingGui tubingGui)`

Opens a GUI inventory for a player.

**Parameters:**
- `player` - The player to show the GUI to
- `tubingGui` - The GUI to display

**Behavior:**
- Closes current inventory
- Maps `TubingGui` to Bukkit `Inventory`
- Opens the inventory for the player
- Stores GUI state for the player

**Example:**
```java
@GuiController
public class ShopController {

    @GuiAction("shop/main")
    public TubingGui showMainShop() {
        TubingGui gui = new TubingGui("Shop", 54);
        // Add items to GUI
        return gui;
    }
}
```

#### `void showGuiTemplate(Player player, GuiTemplate guiTemplate, GuiActionQuery actionQuery, GuiActionConfig guiActionConfig)`

Renders and displays a GUI from a Freemarker template.

**Parameters:**
- `player` - The player to show the GUI to
- `guiTemplate` - The template configuration
- `actionQuery` - The current action query
- `guiActionConfig` - The action configuration

**Example:**
```java
@GuiController
public class PlayerListController {

    @GuiAction("players/list")
    public GuiTemplate showPlayers() {
        Map<String, Object> params = new HashMap<>();
        params.put("players", Bukkit.getOnlinePlayers());
        return GuiTemplate.builder()
            .template("players/list.gui.ftl")
            .params(params)
            .build();
    }
}
```

#### `Optional<TubingGui> getTubingGui(Player player)`

Returns the currently open GUI for a player.

**Parameters:**
- `player` - The player

**Returns:** `Optional<TubingGui>` containing the GUI if one is open

**Example:**
```java
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();

    Optional<TubingGui> currentGui = guiActionService.getTubingGui(player);
    if (!currentGui.isPresent()) {
        return; // Not a Tubing GUI
    }

    // Handle click in Tubing GUI
    event.setCancelled(true);
}
```

#### `void removeInventory(Player player)`

Removes the GUI state for a player.

**Parameters:**
- `player` - The player

**Example:**
```java
@EventHandler
public void onInventoryClose(InventoryCloseEvent event) {
    Player player = (Player) event.getPlayer();
    guiActionService.removeInventory(player);
}
```

#### `void registerExceptionHandler(Class<? extends Exception> clazz, GuiExceptionHandler guiExceptionHandler)`

Registers a custom exception handler for GUI actions.

**Parameters:**
- `clazz` - The exception class to handle
- `guiExceptionHandler` - The handler implementation

**Example:**
```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {PlayerNotFoundException.class})
public class PlayerNotFoundExceptionHandler implements GuiExceptionHandler {

    @Override
    public void accept(Player player, Throwable throwable) {
        player.sendMessage("§cPlayer not found: " + throwable.getMessage());
    }
}
```

### Usage Patterns

#### Pattern 1: Basic GUI Controller

```java
@GuiController
public class MainMenuController {

    @GuiAction("menu/main")
    public TubingGui showMainMenu() {
        TubingGui gui = new TubingGui("§6Main Menu", 27);

        // Add items
        gui.setItem(10, ItemBuilder.create(Material.DIAMOND)
            .name("§aShop")
            .action("shop/main")
            .build());

        gui.setItem(16, ItemBuilder.create(Material.COMPASS)
            .name("§bWarps")
            .action("warps/list")
            .build());

        return gui;
    }
}
```

#### Pattern 2: Parameterized Actions

```java
@GuiController
public class ShopController {

    @GuiAction("shop/category")
    public TubingGui showCategory(@GuiParam("category") String category,
                                   @GuiParam(value = "page", defaultValue = "1") int page) {
        TubingGui gui = new TubingGui("§6Shop - " + category, 54);

        // Load items for category and page
        List<ShopItem> items = shopService.getItemsForCategory(category, page);

        // Populate GUI
        for (int i = 0; i < items.size(); i++) {
            gui.setItem(i, createShopItemStack(items.get(i)));
        }

        // Add navigation
        if (page > 1) {
            gui.setItem(45, ItemBuilder.create(Material.ARROW)
                .name("§7Previous Page")
                .action("shop/category?category=" + category + "&page=" + (page - 1))
                .build());
        }

        return gui;
    }
}
```

#### Pattern 3: Async GUI Actions

```java
@GuiController
public class PlayerProfileController {

    @GuiAction("profile/view")
    public AsyncGui viewProfile(@GuiParam("playerName") String playerName) {
        return AsyncGui.async(() -> {
            // This runs asynchronously
            PlayerData data = database.loadPlayerData(playerName);

            // Return GUI to show (executed on main thread)
            return createProfileGui(data);
        });
    }

    private TubingGui createProfileGui(PlayerData data) {
        TubingGui gui = new TubingGui("§6Profile: " + data.getName(), 27);
        // Populate with player data
        return gui;
    }
}
```

#### Pattern 4: GUI Templates

```java
@GuiController
public class PlayersController {

    @GuiAction("players/list")
    public GuiTemplate listPlayers(@GuiParam(value = "page", defaultValue = "1") int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        Map<String, Object> params = new HashMap<>();
        params.put("players", players);
        params.put("page", page);
        params.put("totalPages", (int) Math.ceil(players.size() / 45.0));

        return GuiTemplate.builder()
            .template("players/list.gui.ftl")
            .params(params)
            .build();
    }
}
```

### See Also
- [GUI Controllers](../gui/GUI-Controllers.md) - Complete controller documentation
- [GUI Actions](../gui/GUI-Actions.md) - Action handling and parameters
- [GUI Templates](../gui/GUI-Templates.md) - Freemarker template usage
- [GUI Async](../gui/GUI-Async.md) - Asynchronous GUI operations

---

## Messages

**Package:** `be.garagepoort.mcioc.tubingbukkit.messaging`

The `Messages` service provides a comprehensive messaging system for sending formatted, colorized messages to players with support for prefixes, placeholders, and multi-line content.

### Purpose

The Messages service:
- Translates color codes (`&` to `§`)
- Adds configurable message prefixes
- Integrates with PlaceholderAPI
- Supports multi-line messages
- Broadcasts to players or groups
- Handles permission-based messaging

### Key Methods

#### `void send(CommandSender sender, String message)`

Sends a message with the configured prefix.

**Parameters:**
- `sender` - The command sender (player or console)
- `message` - The message text (supports color codes and placeholders)

**Example:**
```java
messages.send(player, "&aWelcome to the server!");
// Output: "[PluginPrefix] Welcome to the server!" (colorized)
```

#### `void sendNoPrefix(CommandSender sender, String message)`

Sends a message without the plugin prefix.

**Parameters:**
- `sender` - The command sender
- `message` - The message text

**Example:**
```java
messages.sendNoPrefix(player, "&7This message has no prefix");
// Output: "This message has no prefix" (colorized, no prefix)
```

#### `void broadcast(String message)`

Broadcasts a message to all online players.

**Parameters:**
- `message` - The message to broadcast

**Example:**
```java
messages.broadcast("&6Server will restart in 5 minutes!");
```

#### `void send(Collection<? extends Player> receivers, String message)`

Sends a message to a collection of players.

**Parameters:**
- `receivers` - The players to send to
- `message` - The message text

**Example:**
```java
Collection<Player> teamMembers = getTeamMembers();
messages.send(teamMembers, "&aYour team won the match!");
```

#### `void send(Player player, String message, String permission)`

Sends a message to a player only if they have the specified permission.

**Parameters:**
- `player` - The player
- `message` - The message text
- `permission` - The required permission

**Example:**
```java
messages.send(player, "&eAdmin notification: Something happened", "myplugin.admin");
```

#### `void sendGroupMessage(String message, String permission)`

Broadcasts a message to all players with a specific permission.

**Parameters:**
- `message` - The message to send
- `permission` - The required permission

**Example:**
```java
messages.sendGroupMessage("&c[ALERT] Suspicious activity detected", "myplugin.admin.alerts");
```

#### `String colorize(String message)`

Translates color codes without sending the message.

**Parameters:**
- `message` - The message text with color codes

**Returns:** The colorized string

**Example:**
```java
String colored = messages.colorize("&aGreen &cRed &9Blue");
// Returns: "§aGreen §cRed §9Blue"
```

#### `String parse(Player player, String message)`

Parses placeholders and colorizes a message.

**Parameters:**
- `player` - The player (for placeholder context)
- `message` - The message text

**Returns:** The fully processed string

**Example:**
```java
String parsed = messages.parse(player, "&aHello %player_name%!");
// Returns: "§aHello Steve!" (if PlaceholderAPI is installed)
```

### Usage Patterns

#### Pattern 1: Basic Messaging

```java
@IocBean
public class WelcomeService {
    private final Messages messages;

    public WelcomeService(Messages messages) {
        this.messages = messages;
    }

    public void welcomePlayer(Player player) {
        messages.send(player, "&aWelcome to the server, &e%player_name%&a!");
    }
}
```

#### Pattern 2: Multi-Line Messages

```java
messages.send(player,
    "&6=== Server Rules ===\n" +
    "&71. Be respectful\n" +
    "&72. No griefing\n" +
    "&73. Have fun!"
);
```

#### Pattern 3: Configuration-Based Messages

```java
@IocBean
public class MessageService {
    private final Messages messages;
    private final ConfigurationLoader configLoader;

    public MessageService(Messages messages, ConfigurationLoader configLoader) {
        this.messages = messages;
        this.configLoader = configLoader;
    }

    public void sendWelcome(Player player) {
        String message = configLoader.getConfigStringValue("messages.welcome")
            .orElse("&aWelcome!");
        messages.send(player, message);
    }
}
```

#### Pattern 4: Permission-Based Broadcasts

```java
@IocBean
public class AlertService {
    private final Messages messages;

    public AlertService(Messages messages) {
        this.messages = messages;
    }

    public void sendAdminAlert(String alert) {
        messages.sendGroupMessage("&c[ADMIN] " + alert, "myplugin.admin");
    }

    public void sendStaffAlert(String alert) {
        messages.sendGroupMessage("&e[STAFF] " + alert, "myplugin.staff");
    }
}
```

### Color Code Support

The Messages service supports all standard Minecraft color and formatting codes:

**Colors:**
- `&0` - Black, `&1` - Dark Blue, `&2` - Dark Green, `&3` - Dark Aqua
- `&4` - Dark Red, `&5` - Dark Purple, `&6` - Gold, `&7` - Gray
- `&8` - Dark Gray, `&9` - Blue, `&a` - Green, `&b` - Aqua
- `&c` - Red, `&d` - Light Purple, `&e` - Yellow, `&f` - White

**Formatting:**
- `&k` - Obfuscated, `&l` - Bold, `&m` - Strikethrough
- `&n` - Underline, `&o` - Italic, `&r` - Reset

**Escaping:**
- Use `&&` to display a literal `&` character

### See Also
- [Messaging](../bukkit/Messaging.md) - Complete messaging guide
- [Configuration Injection](../core/Configuration-Injection.md) - Loading messages from config

---

## TubingPermissionService

**Package:** `be.garagepoort.mcioc.tubingbukkit.permissions`

The `TubingPermissionService` interface provides an abstraction layer for permission checks, allowing easy integration with Bukkit's native permissions or third-party permission plugins.

### Purpose

The TubingPermissionService:
- Provides a single interface for all permission checks
- Supports both players and command senders
- Works with Bukkit's native permission system by default
- Can be easily replaced with custom implementations
- Integrates with Vault, LuckPerms, or custom permission systems

### Interface Definition

```java
public interface TubingPermissionService {
    boolean has(Player player, String permission);
    boolean has(CommandSender sender, String permission);
}
```

### Key Methods

#### `boolean has(Player player, String permission)`

Checks if a player has a specific permission.

**Parameters:**
- `player` - The player to check
- `permission` - The permission node (e.g., `"myplugin.admin.ban"`)

**Returns:** `true` if the player has the permission, `false` otherwise

**Example:**
```java
if (permissionService.has(player, "myplugin.admin.ban")) {
    // Player can ban others
    banPlayer(target);
} else {
    player.sendMessage("§cYou don't have permission!");
}
```

#### `boolean has(CommandSender sender, String permission)`

Checks if a command sender has a specific permission.

**Parameters:**
- `sender` - The command sender (player or console)
- `permission` - The permission node

**Returns:** `true` if the sender has the permission, `false` otherwise

**Example:**
```java
if (permissionService.has(sender, "myplugin.reload")) {
    plugin.reload();
    sender.sendMessage("§aPlugin reloaded!");
} else {
    sender.sendMessage("§cNo permission!");
}
```

### Default Implementation

Tubing provides a default implementation using Bukkit's native permissions:

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultTubingPermissionService implements TubingPermissionService {

    @Override
    public boolean has(Player player, String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }
}
```

The `@ConditionalOnMissingBean` annotation means this is only used if you don't provide your own implementation.

### Usage Patterns

#### Pattern 1: Service-Level Checks

```java
@IocBean
public class TeleportService {
    private final TubingPermissionService permissionService;

    public TeleportService(TubingPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public void teleport(Player player, Location location) {
        if (!permissionService.has(player, "myplugin.teleport")) {
            player.sendMessage("§cNo permission to teleport!");
            return;
        }

        player.teleport(location);
        player.sendMessage("§aTeleported!");
    }
}
```

#### Pattern 2: Command Integration

```java
@IocBukkitCommandHandler(
    value = "ban",
    permission = "myplugin.admin.ban"  // Automatically checked
)
public class BanCommand extends AbstractCmd {
    // Permission checked before executeCmd() is called
}
```

#### Pattern 3: Custom Implementation

```java
@IocBean
public class VaultPermissionService implements TubingPermissionService {
    private Permission vaultPermission;

    public VaultPermissionService() {
        setupVault();
    }

    private void setupVault() {
        RegisteredServiceProvider<Permission> rsp =
            Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            vaultPermission = rsp.getProvider();
        }
    }

    @Override
    public boolean has(Player player, String permission) {
        if (vaultPermission != null) {
            return vaultPermission.has(player, permission);
        }
        return player.hasPermission(permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true; // Console has all permissions
        }
        return has((Player) sender, permission);
    }
}
```

### See Also
- [Permissions](../bukkit/Permissions.md) - Complete permission system guide
- [Commands](../bukkit/Commands.md) - Command permission integration

---

## ReflectionUtils

**Package:** `be.garagepoort.mcioc`

The `ReflectionUtils` class provides utility methods for common reflection operations used internally by the IoC container and available for advanced use cases.

### Purpose

ReflectionUtils provides:
- Method scanning for specific annotations
- Configuration value retrieval with placeholder support
- Nested configuration property resolution
- Integration with Tubing's configuration system

### Key Methods

#### `static List<Method> getMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation>... annotations)`

Finds all methods in a class (including inherited) that have specific annotations.

**Parameters:**
- `type` - The class to search
- `annotations` - One or more annotation classes to match (methods must have ALL specified annotations)

**Returns:** List of `Method` objects with all specified annotations

**Behavior:**
- Searches the entire class hierarchy up to (but not including) `Object.class`
- Includes private, protected, package-private, and public methods
- Methods must have ALL specified annotations (AND condition)

**Example:**
```java
// Find all @IocBeanProvider methods
List<Method> providers = ReflectionUtils.getMethodsAnnotatedWith(
    MyConfiguration.class,
    IocBeanProvider.class
);

// Find methods with multiple annotations
List<Method> secureEndpoints = ReflectionUtils.getMethodsAnnotatedWith(
    MyController.class,
    Authorized.class,
    RateLimited.class
);
```

#### `static <T> Optional<T> getConfigValue(String identifier, Map<String, FileConfiguration> configs)`

Retrieves a configuration value with support for file selectors and nested placeholders.

**Parameters:**
- `identifier` - The configuration path (format: `"fileId:path"` or `"path"`)
- `configs` - Map of configuration file IDs to `FileConfiguration` objects

**Returns:** `Optional<T>` containing the value, or empty if not found

**File Selector Syntax:**

Simple path (from default `config.yml`):
```java
Optional<String> host = ReflectionUtils.getConfigValue("database.host", configs);
```

With file selector:
```java
// Get from messages.yml
Optional<String> prefix = ReflectionUtils.getConfigValue("messages:prefix", configs);
```

**Nested Placeholders:**

Configuration:
```yaml
environment: "production"
database:
  host: "db-%environment%.example.com"
```

Usage:
```java
Optional<String> host = ReflectionUtils.getConfigValue("database.host", configs);
// Resolves to: "db-production.example.com"
```

#### `static Optional<String> getConfigStringValue(String identifier, Map<String, FileConfiguration> configs)`

Similar to `getConfigValue()` but specifically retrieves string values.

**Parameters:**
- `identifier` - The configuration path
- `configs` - Map of configuration files

**Returns:** `Optional<String>` containing the string value

**Example:**
```java
Optional<String> message = ReflectionUtils.getConfigStringValue(
    "messages:welcome",
    configs
);

String welcomeMsg = message.orElse("Welcome!");
```

### Usage Patterns

#### Pattern 1: Custom Annotation Scanning

```java
@IocBean
public class EventScanner {

    public void registerHandlers(Object listener) {
        List<Method> handlers = ReflectionUtils.getMethodsAnnotatedWith(
            listener.getClass(),
            EventHandler.class
        );

        for (Method handler : handlers) {
            // Register each handler method
            registerHandler(listener, handler);
        }
    }
}
```

#### Pattern 2: Configuration Object Building

```java
@IocBean
public class ConfigBuilder {

    public DatabaseConfig buildConfig(Map<String, FileConfiguration> configs) {
        String host = ReflectionUtils.getConfigStringValue("database.host", configs)
            .orElse("localhost");

        Integer port = ReflectionUtils.<Integer>getConfigValue("database.port", configs)
            .orElse(3306);

        return new DatabaseConfig(host, port);
    }
}
```

#### Pattern 3: Dynamic Configuration Resolution

```java
@IocBean
public class EnvironmentService {
    private final Map<String, FileConfiguration> configs;

    public EnvironmentService(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigurationFiles();
    }

    public String getEnvironmentValue(String key) {
        String env = ReflectionUtils.getConfigStringValue("environment", configs)
            .orElse("development");

        String envKey = env + "." + key;
        return ReflectionUtils.getConfigStringValue(envKey, configs)
            .orElseGet(() -> ReflectionUtils.getConfigStringValue(key, configs)
                .orElse(""));
    }
}
```

#### Pattern 4: Configuration Validation

```java
@IocBean
public class ConfigValidator {
    private final Map<String, FileConfiguration> configs;

    public ConfigValidator(ConfigurationLoader configLoader) {
        this.configs = configLoader.getConfigurationFiles();
    }

    public void validateRequiredKeys(String... requiredKeys) {
        List<String> missing = new ArrayList<>();

        for (String key : requiredKeys) {
            Optional<Object> value = ReflectionUtils.getConfigValue(key, configs);
            if (!value.isPresent()) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required configuration keys: " + missing
            );
        }
    }
}
```

### Placeholder Resolution

ReflectionUtils supports recursive placeholder resolution with the `%key%` syntax:

**Simple placeholder:**
```yaml
server: "localhost"
url: "http://%server%:8080"
```

**Nested placeholders:**
```yaml
env: "production"
server: "db-%env%.example.com"
url: "jdbc://%server%/mydb"
```

**Cross-file placeholders:**
```yaml
# config.yml
database:
  host: "%database:connection.host%"

# database.yml (id: "database")
connection:
  host: "localhost"
```

### Best Practices

#### When to Use ReflectionUtils

**Use for:**
- Custom annotation processors
- Plugin extension systems
- Dynamic configuration resolution
- Configuration validation utilities
- Building custom IoC features

**Don't use for:**
- Normal dependency injection (use constructor parameters)
- Standard configuration injection (use `@ConfigProperty`)
- Performance-critical code (reflection is slower)

#### Performance Considerations

```java
// Bad: Scanning every time
public void handleRequest(Object handler) {
    List<Method> methods = ReflectionUtils.getMethodsAnnotatedWith(
        handler.getClass(),
        RequestHandler.class
    );
    // Process...
}

// Good: Cache scanned methods
private final Map<Class<?>, List<Method>> cache = new ConcurrentHashMap<>();

public void handleRequest(Object handler) {
    List<Method> methods = cache.computeIfAbsent(
        handler.getClass(),
        clazz -> ReflectionUtils.getMethodsAnnotatedWith(clazz, RequestHandler.class)
    );
    // Process...
}
```

### See Also
- [Reflection Utilities](../advanced/Reflection-Utilities.md) - Complete guide with advanced examples
- [Configuration Files](../core/Configuration-Files.md) - FileConfiguration API
- [Bean Lifecycle](../core/Bean-Lifecycle.md) - How ReflectionUtils is used internally

---

## Summary

These core classes form the foundation of the Tubing framework:

- **IocContainer** - Manages the lifecycle and dependency injection of all beans
- **ConfigurationLoader** - Handles loading, parsing, and accessing configuration files
- **TubingBukkitPlugin/Bungee/Velocity** - Platform base classes that initialize the framework
- **GuiActionService** - Manages the GUI framework with controllers, actions, and navigation
- **Messages** - Provides formatted, colorized messaging with placeholder support
- **TubingPermissionService** - Abstraction layer for permission checks
- **ReflectionUtils** - Utility methods for reflection operations and configuration access

Understanding these classes is essential for advanced Tubing usage and customization.

## See Also

- [IoC Container](../core/IoC-Container.md) - Complete container documentation
- [Configuration Files](../core/Configuration-Files.md) - Configuration management
- [Bukkit Setup](../bukkit/Bukkit-Setup.md) - Platform integration
- [GUI Controllers](../gui/GUI-Controllers.md) - GUI framework
- [Best Practices](../best-practices/Common-Patterns.md) - Design patterns and recommendations
