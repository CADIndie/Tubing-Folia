# Tubing Framework Documentation

**Version:** 7.5.6

Tubing is a powerful Inversion of Control (IoC) and Dependency Injection framework for Minecraft server plugins. It provides annotation-based dependency injection, sophisticated configuration management, and platform-specific integrations for Bukkit, BungeeCord, and Velocity.

## Why Tubing?

Traditional Minecraft plugins often struggle with tight coupling, making code difficult to test, maintain, and extend. Tubing solves this by:

- **Loose Coupling**: Dependency injection enables true separation of concerns
- **Zero Boilerplate**: Annotations eliminate manual object creation and wiring
- **Configuration Made Easy**: Inject configuration values anywhere with `@ConfigProperty`
- **Platform Abstraction**: Write once, deploy across Bukkit, BungeeCord, or Velocity
- **Testability**: IoC design makes unit testing straightforward
- **Extensibility**: Multi-provider patterns and conditional beans support plugin ecosystems

## Core Features

### Dependency Injection
- Constructor-based injection with automatic dependency resolution
- Interface injection for loose coupling
- List injection for multi-implementation patterns
- Conditional bean registration (`@ConditionalOnMissingBean`)

### Configuration Management
- Support for multiple YAML configuration files
- Automatic configuration file updates and migrations
- Type-safe property injection with `@ConfigProperty`
- Complex object mapping with `@ConfigEmbeddedObject` and `@ConfigObjectList`
- Custom type transformers for any data type

### Platform Integrations
- **Bukkit**: Commands, subcommands, event listeners, plugin messages, permissions
- **BungeeCord**: Commands, event listeners, proxy-specific features
- **Velocity**: Modern proxy support with command aliases and event handling

### GUI Framework (Bukkit)
- MVC-style GUI controllers with `@GuiController` and `@GuiAction`
- Freemarker template engine for dynamic inventories
- Async GUI support for long-running operations
- Chat input handling
- Navigation history and exception handling

## Getting Started

New to Tubing? Start here:

1. **[Installation](getting-started/Installation.md)** - Add Tubing to your project
2. **[Quick Start](getting-started/Quick-Start.md)** - Build your first Tubing plugin
3. **[Project Structure](getting-started/Project-Structure.md)** - Organize your code effectively
4. **[Migration Guide](getting-started/Migration-Guide.md)** - Convert existing plugins

## Documentation Structure

### Core Framework
Learn the fundamental IoC and configuration features:

- [IoC Container](core/IoC-Container.md) - Container lifecycle and fundamentals
- [Dependency Injection](core/Dependency-Injection.md) - DI patterns and best practices
- [Bean Lifecycle](core/Bean-Lifecycle.md) - How beans are created and managed
- [Bean Registration](core/Bean-Registration.md) - Using `@IocBean`
- [Bean Providers](core/Bean-Providers.md) - Factory methods with `@IocBeanProvider`
- [Multi-Implementation](core/Multi-Implementation.md) - Managing multiple implementations
- [Conditional Beans](core/Conditional-Beans.md) - Conditional registration patterns
- [Configuration Injection](core/Configuration-Injection.md) - Inject config values
- [Configuration Files](core/Configuration-Files.md) - Multiple configs, migrations, auto-update
- [Configuration Objects](core/Configuration-Objects.md) - Map complex objects from YAML
- [Configuration Transformers](core/Configuration-Transformers.md) - Custom type conversion
- [Post-Initialization](core/Post-Initialization.md) - Hooks after container loads

### Platform-Specific Guides

**Bukkit**
- [Bukkit Setup](bukkit/Bukkit-Setup.md)
- [Commands](bukkit/Commands.md)
- [Subcommands](bukkit/Subcommands.md)
- [Event Listeners](bukkit/Event-Listeners.md)
- [Plugin Messages](bukkit/Plugin-Messages.md)
- [Permissions](bukkit/Permissions.md)
- [Messaging](bukkit/Messaging.md)

**BungeeCord**
- [BungeeCord Setup](bungee/Bungee-Setup.md)
- [Commands](bungee/Commands.md)
- [Listeners](bungee/Listeners.md)

**Velocity**
- [Velocity Setup](velocity/Velocity-Setup.md)
- [Commands](velocity/Commands.md)
- [Listeners](velocity/Listeners.md)

### GUI Framework (Bukkit)
- [GUI Setup](gui/GUI-Setup.md)
- [GUI Controllers](gui/GUI-Controllers.md)
- [GUI Building](gui/GUI-Building.md)
- [GUI Actions](gui/GUI-Actions.md)
- [GUI Templates](gui/GUI-Templates.md)
- [GUI Async](gui/GUI-Async.md)
- [GUI History](gui/GUI-History.md)
- [Chat Input](gui/Chat-Input.md)
- [Exception Handling](gui/Exception-Handling.md)

### Advanced Topics
- [Custom Annotations](advanced/Custom-Annotations.md)
- [Custom Configuration Provider](advanced/Custom-Configuration-Provider.md)
- [Manual Bean Registration](advanced/Manual-Bean-Registration.md)
- [Reflection Utilities](advanced/Reflection-Utilities.md)

### Best Practices
- [Project Architecture](best-practices/Project-Architecture.md)
- [Testing](best-practices/Testing.md)
- [Performance](best-practices/Performance.md)
- [Common Patterns](best-practices/Common-Patterns.md)

### Troubleshooting
- [Common Errors](troubleshooting/Common-Errors.md)
- [Debugging](troubleshooting/Debugging.md)
- [FAQ](troubleshooting/FAQ.md)

### Reference
- [Annotations](reference/Annotations.md)
- [Core Classes](reference/Core-Classes.md)
- [Configuration Options](reference/Configuration-Options.md)

## Quick Example

Here's a taste of what Tubing can do:

```java
// Define a service with dependency injection
@IocBean
public class PlayerManager {
    private final Database database;
    private final PermissionService permissions;

    @ConfigProperty("player.max-homes")
    private int maxHomes;

    // Constructor injection - dependencies resolved automatically
    public PlayerManager(Database database, PermissionService permissions) {
        this.database = database;
        this.permissions = permissions;
    }

    public void teleportHome(Player player, String homeName) {
        if (!permissions.hasPermission(player, "homes.use")) {
            return;
        }
        Location home = database.getHome(player, homeName);
        player.teleport(home);
    }
}

// Register a command - automatically injected with PlayerManager
@IocBukkitCommandHandler("home")
public class HomeCommand {
    private final PlayerManager playerManager;

    public HomeCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length == 0) {
            playerManager.teleportHome(player, "default");
            return true;
        }
        playerManager.teleportHome(player, args[0]);
        return true;
    }
}

// Your main plugin class - minimal setup needed
public class MyPlugin extends TubingBukkitPlugin {
    @Override
    protected void enable() {
        getLogger().info("Plugin enabled with Tubing!");
    }

    @Override
    protected void disable() {
        getLogger().info("Plugin disabled");
    }
}
```

That's it! No manual object creation, no configuration parsing boilerplate, no command registration code. Tubing handles it all.

## Community & Support

- **Issues & Bugs**: [GitHub Issues](https://github.com/garagepoort/Tubing/issues)
- **Maven Repository**: [StaffPlusPlus Nexus](https://nexus.staffplusplus.org/repository/staffplusplus/)

## License

Tubing is available under the MIT License. See the repository for full license details.

---

**Ready to get started?** Head over to the [Installation Guide](getting-started/Installation.md) to set up your first Tubing project!
