# Chat Input

This guide covers Tubing's chat input feature, which allows GUIs to capture player text input through the chat system. You'll learn how to request chat input from GUI controllers, validate and handle input, return to GUIs after input, and manage timeout and cancellation scenarios.

## Overview

The chat input system provides a seamless way to capture text input from players without leaving the GUI workflow. It offers:

- **ChatActionService**: Service for registering chat input handlers
- **Automatic Event Handling**: Intercepts chat events and routes them to handlers
- **Input Validation**: Throw exceptions to reject invalid input with error messages
- **GUI Workflow Integration**: Return to GUIs after input is captured
- **Automatic Cleanup**: Input handlers are removed after execution
- **Exception Handling**: Invalid input errors sent to players automatically
- **Simple API**: Lambda-based input handlers with minimal boilerplate

The chat input system is particularly useful for:
- Entering search queries
- Specifying custom values (prices, amounts, names)
- Text filtering and configuration
- Any scenario requiring flexible text input beyond GUI clicks

## How Chat Input Works

### Flow Overview

The typical chat input flow:

1. **GUI Interaction**: Player clicks an item in a GUI
2. **Request Input**: Controller method calls `chatActionService.requireInput()`
3. **GUI Closes**: GUI is closed automatically (player sees chat)
4. **Player Types**: Player enters text in chat
5. **Input Captured**: Chat event is intercepted by ChatActionListener
6. **Handler Executes**: Lambda processes the input
7. **Return to GUI**: Handler reopens GUI or performs action
8. **Cleanup**: Input handler is automatically removed

### Technical Implementation

The system consists of two main components:

**ChatActionService:**
- Maintains a map of active input handlers per player (UUID -> Consumer<String>)
- Provides `requireInput()` methods to register handlers
- Optionally sends a prompt message to the player

**ChatActionListener:**
- Listens for AsyncPlayerChatEvent with LOWEST priority
- Checks if player has an active input handler
- Cancels chat event to prevent broadcast
- Executes handler with chat message
- Removes handler after execution
- Sends exceptions as messages to player

## Requesting Chat Input

### Basic Usage

The simplest way to request chat input is with a prompt message:

```java
@GuiController
public class ShopController {

    private final ChatActionService chatActionService;
    private final GuiActionService guiActionService;

    public ShopController(ChatActionService chatActionService,
                         GuiActionService guiActionService) {
        this.chatActionService = chatActionService;
        this.guiActionService = guiActionService;
    }

    @GuiAction("shop:search")
    public void searchShop(Player player) {
        // Request input with prompt message
        chatActionService.requireInput(player, "&eEnter search term:", input -> {
            // Handle input
            TubingGui resultsGui = buildSearchResults(player, input);
            guiActionService.showGui(player, resultsGui);
        });
        // GUI automatically closes, player can type
    }
}
```

**Behavior:**
- GUI closes automatically when method returns void
- Prompt message is sent to player
- Player's next chat message is captured
- Lambda executes with the input
- Handler is removed after execution

### Without Prompt Message

You can register an input handler without sending a prompt:

```java
@GuiAction("shop:search")
public void searchShop(Player player) {
    // Send custom message using Messages or player.sendMessage()
    player.sendMessage(ChatColor.GOLD + "What are you looking for?");
    player.sendMessage(ChatColor.YELLOW + "Type 'cancel' to cancel search");

    chatActionService.requireInput(player, input -> {
        if (input.equalsIgnoreCase("cancel")) {
            TubingGui mainGui = buildMainShop(player);
            guiActionService.showGui(player, mainGui);
            return;
        }

        TubingGui resultsGui = buildSearchResults(player, input);
        guiActionService.showGui(player, resultsGui);
    });
}
```

**Use Cases:**
- Multi-line prompts with formatting
- Custom message styling
- Instructions requiring more explanation
- When you already sent a message earlier

### Capturing Context Variables

Use method parameters to capture context for the input handler:

```java
@GuiAction("shop:set-price")
public void setPrice(Player player,
                    @GuiParam("item") String itemId,
                    @GuiParam("category") String category) {
    // Context variables captured in lambda
    chatActionService.requireInput(
        player,
        "&eEnter new price for " + itemId + ":",
        input -> {
            // Context variables available here
            double price = Double.parseDouble(input);
            shopService.setPrice(itemId, price);

            // Return to category view with context
            TubingGui categoryGui = buildCategoryGui(player, category);
            guiActionService.showGui(player, categoryGui);
        }
    );
}
```

**Key Points:**
- Lambda can access method parameters
- Context is preserved across the input flow
- Use captured variables to maintain state
- No need for session storage in simple cases

## Input Validation and Handling

### Validating Input

Throw exceptions from the input handler to reject invalid input:

```java
@GuiAction("shop:set-price")
public void setPrice(Player player, @GuiParam("item") String itemId) {
    chatActionService.requireInput(player, "&eEnter price (1-10000):", input -> {
        // Validate input - throw exception on failure
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("&cPrice cannot be empty!");
        }

        double price;
        try {
            price = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("&cInvalid number format!");
        }

        if (price < 1 || price > 10000) {
            throw new IllegalArgumentException("&cPrice must be between 1 and 10000!");
        }

        // Input is valid - process it
        shopService.setPrice(itemId, price);
        player.sendMessage("&aPrice updated to " + price);

        // Return to GUI
        TubingGui shopGui = buildShopGui(player);
        guiActionService.showGui(player, shopGui);
    });
}
```

**Validation Behavior:**
- Exception message is sent to player automatically
- Input handler is removed after exception
- Player must re-trigger the action to try again
- GUI does not reopen automatically after error

### Validation Best Practices

**1. Provide Clear Error Messages:**

```java
// Good - specific error message
if (input.length() > 32) {
    throw new IllegalArgumentException("&cName too long! Maximum 32 characters.");
}

// Bad - vague error message
if (input.length() > 32) {
    throw new IllegalArgumentException("&cInvalid input!");
}
```

**2. Validate Early:**

```java
chatActionService.requireInput(player, "&eEnter amount:", input -> {
    // Validate first
    if (input.equalsIgnoreCase("cancel")) {
        player.sendMessage("&eCancelled");
        return;
    }

    int amount;
    try {
        amount = Integer.parseInt(input);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("&cPlease enter a valid number!");
    }

    if (amount <= 0) {
        throw new IllegalArgumentException("&cAmount must be positive!");
    }

    // Process after validation
    shopService.purchase(player, amount);
});
```

**3. Handle Edge Cases:**

```java
chatActionService.requireInput(player, "&eEnter player name:", input -> {
    // Trim whitespace
    String playerName = input.trim();

    // Handle empty after trim
    if (playerName.isEmpty()) {
        throw new IllegalArgumentException("&cPlayer name cannot be empty!");
    }

    // Check format
    if (!playerName.matches("[a-zA-Z0-9_]{3,16}")) {
        throw new IllegalArgumentException("&cInvalid player name format!");
    }

    // Check existence
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
        throw new IllegalArgumentException("&cPlayer '" + playerName + "' not found!");
    }

    // Process valid input
    processPlayer(player, target);
});
```

### Complex Validation Logic

For complex validation, create dedicated validation methods:

```java
@GuiController
public class AuctionController {

    private final ChatActionService chatActionService;
    private final GuiActionService guiActionService;
    private final AuctionService auctionService;

    @GuiAction("auction:create")
    public void createAuction(Player player, @GuiParam("item") String itemId) {
        chatActionService.requireInput(
            player,
            "&eEnter starting bid (format: 100.50):",
            input -> {
                double bid = validateBidInput(input);
                double minBid = auctionService.getMinimumBid();

                if (bid < minBid) {
                    throw new IllegalArgumentException(
                        "&cStarting bid must be at least " + minBid + "!"
                    );
                }

                auctionService.createAuction(player, itemId, bid);
                player.sendMessage("&aAuction created with starting bid: " + bid);

                TubingGui auctionGui = buildAuctionGui(player);
                guiActionService.showGui(player, auctionGui);
            }
        );
    }

    private double validateBidInput(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("&cBid cannot be empty!");
        }

        try {
            double value = Double.parseDouble(input);

            if (value <= 0) {
                throw new IllegalArgumentException("&cBid must be positive!");
            }

            if (value > 1000000) {
                throw new IllegalArgumentException("&cBid too large! Maximum 1,000,000");
            }

            // Round to 2 decimal places
            return Math.round(value * 100.0) / 100.0;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "&cInvalid bid format! Use numbers only (e.g., 100.50)"
            );
        }
    }
}
```

## Returning to GUIs

### Basic Return Pattern

The typical pattern is to reopen a GUI after processing input:

```java
@GuiAction("shop:search")
public void searchShop(Player player,
                      @CurrentAction String currentAction) {
    chatActionService.requireInput(player, "&eEnter search term:", input -> {
        // Process input
        List<ShopItem> results = shopService.search(input);

        // Build GUI with results
        TubingGui resultsGui = new TubingGui.Builder("Search: " + input, 54)
            .addItem(/* search results */)
            .build();

        // Show GUI to player
        guiActionService.showGui(player, resultsGui);
    });
}
```

### Preserving Navigation State

Capture the current action to preserve navigation history:

```java
@GuiAction("shop:filter")
public void filterShop(Player player,
                      @CurrentAction String currentAction,
                      @GuiParam("category") String category) {
    chatActionService.requireInput(player, "&eEnter filter term:", input -> {
        // Build GUI with preserved context
        TubingGui filteredGui = buildFilteredShop(player, category, input);

        // Show GUI - history is maintained
        guiActionService.showGui(player, filteredGui);
    });
}
```

### Conditional Return

Return to different GUIs based on input:

```java
@GuiAction("shop:create-item")
public void createItem(Player player) {
    chatActionService.requireInput(player, "&eEnter item name:", input -> {
        if (input.equalsIgnoreCase("cancel")) {
            // Return to main shop
            TubingGui mainGui = buildMainShop(player);
            guiActionService.showGui(player, mainGui);
            return;
        }

        if (shopService.itemExists(input)) {
            // Show error GUI
            TubingGui errorGui = buildErrorGui(
                player,
                "Item '" + input + "' already exists!"
            );
            guiActionService.showGui(player, errorGui);
            return;
        }

        // Create and show success GUI
        shopService.createItem(input);
        TubingGui successGui = buildSuccessGui(player, input);
        guiActionService.showGui(player, successGui);
    });
}
```

### Multi-Step Input Flow

Chain multiple input requests for complex workflows:

```java
@GuiAction("auction:create-full")
public void createFullAuction(Player player, @GuiParam("item") String itemId) {
    // Step 1: Get starting bid
    chatActionService.requireInput(
        player,
        "&eStep 1/2: Enter starting bid:",
        startingBidInput -> {
            double startingBid = validateBidInput(startingBidInput);

            // Step 2: Get duration
            chatActionService.requireInput(
                player,
                "&eStep 2/2: Enter duration in hours:",
                durationInput -> {
                    int hours = validateHoursInput(durationInput);

                    // Create auction with both inputs
                    auctionService.createAuction(
                        player,
                        itemId,
                        startingBid,
                        hours
                    );

                    player.sendMessage("&aAuction created!");
                    player.sendMessage("&7Starting bid: " + startingBid);
                    player.sendMessage("&7Duration: " + hours + " hours");

                    // Return to auction list
                    TubingGui auctionGui = buildAuctionGui(player);
                    guiActionService.showGui(player, auctionGui);
                }
            );
        }
    );
}
```

**Important Notes:**
- Each `requireInput()` call replaces any existing handler
- Validate at each step before proceeding
- Consider storing intermediate state in a session service for complex flows
- Provide clear step indicators ("Step 1/3") in prompts

## Timeout and Cancellation

### Automatic Cleanup

Input handlers are automatically removed after execution or exception:

```java
chatActionService.requireInput(player, "&eEnter name:", input -> {
    processInput(input);
    // Handler automatically removed after this executes
});

chatActionService.requireInput(player, "&eEnter name:", input -> {
    if (input.isEmpty()) {
        throw new IllegalArgumentException("&cName required!");
        // Handler automatically removed after exception
    }
    processInput(input);
});
```

### No Built-in Timeout

The chat input system does not have a built-in timeout mechanism. Input handlers remain active until:
1. Player enters chat and handler executes
2. Handler throws an exception
3. Plugin reloads/restarts
4. Player logs out (handler remains but becomes unreachable)

If you need timeout functionality, implement it yourself:

```java
@GuiController
public class TimedInputController {

    private final ChatActionService chatActionService;
    private final Plugin plugin;

    @GuiAction("shop:timed-search")
    public void timedSearch(Player player) {
        UUID playerId = player.getUniqueId();

        // Schedule timeout task
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> {
                // Remove handler manually if needed
                // (Not possible with current API - handler will remain)
                player.sendMessage("&cSearch timed out!");

                // Reopen GUI
                TubingGui mainGui = buildMainShop(player);
                guiActionService.showGui(player, mainGui);
            },
            20L * 60 // 60 seconds
        );

        chatActionService.requireInput(player, "&eEnter search (60s timeout):", input -> {
            // Cancel timeout
            timeoutTask.cancel();

            // Process input
            TubingGui resultsGui = buildSearchResults(player, input);
            guiActionService.showGui(player, resultsGui);
        });
    }
}
```

**Note:** The ChatActionService does not provide a way to remove handlers manually. Once registered, handlers remain until they execute or throw an exception.

### Manual Cancellation

Players can "cancel" by typing a specific keyword:

```java
@GuiAction("shop:search")
public void searchShop(Player player) {
    chatActionService.requireInput(
        player,
        "&eEnter search term (or 'cancel' to cancel):",
        input -> {
            // Check for cancellation
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("&eSearch cancelled");
                TubingGui mainGui = buildMainShop(player);
                guiActionService.showGui(player, mainGui);
                return;
            }

            // Process normal input
            TubingGui resultsGui = buildSearchResults(player, input);
            guiActionService.showGui(player, resultsGui);
        }
    );
}
```

### Logout Handling

Handlers persist after logout but become unreachable:

```java
// Player clicks item, handler registered
chatActionService.requireInput(player, "Enter name:", input -> {
    // This lambda is registered
});

// Player logs out - handler remains in map but UUID is invalid
// Player logs back in - old handler still in map
// If player types, old handler executes! (if UUID matches)

// This can cause issues with stale handlers
```

**Mitigation Strategy:**

Clear handlers on logout using an event listener:

```java
@IocBukkitListener
public class ChatInputCleanupListener implements Listener {

    private final ChatActionService chatActionService;

    public ChatInputCleanupListener(ChatActionService chatActionService) {
        this.chatActionService = chatActionService;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // ChatActionService does not expose removal method
        // Handlers will remain until player types something
        // Consider requesting this feature from Tubing
    }
}
```

**Current Limitation:** ChatActionService does not provide a public method to remove handlers. This means handlers persist until they execute.

## Best Practices

### 1. Always Validate Input

Never trust user input - validate everything:

```java
// Good - validates all aspects
chatActionService.requireInput(player, "&eEnter amount:", input -> {
    if (input == null || input.trim().isEmpty()) {
        throw new IllegalArgumentException("&cAmount cannot be empty!");
    }

    int amount;
    try {
        amount = Integer.parseInt(input.trim());
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("&cPlease enter a valid number!");
    }

    if (amount <= 0 || amount > 64) {
        throw new IllegalArgumentException("&cAmount must be between 1 and 64!");
    }

    processAmount(player, amount);
});

// Bad - no validation
chatActionService.requireInput(player, "&eEnter amount:", input -> {
    int amount = Integer.parseInt(input); // Can throw exception!
    processAmount(player, amount);
});
```

### 2. Provide Clear Prompts

Make it obvious what input is expected:

```java
// Good - clear and specific
chatActionService.requireInput(
    player,
    "&eEnter player name (3-16 characters, letters/numbers only):",
    input -> { /* ... */ }
);

// Bad - vague
chatActionService.requireInput(
    player,
    "&eEnter name:",
    input -> { /* ... */ }
);
```

### 3. Always Return to a GUI

Don't leave players stranded after input:

```java
// Good - returns to GUI
chatActionService.requireInput(player, "&eEnter search:", input -> {
    TubingGui resultsGui = buildSearchResults(player, input);
    guiActionService.showGui(player, resultsGui);
});

// Bad - leaves player in chat
chatActionService.requireInput(player, "&eEnter search:", input -> {
    player.sendMessage("Search: " + input);
    // No GUI shown!
});
```

### 4. Handle Cancellation

Always provide a way to cancel:

```java
chatActionService.requireInput(
    player,
    "&eEnter price (or 'cancel' to cancel):",
    input -> {
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("&eCancelled");
            TubingGui mainGui = buildMainGui(player);
            guiActionService.showGui(player, mainGui);
            return;
        }

        // Process input
    }
);
```

### 5. Use Descriptive Error Messages

Help players understand what went wrong:

```java
// Good - explains the problem and solution
if (!input.matches("[a-zA-Z0-9_]{3,16}")) {
    throw new IllegalArgumentException(
        "&cInvalid format! Use 3-16 letters, numbers, or underscores."
    );
}

// Bad - doesn't explain how to fix
if (!input.matches("[a-zA-Z0-9_]{3,16}")) {
    throw new IllegalArgumentException("&cInvalid format!");
}
```

### 6. Keep Input Handlers Simple

Avoid complex logic in input handlers - delegate to services:

```java
// Good - simple handler delegates to service
chatActionService.requireInput(player, "&eEnter search:", input -> {
    List<Item> results = searchService.search(input);
    TubingGui resultsGui = guiBuilder.buildSearchResults(player, results);
    guiActionService.showGui(player, resultsGui);
});

// Bad - complex logic in handler
chatActionService.requireInput(player, "&eEnter search:", input -> {
    // 50 lines of search logic, filtering, sorting, etc.
    // Difficult to test and maintain
});
```

### 7. Capture Context Variables

Don't rely on instance variables - capture what you need:

```java
// Good - captures context
@GuiAction("shop:set-price")
public void setPrice(Player player,
                    @GuiParam("item") String itemId,
                    @GuiParam("category") String category) {
    chatActionService.requireInput(player, "&eEnter price:", input -> {
        double price = Double.parseDouble(input);
        shopService.setPrice(itemId, price); // itemId captured
        TubingGui gui = buildCategoryGui(player, category); // category captured
        guiActionService.showGui(player, gui);
    });
}

// Bad - relies on instance variables
private String currentItemId; // Don't do this!

@GuiAction("shop:set-price")
public void setPrice(Player player, @GuiParam("item") String itemId) {
    this.currentItemId = itemId; // Multiple players cause issues!
    chatActionService.requireInput(player, "&eEnter price:", input -> {
        shopService.setPrice(this.currentItemId, Double.parseDouble(input));
    });
}
```

### 8. Test Error Paths

Ensure your validation works correctly:

```java
@Test
public void testPriceValidation() {
    // Test valid input
    controller.setPrice(player, "diamond");
    simulateInput(player, "100.50");
    // Assert success

    // Test invalid inputs
    controller.setPrice(player, "diamond");
    simulateInput(player, ""); // Empty
    // Assert error message sent

    controller.setPrice(player, "diamond");
    simulateInput(player, "abc"); // Non-numeric
    // Assert error message sent

    controller.setPrice(player, "diamond");
    simulateInput(player, "-50"); // Negative
    // Assert error message sent
}
```

## Common Patterns

### Search Pattern

```java
@GuiAction("items:search")
public void searchItems(Player player,
                       @GuiParam("category") String category) {
    chatActionService.requireInput(
        player,
        "&eEnter search term (or 'cancel' to cancel):",
        input -> {
            if (input.equalsIgnoreCase("cancel")) {
                TubingGui categoryGui = buildCategoryGui(player, category);
                guiActionService.showGui(player, categoryGui);
                return;
            }

            List<Item> results = itemService.search(category, input);

            if (results.isEmpty()) {
                player.sendMessage("&cNo results found for: " + input);
                TubingGui categoryGui = buildCategoryGui(player, category);
                guiActionService.showGui(player, categoryGui);
                return;
            }

            TubingGui resultsGui = buildSearchResults(player, category, input, results);
            guiActionService.showGui(player, resultsGui);
        }
    );
}
```

### Custom Value Pattern

```java
@GuiAction("shop:custom-amount")
public void customAmount(Player player,
                        @GuiParam("item") String itemId,
                        @GuiParam("price") double unitPrice) {
    chatActionService.requireInput(
        player,
        "&eHow many would you like to buy? (1-64):",
        input -> {
            int amount;
            try {
                amount = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("&cPlease enter a valid number!");
            }

            if (amount < 1 || amount > 64) {
                throw new IllegalArgumentException("&cAmount must be between 1 and 64!");
            }

            double totalPrice = unitPrice * amount;

            // Show confirmation GUI
            TubingGui confirmGui = buildPurchaseConfirmation(
                player,
                itemId,
                amount,
                totalPrice
            );
            guiActionService.showGui(player, confirmGui);
        }
    );
}
```

### Filter Pattern

```java
@GuiAction("items:filter")
public void filterItems(Player player,
                       @CurrentAction String currentAction,
                       @GuiParam("category") String category) {
    player.sendMessage("&eEnter filter criteria:");
    player.sendMessage("&7Examples: 'price:100-500', 'name:sword', 'rarity:legendary'");

    chatActionService.requireInput(player, input -> {
        if (input.equalsIgnoreCase("clear")) {
            // Clear filters
            TubingGui categoryGui = buildCategoryGui(player, category);
            guiActionService.showGui(player, categoryGui);
            return;
        }

        ItemFilter filter = parseFilter(input);
        if (filter == null) {
            throw new IllegalArgumentException(
                "&cInvalid filter format! Use 'property:value'"
            );
        }

        List<Item> filtered = itemService.filter(category, filter);
        TubingGui filteredGui = buildFilteredItems(player, category, filter, filtered);
        guiActionService.showGui(player, filteredGui);
    });
}
```

### Rename Pattern

```java
@GuiAction("item:rename")
public void renameItem(Player player,
                      @GuiParam("itemId") String itemId) {
    Item item = itemService.getItem(itemId);

    player.sendMessage("&eCurrent name: &f" + item.getName());

    chatActionService.requireInput(
        player,
        "&eEnter new name (max 32 characters, or 'cancel'):",
        input -> {
            if (input.equalsIgnoreCase("cancel")) {
                TubingGui itemGui = buildItemGui(player, itemId);
                guiActionService.showGui(player, itemGui);
                return;
            }

            String newName = input.trim();

            if (newName.isEmpty()) {
                throw new IllegalArgumentException("&cName cannot be empty!");
            }

            if (newName.length() > 32) {
                throw new IllegalArgumentException("&cName too long! Maximum 32 characters.");
            }

            itemService.rename(itemId, newName);
            player.sendMessage("&aItem renamed to: " + newName);

            TubingGui itemGui = buildItemGui(player, itemId);
            guiActionService.showGui(player, itemGui);
        }
    );
}
```

## Related Topics

- **[GUI Controllers](GUI-Controllers.md)** - Controller architecture and dependency injection
- **[GUI Actions](GUI-Actions.md)** - Action routing and parameter binding
- **[GUI Building](GUI-Building.md)** - Programmatic GUI construction
- **[GUI Templates](GUI-Templates.md)** - Template-based GUI creation
