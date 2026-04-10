# Quick Start

This guide will walk you through creating your first Tubing plugin from scratch. You'll learn the basics of dependency injection, configuration, and command handling.

## Project Setup

First, make sure you've completed the [Installation](Installation.md) guide and have a Maven project with Tubing dependencies.

## Step 1: Create Your Main Plugin Class

Instead of extending `JavaPlugin` (Bukkit), extend the platform-specific Tubing class:

### Bukkit

```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled with Tubing!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled");
    }
}
```

### BungeeCord

```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbungee.TubingBungeePlugin;

public class MyPlugin extends TubingBungeePlugin {

    @Override
    protected void enable() {
        getLogger().info("MyPlugin enabled with Tubing!");
    }

    @Override
    protected void disable() {
        getLogger().info("MyPlugin disabled");
    }
}
```

### Velocity

```java
package com.example.myplugin;

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
        getLogger().info("MyPlugin enabled with Tubing!");
    }
}
```

**That's it!** The Tubing container is automatically initialized when your plugin enables. Now let's add some functionality.

## Step 2: Create a Service with Dependency Injection

Create a service class to handle player data:

```java
package com.example.myplugin.services;

import be.garagepoort.mcioc.IocBean;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

@IocBean
public class PlayerDataService {

    private final Map<String, Integer> playerPoints = new HashMap<>();

    public void addPoints(Player player, int points) {
        String uuid = player.getUniqueId().toString();
        playerPoints.put(uuid, getPoints(player) + points);
    }

    public int getPoints(Player player) {
        String uuid = player.getUniqueId().toString();
        return playerPoints.getOrDefault(uuid, 0);
    }

    public void resetPoints(Player player) {
        String uuid = player.getUniqueId().toString();
        playerPoints.remove(uuid);
    }
}
```

The `@IocBean` annotation tells Tubing to manage this class as a bean. It will be:
- Automatically instantiated on plugin startup
- Available for injection into other beans
- A singleton (one instance shared across the plugin)

## Step 3: Add Configuration

Create a `config.yml` in your plugin's data folder (or it will be auto-created):

```yaml
points:
  starting-points: 100
  points-per-kill: 10
  points-per-death: -5
  enabled: true
```

Now create a service that uses these configuration values:

```java
package com.example.myplugin.services;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import org.bukkit.entity.Player;

@IocBean
public class PointsService {

    private final PlayerDataService playerDataService;

    @ConfigProperty("points.enabled")
    private boolean enabled;

    @ConfigProperty("points.starting-points")
    private int startingPoints;

    @ConfigProperty("points.points-per-kill")
    private int pointsPerKill;

    @ConfigProperty("points.points-per-death")
    private int pointsPerDeath;

    // Constructor injection - PlayerDataService is automatically provided
    public PointsService(PlayerDataService playerDataService) {
        this.playerDataService = playerDataService;
    }

    public void onJoin(Player player) {
        if (!enabled) return;

        if (playerDataService.getPoints(player) == 0) {
            playerDataService.addPoints(player, startingPoints);
        }
    }

    public void onKill(Player killer) {
        if (!enabled) return;
        playerDataService.addPoints(killer, pointsPerKill);
    }

    public void onDeath(Player victim) {
        if (!enabled) return;
        playerDataService.addPoints(victim, pointsPerDeath);
    }
}
```

Notice:
- `@ConfigProperty` injects values from `config.yml`
- Constructor parameters are automatically injected
- No manual configuration loading needed!

## Step 4: Create Event Listeners

Let's listen to player events and integrate our points system:

```java
package com.example.myplugin.listeners;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitListener;
import com.example.myplugin.services.PointsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

@IocBukkitListener
public class PlayerListener implements Listener {

    private final PointsService pointsService;

    public PlayerListener(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        pointsService.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        pointsService.onDeath(victim);

        if (killer != null) {
            pointsService.onKill(killer);
        }
    }
}
```

The `@IocBukkitListener` annotation:
- Automatically registers the listener with Bukkit
- Injects dependencies (PointsService) automatically
- No need for manual event registration!

## Step 5: Create Commands

Add a command to check and manage points:

```java
package com.example.myplugin.commands;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitCommandHandler;
import com.example.myplugin.services.PlayerDataService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@IocBukkitCommandHandler("points")
public class PointsCommand {

    private final PlayerDataService playerDataService;

    public PointsCommand(PlayerDataService playerDataService) {
        this.playerDataService = playerDataService;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        int points = playerDataService.getPoints(player);

        player.sendMessage("§aYou have §e" + points + "§a points!");
        return true;
    }
}
```

The `@IocBukkitCommandHandler` annotation:
- Automatically registers the command with Bukkit
- Injects dependencies
- No need for `plugin.yml` command entries (though you can still use it for descriptions)

## Step 6: Update plugin.yml

Create or update your `src/main/resources/plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
api-version: 1.20
author: YourName
description: A points system plugin using Tubing

commands:
  points:
    description: Check your points
    usage: /points
```

## Step 7: Build and Test

Build your plugin:

```bash
mvn clean package
```

Copy the JAR from `target/` to your server's `plugins/` folder and start the server.

You should see:
1. Plugin loads with "MyPlugin enabled with Tubing!"
2. A `config.yml` is created with your configuration
3. `/points` command is registered and working
4. Players gain/lose points on kill/death

## What Just Happened?

Let's review what Tubing did for you:

1. **Zero Boilerplate**: No manual object creation, no singleton patterns
2. **Automatic Wiring**: PointsService got PlayerDataService automatically
3. **Configuration**: Values from YAML injected directly into fields
4. **Command Registration**: Commands registered without plugin.yml setup
5. **Event Registration**: Listeners registered automatically
6. **Testability**: All classes use constructor injection, easy to unit test

## Understanding the Flow

When your plugin loads:

1. Tubing scans your package for annotated classes
2. It discovers: `PlayerDataService`, `PointsService`, `PlayerListener`, `PointsCommand`
3. It loads `config.yml` and prepares configuration values
4. It creates beans in order, resolving dependencies:
   - Creates `PlayerDataService` (no dependencies)
   - Creates `PointsService` (injects `PlayerDataService`)
   - Injects `@ConfigProperty` values into `PointsService`
   - Creates `PlayerListener` (injects `PointsService`)
   - Creates `PointsCommand` (injects `PlayerDataService`)
5. Registers listeners and commands with Bukkit
6. Calls your `enable()` method

All automatically!

## Expanding Your Plugin

Now that you have the basics, you can:

- Add more services and commands
- Use subcommands for complex command trees
- Implement multi-provider patterns for plugin APIs
- Create conditional beans for optional features
- Add configuration migrations for updates
- Build inventory GUIs with the GUI module

## Next Steps

**Next:** [Project Structure](Project-Structure.md) - Learn how to organize larger projects

---

**See also:**
- [Dependency Injection](../core/Dependency-Injection.md) - Deep dive into DI patterns
- [Configuration Injection](../core/Configuration-Injection.md) - Advanced configuration features
- [Commands](../bukkit/Commands.md) - Command handling in detail
- [Event Listeners](../bukkit/Event-Listeners.md) - Event listener patterns

## Complete Code

Here's the complete project structure for reference:

```
src/main/java/com/example/myplugin/
├── MyPlugin.java
├── commands/
│   └── PointsCommand.java
├── listeners/
│   └── PlayerListener.java
└── services/
    ├── PlayerDataService.java
    └── PointsService.java

src/main/resources/
├── plugin.yml
└── config.yml
```

That's all the code needed for a fully functional points system with commands, events, and configuration!
