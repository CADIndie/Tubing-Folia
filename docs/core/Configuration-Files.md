# Configuration Files

Tubing provides a powerful configuration file management system that goes beyond simple YAML loading. It handles multiple configuration files, automatic discovery, auto-save functionality, configuration migrations, and automatic updates when you add new properties to your plugin.

## Overview

The configuration system includes:

- **Multiple YAML Files**: Organize configuration by feature or concern
- **Automatic Discovery**: Files are discovered and loaded during container initialization
- **Auto-Save**: Files are automatically created from resources if they don't exist
- **Auto-Update**: New properties are automatically added to existing config files
- **Configuration Migrations**: Programmatic migrations for version updates
- **Property References**: Reference values from one config file in another
- **Type Safety**: Access configurations through the `ConfigurationLoader` service

## The ConfigurationLoader Service

`ConfigurationLoader` is the core service that manages all configuration files in Tubing. It's automatically created as a priority bean during container initialization.

### Key Features

```java
@IocBean(priority = true)
public class ConfigurationLoader {

    // Get all loaded configuration files
    public Map<String, FileConfiguration> getConfigurationFiles();

    // Get a configuration value with type inference
    public <T> Optional<T> getConfigValue(String identifier);

    // Get a configuration value as string
    public Optional<String> getConfigStringValue(String identifier);
}
```

The `ConfigurationLoader`:
- Has `priority = true` to ensure it loads before other beans
- Loads all configuration files during initialization
- Provides access to `FileConfiguration` objects
- Supports property references between config files
- Handles auto-updates and migrations

## Setting Up Configuration Files

### Default Configuration (config.yml)

By default, Tubing looks for a `config.yml` file in your plugin's resources:

```yaml
# src/main/resources/config.yml
plugin:
  debug: false
  language: en
  autosave-interval: 300

features:
  homes-enabled: true
  warps-enabled: true
  teleportation-delay: 3
```

If this file exists in your plugin's JAR, Tubing automatically:
1. Creates the file in the plugin's data folder if it doesn't exist
2. Loads it into the `ConfigurationLoader`
3. Makes it available for `@ConfigProperty` injection

### Multiple Configuration Files

For larger plugins, split configuration into multiple files by implementing `TubingConfigurationProvider`:

```java
package com.example.myplugin.config;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.common.TubingConfigurationProvider;
import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.load.InjectTubingPlugin;
import be.garagepoort.mcioc.TubingPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public MyConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml"),
            new ConfigurationFile("homes.yml"),
            new ConfigurationFile("warps.yml"),
            new ConfigurationFile("messages.yml")
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

**Important:** When you provide a custom `TubingConfigurationProvider`, the default provider is disabled due to `@ConditionalOnMissingBean` on `DefaultTubingConfigurationProvider`.

### ConfigurationFile Constructor Options

The `ConfigurationFile` class has three constructors:

```java
// Basic - path only, identifier derived from path
new ConfigurationFile("config.yml")  // identifier: "config"

// With custom identifier
new ConfigurationFile("subdir/feature.yml", "feature")

// With custom identifier and ignore auto-updater
new ConfigurationFile("messages.yml", "messages", true)
```

**Parameters:**
- **path**: Relative path to the YAML file in your plugin's data folder
- **identifier**: Unique identifier used for property references (defaults to filename without .yml)
- **ignoreUpdater**: If `true`, skips auto-update (useful for user-modified files like messages)

## File Discovery and Loading Process

When the container initializes, `ConfigurationLoader` follows this process:

### 1. Save Files from Resources

```java
for (ConfigurationFile configurationFile : configurationFiles) {
    ConfigurationUtil.saveConfigFile(tubingPlugin, configurationFile.getPath());
    // ...
}
```

For each configuration file:
- Checks if the file exists in the plugin's data folder
- If not, copies it from the plugin's JAR resources
- Creates parent directories if needed

### 2. Load Initial Configurations

```java
FileConfiguration currentConfig = ConfigurationUtil.loadConfiguration(tubingPlugin, configurationFile.getPath());
configurationFile.setFileConfiguration(currentConfig);
```

Loads the YAML file from disk into a `FileConfiguration` object.

### 3. Run Configuration Migrations

```java
AutoUpdater.runMigrations(tubingPlugin, configurationFiles, configurationMigrators);
```

Executes any custom migration logic (see [Configuration Migrations](#configuration-migrations) below).

### 4. Auto-Update Configurations

```java
FileConfiguration updatedConfig = AutoUpdater.updateConfig(tubingPlugin, configurationFile);
```

The auto-updater:
- Loads the default configuration from your plugin's JAR
- Compares it with the existing file on disk
- Adds any missing properties from defaults
- Preserves all existing values
- Saves the updated file back to disk

**Example:** If your JAR has:
```yaml
plugin:
  debug: false
  language: en
  new-feature: true  # New in v2.0
```

And the user's existing file has:
```yaml
plugin:
  debug: true
  language: es
```

The auto-updater creates:
```yaml
plugin:
  debug: true        # User's value preserved
  language: es       # User's value preserved
  new-feature: true  # Added from defaults
```

### 5. Parse Property References

```java
String newConfigFile = parseConfigurationPropertiesFromFile(configurationFile.getPath());
FileConfiguration configuration = ConfigurationUtil.loadConfiguration(newConfigFile);
```

Resolves property references like `{{config:plugin.debug}}` (see [Property References](#property-references) below).

## Auto-Save and Defaults

### How Auto-Save Works

When a configuration file doesn't exist in the plugin's data folder, Tubing automatically saves it from resources:

```java
public static void saveConfigFile(TubingPlugin tubingPlugin, String configurationFile) {
    File dataFolder = tubingPlugin.getDataFolder();
    String fullConfigResourcePath = (configurationFile).replace('\\', '/');

    InputStream in = getResource(fullConfigResourcePath);
    if (in == null) {
        tubingPlugin.getLogger().log(Level.SEVERE, "Could not find configuration file " + fullConfigResourcePath);
        return;
    }

    File outFile = new File(dataFolder, fullConfigResourcePath);

    // Create parent directories
    int lastIndex = fullConfigResourcePath.lastIndexOf('/');
    File outDir = new File(dataFolder, fullConfigResourcePath.substring(0, Math.max(lastIndex, 0)));
    if (!outDir.exists()) {
        outDir.mkdirs();
    }

    // Copy file from JAR to disk if it doesn't exist
    if (!outFile.exists()) {
        // ... copy bytes from InputStream to FileOutputStream
    }
}
```

**Key Points:**
- Files must exist in `src/main/resources/` in your plugin JAR
- Files are only saved if they don't already exist
- Parent directories are created automatically
- Files in subdirectories are supported (e.g., `config/homes.yml`)

### Default Configuration Structure

Create your default configurations in `src/main/resources/`:

```
src/main/resources/
├── config.yml
├── homes.yml
├── warps.yml
└── messages.yml
```

Each file should contain sensible defaults:

```yaml
# homes.yml
homes:
  max-homes: 3
  cooldown-seconds: 5
  allow-cross-world: false
  teleport-delay: 3
```

When the plugin first runs, these files are copied to the plugin's data folder.

## Auto-Update Mechanism

The auto-update mechanism ensures configuration files stay current when you add new properties.

### How Auto-Update Works

The `AutoUpdater.updateConfig()` method:

1. **Validates** the existing config file on disk
2. **Loads defaults** from the JAR resources
3. **Compares** defaults with the existing configuration
4. **Adds missing properties** while preserving existing values
5. **Saves** the updated configuration back to disk

### Implementation Details

```java
public static FileConfiguration updateConfig(TubingPlugin tubingPlugin, ConfigurationFile configurationFile) {
    if (configurationFile.isIgnoreUpdater()) {
        return configurationFile.getFileConfiguration();
    }

    FileConfiguration config = configurationFile.getFileConfiguration();
    FileConfiguration newConfig = new YamlConfiguration();

    Map<String, Object> defaultConfigMap = loadConfig(configurationFile.getPath());

    // Add all properties from defaults
    defaultConfigMap.forEach((k, v) -> {
        if (!config.contains(k) && !(v instanceof ConfigurationSection)) {
            newConfig.set(k, v);  // Add new property
        } else {
            newConfig.set(k, config.get(k));  // Preserve existing value
        }
    });

    // Keep properties not in defaults (user additions)
    config.getKeys(true).forEach((k) -> {
        Object value = config.get(k);
        if (!newConfig.contains(k) && !(value instanceof ConfigurationSection)) {
            newConfig.set(k, value);
        }
    });

    // Save updated config
    File file = new File(tubingPlugin.getDataFolder() + File.separator + configurationFile.getPath());
    newConfig.save(file);

    return newConfig;
}
```

### Disabling Auto-Update

For files that users heavily customize (like messages), disable auto-update:

```java
new ConfigurationFile("messages.yml", "messages", true)
//                                                  ^^^^ ignoreUpdater = true
```

This prevents the auto-updater from modifying the file, but it will still be loaded normally.

## Configuration Migrations

Configuration migrations allow you to programmatically update configurations when your plugin's structure changes.

### Creating a Migrator

Implement the `ConfigMigrator` interface:

```java
package com.example.myplugin.config.migrations;

import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;

import java.util.List;

public class Version2Migration implements ConfigMigrator {

    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Migration logic
        if (config.contains("old-property")) {
            // Move old-property to new-property
            Object value = config.get("old-property");
            config.set("new-section.new-property", value);
            config.set("old-property", null);
        }

        // Add version tracking
        if (!config.contains("config-version")) {
            config.set("config-version", 2);
        }
    }
}
```

### Registering Migrators

Return your migrators from `TubingConfigurationProvider`:

```java
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Arrays.asList(
            new Version2Migration(),
            new Version3Migration(),
            new MessagesRestructureMigration()
        );
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml"),
            new ConfigurationFile("homes.yml")
        );
    }
}
```

### Migration Execution

Migrations run **before** the auto-updater:

```
1. Load configuration files from disk
2. Run all migrations in order
3. Save migrated configurations
4. Run auto-updater to add new properties
5. Final save and property reference parsing
```

### Migration Helper Methods

The `ConfigMigrator` interface provides a helper method:

```java
default FileConfiguration getConfig(List<ConfigurationFile> configs, String identifier) {
    return configs.stream()
        .filter(c -> c.getIdentifier().equalsIgnoreCase(identifier))
        .findFirst()
        .map(ConfigurationFile::getFileConfiguration)
        .orElse(null);
}
```

Use it to easily access specific configuration files:

```java
@Override
public void migrate(List<ConfigurationFile> configs) {
    FileConfiguration mainConfig = getConfig(configs, "config");
    FileConfiguration homesConfig = getConfig(configs, "homes");

    if (mainConfig != null && homesConfig != null) {
        // Migrate data between configs
        if (mainConfig.contains("homes.max-homes")) {
            homesConfig.set("max-homes", mainConfig.get("homes.max-homes"));
            mainConfig.set("homes", null);
        }
    }
}
```

### Real-World Migration Examples

**Example 1: Rename a property**
```java
public class RenamePropertyMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        if (config.contains("old-name") && !config.contains("new-name")) {
            config.set("new-name", config.get("old-name"));
            config.set("old-name", null);
        }
    }
}
```

**Example 2: Restructure nested properties**
```java
public class RestructureMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Flatten nested structure
        if (config.contains("features.homes.enabled")) {
            config.set("homes.enabled", config.get("features.homes.enabled"));
            config.set("homes.max", config.get("features.homes.max"));
            config.set("features.homes", null);
        }
    }
}
```

**Example 3: Move properties between files**
```java
public class SplitConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration mainConfig = getConfig(configs, "config");
        FileConfiguration messagesConfig = getConfig(configs, "messages");

        if (mainConfig == null || messagesConfig == null) return;

        // Move all message-related properties to messages.yml
        if (mainConfig.contains("messages")) {
            ConfigurationSection section = mainConfig.getConfigurationSection("messages");
            if (section != null) {
                section.getKeys(true).forEach(key -> {
                    messagesConfig.set(key, section.get(key));
                });
                mainConfig.set("messages", null);
            }
        }
    }
}
```

## Property References

Configuration files can reference values from other configuration files using the `{{identifier:path}}` syntax.

### Syntax

```yaml
# config.yml
plugin:
  prefix: "&8[&6MyPlugin&8]"

# messages.yml
welcome: "{{config:plugin.prefix}} &7Welcome to the server!"
goodbye: "{{config:plugin.prefix}} &7See you later!"
```

### How It Works

After all configurations are loaded and updated, `ConfigurationLoader` parses each file:

```java
private String replaceConfigProperties(String message) {
    String newMessage = message;
    String regexString = Pattern.quote("{{") + "(.*?)" + Pattern.quote("}}");
    Pattern pattern = Pattern.compile(regexString);
    Matcher matcher = pattern.matcher(message);

    while (matcher.find()) {
        String matched = matcher.group(1);
        Optional<String> configValue = getConfigStringValue(matched);
        if (configValue.isPresent()) {
            newMessage = newMessage.replace("{{" + matched + "}}", configValue.get());
        }
    }

    return newMessage;
}
```

The pattern `{{identifier:path}}` is replaced with the actual value from that configuration file.

### Reference Format

```
{{identifier:path.to.property}}
```

- **identifier**: The configuration file identifier (usually filename without .yml)
- **path**: The property path using dot notation

### Examples

**Cross-file references:**
```yaml
# config.yml
server:
  name: "Awesome Server"
  max-players: 100

# messages.yml
server-info: "Welcome to {{config:server.name}}"
capacity: "Players online: %online% / {{config:server.max-players}}"
```

**Same-file references:**
```yaml
# config.yml
branding:
  name: "MyPlugin"
  version: "1.0"

messages:
  header: "&e{{config:branding.name}} v{{config:branding.version}}"
```

### Limitations

- Property references are resolved as strings
- Circular references are not detected (don't create them!)
- References are evaluated once during configuration loading
- Type conversion happens when properties are injected via `@ConfigProperty`

## Accessing FileConfiguration Objects

### Injecting ConfigurationLoader

Inject `ConfigurationLoader` into your beans to access configurations:

```java
@IocBean
public class MyService {

    private final ConfigurationLoader configLoader;

    public MyService(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void doSomething() {
        Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();
        FileConfiguration mainConfig = configs.get("config");

        if (mainConfig != null) {
            boolean debug = mainConfig.getBoolean("plugin.debug", false);
            // ...
        }
    }
}
```

### Getting Specific Configurations

```java
// Get all configurations as a map
Map<String, FileConfiguration> allConfigs = configLoader.getConfigurationFiles();

// Get specific configuration by identifier
FileConfiguration config = allConfigs.get("config");
FileConfiguration homes = allConfigs.get("homes");
FileConfiguration messages = allConfigs.get("messages");
```

The identifier is:
- The filename without `.yml` by default: `"config.yml"` → `"config"`
- Custom identifier if provided: `new ConfigurationFile("x.yml", "custom")` → `"custom"`

### Using FileConfiguration

`FileConfiguration` is Tubing's YAML configuration API:

```java
FileConfiguration config = allConfigs.get("config");

// Primitive types
boolean debug = config.getBoolean("plugin.debug");
int maxHomes = config.getInt("homes.max-homes");
double multiplier = config.getDouble("rewards.multiplier");
String prefix = config.getString("messages.prefix");

// With defaults
int cooldown = config.getInt("teleport.cooldown", 5);

// Lists
List<String> worlds = config.getStringList("allowed-worlds");
List<Integer> amounts = config.getIntegerList("reward-amounts");

// Check if property exists
if (config.contains("optional-feature.enabled")) {
    // ...
}

// Get configuration section
ConfigurationSection section = config.getConfigurationSection("database");
if (section != null) {
    String host = section.getString("host");
    int port = section.getInt("port");
}

// Get all keys
Set<String> keys = config.getKeys(false);  // Top-level keys
Set<String> allKeys = config.getKeys(true);  // All keys recursively
```

### Using ConfigurationLoader Helper Methods

For simple value lookups, use the helper methods:

```java
// Get any type with automatic casting
Optional<Integer> maxHomes = configLoader.getConfigValue("config:homes.max-homes");
Optional<Boolean> debug = configLoader.getConfigValue("config:plugin.debug");

// Get as string
Optional<String> prefix = configLoader.getConfigStringValue("messages:prefix");
```

The identifier uses the format `"identifier:path"`:
```java
configLoader.getConfigValue("config:plugin.debug");
//                            ^^^^^^ ^^^^^^^^^^^
//                            file   property path
```

## Best Practices

### 1. Organize by Feature

Split large configurations by feature or domain:

```
config/
├── config.yml          # Core plugin settings
├── database.yml        # Database configuration
├── homes.yml           # Homes feature settings
├── warps.yml           # Warps feature settings
├── permissions.yml     # Permission nodes
└── messages.yml        # All user messages
```

### 2. Use Descriptive Identifiers

For subdirectory configurations, use custom identifiers:

```java
new ConfigurationFile("features/homes.yml", "homes")
new ConfigurationFile("features/warps.yml", "warps")
```

This makes property references cleaner:
```yaml
message: "{{homes:max-homes}}"  # Instead of "{{features-homes:max-homes}}"
```

### 3. Document Your Configuration

Add comments to your default configuration files:

```yaml
# homes.yml
homes:
  # Maximum number of homes a player can set
  max-homes: 3

  # Cooldown between home teleports (in seconds)
  cooldown-seconds: 5

  # Allow teleporting to homes in different worlds
  allow-cross-world: false

  # Delay before teleporting (in seconds, 0 for instant)
  teleport-delay: 3
```

### 4. Provide Sensible Defaults

Always provide working defaults in your JAR resources:

```yaml
# Good defaults that work out of the box
database:
  type: sqlite
  sqlite:
    file: data.db
  mysql:
    host: localhost
    port: 3306
    database: minecraft
```

### 5. Use Migrations for Breaking Changes

When restructuring configurations, write migrations:

```java
@Override
public void migrate(List<ConfigurationFile> configs) {
    FileConfiguration config = getConfig(configs, "config");
    if (config == null) return;

    // Track migration version
    int version = config.getInt("config-version", 1);

    if (version < 2) {
        // Perform v1 -> v2 migration
        migrateToV2(config);
        config.set("config-version", 2);
    }

    if (version < 3) {
        // Perform v2 -> v3 migration
        migrateToV3(config);
        config.set("config-version", 3);
    }
}
```

### 6. Ignore Auto-Update for User-Customized Files

Files that users extensively customize should ignore auto-update:

```java
new ConfigurationFile("messages.yml", "messages", true)  // Ignore updater
```

Alternatively, handle messages differently:
- Provide a `messages_en.yml` with all defaults
- Let users create a custom `messages.yml` that overrides only what they want

### 7. Validate Configuration on Load

Add validation for critical properties:

```java
@IocBean
public class ConfigValidator {

    private final ConfigurationLoader configLoader;

    public ConfigValidator(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    @PostConstruct
    public void validateConfig() {
        Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();
        FileConfiguration config = configs.get("config");

        if (config == null) {
            throw new IllegalStateException("Configuration file not found!");
        }

        int maxHomes = config.getInt("homes.max-homes", 3);
        if (maxHomes < 1 || maxHomes > 100) {
            throw new IllegalStateException("homes.max-homes must be between 1 and 100");
        }
    }
}
```

### 8. Don't Abuse Property References

Property references are useful but can make configs harder to read:

```yaml
# Good - minimal references for truly shared values
prefix: "&8[&6MyPlugin&8]"
error-prefix: "{{config:prefix}} &cError:"

# Bad - over-using references
message1: "{{config:common.text.welcome}}"
message2: "{{config:common.text.goodbye}}"
message3: "{{config:colors.primary}}Welcome{{config:colors.secondary}}!"
```

### 9. Use @ConfigProperty for Bean Properties

Instead of accessing `ConfigurationLoader` everywhere, use `@ConfigProperty`:

```java
// Good - declarative and type-safe
@IocBean
public class HomeService {

    @ConfigProperty("homes.max-homes")
    private int maxHomes;

    @ConfigProperty("homes.cooldown-seconds")
    private int cooldown;
}

// Bad - manual config access everywhere
@IocBean
public class HomeService {

    private final ConfigurationLoader configLoader;

    public HomeService(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void doSomething() {
        int maxHomes = configLoader.getConfigurationFiles()
            .get("config")
            .getInt("homes.max-homes");
        // ...
    }
}
```

### 10. Keep Configuration File Names Simple

Use simple, lowercase names without special characters:

```java
// Good
new ConfigurationFile("config.yml")
new ConfigurationFile("homes.yml")
new ConfigurationFile("messages.yml")

// Avoid
new ConfigurationFile("MyPlugin-Config.yml")
new ConfigurationFile("homes_configuration.yml")
new ConfigurationFile("config-v2.yml")
```

## Complete Example

Here's a complete example showing all configuration file features:

### Configuration Provider

```java
package com.example.myplugin.config;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.TubingPlugin;
import be.garagepoort.mcioc.common.TubingConfigurationProvider;
import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.load.InjectTubingPlugin;

import java.util.Arrays;
import java.util.List;

@IocBean
public class PluginConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public PluginConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml"),
            new ConfigurationFile("homes.yml"),
            new ConfigurationFile("database.yml"),
            new ConfigurationFile("messages.yml", "messages", true)  // Ignore updater
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Arrays.asList(
            new Version2ConfigMigration(),
            new DatabaseMigration()
        );
    }
}
```

### Migration Example

```java
package com.example.myplugin.config;

import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;

import java.util.List;

public class Version2ConfigMigration implements ConfigMigrator {

    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        FileConfiguration homes = getConfig(configs, "homes");

        if (config == null || homes == null) return;

        // Track version
        int version = config.getInt("config-version", 1);
        if (version >= 2) return;  // Already migrated

        // Move homes settings from config.yml to homes.yml
        if (config.contains("homes")) {
            homes.set("max-homes", config.getInt("homes.max-homes", 3));
            homes.set("cooldown", config.getInt("homes.cooldown", 5));
            config.set("homes", null);
        }

        config.set("config-version", 2);
    }
}
```

### Default Configuration Files

**config.yml:**
```yaml
config-version: 2

plugin:
  debug: false
  language: en
  prefix: "&8[&6MyPlugin&8]"

features:
  homes-enabled: true
  database-enabled: true
```

**homes.yml:**
```yaml
max-homes: 3
cooldown: 5
allow-cross-world: false
teleport-delay: 3
```

**database.yml:**
```yaml
type: sqlite
sqlite:
  file: data.db
mysql:
  host: localhost
  port: 3306
  database: mydb
  username: root
  password: pass
```

**messages.yml:**
```yaml
prefix: "{{config:plugin.prefix}}"

success:
  home-set: "{{messages:prefix}} &aHome set!"
  home-deleted: "{{messages:prefix}} &aHome deleted!"

errors:
  max-homes: "{{messages:prefix}} &cYou have reached the maximum of {{homes:max-homes}} homes!"
  not-found: "{{messages:prefix}} &cHome not found!"
```

### Using in a Service

```java
package com.example.myplugin.service;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import com.example.myplugin.model.Home;
import org.bukkit.entity.Player;

@IocBean
public class HomeService {

    private final HomeRepository repository;

    @ConfigProperty("homes:max-homes")
    private int maxHomes;

    @ConfigProperty("homes:cooldown")
    private int cooldown;

    public HomeService(HomeRepository repository) {
        this.repository = repository;
    }

    public void createHome(Player player, String name) {
        int currentHomes = repository.countHomes(player);

        if (currentHomes >= maxHomes) {
            player.sendMessage("You can only have " + maxHomes + " homes!");
            return;
        }

        repository.save(new Home(player.getUniqueId(), name, player.getLocation()));
        player.sendMessage("Home created!");
    }
}
```

## Troubleshooting

### Configuration File Not Loading

**Problem:** Configuration file isn't being loaded or auto-saved.

**Solutions:**
1. Ensure the file exists in `src/main/resources/`
2. Check that it's included in your Maven/Gradle build
3. Verify the path in `ConfigurationFile` matches the resource path
4. Check logs for errors during configuration loading

### Auto-Update Not Working

**Problem:** New properties aren't being added to existing configs.

**Solutions:**
1. Ensure `ignoreUpdater` is `false` (or not set)
2. Verify the default file in your JAR has the new properties
3. Check for YAML syntax errors in both default and existing files
4. Look for errors in the console during auto-update

### Property References Not Working

**Problem:** `{{config:property}}` appears literally in config.

**Solutions:**
1. Ensure the referenced property exists in the specified file
2. Check the identifier matches the configuration file's identifier
3. Verify property path syntax: `{{identifier:path.to.property}}`
4. Property references are case-sensitive

### Migration Not Running

**Problem:** Configuration migration doesn't execute.

**Solutions:**
1. Ensure migrator is returned from `getConfigurationMigrators()`
2. Check that `TubingConfigurationProvider` bean is registered
3. Verify migration logic doesn't return early
4. Add logging to your migration to confirm it runs

## Next Steps

Now that you understand configuration files:

- Learn about [Configuration Injection](Configuration-Injection.md) to inject config values into beans
- Explore [Configuration Objects](Configuration-Objects.md) for mapping complex YAML structures
- Read [Configuration Transformers](Configuration-Transformers.md) for custom type conversion
- Check [Post-Initialization](Post-Initialization.md) for configuration validation hooks

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration setup
- [Project Structure](../getting-started/Project-Structure.md) - Organizing configuration files
- [Custom Configuration Provider](../advanced/Custom-Configuration-Provider.md) - Advanced provider patterns
- [Bean Lifecycle](Bean-Lifecycle.md) - When configurations are loaded
