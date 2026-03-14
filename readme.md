# Tubing Framework

**Version:** 7.5.6

Tubing is a powerful Inversion of Control (IoC) and Dependency Injection framework for Minecraft server plugins. It brings modern software architecture patterns to plugin development, enabling loose coupling, testability, and clean code organization across Bukkit, BungeeCord, and Velocity platforms.

## Why Tubing?

Traditional Minecraft plugins struggle with tight coupling and boilerplate code. Tubing solves this with:

- 🔌 **Zero Boilerplate** - Annotations eliminate manual object creation and wiring
- 💉 **Dependency Injection** - Constructor-based injection with automatic dependency resolution
- ⚙️ **Smart Configuration** - Type-safe property injection with auto-updates and migrations
- 🎮 **Platform Abstraction** - Write once, deploy to Bukkit, BungeeCord, or Velocity
- 🖼️ **Advanced GUI Framework** - MVC-style inventory GUIs with Freemarker templates (Bukkit)
- 🧪 **Testability** - Constructor injection makes unit testing straightforward
- 🔧 **Extensibility** - Multi-provider patterns for building plugin ecosystems

## Core Features

### Dependency Injection
- 🏗️ Constructor-based dependency injection
- 🔗 Interface injection for loose coupling
- 📋 List injection for multi-implementation patterns
- ❓ Conditional bean registration (`@ConditionalOnMissingBean`, `@ConditionalOnProperty`)

### Configuration Management
- 📄 Multiple YAML configuration files
- 🔄 Automatic file updates preserving user values
- 🔀 Configuration migrations between versions
- 💾 Type-safe injection with `@ConfigProperty`
- 🎯 Complex object mapping (`@ConfigEmbeddedObject`, `@ConfigObjectList`)
- 🔧 Custom type transformers

### Platform Support
- **Bukkit/Spigot/Paper**: Commands, subcommands, events, plugin messages, permissions
- **BungeeCord/Waterfall**: Proxy commands, events, cross-server communication
- **Velocity**: Modern proxy with Adventure components, advanced commands

### GUI Framework (Bukkit)
- 🎨 MVC pattern with `@GuiController` and `@GuiAction`
- 📝 Freemarker templates for dynamic inventories
- ⚡ Async GUI support with loading screens
- 📜 Navigation history and back button
- 💬 Chat input integration
- ⚠️ Exception handlers for graceful errors

## Quick Start

### 1. Add Maven Dependency

```xml
<repository>
    <id>staffplusplus-repo</id>
    <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
</repository>
```

Choose your platform:

**Bukkit:**
```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**BungeeCord:**
```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bungee</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Velocity:**
```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-velocity</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 2. Configure Maven Shade Plugin

**Important:** Always relocate Tubing to avoid conflicts:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>be.garagepoort.mcioc.</pattern>
                        <shadedPattern>com.yourplugin.tubing.</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3. Create Your Main Plugin Class

**Bukkit:**
```java
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

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

**BungeeCord:**
```java
import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyPlugin extends TubingBungeePlugin {

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

**Velocity:**
```java
import be.garagepoort.mcioc.tubingvelocity.TubingVelocityPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public class MyPlugin extends TubingVelocityPlugin {

    @Inject
    public MyPlugin(ProxyServer server) {
        super(server);
    }

    @Override
    protected void enable() {
        getLogger().info("Plugin enabled with Tubing!");
    }
}
```

### 4. Create Your First Bean

```java
import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;

@IocBean
public class PlayerManager {

    @ConfigProperty("player.max-homes")
    private int maxHomes;

    public void teleportHome(Player player, String homeName) {
        // Your logic here
    }
}
```

### 5. Create a Command

```java
import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;

@IocBukkitCommandHandler("home")
public class HomeCommand {

    private final PlayerManager playerManager;

    // Dependency automatically injected!
    public HomeCommand(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public boolean handle(Player player, String[] args) {
        playerManager.teleportHome(player, args[0]);
        return true;
    }
}
```

**That's it!** No manual registration, no configuration boilerplate, no singleton patterns. Everything is automatically wired together.

## 📚 Complete Documentation

Comprehensive documentation is now available in the [`docs/`](docs/) directory:

### Getting Started
- **[Installation Guide](docs/getting-started/Installation.md)** - Complete Maven setup
- **[Quick Start Tutorial](docs/getting-started/Quick-Start.md)** - Build your first plugin
- **[Project Structure](docs/getting-started/Project-Structure.md)** - Organize your code
- **[Migration Guide](docs/getting-started/Migration-Guide.md)** - Convert existing plugins

### Core Concepts
- [IoC Container](docs/core/IoC-Container.md) - Understanding the container
- [Dependency Injection](docs/core/Dependency-Injection.md) - DI patterns
- [Bean Lifecycle](docs/core/Bean-Lifecycle.md) - Bean creation and management
- [Configuration System](docs/core/Configuration-Files.md) - Advanced configuration

### Platform Guides
- [Bukkit Documentation](docs/bukkit/Bukkit-Setup.md) - Commands, events, listeners
- [BungeeCord Documentation](docs/bungee/Bungee-Setup.md) - Proxy commands and events
- [Velocity Documentation](docs/velocity/Velocity-Setup.md) - Modern proxy features

### Advanced Topics
- [GUI Framework](docs/gui/GUI-Setup.md) - Build sophisticated inventory GUIs
- [Best Practices](docs/best-practices/Project-Architecture.md) - Architecture and patterns
- [Testing Guide](docs/best-practices/Testing.md) - Unit and integration testing
- [Performance Optimization](docs/best-practices/Performance.md) - Performance tips

### Reference
- [All Annotations](docs/reference/Annotations.md) - Complete annotation reference
- [Core Classes](docs/reference/Core-Classes.md) - API reference
- [Troubleshooting](docs/troubleshooting/Common-Errors.md) - Solutions to common issues
- [FAQ](docs/troubleshooting/FAQ.md) - Frequently asked questions

**📖 [View Full Documentation](docs/README.md)**

## Example: Complete Plugin

Here's a complete example showing Tubing's power:

```java
// Service with dependency injection and configuration
@IocBean
public class HomeService {
    private final Database database;

    @ConfigProperty("homes.max-per-player")
    private int maxHomes;

    public HomeService(Database database) {
        this.database = database;
    }

    public void setHome(Player player, String name, Location location) {
        if (database.getHomeCount(player) >= maxHomes) {
            throw new TooManyHomesException();
        }
        database.saveHome(player, name, location);
    }
}

// Command handler with automatic dependency injection
@IocBukkitCommandHandler("sethome")
public class SetHomeCommand {
    private final HomeService homeService;

    public SetHomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    public boolean handle(Player player, String[] args) {
        homeService.setHome(player, args[0], player.getLocation());
        player.sendMessage("§aHome set!");
        return true;
    }
}

// Event listener
@IocBukkitListener
public class PlayerListener implements Listener {
    private final HomeService homeService;

    public PlayerListener(HomeService homeService) {
        this.homeService = homeService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Use homeService
    }
}
```

No manual wiring needed - everything is automatically connected!

## Platform Compatibility

| Platform | Versions | Java |
|----------|----------|------|
| Bukkit/Spigot/Paper | 1.8 - 1.20.x | Java 8+ |
| BungeeCord/Waterfall | All versions | Java 8+ |
| Velocity | 3.0+ | Java 11+ |

## Contributing

Contributions are welcome! Please submit issues and pull requests on GitHub.

## License

Tubing is available under the MIT License.

## Support

- 📖 **Documentation**: [docs/](docs/)
- 🐛 **Issues**: [GitHub Issues](https://github.com/garagepoort/Tubing/issues)
- 📦 **Maven Repository**: [StaffPlusPlus Nexus](https://nexus.staffplusplus.org/repository/staffplusplus/)

---

**Ready to build better plugins?** Start with the [Installation Guide](docs/getting-started/Installation.md)!
