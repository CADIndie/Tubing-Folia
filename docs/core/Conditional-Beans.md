# Conditional Beans

Conditional beans allow you to control which beans are instantiated based on runtime conditions like configuration properties or the presence of other beans. This enables flexible plugin architectures with feature toggles, default implementations, and platform-specific behavior.

## Overview

Tubing provides two main mechanisms for conditional bean registration:

- **`@ConditionalOnProperty`** - Register beans only when configuration properties match specific values
- **`@ConditionalOnMissingBean`** - Register fallback beans only when no other implementation exists

These mechanisms enable:
- Feature toggles controlled by configuration
- Default implementations that can be overridden
- Platform-specific bean selection
- Environment-based configuration (dev/staging/production)
- Optional integrations with external plugins

## @ConditionalOnProperty

The `conditionalOnProperty` attribute on bean annotations allows you to conditionally register beans based on configuration values.

### Basic Usage

All bean annotations support the `conditionalOnProperty` attribute:

```java
@IocBean(conditionalOnProperty = "features.pvp.enabled")
public class PvpService {
    // Only created if features.pvp.enabled = true in config
}
```

**Configuration (config.yml):**
```yaml
features:
  pvp:
    enabled: true
```

If `features.pvp.enabled` is `false` or missing (and not required), the bean is not instantiated.

### Platform-Specific Beans

```java
@IocBukkitListener(conditionalOnProperty = "features.combat.enabled")
public class CombatListener implements Listener {
    // Listener registered only if combat feature is enabled
}

@IocBukkitCommandHandler(
    value = "shop",
    conditionalOnProperty = "features.shop.enabled"
)
public class ShopCommand {
    // Command registered only if shop feature is enabled
}
```

### Supported Condition Formats

The `IocConditionalPropertyFilter` evaluates conditions in several formats:

#### 1. Boolean Equality

```java
@IocBean(conditionalOnProperty = "feature.enabled=true")
public class EnabledFeature {
    // Created only if feature.enabled equals "true" (case-insensitive)
}
```

Configuration:
```yaml
feature:
  enabled: true
```

#### 2. String Equality

```java
@IocBean(conditionalOnProperty = "storage.type=mysql")
public class MySQLStorageService implements StorageService {
    // Created only if storage.type equals "mysql"
}

@IocBean(conditionalOnProperty = "storage.type=redis")
public class RedisStorageService implements StorageService {
    // Created only if storage.type equals "redis"
}
```

Configuration:
```yaml
storage:
  type: mysql
```

#### 3. isEmpty Check

```java
@IocBean(conditionalOnProperty = "isEmpty(external.api.key)")
public class DisabledExternalService implements ExternalService {
    // Created only if external.api.key is empty or blank

    @Override
    public void callApi() {
        // No-op implementation
    }
}
```

Configuration (disabled):
```yaml
external:
  api:
    key: ""
```

#### 4. isNotEmpty Check

```java
@IocBean(conditionalOnProperty = "isNotEmpty(external.api.key)")
public class EnabledExternalService implements ExternalService {
    @ConfigProperty("external.api.key")
    private String apiKey;

    @Override
    public void callApi() {
        // Real API implementation
    }
}
```

Configuration (enabled):
```yaml
external:
  api:
    key: "abc123xyz"
```

### Combining Multiple Conditions

Use `&&` to combine multiple conditions (all must be true):

```java
@IocBean(conditionalOnProperty = "features.advanced.enabled && features.premium.enabled")
public class AdvancedPremiumFeature {
    // Created only if BOTH features are enabled
}
```

Configuration:
```yaml
features:
  advanced:
    enabled: true
  premium:
    enabled: true
```

**Complex example:**

```java
@IocBean(conditionalOnProperty = "storage.type=mysql && isNotEmpty(database.host)")
public class MySQLWithHostService {
    // Created only if storage type is mysql AND database.host is configured
}
```

Configuration:
```yaml
storage:
  type: mysql
database:
  host: localhost
```

### When Conditions Are Evaluated

Conditional properties are evaluated during the **Validation Phase** of the bean lifecycle, after the `ConfigurationLoader` is instantiated but before beans are created:

```
1. Discovery Phase (ClassGraph scans for beans)
2. Collection Phase (Beans collected)
3. Sorting Phase (Priority beans sorted)
4. ConfigurationLoader instantiated (priority bean)
5. Validation Phase (Conditional beans evaluated) ← @ConditionalOnProperty checked here
6. Instantiation Phase (Valid beans created)
```

This means:
- Configuration files must be loaded before conditions are evaluated
- `ConfigurationLoader` always has `priority = true` to ensure it's created first
- Beans that don't pass validation are filtered out and never instantiated

### Use Cases for @ConditionalOnProperty

#### 1. Feature Toggles

```java
@IocBean(conditionalOnProperty = "features.homes.enabled")
public class HomesService {
    // Player home management
}

@IocBukkitCommandHandler(
    value = "home",
    conditionalOnProperty = "features.homes.enabled"
)
public class HomeCommand {
    // /home command
}
```

Configuration:
```yaml
features:
  homes:
    enabled: true  # Toggle feature on/off
```

#### 2. Environment-Specific Beans

```java
@IocBean(conditionalOnProperty = "environment=development")
public class DevelopmentDatabaseService implements DatabaseService {
    // Uses in-memory database for testing
}

@IocBean(conditionalOnProperty = "environment=production")
public class ProductionDatabaseService implements DatabaseService {
    // Uses real MySQL database
}
```

Configuration (dev):
```yaml
environment: development
```

#### 3. Optional Integration

```java
@IocBean(conditionalOnProperty = "isNotEmpty(vault.enabled)")
public class VaultEconomyProvider implements EconomyProvider {
    // Integration with Vault plugin
}

@IocBean(conditionalOnProperty = "isEmpty(vault.enabled)")
public class BasicEconomyProvider implements EconomyProvider {
    // Built-in economy system
}
```

Configuration:
```yaml
vault:
  enabled: true
```

#### 4. Backend Selection

```java
@IocBean(conditionalOnProperty = "cache.backend=redis")
public class RedisCacheService implements CacheService {
    // Redis cache implementation
}

@IocBean(conditionalOnProperty = "cache.backend=memory")
public class InMemoryCacheService implements CacheService {
    // In-memory cache
}

@IocBean(conditionalOnProperty = "cache.backend=disabled")
public class NoOpCacheService implements CacheService {
    // No-op cache (disabled)
}
```

Configuration:
```yaml
cache:
  backend: redis
```

## @ConditionalOnMissingBean

The `@ConditionalOnMissingBean` annotation marks a bean as a fallback implementation. It's only instantiated when no other non-conditional implementation of the interface exists.

### Basic Usage

```java
public interface PermissionService {
    boolean hasPermission(Player player, String permission);
}

@IocBean
@ConditionalOnMissingBean
public class DefaultPermissionService implements PermissionService {
    @Override
    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }
}
```

If no other `PermissionService` implementation is registered, `DefaultPermissionService` is used. If a user provides their own implementation:

```java
@IocBean
public class CustomPermissionService implements PermissionService {
    @Override
    public boolean hasPermission(Player player, String permission) {
        // Custom permission logic
        return checkCustomPermissions(player, permission);
    }
}
```

Then `DefaultPermissionService` is skipped, and `CustomPermissionService` is used instead.

### How It Works

When resolving an interface dependency, the container follows this algorithm:

```
1. Check if a bean already exists implementing the interface
2. Check if a provider method returns the interface
3. Find all implementations in the codebase:
   a. Separate into non-conditional and @ConditionalOnMissingBean
   b. If multiple non-conditional exist → ERROR
   c. If one non-conditional exists → USE IT
   d. If zero non-conditional exist:
      - If multiple @ConditionalOnMissingBean → ERROR
      - If one @ConditionalOnMissingBean → USE IT
      - If zero implementations → ERROR
```

**Key rules:**

- Non-conditional implementations always take precedence
- Only one non-conditional implementation allowed per interface
- Only one conditional implementation allowed when no non-conditional exists
- Evaluated during **Interface Resolution** in the Instantiation Phase

### Real-World Examples

#### 1. Default Permission Provider

From `tubing-bukkit`:

```java
public interface TubingPermissionService {
    boolean has(Player player, String permission);
    boolean has(CommandSender sender, String permission);
}

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

Users can override with Vault integration:

```java
@IocBean
public class VaultPermissionService implements TubingPermissionService {

    private final Permission vaultPerms;

    @Override
    public boolean has(Player player, String permission) {
        return vaultPerms.has(player, permission);
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return vaultPerms.has(sender, permission);
    }
}
```

#### 2. Default Configuration Provider

From `tubing-core`:

```java
public interface TubingConfigurationProvider {
    Map<String, InputStream> getConfigurations();
}

@IocBean
@ConditionalOnMissingBean
public class DefaultTubingConfigurationProvider implements TubingConfigurationProvider {

    @InjectTubingPlugin
    private TubingPlugin tubingPlugin;

    @Override
    public Map<String, InputStream> getConfigurations() {
        Map<String, InputStream> configs = new LinkedHashMap<>();
        configs.put("config.yml", tubingPlugin.getResource("config.yml"));
        return configs;
    }
}
```

Users can provide custom configuration sources:

```java
@IocBean
public class MultiFileConfigProvider implements TubingConfigurationProvider {

    @Override
    public Map<String, InputStream> getConfigurations() {
        Map<String, InputStream> configs = new LinkedHashMap<>();
        configs.put("config.yml", getResource("config.yml"));
        configs.put("messages.yml", getResource("messages.yml"));
        configs.put("database.yml", getResource("database.yml"));
        return configs;
    }
}
```

#### 3. Default Template Resolver

From `tubing-bukkit-gui`:

```java
public interface TubingGuiTemplateResolver {
    String resolveTemplate(String templateName, Map<String, Object> model);
}

@IocBean
@ConditionalOnMissingBean
public class FreemarkerGuiTemplateResolver implements TubingGuiTemplateResolver {

    @Override
    public String resolveTemplate(String templateName, Map<String, Object> model) {
        // FreeMarker template engine integration
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        // ... template processing
    }
}
```

Users can replace with a different template engine:

```java
@IocBean
public class MustacheGuiTemplateResolver implements TubingGuiTemplateResolver {

    @Override
    public String resolveTemplate(String templateName, Map<String, Object> model) {
        // Mustache template engine integration
        MustacheFactory mf = new DefaultMustacheFactory();
        // ... template processing
    }
}
```

### Error Scenarios

#### Too Many Non-Conditional Implementations

```java
public interface StorageService { }

@IocBean
public class MySQLStorage implements StorageService { }

@IocBean
public class RedisStorage implements StorageService { }

// ERROR: Multiple beans found with interface StorageService.
// Use @IocMultiProvider for multiple implementations.
```

**Solution:** Use `@ConditionalOnProperty` to ensure only one is active:

```java
@IocBean(conditionalOnProperty = "storage.type=mysql")
public class MySQLStorage implements StorageService { }

@IocBean(conditionalOnProperty = "storage.type=redis")
public class RedisStorage implements StorageService { }
```

#### Too Many Conditional Implementations

```java
public interface RewardHandler { }

@IocBean
@ConditionalOnMissingBean
public class DefaultRewardHandler implements RewardHandler { }

@IocBean
@ConditionalOnMissingBean
public class AlternativeRewardHandler implements RewardHandler { }

// ERROR: At most one bean should be defined.
// Too many beans annotated with @ConditionalOnMissingBean
```

**Solution:** Only define one default implementation, or use `@ConditionalOnProperty`:

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultRewardHandler implements RewardHandler { }

// Remove alternative, or make it conditional:
@IocBean(conditionalOnProperty = "rewards.alternative=true")
public class AlternativeRewardHandler implements RewardHandler { }
```

## Combining Conditions

You can combine `@ConditionalOnProperty` and `@ConditionalOnMissingBean` for sophisticated fallback logic.

### Property-Based Defaults

```java
public interface NotificationService {
    void notify(Player player, String message);
}

// Default: No-op service when notifications disabled
@IocBean(conditionalOnProperty = "notifications.enabled=false")
@ConditionalOnMissingBean
public class DisabledNotificationService implements NotificationService {
    @Override
    public void notify(Player player, String message) {
        // No-op
    }
}

// Default: Chat-based when enabled but no custom impl
@IocBean(conditionalOnProperty = "notifications.enabled=true")
@ConditionalOnMissingBean
public class ChatNotificationService implements NotificationService {
    @Override
    public void notify(Player player, String message) {
        player.sendMessage(message);
    }
}

// User can override with custom implementation
@IocBean
public class CustomNotificationService implements NotificationService {
    @Override
    public void notify(Player player, String message) {
        // Custom notification logic (title, actionbar, sound, etc.)
    }
}
```

Configuration:
```yaml
notifications:
  enabled: true
```

Resolution:
- If `notifications.enabled = false` and no custom impl → `DisabledNotificationService`
- If `notifications.enabled = true` and no custom impl → `ChatNotificationService`
- If custom impl exists → `CustomNotificationService` (regardless of config)

### Multi-Tier Fallbacks

```java
public interface DatabaseService {
    void connect();
}

// Tier 1: Preferred production database
@IocBean(conditionalOnProperty = "database.type=mysql && isNotEmpty(database.host)")
public class MySQLDatabaseService implements DatabaseService {
    @Override
    public void connect() {
        // Connect to MySQL
    }
}

// Tier 2: Fallback SQLite for local dev
@IocBean(conditionalOnProperty = "database.type=sqlite")
public class SQLiteDatabaseService implements DatabaseService {
    @Override
    public void connect() {
        // Use local SQLite file
    }
}

// Tier 3: Default in-memory for testing
@IocBean
@ConditionalOnMissingBean
public class InMemoryDatabaseService implements DatabaseService {
    @Override
    public void connect() {
        // Use in-memory H2 database
    }
}
```

Configuration scenarios:

1. **Production** (MySQL configured):
```yaml
database:
  type: mysql
  host: db.example.com
```
→ `MySQLDatabaseService` used

2. **Local development** (SQLite):
```yaml
database:
  type: sqlite
```
→ `SQLiteDatabaseService` used

3. **Testing** (no config):
```yaml
# No database config
```
→ `InMemoryDatabaseService` used (default)

## Use Cases for Conditional Beans

### 1. Default Implementations

Provide sensible defaults that can be overridden:

```java
public interface CommandExceptionHandler {
    void handleException(CommandSender sender, Exception exception);
}

@IocBean
@ConditionalOnMissingBean
public class DefaultCommandExceptionHandler implements CommandExceptionHandler {
    @Override
    public void handleException(CommandSender sender, Exception exception) {
        sender.sendMessage(ChatColor.RED + "An error occurred: " + exception.getMessage());
    }
}
```

Users can provide custom error handling:

```java
@IocBean
public class PrettyCommandExceptionHandler implements CommandExceptionHandler {
    @Override
    public void handleException(CommandSender sender, Exception exception) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.DARK_RED + "═══════════════════════");
        sender.sendMessage(ChatColor.RED + "⚠ Error");
        sender.sendMessage(ChatColor.GRAY + exception.getMessage());
        sender.sendMessage(ChatColor.DARK_RED + "═══════════════════════");
    }
}
```

### 2. Feature Toggles

Enable/disable entire features through configuration:

```java
@IocBean(conditionalOnProperty = "features.economy.enabled")
public class EconomyService {
    // Full economy system
}

@IocBukkitCommandHandler(
    value = "balance",
    conditionalOnProperty = "features.economy.enabled"
)
public class BalanceCommand {
    // /balance command
}

@IocBukkitCommandHandler(
    value = "pay",
    conditionalOnProperty = "features.economy.enabled"
)
public class PayCommand {
    // /pay command
}
```

Configuration:
```yaml
features:
  economy:
    enabled: true  # Toggle entire economy system
```

### 3. Platform-Specific Beans

Select implementations based on platform:

```java
public interface SchedulerService {
    void runAsync(Runnable task);
}

@IocBean(conditionalOnProperty = "platform=bukkit")
public class BukkitSchedulerService implements SchedulerService {
    @InjectTubingPlugin
    private TubingBukkitPlugin plugin;

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }
}

@IocBean(conditionalOnProperty = "platform=velocity")
public class VelocitySchedulerService implements SchedulerService {
    @InjectTubingPlugin
    private ProxyServer proxy;

    @Override
    public void runAsync(Runnable task) {
        proxy.getScheduler().buildTask(plugin, task).schedule();
    }
}
```

### 4. Development vs Production

Different behaviors for different environments:

```java
@IocBean(conditionalOnProperty = "environment=development")
public class DevelopmentEmailService implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        // Log to console instead of sending
        System.out.println("EMAIL: " + to + " - " + subject);
    }
}

@IocBean(conditionalOnProperty = "environment=production")
public class ProductionEmailService implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        // Actually send email via SMTP
        smtpClient.send(to, subject, body);
    }
}
```

Configuration (dev):
```yaml
environment: development
```

### 5. Optional External Integrations

Integrate with external plugins only when available:

```java
public interface EconomyProvider {
    double getBalance(Player player);
    void withdraw(Player player, double amount);
}

@IocBean(conditionalOnProperty = "integrations.vault.enabled=true")
public class VaultEconomyProvider implements EconomyProvider {

    private Economy vaultEconomy;

    @Override
    public double getBalance(Player player) {
        return vaultEconomy.getBalance(player);
    }

    @Override
    public void withdraw(Player player, double amount) {
        vaultEconomy.withdrawPlayer(player, amount);
    }
}

@IocBean
@ConditionalOnMissingBean
public class BuiltInEconomyProvider implements EconomyProvider {

    private final Map<UUID, Double> balances = new HashMap<>();

    @Override
    public double getBalance(Player player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    @Override
    public void withdraw(Player player, double amount) {
        double balance = getBalance(player);
        balances.put(player.getUniqueId(), balance - amount);
    }
}
```

Configuration (with Vault):
```yaml
integrations:
  vault:
    enabled: true
```

Configuration (without Vault):
```yaml
integrations:
  vault:
    enabled: false
```

### 6. A/B Testing

Enable different implementations for testing:

```java
@IocBean(conditionalOnProperty = "experiment.algorithm=v1")
public class AlgorithmV1 implements RecommendationAlgorithm {
    @Override
    public List<Item> recommend(Player player) {
        // Original algorithm
    }
}

@IocBean(conditionalOnProperty = "experiment.algorithm=v2")
public class AlgorithmV2 implements RecommendationAlgorithm {
    @Override
    public List<Item> recommend(Player player) {
        // New experimental algorithm
    }
}
```

Configuration:
```yaml
experiment:
  algorithm: v2  # Switch between v1 and v2
```

## Best Practices

### 1. Prefer @ConditionalOnProperty for Mutually Exclusive Beans

When you have multiple implementations that should never coexist:

```java
@IocBean(conditionalOnProperty = "storage=mysql")
public class MySQLStorage implements Storage { }

@IocBean(conditionalOnProperty = "storage=redis")
public class RedisStorage implements Storage { }

@IocBean(conditionalOnProperty = "storage=file")
public class FileStorage implements Storage { }
```

### 2. Use @ConditionalOnMissingBean for User-Overridable Defaults

When providing framework defaults that users might replace:

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultPermissionService implements PermissionService {
    // Framework default
}
```

### 3. Document Conditional Beans

Make it clear which beans are conditional and how to override them:

```java
/**
 * Default permission service implementation.
 * Uses Bukkit's built-in permission system.
 *
 * To override with a custom implementation:
 * <pre>
 * {@code
 * @IocBean
 * public class MyPermissionService implements TubingPermissionService {
 *     // Custom implementation
 * }
 * }
 * </pre>
 */
@IocBean
@ConditionalOnMissingBean
public class DefaultTubingPermissionService implements TubingPermissionService {
    // ...
}
```

### 4. Validate Configuration Properties

Ensure required properties are present when using conditions:

```java
@IocBean(conditionalOnProperty = "isNotEmpty(api.key)")
public class ExternalApiService {

    @ConfigProperty(value = "api.key", required = true)
    private String apiKey;

    // Bean is created only if api.key is not empty
    // AND @ConfigProperty ensures it's not null
}
```

### 5. Combine Conditions Logically

Use `&&` for conditions that must all be true:

```java
@IocBean(conditionalOnProperty = "feature.enabled && isNotEmpty(api.endpoint)")
public class FeatureService {
    // Created only if feature is enabled AND endpoint is configured
}
```

### 6. Provide No-Op Implementations

Instead of returning null or not creating beans, provide no-op implementations:

```java
public interface MetricsService {
    void track(String event);
}

@IocBean(conditionalOnProperty = "metrics.enabled=true")
public class BStatsMetricsService implements MetricsService {
    @Override
    public void track(String event) {
        // Send to bStats
    }
}

@IocBean(conditionalOnProperty = "metrics.enabled=false")
@ConditionalOnMissingBean
public class DisabledMetricsService implements MetricsService {
    @Override
    public void track(String event) {
        // No-op
    }
}
```

This prevents null pointer exceptions when beans depend on `MetricsService`.

### 7. Keep Conditions Simple

Avoid overly complex condition strings. If you need complex logic, use bean providers:

```java
// Bad: Complex condition in annotation
@IocBean(conditionalOnProperty = "a=1 && b=2 && isNotEmpty(c) && isEmpty(d)")
public class ComplexBean { }

// Good: Use a provider with clear logic
@TubingConfiguration
public class BeanConfiguration {

    @IocBeanProvider
    public static ComplexBean provideComplexBean(
            @ConfigProperty("a") int a,
            @ConfigProperty("b") int b,
            @ConfigProperty("c") String c,
            @ConfigProperty("d") String d) {

        if (a == 1 && b == 2 &&
            c != null && !c.isEmpty() &&
            (d == null || d.isEmpty())) {
            return new ComplexBean();
        }

        return null;
    }
}
```

### 8. Test All Conditional Paths

Ensure you test all configuration combinations:

```java
// Test with feature enabled
@Test
public void testFeatureEnabled() {
    config.set("feature.enabled", true);
    container.reload();
    assertNotNull(container.get(FeatureService.class));
}

// Test with feature disabled
@Test
public void testFeatureDisabled() {
    config.set("feature.enabled", false);
    container.reload();
    assertThrows(IocException.class, () ->
        container.get(FeatureService.class));
}
```

## Common Pitfalls

### 1. Missing Configuration Keys

If a configuration key doesn't exist, the condition evaluation fails:

```java
@IocBean(conditionalOnProperty = "unknown.key")
public class MyBean { }
```

```
IocException: ConditionOnProperty referencing an unknown property [unknown.key]
```

**Solution:** Ensure configuration keys exist or use `isEmpty/isNotEmpty` for optional keys.

### 2. Case Sensitivity

Configuration property paths are case-sensitive:

```yaml
features:
  PVP:  # Wrong case
    enabled: true
```

```java
@IocBean(conditionalOnProperty = "features.pvp.enabled")  // Won't match
public class PvpService { }
```

**Solution:** Use consistent casing in both configuration and annotations.

### 3. Multiple Conditional Defaults

Don't define multiple `@ConditionalOnMissingBean` for the same interface:

```java
// Wrong!
@IocBean
@ConditionalOnMissingBean
public class DefaultServiceA implements Service { }

@IocBean
@ConditionalOnMissingBean
public class DefaultServiceB implements Service { }
```

**Solution:** Use only one default, or use `@ConditionalOnProperty` to differentiate.

### 4. Forgetting to Load Configuration

Conditions require configuration to be loaded. Ensure `ConfigurationLoader` is prioritized:

```java
@IocBean(priority = true)
public class ConfigurationLoader {
    // Must be created before conditional beans are evaluated
}
```

This is handled automatically by Tubing, but custom configuration loaders must maintain priority.

## Evaluation Order

Understanding when conditions are evaluated helps debug issues:

```
1. Plugin Startup
2. ClassGraph Package Scan
3. Bean Discovery (all annotated classes found)
4. Bean Collection (beans catalogued)
5. Priority Sorting (priority beans first)
6. ConfigurationLoader Instantiation (always priority)
   ↓
7. Conditional Property Evaluation ← @ConditionalOnProperty checked here
   - Filters out beans with unmet property conditions
   ↓
8. Bean Instantiation
   - For each bean in sorted order
   - Interface Resolution
     ↓
     - @ConditionalOnMissingBean evaluated here
     - Non-conditional implementations preferred
     - Conditional used only if no non-conditional exists
   ↓
9. Configuration Injection
10. Post-Initialization
```

## Summary

Conditional beans provide powerful mechanisms for flexible plugin architectures:

- **`@ConditionalOnProperty`** - Evaluated during Validation Phase based on configuration
  - Supports equality checks (`key=value`)
  - Supports `isEmpty(key)` and `isNotEmpty(key)`
  - Supports multiple conditions with `&&`
  - Use for feature toggles, environment selection, and backend switching

- **`@ConditionalOnMissingBean`** - Evaluated during Interface Resolution
  - Only instantiated when no non-conditional implementation exists
  - Provides fallback/default implementations
  - Use for user-overridable framework defaults

**Key principles:**
- Configuration-based conditions enable runtime flexibility
- Default implementations provide good out-of-box experience
- Users can override defaults without modifying framework code
- Combine both mechanisms for sophisticated conditional logic

## Next Steps

- **[Bean Lifecycle](Bean-Lifecycle.md)** - Understand when conditions are evaluated
- **[Bean Providers](Bean-Providers.md)** - Use providers for complex conditional logic
- **[Configuration Injection](Configuration-Injection.md)** - Master configuration properties
- **[Multi-Implementation](Multi-Implementation.md)** - Work with multiple implementations

---

**See also:**
- [IoC Container](IoC-Container.md) - Container fundamentals
- [Bean Registration](Bean-Registration.md) - Basic bean registration
- [Project Structure](../getting-started/Project-Structure.md) - Organizing conditional beans
