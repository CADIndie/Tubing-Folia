# Custom Configuration Provider

Custom Configuration Providers allow you to take full control of how Tubing loads and manages your plugin's configuration files. By implementing the `TubingConfigurationProvider` interface, you can define multiple configuration files, set up configuration migrations, and customize the entire configuration lifecycle.

## Overview

The `TubingConfigurationProvider` interface is the foundation of Tubing's configuration system. While Tubing provides a default implementation that loads a single `config.yml`, creating a custom provider enables:

- **Multiple configuration files** organized by feature or concern
- **Configuration migrations** for seamless version upgrades
- **Custom file structures** with subdirectories and custom identifiers
- **Selective auto-update control** for user-modified files
- **Advanced configuration management** patterns

## The TubingConfigurationProvider Interface

The `TubingConfigurationProvider` interface is simple but powerful:

```java
package be.garagepoort.mcioc.common;

import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import java.util.List;

public interface TubingConfigurationProvider {
    List<ConfigMigrator> getConfigurationMigrators();
    List<ConfigurationFile> getConfigurationFiles();
}
```

### Interface Methods

#### getConfigurationFiles()

Returns a list of `ConfigurationFile` objects that define which YAML files to load.

**Return type:** `List<ConfigurationFile>`

**Purpose:**
- Specifies all configuration files your plugin uses
- Defines file paths, identifiers, and auto-update behavior
- Called during container initialization before beans are created

**Example:**
```java
@Override
public List<ConfigurationFile> getConfigurationFiles() {
    return Arrays.asList(
        new ConfigurationFile("config.yml"),
        new ConfigurationFile("database.yml"),
        new ConfigurationFile("messages.yml")
    );
}
```

#### getConfigurationMigrators()

Returns a list of `ConfigMigrator` implementations that handle configuration migrations.

**Return type:** `List<ConfigMigrator>`

**Purpose:**
- Provides migration logic for configuration structure changes
- Executes before auto-update runs
- Allows programmatic configuration transformations

**Example:**
```java
@Override
public List<ConfigMigrator> getConfigurationMigrators() {
    return Arrays.asList(
        new Version2Migration(),
        new Version3Migration()
    );
}
```

## Default Implementation

Tubing provides a default implementation that loads a single `config.yml`:

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultTubingConfigurationProvider implements TubingConfigurationProvider {

    private TubingPlugin tubingPlugin;

    public DefaultTubingConfigurationProvider(@InjectTubingPlugin TubingPlugin tubingPlugin) {
        this.tubingPlugin = tubingPlugin;
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        InputStream defConfigStream = getResource(tubingPlugin, "config.yml");
        if (defConfigStream != null) {
            return Collections.singletonList(new ConfigurationFile("config.yml"));
        } else {
            return Collections.emptyList();
        }
    }
}
```

**Key Features:**
- Annotated with `@ConditionalOnMissingBean` - automatically disabled when you provide your own
- Checks if `config.yml` exists in plugin resources
- Returns empty list if no config file found
- No migrations by default

## Creating a Custom Provider

### Basic Custom Provider

Create a class that implements `TubingConfigurationProvider` and annotate it with `@IocBean`:

```java
package com.example.myplugin.config;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.TubingPlugin;
import be.garagepoort.mcioc.common.TubingConfigurationProvider;
import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.load.InjectTubingPlugin;

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
            new ConfigurationFile("warps.yml")
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

**Important Notes:**
- The `@IocBean` annotation registers your provider with the container
- When you create a custom provider, the `DefaultTubingConfigurationProvider` is automatically disabled
- The `@InjectTubingPlugin` annotation injects the plugin instance if needed
- Configuration files are loaded in the order you define them

### When to Create a Custom Provider

**Create a custom provider when:**
- You have multiple configuration files (homes, warps, database, messages, etc.)
- You need configuration migrations for version upgrades
- You want to organize configs in subdirectories
- You need to disable auto-update for certain files
- You want custom identifiers for property references

**Use the default provider when:**
- You only need a single `config.yml`
- You don't need configuration migrations
- Simple configuration is sufficient for your plugin

## The ConfigurationFile Class

The `ConfigurationFile` class represents a single YAML configuration file with metadata about how it should be loaded and managed.

### Class Structure

```java
package be.garagepoort.mcioc.configuration.files;

public class ConfigurationFile {
    private final String identifier;
    private final String path;
    private boolean ignoreUpdater = false;
    private FileConfiguration fileConfiguration;

    public ConfigurationFile(String path);
    public ConfigurationFile(String path, String identifier);
    public ConfigurationFile(String path, String identifier, boolean ignoreUpdater);

    public String getIdentifier();
    public String getPath();
    public boolean isIgnoreUpdater();
    public FileConfiguration getFileConfiguration();
    public void setFileConfiguration(FileConfiguration fileConfiguration);
}
```

### Constructor Options

#### Simple Constructor

```java
new ConfigurationFile("config.yml")
```

**Parameters:**
- `path`: File path relative to the plugin's data folder

**Behavior:**
- Identifier is derived from filename (e.g., `"config.yml"` → `"config"`)
- Auto-update is enabled by default
- File will be saved from resources if it doesn't exist

**Example:**
```java
new ConfigurationFile("config.yml")         // identifier: "config"
new ConfigurationFile("homes.yml")          // identifier: "homes"
new ConfigurationFile("data/stats.yml")     // identifier: "data-stats"
```

#### Constructor with Custom Identifier

```java
new ConfigurationFile("subdir/feature.yml", "feature")
```

**Parameters:**
- `path`: File path relative to the plugin's data folder
- `identifier`: Custom identifier for property references

**Use Cases:**
- Organizing files in subdirectories while keeping simple identifiers
- Creating multiple configs with meaningful reference names
- Avoiding identifier conflicts

**Example:**
```java
new ConfigurationFile("features/homes.yml", "homes")
new ConfigurationFile("features/warps.yml", "warps")
new ConfigurationFile("lang/en.yml", "messages")
```

#### Constructor with Ignore Updater

```java
new ConfigurationFile("messages.yml", "messages", true)
```

**Parameters:**
- `path`: File path relative to the plugin's data folder
- `identifier`: Custom identifier for property references
- `ignoreUpdater`: If `true`, skip auto-update for this file

**Use Cases:**
- Files that users heavily customize (messages, translations)
- Dynamic configuration that shouldn't be overwritten
- User-created content files

**Example:**
```java
// Skip auto-update for messages
new ConfigurationFile("messages.yml", "messages", true)

// Skip auto-update for custom user config
new ConfigurationFile("custom.yml", "custom", true)
```

### Identifier Derivation

When using the simple constructor, identifiers are automatically generated:

```java
private String getConfigId(String path) {
    return path.replace("/", "-")
        .replace(".yml", "");
}
```

**Examples:**
- `"config.yml"` → `"config"`
- `"homes.yml"` → `"homes"`
- `"features/homes.yml"` → `"features-homes"`
- `"data/players/stats.yml"` → `"data-players-stats"`

**Best Practice:** For files in subdirectories, use custom identifiers to keep references clean:

```java
// Without custom identifier
new ConfigurationFile("features/homes.yml")  // identifier: "features-homes"
// Reference in YAML: {{features-homes:max-homes}}

// With custom identifier
new ConfigurationFile("features/homes.yml", "homes")  // identifier: "homes"
// Reference in YAML: {{homes:max-homes}}
```

## Multiple Configuration Files Setup

### Organizing Configuration by Feature

Split your configuration into logical units based on features:

```java
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            // Core plugin settings
            new ConfigurationFile("config.yml"),

            // Feature-specific configs
            new ConfigurationFile("homes.yml"),
            new ConfigurationFile("warps.yml"),
            new ConfigurationFile("teleportation.yml"),

            // Infrastructure configs
            new ConfigurationFile("database.yml"),
            new ConfigurationFile("permissions.yml"),

            // User-facing content (no auto-update)
            new ConfigurationFile("messages.yml", "messages", true)
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

**Resource Structure:**
```
src/main/resources/
├── config.yml
├── homes.yml
├── warps.yml
├── teleportation.yml
├── database.yml
├── permissions.yml
└── messages.yml
```

**Runtime File Structure:**
```
plugins/MyPlugin/
├── config.yml
├── homes.yml
├── warps.yml
├── teleportation.yml
├── database.yml
├── permissions.yml
└── messages.yml
```

### Using Subdirectories

Organize files in subdirectories for better structure:

```java
@IocBean
public class OrganizedConfigurationProvider implements TubingConfigurationProvider {

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            // Main config
            new ConfigurationFile("config.yml"),

            // Feature configs in subdirectory
            new ConfigurationFile("features/homes.yml", "homes"),
            new ConfigurationFile("features/warps.yml", "warps"),
            new ConfigurationFile("features/economy.yml", "economy"),

            // Language files
            new ConfigurationFile("lang/en.yml", "lang-en"),
            new ConfigurationFile("lang/es.yml", "lang-es"),
            new ConfigurationFile("lang/fr.yml", "lang-fr"),

            // Database configs
            new ConfigurationFile("database/mysql.yml", "db-mysql"),
            new ConfigurationFile("database/sqlite.yml", "db-sqlite")
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

**Resource Structure:**
```
src/main/resources/
├── config.yml
├── features/
│   ├── homes.yml
│   ├── warps.yml
│   └── economy.yml
├── lang/
│   ├── en.yml
│   ├── es.yml
│   └── fr.yml
└── database/
    ├── mysql.yml
    └── sqlite.yml
```

### Language-Based Configuration

Support multiple languages with separate config files:

```java
@IocBean
public class MultiLanguageConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public MultiLanguageConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        List<ConfigurationFile> files = new ArrayList<>();

        // Main config
        files.add(new ConfigurationFile("config.yml"));

        // Load language files dynamically
        String defaultLang = "en";  // Could load from config
        files.add(new ConfigurationFile("lang/" + defaultLang + ".yml", "messages", true));

        return files;
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }
}
```

### Conditional Configuration Loading

Load different configs based on conditions:

```java
@IocBean
public class ConditionalConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public ConditionalConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        List<ConfigurationFile> files = new ArrayList<>();

        // Always load main config
        files.add(new ConfigurationFile("config.yml"));

        // Load development config in dev environment
        if (isDevelopmentMode()) {
            files.add(new ConfigurationFile("dev-config.yml", "dev"));
        }

        // Load platform-specific configs
        if (isPaper()) {
            files.add(new ConfigurationFile("paper-specific.yml", "paper"));
        }

        return files;
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Collections.emptyList();
    }

    private boolean isDevelopmentMode() {
        return plugin.getDataFolder().getPath().contains("dev");
    }

    private boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

## Configuration Migrations

Configuration migrations allow you to programmatically update configuration files when your plugin's structure changes between versions.

### The ConfigMigrator Interface

```java
package be.garagepoort.mcioc.configuration.files;

import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;
import java.util.List;

public interface ConfigMigrator {
    void migrate(List<ConfigurationFile> config);

    default FileConfiguration getConfig(List<ConfigurationFile> configs, String identifier) {
        return configs.stream()
            .filter(c -> c.getIdentifier().equalsIgnoreCase(identifier))
            .findFirst()
            .map(ConfigurationFile::getFileConfiguration)
            .orElse(null);
    }
}
```

### Creating a Migration

Implement the `ConfigMigrator` interface:

```java
package com.example.myplugin.migrations;

import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;

import java.util.List;

public class Version2Migration implements ConfigMigrator {

    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Check if migration already ran
        int version = config.getInt("config-version", 1);
        if (version >= 2) return;

        // Rename property
        if (config.contains("old-property-name")) {
            Object value = config.get("old-property-name");
            config.set("new-property-name", value);
            config.set("old-property-name", null);
        }

        // Restructure nested properties
        if (config.contains("flat-setting")) {
            Object value = config.get("flat-setting");
            config.set("settings.nested-setting", value);
            config.set("flat-setting", null);
        }

        // Update version
        config.set("config-version", 2);
    }
}
```

### Registering Migrations

Add migrations to your configuration provider:

```java
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            new ConfigurationFile("config.yml"),
            new ConfigurationFile("homes.yml")
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Arrays.asList(
            new Version2Migration(),
            new Version3Migration(),
            new HomesRestructureMigration()
        );
    }
}
```

**Execution Order:**
1. Migrations execute in the order you define them
2. All migrations run before the auto-updater
3. Migrations can modify any loaded configuration file
4. Changes are saved to disk after all migrations complete

### Migration Patterns

#### Pattern: Version Tracking

Track the config version to avoid re-running migrations:

```java
public class Version2Migration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        int version = config.getInt("config-version", 1);
        if (version >= 2) return;  // Already migrated

        // Perform migration...

        config.set("config-version", 2);
    }
}
```

**config.yml:**
```yaml
config-version: 2

plugin:
  name: "MyPlugin"
  debug: false
```

#### Pattern: Rename Properties

Rename properties while preserving user values:

```java
public class RenamePropertyMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Rename single property
        if (config.contains("max-players") && !config.contains("player-limit")) {
            config.set("player-limit", config.get("max-players"));
            config.set("max-players", null);
        }

        // Rename nested property
        if (config.contains("database.host-name")) {
            config.set("database.host", config.get("database.host-name"));
            config.set("database.host-name", null);
        }
    }
}
```

#### Pattern: Restructure Configuration

Move properties between sections:

```java
public class RestructureMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Flatten structure
        if (config.contains("features.homes.enabled")) {
            config.set("homes.enabled", config.get("features.homes.enabled"));
            config.set("homes.max", config.get("features.homes.max"));
            config.set("features.homes", null);
        }

        // Nest properties
        if (config.contains("database-host")) {
            config.set("database.host", config.get("database-host"));
            config.set("database.port", config.get("database-port"));
            config.set("database.name", config.get("database-name"));
            config.set("database-host", null);
            config.set("database-port", null);
            config.set("database-name", null);
        }
    }
}
```

#### Pattern: Split Configuration Files

Move properties from one file to another:

```java
public class SplitConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration mainConfig = getConfig(configs, "config");
        FileConfiguration homesConfig = getConfig(configs, "homes");

        if (mainConfig == null || homesConfig == null) return;

        // Move homes settings from config.yml to homes.yml
        if (mainConfig.contains("homes") && homesConfig.getKeys(false).isEmpty()) {
            ConfigurationSection homesSection = mainConfig.getConfigurationSection("homes");
            if (homesSection != null) {
                for (String key : homesSection.getKeys(false)) {
                    homesConfig.set(key, homesSection.get(key));
                }
                mainConfig.set("homes", null);
            }
        }
    }
}
```

#### Pattern: Merge Configuration Files

Combine multiple files into one:

```java
public class MergeConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration mainConfig = getConfig(configs, "config");
        FileConfiguration oldHomesConfig = getConfig(configs, "old-homes");

        if (mainConfig == null || oldHomesConfig == null) return;

        // Merge old-homes.yml into config.yml under "homes" section
        if (!mainConfig.contains("homes") && !oldHomesConfig.getKeys(false).isEmpty()) {
            for (String key : oldHomesConfig.getKeys(true)) {
                Object value = oldHomesConfig.get(key);
                if (!(value instanceof ConfigurationSection)) {
                    mainConfig.set("homes." + key, value);
                }
            }
        }
    }
}
```

#### Pattern: Data Type Conversion

Convert values to different types:

```java
public class TypeConversionMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Convert boolean to string
        if (config.contains("debug") && config.get("debug") instanceof Boolean) {
            boolean debug = config.getBoolean("debug");
            config.set("log-level", debug ? "DEBUG" : "INFO");
            config.set("debug", null);
        }

        // Convert single value to list
        if (config.contains("allowed-world") && config.get("allowed-world") instanceof String) {
            String world = config.getString("allowed-world");
            config.set("allowed-worlds", Collections.singletonList(world));
            config.set("allowed-world", null);
        }

        // Convert list to map
        if (config.contains("warps") && config.get("warps") instanceof List) {
            List<String> warps = config.getStringList("warps");
            for (String warp : warps) {
                config.set("warps-map." + warp + ".enabled", true);
            }
            config.set("warps", null);
        }
    }
}
```

#### Pattern: Add Default Values

Add new properties with defaults only if they don't exist:

```java
public class AddDefaultsMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        // Add new feature flags
        if (!config.contains("features.new-feature")) {
            config.set("features.new-feature.enabled", true);
            config.set("features.new-feature.setting", "default");
        }

        // Add new database options
        if (!config.contains("database.pool-size")) {
            config.set("database.pool-size", 10);
        }
    }
}
```

### Migration Execution Lifecycle

Understanding when migrations run is crucial:

```
1. Load configuration files from disk
   ↓
2. Run ALL migrations in order
   ↓
3. Save migrated configurations to disk
   ↓
4. Run auto-updater to add new properties
   ↓
5. Save updated configurations to disk
   ↓
6. Parse property references
   ↓
7. Configuration ready for use
```

**Key Points:**
- Migrations run before auto-update
- All migrations execute even if one modifies a file
- Migrations can access all loaded configuration files
- Changes are persisted after migrations complete

## Integration with ConfigurationLoader

Your custom provider integrates seamlessly with the `ConfigurationLoader` service.

### How ConfigurationLoader Uses Your Provider

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    public ConfigurationLoader(
        @InjectTubingPlugin TubingPlugin tubingPlugin,
        TubingConfigurationProvider tubingConfigurationProvider
    ) {
        this.configurationFiles = tubingConfigurationProvider.getConfigurationFiles();
        List<ConfigMigrator> migrators = tubingConfigurationProvider.getConfigurationMigrators();

        // Load, migrate, and update configurations
        loadConfig(tubingPlugin, migrators);
    }
}
```

**Integration Flow:**
1. Container creates `ConfigurationLoader` with priority
2. Your provider is injected into `ConfigurationLoader`
3. `getConfigurationFiles()` is called to get file list
4. `getConfigurationMigrators()` is called to get migrations
5. Files are loaded, migrated, and made available to beans

### Accessing Configurations

After loading, configurations are available through `ConfigurationLoader`:

```java
@IocBean
public class MyService {
    private final ConfigurationLoader configLoader;

    public MyService(ConfigurationLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void useConfig() {
        Map<String, FileConfiguration> configs = configLoader.getConfigurationFiles();

        FileConfiguration mainConfig = configs.get("config");
        FileConfiguration homesConfig = configs.get("homes");
        FileConfiguration messagesConfig = configs.get("messages");

        // Use configurations...
    }
}
```

## Best Practices

### 1. Use Clear, Consistent File Organization

```java
// Good - organized by domain
@Override
public List<ConfigurationFile> getConfigurationFiles() {
    return Arrays.asList(
        new ConfigurationFile("config.yml"),              // Core settings
        new ConfigurationFile("features/homes.yml", "homes"),
        new ConfigurationFile("features/warps.yml", "warps"),
        new ConfigurationFile("database.yml"),
        new ConfigurationFile("messages.yml", "messages", true)
    );
}

// Bad - inconsistent organization
@Override
public List<ConfigurationFile> getConfigurationFiles() {
    return Arrays.asList(
        new ConfigurationFile("config.yml"),
        new ConfigurationFile("someStuff.yml"),
        new ConfigurationFile("data/things/whatever.yml", "x"),
        new ConfigurationFile("misc.yml", "misc-stuff")
    );
}
```

### 2. Disable Auto-Update for User-Modified Files

```java
// Good - messages won't be overwritten
new ConfigurationFile("messages.yml", "messages", true)

// Good - custom user config
new ConfigurationFile("custom-rules.yml", "rules", true)

// Bad - messages will lose user customization
new ConfigurationFile("messages.yml")
```

### 3. Use Custom Identifiers for Subdirectories

```java
// Good - clean property references
new ConfigurationFile("features/homes.yml", "homes")
// Reference: {{homes:max-homes}}

// Bad - ugly property references
new ConfigurationFile("features/homes.yml")
// Reference: {{features-homes:max-homes}}
```

### 4. Track Configuration Versions

```java
// Good - tracks version for migrations
@Override
public void migrate(List<ConfigurationFile> configs) {
    FileConfiguration config = getConfig(configs, "config");
    if (config == null) return;

    int version = config.getInt("config-version", 1);
    if (version >= 2) return;

    // Migration logic...

    config.set("config-version", 2);
}
```

**config.yml:**
```yaml
config-version: 2

plugin:
  name: "MyPlugin"
```

### 5. Order Migrations Chronologically

```java
// Good - clear version progression
@Override
public List<ConfigMigrator> getConfigurationMigrators() {
    return Arrays.asList(
        new Version2Migration(),   // v1 -> v2
        new Version3Migration(),   // v2 -> v3
        new Version4Migration()    // v3 -> v4
    );
}

// Bad - confusing order
@Override
public List<ConfigMigrator> getConfigurationMigrators() {
    return Arrays.asList(
        new RenameSomething(),
        new AddNewStuff(),
        new FixOldStuff()
    );
}
```

### 6. Handle Missing Configs Gracefully

```java
// Good - null check before migration
@Override
public void migrate(List<ConfigurationFile> configs) {
    FileConfiguration config = getConfig(configs, "config");
    if (config == null) return;  // File doesn't exist, skip migration

    // Migration logic...
}

// Bad - will throw NullPointerException
@Override
public void migrate(List<ConfigurationFile> configs) {
    FileConfiguration config = getConfig(configs, "config");
    config.set("value", 123);  // NPE if config is null!
}
```

### 7. Preserve User Values in Migrations

```java
// Good - only migrate if new property doesn't exist
if (config.contains("old-name") && !config.contains("new-name")) {
    config.set("new-name", config.get("old-name"));
    config.set("old-name", null);
}

// Bad - overwrites user's new value
if (config.contains("old-name")) {
    config.set("new-name", config.get("old-name"));  // Overwrites!
    config.set("old-name", null);
}
```

### 8. Document Your Configuration Structure

```java
/**
 * Configuration provider for MyPlugin.
 *
 * Configuration files:
 * - config.yml: Main plugin settings
 * - homes.yml: Home system configuration
 * - warps.yml: Warp system configuration
 * - database.yml: Database connection settings
 * - messages.yml: User-facing messages (no auto-update)
 */
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {
    // Implementation...
}
```

### 9. Keep Provider Logic Simple

```java
// Good - simple and clear
@Override
public List<ConfigurationFile> getConfigurationFiles() {
    return Arrays.asList(
        new ConfigurationFile("config.yml"),
        new ConfigurationFile("homes.yml"),
        new ConfigurationFile("warps.yml")
    );
}

// Bad - complex logic in provider
@Override
public List<ConfigurationFile> getConfigurationFiles() {
    List<ConfigurationFile> files = new ArrayList<>();

    if (checkSomething()) {
        for (String feature : getFeatures()) {
            if (isEnabled(feature)) {
                files.add(loadFeatureConfig(feature));
            }
        }
    }

    // Too complex!
    return files;
}
```

### 10. Separate Migration Logic by Version

```java
// Good - one migration class per version
public class Version2Migration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        // Only v1 -> v2 changes
    }
}

public class Version3Migration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        // Only v2 -> v3 changes
    }
}

// Bad - all migrations in one class
public class AllMigrations implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        migrateV1ToV2(configs);
        migrateV2ToV3(configs);
        migrateV3ToV4(configs);
        // Hard to maintain!
    }
}
```

## Complete Example

Here's a comprehensive example demonstrating all concepts:

### Configuration Provider

```java
package com.example.myplugin.config;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.TubingPlugin;
import be.garagepoort.mcioc.common.TubingConfigurationProvider;
import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.load.InjectTubingPlugin;
import com.example.myplugin.config.migrations.*;

import java.util.Arrays;
import java.util.List;

/**
 * Custom configuration provider for MyPlugin.
 *
 * Manages multiple configuration files organized by feature:
 * - config.yml: Core plugin settings
 * - homes.yml: Home system configuration
 * - warps.yml: Warp system configuration
 * - teleport.yml: Teleportation settings
 * - database.yml: Database configuration
 * - permissions.yml: Permission node definitions
 * - messages.yml: User messages (no auto-update)
 */
@IocBean
public class MyConfigurationProvider implements TubingConfigurationProvider {

    private final TubingPlugin plugin;

    public MyConfigurationProvider(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return Arrays.asList(
            // Core configuration
            new ConfigurationFile("config.yml"),

            // Feature configurations
            new ConfigurationFile("features/homes.yml", "homes"),
            new ConfigurationFile("features/warps.yml", "warps"),
            new ConfigurationFile("features/teleport.yml", "teleport"),

            // Infrastructure
            new ConfigurationFile("database.yml"),
            new ConfigurationFile("permissions.yml"),

            // User content (no auto-update)
            new ConfigurationFile("messages.yml", "messages", true)
        );
    }

    @Override
    public List<ConfigMigrator> getConfigurationMigrators() {
        return Arrays.asList(
            new Version2ConfigMigration(),    // v1 -> v2: Restructure homes
            new Version3ConfigMigration(),    // v2 -> v3: Split configs
            new Version4ConfigMigration()     // v3 -> v4: Add new features
        );
    }
}
```

### Migration Examples

```java
package com.example.myplugin.config.migrations;

import be.garagepoort.mcioc.configuration.files.ConfigMigrator;
import be.garagepoort.mcioc.configuration.files.ConfigurationFile;
import be.garagepoort.mcioc.configuration.yaml.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Version 2 migration: Restructure homes configuration
 */
public class Version2ConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        int version = config.getInt("config-version", 1);
        if (version >= 2) return;

        // Rename old properties
        if (config.contains("max-homes")) {
            config.set("homes.max-homes", config.get("max-homes"));
            config.set("max-homes", null);
        }

        if (config.contains("home-cooldown")) {
            config.set("homes.cooldown-seconds", config.get("home-cooldown"));
            config.set("home-cooldown", null);
        }

        config.set("config-version", 2);
    }
}

/**
 * Version 3 migration: Split homes into separate file
 */
public class Version3ConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration mainConfig = getConfig(configs, "config");
        FileConfiguration homesConfig = getConfig(configs, "homes");

        if (mainConfig == null || homesConfig == null) return;

        int version = mainConfig.getInt("config-version", 1);
        if (version >= 3) return;

        // Move homes section to homes.yml
        if (mainConfig.contains("homes")) {
            ConfigurationSection homesSection = mainConfig.getConfigurationSection("homes");
            if (homesSection != null) {
                for (String key : homesSection.getKeys(false)) {
                    homesConfig.set(key, homesSection.get(key));
                }
                mainConfig.set("homes", null);
            }
        }

        mainConfig.set("config-version", 3);
    }
}

/**
 * Version 4 migration: Add new feature settings
 */
public class Version4ConfigMigration implements ConfigMigrator {
    @Override
    public void migrate(List<ConfigurationFile> configs) {
        FileConfiguration config = getConfig(configs, "config");
        if (config == null) return;

        int version = config.getInt("config-version", 1);
        if (version >= 4) return;

        // Add new feature flags
        if (!config.contains("features.economy")) {
            config.set("features.economy.enabled", false);
            config.set("features.economy.currency", "coins");
        }

        if (!config.contains("features.chat")) {
            config.set("features.chat.enabled", true);
            config.set("features.chat.format", "&7{player}: {message}");
        }

        config.set("config-version", 4);
    }
}
```

### Configuration Files

**config.yml:**
```yaml
config-version: 4

plugin:
  name: "MyPlugin"
  debug: false
  language: "en"

features:
  economy:
    enabled: false
    currency: "coins"
  chat:
    enabled: true
    format: "&7{player}: {message}"
```

**features/homes.yml:**
```yaml
max-homes: 5
cooldown-seconds: 60
allow-nether: false
allow-end: false
teleport-delay: 3
```

**features/warps.yml:**
```yaml
max-warps: 10
require-permission: true
allow-players: true
teleport-delay: 0
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
  password: ""
  pool-size: 10
```

**messages.yml:**
```yaml
prefix: "{{config:plugin.name}}"

homes:
  set: "{{messages:prefix}} &aHome set!"
  deleted: "{{messages:prefix}} &aHome deleted!"
  limit-reached: "{{messages:prefix}} &cYou have reached the maximum of {{homes:max-homes}} homes!"

warps:
  created: "{{messages:prefix}} &aWarp created!"
  deleted: "{{messages:prefix}} &aWarp deleted!"
  not-found: "{{messages:prefix}} &cWarp not found!"
```

## Troubleshooting

### Provider Not Being Used

**Problem:** Your custom provider isn't being loaded.

**Solutions:**
1. Ensure the class is annotated with `@IocBean`
2. Verify the class is in your plugin's package or subpackage
3. Check that the default provider isn't being forced somehow
4. Look for errors during container initialization

### Configuration Files Not Loading

**Problem:** Files defined in provider aren't loading.

**Solutions:**
1. Verify files exist in `src/main/resources/` at the specified paths
2. Check that paths are correct (relative to data folder)
3. Ensure files are included in your build (check JAR contents)
4. Look for errors in console during configuration loading

### Migrations Not Running

**Problem:** Migrations aren't executing.

**Solutions:**
1. Ensure migrations are returned from `getConfigurationMigrators()`
2. Add logging to verify migration is called
3. Check that configuration files are loaded before migration runs
4. Verify migration logic doesn't return early due to conditions

### Auto-Update Not Working as Expected

**Problem:** Some files update, others don't.

**Solutions:**
1. Check `ignoreUpdater` flag for files that don't update
2. Verify default files in JAR have the new properties
3. Check for YAML syntax errors in both default and existing files
4. Look for errors during auto-update phase

### Property References Not Resolving

**Problem:** `{{identifier:path}}` references appear literally.

**Solutions:**
1. Verify the identifier matches the configuration file's identifier
2. Check that the referenced property exists
3. Ensure property path syntax is correct
4. Confirm files are loaded in the right order

## Next Steps

Now that you understand custom configuration providers:

- Learn about [Configuration Files](../core/Configuration-Files.md) for file management details
- Explore [Configuration Injection](../core/Configuration-Injection.md) for using config values
- Read [Configuration Objects](../core/Configuration-Objects.md) for complex structure mapping
- Check [Bean Lifecycle](../core/Bean-Lifecycle.md) to understand when providers load

---

**See also:**
- [Quick Start](../getting-started/Quick-Start.md) - Basic configuration setup
- [Project Structure](../getting-started/Project-Structure.md) - Organizing your plugin
- [Configuration Transformers](../core/Configuration-Transformers.md) - Custom value transformations
- [Post-Initialization](../core/Post-Initialization.md) - Configuration validation hooks
