# GUI Actions

This guide covers the GUI action system in Tubing, which provides URL-style routing for GUI interactions. You'll learn about action query syntax, parameter binding, navigation mechanisms, and best practices for building interactive GUI workflows.

## Overview

The GUI action system is the routing layer that connects user interactions to controller logic. It provides:

- **Action Routing**: URL-style routing with controller:action format
- **Query Parameters**: Pass data through action strings with automatic parsing
- **Parameter Binding**: Automatic type conversion and injection into controller methods
- **Navigation History**: Built-in back button support with history stack
- **Built-in Actions**: Pre-defined actions for common operations (NOOP, BACK)
- **Return Types**: Multiple return types to control GUI behavior and navigation
- **Programmatic Building**: GuiActionBuilder for constructing action queries in code

The action system is managed by `GuiActionService`, which:
- Discovers and registers all `@GuiAction` methods on startup
- Routes action strings to appropriate controller methods
- Parses and injects parameters automatically
- Manages navigation history for each player
- Handles action execution and GUI lifecycle

## Action Query Syntax

Actions use a URL-style syntax with routes and optional query parameters.

### Basic Action Format

The simplest action is just a route identifier:

```java
// In GUI item
.withLeftClickAction("shop:browse")

// In controller
@GuiAction("shop:browse")
public TubingGui browseShop(Player player) {
    // Handle action
}
```

**Action Routes:**
- Use descriptive, semantic names (e.g., "shop:browse", "player:profile")
- Typically follow namespace:action convention
- Use lowercase with hyphens for multi-word actions (e.g., "shop:view-history")
- Must be unique across all controllers

### Action Query with Parameters

Add parameters using query string syntax:

```java
.withLeftClickAction("shop:buy?item=diamond_sword&quantity=1&price=100")
```

**Query String Format:**
- Start with `?` after the route
- Separate parameters with `&`
- Use `key=value` format
- Values are URL-encoded automatically when using GuiActionBuilder
- Values are URL-decoded automatically when parsing

### Complex Parameter Values

Parameters are automatically URL-encoded/decoded:

```java
// In code - GuiActionBuilder handles encoding
GuiActionBuilder.builder()
    .action("player:message")
    .param("text", "Hello World!")  // Space encoded as %20
    .param("color", "&aGreen Text") // & encoded as %26
    .build();
// Result: "player:message?text=Hello+World%21&color=%26aGreen+Text"

// In controller - automatic decoding
@GuiAction("player:message")
public TubingGui sendMessage(Player player,
                             @GuiParam("text") String text,
                             @GuiParam("color") String color) {
    // text = "Hello World!"
    // color = "&aGreen Text"
}
```

### Dynamic Actions in Templates

When using Freemarker templates, build actions dynamically:

```xml
<item slot="${slot}"
      leftClickAction="shop:buy?item=${item.id}&price=${item.price}&category=${category}">
    <itemStack material="${item.material}" name="${item.name}"/>
</item>
```

**Template Variables:**
- Access any parameter passed to the template
- Use `${variable}` syntax
- Expressions are evaluated before action creation
- Encoding is handled automatically

### GuiActionBuilder

For programmatic action building in Java:

```java
// Build action with builder pattern
String action = GuiActionBuilder.builder()
    .action("shop:buy")
    .param("item", "diamond_sword")
    .param("quantity", "1")
    .param("price", "100")
    .build();
// Result: "shop:buy?item=diamond_sword&quantity=1&price=100"

// Parse existing action
GuiActionBuilder builder = GuiActionBuilder.fromAction("shop:buy?item=sword&price=50");
builder.param("discount", "10");
String updated = builder.build();
// Result: "shop:buy?item=sword&price=50&discount=10"
```

**Builder Methods:**
- `builder()` - Create new builder
- `fromAction(String)` - Parse existing action string
- `action(String)` - Set the action route
- `param(String key, String value)` - Add parameter (null/blank values skipped)
- `build()` - Generate final action string

**Use Cases:**
- Building actions dynamically based on runtime data
- Modifying existing actions
- Ensuring proper URL encoding
- Constructing complex action queries

## Parameter Binding

Parameters from action queries are automatically extracted, converted, and injected into controller method parameters.

### @GuiParam Annotation

Bind individual parameters by name:

```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String itemId,
                         @GuiParam("quantity") int quantity,
                         @GuiParam("price") double price) {
    // itemId = "diamond_sword"
    // quantity = 1 (converted from string)
    // price = 100.0 (converted from string)
}
```

**Supported Types:**
- `String` - No conversion
- `int` / `Integer` - Integer conversion
- `long` / `Long` - Long conversion
- `boolean` / `Boolean` - Boolean conversion
- `float` / `Float` - Float conversion
- `double` / `Double` - Double conversion
- `byte` / `Byte` - Byte conversion
- `short` / `Short` - Short conversion

**Type Conversion:**
- Automatic conversion from string to target type
- Uses standard parsing methods (Integer.parseInt, etc.)
- Throws exception on invalid format (e.g., "abc" to int)
- Exceptions can be caught by GuiExceptionHandlers

### Default Values

Provide fallback values when parameters are missing:

```java
@GuiAction("shop:browse")
public TubingGui browse(Player player,
                       @GuiParam(value = "category", defaultValue = "all") String category,
                       @GuiParam(value = "page", defaultValue = "1") int page,
                       @GuiParam(value = "sort", defaultValue = "name") String sortBy) {
    // If action is "shop:browse" with no params:
    // category = "all"
    // page = 1
    // sortBy = "name"
}
```

**Default Value Rules:**
- Used when parameter is missing from action query
- Must be string literal in annotation (evaluated at compile time)
- Converted to target type using same rules as regular parameters
- Empty string ("") means no default - parameter will be null

### Optional Parameters

Use wrapper types for truly optional parameters:

```java
@GuiAction("shop:filter")
public TubingGui filter(Player player,
                       @GuiParam("minPrice") Integer minPrice,  // Use Integer, not int
                       @GuiParam("maxPrice") Integer maxPrice,
                       @GuiParam("category") String category) {
    // Check for null to determine if provided
    if (minPrice != null) {
        // Filter by minimum price
    }

    if (maxPrice != null) {
        // Filter by maximum price
    }

    if (category != null && !category.isEmpty()) {
        // Filter by category
    }
}
```

**Best Practices:**
- Use wrapper types (Integer, Boolean) instead of primitives for optional params
- Allows null checks to determine if parameter was provided
- Primitive types get default value (0, false) even if not provided
- Use `StringUtils.isNotBlank()` for optional string parameters

### @GuiParams - All Parameters

Receive all parameters as a map:

```java
@GuiAction("search:advanced")
public TubingGui advancedSearch(Player player,
                               @GuiParams Map<String, String> params) {
    // params contains all query parameters as strings
    // Access: params.get("keyword"), params.get("minPrice"), etc.

    String keyword = params.getOrDefault("keyword", "");
    String minPrice = params.getOrDefault("minPrice", "0");
    String category = params.getOrDefault("category", "all");

    // Manual conversion if needed
    int price = Integer.parseInt(minPrice);
}
```

**Use Cases:**
- Unknown number of parameters
- Dynamic filtering systems
- Forwarding parameters to other methods
- Custom parameter processing logic

### @CurrentAction - Full Action String

Get the complete action query:

```java
@GuiAction("history:view")
public TubingGui viewHistory(Player player,
                             @CurrentAction String currentAction,
                             @GuiParam("page") int page) {
    // currentAction = "history:view?page=2&filter=recent"

    // Useful for:
    // - Building back buttons with current state
    // - Logging user actions
    // - Storing action in player session
    // - Refreshing current GUI
}
```

**Use Cases:**
- Creating navigation that preserves current state
- Implementing refresh functionality
- Audit logging
- Storing last action for reconnect

### @InteractableItems - Inventory Items

Access items from interactable slots:

```java
@GuiAction("auction:create")
public TubingGui createAuction(Player player,
                              @InteractableItems List<ItemStack> items) {
    // items contains all items placed in interactable slots

    if (items.isEmpty()) {
        player.sendMessage("Please place items to auction");
        return GuiActionReturnType.KEEP_OPEN;
    }

    // Create auction with provided items
    auctionService.createAuction(player, items);
    return buildConfirmationGui();
}
```

**Interactable Slots:**
- Defined when building TubingGui
- Slots where players can place/remove items
- Used for trading GUIs, crafting interfaces, auctions, etc.
- Items in these slots are collected and passed to action methods

### Player Parameter (Automatic)

The player who triggered the action is automatically injected:

```java
@GuiAction("profile:view")
public TubingGui viewProfile(Player player) {
    // No annotation needed - Player is automatically injected
    // This is always the player who clicked the GUI item
}
```

**Rules:**
- Any `Player` parameter is automatically injected
- No annotation required
- Always represents the player who triggered the action
- Can be combined with other parameter types in any order

### Parameter Order

Parameters can appear in any order - the framework matches by type and annotation:

```java
@GuiAction("trade:confirm")
public TubingGui confirmTrade(@GuiParam("target") String targetPlayer,
                              @CurrentAction String action,
                              @InteractableItems List<ItemStack> items,
                              Player player,
                              @GuiParam("offer") double offer) {
    // Order doesn't matter - framework injects correctly
}
```

## Return Types and Navigation

Controller methods support multiple return types that control what happens after action execution.

### TubingGui - Display New GUI

Return a `TubingGui` to display a new GUI:

```java
@GuiAction("shop:category")
public TubingGui showCategory(Player player,
                             @GuiParam("category") String category) {
    return new TubingGui.Builder("Shop - " + category, 54)
        .addItem(/* items */)
        .build();
}
```

**Behavior:**
- Current GUI is closed
- New GUI is displayed
- Action is added to history stack (unless skipHistory=true)
- Previous GUI is remembered for back navigation

### GuiTemplate - Render Template

Return a `GuiTemplate` to render from XML template:

```java
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player,
                             @GuiParam("category") String category) {
    Map<String, Object> params = new HashMap<>();
    params.put("category", category);
    params.put("items", shopService.getItems(category));

    return GuiTemplate.template("shop-browse", params);
}
```

**Behavior:**
- Template is resolved and rendered with parameters
- Resulting TubingGui is displayed
- All parameters (including Player and current action) are available in template
- Same navigation behavior as returning TubingGui

### ChatTemplate - Send Messages

Return a `ChatTemplate` to send formatted chat messages:

```java
@GuiAction("help:info")
public ChatTemplate showHelp(Player player,
                            @GuiParam("topic") String topic) {
    Map<String, Object> params = new HashMap<>();
    params.put("topic", topic);
    params.put("commands", helpService.getCommands(topic));

    return ChatTemplate.template("help-message", params);
}
```

**Behavior:**
- GUI is closed
- Chat template is rendered and sent to player
- History is cleared (chat messages don't support back navigation)
- Player returns to normal gameplay

### String - Redirect to Action

Return a string to redirect to another action:

```java
@GuiAction("shop:purchase")
public String completePurchase(Player player,
                              @GuiParam("item") String itemId) {
    boolean success = shopService.purchase(player, itemId);

    if (success) {
        return "shop:success?item=" + itemId;
    } else {
        return "shop:error?reason=insufficient_funds";
    }
}
```

**Behavior:**
- Returned string is executed as a new action
- Parameters can be included in redirect
- Original action is not added to history (redirect replaces it)
- Useful for conditional navigation

**Use Cases:**
- Conditional navigation based on results
- Implementing state machines
- Routing to different GUIs based on data
- Error handling with fallback GUIs

### void (null) - Close GUI

Return `null` or use `void` to close the GUI:

```java
@GuiAction("logout")
public void logout(Player player) {
    player.sendMessage("Logged out successfully");
    // GUI closes automatically
}

// Or explicitly return null
@GuiAction("exit")
public TubingGui exit(Player player) {
    playerService.saveData(player);
    return null; // Close GUI
}
```

**Behavior:**
- GUI is closed
- History is cleared
- No new GUI is opened
- Player returns to normal gameplay

### GuiActionReturnType - Control Behavior

Return enum values for special behaviors:

```java
public enum GuiActionReturnType {
    KEEP_OPEN,  // Don't change current GUI
    BACK,       // Navigate to previous GUI
    CLOSE       // Close GUI and clear history
}
```

#### KEEP_OPEN

Keep the current GUI open without changes:

```java
@GuiAction("shop:preview")
public GuiActionReturnType previewItem(Player player,
                                      @GuiParam("item") String itemId) {
    // Show preview in chat but keep GUI open
    player.sendMessage("Preview: " + itemService.getDescription(itemId));
    return GuiActionReturnType.KEEP_OPEN;
}
```

**Use Cases:**
- Displaying information without navigation
- Toggle actions that don't require GUI refresh
- Sound/effect triggers
- Permission checks that fail

#### BACK

Navigate to the previous GUI:

```java
@GuiAction("purchase:confirm")
public GuiActionReturnType confirmPurchase(Player player,
                                          @GuiParam("item") String itemId) {
    if (shopService.purchase(player, itemId)) {
        player.sendMessage("Purchase successful!");
        return GuiActionReturnType.BACK;
    }
    return GuiActionReturnType.KEEP_OPEN;
}
```

**Use Cases:**
- Success confirmations
- Cancellation actions
- Completing workflows
- Error recovery

#### CLOSE

Close GUI and clear history:

```java
@GuiAction("confirm:delete")
public GuiActionReturnType confirmDelete(Player player,
                                        @GuiParam("id") String id) {
    dataService.delete(id);
    player.sendMessage("Deleted successfully");
    return GuiActionReturnType.CLOSE;
}
```

**Use Cases:**
- Final actions that complete a flow
- Destructive operations
- Logout/exit actions
- Error states that shouldn't allow navigation

### AsyncGui - Async Execution

Wrap any return type for async execution:

```java
@GuiAction("data:load")
public AsyncGui<TubingGui> loadData(Player player,
                                   @GuiParam("id") String dataId) {
    return AsyncGui.async(() -> {
        // Runs asynchronously off main thread
        DataModel data = database.loadData(dataId);

        // Build GUI with loaded data
        return new TubingGui.Builder("Data: " + data.getName(), 27)
            .addItem(/* items built from data */)
            .build();
    });
}
```

**Behavior:**
- Lambda executes on async thread
- Return value is processed on main thread
- GUI is displayed once async operation completes
- Same navigation rules as synchronous return type

**Important:**
- Don't access Bukkit API directly in async block
- Exceptions in async block are caught and routed to exception handlers
- Player might log out during async execution - GUI won't open if offline

## Built-in Actions

Tubing provides pre-defined action constants for common operations.

### TubingGuiActions.NOOP

No operation - click does nothing:

```java
import be.garagepoort.mcioc.tubinggui.model.TubingGuiActions;

// In GUI builder
TubingGuiItem decorativeItem = new TubingGuiItem.Builder(null, 4)
    .withItemStack(titleItemStack)
    .withLeftClickAction(TubingGuiActions.NOOP)
    .build();

// Or omit click action - defaults to NOOP
TubingGuiItem decorativeItem = new TubingGuiItem.Builder(null, 4)
    .withItemStack(titleItemStack)
    // No action specified = NOOP
    .build();
```

**Constant Value:** `"$NOOP"`

**Use Cases:**
- Decorative items (borders, titles, spacers)
- Information displays
- Items that show data but aren't clickable
- Temporarily disabling functionality

**Behavior:**
- Click is captured but no action executes
- Item cannot be moved or removed
- GUI remains open
- No history change

### TubingGuiActions.BACK

Navigate to previous GUI in history:

```java
import be.garagepoort.mcioc.tubinggui.model.TubingGuiActions;

// In GUI builder
TubingGuiItem backButton = new TubingGuiItem.Builder(null, 49)
    .withItemStack(backItemStack)
    .withLeftClickAction(TubingGuiActions.BACK)
    .build();

// In templates
<item slot="49" leftClickAction="BACK">
    <itemStack material="ARROW" name="&cBack"/>
</item>
```

**Constant Value:** `"$BACK"`

**Use Cases:**
- Back buttons in navigation
- Cancel actions
- Return from confirmation dialogs
- Exit from sub-menus

**Behavior:**
- Pops last action from history stack
- Executes previous action
- If history is empty, closes GUI
- History entry for current GUI is removed
- Skipped history entries (skipHistory=true) are bypassed

**Example Flow:**
```
Player navigation: main → shop → category → item
History stack: [main, shop, category, item]

Click BACK on item page:
- Pop "item" from history
- Pop and execute "category"
- History: [main, shop, category]

Click BACK on category page:
- Pop "category" from history
- Pop and execute "shop"
- History: [main, shop]

Click BACK on main page:
- Pop "main" from history
- History is empty
- GUI closes
```

## Navigation History

The GUI framework maintains a history stack for each player, enabling back navigation similar to a web browser.

### How History Works

**History Stack:**
- Each player has their own history stack (Map<UUID, Stack<String>>)
- Stack stores complete action queries (route + parameters)
- Managed automatically by GuiActionService
- Cleared when GUI is closed or on errors

**Automatic History Management:**

When an action executes:
1. Current action is pushed to history stack
2. GUI is displayed
3. Player can navigate back using BACK action

**History Modifiers:**

```java
@GuiAction(
    value = "action-route",
    overrideHistory = true,  // Default: true
    skipHistory = false      // Default: false
)
```

### overrideHistory

Controls whether action replaces the last history entry:

```java
@GuiAction(value = "shop:page", overrideHistory = true)
public TubingGui changePage(Player player,
                           @GuiParam("page") int page) {
    // Pagination replaces history entry
    // shop:page?page=1 → shop:page?page=2 (only page 2 in history)
}

@GuiAction(value = "shop:category", overrideHistory = false)
public TubingGui showCategory(Player player,
                              @GuiParam("category") String category) {
    // Each category is separate history entry
    // Navigating categories builds history: [books, weapons, armor]
}
```

**When to Use:**
- **overrideHistory=true** (default):
  - Pagination (avoid history per page)
  - Sorting/filtering same view
  - State changes within same logical screen
  - Real-time updates/refreshes

- **overrideHistory=false**:
  - Navigation between different sections
  - Drilling down hierarchies
  - Opening different items
  - Each click should be in history

**How It Works:**
```java
// With overrideHistory=true:
History: [main]
Execute: shop:page?page=1
History: [main, shop:page?page=1]

Execute: shop:page?page=2
History: [main, shop:page?page=2]  // Replaced page 1

// With overrideHistory=false:
History: [main]
Execute: shop:category?cat=weapons
History: [main, shop:category?cat=weapons]

Execute: shop:category?cat=armor
History: [main, shop:category?cat=weapons, shop:category?cat=armor]  // Added
```

### skipHistory

Skip adding action to history entirely:

```java
@GuiAction(value = "shop:refresh", skipHistory = true)
public TubingGui refresh(Player player) {
    // Refresh doesn't affect history
    // Back button returns to action before refresh
}

@GuiAction(value = "shop:sort", skipHistory = true)
public TubingGui sortItems(Player player,
                          @GuiParam("sortBy") String sortBy) {
    // Sorting is temporary - don't add to history
    // Back goes to previous screen, not previous sort
}
```

**When to Use:**
- Refresh actions
- Sorting/filtering that shouldn't persist
- Temporary overlays/popups
- Preview actions
- Sound/particle effects

**How It Works:**
```java
History: [main, shop]
Execute: shop:refresh (skipHistory=true)
History: [main, shop, $SKIP_HISTORY$]  // Marker added

Execute: shop:item?id=sword
History: [main, shop, $SKIP_HISTORY$, shop:item?id=sword]

Click BACK:
- Pop shop:item?id=sword
- Pop $SKIP_HISTORY$ (skipped)
- Pop and execute shop
History: [main, shop]
```

### Manual History Control

The history system is mostly automatic, but manual control is available:

```java
@IocBean
public class AdvancedNavigationService {

    private final GuiHistoryStack historyStack;

    public AdvancedNavigationService(GuiHistoryStack historyStack) {
        this.historyStack = historyStack;
    }

    // Check last action
    public boolean isLastAction(Player player, String action) {
        return historyStack.isLastAction(player.getUniqueId(), action);
    }

    // Clear history
    public void clearHistory(Player player) {
        historyStack.clear(player.getUniqueId());
    }

    // Note: push() and pop() are internal - called by GuiActionService
}
```

**Use Cases:**
- Checking if player came from specific action
- Clearing history on major navigation
- Custom navigation logic
- Debug/logging purposes

### History Best Practices

**1. Use skipHistory for Transient Actions:**
```java
// Good - refresh doesn't affect navigation
@GuiAction(value = "list:refresh", skipHistory = true)
public TubingGui refresh(Player player) { }

// Bad - refresh creates useless history entries
@GuiAction(value = "list:refresh", skipHistory = false)
public TubingGui refresh(Player player) { }
```

**2. Use overrideHistory for Pagination:**
```java
// Good - only current page in history
@GuiAction(value = "list:page", overrideHistory = true)
public TubingGui showPage(@GuiParam("page") int page) { }

// Bad - every page in history
@GuiAction(value = "list:page", overrideHistory = false)
public TubingGui showPage(@GuiParam("page") int page) { }
```

**3. Clear History on Major Navigation:**
```java
@GuiAction("menu:main")
public TubingGui mainMenu(Player player) {
    // Main menu is top level - clear old history
    // (happens automatically, but can be explicit)
    return buildMainMenu();
}
```

**4. Include Back Buttons:**
```java
private TubingGuiItem createBackButton(int slot) {
    return new TubingGuiItem.Builder(null, slot)
        .withItemStack(backButtonStack)
        .withLeftClickAction(TubingGuiActions.BACK)
        .build();
}

// Add to every GUI except main menu
public TubingGui buildShopGui() {
    return new TubingGui.Builder("Shop", 54)
        .addItem(createBackButton(49))
        .addItem(/* other items */)
        .build();
}
```

**5. Preserve State in Back Navigation:**
```java
@GuiAction("search:results")
public TubingGui searchResults(Player player,
                              @CurrentAction String currentAction,
                              @GuiParam("query") String query,
                              @GuiParam("page") int page) {
    // Use currentAction to build back button that preserves state
    return new TubingGui.Builder("Search: " + query, 54)
        .addItem(/* results */)
        .addItem(createBackButton(49))  // Will return to correct page
        .build();
}
```

## Action Routing Mechanism

Understanding how actions are routed helps you design better GUI architectures.

### Action Registration

Actions are registered during plugin startup:

**1. Discovery:**
```java
// TubingGuiOnload discovers @GuiController classes
@GuiController
public class ShopController {
    @GuiAction("shop:browse")
    public TubingGui browse(Player player) { }

    @GuiAction("shop:purchase")
    public TubingGui purchase(Player player) { }
}
```

**2. Registration:**
```java
// GuiActionService.loadGuiControllers() registers actions
Map<String, GuiActionConfig> guiActions = new HashMap<>();
// "shop:browse" -> GuiActionConfig(method, overrideHistory, skipHistory)
// "shop:purchase" -> GuiActionConfig(method, overrideHistory, skipHistory)
```

**3. Validation:**
- Action routes must be unique
- Duplicate routes throw `IocException`
- Method signatures must be valid (proper parameter types)

### Action Execution Flow

When a player clicks an item:

**1. Click Event:**
```java
// InventoryClick listener captures event
@EventHandler
public void onClick(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();
    TubingGui gui = guiActionService.getTubingGui(player);
    TubingGuiItem item = gui.getItem(event.getSlot());
    String action = item.getLeftClickAction(); // or right/middle/shift
    guiActionService.executeAction(player, action);
}
```

**2. Action Parsing:**
```java
// GuiActionQuery parses action string
GuiActionQuery query = new GuiActionQuery("shop:buy?item=sword&price=100");
// route = "shop:buy"
// params = {item: "sword", price: "100"}
```

**3. Route Lookup:**
```java
// Find matching GuiActionConfig
if (!guiActions.containsKey(query.getRoute())) {
    throw new IocException("No Gui Action found for [" + query.getRoute() + "]");
}
GuiActionConfig config = guiActions.get(query.getRoute());
Method method = config.getMethod();
```

**4. Parameter Injection:**
```java
// ActionQueryParser resolves method parameters
Object[] params = actionQueryParser.getMethodParams(
    method,        // Controller method
    query,         // Parsed action
    player,        // Current player
    currentGui     // Current GUI
);
```

**5. Method Invocation:**
```java
// Get controller bean from IoC container
Object controller = iocContainer.get(method.getDeclaringClass());

// Invoke method
Object result = method.invoke(controller, params);
```

**6. Result Processing:**
```java
// Handle return type
if (result instanceof TubingGui) {
    showGui(player, (TubingGui) result);
} else if (result instanceof GuiTemplate) {
    renderAndShowTemplate(player, (GuiTemplate) result);
} else if (result instanceof String) {
    executeAction(player, (String) result);  // Redirect
} else if (result == null) {
    player.closeInventory();
} // ... other return types
```

**7. History Management:**
```java
// Add to history (unless skipHistory=true)
historyStack.push(player.getUniqueId(), query, overrideHistory, skipHistory);
```

### Built-in Action Handling

Special actions are handled before routing:

```java
if (actionQuery.equalsIgnoreCase(TubingGuiActions.BACK)) {
    Optional<String> backAction = guiHistoryStack.pop(player.getUniqueId());
    if (backAction.isPresent()) {
        executeAction(player, backAction.get());
        return;
    }
    player.closeInventory();
    return;
}
```

**NOOP is handled at click level:**
- Items with NOOP action don't call executeAction
- Click is cancelled, GUI stays open
- No performance overhead

## Best Practices

### 1. Use Consistent Naming Conventions

**Good:**
```java
@GuiAction("shop:browse")
@GuiAction("shop:category")
@GuiAction("shop:buy")
@GuiAction("shop:checkout")

@GuiAction("player:profile")
@GuiAction("player:stats")
@GuiAction("player:settings")
```

**Bad:**
```java
@GuiAction("browseShop")
@GuiAction("shop_category")
@GuiAction("BuyItem")
@GuiAction("shop/checkout")

@GuiAction("profile")
@GuiAction("playerStats")
@GuiAction("settings-player")
```

**Guidelines:**
- Use namespace:action format
- Lowercase with hyphens for multi-word names
- Group related actions with common namespace
- Make actions self-documenting

### 2. Keep Actions Focused

**Good - Single responsibility:**
```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player, @GuiParam("item") String item) {
    // Only handles purchase
}

@GuiAction("shop:preview")
public TubingGui previewItem(Player player, @GuiParam("item") String item) {
    // Only shows preview
}
```

**Bad - Multiple responsibilities:**
```java
@GuiAction("shop:item")
public TubingGui handleItem(Player player,
                           @GuiParam("item") String item,
                           @GuiParam("action") String action) {
    if ("buy".equals(action)) {
        // Buy logic
    } else if ("preview".equals(action)) {
        // Preview logic
    } else if ("favorite".equals(action)) {
        // Favorite logic
    }
}
```

### 3. Validate Parameters Early

**Good:**
```java
@GuiAction("shop:buy")
public TubingGui buyItem(Player player,
                         @GuiParam("item") String itemId,
                         @GuiParam("quantity") int quantity) {
    // Validate input
    if (itemId == null || itemId.isEmpty()) {
        player.sendMessage("Invalid item!");
        return GuiActionReturnType.KEEP_OPEN;
    }

    if (quantity <= 0 || quantity > 64) {
        player.sendMessage("Invalid quantity!");
        return GuiActionReturnType.KEEP_OPEN;
    }

    // Process purchase
    return processPurchase(player, itemId, quantity);
}
```

### 4. Use GuiActionBuilder for Complex Actions

**Good:**
```java
String action = GuiActionBuilder.builder()
    .action("shop:buy")
    .param("item", item.getId())
    .param("quantity", String.valueOf(quantity))
    .param("category", category)
    .param("return", currentAction)
    .build();
```

**Bad:**
```java
String action = "shop:buy?item=" + item.getId() +
                "&quantity=" + quantity +
                "&category=" + category +
                "&return=" + currentAction;
// Doesn't handle URL encoding!
```

### 5. Design for Navigation Flow

**Good - Clear flow:**
```java
main-menu
├── shop:browse
│   ├── shop:category?cat=weapons
│   │   └── shop:item?id=sword
│   └── shop:category?cat=armor
│       └── shop:item?id=helmet
└── player:profile
    ├── player:stats
    └── player:settings
```

**Bad - Unclear flow:**
```java
menu1 → action2 → thing3 → back → somewhere
```

**Guidelines:**
- Design navigation hierarchy on paper first
- Use consistent patterns across features
- Provide multiple paths to common destinations
- Always include way back or close

### 6. Handle Errors Gracefully

**Good:**
```java
@GuiAction("data:load")
public TubingGui loadData(Player player, @GuiParam("id") String id) {
    try {
        Data data = dataService.load(id);
        return buildDataGui(data);
    } catch (DataNotFoundException e) {
        player.sendMessage("&cData not found!");
        return GuiActionReturnType.BACK;
    } catch (Exception e) {
        player.sendMessage("&cAn error occurred!");
        throw e; // Let exception handler deal with it
    }
}
```

### 7. Use Appropriate Return Types

**Good:**
```java
// Display GUI
@GuiAction("shop:browse")
public TubingGui browse() { return gui; }

// Conditional redirect
@GuiAction("shop:checkout")
public String checkout() {
    return hasBalance() ? "shop:success" : "shop:insufficient-funds";
}

// Keep open for toggle
@GuiAction("filter:toggle")
public GuiActionReturnType toggle() {
    return GuiActionReturnType.KEEP_OPEN;
}

// Close on complete
@GuiAction("logout")
public void logout() { /* close */ }
```

### 8. Document Complex Actions

**Good:**
```java
/**
 * Processes item purchase and redirects based on result.
 *
 * @param player The buyer
 * @param itemId Item identifier
 * @param quantity Amount to purchase (1-64)
 * @param useCredits Whether to use credit system
 * @return Redirect to success or error page
 */
@GuiAction("shop:purchase")
public String purchase(Player player,
                      @GuiParam("item") String itemId,
                      @GuiParam("quantity") int quantity,
                      @GuiParam(value = "credits", defaultValue = "false") boolean useCredits) {
    // Implementation
}
```

### 9. Test Action Flows

**Good:**
```java
@Test
public void testPurchaseFlow() {
    // Test successful purchase
    String result = controller.purchase(player, "sword", 1, false);
    assertEquals("shop:success?item=sword", result);

    // Test insufficient funds
    when(economyService.getBalance(player)).thenReturn(0.0);
    result = controller.purchase(player, "sword", 1, false);
    assertEquals("shop:error?reason=insufficient_funds", result);
}
```

## Related Topics

- **[GUI Controllers](GUI-Controllers.md)** - Controller architecture and dependency injection
- **[GUI Building](GUI-Building.md)** - Programmatic GUI construction
- **[GUI Templates](GUI-Templates.md)** - Template-based GUI creation
- **[GUI Setup](GUI-Setup.md)** - Initial configuration and lifecycle
