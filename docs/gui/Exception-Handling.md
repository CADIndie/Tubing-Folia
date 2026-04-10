# Exception Handling

This guide covers exception handling in Tubing's GUI framework. Learn how to create custom exception handlers to gracefully handle errors in GUI actions, provide user-friendly error messages, and maintain a robust user experience even when things go wrong.

## Overview

The Tubing GUI framework provides a centralized exception handling system that catches errors in GUI actions and routes them to appropriate handlers. This system offers:

- **Custom Exception Handlers**: Create handlers for specific exception types
- **Automatic Registration**: Handlers are discovered and registered on plugin startup
- **Exception Type Hierarchy**: Support for handling exception inheritance chains
- **Automatic GUI Cleanup**: GUIs are closed and history cleared on exceptions
- **Multi-Provider Support**: Multiple handlers for different exception types
- **Conditional Registration**: Register handlers based on configuration properties
- **Priority Control**: Control handler loading order
- **Dependency Injection**: Full IoC support in exception handlers

When an exception occurs in a GUI action:
1. The exception is caught by `GuiActionService`
2. A matching `GuiExceptionHandler` is found (if registered)
3. The handler is invoked with the player and exception
4. The player's GUI is closed
5. Navigation history is cleared
6. If no handler is found, the exception is re-thrown

## GuiExceptionHandler Interface

The `GuiExceptionHandler` interface is the foundation of the exception handling system. It's a generic interface that extends `BiConsumer<Player, T>` where `T` is the exception type.

### Interface Definition

```java
package be.garagepoort.mcioc.tubinggui.exceptions;

import org.bukkit.entity.Player;
import java.util.function.BiConsumer;

public interface GuiExceptionHandler<T extends Throwable> extends BiConsumer<Player, T> {
    // Inherits: void accept(Player player, T exception)
}
```

### Key Points

- Generic interface with type parameter `T extends Throwable`
- Extends `BiConsumer<Player, T>` for functional interface benefits
- Single method: `accept(Player player, T exception)`
- Type parameter determines which exceptions this handler processes
- Must be annotated with `@GuiExceptionHandlerProvider` to be registered

## Creating Custom Exception Handlers

Creating a custom exception handler involves three steps:
1. Create a class implementing `GuiExceptionHandler<T>`
2. Annotate with `@IocBean` for IoC registration
3. Annotate with `@GuiExceptionHandlerProvider` specifying exception types

### Basic Exception Handler

```java
package com.example.myplugin.gui.handlers;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubinggui.exceptions.GuiExceptionHandler;
import be.garagepoort.mcioc.tubinggui.exceptions.GuiExceptionHandlerProvider;
import org.bukkit.entity.Player;
import java.sql.SQLException;

@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {

    @Override
    public void accept(Player player, SQLException exception) {
        // Send user-friendly message
        player.sendMessage("§cDatabase error! Please try again later.");

        // Log the error for admins
        plugin.getLogger().severe("Database error for player " + player.getName() + ": " + exception.getMessage());
    }
}
```

**What happens:**
- Handler is automatically discovered on plugin startup
- Registered for `SQLException` exceptions
- When a `SQLException` occurs in any GUI action, this handler is invoked
- Player receives a user-friendly message
- Error is logged for debugging
- GUI is closed automatically after handler completes

### Handler with Dependencies

Exception handlers support full dependency injection:

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {

    private final MessageService messageService;
    private final LoggingService loggingService;
    private final AdminNotificationService adminNotificationService;

    public DatabaseExceptionHandler(MessageService messageService,
                                   LoggingService loggingService,
                                   AdminNotificationService adminNotificationService) {
        this.messageService = messageService;
        this.loggingService = loggingService;
        this.adminNotificationService = adminNotificationService;
    }

    @Override
    public void accept(Player player, SQLException exception) {
        // Send formatted message
        messageService.sendError(player, "database-error");

        // Log with context
        loggingService.logError(
            "GUI database error",
            exception,
            Map.of(
                "player", player.getName(),
                "uuid", player.getUniqueId().toString()
            )
        );

        // Notify online admins
        adminNotificationService.notifyAdmins(
            "§cDatabase error in GUI for player " + player.getName()
        );
    }
}
```

### Configuration Injection

Inject configuration values for customizable error messages:

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {

    private final MessageService messageService;

    @ConfigProperty("error-messages.database")
    private String databaseErrorMessage;

    @ConfigProperty("error-messages.notify-admins")
    private boolean notifyAdmins;

    public DatabaseExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void accept(Player player, SQLException exception) {
        // Use configured message
        player.sendMessage(databaseErrorMessage);

        // Conditionally notify admins
        if (notifyAdmins) {
            notifyOnlineAdmins(exception);
        }

        messageService.logException(exception);
    }

    private void notifyOnlineAdmins(SQLException exception) {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("myplugin.admin.notifications"))
            .forEach(admin -> admin.sendMessage("§cDatabase error occurred: " + exception.getMessage()));
    }
}
```

## Exception Types and Handling Strategies

Different exception types require different handling strategies. The framework supports exact type matching and exception hierarchy matching.

### Single Exception Type

Handle one specific exception type:

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class SqlExceptionHandler implements GuiExceptionHandler<SQLException> {

    @Override
    public void accept(Player player, SQLException exception) {
        player.sendMessage("§cDatabase connection failed! Please try again.");
        logDatabaseError(exception);
    }
}
```

### Multiple Exception Types

One handler can process multiple exception types:

```java
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {IOException.class, TimeoutException.class, SQLException.class}
)
public class DataLoadExceptionHandler implements GuiExceptionHandler<Exception> {

    @Override
    public void accept(Player player, Exception exception) {
        if (exception instanceof IOException) {
            player.sendMessage("§cFile error! Please contact an admin.");
            logFileError((IOException) exception);

        } else if (exception instanceof TimeoutException) {
            player.sendMessage("§cOperation timed out. Please try again.");
            logTimeoutError((TimeoutException) exception);

        } else if (exception instanceof SQLException) {
            player.sendMessage("§cDatabase error! Please try again later.");
            logDatabaseError((SQLException) exception);
        }
    }

    private void logFileError(IOException e) {
        plugin.getLogger().log(Level.SEVERE, "File I/O error in GUI", e);
    }

    private void logTimeoutError(TimeoutException e) {
        plugin.getLogger().log(Level.WARNING, "GUI operation timed out", e);
    }

    private void logDatabaseError(SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "Database error in GUI", e);
    }
}
```

### Exception Hierarchy

Handlers automatically catch subclass exceptions:

```java
// Base exception handler - catches all IOException subclasses
@IocBean
@GuiExceptionHandlerProvider(exceptions = {IOException.class})
public class IOExceptionHandler implements GuiExceptionHandler<IOException> {

    @Override
    public void accept(Player player, IOException exception) {
        // Handles IOException, FileNotFoundException, SocketException, etc.

        if (exception instanceof FileNotFoundException) {
            player.sendMessage("§cFile not found! Please contact an admin.");
        } else if (exception instanceof SocketException) {
            player.sendMessage("§cNetwork error! Please try again.");
        } else {
            player.sendMessage("§cI/O error occurred!");
        }

        logIOError(exception);
    }
}
```

**Exception Resolution Order:**
1. **Exact match**: Handler registered for exact exception type
2. **Parent match**: Handler registered for parent exception type (via `isAssignableFrom`)
3. **No match**: Exception is re-thrown, causing server error

**Example Flow:**
```java
// Handlers registered:
@GuiExceptionHandlerProvider(exceptions = {FileNotFoundException.class})
class FileNotFoundHandler { }

@GuiExceptionHandlerProvider(exceptions = {IOException.class})
class IOHandler { }

@GuiExceptionHandlerProvider(exceptions = {Exception.class})
class GeneralHandler { }

// Exception thrown: FileNotFoundException
// Resolution: FileNotFoundHandler (exact match)

// Exception thrown: SocketException
// Resolution: IOHandler (parent match - SocketException extends IOException)

// Exception thrown: RuntimeException
// Resolution: GeneralHandler (parent match - RuntimeException extends Exception)

// Exception thrown: OutOfMemoryError
// Resolution: None - Error re-thrown (no handler for Error types)
```

### Custom Exception Types

Create custom exception types for domain-specific errors:

```java
// Custom exception
public class ShopTransactionException extends Exception {
    private final TransactionFailureReason reason;

    public ShopTransactionException(String message, TransactionFailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public TransactionFailureReason getReason() {
        return reason;
    }
}

public enum TransactionFailureReason {
    INSUFFICIENT_FUNDS,
    ITEM_NOT_AVAILABLE,
    INVENTORY_FULL,
    PERMISSION_DENIED
}

// Custom exception handler
@IocBean
@GuiExceptionHandlerProvider(exceptions = {ShopTransactionException.class})
public class ShopTransactionExceptionHandler implements GuiExceptionHandler<ShopTransactionException> {

    private final MessageService messageService;

    public ShopTransactionExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void accept(Player player, ShopTransactionException exception) {
        switch (exception.getReason()) {
            case INSUFFICIENT_FUNDS:
                messageService.send(player, "shop.error.insufficient-funds");
                break;
            case ITEM_NOT_AVAILABLE:
                messageService.send(player, "shop.error.item-unavailable");
                break;
            case INVENTORY_FULL:
                messageService.send(player, "shop.error.inventory-full");
                break;
            case PERMISSION_DENIED:
                messageService.send(player, "shop.error.no-permission");
                break;
        }
    }
}

// Usage in GUI controller
@GuiAction("shop:purchase")
public TubingGui purchaseItem(Player player, @GuiParam("item") String itemId) throws ShopTransactionException {
    if (!economyService.hasBalance(player, price)) {
        throw new ShopTransactionException(
            "Insufficient funds",
            TransactionFailureReason.INSUFFICIENT_FUNDS
        );
    }

    if (!player.hasPermission("shop.purchase")) {
        throw new ShopTransactionException(
            "No permission",
            TransactionFailureReason.PERMISSION_DENIED
        );
    }

    // Process purchase
    return buildSuccessGui();
}
```

## Default Exception Behavior

Understanding what happens when exceptions occur helps you design effective error handling strategies.

### Uncaught Exceptions

If no exception handler is registered for an exception type:

```java
@GuiAction("data:load")
public TubingGui loadData(Player player) {
    // Throws NullPointerException
    String value = null;
    int length = value.length(); // Exception!

    // No handler registered for NullPointerException
    return buildGui();
}
```

**What happens:**
1. Exception is caught by `GuiActionService.executeAction()`
2. `handleException()` searches for matching handler
3. No handler found (exact or parent match)
4. Exception is re-thrown as `IocException`
5. Stack trace appears in server console
6. Player's GUI remains open (no automatic cleanup)
7. Navigation history remains intact

**Result**: Server error is logged, player sees broken GUI state.

### Caught Exceptions with Handler

When a handler is registered:

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {NullPointerException.class})
public class NullPointerExceptionHandler implements GuiExceptionHandler<NullPointerException> {

    @Override
    public void accept(Player player, NullPointerException exception) {
        player.sendMessage("§cAn unexpected error occurred!");
        plugin.getLogger().log(Level.SEVERE, "NPE in GUI", exception);
    }
}

@GuiAction("data:load")
public TubingGui loadData(Player player) {
    String value = null;
    int length = value.length(); // Exception!
    return buildGui();
}
```

**What happens:**
1. Exception is caught by `GuiActionService.executeAction()`
2. `handleException()` finds `NullPointerExceptionHandler`
3. Handler's `accept()` method is called
4. Player receives error message
5. Exception is logged
6. **Player's inventory is closed automatically**
7. **GUI reference is removed from tracking**
8. **Navigation history is cleared**

**Result**: Graceful error handling with automatic cleanup.

### Async Exception Handling

Exceptions in async GUI operations are caught and routed to handlers:

```java
@GuiAction("data:load")
public AsyncGui<TubingGui> loadDataAsync(Player player) {
    return AsyncGui.async(() -> {
        // Runs on async thread
        throw new SQLException("Database connection failed");
    });
}
```

**What happens:**
1. Async lambda executes on background thread
2. Exception is caught in `processAsyncGuiAction()`
3. `handleException()` is called (may need to sync to main thread)
4. Matching handler is invoked
5. GUI is closed and history cleared

**Important**: Exception handlers should be thread-safe if they access shared state.

### Automatic Cleanup on Exception

When an exception is handled, automatic cleanup occurs:

```java
// In GuiActionService.handleException()
private void handleException(Player player, Throwable e) throws Throwable {
    if (exceptionHandlers.containsKey(e.getClass())) {
        exceptionHandlers.get(e.getClass()).accept(player, e);
        player.closeInventory();              // Close GUI
        removeInventory(player);              // Remove tracking
        guiHistoryStack.clear(player.getUniqueId());  // Clear history
        return;
    }

    // Check parent exception types
    Optional<Class<? extends Exception>> parentException = exceptionHandlers.keySet().stream()
        .filter(c -> c.isAssignableFrom(e.getClass()))
        .findFirst();

    if (parentException.isPresent()) {
        exceptionHandlers.get(parentException.get()).accept(player, e);
        player.closeInventory();              // Close GUI
        removeInventory(player);              // Remove tracking
        guiHistoryStack.clear(player.getUniqueId());  // Clear history
        return;
    }

    throw e; // Re-throw if no handler found
}
```

**Cleanup includes:**
- Closing player's open inventory
- Removing GUI from internal tracking map
- Clearing navigation history stack
- Player returns to normal gameplay state

## @GuiExceptionHandlerProvider Annotation

The `@GuiExceptionHandlerProvider` annotation registers exception handlers and configures their behavior.

### Annotation Definition

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GuiExceptionHandlerProvider {

    String conditionalOnProperty() default "";

    boolean priority() default false;

    Class multiproviderClass() default Object.class;

    Class[] exceptions();  // Required
}
```

### Annotation Attributes

#### exceptions (Required)

Specifies which exception types this handler processes:

```java
// Single exception
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})

// Multiple exceptions
@GuiExceptionHandlerProvider(exceptions = {
    SQLException.class,
    IOException.class,
    TimeoutException.class
})

// Exception hierarchy
@GuiExceptionHandlerProvider(exceptions = {Exception.class})  // Catches all Exception subclasses
```

#### conditionalOnProperty

Register handler only if a configuration property is set:

```java
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {SQLException.class},
    conditionalOnProperty = "features.advanced-error-handling"
)
public class AdvancedDatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {
    // Only registered if "features.advanced-error-handling" is true in config

    @Override
    public void accept(Player player, SQLException exception) {
        // Advanced error handling with detailed diagnostics
        performDetailedDiagnostics(exception);
        sendDetailedErrorReport(player, exception);
    }
}
```

**Use cases:**
- Feature flags for different error handling strategies
- Debug vs. production error messages
- Optional integrations (e.g., Sentry error reporting)

#### priority

Load handler with priority during plugin startup:

```java
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {Exception.class},
    priority = true
)
public class CoreExceptionHandler implements GuiExceptionHandler<Exception> {
    // Loaded early with priority

    @Override
    public void accept(Player player, Exception exception) {
        // Core exception handling logic
    }
}
```

**Use cases:**
- Core exception handlers that other systems depend on
- Handlers that need to initialize resources early
- Base handlers in exception hierarchy

#### multiproviderClass

Advanced feature for multi-implementation support:

```java
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {CustomException.class},
    multiproviderClass = CustomExceptionHandler.class
)
public class CustomExceptionHandler implements GuiExceptionHandler<CustomException> {
    // Advanced multi-provider configuration
}
```

**Typically left at default value (`Object.class`)**.

### Registration Process

Exception handlers are registered during plugin startup:

1. **Discovery**: `TubingGuiBeanRegistrator` discovers classes annotated with `@GuiExceptionHandlerProvider`
2. **Bean Registration**: Classes are registered as IoC beans (must also have `@IocBean`)
3. **Handler Registration**: `GuiActionService` constructor registers handlers:
   ```java
   public GuiActionService(...,
                          @IocMulti(GuiExceptionHandler.class) List<GuiExceptionHandler> providedExceptionHandlers) {
       // ...
       registerExceptionHandlers(providedExceptionHandlers);
   }
   ```
4. **Annotation Processing**: For each handler:
   - Extract `@GuiExceptionHandlerProvider` annotation
   - Read `exceptions()` array
   - Register handler for each exception class
5. **Map Storage**: Handlers stored in `Map<Class<? extends Exception>, GuiExceptionHandler>`

## Best Practices for Error Handling

### 1. Always Send User-Friendly Messages

```java
// Good - Clear, actionable message
@Override
public void accept(Player player, SQLException exception) {
    player.sendMessage("§cDatabase error! Please try again in a few moments.");
    player.sendMessage("§cIf the problem persists, contact an administrator.");
    logError(exception);
}

// Bad - Technical message
@Override
public void accept(Player player, SQLException exception) {
    player.sendMessage("SQLException: " + exception.getMessage());
}
```

### 2. Log Exceptions with Context

```java
@Override
public void accept(Player player, SQLException exception) {
    // Good - Contextual logging
    plugin.getLogger().log(
        Level.SEVERE,
        String.format(
            "Database error in GUI for player %s (UUID: %s)",
            player.getName(),
            player.getUniqueId()
        ),
        exception
    );

    // Bad - Generic logging
    plugin.getLogger().log(Level.SEVERE, "Error", exception);
}
```

### 3. Use Specific Exception Types

```java
// Good - Specific handlers for specific errors
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {IOException.class})
public class IOExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {TimeoutException.class})
public class TimeoutExceptionHandler { }

// Bad - Catch-all handler
@GuiExceptionHandlerProvider(exceptions = {Exception.class})
public class GenericExceptionHandler {
    // Can't provide specific error messages
}
```

### 4. Notify Admins of Critical Errors

```java
@Override
public void accept(Player player, SQLException exception) {
    // Send user-friendly message
    player.sendMessage("§cDatabase error! Please try again later.");

    // Log error
    plugin.getLogger().log(Level.SEVERE, "Critical database error", exception);

    // Notify online admins
    Bukkit.getOnlinePlayers().stream()
        .filter(p -> p.hasPermission("myplugin.admin"))
        .forEach(admin -> {
            admin.sendMessage("§c[Admin] Database error occurred!");
            admin.sendMessage("§c[Admin] Player: " + player.getName());
            admin.sendMessage("§c[Admin] Error: " + exception.getMessage());
        });
}
```

### 5. Consider Retry Logic

```java
@Override
public void accept(Player player, SQLException exception) {
    // Check if error is transient
    if (isTransientError(exception)) {
        player.sendMessage("§eConnection temporarily unavailable.");
        player.sendMessage("§eRetrying in 3 seconds...");

        // Schedule retry
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                guiActionService.executeAction(player, lastAction);
            } catch (Exception e) {
                player.sendMessage("§cRetry failed. Please try again later.");
            }
        }, 60L); // 3 seconds

    } else {
        player.sendMessage("§cDatabase error! Please contact an admin.");
        logPermanentError(exception);
    }
}

private boolean isTransientError(SQLException e) {
    // Check SQL state codes for transient errors
    String sqlState = e.getSQLState();
    return "08S01".equals(sqlState) || // Communication link failure
           "40001".equals(sqlState);    // Serialization failure
}
```

### 6. Handle Async Exceptions Carefully

```java
@GuiAction("data:load")
public AsyncGui<TubingGui> loadDataAsync(Player player) {
    UUID playerId = player.getUniqueId();

    return AsyncGui.async(() -> {
        try {
            // Async operation
            Data data = database.load(playerId);
            return buildGui(data);

        } catch (SQLException e) {
            // Exception caught and routed to handler automatically
            throw e;

        } catch (Exception e) {
            // Log unexpected exceptions
            plugin.getLogger().log(Level.SEVERE, "Unexpected error in async GUI", e);
            throw e;
        }
    });
}
```

### 7. Provide Fallback Options

```java
@Override
public void accept(Player player, SQLException exception) {
    player.sendMessage("§cCouldn't load data from database.");
    player.sendMessage("§eShowing cached data instead...");

    // Show cached GUI
    Bukkit.getScheduler().runTask(plugin, () -> {
        try {
            TubingGui cachedGui = guiCache.getCachedGui(player);
            guiActionService.showGui(player, cachedGui);
        } catch (Exception e) {
            player.sendMessage("§cNo cached data available.");
        }
    });
}
```

### 8. Test Exception Handlers

```java
@Test
public void testDatabaseExceptionHandler() {
    // Arrange
    Player mockPlayer = mock(Player.class);
    SQLException exception = new SQLException("Connection failed");
    DatabaseExceptionHandler handler = new DatabaseExceptionHandler(messageService);

    // Act
    handler.accept(mockPlayer, exception);

    // Assert
    verify(mockPlayer).sendMessage(contains("Database error"));
    verify(messageService).logException(exception);
}
```

### 9. Document Exception Scenarios

```java
/**
 * Handles database exceptions that occur during GUI operations.
 *
 * This handler processes:
 * - Connection failures (SQLState: 08xxx)
 * - Query timeouts (SQLState: HYT00)
 * - Deadlock exceptions (SQLState: 40001)
 *
 * Behavior:
 * - Sends user-friendly error message
 * - Logs full exception with context
 * - Notifies admins for critical errors
 * - Attempts retry for transient errors
 *
 * @see DatabaseService
 * @see GuiActionService
 */
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {
    // Implementation
}
```

### 10. Handle All Expected Exceptions

```java
// Register handlers for all expected exceptions in your GUI operations

@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {IOException.class})
public class FileIOExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {TimeoutException.class})
public class TimeoutExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {IllegalArgumentException.class})
public class ValidationExceptionHandler { }

@GuiExceptionHandlerProvider(exceptions = {PermissionException.class})
public class PermissionExceptionHandler { }

// Fallback for unexpected exceptions
@GuiExceptionHandlerProvider(exceptions = {RuntimeException.class})
public class RuntimeExceptionHandler { }
```

## Common Exception Scenarios

### Database Exceptions

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {SQLException.class})
public class DatabaseExceptionHandler implements GuiExceptionHandler<SQLException> {

    private final MessageService messageService;

    public DatabaseExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void accept(Player player, SQLException exception) {
        String sqlState = exception.getSQLState();

        if (sqlState != null && sqlState.startsWith("08")) {
            // Connection errors
            messageService.send(player, "error.database.connection");

        } else if (sqlState != null && sqlState.equals("40001")) {
            // Deadlock
            messageService.send(player, "error.database.deadlock");
            player.sendMessage("§ePlease try again.");

        } else {
            // Generic database error
            messageService.send(player, "error.database.generic");
        }

        // Log with SQL state
        plugin.getLogger().severe(String.format(
            "Database error (SQLState: %s) for player %s: %s",
            sqlState,
            player.getName(),
            exception.getMessage()
        ));
    }
}
```

### Network Timeout Exceptions

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {TimeoutException.class})
public class TimeoutExceptionHandler implements GuiExceptionHandler<TimeoutException> {

    @Override
    public void accept(Player player, TimeoutException exception) {
        player.sendMessage("§cOperation timed out!");
        player.sendMessage("§eThe server may be under heavy load. Please try again.");

        plugin.getLogger().warning(String.format(
            "Timeout in GUI for player %s: %s",
            player.getName(),
            exception.getMessage()
        ));
    }
}
```

### Permission Denied Exceptions

```java
public class PermissionDeniedException extends Exception {
    private final String requiredPermission;

    public PermissionDeniedException(String requiredPermission) {
        super("Permission denied: " + requiredPermission);
        this.requiredPermission = requiredPermission;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}

@IocBean
@GuiExceptionHandlerProvider(exceptions = {PermissionDeniedException.class})
public class PermissionExceptionHandler implements GuiExceptionHandler<PermissionDeniedException> {

    private final MessageService messageService;

    public PermissionExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void accept(Player player, PermissionDeniedException exception) {
        messageService.send(player, "error.permission.denied");

        // Show required permission to admins
        if (player.hasPermission("myplugin.admin")) {
            player.sendMessage("§7Required: " + exception.getRequiredPermission());
        }
    }
}
```

### Validation Exceptions

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {IllegalArgumentException.class})
public class ValidationExceptionHandler implements GuiExceptionHandler<IllegalArgumentException> {

    @Override
    public void accept(Player player, IllegalArgumentException exception) {
        player.sendMessage("§cInvalid input: " + exception.getMessage());

        // Validation errors are usually user error, not server error
        plugin.getLogger().info(String.format(
            "Validation error for player %s: %s",
            player.getName(),
            exception.getMessage()
        ));
    }
}
```

### Number Format Exceptions

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {NumberFormatException.class})
public class NumberFormatExceptionHandler implements GuiExceptionHandler<NumberFormatException> {

    @Override
    public void accept(Player player, NumberFormatException exception) {
        player.sendMessage("§cInvalid number format!");
        player.sendMessage("§ePlease enter a valid number.");

        // Usually indicates malformed GUI action parameters
        plugin.getLogger().warning(String.format(
            "Number format error for player %s: %s",
            player.getName(),
            exception.getMessage()
        ));
    }
}
```

## Advanced Exception Handling

### Exception Handler Priority

Control which handler is invoked when multiple handlers match:

```java
// General handler - low priority
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {IOException.class},
    priority = false
)
public class GeneralIOExceptionHandler implements GuiExceptionHandler<IOException> {
    @Override
    public void accept(Player player, IOException exception) {
        player.sendMessage("§cFile error!");
    }
}

// Specific handler - high priority (loaded first)
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {FileNotFoundException.class},
    priority = true
)
public class FileNotFoundExceptionHandler implements GuiExceptionHandler<FileNotFoundException> {
    @Override
    public void accept(Player player, FileNotFoundException exception) {
        player.sendMessage("§cFile not found!");
    }
}
```

**Note**: The framework uses exact type matching first, then parent type matching, so priority mainly affects loading order rather than selection order.

### Conditional Exception Handlers

Enable different handlers based on configuration:

```java
// Development error handler with detailed information
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {Exception.class},
    conditionalOnProperty = "debug-mode"
)
public class DebugExceptionHandler implements GuiExceptionHandler<Exception> {

    @Override
    public void accept(Player player, Exception exception) {
        // Detailed error information
        player.sendMessage("§c§lDEBUG MODE - Exception Details:");
        player.sendMessage("§7Type: " + exception.getClass().getSimpleName());
        player.sendMessage("§7Message: " + exception.getMessage());
        player.sendMessage("§7Stack trace logged to console");

        // Full stack trace
        exception.printStackTrace();
    }
}

// Production error handler with minimal information
@IocBean
@GuiExceptionHandlerProvider(
    exceptions = {Exception.class},
    conditionalOnProperty = "production-mode"
)
public class ProductionExceptionHandler implements GuiExceptionHandler<Exception> {

    @Override
    public void accept(Player player, Exception exception) {
        // Minimal user-facing message
        player.sendMessage("§cAn error occurred. Please try again.");

        // Log error for admin review
        plugin.getLogger().log(Level.SEVERE, "GUI error", exception);
    }
}
```

### Integration with External Error Tracking

```java
@IocBean
@GuiExceptionHandlerProvider(exceptions = {Exception.class})
public class SentryExceptionHandler implements GuiExceptionHandler<Exception> {

    private final SentryService sentryService;

    public SentryExceptionHandler(SentryService sentryService) {
        this.sentryService = sentryService;
    }

    @Override
    public void accept(Player player, Exception exception) {
        // Send to Sentry for tracking
        sentryService.captureException(
            exception,
            Map.of(
                "player.name", player.getName(),
                "player.uuid", player.getUniqueId().toString(),
                "context", "GUI"
            )
        );

        // User-friendly message
        player.sendMessage("§cAn error occurred. Our team has been notified.");
    }
}
```

## Related Topics

- **[GUI Async](GUI-Async.md)** - Async exception handling in GUI operations
- **[GUI Actions](GUI-Actions.md)** - Action routing and parameter binding
- **[GUI Controllers](GUI-Controllers.md)** - Controller architecture and lifecycle
- **[GUI Setup](GUI-Setup.md)** - Initial GUI framework configuration
