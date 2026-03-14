# GUI Setup

This guide covers setting up Tubing's GUI framework for creating interactive inventory menus in Bukkit. You'll learn how to add the dependency, use the GuiActionService, create basic GUIs, and manage the GUI lifecycle.

## Overview

Tubing's GUI framework provides a powerful, declarative approach to building inventory-based user interfaces. It offers:

- **Controller-Based Architecture**: Use `@GuiController` and `@GuiAction` annotations to define GUI logic
- **Automatic Event Handling**: Click events, inventory management, and navigation handled automatically
- **Template Support**: Build dynamic GUIs using XML templates with Freemarker
- **Navigation History**: Built-in back button functionality with history stack
- **Action Routing**: URL-style action routing with query parameters
- **Exception Handling**: Centralized error handling for GUI actions
- **Async Support**: Execute long-running operations asynchronously
- **Dependency Injection**: Full IoC integration with automatic service injection

## Adding the Dependency

To use Tubing's GUI framework, add the `tubing-bukkit-gui` dependency to your project.

### Maven

Add the repository:

```xml
<repositories>
    <repository>
        <id>staffplusplus-repo</id>
        <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependencies>
    <!-- Tubing Bukkit (required base dependency) -->
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

    <!-- Tubing Bukkit GUI -->
    <dependency>
        <groupId>be.garagepoort.mcioc</groupId>
        <artifactId>tubing-bukkit-gui</artifactId>
        <version>7.5.6</version>
        <exclusions>
            <exclusion>
                <groupId>*</groupId>
                <artifactId>*</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

Update your shade plugin configuration to relocate the GUI framework:

```xml
<build>
    <plugins>
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
                                <shadedPattern>com.example.myplugin.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Important:** Replace `com.example.myplugin.tubing` with your own unique package path to avoid conflicts.

### What's Included

The `tubing-bukkit-gui` dependency includes:
- **Core GUI Framework**: Action routing, GUI controllers, and lifecycle management
- **Freemarker Templates**: For dynamic GUI generation from XML templates
- **JSoup**: For parsing XML GUI templates
- **Event Handlers**: Automatic inventory click and close event handling
- **History Management**: Built-in navigation history and back functionality

These dependencies are automatically shaded and relocated when you build your plugin.

## GuiActionService Overview

The `GuiActionService` is the central service that manages all GUI operations. It's automatically registered as an `@IocBean` and available for injection.

### Key Responsibilities

**Action Registration:**
- Discovers `@GuiController` classes on plugin startup
- Registers all `@GuiAction` methods as action routes
- Validates action uniqueness and method signatures

**GUI Display:**
- Opens GUIs for players with `showGui()`
- Maps `TubingGui` objects to Bukkit inventories
- Tracks which GUI each player currently has open

**Action Execution:**
- Routes action strings to appropriate controller methods
- Parses query parameters from action strings
- Injects method parameters automatically
- Handles return types (TubingGui, GuiTemplate, String redirects, etc.)

**Navigation:**
- Maintains history stack for each player
- Handles "BACK" actions automatically
- Clears history on GUI close

**Lifecycle Management:**
- Removes GUI references when inventories close
- Handles GUI transitions seamlessly
- Manages async GUI operations

**Exception Handling:**
- Routes exceptions to registered `GuiExceptionHandler` implementations
- Closes GUIs and clears history on errors
- Provides centralized error handling

### Injecting GuiActionService

You can inject `GuiActionService` into any bean:

```java
@IocBean
public class MenuService {

    private final GuiActionService guiActionService;

    public MenuService(GuiActionService guiActionService) {
        this.guiActionService = guiActionService;
    }

    public void openMainMenu(Player player) {
        // Use the service to execute actions or open GUIs
        guiActionService.executeAction(player, "main-menu");
    }
}
```

The service is automatically available - you don't need to register or configure it.

## Basic GUI Creation Workflow

Creating a GUI in Tubing follows a simple pattern:

1. **Create a GUI Controller** - Define a class with `@GuiController`
2. **Add GUI Actions** - Create methods with `@GuiAction` that return GUIs
3. **Build TubingGui Objects** - Use the builder pattern to construct GUIs
4. **Open for Players** - GUIs are automatically opened when actions execute

### Step 1: Create a GUI Controller

```java
package com.example.myplugin.gui;

import be.garagepoort.mcioc.tubinggui.GuiController;
import be.garagepoort.mcioc.tubinggui.GuiAction;
import be.garagepoort.mcioc.tubinggui.model.TubingGui;
import org.bukkit.entity.Player;

@GuiController
public class MainMenuController {

    @GuiAction("main-menu")
    public TubingGui showMainMenu(Player player) {
        // Build and return a GUI
        return new TubingGui.Builder("Main Menu", 27)
            .build();
    }
}
```

**What happens:**
- `@GuiController` marks this class as a GUI controller
- The class is automatically discovered during plugin startup
- `@GuiAction("main-menu")` registers the method as an action route
- When "main-menu" action executes, this method is called
- The returned `TubingGui` is automatically opened for the player

### Step 2: Build a GUI with Items

```java
import be.garagepoort.mcioc.tubinggui.model.TubingGuiItem;
import be.garagepoort.mcioc.tubinggui.model.TubingGuiItemStack;
import be.garagepoort.mcioc.tubinggui.model.TubingGuiText;
import be.garagepoort.mcioc.tubinggui.model.TubingGuiTextPart;
import org.bukkit.Material;

@GuiAction("main-menu")
public TubingGui showMainMenu(Player player) {
    // Create item text
    TubingGuiText itemName = new TubingGuiText();
    itemName.addPart(new TubingGuiTextPart("&aSettings", null));

    // Create item stack
    TubingGuiItemStack itemStack = new TubingGuiItemStack(
        1,                    // amount
        Material.CHEST,       // material
        itemName,            // display name
        false,               // glow effect
        new ArrayList<>()    // lore lines
    );

    // Create GUI item with click action
    TubingGuiItem settingsItem = new TubingGuiItem.Builder(null, 13)
        .withItemStack(itemStack)
        .withLeftClickAction("settings-menu")
        .build();

    // Build GUI
    return new TubingGui.Builder("Main Menu", 27)
        .addItem(settingsItem)
        .build();
}
```

**Components:**
- **TubingGuiText**: Colored, formatted text for titles and item names
- **TubingGuiItemStack**: Represents a Minecraft item with metadata
- **TubingGuiItem**: An item in a GUI slot with click actions
- **TubingGui**: The complete GUI with title, size, and items

### Step 3: Add Navigation

```java
@GuiAction("settings-menu")
public TubingGui showSettings(Player player) {
    // Create back button
    TubingGuiText backText = new TubingGuiText();
    backText.addPart(new TubingGuiTextPart("&cBack", null));

    TubingGuiItemStack backStack = new TubingGuiItemStack(
        1,
        Material.ARROW,
        backText,
        false,
        new ArrayList<>()
    );

    TubingGuiItem backItem = new TubingGuiItem.Builder(null, 18)
        .withItemStack(backStack)
        .withLeftClickAction("BACK")  // Special action
        .build();

    return new TubingGui.Builder("Settings", 27)
        .addItem(backItem)
        .build();
}
```

**Special Actions:**
- `"BACK"`: Returns to the previous GUI in the history stack
- `"NOOP"`: No operation, click does nothing
- `"CLOSE"`: Closes the GUI

## Opening GUIs for Players

There are several ways to open GUIs for players:

### 1. Execute an Action

The most common approach - let the action system handle it:

```java
@IocBean
public class MenuService {
    private final GuiActionService guiActionService;

    public MenuService(GuiActionService guiActionService) {
        this.guiActionService = guiActionService;
    }

    public void openMainMenu(Player player) {
        guiActionService.executeAction(player, "main-menu");
    }
}
```

**Benefits:**
- Automatic history tracking
- Parameter parsing support
- Exception handling included
- Consistent with GUI navigation

### 2. Direct GUI Display

For programmatically created GUIs without actions:

```java
public void showCustomGui(Player player) {
    TubingGui gui = new TubingGui.Builder("Custom", 27)
        .addItem(/* ... */)
        .build();

    guiActionService.showGui(player, gui);
}
```

**Use cases:**
- Dynamic GUIs not tied to actions
- Generated GUIs from external data
- One-off GUI displays

### 3. From Commands

Open GUIs from command handlers:

```java
@IocBukkitCommandHandler("menu")
public class MenuCommand extends AbstractCmd {
    private final GuiActionService guiActionService;

    public MenuCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService,
                      GuiActionService guiActionService) {
        super(exceptionHandler, permissionService);
        this.guiActionService = guiActionService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;
        guiActionService.executeAction(player, "main-menu");
        return true;
    }
}
```

### 4. From Event Listeners

Open GUIs in response to events:

```java
@IocBukkitListener
public class PlayerJoinListener implements Listener {
    private final GuiActionService guiActionService;

    public PlayerJoinListener(GuiActionService guiActionService) {
        this.guiActionService = guiActionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Open welcome GUI after 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            guiActionService.executeAction(player, "welcome-gui");
        }, 1L);
    }
}
```

**Note:** Always open GUIs at least 1 tick after the event for proper initialization.

## GUI Lifecycle and Cleanup

Understanding the GUI lifecycle helps you manage resources and handle edge cases properly.

### Lifecycle Stages

**1. Action Execution**
```
Player triggers action → GuiActionService.executeAction()
```
- Action string is parsed
- Route is matched to a controller method
- Method parameters are injected and method is called

**2. GUI Construction**
```
Controller method executes → Returns TubingGui object
```
- Your controller builds and returns a GUI
- Return type determines what happens next

**3. GUI Display**
```
TubingGui is converted → Bukkit Inventory is created → Opened for player
```
- `TubingGui` is mapped to a Bukkit `Inventory`
- Previous inventory is closed
- New inventory is opened
- GUI is stored in `GuiActionService` tracking map
- Action is added to player's history stack

**4. Player Interaction**
```
Player clicks item → InventoryClick listener captures → Action executed
```
- Click events are handled by `InventoryClick` listener
- Interactable slots are allowed, others are cancelled
- Click type determines which action executes (left/right/middle/shift)
- Action is executed, potentially opening a new GUI

**5. GUI Close**
```
Player closes inventory → InventoryClose listener captures → Cleanup
```
- `InventoryClose` listener detects close event
- Close action is executed if defined
- History stack is cleared
- GUI reference is removed from tracking map

### Automatic Cleanup

Cleanup happens automatically in these scenarios:

**Inventory Close:**
```java
// When player closes inventory:
// 1. InventoryClose event fires
// 2. GuiActionService.removeInventory() is called
// 3. History stack is cleared
// 4. No memory leaks
```

**Action Returns Null:**
```java
@GuiAction("close-menu")
public TubingGui closeMenu(Player player) {
    // Save player data or cleanup
    playerService.saveData(player);

    // Returning null closes the GUI
    return null;
}
```

**Exception Thrown:**
```java
// If an exception occurs in a GUI action:
// 1. Exception handler is invoked (if registered)
// 2. Inventory is closed
// 3. GUI reference is removed
// 4. History is cleared
```

**Server Reload:**
```java
// On plugin reload/disable:
// - Open inventories remain open
// - New plugin instance has empty tracking maps
// - Player closing inventory won't trigger cleanup in old plugin
// - This is generally fine - no resource leaks
```

### Manual Cleanup

You rarely need manual cleanup, but it's available:

```java
@IocBean
public class GuiManager {
    private final GuiActionService guiActionService;

    public GuiManager(GuiActionService guiActionService) {
        this.guiActionService = guiActionService;
    }

    public void closePlayerGui(Player player) {
        // Get current GUI
        Optional<TubingGui> currentGui = guiActionService.getTubingGui(player);

        if (currentGui.isPresent()) {
            // Close inventory
            player.closeInventory();

            // Remove reference (happens automatically, but shown for clarity)
            guiActionService.removeInventory(player);
        }
    }

    public boolean hasOpenGui(Player player) {
        return guiActionService.getTubingGui(player).isPresent();
    }
}
```

### Close Actions

Define what happens when a player closes a GUI:

```java
@GuiAction("confirmation-dialog")
public TubingGui showConfirmation(Player player) {
    return new TubingGui.Builder("Confirm Action", 27)
        .addItem(confirmButton)
        .addItem(cancelButton)
        .closeAction("cancel-action")  // Executed on close
        .build();
}

@GuiAction("cancel-action")
public TubingGui handleCancel(Player player) {
    player.sendMessage("&cAction cancelled");
    return null;  // Close GUI
}
```

**Use cases for close actions:**
- Tracking whether player completed a flow
- Cancelling pending operations
- Returning to a previous menu
- Saving partial state

## Best Practices

### 1. Use Action Routes Consistently

**Bad - Direct GUI creation everywhere:**
```java
public void showMenu(Player player) {
    TubingGui gui = createComplexGui();
    guiActionService.showGui(player, gui);
}

public void showOtherMenu(Player player) {
    TubingGui gui = createOtherGui();
    guiActionService.showGui(player, gui);
}
```

**Good - Use actions for navigation:**
```java
@GuiController
public class MenuController {
    @GuiAction("main-menu")
    public TubingGui showMainMenu(Player player) {
        // ...
    }

    @GuiAction("settings")
    public TubingGui showSettings(Player player) {
        // ...
    }
}

// From services:
guiActionService.executeAction(player, "main-menu");
```

### 2. Separate GUI Building Logic

**Bad - Everything in the action method:**
```java
@GuiAction("player-list")
public TubingGui showPlayers(Player player) {
    List<Player> players = Bukkit.getOnlinePlayers();
    TubingGui gui = new TubingGui.Builder("Players", 54);

    int slot = 0;
    for (Player p : players) {
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart(p.getName(), null));
        TubingGuiItemStack stack = new TubingGuiItemStack(/*...*/);
        TubingGuiItem item = new TubingGuiItem.Builder(null, slot++)
            .withItemStack(stack)
            .withLeftClickAction("view-player?name=" + p.getName())
            .build();
        gui.addItem(item);
    }

    return gui.build();
}
```

**Good - Use service classes:**
```java
@IocBean
public class PlayerGuiBuilder {
    public TubingGui buildPlayerList(Player viewer) {
        TubingGui.Builder builder = new TubingGui.Builder("Players", 54);

        int slot = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.addItem(createPlayerItem(player, slot++));
        }

        return builder.build();
    }

    private TubingGuiItem createPlayerItem(Player player, int slot) {
        // Item creation logic
    }
}

@GuiController
public class PlayerGuiController {
    private final PlayerGuiBuilder guiBuilder;

    public PlayerGuiController(PlayerGuiBuilder guiBuilder) {
        this.guiBuilder = guiBuilder;
    }

    @GuiAction("player-list")
    public TubingGui showPlayers(Player player) {
        return guiBuilder.buildPlayerList(player);
    }
}
```

### 3. Use Templates for Complex GUIs

Instead of building GUIs in code, use XML templates with Freemarker:

```java
@GuiAction("shop")
public GuiTemplate showShop(Player player) {
    Map<String, Object> params = new HashMap<>();
    params.put("items", shopService.getItems());
    params.put("balance", economyService.getBalance(player));

    return GuiTemplate.template("shop/main.xml", params);
}
```

See the GUI Templates documentation for details.

### 4. Handle Edge Cases

**Check GUI state:**
```java
public void refreshPlayerGui(Player player) {
    Optional<TubingGui> currentGui = guiActionService.getTubingGui(player);

    if (currentGui.isPresent()) {
        // Re-execute current action to refresh
        // (Advanced - requires storing current action)
    }
}
```

**Prevent double-opens:**
```java
@GuiAction("expensive-gui")
public TubingGui showExpensiveData(Player player) {
    // Check if already loading
    if (isLoading.contains(player.getUniqueId())) {
        return GuiActionReturnType.KEEP_OPEN;
    }

    isLoading.add(player.getUniqueId());
    try {
        // Load data
        return buildGui();
    } finally {
        isLoading.remove(player.getUniqueId());
    }
}
```

### 5. Close GUIs on Logout

GUIs are automatically closed when players disconnect, but you may want custom cleanup:

```java
@IocBukkitListener
public class GuiCleanupListener implements Listener {
    private final GuiActionService guiActionService;
    private final TransactionService transactionService;

    public GuiCleanupListener(GuiActionService guiActionService,
                             TransactionService transactionService) {
        this.guiActionService = guiActionService;
        this.transactionService = transactionService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if player had a GUI open
        Optional<TubingGui> gui = guiActionService.getTubingGui(player);

        if (gui.isPresent()) {
            // Cancel pending transactions
            transactionService.cancelPending(player);

            // Cleanup is automatic, but shown for clarity
            guiActionService.removeInventory(player);
        }
    }
}
```

### 6. Test GUI Controllers

GUI controllers use dependency injection, making them testable:

```java
@Test
public void testMainMenu() {
    // Mock dependencies
    ShopService mockShop = mock(ShopService.class);
    when(mockShop.getItems()).thenReturn(testItems);

    // Create controller
    ShopController controller = new ShopController(mockShop);

    // Test action
    Player player = mock(Player.class);
    TubingGui gui = controller.showShop(player);

    // Verify GUI structure
    assertNotNull(gui);
    assertEquals(54, gui.getSize());
    assertEquals("Shop", gui.getTitle().getText());
}
```

### 7. Use Return Types Appropriately

```java
// Return null to close
@GuiAction("logout")
public TubingGui logout(Player player) {
    player.sendMessage("Goodbye!");
    return null;
}

// Return GuiActionReturnType to control behavior
@GuiAction("toggle-setting")
public GuiActionReturnType toggleSetting(Player player) {
    settingService.toggle(player);
    return GuiActionReturnType.KEEP_OPEN;  // Don't close or navigate
}

// Return action string to redirect
@GuiAction("expired-offer")
public String checkOffer(Player player) {
    if (!offerService.isValid()) {
        return "main-menu";  // Redirect to main menu
    }
    return "offer-details";
}

// Return GuiActionReturnType.BACK
@GuiAction("cancel")
public GuiActionReturnType cancel(Player player) {
    return GuiActionReturnType.BACK;  // Same as "BACK" action
}
```

## Next Steps

Now that you understand GUI setup and basic usage:

- **GUI Controllers** - Learn about action routing and parameters
- **GUI Templates** - Build dynamic GUIs with XML and Freemarker
- **GUI Items and Styling** - Advanced item configuration and styling
- **Async GUIs** - Handle long-running operations
- **Exception Handling** - Custom error handling for GUIs

---

**See also:**
- [Bukkit Setup](../bukkit/Bukkit-Setup.md) - Set up your Tubing plugin
- [Commands](../bukkit/Commands.md) - Command handling patterns
- [IoC Container](../core/IoC-Container.md) - Dependency injection fundamentals
