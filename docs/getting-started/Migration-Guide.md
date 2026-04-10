# Migration Guide

This guide walks you through converting an existing Bukkit, BungeeCord, or Velocity plugin to use Tubing. You'll learn how to refactor traditional patterns into modern dependency injection, eliminate singletons, and take advantage of Tubing's powerful features.

## Why Migrate to Tubing?

If your plugin has any of these pain points, Tubing can help:

- **Singletons everywhere**: `getInstance()` calls making code tightly coupled
- **Static managers**: Static accessors creating hidden dependencies
- **Manual configuration loading**: Parsing YAML files manually with boilerplate
- **Difficult testing**: Tightly coupled code that's hard to unit test
- **Complex initialization**: Manual object creation and wiring in `onEnable()`
- **Command registration boilerplate**: Repetitive command handler setup
- **Event listener registration**: Manual listener registration and management

Tubing eliminates these issues through dependency injection, automatic configuration loading, and platform integrations.

## Migration Overview

The migration process typically follows these steps:

1. Add Tubing dependencies to your project
2. Convert your main plugin class
3. Refactor singletons and static managers to beans
4. Convert configuration loading to `@ConfigProperty`
5. Migrate command handlers to Tubing commands
6. Migrate event listeners to Tubing listeners
7. Update initialization logic
8. Test and validate

## Step 1: Add Tubing Dependencies

Follow the [Installation Guide](Installation.md) to add Tubing to your `pom.xml`. Make sure to:

- Add the StaffPlusPlus repository
- Add the platform-specific Tubing dependency (bukkit, bungee, or velocity)
- Configure the Maven Shade Plugin with relocation
- Exclude transitive dependencies

**Important**: Relocate Tubing to avoid conflicts with other plugins.

## Step 2: Convert Your Main Plugin Class

### Bukkit

**Before:**
```java
public class MyPlugin extends JavaPlugin {

    private static MyPlugin instance;
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(configManager);
        playerDataManager = new PlayerDataManager(databaseManager);

        // Register commands
        getCommand("home").setExecutor(new HomeCommand(this, playerDataManager));
        getCommand("sethome").setExecutor(new SetHomeCommand(this, playerDataManager));

        // Register events
        getServer().getPluginManager().registerEvents(
            new PlayerListener(playerDataManager), this);

        getLogger().info("MyPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("MyPlugin disabled!");
    }

    public static MyPlugin getInstance() {
        return instance;
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled!");
    }
}
```

**What changed:**
- Extend `TubingBukkitPlugin` instead of `JavaPlugin`
- Remove the singleton pattern (`getInstance()`)
- Remove manual manager initialization
- Remove manual command and listener registration
- Tubing handles all initialization automatically

### BungeeCord

**Before:**
```java
public class MyPlugin extends Plugin {

    private static MyPlugin instance;
    private ConfigManager configManager;
    private PlayerManager playerManager;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        playerManager = new PlayerManager(configManager);

        getProxy().getPluginManager().registerCommand(
            this, new HomeCommand(this, playerManager));
        getProxy().getPluginManager().registerListener(
            this, new PlayerListener(playerManager));

        getLogger().info("MyPlugin enabled!");
    }

    public static MyPlugin getInstance() {
        return instance;
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled!");
    }
}
```

### Velocity

**Before:**
```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public class MyPlugin {

    private static MyPlugin instance;
    private final ProxyServer server;
    private ConfigManager configManager;
    private PlayerManager playerManager;

    @Inject
    public MyPlugin(ProxyServer server) {
        this.server = server;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        configManager = new ConfigManager(this);
        playerManager = new PlayerManager(configManager);

        server.getCommandManager().register("home",
            new HomeCommand(playerManager));
        server.getEventManager().register(this,
            new PlayerListener(playerManager));

        logger.info("MyPlugin enabled!");
    }

    public static MyPlugin getInstance() {
        return instance;
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public class MyPlugin extends TubingVelocityPlugin {

    @Inject
    public MyPlugin(ProxyServer server) {
        super(server);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        enable();
    }

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled!");
    }
}
```

## Step 3: Refactor Singletons to Beans

This is the most important refactoring step. Convert all singleton managers to IoC beans.

### Pattern 1: Basic Singleton Manager

**Before:**
```java
public class PlayerDataManager {

    private static PlayerDataManager instance;
    private final MyPlugin plugin;
    private final Map<UUID, PlayerData> dataMap = new HashMap<>();

    public PlayerDataManager(MyPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static PlayerDataManager getInstance() {
        return instance;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void savePlayerData(UUID uuid, PlayerData data) {
        dataMap.put(uuid, data);
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;

@IocBean
public class PlayerDataManager {

    private final Map<UUID, PlayerData> dataMap = new HashMap<>();

    // No constructor needed if no dependencies
    // Plugin reference not needed - inject where actually used

    public PlayerData getPlayerData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public void savePlayerData(UUID uuid, PlayerData data) {
        dataMap.put(uuid, data);
    }
}
```

**What changed:**
- Added `@IocBean` annotation
- Removed singleton pattern (`getInstance()`)
- Removed plugin reference (inject the plugin only where needed)
- Tubing automatically creates and manages the instance

### Pattern 2: Manager with Dependencies

**Before:**
```java
public class EconomyManager {

    private static EconomyManager instance;
    private final MyPlugin plugin;
    private final DatabaseManager database;

    public EconomyManager(MyPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        instance = this;
    }

    public static EconomyManager getInstance() {
        return instance;
    }

    public double getBalance(UUID player) {
        return database.getBalance(player);
    }

    public void setBalance(UUID player, double balance) {
        database.setBalance(player, balance);
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;

@IocBean
public class EconomyManager {

    private final DatabaseManager database;

    // Constructor injection - Tubing automatically provides DatabaseManager
    public EconomyManager(DatabaseManager database) {
        this.database = database;
    }

    public double getBalance(UUID player) {
        return database.getBalance(player);
    }

    public void setBalance(UUID player, double balance) {
        database.setBalance(player, balance);
    }
}
```

**What changed:**
- Dependencies injected via constructor
- No manual wiring needed
- No plugin reference stored unless actually needed
- Tubing resolves dependencies automatically

### Pattern 3: Static Utility Classes

**Before:**
```java
public class MessageUtils {

    private static String prefix;

    public static void init(String pluginPrefix) {
        prefix = pluginPrefix;
    }

    public static void sendMessage(Player player, String message) {
        player.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    public static void broadcast(String message) {
        Bukkit.broadcastMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class MessageService {

    @ConfigProperty("plugin.prefix")
    private String prefix;

    public void sendMessage(Player player, String message) {
        player.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }

    public void broadcast(String message) {
        Bukkit.broadcastMessage(prefix + ChatColor.translateAlternateColorCodes('&', message));
    }
}
```

**What changed:**
- Converted to instance methods instead of static
- Configuration value injected with `@ConfigProperty`
- No manual initialization needed
- Inject `MessageService` wherever messages are sent

## Step 4: Convert Configuration Loading

### Pattern 1: Manual Configuration Loading

**Before:**
```java
public class ConfigManager {

    private final MyPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(MyPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public int getMaxHomes() {
        return config.getInt("homes.max-homes", 3);
    }

    public boolean isFeatureEnabled() {
        return config.getBoolean("features.homes-enabled", true);
    }

    public String getPrefix() {
        return config.getString("messages.prefix", "&7[&aMyPlugin&7] ");
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class PluginConfiguration {

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("features.homes-enabled")
    private boolean homesEnabled;

    @ConfigProperty("messages.prefix")
    private String prefix;

    // Getters (optional - fields can be public or accessed via methods)
    public int getMaxHomes() {
        return maxHomes;
    }

    public boolean isHomesEnabled() {
        return homesEnabled;
    }

    public String getPrefix() {
        return prefix;
    }
}
```

**What changed:**
- No manual configuration loading
- Values automatically injected from `config.yml`
- No boilerplate parsing code
- Type-safe access to configuration values
- Automatic reloading support

### Pattern 2: Configuration in Service Classes

Instead of having a separate config manager, inject config values directly where needed:

**Before:**
```java
public class HomeManager {

    private final ConfigManager config;

    public HomeManager(ConfigManager config) {
        this.config = config;
    }

    public boolean canSetHome(Player player) {
        int currentHomes = getHomeCount(player);
        int maxHomes = config.getMaxHomes();
        return currentHomes < maxHomes;
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class HomeManager {

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    public boolean canSetHome(Player player) {
        int currentHomes = getHomeCount(player);
        return currentHomes < maxHomes;
    }
}
```

**Benefits:**
- Configuration values exactly where they're used
- No intermediate config manager needed
- Clear what configuration each service needs

## Step 5: Convert Command Handlers

### Bukkit Commands

**Before:**
```java
public class HomeCommand implements CommandExecutor {

    private final MyPlugin plugin;
    private final HomeManager homeManager;

    public HomeCommand(MyPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("myplugin.home")) {
            player.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        String homeName = args.length > 0 ? args[0] : "default";
        homeManager.teleportHome(player, homeName);

        return true;
    }
}

// In main class:
getCommand("home").setExecutor(new HomeCommand(this, homeManager));
```

**After:**
```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;

@IocBukkitCommandHandler(
    value = "home",
    permission = "myplugin.home",
    onlyPlayers = true
)
public class HomeCommand {

    private final HomeManager homeManager;

    public HomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    public boolean handle(CommandSender sender, Command command,
                          String label, String[] args) {
        Player player = (Player) sender;
        String homeName = args.length > 0 ? args[0] : "default";
        homeManager.teleportHome(player, homeName);
        return true;
    }
}
```

**What changed:**
- Use `@IocBukkitCommandHandler` annotation
- Permission check handled by annotation
- Player-only check handled by annotation
- Automatic registration - no manual `getCommand().setExecutor()`
- Dependencies injected automatically
- Less boilerplate, cleaner code

### BungeeCord Commands

**Before:**
```java
public class ServerCommand extends Command {

    private final MyPlugin plugin;
    private final ServerManager serverManager;

    public ServerCommand(MyPlugin plugin, ServerManager serverManager) {
        super("server");
        this.plugin = plugin;
        this.serverManager = serverManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage("Only players can use this command!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /server <name>");
            return;
        }

        serverManager.connectToServer(player, args[0]);
    }
}

// In main class:
getProxy().getPluginManager().registerCommand(
    this, new ServerCommand(this, serverManager));
```

**After:**
```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeCommandHandler;

@IocBungeeCommandHandler("server")
public class ServerCommand {

    private final ServerManager serverManager;

    public ServerCommand(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage("Only players can use this command!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            player.sendMessage("Usage: /server <name>");
            return;
        }

        serverManager.connectToServer(player, args[0]);
    }
}
```

## Step 6: Convert Event Listeners

### Bukkit Listeners

**Before:**
```java
public class PlayerListener implements Listener {

    private final MyPlugin plugin;
    private final PlayerDataManager dataManager;

    public PlayerListener(MyPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.loadPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        dataManager.savePlayerData(player.getUniqueId());
    }
}

// In main class:
getServer().getPluginManager().registerEvents(
    new PlayerListener(this, dataManager), this);
```

**After:**
```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;

@IocBukkitListener
public class PlayerListener implements Listener {

    private final PlayerDataManager dataManager;

    public PlayerListener(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.loadPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        dataManager.savePlayerData(player.getUniqueId());
    }
}
```

**What changed:**
- Add `@IocBukkitListener` annotation
- Automatic registration with Bukkit
- Dependencies injected automatically
- No manual `registerEvents()` call needed

### BungeeCord Listeners

**Before:**
```java
public class PlayerListener implements Listener {

    private final MyPlugin plugin;
    private final PlayerManager playerManager;

    public PlayerListener(MyPlugin plugin, PlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onPlayerConnect(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        playerManager.loadPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        playerManager.savePlayerData(player.getUniqueId());
    }
}

// In main class:
getProxy().getPluginManager().registerListener(
    this, new PlayerListener(this, playerManager));
```

**After:**
```java
import be.garagepoort.mcioc.tubingbungee.annotations.IocBungeeListener;

@IocBungeeListener
public class PlayerListener implements Listener {

    private final PlayerManager playerManager;

    public PlayerListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onPlayerConnect(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        playerManager.loadPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        playerManager.savePlayerData(player.getUniqueId());
    }
}
```

## Step 7: Update Dependencies Between Classes

When migrating, update all classes that used `getInstance()` to use constructor injection.

**Before:**
```java
public class WarpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        // Getting dependencies via singleton
        WarpManager warpManager = WarpManager.getInstance();
        ConfigManager configManager = ConfigManager.getInstance();

        if (!configManager.isWarpsEnabled()) {
            sender.sendMessage("Warps are disabled!");
            return true;
        }

        // Use warpManager...
        return true;
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBukkitCommandHandler("warp")
public class WarpCommand {

    private final WarpManager warpManager;

    @ConfigProperty("features.warps-enabled")
    private boolean warpsEnabled;

    // Dependencies injected via constructor
    public WarpCommand(WarpManager warpManager) {
        this.warpManager = warpManager;
    }

    public boolean handle(CommandSender sender, Command command,
                          String label, String[] args) {
        if (!warpsEnabled) {
            sender.sendMessage("Warps are disabled!");
            return true;
        }

        // Use warpManager...
        return true;
    }
}
```

## Common Pitfalls and Solutions

### Pitfall 1: Forgetting to Add Annotations

**Problem:** Classes aren't being managed by Tubing.

**Solution:** Ensure all services have `@IocBean`, commands have `@IocBukkitCommandHandler`, and listeners have `@IocBukkitListener`.

### Pitfall 2: Circular Dependencies

**Problem:** Two beans depend on each other, causing initialization to fail.

```java
@IocBean
public class ServiceA {
    public ServiceA(ServiceB serviceB) { }
}

@IocBean
public class ServiceB {
    public ServiceB(ServiceA serviceA) { }
}
```

**Solution:** Refactor to break the circular dependency. Often this means extracting shared functionality to a third service:

```java
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

### Pitfall 3: Storing Plugin Instance

**Problem:** Storing the main plugin instance when it's not needed.

**Before:**
```java
@IocBean
public class MyService {
    private final MyPlugin plugin;

    public MyService(MyPlugin plugin) {
        this.plugin = plugin;
    }
}
```

**Solution:** Only inject the plugin if you actually need it. Often you don't:

**After:**
```java
@IocBean
public class MyService {
    // No plugin reference needed if not used
}
```

If you need server access, inject `Server` (Bukkit) or `ProxyServer` (BungeeCord) directly.

### Pitfall 4: Not Relocating Tubing

**Problem:** Conflicts with other plugins using Tubing.

**Solution:** Always relocate the Tubing package in your Maven Shade Plugin configuration:

```xml
<relocation>
    <pattern>be.garagepoort.mcioc.</pattern>
    <shadedPattern>com.yourplugin.tubing.</shadedPattern>
</relocation>
```

### Pitfall 5: Using Static Access to Bukkit API

**Problem:** Static calls like `Bukkit.getServer()` are hard to test.

**Before:**
```java
@IocBean
public class TeleportService {
    public void teleport(Player player, Location location) {
        Bukkit.getScheduler().runTask(
            MyPlugin.getInstance(),
            () -> player.teleport(location)
        );
    }
}
```

**Better:**
```java
@IocBean
public class TeleportService {
    private final Server server;
    private final JavaPlugin plugin;

    public TeleportService(Server server, JavaPlugin plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    public void teleport(Player player, Location location) {
        server.getScheduler().runTask(
            plugin,
            () -> player.teleport(location)
        );
    }
}
```

Now `server` and `plugin` can be mocked for testing.

### Pitfall 6: Complex Configuration Objects

**Problem:** Trying to manually parse complex configuration sections.

**Solution:** Use `@ConfigEmbeddedObject` and `@ConfigObjectList` for complex structures.

**Before:**
```java
@IocBean
public class KitManager {

    private final Map<String, Kit> kits = new HashMap<>();

    public KitManager(MyPlugin plugin) {
        ConfigurationSection kitsSection = plugin.getConfig()
            .getConfigurationSection("kits");

        for (String kitName : kitsSection.getKeys(false)) {
            ConfigurationSection kitSection = kitsSection
                .getConfigurationSection(kitName);
            Kit kit = new Kit(
                kitName,
                kitSection.getInt("cooldown"),
                kitSection.getStringList("items")
            );
            kits.put(kitName, kit);
        }
    }
}
```

**After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigObjectList;

@IocBean
public class KitManager {

    @ConfigObjectList(value = "kits", key = "name")
    private Map<String, Kit> kits;

    // No constructor needed - map is automatically populated
}
```

See [Configuration Objects](../core/Configuration-Objects.md) for details.

## Step-by-Step Migration Example

Let's walk through a complete migration of a simple homes plugin.

### Original Plugin Structure

```
src/main/java/com/example/homes/
├── HomesPlugin.java              (extends JavaPlugin)
├── managers/
│   ├── ConfigManager.java        (singleton)
│   ├── HomeManager.java          (singleton)
│   └── DatabaseManager.java      (singleton)
├── commands/
│   ├── HomeCommand.java          (CommandExecutor)
│   ├── SetHomeCommand.java       (CommandExecutor)
│   └── DeleteHomeCommand.java    (CommandExecutor)
└── listeners/
    └── PlayerListener.java       (Listener)
```

### Step 1: Main Plugin Class

**HomesPlugin.java - Before:**
```java
public class HomesPlugin extends JavaPlugin {

    private static HomesPlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private HomeManager homeManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this, configManager);
        homeManager = new HomeManager(this, databaseManager, configManager);

        getCommand("home").setExecutor(
            new HomeCommand(this, homeManager));
        getCommand("sethome").setExecutor(
            new SetHomeCommand(this, homeManager, configManager));
        getCommand("delhome").setExecutor(
            new DeleteHomeCommand(this, homeManager));

        getServer().getPluginManager().registerEvents(
            new PlayerListener(this, homeManager), this);

        getLogger().info("HomesPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("HomesPlugin disabled!");
    }

    public static HomesPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }
}
```

**HomesPlugin.java - After:**
```java
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class HomesPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("HomesPlugin enabled!");
    }

    @Override
    protected void disable() {
        getLogger().info("HomesPlugin disabled!");
    }
}
```

### Step 2: Convert ConfigManager

**ConfigManager.java - Before:**
```java
public class ConfigManager {

    private static ConfigManager instance;
    private final HomesPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(HomesPlugin plugin) {
        this.plugin = plugin;
        this.instance = this;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public static ConfigManager getInstance() {
        return instance;
    }

    public int getMaxHomes() {
        return config.getInt("homes.max-homes", 5);
    }

    public int getTeleportDelay() {
        return config.getInt("homes.teleport-delay", 3);
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }
}
```

**ConfigManager.java - After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class PluginConfig {

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.teleport-delay")
    private int teleportDelay;

    @ConfigProperty("database.type")
    private String databaseType;

    public int getMaxHomes() {
        return maxHomes;
    }

    public int getTeleportDelay() {
        return teleportDelay;
    }

    public String getDatabaseType() {
        return databaseType;
    }
}
```

### Step 3: Convert DatabaseManager

**DatabaseManager.java - Before:**
```java
public class DatabaseManager {

    private static DatabaseManager instance;
    private final HomesPlugin plugin;
    private final ConfigManager config;
    private Connection connection;

    public DatabaseManager(HomesPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.instance = this;
        connect();
    }

    public static DatabaseManager getInstance() {
        return instance;
    }

    private void connect() {
        String type = config.getDatabaseType();
        // Connection logic...
    }

    public void saveHome(UUID player, String name, Location location) {
        // Database save logic...
    }

    public Location getHome(UUID player, String name) {
        // Database get logic...
        return null;
    }

    public void close() {
        // Close connection...
    }
}
```

**DatabaseManager.java - After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class DatabaseManager {

    @ConfigProperty("database.type")
    private String databaseType;

    private Connection connection;

    // Automatically called after bean creation
    @AfterIocLoad
    public void connect() {
        // Connection logic...
    }

    public void saveHome(UUID player, String name, Location location) {
        // Database save logic...
    }

    public Location getHome(UUID player, String name) {
        // Database get logic...
        return null;
    }

    public void close() {
        // Close connection...
    }
}
```

### Step 4: Convert HomeManager

**HomeManager.java - Before:**
```java
public class HomeManager {

    private static HomeManager instance;
    private final HomesPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;

    public HomeManager(HomesPlugin plugin, DatabaseManager database,
                       ConfigManager config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.instance = this;
    }

    public static HomeManager getInstance() {
        return instance;
    }

    public boolean canSetHome(Player player) {
        int currentHomes = getHomeCount(player);
        return currentHomes < config.getMaxHomes();
    }

    public void teleportToHome(Player player, String homeName) {
        Location home = database.getHome(player.getUniqueId(), homeName);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "Home not found!");
            return;
        }

        int delay = config.getTeleportDelay();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(home);
            player.sendMessage(ChatColor.GREEN + "Teleported to home: " + homeName);
        }, delay * 20L);
    }

    public void setHome(Player player, String homeName) {
        if (!canSetHome(player)) {
            player.sendMessage(ChatColor.RED + "You've reached the maximum number of homes!");
            return;
        }

        database.saveHome(player.getUniqueId(), homeName, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Home set: " + homeName);
    }

    private int getHomeCount(Player player) {
        // Count homes logic...
        return 0;
    }
}
```

**HomeManager.java - After:**
```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class HomeManager {

    private final DatabaseManager database;
    private final JavaPlugin plugin;

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.teleport-delay")
    private int teleportDelay;

    public HomeManager(DatabaseManager database, JavaPlugin plugin) {
        this.database = database;
        this.plugin = plugin;
    }

    public boolean canSetHome(Player player) {
        int currentHomes = getHomeCount(player);
        return currentHomes < maxHomes;
    }

    public void teleportToHome(Player player, String homeName) {
        Location home = database.getHome(player.getUniqueId(), homeName);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "Home not found!");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(home);
            player.sendMessage(ChatColor.GREEN + "Teleported to home: " + homeName);
        }, teleportDelay * 20L);
    }

    public void setHome(Player player, String homeName) {
        if (!canSetHome(player)) {
            player.sendMessage(ChatColor.RED + "You've reached the maximum number of homes!");
            return;
        }

        database.saveHome(player.getUniqueId(), homeName, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Home set: " + homeName);
    }

    private int getHomeCount(Player player) {
        // Count homes logic...
        return 0;
    }
}
```

### Step 5: Convert Commands

**HomeCommand.java - Before:**
```java
public class HomeCommand implements CommandExecutor {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;

    public HomeCommand(HomesPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("homes.use")) {
            player.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /home <name>");
            return true;
        }

        homeManager.teleportToHome(player, args[0]);
        return true;
    }
}
```

**HomeCommand.java - After:**
```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;

@IocBukkitCommandHandler(
    value = "home",
    permission = "homes.use",
    onlyPlayers = true
)
public class HomeCommand {

    private final HomeManager homeManager;

    public HomeCommand(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    public boolean handle(CommandSender sender, Command command,
                          String label, String[] args) {
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /home <name>");
            return true;
        }

        homeManager.teleportToHome(player, args[0]);
        return true;
    }
}
```

### Step 6: Convert Listener

**PlayerListener.java - Before:**
```java
public class PlayerListener implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;

    public PlayerListener(HomesPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load player homes from database
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save player data
    }
}
```

**PlayerListener.java - After:**
```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;

@IocBukkitListener
public class PlayerListener implements Listener {

    private final HomeManager homeManager;

    public PlayerListener(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load player homes from database
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save player data
    }
}
```

## Testing After Migration

After migrating, test thoroughly:

1. **Build the plugin**: `mvn clean package`
2. **Check for compilation errors**: Ensure all dependencies are resolved
3. **Test on a development server**:
   - Plugin loads without errors
   - Commands are registered and work correctly
   - Event listeners fire properly
   - Configuration values are loaded correctly
4. **Test reload functionality**: Use `/reload` or plugin reload command
5. **Check for memory leaks**: Monitor memory usage over time

## Benefits Summary

After migration, you should see:

1. **Less Code**: Eliminated singleton patterns, manual registration, and boilerplate
2. **Better Testing**: Constructor injection makes unit testing straightforward
3. **Clearer Dependencies**: Explicit constructor parameters show what each class needs
4. **Easier Maintenance**: Changes to configuration don't require refactoring parsing code
5. **Automatic Reloading**: Configuration reloads without manual handling
6. **Type Safety**: Configuration values are type-checked
7. **Platform Agnostic**: Easier to support multiple platforms (Bukkit, BungeeCord, Velocity)

## Gradual Migration Strategy

Don't have to migrate everything at once! You can migrate gradually:

1. **Start with new features**: Use Tubing for new code
2. **Migrate commands**: Easy wins with less boilerplate
3. **Migrate listeners**: Another quick migration
4. **Migrate configuration**: Replace config managers
5. **Migrate core services**: Refactor singletons to beans
6. **Remove legacy code**: Clean up old patterns

During gradual migration, you can mix traditional and Tubing patterns. The plugin instance is still accessible for legacy code that needs it.

## Need Help?

- Check the [Quick Start Guide](Quick-Start.md) for a fresh project example
- See [Configuration Injection](../core/Configuration-Injection.md) for advanced config patterns
- Read [Dependency Injection](../core/Dependency-Injection.md) for DI best practices
- Visit [Common Errors](../troubleshooting/Common-Errors.md) if you encounter issues

## Next Steps

Now that your plugin is migrated:

**Next:** [Project Structure](Project-Structure.md) - Organize your Tubing project effectively

---

**See also:**
- [Configuration Objects](../core/Configuration-Objects.md) - Complex configuration mapping
- [Multi-Implementation](../core/Multi-Implementation.md) - Plugin APIs and multi-provider patterns
- [Testing](../best-practices/Testing.md) - Unit testing with dependency injection
