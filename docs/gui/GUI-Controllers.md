# GUI Controllers

GUI Controllers are the heart of the Tubing GUI framework. They implement an MVC (Model-View-Controller) pattern where controllers handle user interactions, process business logic, and return views (GUIs or templates) to display. This guide covers everything you need to know about creating and working with GUI controllers.

## Overview

The Tubing GUI controller system provides:

- **Automatic Registration**: Controllers are discovered and registered automatically
- **Action-Based Routing**: Map GUI interactions to specific controller methods
- **Parameter Binding**: Automatic extraction and type conversion of action parameters
- **Dependency Injection**: Controllers receive dependencies via constructor injection
- **Multiple Return Types**: Return GUIs, templates, redirect to other actions, or close the GUI
- **Async Support**: Execute long-running operations asynchronously without blocking
- **Exception Handling**: Centralized error handling with custom exception handlers
- **Navigation History**: Built-in back navigation support

## Basic Controller

### Creating a Controller

The simplest way to create a GUI controller is to annotate a class with `@GuiController`:

```java
@GuiController
public class PlayerMenuController {

    private final PlayerService playerService;

    // Dependencies injected via constructor
    public PlayerMenuController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GuiAction("player:menu")
    public TubingGui showMenu(Player player) {
        return new TubingGui.Builder("Player Menu", 27)
            .addItem(new TubingGuiItem.Builder(null, 10)
                .withLeftClickAction("player:stats")
                .withItemStack(/* ... */)
                .build())
            .build();
    }

    @GuiAction("player:stats")
    public TubingGui showStats(Player player) {
        PlayerStats stats = playerService.getStats(player);
        // Build and return GUI with stats
        return new TubingGui.Builder("Your Stats", 27)
            .addItem(/* ... */)
            .build();
    }
}
```

**Key Points:**
- The `@GuiController` annotation marks a class as a GUI controller
- Controllers are automatically registered when your plugin loads
- Use `@GuiAction` to map action identifiers to methods
- Action identifiers typically use a namespace:action format (e.g., "player:menu")
- Dependencies are injected through constructor parameters

## @GuiController Annotation

The `@GuiController` annotation marks a class as a GUI controller and configures its behavior.

### Annotation Attributes

```java
@GuiController(
    conditionalOnProperty = "features.player-menu",  // Register only if property is set (optional)
    priority = false,                                 // Load with priority (default: false)
    multiproviderClass = Object.class                 // Multi-provider class (advanced, default: Object.class)
)
public class PlayerMenuController {
    // ...
}
```

### Attributes Explained

#### conditionalOnProperty

Register the controller only if a specific configuration property is set:

```java
@GuiController(conditionalOnProperty = "features.advanced-menus")
public class AdvancedMenuController {
    // Only registered if "features.advanced-menus" is set in configuration
}
```

This is useful for feature flags or optional functionality.

#### priority

Controls the loading order of controllers:

```java
@GuiController(priority = true)
public class CoreMenuController {
    // Loaded with priority
}
```

Use this when you need controllers to be registered early in the loading process.

#### multiproviderClass

Advanced feature for multi-implementation support. Typically left at default value.

## @GuiAction Annotation

The `@GuiAction` annotation maps an action identifier to a controller method. When a player clicks an item in a GUI, the framework executes the corresponding action.

### Basic Usage

```java
@GuiAction("shop:open")
public TubingGui openShop(Player player) {
    // Build and return shop GUI
}
```

### Annotation Attributes

```java
@GuiAction(
    value = "shop:purchase",      // Action identifier (required)
    overrideHistory = true,       // Override history entry (default: true)
    skipHistory = false           // Skip adding to history (default: false)
)
public TubingGui purchaseItem(Player player, @GuiParam("item") String itemId) {
    // Handle purchase
}
```

### Attributes Explained

#### value (required)

The unique action identifier. This is what you reference in GUI item definitions:

```java
// In controller
@GuiAction("shop:category")
public TubingGui showCategory(Player player) { /* ... */ }

// In GUI template or builder
.withLeftClickAction("shop:category")
```

**Best Practices:**
- Use a consistent naming convention (e.g., "namespace:action")
- Use lowercase with hyphens for multi-word actions (e.g., "shop:view-history")
- Keep action names descriptive and semantic

#### overrideHistory

Controls whether this action replaces the last entry in the navigation history:

```java
@GuiAction(value = "shop:page", overrideHistory = true)
public TubingGui changePage(Player player, @GuiParam("page") int page) {
    // Pagination doesn't add multiple history entries
    // Each page change replaces the previous page in history
}
```

Set to `false` if each action should be a separate history entry.

#### skipHistory

Skip adding this action to the navigation history entirely:

```java
@GuiAction(value = "shop:refresh", skipHistory = true)
public TubingGui refresh(Player player) {
    // Refresh doesn't affect navigation history
    // Back button returns to previous action before refresh
}
```

Useful for refresh actions, filters, or sorting that shouldn't affect navigation.

## Controller Method Signatures

Controller methods can accept various parameter types that are automatically resolved and injected by the framework.

### Supported Parameter Types

#### Player (Automatic)

The player who triggered the action is automatically injected:

```java
@GuiAction("menu:open")
public TubingGui openMenu(Player player) {
    player.sendMessage("Opening menu...");
    return buildMenu();
}
```

**Important**: No annotation needed - any `Player` parameter is automatically injected.

#### @GuiParam - Action Parameters

Extract parameters from the action query string:

```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String itemId,
                         @GuiParam("quantity") int quantity) {
    // Action query: "shop:buy?item=diamond_sword&quantity=5"
    // itemId = "diamond_sword"
    // quantity = 5 (automatically converted to int)
}
```

**Supported Types:**
- `String` (default)
- `int` / `Integer`
- `long` / `Long`
- `boolean` / `Boolean`
- `float` / `Float`
- `double` / `Double`
- `byte` / `Byte`
- `short` / `Short`

**Default Values:**

```java
@GuiParam(value = "page", defaultValue = "1")
int page
// If "page" parameter is missing, defaults to 1
```

#### @GuiParams - All Parameters

Get all parameters as a map:

```java
@GuiAction("filter:apply")
public TubingGui applyFilter(Player player,
                             @GuiParams Map<String, String> params) {
    // params contains all query parameters
    String category = params.get("category");
    String sortBy = params.get("sortBy");
    // ...
}
```

#### @CurrentAction - Full Action Query

Get the complete action query string:

```java
@GuiAction("history:view")
public TubingGui viewHistory(Player player,
                             @CurrentAction String currentAction) {
    // currentAction = "history:view?page=2&filter=recent"
    // Useful for building back buttons or saving state
}
```

#### @InteractableItems - Inventory Items

Get items from interactable slots in the current GUI:

```java
@GuiAction("auction:create")
public TubingGui createAuction(Player player,
                               @InteractableItems List<ItemStack> items) {
    // items contains all items placed in interactable slots
    if (items.isEmpty()) {
        player.sendMessage("Please place items to auction");
        return GuiActionReturnType.KEEP_OPEN;
    }
    // Process auction creation
}
```

This is useful for GUIs where players can place items (e.g., trading, crafting, auctions).

### Parameter Order

Parameters can appear in any order. The framework matches them by type and annotation:

```java
@GuiAction("trade:confirm")
public TubingGui confirmTrade(@GuiParam("target") String targetPlayer,
                              @InteractableItems List<ItemStack> items,
                              Player player,
                              @CurrentAction String action) {
    // Order doesn't matter - framework injects correctly
}
```

## Action Parameter Binding

Action parameters are passed through query strings appended to action identifiers.

### Basic Parameter Syntax

```java
// In GUI item definition
.withLeftClickAction("shop:buy?item=diamond_sword&price=100")

// In controller
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String item,
                         @GuiParam("price") int price) {
    // item = "diamond_sword"
    // price = 100 (auto-converted to int)
}
```

### Dynamic Parameters in Templates

When using Freemarker templates, you can dynamically build action strings:

```xml
<item slot="10"
      leftClickAction="shop:buy?item=${item.id}&price=${item.price}">
    <!-- ... -->
</item>
```

### Type Conversion

The framework automatically converts string parameters to the appropriate types:

```java
@GuiParam("amount") int amount        // "42" → 42
@GuiParam("enabled") boolean enabled  // "true" → true
@GuiParam("price") double price       // "19.99" → 19.99
```

**Error Handling**: If conversion fails (e.g., "abc" to int), a `NumberFormatException` is thrown and can be handled by exception handlers.

### Missing Parameters

If a required parameter is missing and no default value is provided, the parameter will be `null` (for objects) or the default primitive value (0, false, etc.):

```java
@GuiAction("shop:filter")
public TubingGui filter(Player player,
                       @GuiParam(value = "category", defaultValue = "all") String category,
                       @GuiParam("minPrice") Integer minPrice) {
    // category = "all" if not provided
    // minPrice = null if not provided (use Integer, not int)
}
```

**Best Practice**: Use wrapper types (Integer, Boolean) instead of primitives when parameters are optional, so you can check for `null`.

## Return Types

Controller methods support multiple return types to control what happens after the action is processed.

### TubingGui - Show a GUI

Return a `TubingGui` to display a new GUI to the player:

```java
@GuiAction("menu:main")
public TubingGui showMainMenu(Player player) {
    return new TubingGui.Builder("Main Menu", 27)
        .addItem(/* ... */)
        .build();
}
```

This is the most common return type. The returned GUI replaces the current one.

### GuiTemplate - Render a Template

Return a `GuiTemplate` to render a Freemarker template:

```java
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player) {
    List<ShopItem> items = shopService.getItems();
    Map<String, Object> params = new HashMap<>();
    params.put("items", items);
    params.put("player", player);

    return GuiTemplate.template("shop-browse", params);
}
```

The template is resolved from your template directory and rendered with the provided parameters.

### ChatTemplate - Send Chat Messages

Return a `ChatTemplate` to send formatted chat messages:

```java
@GuiAction("help:command")
public ChatTemplate showHelp(Player player) {
    Map<String, Object> params = new HashMap<>();
    params.put("command", "shop");

    return ChatTemplate.template("help-message", params);
}
```

This closes the GUI and sends the rendered chat template.

### String - Redirect to Another Action

Return a string to redirect to another action:

```java
@GuiAction("shop:cancel")
public String cancelPurchase(Player player) {
    // Clean up transaction
    return "shop:main"; // Redirect to main shop menu
}
```

The string should be a valid action identifier (with optional parameters).

### void (null) - Close GUI

Return `null` or use a `void` method to close the GUI:

```java
@GuiAction("menu:exit")
public void exitMenu(Player player) {
    player.sendMessage("Thanks for visiting!");
    // GUI closes automatically
}
```

### GuiActionReturnType - Control Behavior

Return `GuiActionReturnType` enum values for special behaviors:

```java
@GuiAction("filter:toggle")
public GuiActionReturnType toggleFilter(Player player,
                                        @GuiParam("filter") String filter) {
    // Update filter state
    return GuiActionReturnType.KEEP_OPEN; // Don't close or replace GUI
}
```

**Available Values:**
- `KEEP_OPEN` - Don't change the current GUI
- `CLOSE` - Close the GUI and clear history
- `BACK` - Navigate back to previous GUI in history

### AsyncGui - Async Execution

Wrap any return type in `AsyncGui` for async execution:

```java
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData(Player player, @GuiParam("id") String id) {
    return AsyncGui.async(() -> {
        // Runs asynchronously off the main thread
        DataModel data = database.loadData(id);

        // Build GUI with loaded data
        return new TubingGui.Builder("Data View", 27)
            .addItem(/* ... */)
            .build();
    });
}
```

**Key Points:**
- The lambda runs on an async thread
- The return value is processed on the main thread
- Useful for database queries, file I/O, or HTTP requests
- Don't access Bukkit API directly in the async block

## Dependency Injection in Controllers

Controllers support full dependency injection through constructor parameters, just like any other IoC bean.

### Basic Injection

```java
@GuiController
public class ShopController {

    private final ShopService shopService;
    private final EconomyService economyService;
    private final MessageService messageService;

    public ShopController(ShopService shopService,
                         EconomyService economyService,
                         MessageService messageService) {
        this.shopService = shopService;
        this.economyService = economyService;
        this.messageService = messageService;
    }

    @GuiAction("shop:purchase")
    public TubingGui purchase(Player player, @GuiParam("item") String itemId) {
        ShopItem item = shopService.getItem(itemId);
        if (economyService.withdraw(player, item.getPrice())) {
            player.getInventory().addItem(item.getItemStack());
            messageService.send(player, "Purchase successful!");
        }
        return buildShopGui(player);
    }
}
```

### Configuration Injection

Inject configuration values directly:

```java
@GuiController
public class ShopController {

    private final ShopService shopService;

    @ConfigProperty("shop.tax-rate")
    private double taxRate;

    @ConfigProperty("shop.currency-symbol")
    private String currencySymbol;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GuiAction("shop:checkout")
    public TubingGui checkout(Player player) {
        double total = calculateTotal(cart);
        double tax = total * taxRate;

        player.sendMessage("Total: " + currencySymbol + (total + tax));
        // ...
    }
}
```

### Plugin Instance Injection

Inject the plugin instance if needed:

```java
@GuiController
public class AdminController {

    private final TubingPlugin plugin;

    public AdminController(@InjectTubingPlugin TubingPlugin plugin) {
        this.plugin = plugin;
    }

    @GuiAction("admin:reload")
    public void reloadPlugin(Player player) {
        plugin.reload();
        player.sendMessage("Plugin reloaded!");
    }
}
```

### Multi-Provider Injection

Inject all implementations of an interface:

```java
@GuiController
public class IntegrationMenuController {

    private final List<ThirdPartyIntegration> integrations;

    public IntegrationMenuController(
            @IocMulti(ThirdPartyIntegration.class)
            List<ThirdPartyIntegration> integrations) {
        this.integrations = integrations;
    }

    @GuiAction("integrations:list")
    public TubingGui listIntegrations(Player player) {
        TubingGui.Builder builder = new TubingGui.Builder("Integrations", 27);

        for (int i = 0; i < integrations.size(); i++) {
            ThirdPartyIntegration integration = integrations.get(i);
            builder.addItem(/* build item for integration */);
        }

        return builder.build();
    }
}
```

## Navigation and History

The GUI framework maintains a navigation history stack for each player, enabling back navigation.

### Automatic History Management

By default, every action is added to the navigation history:

```java
// Player flow: main → shop → category → item
// History stack: [main, shop, category, item]
// Back button returns to: category → shop → main
```

### Back Navigation

Use the special `TubingGuiActions.BACK` action to navigate backwards:

```java
// In GUI builder
.withLeftClickAction(TubingGuiActions.BACK)

// Or in template
<item slot="0" leftClickAction="BACK">
    <itemStack material="ARROW" name="&cBack"/>
</item>
```

When the player clicks back:
1. The last action is popped from the history stack
2. The previous action is executed
3. If history is empty, the GUI closes

### Controlling History

Use `@GuiAction` attributes to control history behavior:

```java
// Pagination replaces history entry (prevents multiple entries per page)
@GuiAction(value = "shop:page", overrideHistory = true)
public TubingGui changePage(Player player, @GuiParam("page") int page) {
    // Each page change replaces previous page in history
}

// Refresh doesn't add to history
@GuiAction(value = "shop:refresh", skipHistory = true)
public TubingGui refresh(Player player) {
    // Back button ignores this action
}
```

### Manual Back Navigation

Return `GuiActionReturnType.BACK` to trigger back navigation programmatically:

```java
@GuiAction("purchase:confirm")
public GuiActionReturnType confirmPurchase(Player player,
                                           @GuiParam("item") String itemId) {
    if (processTransaction(player, itemId)) {
        player.sendMessage("Purchase successful!");
        return GuiActionReturnType.BACK; // Return to previous GUI
    }
    return GuiActionReturnType.KEEP_OPEN; // Stay on confirmation screen
}
```

### History Clearing

History is automatically cleared when:
- The GUI is closed manually by the player
- An action returns `null` (closes GUI)
- An exception is thrown and handled

## Best Practices

### 1. Use Consistent Action Naming

```java
// Good - namespace:action format
@GuiAction("shop:browse")
@GuiAction("shop:purchase")
@GuiAction("shop:confirm")

// Bad - inconsistent naming
@GuiAction("browseShop")
@GuiAction("purchase_item")
@GuiAction("Shop-Confirm")
```

### 2. Keep Controllers Focused

Each controller should handle a specific feature area:

```java
// Good - focused on shop functionality
@GuiController
public class ShopController {
    @GuiAction("shop:browse") public TubingGui browse() { }
    @GuiAction("shop:purchase") public TubingGui purchase() { }
    @GuiAction("shop:history") public TubingGui history() { }
}

// Bad - mixing unrelated features
@GuiController
public class MixedController {
    @GuiAction("shop:browse") public TubingGui shop() { }
    @GuiAction("profile:view") public TubingGui profile() { }
    @GuiAction("settings:edit") public TubingGui settings() { }
}
```

### 3. Validate Input Early

```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String itemId,
                         @GuiParam("quantity") int quantity) {
    // Validate parameters
    if (itemId == null || itemId.isEmpty()) {
        player.sendMessage("Invalid item!");
        return GuiActionReturnType.KEEP_OPEN;
    }

    if (quantity <= 0 || quantity > 64) {
        player.sendMessage("Invalid quantity!");
        return GuiActionReturnType.KEEP_OPEN;
    }

    // Process purchase
    // ...
}
```

### 4. Use Templates for Complex GUIs

```java
// Good - use templates for dynamic content
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player, @GuiParam("category") String category) {
    List<ShopItem> items = shopService.getItemsByCategory(category);
    Map<String, Object> params = new HashMap<>();
    params.put("items", items);
    params.put("category", category);
    return GuiTemplate.template("shop-browse", params);
}

// Bad - building complex GUIs in code
@GuiAction("shop:browse")
public TubingGui browseShop(Player player, @GuiParam("category") String category) {
    TubingGui.Builder builder = new TubingGui.Builder("Shop", 54);
    // 100+ lines of item building code...
    return builder.build();
}
```

### 5. Use Async for Blocking Operations

```java
// Good - async for database operations
@GuiAction("stats:view")
public AsyncGui<TubingGui> viewStats(Player player) {
    return AsyncGui.async(() -> {
        PlayerStats stats = database.loadStats(player.getUniqueId());
        return buildStatsGui(stats);
    });
}

// Bad - blocking the main thread
@GuiAction("stats:view")
public TubingGui viewStats(Player player) {
    PlayerStats stats = database.loadStats(player.getUniqueId()); // Blocks!
    return buildStatsGui(stats);
}
```

### 6. Handle Permissions in Controllers

```java
@GuiController
public class AdminMenuController {

    @GuiAction("admin:panel")
    public TubingGui adminPanel(Player player) {
        // Check permissions
        if (!player.hasPermission("myplugin.admin")) {
            player.sendMessage("You don't have permission!");
            return null; // Close GUI
        }

        return buildAdminPanel();
    }
}
```

### 7. Use Service Layer for Business Logic

```java
// Good - controller delegates to service
@GuiController
public class ShopController {
    private final ShopService shopService;

    @GuiAction("shop:purchase")
    public TubingGui purchase(Player player, @GuiParam("item") String itemId) {
        PurchaseResult result = shopService.processPurchase(player, itemId);
        if (result.isSuccess()) {
            return buildSuccessGui(result);
        } else {
            return buildFailureGui(result.getError());
        }
    }
}

// Bad - business logic in controller
@GuiController
public class ShopController {
    @GuiAction("shop:purchase")
    public TubingGui purchase(Player player, @GuiParam("item") String itemId) {
        // 50+ lines of business logic...
        double balance = getBalance(player);
        if (balance < price) { /* ... */ }
        if (!hasSpace(player)) { /* ... */ }
        // ...
    }
}
```

### 8. Provide Meaningful Feedback

```java
@GuiAction("trade:accept")
public TubingGui acceptTrade(Player player, @GuiParam("target") String targetName) {
    Player target = Bukkit.getPlayer(targetName);

    if (target == null) {
        player.sendMessage("Player not found!");
        return GuiActionReturnType.BACK;
    }

    if (tradeService.acceptTrade(player, target)) {
        player.sendMessage("Trade completed successfully!");
        target.sendMessage("Trade completed successfully!");
    } else {
        player.sendMessage("Trade failed - please try again");
    }

    return null; // Close GUI
}
```

## Common Patterns

### Pagination

```java
@GuiAction(value = "shop:page", overrideHistory = true)
public GuiTemplate showPage(Player player,
                           @GuiParam(value = "page", defaultValue = "1") int page,
                           @GuiParam(value = "category", defaultValue = "all") String category) {

    List<ShopItem> items = shopService.getItems(category);
    int itemsPerPage = 45;
    int totalPages = (int) Math.ceil(items.size() / (double) itemsPerPage);

    Map<String, Object> params = new HashMap<>();
    params.put("items", paginateItems(items, page, itemsPerPage));
    params.put("page", page);
    params.put("totalPages", totalPages);
    params.put("hasNext", page < totalPages);
    params.put("hasPrevious", page > 1);
    params.put("category", category);

    return GuiTemplate.template("shop-page", params);
}
```

### Confirmation Dialogs

```java
@GuiAction("item:delete")
public TubingGui confirmDelete(Player player, @GuiParam("item") String itemId) {
    return new TubingGui.Builder("Confirm Delete", 27)
        .addItem(new TubingGuiItem.Builder(null, 11)
            .withLeftClickAction("item:delete-confirm?item=" + itemId)
            .withItemStack(/* green wool - confirm */)
            .build())
        .addItem(new TubingGuiItem.Builder(null, 15)
            .withLeftClickAction(TubingGuiActions.BACK)
            .withItemStack(/* red wool - cancel */)
            .build())
        .build();
}

@GuiAction("item:delete-confirm")
public GuiActionReturnType confirmDelete(Player player, @GuiParam("item") String itemId) {
    itemService.deleteItem(itemId);
    player.sendMessage("Item deleted!");
    return GuiActionReturnType.BACK; // Return to previous menu
}
```

### Search/Filter

```java
@GuiAction("shop:filter")
public GuiTemplate filterShop(Player player,
                             @GuiParams Map<String, String> filters) {

    List<ShopItem> items = shopService.getItems();

    // Apply filters
    if (filters.containsKey("category")) {
        items = filterByCategory(items, filters.get("category"));
    }
    if (filters.containsKey("minPrice")) {
        items = filterByMinPrice(items, Integer.parseInt(filters.get("minPrice")));
    }
    if (filters.containsKey("maxPrice")) {
        items = filterByMaxPrice(items, Integer.parseInt(filters.get("maxPrice")));
    }

    Map<String, Object> params = new HashMap<>();
    params.put("items", items);
    params.put("filters", filters);

    return GuiTemplate.template("shop-filtered", params);
}
```

## Related Topics

- **[GUI Templates](GUI-Templates.md)** - Learn about Freemarker templates for dynamic GUIs
- **[GUI Basics](GUI-Basics.md)** - Understanding the GUI framework fundamentals
- **[AsyncGui](Async-GUI.md)** - Detailed guide on asynchronous GUI operations
- **[Exception Handling](Exception-Handling.md)** - Custom exception handlers for GUIs
