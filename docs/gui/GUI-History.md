# GUI History

This guide covers the navigation history system in Tubing GUIs, which provides browser-like back navigation for your menu systems. You'll learn how the history stack works, how to control history behavior with annotations, and best practices for implementing intuitive navigation flows.

## Overview

The GUI history system provides automatic navigation memory, enabling players to navigate back through their menu journey just like using a web browser's back button. This creates a more intuitive and user-friendly experience.

**Key Features:**

- **Automatic History Management**: Every GUI navigation is automatically tracked
- **Browser-Like Back Navigation**: Built-in BACK action returns to previous screens
- **Per-Player Stack**: Each player has their own independent history
- **History Control**: Fine-tune history behavior with `overrideHistory` and `skipHistory`
- **Smart Clearing**: History automatically clears when GUIs close or errors occur
- **Skip History Markers**: Temporary actions can be excluded from navigation

The history system is managed by `GuiHistoryStack`, which maintains a stack of action queries for each player. When actions execute, they're automatically pushed to the history stack. When players click back buttons, the previous action is popped and re-executed.

## How History Works

### The History Stack

Every player has their own history stack stored in memory:

```java
// Internal structure (simplified)
Map<UUID, Stack<String>> playerHistoryStack
// UUID -> Stack of action query strings
```

**Stack Contents:**
- Each entry is a complete action query string (e.g., "shop:browse?category=weapons&page=2")
- Includes both the route and all parameters
- Preserves the exact state when the action was executed
- Cleared when GUI closes or player logs out

### Navigation Flow Example

Let's walk through a typical navigation flow:

```
Player Action:           History Stack After Action:
--------------           ---------------------------
Open main menu    ->     ["menu:main"]
Open shop         ->     ["menu:main", "shop:browse"]
Select category   ->     ["menu:main", "shop:browse", "shop:category?cat=weapons"]
View item         ->     ["menu:main", "shop:browse", "shop:category?cat=weapons", "shop:item?id=sword"]

Click BACK        ->     ["menu:main", "shop:browse", "shop:category?cat=weapons"]
                         (Executes "shop:category?cat=weapons")

Click BACK        ->     ["menu:main", "shop:browse"]
                         (Executes "shop:browse")

Click BACK        ->     ["menu:main"]
                         (Executes "menu:main")

Click BACK        ->     []
                         (History empty - GUI closes)
```

### Automatic History Push

When an action executes successfully:

1. **Action Executes**: Controller method is invoked
2. **History Check**: Framework checks `overrideHistory` and `skipHistory` flags
3. **Stack Update**: Action is pushed to player's history stack (unless skipped)
4. **GUI Display**: New GUI is shown to player
5. **Navigation Ready**: Back navigation now available

**Code Flow:**
```java
// In GuiActionService.processGuiAction()
guiHistoryStack.push(player.getUniqueId(), actionQuery, overrideHistory, skipHistory);
```

This happens automatically for all actions that return:
- `TubingGui` - Display new GUI
- `GuiTemplate` - Render template to GUI
- `String` - Redirect to another action

### Automatic History Clear

History is automatically cleared when:

**1. GUI Closes Normally:**
```java
@GuiAction("menu:exit")
public void exitMenu(Player player) {
    player.sendMessage("Goodbye!");
    return; // null return - GUI closes, history cleared
}
```

**2. ChatTemplate Returns:**
```java
@GuiAction("help:info")
public ChatTemplate showHelp(Player player) {
    // GUI closes, chat displayed, history cleared
    return ChatTemplate.template("help-info", params);
}
```

**3. Exception Occurs:**
```java
@GuiAction("data:load")
public TubingGui loadData(Player player) {
    throw new DataNotFoundException(); // Exception handler closes GUI and clears history
}
```

**4. Programmatic Close:**
```java
guiActionService.removeInventory(player);
guiHistoryStack.clear(player.getUniqueId());
```

## Back Navigation

### Using TubingGuiActions.BACK

The special `TubingGuiActions.BACK` constant provides built-in back navigation:

```java
import be.garagepoort.mcioc.tubinggui.model.TubingGuiActions;

// In GUI builder
TubingGuiItem backButton = new TubingGuiItem.Builder(null, 49)
    .withItemStack(new ItemStack(Material.ARROW))
    .withLeftClickAction(TubingGuiActions.BACK)
    .build();

// Add to GUI
return new TubingGui.Builder("Shop", 54)
    .addItem(backButton)
    .addItem(/* other items */)
    .build();
```

**Constant Value:** `"$BACK"`

### Back in Templates

Use the BACK action directly in XML templates:

```xml
<!-- Simple back button -->
<item slot="49" leftClickAction="BACK">
    <itemStack material="ARROW" name="&c&lBack"/>
</item>

<!-- Back button with lore -->
<item slot="49" leftClickAction="BACK">
    <itemStack material="ARROW" name="&c&lBack">
        <lore>
            <line>&7Return to previous menu</line>
        </lore>
    </itemStack>
</item>

<!-- Conditional back button (Freemarker) -->
<#if hasHistory>
    <item slot="49" leftClickAction="BACK">
        <itemStack material="ARROW" name="&c&lBack"/>
    </item>
</#if>
```

### How BACK Works

When a player clicks a BACK action:

**1. History Check:**
```java
if (actionQuery.equalsIgnoreCase(TubingGuiActions.BACK)) {
    Optional<String> backAction = guiHistoryStack.pop(player.getUniqueId());
    // ...
}
```

**2. Stack Pop:**
- Current action is popped (removed) from stack
- Previous action is popped from stack
- Skip history markers (`$SKIP_HISTORY$`) are automatically bypassed

**3. Action Re-execution:**
- Previous action is executed with original parameters
- GUI is rebuilt with original state
- New action is pushed to history

**4. Empty Stack:**
- If history has fewer than 2 entries, stack is empty
- GUI closes instead of navigating
- Player returns to normal gameplay

**Example Flow:**
```
History: ["main", "shop", "category", "item"]
Player clicks BACK on item screen

Step 1: Pop "item" (current screen)
Step 2: Pop "category" (destination)
Step 3: Execute "category" action
Step 4: History now: ["main", "shop", "category"]
```

### Programmatic Back Navigation

Return `GuiActionReturnType.BACK` from controller methods:

```java
@GuiAction("purchase:confirm")
public GuiActionReturnType confirmPurchase(Player player,
                                           @GuiParam("item") String itemId) {
    if (shopService.purchase(player, itemId)) {
        player.sendMessage("Purchase successful!");
        return GuiActionReturnType.BACK; // Navigate back
    } else {
        player.sendMessage("Insufficient funds!");
        return GuiActionReturnType.KEEP_OPEN; // Stay on current screen
    }
}
```

**Use Cases:**
- Success confirmations
- Cancel actions
- Quick actions that complete a workflow
- Error recovery

## History Control Annotations

The `@GuiAction` annotation provides two attributes for controlling history behavior.

### overrideHistory

Controls whether the action replaces the last history entry instead of adding a new one.

**Syntax:**
```java
@GuiAction(value = "action-route", overrideHistory = true)  // Default: true
@GuiAction(value = "action-route", overrideHistory = false)
```

#### overrideHistory = true (Default)

The action replaces the last entry in history if it has the same route:

```java
@GuiAction(value = "shop:page", overrideHistory = true)
public TubingGui changePage(Player player, @GuiParam("page") int page) {
    // Pagination replaces history entry
    return buildShopPage(page);
}
```

**Behavior:**
```
History: ["menu:main", "shop:browse"]
Execute: shop:page?page=1
History: ["menu:main", "shop:browse", "shop:page?page=1"]

Execute: shop:page?page=2
History: ["menu:main", "shop:browse", "shop:page?page=2"]  // Replaced page 1

Execute: shop:page?page=3
History: ["menu:main", "shop:browse", "shop:page?page=3"]  // Replaced page 2
```

**When to Use:**
- Pagination (avoid one entry per page)
- Sorting the same view
- Filtering within same screen
- State changes on the same logical page
- Real-time updates/refreshes

#### overrideHistory = false

Each action creates a new history entry, even for the same route:

```java
@GuiAction(value = "shop:item", overrideHistory = false)
public TubingGui viewItem(Player player, @GuiParam("item") String itemId) {
    // Each item view adds to history
    return buildItemView(itemId);
}
```

**Behavior:**
```
History: ["menu:main", "shop:browse"]
Execute: shop:item?item=sword
History: ["menu:main", "shop:browse", "shop:item?item=sword"]

Execute: shop:item?item=helmet
History: ["menu:main", "shop:browse", "shop:item?item=sword", "shop:item?item=helmet"]

Click BACK:
History: ["menu:main", "shop:browse", "shop:item?item=sword"]  // Returns to sword
```

**When to Use:**
- Viewing different items/entities
- Navigating between different categories
- Drilling down hierarchies
- Each click should be remembered separately
- Building a navigation trail through content

#### Route Matching Logic

Override history only applies when routes match exactly:

```java
// GuiHistoryStack.push() implementation
String lastRoute = historyStack.peek().split(Pattern.quote("?"), 2)[0];
if (action.getRoute().equalsIgnoreCase(lastRoute) && overrideHistory) {
    historyStack.pop(); // Remove last entry
}
historyStack.push(action.getFullQuery()); // Add new entry
```

**Example:**
```
History: ["menu:main", "shop:browse?category=all"]

Execute: shop:browse?category=weapons (overrideHistory=true)
Match: "shop:browse" == "shop:browse" → Replace
History: ["menu:main", "shop:browse?category=weapons"]

Execute: shop:item?id=sword (overrideHistory=true)
Match: "shop:item" != "shop:browse" → Don't replace, add new
History: ["menu:main", "shop:browse?category=weapons", "shop:item?id=sword"]
```

### skipHistory

Skip adding this action to history entirely. The action executes but leaves no history trace.

**Syntax:**
```java
@GuiAction(value = "action-route", skipHistory = false)  // Default: false
@GuiAction(value = "action-route", skipHistory = true)
```

#### skipHistory = true

Action is marked with a special `$SKIP_HISTORY$` marker:

```java
@GuiAction(value = "shop:refresh", skipHistory = true)
public TubingGui refresh(Player player) {
    // Refresh doesn't appear in navigation history
    return buildFreshShopGui();
}
```

**Behavior:**
```
History: ["menu:main", "shop:browse"]

Execute: shop:refresh (skipHistory=true)
History: ["menu:main", "shop:browse", "$SKIP_HISTORY$"]

Execute: shop:item?id=sword
History: ["menu:main", "shop:browse", "$SKIP_HISTORY$", "shop:item?id=sword"]

Click BACK on item screen:
- Pop "shop:item?id=sword"
- Pop "$SKIP_HISTORY$" (automatically skipped)
- Pop and execute "shop:browse"
History: ["menu:main", "shop:browse"]
```

**When to Use:**
- Refresh actions
- Temporary overlays/popups
- Preview actions
- Sorting/filtering that shouldn't persist in history
- Sound/particle effects
- Toggle actions
- Any action that's transient and shouldn't affect navigation

#### How Skip History Works

The `$SKIP_HISTORY$` marker is automatically bypassed during back navigation:

```java
// GuiHistoryStack.pop() implementation
String route = stack.pop();
while(SKIP_HISTORY.equalsIgnoreCase(route)) {
    route = stack.pop(); // Keep popping until we find a real action
}
return Optional.ofNullable(route);
```

This ensures back navigation skips over all skipped actions transparently.

### Combining History Controls

You can use both `overrideHistory` and `skipHistory`, but `skipHistory` takes precedence:

```java
@GuiAction(value = "shop:sort", skipHistory = true, overrideHistory = true)
public TubingGui sortItems(Player player, @GuiParam("sortBy") String sortBy) {
    // skipHistory=true means this is skipped entirely
    // overrideHistory is ignored because action isn't added to history
    return buildSortedShop(sortBy);
}
```

**Precedence Rule:** `skipHistory = true` → Action not added to history, `overrideHistory` has no effect.

## History Stack Management

### GuiHistoryStack API

While history is managed automatically, you can interact with it directly:

```java
@IocBean
public class CustomNavigationService {

    private final GuiHistoryStack guiHistoryStack;

    public CustomNavigationService(GuiHistoryStack guiHistoryStack) {
        this.guiHistoryStack = guiHistoryStack;
    }

    // Check if specific action is last in history
    public boolean isLastAction(Player player, String action) {
        return guiHistoryStack.isLastAction(player.getUniqueId(), action);
    }

    // Clear player's history
    public void clearHistory(Player player) {
        guiHistoryStack.clear(player.getUniqueId());
    }

    // Note: push() and pop() are internal - called by GuiActionService
}
```

### isLastAction()

Check if a specific action is the last entry in the history stack:

```java
@GuiAction("confirm:purchase")
public TubingGui confirmPurchase(Player player, @GuiParam("item") String itemId) {
    // Check if player came from the shop
    if (guiHistoryStack.isLastAction(player.getUniqueId(), "shop:browse")) {
        // Player came from shop - show return to shop option
    }

    return buildConfirmationGui(itemId);
}
```

**Use Cases:**
- Conditional navigation
- Context-aware GUIs
- Different behavior based on navigation source
- Analytics/tracking

### clear()

Manually clear a player's history:

```java
@GuiAction("reset:session")
public TubingGui resetSession(Player player) {
    // Start fresh navigation session
    guiHistoryStack.clear(player.getUniqueId());

    return buildMainMenu();
}
```

**Use Cases:**
- Major navigation transitions
- Starting new workflows
- Resetting after errors
- Session management

**Note:** History is automatically cleared when GUIs close, so manual clearing is rarely needed.

## Navigation Flow Patterns

### Hierarchical Navigation

Build menu hierarchies with clear parent-child relationships:

```java
@GuiController
public class ShopController {

    @GuiAction("shop:main")
    public TubingGui mainMenu(Player player) {
        return new TubingGui.Builder("Shop", 54)
            .addItem(createCategoryButton("weapons", 10))
            .addItem(createCategoryButton("armor", 11))
            .addItem(createCategoryButton("tools", 12))
            .build();
    }

    @GuiAction(value = "shop:category", overrideHistory = false)
    public TubingGui showCategory(Player player, @GuiParam("cat") String category) {
        return new TubingGui.Builder("Shop - " + category, 54)
            .addItem(createBackButton(49)) // Returns to shop:main
            .addItem(/* category items */)
            .build();
    }

    @GuiAction(value = "shop:item", overrideHistory = false)
    public TubingGui showItem(Player player, @GuiParam("item") String itemId) {
        return new TubingGui.Builder("Item Details", 27)
            .addItem(createBackButton(22)) // Returns to shop:category
            .addItem(/* item details */)
            .build();
    }

    private TubingGuiItem createBackButton(int slot) {
        return new TubingGuiItem.Builder(null, slot)
            .withItemStack(new ItemStack(Material.ARROW))
            .withLeftClickAction(TubingGuiActions.BACK)
            .build();
    }
}
```

**Flow:**
```
shop:main → shop:category?cat=weapons → shop:item?item=sword
         ←                           ←
    (BACK)                      (BACK)
```

### Pagination with History

Use `overrideHistory = true` to avoid cluttering history with page numbers:

```java
@GuiAction(value = "list:page", overrideHistory = true)
public GuiTemplate showPage(Player player,
                           @GuiParam(value = "page", defaultValue = "1") int page) {

    Map<String, Object> params = new HashMap<>();
    params.put("page", page);
    params.put("items", getItemsForPage(page));
    params.put("hasNext", hasNextPage(page));
    params.put("hasPrevious", page > 1);

    return GuiTemplate.template("paginated-list", params);
}
```

**Template (paginated-list.xml):**
```xml
<gui title="Items - Page ${page}" size="54">
    <!-- Items -->
    <#list items as item>
        <item slot="${item_index}" leftClickAction="item:view?id=${item.id}">
            <itemStack material="${item.material}" name="${item.name}"/>
        </item>
    </#list>

    <!-- Previous page -->
    <#if hasPrevious>
        <item slot="45" leftClickAction="list:page?page=${page - 1}">
            <itemStack material="ARROW" name="&e← Previous Page"/>
        </item>
    </#if>

    <!-- Next page -->
    <#if hasNext>
        <item slot="53" leftClickAction="list:page?page=${page + 1}">
            <itemStack material="ARROW" name="&eNext Page →"/>
        </item>
    </#if>

    <!-- Back to menu -->
    <item slot="49" leftClickAction="BACK">
        <itemStack material="BARRIER" name="&c&lBack"/>
    </item>
</gui>
```

**History Behavior:**
```
Start at list:page?page=1
History: ["menu:main", "list:page?page=1"]

Navigate to page 2:
History: ["menu:main", "list:page?page=2"]  // Replaced page 1

Navigate to page 3:
History: ["menu:main", "list:page?page=3"]  // Replaced page 2

Click BACK:
Returns to menu:main (not previous page)
```

### Filtering and Sorting

Use `skipHistory = true` for temporary view changes:

```java
@GuiAction(value = "shop:filter", skipHistory = true)
public TubingGui filterShop(Player player,
                           @GuiParam("filter") String filterType,
                           @CurrentAction String currentAction) {
    // Apply filter but don't add to history
    List<ShopItem> filtered = shopService.filter(filterType);
    return buildShopGui(filtered);
}

@GuiAction(value = "shop:sort", skipHistory = true, overrideHistory = true)
public TubingGui sortShop(Player player,
                         @GuiParam("sortBy") String sortBy) {
    // Sort but don't add to history
    List<ShopItem> sorted = shopService.sort(sortBy);
    return buildShopGui(sorted);
}
```

**History Behavior:**
```
History: ["menu:main", "shop:browse"]

Apply filter (skipHistory=true):
History: ["menu:main", "shop:browse", "$SKIP_HISTORY$"]

Apply sort (skipHistory=true):
History: ["menu:main", "shop:browse", "$SKIP_HISTORY$", "$SKIP_HISTORY$"]

Select item:
History: ["menu:main", "shop:browse", "$SKIP_HISTORY$", "$SKIP_HISTORY$", "shop:item?id=sword"]

Click BACK:
Bypasses both skip markers, returns directly to shop:browse
History: ["menu:main", "shop:browse"]
```

### Confirmation Dialogs

Use back navigation for cancel actions:

```java
@GuiAction(value = "delete:confirm", overrideHistory = false)
public TubingGui confirmDelete(Player player, @GuiParam("id") String itemId) {
    return new TubingGui.Builder("Confirm Delete", 27)
        .addItem(new TubingGuiItem.Builder(null, 11)
            .withItemStack(greenWool)
            .withLeftClickAction("delete:execute?id=" + itemId)
            .build())
        .addItem(new TubingGuiItem.Builder(null, 15)
            .withItemStack(redWool)
            .withLeftClickAction(TubingGuiActions.BACK) // Cancel
            .build())
        .build();
}

@GuiAction("delete:execute")
public GuiActionReturnType executeDelete(Player player, @GuiParam("id") String itemId) {
    itemService.delete(itemId);
    player.sendMessage("Item deleted!");
    return GuiActionReturnType.BACK; // Return to list
}
```

**Flow:**
```
list:view → delete:confirm?id=123 → delete:execute?id=123
         ←                        ↓
    (Cancel/BACK)        (Execute + BACK)
         ↓                        ↓
    list:view ←─────────────────┘
```

### Wizard/Multi-Step Flows

For linear workflows, use `overrideHistory = false` to enable stepping back:

```java
@GuiAction(value = "quest:step1", overrideHistory = false)
public TubingGui step1(Player player) {
    return buildStepGui("Step 1", "quest:step2");
}

@GuiAction(value = "quest:step2", overrideHistory = false)
public TubingGui step2(Player player) {
    return buildStepGui("Step 2", "quest:step3");
}

@GuiAction(value = "quest:step3", overrideHistory = false)
public TubingGui step3(Player player) {
    return buildStepGui("Step 3", "quest:complete");
}

@GuiAction("quest:complete")
public GuiActionReturnType complete(Player player) {
    questService.completeQuest(player);
    player.sendMessage("Quest completed!");
    return GuiActionReturnType.BACK;
}
```

**Flow:**
```
step1 → step2 → step3 → complete
     ←       ←       ←     ↓
  (BACK)  (BACK)  (BACK)  (Returns to step3)
```

## Best Practices

### 1. Always Provide Back Buttons

Every GUI should have a way to navigate back (except main menus):

```java
// Good - back button included
@GuiAction("shop:category")
public TubingGui showCategory(Player player, @GuiParam("cat") String category) {
    return new TubingGui.Builder("Category: " + category, 54)
        .addItem(createBackButton(49))
        .addItem(/* content */)
        .build();
}

// Bad - no way to go back
@GuiAction("shop:category")
public TubingGui showCategory(Player player, @GuiParam("cat") String category) {
    return new TubingGui.Builder("Category: " + category, 54)
        .addItem(/* content */)
        .build(); // Player must close inventory manually
}
```

### 2. Use Standard Back Button Position

Place back buttons consistently (typically slot 49 in 6-row GUIs):

```java
// Standard back button position
private TubingGuiItem createBackButton() {
    return new TubingGuiItem.Builder(null, 49)
        .withItemStack(new ItemStack(Material.ARROW))
        .withLeftClickAction(TubingGuiActions.BACK)
        .build();
}

// Use in all sub-menus
return new TubingGui.Builder("Sub Menu", 54)
    .addItem(createBackButton())
    .addItem(/* other items */)
    .build();
```

**Standard Positions:**
- 6-row GUI (54 slots): Slot 49 (bottom center)
- 3-row GUI (27 slots): Slot 22 (bottom center)
- 4-row GUI (36 slots): Slot 31 (bottom center)

### 3. Use overrideHistory for Pagination

Prevent history pollution with multiple page entries:

```java
// Good - pagination replaces history
@GuiAction(value = "list:page", overrideHistory = true)
public TubingGui showPage(@GuiParam("page") int page) { }

// Bad - every page in history
@GuiAction(value = "list:page", overrideHistory = false)
public TubingGui showPage(@GuiParam("page") int page) { }
// Result: Player must click back 10 times to exit if they viewed 10 pages
```

### 4. Use skipHistory for Transient Actions

Don't clutter history with temporary state changes:

```java
// Good - refresh doesn't affect history
@GuiAction(value = "list:refresh", skipHistory = true)
public TubingGui refresh(Player player) { }

// Good - sort is temporary
@GuiAction(value = "list:sort", skipHistory = true)
public TubingGui sort(@GuiParam("by") String sortBy) { }

// Bad - every refresh in history
@GuiAction(value = "list:refresh", skipHistory = false)
public TubingGui refresh(Player player) { }
```

### 5. Use overrideHistory=false for Content Navigation

Each piece of content should be a separate history entry:

```java
// Good - each item is separate history entry
@GuiAction(value = "wiki:article", overrideHistory = false)
public TubingGui viewArticle(@GuiParam("article") String articleId) { }

// Good - each profile is separate
@GuiAction(value = "player:profile", overrideHistory = false)
public TubingGui viewProfile(@GuiParam("player") String playerName) { }

// Bad - can't navigate back through articles
@GuiAction(value = "wiki:article", overrideHistory = true)
public TubingGui viewArticle(@GuiParam("article") String articleId) { }
```

### 6. Clear History on Major Transitions

Start fresh when entering new major sections:

```java
@GuiAction("mainmenu:show")
public TubingGui showMainMenu(Player player) {
    // Main menu is top level - start fresh
    // (Happens automatically, but can be explicit)
    return buildMainMenu();
}

@GuiAction("minigame:start")
public TubingGui startMinigame(Player player) {
    // Starting minigame - clear shop history
    guiHistoryStack.clear(player.getUniqueId());
    return buildMinigameMenu();
}
```

### 7. Preserve State with Parameters

Use parameters to maintain GUI state through navigation:

```java
// Good - state preserved in history
@GuiAction(value = "shop:browse", overrideHistory = false)
public TubingGui browseShop(Player player,
                           @GuiParam(value = "filter", defaultValue = "all") String filter,
                           @GuiParam(value = "page", defaultValue = "1") int page) {
    // filter and page preserved in history
    // Back button returns to exact same state
    return buildShopGui(filter, page);
}

// Bad - state lost
@GuiAction(value = "shop:browse", overrideHistory = false)
public TubingGui browseShop(Player player) {
    String filter = getSessionFilter(player); // Not in action query
    int page = getSessionPage(player); // Not in action query
    // Back returns to shop:browse with no params - loses filter/page
    return buildShopGui(filter, page);
}
```

### 8. Design Navigation Hierarchies

Plan your navigation structure before implementation:

```
Main Menu (clear history)
├── Shop
│   ├── Categories (overrideHistory=false)
│   │   └── Items (overrideHistory=false)
│   │       └── Details (overrideHistory=false)
│   ├── Sort (skipHistory=true)
│   ├── Filter (skipHistory=true)
│   └── Pagination (overrideHistory=true)
├── Profile
│   ├── Stats
│   ├── Settings
│   └── Friends (overrideHistory=false)
└── Admin
    ├── Players (pagination: overrideHistory=true)
    ├── Moderate (overrideHistory=false)
    └── Logs (pagination: overrideHistory=true)
```

### 9. Test Navigation Flows

Test complete navigation paths:

```java
@Test
public void testShopNavigation() {
    // Open main menu
    executeAction(player, "menu:main");
    assertHistory(player, "menu:main");

    // Enter shop
    executeAction(player, "shop:browse");
    assertHistory(player, "menu:main", "shop:browse");

    // Select category
    executeAction(player, "shop:category?cat=weapons");
    assertHistory(player, "menu:main", "shop:browse", "shop:category?cat=weapons");

    // View item
    executeAction(player, "shop:item?id=sword");
    assertHistory(player, "menu:main", "shop:browse", "shop:category?cat=weapons", "shop:item?id=sword");

    // Back to category
    executeAction(player, TubingGuiActions.BACK);
    assertHistory(player, "menu:main", "shop:browse", "shop:category?cat=weapons");

    // Back to shop
    executeAction(player, TubingGuiActions.BACK);
    assertHistory(player, "menu:main", "shop:browse");
}
```

### 10. Handle Edge Cases

Consider what happens with empty history:

```java
@GuiAction("action:something")
public TubingGui doSomething(Player player) {
    // What if player navigates directly here (command/external)?
    // History might be empty - BACK would close GUI

    // Option 1: Redirect to main menu if no history
    if (!hasHistory(player)) {
        return "menu:main";
    }

    // Option 2: Add breadcrumb trail
    TubingGui.Builder builder = new TubingGui.Builder("Something", 54);
    if (hasHistory(player)) {
        builder.addItem(createBackButton(49));
    } else {
        builder.addItem(createMainMenuButton(49));
    }

    return builder.build();
}
```

## Common Pitfalls

### Pitfall 1: Not Using overrideHistory for Pagination

**Problem:**
```java
// Bad - every page creates history entry
@GuiAction(value = "shop:page", overrideHistory = false)
public TubingGui changePage(@GuiParam("page") int page) { }

// History after browsing 5 pages:
// ["menu:main", "shop:page?page=1", "shop:page?page=2", "shop:page?page=3", "shop:page?page=4", "shop:page?page=5"]
// Player must click back 5 times to exit!
```

**Solution:**
```java
// Good - pagination replaces history
@GuiAction(value = "shop:page", overrideHistory = true)
public TubingGui changePage(@GuiParam("page") int page) { }

// History after browsing 5 pages:
// ["menu:main", "shop:page?page=5"]
// One click back to exit
```

### Pitfall 2: Using skipHistory for Content

**Problem:**
```java
// Bad - viewing items doesn't add to history
@GuiAction(value = "shop:item", skipHistory = true)
public TubingGui viewItem(@GuiParam("item") String itemId) { }

// Result: Back button skips over items entirely
// Player views: browse → item1 → item2 → item3
// Click back: Returns directly to browse, can't navigate back through items
```

**Solution:**
```java
// Good - each item in history
@GuiAction(value = "shop:item", overrideHistory = false, skipHistory = false)
public TubingGui viewItem(@GuiParam("item") String itemId) { }

// Player can navigate back: item3 → item2 → item1 → browse
```

### Pitfall 3: Forgetting Back Buttons

**Problem:**
```java
// Bad - no back button
public TubingGui buildShopGui() {
    return new TubingGui.Builder("Shop", 54)
        .addItem(/* items */)
        .build(); // Player stuck! Must close inventory manually
}
```

**Solution:**
```java
// Good - back button included
public TubingGui buildShopGui() {
    return new TubingGui.Builder("Shop", 54)
        .addItem(createBackButton(49))
        .addItem(/* items */)
        .build();
}
```

### Pitfall 4: Not Preserving State

**Problem:**
```java
// Bad - state not in action query
@GuiAction("shop:browse")
public TubingGui browseShop(Player player) {
    String currentFilter = playerSessionData.get(player).getFilter();
    return buildShop(currentFilter);
}
// History: "shop:browse" (no filter)
// Back button returns with default filter, loses user's selection
```

**Solution:**
```java
// Good - state in action query
@GuiAction("shop:browse")
public TubingGui browseShop(Player player,
                           @GuiParam(value = "filter", defaultValue = "all") String filter) {
    return buildShop(filter);
}
// History: "shop:browse?filter=weapons"
// Back button returns with correct filter preserved
```

### Pitfall 5: Mixing Navigation Styles

**Problem:**
```java
// Inconsistent - some views use history, others don't
@GuiAction(value = "view:item1", overrideHistory = false)
public TubingGui viewItem1() { }

@GuiAction(value = "view:item2", skipHistory = true)
public TubingGui viewItem2() { }

@GuiAction(value = "view:item3", overrideHistory = true)
public TubingGui viewItem3() { }
// Result: Confusing, unpredictable navigation
```

**Solution:**
```java
// Consistent - all content views use same pattern
@GuiAction(value = "view:item", overrideHistory = false)
public TubingGui viewItem(@GuiParam("id") String itemId) { }
// Result: Predictable, intuitive navigation
```

## Related Topics

- **[GUI Actions](GUI-Actions.md)** - Action routing, parameter binding, and return types
- **[GUI Controllers](GUI-Controllers.md)** - Controller architecture and action methods
- **[GUI Building](GUI-Building.md)** - Programmatic GUI construction with navigation
- **[GUI Templates](GUI-Templates.md)** - Template-based GUIs with navigation actions
- **[GUI Setup](GUI-Setup.md)** - Framework initialization and lifecycle
