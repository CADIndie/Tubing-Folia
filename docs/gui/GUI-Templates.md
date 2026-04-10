# GUI Templates

This guide covers Tubing's XML-based GUI template system powered by Freemarker. Templates allow you to create dynamic, reusable GUIs with data binding, conditional rendering, and loops. For programmatic GUI building, see [GUI Building](GUI-Building.md).

## Overview

Tubing's template system combines XML structure with Freemarker's template engine to provide:

- **XML-Based Structure**: Define GUI layouts using semantic XML markup
- **Freemarker Integration**: Use Freemarker's powerful templating features (variables, conditionals, loops)
- **Data Binding**: Pass data from controllers to templates for dynamic rendering
- **Configuration Integration**: Access plugin configuration values directly in templates
- **Permission Support**: Show/hide elements based on player permissions
- **Reusable Components**: Create template fragments that can be included in multiple GUIs
- **Style System**: Apply consistent styling with CSS-like class and ID selectors

The template rendering pipeline:
1. Controller returns `GuiTemplate` with template path and parameters
2. Freemarker processes the template with provided data
3. XML parser converts the rendered template to TubingGui objects
4. Style system applies any configured styles
5. GUI is displayed to the player

## Template Basics

### Creating Your First Template

Templates are stored in your plugin's resources directory. Create a file at `src/main/resources/templates/my-menu.xml`:

```xml
<TubingGui size="27">
    <title color="&6">My Menu</title>

    <GuiItem slot="13" material="DIAMOND" name="Click Me">
        <Name color="&a">Welcome!</Name>
        <Lore>
            <LoreLine color="&7">This is a simple template</LoreLine>
        </Lore>
    </GuiItem>
</TubingGui>
```

### Using Templates in Controllers

Return a `GuiTemplate` from your controller action:

```java
@GuiController
public class MenuController {

    @GuiAction("menu:show")
    public GuiTemplate showMenu(Player player) {
        Map<String, Object> params = new HashMap<>();
        params.put("playerName", player.getName());

        return GuiTemplate.template("templates/my-menu.xml", params);
    }
}
```

The `GuiTemplate.template()` method takes:
- **template**: Path to the template file (relative to resources root)
- **params**: Map of variables to make available in the template

## Freemarker Template Engine

Tubing uses Freemarker 2.3.31 for template processing. This section covers the most useful Freemarker features for GUI templates.

### Variable Interpolation

Use `${variable}` to insert variable values:

```xml
<TubingGui size="27">
    <title color="&6">Welcome ${playerName}!</title>

    <GuiItem slot="10" material="PLAYER_HEAD" name="${playerName}">
        <Name color="&e">${playerName}'s Profile</Name>
        <Lore>
            <LoreLine color="&7">Level: ${playerLevel}</LoreLine>
            <LoreLine color="&7">Balance: $${playerBalance}</LoreLine>
        </Lore>
    </GuiItem>
</TubingGui>
```

In your controller:

```java
@GuiAction("profile:view")
public GuiTemplate viewProfile(Player player) {
    Map<String, Object> params = new HashMap<>();
    params.put("playerName", player.getName());
    params.put("playerLevel", getPlayerLevel(player));
    params.put("playerBalance", getBalance(player));

    return GuiTemplate.template("templates/profile.xml", params);
}
```

### Conditional Rendering

Use `<#if>` directives to conditionally include elements:

```xml
<TubingGui size="27">
    <title color="&6">Shop</title>

    <#if hasVipAccess>
        <GuiItem slot="10" material="EMERALD" name="VIP Section">
            <Name color="&a">VIP Items</Name>
            <Lore>
                <LoreLine color="&7">Exclusive items for VIP members</LoreLine>
            </Lore>
        </GuiItem>
    </#if>

    <#if playerBalance >= 1000>
        <GuiItem slot="11" material="DIAMOND" name="Premium Items">
            <Name color="&b">Premium Shop</Name>
        </GuiItem>
    <#else>
        <GuiItem slot="11" material="IRON_INGOT" name="Basic Items">
            <Name color="&7">Basic Shop</Name>
        </GuiItem>
    </#if>
</TubingGui>
```

**Supported Operators:**
- `==` - Equals
- `!=` - Not equals
- `>` - Greater than
- `>=` - Greater than or equal
- `<` - Less than
- `<=` - Less than or equal
- `&&` - Logical AND
- `||` - Logical OR
- `!` - Logical NOT

### Loops

Use `<#list>` to iterate over collections:

```xml
<TubingGui size="54">
    <title color="&6">Shop - Browse Items</title>

    <#list items as item>
        <GuiItem slot="${item.slot}"
                 material="${item.material}"
                 amount="${item.quantity}"
                 onLeftClick="shop:buy?item=${item.id}">
            <Name color="&e">${item.name}</Name>
            <Lore>
                <LoreLine color="&7">${item.description}</LoreLine>
                <LoreLine color="&a">Price: $${item.price}</LoreLine>
                <LoreLine color="&7">Click to purchase</LoreLine>
            </Lore>
        </GuiItem>
    </#list>
</TubingGui>
```

In your controller:

```java
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player) {
    List<ShopItem> items = shopService.getItems();

    // Add slot numbers to items
    int slot = 0;
    for (ShopItem item : items) {
        item.setSlot(slot++);
    }

    Map<String, Object> params = new HashMap<>();
    params.put("items", items);

    return GuiTemplate.template("templates/shop-browse.xml", params);
}
```

**Loop Features:**

```xml
<#list items as item>
    Slot: ${item?index}           <!-- 0-based index -->
    <#if item?is_first>First!</#if>
    <#if item?is_last>Last!</#if>
    <#if item?has_next>Not last</#if>
</#list>
```

### Variables and Assignments

Use `<#assign>` to create or modify variables:

```xml
<TubingGui size="27">
    <#assign baseSlot = 10>
    <#assign vipColor = hasVip?then("&a", "&7")>

    <GuiItem slot="${baseSlot}" material="DIAMOND">
        <Name color="${vipColor}">Premium Section</Name>
    </GuiItem>

    <#assign nextSlot = baseSlot + 1>
    <GuiItem slot="${nextSlot}" material="GOLD_INGOT">
        <Name color="&e">Gold Shop</Name>
    </GuiItem>
</TubingGui>
```

### Built-in Functions

Freemarker provides many useful built-in functions:

```xml
<!-- String operations -->
<LoreLine>${playerName?upper_case}</LoreLine>
<LoreLine>${playerName?lower_case}</LoreLine>
<LoreLine>${description?cap_first}</LoreLine>
<LoreLine>${text?trim}</LoreLine>

<!-- Number formatting -->
<LoreLine>Balance: $${balance?string["0.00"]}</LoreLine>
<LoreLine>Count: ${count?string["000"]}</LoreLine>

<!-- Collections -->
<LoreLine>Items: ${items?size}</LoreLine>
<#if items?has_content>Has items</#if>

<!-- Default values -->
<LoreLine>${optionalValue!"Default Value"}</LoreLine>
<LoreLine>Level: ${level!0}</LoreLine>

<!-- Null checks -->
<#if variable??>Variable exists</#if>
```

### Ternary Operator

Use `?then()` for inline conditionals:

```xml
<GuiItem slot="13"
         material="${isActive?then('EMERALD', 'REDSTONE')}"
         amount="${count?then(count, 1)}">
    <Name color="${hasPermission?then('&a', '&c')}">
        ${isActive?then('Active', 'Inactive')}
    </Name>
</GuiItem>
```

### Includes

Include other template files for reusability:

```xml
<!-- templates/common/header.xml -->
<GuiItem slot="0" material="STAINED_GLASS_PANE" name=" ">
    <Name color="&7"> </Name>
</GuiItem>

<!-- templates/shop.xml -->
<TubingGui size="54">
    <title color="&6">Shop</title>

    <#include "templates/common/header.xml">

    <!-- Rest of shop GUI -->
</TubingGui>
```

### Macros

Define reusable template fragments:

```xml
<#macro navButton slot icon text action>
    <GuiItem slot="${slot}" material="${icon}" onLeftClick="${action}">
        <Name color="&e">${text}</Name>
    </GuiItem>
</#macro>

<TubingGui size="27">
    <title color="&6">Navigation</title>

    <@navButton slot=0 icon="ARROW_LEFT" text="Back" action="menu:back"/>
    <@navButton slot=8 icon="BARRIER" text="Close" action="menu:close"/>
</TubingGui>
```

## XML Template Structure

### Root Element: TubingGui

Every template must have a `<TubingGui>` root element:

```xml
<TubingGui size="54"
           onClose="menu:closed"
           interactableSlots="10..20,25">
    <!-- GUI contents -->
</TubingGui>
```

**Attributes:**

- `size` - Inventory size (9, 18, 27, 36, 45, or 54) - Default: 54
- `onClose` - Action to execute when GUI is closed - Optional
- `interactableSlots` - Slots where players can place items - Optional
- `id` - Style ID for applying styles - Optional
- `class` - Style classes (space-separated) - Optional

**Interactable Slots Syntax:**

```xml
<!-- Single slot -->
interactableSlots="10"

<!-- Multiple slots -->
interactableSlots="10,11,12"

<!-- Range of slots -->
interactableSlots="10..20"

<!-- Mixed -->
interactableSlots="0..8,45..53"
```

### Title Element

Define the GUI title:

```xml
<title color="&6">My Shop</title>

<!-- Or with text parts -->
<title>
    <t color="&6">Shop</t>
    <t color="&7"> - </t>
    <t color="&e">Browse Items</t>
</title>
```

**Attributes:**
- `color` - Default color for the title (Minecraft color codes)

### GuiItem Element

Define items in the GUI:

```xml
<GuiItem slot="13"
         material="DIAMOND_SWORD"
         amount="1"
         enchanted="false"
         onLeftClick="action:left"
         onRightClick="action:right"
         onLeftShiftClick="action:shift_left"
         onRightShiftClick="action:shift_right"
         onMiddleClick="action:middle"
         permission="myplugin.admin"
         if="true">
    <!-- Item contents -->
</GuiItem>
```

**Attributes:**

- `slot` - Inventory slot (0-53) - **Required**
- `material` - Bukkit Material enum name - **Required** (or use `materialUrl`)
- `materialUrl` - Base64 player head texture - Alternative to `material`
- `amount` - Item stack size (1-64) - Default: 1
- `enchanted` - Add enchant glow effect - Default: false
- `onLeftClick` - Action for left click - Optional
- `onRightClick` - Action for right click - Optional
- `onLeftShiftClick` - Action for shift+left click - Optional
- `onRightShiftClick` - Action for shift+right click - Optional
- `onMiddleClick` - Action for middle click - Optional
- `permission` - Required permission to see item - Optional
- `if` - Conditional rendering (true/false) - Optional
- `id` - Style ID - Optional
- `class` - Style classes - Optional

**Material URL Example:**

```xml
<GuiItem slot="10"
         materialUrl="eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTU0ZDlhNGMyZTkzODI2YTYzNWI4ZGIzZjA2ZjQ1MjQxZGQ3MjlkMzU0YTlmOTJkZDRkYWZjYmEifX19">
    <Name color="&e">Custom Player Head</Name>
</GuiItem>
```

### Name Element

Define the item's display name:

```xml
<!-- Simple name -->
<Name color="&e">Diamond Sword</Name>

<!-- Multi-colored name -->
<Name>
    <t color="&6">Legendary</t>
    <t color="&7"> </t>
    <t color="&b">Sword</t>
</Name>
```

**Attributes:**
- `color` - Default color for the name
- `id` - Style ID - Optional
- `class` - Style classes - Optional

### Lore Element

Define item lore (description):

```xml
<Lore permission="myplugin.view" if="true">
    <LoreLine color="&7">A powerful weapon</LoreLine>
    <LoreLine color="&7">that deals massive damage.</LoreLine>
    <LoreLine color="&a">Damage: +10</LoreLine>
</Lore>
```

**Lore Attributes:**
- `permission` - Required permission to see lore - Optional
- `if` - Conditional rendering - Optional

### LoreLine Element

Individual lore lines:

```xml
<LoreLine color="&7" permission="admin.view" if="true">
    Admin only line
</LoreLine>

<!-- Multi-colored line -->
<LoreLine>
    <t color="&7">Price: </t>
    <t color="&a">$100</t>
</LoreLine>
```

**Attributes:**
- `color` - Default color for the line
- `permission` - Required permission to see line - Optional
- `if` - Conditional rendering - Optional
- `id` - Style ID - Optional
- `class` - Style classes - Optional

### Text Parts (`<t>` element)

Create multi-colored text within Name or LoreLine:

```xml
<Name>
    <t color="&6">Legendary</t>
    <t color="&7"> - </t>
    <t color="&c">Sword of Fire</t>
</Name>

<LoreLine>
    <t color="&7">Level: </t>
    <t color="&e">${playerLevel}</t>
    <t color="&7"> | </t>
    <t color="&a">$${balance}</t>
</LoreLine>
```

**Attributes:**
- `color` - Text color
- `id` - Style ID - Optional
- `class` - Style classes - Optional

## Template Variables

Templates have access to several built-in variables in addition to your custom parameters.

### Built-in Variables

#### $config

Access plugin configuration values:

```xml
<GuiItem slot="10" material="DIAMOND">
    <Name color="&e">${$config.get("shop.currency-symbol")}${itemPrice}</Name>
    <Lore>
        <LoreLine>Shop: ${$config.get("shop.name")}</LoreLine>
        <LoreLine>Tax Rate: ${$config.get("shop.tax-rate")}%</LoreLine>
    </Lore>
</GuiItem>
```

The `$config.get()` method accepts a property path and returns the value from your plugin's configuration files.

#### $permissions

Check player permissions:

```xml
<#if $permissions.has(player, "shop.vip")>
    <GuiItem slot="10" material="EMERALD">
        <Name color="&a">VIP Section</Name>
    </GuiItem>
</#if>

<GuiItem slot="11" material="${$permissions.has(player, 'admin')?then('DIAMOND', 'IRON_INGOT')}">
    <Name>Shop Access</Name>
</GuiItem>
```

#### statics

Access static Java methods and constants:

```xml
<#assign Material = statics['org.bukkit.Material']>
<#assign ChatColor = statics['org.bukkit.ChatColor']>

<GuiItem slot="10" material="${Material.DIAMOND}">
    <Name>${ChatColor.GOLD}Golden Item</Name>
</GuiItem>
```

### Custom Parameters

Pass any Java object as a parameter:

```java
@GuiAction("shop:view")
public GuiTemplate viewShop(Player player) {
    Map<String, Object> params = new HashMap<>();

    // Simple types
    params.put("playerName", player.getName());
    params.put("balance", getBalance(player));
    params.put("isVip", hasVipRank(player));

    // Collections
    params.put("items", shopService.getItems());
    params.put("categories", Arrays.asList("Weapons", "Armor", "Tools"));

    // Custom objects
    ShopConfig config = new ShopConfig();
    config.setTaxRate(0.15);
    params.put("shopConfig", config);

    // Maps
    Map<String, Integer> prices = new HashMap<>();
    prices.put("diamond", 1000);
    prices.put("emerald", 500);
    params.put("prices", prices);

    return GuiTemplate.template("templates/shop.xml", params);
}
```

In the template:

```xml
<title>${playerName}'s Shop - Balance: $${balance}</title>

<#list items as item>
    <GuiItem slot="${item?index}" material="${item.material}">
        <Name color="&e">${item.name}</Name>
        <Lore>
            <LoreLine>Price: $${prices[item.id]}</LoreLine>
            <LoreLine>Tax: ${shopConfig.taxRate * 100}%</LoreLine>
        </Lore>
    </GuiItem>
</#list>
```

### Accessing Object Properties

Freemarker can access Java object properties using dot notation or bracket notation:

```xml
<!-- Dot notation (calls getName()) -->
<LoreLine>${item.name}</LoreLine>

<!-- Bracket notation -->
<LoreLine>${item["name"]}</LoreLine>

<!-- Nested properties -->
<LoreLine>${player.location.world.name}</LoreLine>

<!-- Method calls -->
<LoreLine>${item.getPrice()}</LoreLine>

<!-- Map access -->
<LoreLine>${priceMap["diamond"]}</LoreLine>
```

## Configuration Integration

The `config|` prefix allows you to bind configuration values directly in XML attributes.

### Using config| Prefix

```xml
<TubingGui size="config|gui.shop.size">
    <title color="config|gui.shop.title-color">Shop</title>

    <GuiItem slot="10"
             material="config|shop.currency-item"
             amount="config|shop.display-amount">
        <Name color="config|colors.primary">Balance</Name>
    </GuiItem>
</TubingGui>
```

This references your plugin's configuration:

```yaml
gui:
  shop:
    size: 54
    title-color: "&6"

shop:
  currency-item: "GOLD_INGOT"
  display-amount: 1

colors:
  primary: "&e"
```

### Configuration Resolution

The config resolver:
1. Parses the property path (e.g., "gui.shop.size")
2. Looks up the value in loaded configuration files
3. Replaces the attribute value before template processing
4. Throws an exception if the property doesn't exist

### Mixing Config and Variables

You can mix configuration bindings with Freemarker variables:

```xml
<GuiItem slot="config|slots.info" material="${playerMaterial}">
    <Name color="config|colors.primary">${playerName}</Name>
    <Lore>
        <LoreLine color="config|colors.secondary">Level: ${level}</LoreLine>
    </Lore>
</GuiItem>
```

## Permission Checking

Templates support permission-based rendering at multiple levels.

### Item-Level Permissions

Hide entire items based on permissions:

```xml
<GuiItem slot="10"
         material="DIAMOND"
         permission="shop.vip">
    <Name>VIP Only Item</Name>
</GuiItem>

<!-- Item only shown to players with "shop.vip" permission -->
```

### Lore-Level Permissions

Show/hide lore sections:

```xml
<GuiItem slot="10" material="DIAMOND">
    <Name>Shop Item</Name>
    <Lore permission="shop.view-prices">
        <LoreLine>Price: $100</LoreLine>
        <LoreLine>Tax: 15%</LoreLine>
    </Lore>
</GuiItem>
```

### Line-Level Permissions

Control individual lore lines:

```xml
<Lore>
    <LoreLine>Available to all players</LoreLine>
    <LoreLine permission="shop.admin">Admin cost: $50</LoreLine>
    <LoreLine permission="shop.vip">VIP discount: 20%</LoreLine>
</Lore>
```

### Combining with Freemarker

Use `$permissions` for complex logic:

```xml
<#if $permissions.has(player, "shop.admin")>
    <GuiItem slot="53" material="REDSTONE">
        <Name color="&c">Admin Tools</Name>
    </GuiItem>
</#if>

<GuiItem slot="10" material="DIAMOND">
    <Name color="${$permissions.has(player, 'shop.vip')?then('&a', '&7')}">
        ${$permissions.has(player, 'shop.vip')?then('VIP ', '')}Shop
    </Name>
</GuiItem>
```

## Conditional Rendering

Multiple approaches to conditional rendering exist in templates.

### Using if Attribute

The `if` attribute controls element visibility:

```xml
<GuiItem slot="10" material="DIAMOND" if="${hasPermission}">
    <Name>Conditional Item</Name>
</GuiItem>

<Lore if="${showDetails}">
    <LoreLine>Detail line 1</LoreLine>
    <LoreLine>Detail line 2</LoreLine>
</Lore>

<LoreLine if="${balance > 1000}">
    You're rich!
</LoreLine>
```

The `if` attribute must evaluate to the string "true" to show the element.

### Using Freemarker Conditionals

Use `<#if>` directives for more complex logic:

```xml
<#if playerLevel >= 10>
    <GuiItem slot="10" material="DIAMOND_SWORD">
        <Name color="&a">Advanced Weapons</Name>
    </GuiItem>
<#elseif playerLevel >= 5>
    <GuiItem slot="10" material="IRON_SWORD">
        <Name color="&7">Intermediate Weapons</Name>
    </GuiItem>
<#else>
    <GuiItem slot="10" material="WOOD_SWORD">
        <Name color="&8">Basic Weapons</Name>
    </GuiItem>
</#if>

<#if balance > 10000>
    <LoreLine color="&a">Balance: High</LoreLine>
<#elseif balance > 1000>
    <LoreLine color="&e">Balance: Medium</LoreLine>
<#else>
    <LoreLine color="&c">Balance: Low</LoreLine>
</#if>
```

### Complex Conditions

Combine multiple conditions:

```xml
<#if (playerLevel >= 10) && $permissions.has(player, "shop.advanced")>
    <GuiItem slot="10" material="DIAMOND">
        <Name color="&b">Premium Section</Name>
    </GuiItem>
</#if>

<#if (balance >= 1000) || hasVipRank>
    <GuiItem slot="11" material="EMERALD">
        <Name color="&a">Special Offers</Name>
    </GuiItem>
</#if>
```

### Conditional Attributes

Use ternary operators for conditional attributes:

```xml
<GuiItem slot="10"
         material="${isActive?then('EMERALD', 'REDSTONE')}"
         amount="${quantity > 0?then(quantity, 1)}">
    <Name color="${isPremium?then('&6', '&7')}">
        ${isActive?then('Active', 'Inactive')} ${itemName}
    </Name>
</GuiItem>
```

## Creating Reusable Templates

### Template Organization

Organize templates into logical directories:

```
src/main/resources/
└── templates/
    ├── shop/
    │   ├── browse.xml
    │   ├── category.xml
    │   └── checkout.xml
    ├── admin/
    │   ├── manage.xml
    │   └── logs.xml
    ├── common/
    │   ├── header.xml
    │   ├── footer.xml
    │   └── navigation.xml
    └── components/
        ├── item-display.xml
        └── pagination.xml
```

### Using Includes

Include template fragments:

```xml
<!-- templates/common/header.xml -->
<#list 0..8 as slot>
    <GuiItem slot="${slot}" material="STAINED_GLASS_PANE" name=" ">
        <Name color="&7"> </Name>
    </GuiItem>
</#list>

<!-- templates/common/footer.xml -->
<GuiItem slot="45" material="ARROW" onLeftClick="menu:back">
    <Name color="&e">Back</Name>
</GuiItem>
<GuiItem slot="49" material="BARRIER" onLeftClick="menu:close">
    <Name color="&c">Close</Name>
</GuiItem>

<!-- templates/shop/browse.xml -->
<TubingGui size="54">
    <title color="&6">Shop</title>

    <#include "templates/common/header.xml">

    <!-- Main content -->
    <#list items as item>
        <GuiItem slot="${10 + item?index}" material="${item.material}">
            <Name>${item.name}</Name>
        </GuiItem>
    </#list>

    <#include "templates/common/footer.xml">
</TubingGui>
```

### Using Macros

Create reusable components with macros:

```xml
<!-- templates/components/item-display.xml -->
<#macro displayItem item slot>
    <GuiItem slot="${slot}"
             material="${item.material}"
             amount="${item.quantity}"
             onLeftClick="shop:buy?item=${item.id}">
        <Name color="&e">${item.name}</Name>
        <Lore>
            <LoreLine color="&7">${item.description}</LoreLine>
            <LoreLine color="&a">Price: $${item.price}</LoreLine>
            <LoreLine color="&7">Click to purchase</LoreLine>
        </Lore>
    </GuiItem>
</#macro>

<!-- templates/shop/browse.xml -->
<TubingGui size="54">
    <#include "templates/components/item-display.xml">

    <title>Shop</title>

    <#list items as item>
        <@displayItem item=item slot=(10 + item?index)/>
    </#list>
</TubingGui>
```

### Pagination Template

Create a reusable pagination component:

```xml
<#macro pagination page totalPages baseAction>
    <#if page > 0>
        <GuiItem slot="45"
                 material="ARROW"
                 onLeftClick="${baseAction}?page=${page - 1}">
            <Name color="&e">Previous Page</Name>
        </GuiItem>
    </#if>

    <GuiItem slot="49" material="PAPER">
        <Name color="&a">Page ${page + 1} / ${totalPages}</Name>
    </GuiItem>

    <#if page < totalPages - 1>
        <GuiItem slot="53"
                 material="ARROW"
                 onLeftClick="${baseAction}?page=${page + 1}">
            <Name color="&e">Next Page</Name>
        </GuiItem>
    </#if>
</#macro>
```

Use it in your templates:

```xml
<TubingGui size="54">
    <#include "templates/components/pagination.xml">

    <title>Items - Page ${currentPage + 1}</title>

    <!-- Display items -->
    <#list pageItems as item>
        <GuiItem slot="${item?index}" material="${item.material}">
            <Name>${item.name}</Name>
        </GuiItem>
    </#list>

    <@pagination page=currentPage totalPages=totalPages baseAction="shop:browse"/>
</TubingGui>
```

## Best Practices

### 1. Separate Logic from Presentation

Keep business logic in controllers, not templates:

**Bad:**
```xml
<#assign filteredItems = []>
<#list items as item>
    <#if item.price > 100 && item.category == "weapons">
        <#assign filteredItems = filteredItems + [item]>
    </#if>
</#list>
```

**Good:**
```java
// In controller
List<Item> expensiveWeapons = items.stream()
    .filter(i -> i.getPrice() > 100)
    .filter(i -> i.getCategory().equals("weapons"))
    .collect(Collectors.toList());

params.put("expensiveWeapons", expensiveWeapons);
```

### 2. Use Meaningful Variable Names

**Bad:**
```java
params.put("l", items);
params.put("p", player);
params.put("c", config);
```

**Good:**
```java
params.put("shopItems", items);
params.put("currentPlayer", player);
params.put("shopConfig", config);
```

### 3. Extract Common Components

Create reusable macros for repeated patterns:

```xml
<!-- Navigation buttons -->
<#macro navButtons backAction closeAction>
    <GuiItem slot="45" material="ARROW" onLeftClick="${backAction}">
        <Name color="&e">Back</Name>
    </GuiItem>
    <GuiItem slot="53" material="BARRIER" onLeftClick="${closeAction}">
        <Name color="&c">Close</Name>
    </GuiItem>
</#macro>

<!-- Confirmation dialog -->
<#macro confirmDialog confirmAction cancelAction>
    <GuiItem slot="11" material="EMERALD_BLOCK" onLeftClick="${confirmAction}">
        <Name color="&a">Confirm</Name>
    </GuiItem>
    <GuiItem slot="15" material="REDSTONE_BLOCK" onLeftClick="${cancelAction}">
        <Name color="&c">Cancel</Name>
    </GuiItem>
</#macro>
```

### 4. Handle Missing Data Gracefully

Use default values for optional parameters:

```xml
<title>${title!"Default Title"}</title>

<#if items?has_content>
    <#list items as item>
        <GuiItem slot="${item?index}" material="${item.material}">
            <Name>${item.name!"Unknown Item"}</Name>
        </GuiItem>
    </#list>
<#else>
    <GuiItem slot="13" material="BARRIER">
        <Name color="&c">No items available</Name>
    </GuiItem>
</#if>
```

### 5. Keep Templates Simple

If a template becomes too complex, split it into smaller templates:

**Bad:**
```xml
<!-- 300+ lines with complex nested logic -->
```

**Good:**
```xml
<TubingGui size="54">
    <#include "templates/shop/header.xml">
    <#include "templates/shop/categories.xml">
    <#include "templates/shop/featured-items.xml">
    <#include "templates/shop/footer.xml">
</TubingGui>
```

### 6. Document Template Parameters

Add comments documenting expected parameters:

```xml
<!--
  Shop Browse Template

  Required Parameters:
    - items: List<ShopItem> - Items to display
    - currentPage: int - Current page number (0-based)
    - totalPages: int - Total number of pages

  Optional Parameters:
    - category: String - Filter by category (default: "all")
    - sortBy: String - Sort field (default: "name")
-->
<TubingGui size="54">
    <!-- Template content -->
</TubingGui>
```

### 7. Use Configuration for Styling

Store colors and common values in configuration:

```xml
<!-- Instead of hardcoding -->
<Name color="&6">Shop</Name>

<!-- Use configuration -->
<Name color="config|colors.shop-primary">Shop</Name>
```

### 8. Validate Materials

Use valid Bukkit material names to avoid runtime errors:

```xml
<!-- Check Material enum values -->
<GuiItem slot="10" material="DIAMOND_SWORD">  <!-- Valid -->
<GuiItem slot="11" material="DIAMOND">        <!-- Valid -->
<GuiItem slot="12" material="INVALID_MAT">    <!-- Error! -->
```

### 9. Test with Edge Cases

Test templates with:
- Empty lists
- Null values
- Maximum/minimum values
- Special characters in strings
- Different permission combinations

```java
@Test
public void testEmptyShop() {
    Map<String, Object> params = new HashMap<>();
    params.put("items", Collections.emptyList());
    params.put("currentPage", 0);
    params.put("totalPages", 0);

    // Should not throw exception
    GuiTemplate template = GuiTemplate.template("templates/shop.xml", params);
}
```

### 10. Use Consistent Naming Conventions

Establish naming conventions for:
- Template files: `kebab-case.xml`
- Template directories: `lowercase`
- Parameters: `camelCase`
- Actions: `namespace:action`

```
templates/
├── shop/
│   ├── browse-items.xml
│   ├── item-details.xml
│   └── checkout-confirm.xml
└── admin/
    ├── player-manager.xml
    └── server-settings.xml
```

## Common Patterns

### Pagination

```java
// Controller
@GuiAction("shop:browse")
public GuiTemplate browseShop(Player player,
                              @GuiParam(value = "page", defaultValue = "0") int page) {
    int itemsPerPage = 45;
    List<ShopItem> allItems = shopService.getItems();
    int totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);

    int start = page * itemsPerPage;
    int end = Math.min(start + itemsPerPage, allItems.size());
    List<ShopItem> pageItems = allItems.subList(start, end);

    Map<String, Object> params = new HashMap<>();
    params.put("items", pageItems);
    params.put("currentPage", page);
    params.put("totalPages", totalPages);

    return GuiTemplate.template("templates/shop-browse.xml", params);
}
```

```xml
<!-- Template -->
<TubingGui size="54">
    <title>Shop - Page ${currentPage + 1}/${totalPages}</title>

    <#list items as item>
        <GuiItem slot="${item?index}" material="${item.material}">
            <Name>${item.name}</Name>
        </GuiItem>
    </#list>

    <#if currentPage > 0>
        <GuiItem slot="45" material="ARROW"
                 onLeftClick="shop:browse?page=${currentPage - 1}">
            <Name>&e&lPrevious</Name>
        </GuiItem>
    </#if>

    <#if currentPage < totalPages - 1>
        <GuiItem slot="53" material="ARROW"
                 onLeftClick="shop:browse?page=${currentPage + 1}">
            <Name>&e&lNext</Name>
        </GuiItem>
    </#if>
</TubingGui>
```

### Search/Filter

```java
@GuiAction("shop:search")
public GuiTemplate search(Player player,
                          @GuiParam(value = "query", defaultValue = "") String query,
                          @GuiParam(value = "category", defaultValue = "all") String category) {
    List<ShopItem> items = shopService.search(query, category);

    Map<String, Object> params = new HashMap<>();
    params.put("items", items);
    params.put("query", query);
    params.put("category", category);
    params.put("categories", shopService.getCategories());

    return GuiTemplate.template("templates/shop-search.xml", params);
}
```

### Confirmation Dialog

```java
@GuiAction("shop:confirm")
public GuiTemplate confirmPurchase(Player player,
                                   @GuiParam("item") String itemId) {
    ShopItem item = shopService.getItem(itemId);

    Map<String, Object> params = new HashMap<>();
    params.put("item", item);

    return GuiTemplate.template("templates/confirm-purchase.xml", params);
}
```

```xml
<TubingGui size="27">
    <title>Confirm Purchase</title>

    <!-- Display item -->
    <GuiItem slot="13" material="${item.material}">
        <Name color="&e">${item.name}</Name>
        <Lore>
            <LoreLine color="&7">Price: $${item.price}</LoreLine>
        </Lore>
    </GuiItem>

    <!-- Confirm button -->
    <GuiItem slot="11" material="EMERALD_BLOCK"
             onLeftClick="shop:purchase?item=${item.id}">
        <Name color="&a">&lCONFIRM</Name>
    </GuiItem>

    <!-- Cancel button -->
    <GuiItem slot="15" material="REDSTONE_BLOCK"
             onLeftClick="shop:browse">
        <Name color="&c">&lCANCEL</Name>
    </GuiItem>
</TubingGui>
```

## Troubleshooting

### Template Not Found

**Error:** `Could not load template: [templates/shop.xml]`

**Solutions:**
- Verify the template file exists in `src/main/resources/`
- Check the path is correct (relative to resources root)
- Ensure the file is included in your JAR (check Maven resources configuration)

### Freemarker Syntax Error

**Error:** `TemplateException: Syntax error in template`

**Solutions:**
- Check for missing closing tags (`</#if>`, `</#list>`, etc.)
- Verify variable names match parameters passed from controller
- Ensure proper escaping of special characters
- Use `${"$"}` to output a literal `$` character

### Invalid Material

**Error:** `IllegalArgumentException: No enum constant org.bukkit.Material.INVALID_MAT`

**Solutions:**
- Use valid Bukkit Material enum names
- Check material names match your Minecraft version
- Use variables for dynamic materials: `material="${item.material}"`

### Permission Not Working

**Problem:** Items show when they shouldn't

**Solutions:**
- Verify permission string matches your permission plugin configuration
- Check player has/doesn't have the expected permission
- Use `$permissions.has(player, "permission")` in template for debugging
- Ensure Player object is passed to template parameters

### Configuration Not Resolving

**Error:** `Unknown property defined in permission attribute: [shop.color]`

**Solutions:**
- Verify property exists in configuration files
- Check property path is correct (case-sensitive)
- Ensure configuration files are loaded before GUI is opened
- Use `$config.get("property")` for debugging

## See Also

- [GUI Building](GUI-Building.md) - Programmatic GUI construction
- [GUI Controllers](GUI-Controllers.md) - Controller patterns and return types
- [GUI Setup](GUI-Setup.md) - Initial setup and configuration
- [Freemarker Documentation](https://freemarker.apache.org/docs/) - Full Freemarker reference
