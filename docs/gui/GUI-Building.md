# GUI Building

This guide covers how to programmatically build GUIs using the TubingGui builder pattern. For template-based GUIs, see [GUI Templates](GUI-Templates.md).

## Overview

Tubing provides a fluent builder API for creating inventory GUIs programmatically. The builder pattern allows you to:

- Create custom inventory layouts with configurable rows and titles
- Add items to specific slots with custom appearances
- Define click handlers for different mouse actions
- Control inventory behavior with actions and refresh logic
- Build dynamic GUIs that respond to player actions

The GUI system consists of two main builders:
- `TubingGui.Builder` - Builds the inventory container
- `TubingGuiItem.Builder` - Builds individual items within the inventory

## Creating a Basic GUI

### Simple GUI Example

The simplest way to create a GUI is with a title and size:

```java
@IocBean
public class SimpleMenuService {

    public TubingGui buildMenu() {
        return new TubingGui.Builder("My Menu", 27) // 27 slots = 3 rows
            .addItem(
                new TubingGuiItem.Builder(null, 13) // Slot 13 (center of row 2)
                    .withItemStack(createItemStack())
                    .withLeftClickAction(TubingGuiActions.NOOP)
                    .build()
            )
            .build();
    }

    private TubingGuiItemStack createItemStack() {
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart("Click Me", "&a"));

        return new TubingGuiItemStack(
            Material.DIAMOND,
            name,
            false, // not enchanted
            Collections.emptyList() // no lore
        );
    }
}
```

## TubingGui Builder

### Constructor Options

The `TubingGui.Builder` has several constructor variations:

```java
// Simple constructor with string title and size
new TubingGui.Builder(String title, int size)

// Constructor with TubingGuiText for formatted titles
new TubingGui.Builder(TubingGuiText title, int size)

// Constructor with style ID for template integration
new TubingGui.Builder(StyleId guiId, String title, int size)

// Full constructor with interactable slots
new TubingGui.Builder(StyleId guiId, TubingGuiText title, int size, List<Integer> interactableSlots)
```

### Setting Inventory Properties

#### Title

The title appears at the top of the inventory:

```java
// Simple string title
TubingGui.Builder builder = new TubingGui.Builder("Shop Menu", 54);

// Formatted title with color
TubingGuiText title = new TubingGuiText();
title.addPart(new TubingGuiTextPart("Shop Menu", "&6")); // Gold color
TubingGui.Builder builder = new TubingGui.Builder(title, 54);
```

#### Rows and Size

The size parameter determines the number of slots. Valid sizes are multiples of 9 (1-6 rows):

```java
new TubingGui.Builder("1 Row", 9)    // 1 row (9 slots)
new TubingGui.Builder("2 Rows", 18)  // 2 rows (18 slots)
new TubingGui.Builder("3 Rows", 27)  // 3 rows (27 slots)
new TubingGui.Builder("4 Rows", 36)  // 4 rows (36 slots)
new TubingGui.Builder("5 Rows", 45)  // 5 rows (45 slots)
new TubingGui.Builder("6 Rows", 54)  // 6 rows (54 slots - maximum)
```

#### Close Action

Specify an action to execute when the GUI is closed:

```java
TubingGui gui = new TubingGui.Builder("Menu", 27)
    .closeAction("menu/home")
    .build();
```

## TubingGuiItem Builder

### Constructor

Create an item builder with a style ID (can be null) and slot position:

```java
new TubingGuiItem.Builder(StyleId id, int slot)
```

Slot numbers are 0-indexed:
- Slots 0-8: First row
- Slots 9-17: Second row
- Slots 18-26: Third row
- And so on...

### Creating Item Stacks

#### Basic Item Stack

```java
TubingGuiText name = new TubingGuiText();
name.addPart(new TubingGuiTextPart("Diamond Sword", "&b"));

TubingGuiItemStack itemStack = new TubingGuiItemStack(
    Material.DIAMOND_SWORD,
    name,
    false, // not enchanted
    Collections.emptyList() // no lore
);
```

#### Item Stack with Amount

```java
TubingGuiItemStack itemStack = new TubingGuiItemStack(
    16, // amount
    Material.GOLD_INGOT,
    name,
    false,
    Collections.emptyList()
);
```

#### Item Stack with Lore

```java
List<TubingGuiText> lore = new ArrayList<>();

TubingGuiText loreLine1 = new TubingGuiText();
loreLine1.addPart(new TubingGuiTextPart("This is a description", "&7"));
lore.add(loreLine1);

TubingGuiText loreLine2 = new TubingGuiText();
loreLine2.addPart(new TubingGuiTextPart("Click to buy!", "&e"));
lore.add(loreLine2);

TubingGuiItemStack itemStack = new TubingGuiItemStack(
    Material.DIAMOND,
    name,
    false,
    lore
);
```

#### Enchanted Appearance

Make items glow without actual enchantments:

```java
TubingGuiItemStack itemStack = new TubingGuiItemStack(
    Material.DIAMOND,
    name,
    true, // enchanted glow
    lore
);
```

#### Custom Skull Textures

Use custom player head textures with material URLs:

```java
TubingGuiItemStack itemStack = new TubingGuiItemStack(
    1, // amount
    "http://textures.minecraft.net/texture/abc123...", // texture URL
    name,
    false,
    lore
);
```

### Adding Items to Builder

```java
TubingGuiItem item = new TubingGuiItem.Builder(null, 10)
    .withItemStack(itemStack)
    .withLeftClickAction("shop/buy?item=diamond")
    .build();

TubingGui gui = new TubingGui.Builder("Shop", 27)
    .addItem(item)
    .build();
```

## Click Handlers and Actions

### Action Types

TubingGuiItem supports five types of click actions:

```java
TubingGuiItem item = new TubingGuiItem.Builder(null, slot)
    .withLeftClickAction("action/left")        // Left click
    .withRightClickAction("action/right")      // Right click
    .withMiddleClickAction("action/middle")    // Middle click (scroll wheel)
    .withLeftShiftClickAction("action/lshift") // Shift + left click
    .withRightShiftClickAction("action/rshift") // Shift + right click
    .build();
```

### Default Actions

If no action is specified, items default to `TubingGuiActions.NOOP` (no operation):

```java
TubingGuiItem item = new TubingGuiItem.Builder(null, slot)
    .withItemStack(itemStack)
    // No click actions - defaults to NOOP
    .build();
```

### Built-in Actions

Tubing provides built-in action constants:

```java
// Do nothing when clicked
.withLeftClickAction(TubingGuiActions.NOOP)

// Go back to previous GUI in history
.withLeftClickAction(TubingGuiActions.BACK)
```

### Action Query Format

Actions can include parameters using query string format:

```java
.withLeftClickAction("shop/buy?item=diamond&amount=1&price=100")
```

These parameters are automatically parsed and injected into your GUI controller methods. See [GUI Controllers](GUI-Controllers.md) for details.

### Click Action Best Practices

- Use descriptive action routes: `shop/buy` not `action1`
- Include all necessary data in action parameters
- Use `NOOP` for display-only items
- Use `BACK` for navigation items
- Consider different actions for left/right clicks to provide multiple options

## Complete Example

Here's a comprehensive example showing all features:

```java
@IocBean
public class ShopGuiService {

    public TubingGui buildShopGui(Player player, String category) {
        // Create title with formatting
        TubingGuiText title = new TubingGuiText();
        title.addPart(new TubingGuiTextPart("Shop - ", "&6"));
        title.addPart(new TubingGuiTextPart(category, "&e"));

        TubingGui.Builder builder = new TubingGui.Builder(title, 54)
            .closeAction("menu/main");

        // Add category items
        builder.addItem(createShopItem(10, "Diamond", Material.DIAMOND, 100));
        builder.addItem(createShopItem(11, "Gold Ingot", Material.GOLD_INGOT, 50));
        builder.addItem(createShopItem(12, "Iron Ingot", Material.IRON_INGOT, 25));

        // Add navigation items
        builder.addItem(createBackButton(49));
        builder.addItem(createInfoButton(4));

        return builder.build();
    }

    private TubingGuiItem createShopItem(int slot, String itemName, Material material, int price) {
        // Create item name
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart(itemName, "&a"));

        // Create lore
        List<TubingGuiText> lore = new ArrayList<>();

        TubingGuiText priceLine = new TubingGuiText();
        priceLine.addPart(new TubingGuiTextPart("Price: ", "&7"));
        priceLine.addPart(new TubingGuiTextPart("$" + price, "&6"));
        lore.add(priceLine);

        TubingGuiText actionLine = new TubingGuiText();
        actionLine.addPart(new TubingGuiTextPart("Left click: Buy 1", "&e"));
        lore.add(actionLine);

        TubingGuiText actionLine2 = new TubingGuiText();
        actionLine2.addPart(new TubingGuiTextPart("Right click: Buy stack", "&e"));
        lore.add(actionLine2);

        // Create item stack
        TubingGuiItemStack itemStack = new TubingGuiItemStack(
            1,
            material,
            name,
            false,
            lore
        );

        // Create GUI item with actions
        return new TubingGuiItem.Builder(null, slot)
            .withItemStack(itemStack)
            .withLeftClickAction("shop/buy?item=" + material.name() + "&amount=1&price=" + price)
            .withRightClickAction("shop/buy?item=" + material.name() + "&amount=64&price=" + (price * 64))
            .build();
    }

    private TubingGuiItem createBackButton(int slot) {
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart("Back", "&c"));

        TubingGuiItemStack itemStack = new TubingGuiItemStack(
            Material.ARROW,
            name,
            false,
            Collections.emptyList()
        );

        return new TubingGuiItem.Builder(null, slot)
            .withItemStack(itemStack)
            .withLeftClickAction(TubingGuiActions.BACK)
            .build();
    }

    private TubingGuiItem createInfoButton(int slot) {
        TubingGuiText name = new TubingGuiText();
        name.addPart(new TubingGuiTextPart("Information", "&b"));

        List<TubingGuiText> lore = new ArrayList<>();
        TubingGuiText info = new TubingGuiText();
        info.addPart(new TubingGuiTextPart("Click items to purchase", "&7"));
        lore.add(info);

        TubingGuiItemStack itemStack = new TubingGuiItemStack(
            Material.BOOK,
            name,
            false,
            lore
        );

        return new TubingGuiItem.Builder(null, slot)
            .withItemStack(itemStack)
            .withLeftClickAction(TubingGuiActions.NOOP)
            .build();
    }
}
```

## Opening GUIs

To open a GUI for a player, use the `GuiActionService`:

```java
@IocBean
public class ShopCommand extends AbstractCmd {

    private final GuiActionService guiActionService;
    private final ShopGuiService shopGuiService;

    public ShopCommand(CommandExceptionHandler exceptionHandler,
                      TubingPermissionService permissionService,
                      GuiActionService guiActionService,
                      ShopGuiService shopGuiService) {
        super(exceptionHandler, permissionService);
        this.guiActionService = guiActionService;
        this.shopGuiService = shopGuiService;
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        TubingGui gui = shopGuiService.buildShopGui(player, "General");
        guiActionService.showGui(player, gui);
        return true;
    }
}
```

Alternatively, return the GUI from a GUI controller method - see [GUI Controllers](GUI-Controllers.md).

## Refresh Behavior

When a GUI needs to update based on player actions:

### Rebuilding the GUI

The recommended approach is to rebuild the entire GUI:

```java
@GuiController
public class ShopController {

    private final ShopGuiService shopGuiService;
    private final GuiActionService guiActionService;

    public ShopController(ShopGuiService shopGuiService,
                         GuiActionService guiActionService) {
        this.shopGuiService = shopGuiService;
        this.guiActionService = guiActionService;
    }

    @GuiAction("shop/buy")
    public TubingGui buyItem(Player player,
                            @GuiParam("item") String item,
                            @GuiParam("amount") int amount) {
        // Process purchase
        processPurchase(player, item, amount);

        // Return rebuilt GUI with updated state
        return shopGuiService.buildShopGui(player, "General");
    }
}
```

### Keeping GUI Open

Return `GuiActionReturnType.KEEP_OPEN` to keep the current GUI open without changes:

```java
@GuiAction("shop/info")
public GuiActionReturnType showInfo(Player player) {
    player.sendMessage("This is the shop!");
    return GuiActionReturnType.KEEP_OPEN;
}
```

### Going Back

Return `GuiActionReturnType.BACK` to navigate to the previous GUI:

```java
@GuiAction("shop/cancel")
public GuiActionReturnType cancel(Player player) {
    player.sendMessage("Purchase cancelled");
    return GuiActionReturnType.BACK;
}
```

## Best Practices

### Design

- **Consistent Layouts**: Use the same slot positions for navigation items across GUIs
- **Visual Hierarchy**: Use enchanted items or special materials to highlight important items
- **Clear Labels**: Use descriptive names and lore to explain item functions
- **Color Coding**: Use consistent colors for different types of items (e.g., green for buy, red for back)

### Performance

- **Cache Static GUIs**: For GUIs that don't change, build once and reuse
- **Lazy Building**: Only build GUIs when needed, not at plugin startup
- **Efficient Rebuilding**: When refreshing, only rebuild what changed if possible

### Usability

- **Multiple Click Actions**: Provide different actions for left/right clicks
- **Back Navigation**: Always include a way to go back or close the GUI
- **Confirmation**: For destructive actions, show a confirmation GUI
- **Feedback**: Send messages to players after actions complete

### Code Organization

- **Separate Builders**: Create builder methods for complex items
- **Service Classes**: Keep GUI building logic in dedicated service classes
- **Reusable Components**: Create utility methods for common items (back buttons, info items, etc.)
- **Constants**: Define slot positions, colors, and materials as constants

### Example Organization

```java
@IocBean
public class GuiConstants {
    public static final int BACK_BUTTON_SLOT = 49;
    public static final int INFO_BUTTON_SLOT = 4;
    public static final String COLOR_SUCCESS = "&a";
    public static final String COLOR_ERROR = "&c";
    public static final String COLOR_INFO = "&7";
}

@IocBean
public class GuiItemFactory {

    public TubingGuiItem createBackButton(int slot) {
        // Reusable back button
    }

    public TubingGuiItem createInfoButton(int slot, String... infoLines) {
        // Reusable info button
    }

    public TubingGuiItem createConfirmButton(int slot, String action) {
        // Reusable confirm button
    }
}

@IocBean
public class ShopGuiService {

    private final GuiItemFactory itemFactory;

    public ShopGuiService(GuiItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    public TubingGui buildShopGui(Player player, String category) {
        // Use factory methods
    }
}
```

## Next Steps

- **[GUI Controllers](GUI-Controllers.md)** - Learn how to handle GUI actions
- **[GUI Templates](GUI-Templates.md)** - Use XML templates for easier GUI creation
- **[GUI History](GUI-History.md)** - Understand navigation and history management

## See Also

- [Commands](../bukkit/Commands.md) - Opening GUIs from commands
- [Event Listeners](../bukkit/Event-Listeners.md) - Responding to inventory events
- [Dependency Injection](../core/Dependency-Injection.md) - Injecting GUI services
