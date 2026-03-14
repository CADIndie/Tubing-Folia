# Multi-Implementation

When building extensible systems, you often need to work with multiple implementations of a single interface. Tubing provides powerful multi-implementation support through `@IocMultiProvider` and `@IocMulti` annotations, enabling plugin APIs, strategy patterns, and dynamic extension systems.

## Overview

Multi-implementation allows you to:

- Register multiple beans for a single interface
- Inject all implementations as a `List<T>`
- Build plugin-style APIs where extensions can add handlers
- Implement strategy patterns with multiple strategies
- Create event-driven architectures with multiple listeners

**Single Implementation (Normal):**
```java
public interface PaymentProcessor { }

@IocBean
public class StripeProcessor implements PaymentProcessor { }

@IocBean
public class PaymentService {
    private final PaymentProcessor processor; // Single implementation injected

    public PaymentService(PaymentProcessor processor) {
        this.processor = processor;
    }
}
```

**Multiple Implementations:**
```java
public interface PaymentProcessor { }

@IocBean
@IocMultiProvider(PaymentProcessor.class)
public class StripeProcessor implements PaymentProcessor { }

@IocBean
@IocMultiProvider(PaymentProcessor.class)
public class PayPalProcessor implements PaymentProcessor { }

@IocBean
public class PaymentService {
    private final List<PaymentProcessor> processors; // All implementations injected

    public PaymentService(@IocMulti(PaymentProcessor.class) List<PaymentProcessor> processors) {
        this.processors = processors;
    }
}
```

## @IocMultiProvider Annotation

The `@IocMultiProvider` annotation marks a bean class or provider method to contribute to a multi-implementation list.

### Class-Level Usage

```java
@IocMultiProvider(Interface.class)
```

Mark a bean implementation to be included in a list of implementations for the specified interface.

**Basic Example:**
```java
public interface NotificationHandler {
    void sendNotification(Player player, String message);
}

@IocBean
@IocMultiProvider(NotificationHandler.class)
public class ChatNotificationHandler implements NotificationHandler {
    @Override
    public void sendNotification(Player player, String message) {
        player.sendMessage(message);
    }
}

@IocBean
@IocMultiProvider(NotificationHandler.class)
public class TitleNotificationHandler implements NotificationHandler {
    @Override
    public void sendNotification(Player player, String message) {
        player.sendTitle(message, "", 10, 70, 20);
    }
}

@IocBean
@IocMultiProvider(NotificationHandler.class)
public class ActionBarNotificationHandler implements NotificationHandler {
    @Override
    public void sendNotification(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(message));
    }
}
```

### How It Works

When you annotate a bean with `@IocMultiProvider`:

1. The bean is instantiated normally
2. Instead of being registered under its own class, it's added to a list
3. The list is stored in the container under the interface class
4. All beans with the same multi-provider interface are collected together

**Container behavior:**
```java
// IocContainer.java (simplified)
if (aClass.isAnnotationPresent(IocMultiProvider.class)) {
    Class[] multiClasses = aClass.getAnnotation(IocMultiProvider.class).value();
    Object bean = createBean(scanResult, aClass, validBeans, providedBeans, multiProviders);
    for (Class multiClass : multiClasses) {
        beans.putIfAbsent(multiClass, new ArrayList<>());
        List list = (List) beans.get(multiClass);
        if (!list.contains(bean) && bean != null) {
            list.add(bean);
        }
    }
    return bean;
}
```

### Multiple Interfaces

A bean can contribute to multiple multi-provider lists:

```java
public interface EventHandler {
    void handle(Event event);
}

public interface Validator {
    boolean validate(Object obj);
}

@IocBean
@IocMultiProvider({EventHandler.class, Validator.class})
public class PlayerJoinHandler implements EventHandler, Validator {

    @Override
    public void handle(Event event) {
        // Handle player join event
    }

    @Override
    public boolean validate(Object obj) {
        // Validate player data
        return true;
    }
}
```

This bean will be added to both the `EventHandler` list and the `Validator` list.

### Method-Level Usage (Provider Methods)

You can also use `@IocMultiProvider` on provider methods in `@TubingConfiguration` classes to contribute collections of beans:

```java
@TubingConfiguration
public class HandlerConfiguration {

    @IocMultiProvider(CommandHandler.class)
    public static Collection<CommandHandler> provideHandlers(
            DatabaseService database,
            PermissionService permissions) {

        List<CommandHandler> handlers = new ArrayList<>();

        // Dynamically create handlers based on configuration
        handlers.add(new AdminCommandHandler(database, permissions));
        handlers.add(new PlayerCommandHandler(database));
        handlers.add(new ModeratorCommandHandler(permissions));

        return handlers;
    }
}
```

**Key points:**
- Method must be `static`
- Must return `Collection` or `List`
- Each element in the returned collection is added to the multi-provider list
- Method parameters are resolved like constructor parameters

## @IocMulti Annotation

The `@IocMulti` annotation is used on constructor parameters to inject a list of all implementations.

### Basic Usage

```java
@IocMulti(Interface.class)
```

Inject all implementations of the specified interface as a `List`.

**Example:**
```java
@IocBean
public class NotificationService {
    private final List<NotificationHandler> handlers;

    public NotificationService(@IocMulti(NotificationHandler.class)
                               List<NotificationHandler> handlers) {
        this.handlers = handlers;
    }

    public void notifyPlayer(Player player, String message) {
        // Send notification through all handlers
        for (NotificationHandler handler : handlers) {
            handler.sendNotification(player, message);
        }
    }
}
```

### Why Use @IocMulti?

Without `@IocMulti`, the container would try to inject a single implementation:

```java
// This would fail if multiple implementations exist
public NotificationService(NotificationHandler handler) {
    // IocException: Multiple beans found with interface NotificationHandler
}

// This explicitly requests all implementations
public NotificationService(@IocMulti(NotificationHandler.class)
                          List<NotificationHandler> handlers) {
    // Works correctly - receives list of all implementations
}
```

### How Resolution Works

When the container encounters `@IocMulti`:

1. It recognizes the parameter is requesting a multi-implementation list
2. It looks up the list stored under the specified interface
3. The list contains all beans marked with `@IocMultiProvider(Interface.class)`
4. The complete list is injected into the constructor

**Container behavior:**
```java
// IocContainer.java - buildParams method (simplified)
Optional<Annotation> multiAnnotation = Arrays.stream(annotations)
    .filter(a -> a.annotationType().equals(IocMulti.class))
    .findFirst();

if (multiAnnotation.isPresent()) {
    IocMulti iocMulti = (IocMulti) multiAnnotation.get();
    constructorParams.add(instantiateBean(
        scanResult, iocMulti.value(), validBeans, providedBeans,
        multiProviders, true  // multiProvider flag = true
    ));
}
```

## Use Cases

### Plugin APIs

Build extensible plugin systems where other developers can add handlers:

```java
// Your plugin's API interface
public interface QuestHandler {
    String getQuestId();
    void startQuest(Player player);
    void completeQuest(Player player);
}

// Core quest system
@IocBean
public class QuestManager {
    private final List<QuestHandler> questHandlers;
    private final Map<String, QuestHandler> handlerMap;

    public QuestManager(@IocMulti(QuestHandler.class)
                       List<QuestHandler> questHandlers) {
        this.questHandlers = questHandlers;
        this.handlerMap = new HashMap<>();

        // Build lookup map
        for (QuestHandler handler : questHandlers) {
            handlerMap.put(handler.getQuestId(), handler);
        }
    }

    public void startQuest(Player player, String questId) {
        QuestHandler handler = handlerMap.get(questId);
        if (handler != null) {
            handler.startQuest(player);
        }
    }
}

// Extension developers add quest implementations
@IocBean
@IocMultiProvider(QuestHandler.class)
public class DragonSlayerQuest implements QuestHandler {
    @Override
    public String getQuestId() {
        return "dragon_slayer";
    }

    @Override
    public void startQuest(Player player) {
        // Quest implementation
    }

    @Override
    public void completeQuest(Player player) {
        // Completion logic
    }
}

@IocBean
@IocMultiProvider(QuestHandler.class)
public class TreasureHuntQuest implements QuestHandler {
    @Override
    public String getQuestId() {
        return "treasure_hunt";
    }

    @Override
    public void startQuest(Player player) {
        // Quest implementation
    }

    @Override
    public void completeQuest(Player player) {
        // Completion logic
    }
}
```

### Strategy Pattern

Implement strategy pattern with multiple algorithms:

```java
public interface CompressionStrategy {
    byte[] compress(byte[] data);
    byte[] decompress(byte[] data);
    String getName();
}

@IocBean
@IocMultiProvider(CompressionStrategy.class)
public class GzipCompressionStrategy implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        // GZIP compression
    }

    @Override
    public byte[] decompress(byte[] data) {
        // GZIP decompression
    }

    @Override
    public String getName() {
        return "gzip";
    }
}

@IocBean
@IocMultiProvider(CompressionStrategy.class)
public class ZlibCompressionStrategy implements CompressionStrategy {
    @Override
    public byte[] compress(byte[] data) {
        // ZLIB compression
    }

    @Override
    public byte[] decompress(byte[] data) {
        // ZLIB decompression
    }

    @Override
    public String getName() {
        return "zlib";
    }
}

@IocBean
public class DataCompressor {
    private final Map<String, CompressionStrategy> strategies;

    @ConfigProperty("compression.default")
    private String defaultStrategy;

    public DataCompressor(@IocMulti(CompressionStrategy.class)
                         List<CompressionStrategy> strategies) {
        this.strategies = new HashMap<>();
        for (CompressionStrategy strategy : strategies) {
            this.strategies.put(strategy.getName(), strategy);
        }
    }

    public byte[] compress(byte[] data) {
        CompressionStrategy strategy = strategies.get(defaultStrategy);
        return strategy != null ? strategy.compress(data) : data;
    }

    public byte[] compress(byte[] data, String strategyName) {
        CompressionStrategy strategy = strategies.get(strategyName);
        return strategy != null ? strategy.compress(data) : data;
    }
}
```

### Chain of Responsibility

Process events through a chain of handlers:

```java
public interface EventProcessor {
    boolean canProcess(Event event);
    void process(Event event);
    int getPriority(); // Lower = higher priority
}

@IocBean
@IocMultiProvider(EventProcessor.class)
public class SecurityProcessor implements EventProcessor {
    @Override
    public boolean canProcess(Event event) {
        return event instanceof SecurityEvent;
    }

    @Override
    public void process(Event event) {
        // Security checks
    }

    @Override
    public int getPriority() {
        return 100; // High priority
    }
}

@IocBean
@IocMultiProvider(EventProcessor.class)
public class LoggingProcessor implements EventProcessor {
    @Override
    public boolean canProcess(Event event) {
        return true; // Process all events
    }

    @Override
    public void process(Event event) {
        // Log event
    }

    @Override
    public int getPriority() {
        return 1000; // Low priority
    }
}

@IocBean
public class EventBus {
    private final List<EventProcessor> processors;

    public EventBus(@IocMulti(EventProcessor.class)
                   List<EventProcessor> processors) {
        // Sort by priority
        this.processors = processors.stream()
            .sorted(Comparator.comparingInt(EventProcessor::getPriority))
            .collect(Collectors.toList());
    }

    public void fire(Event event) {
        for (EventProcessor processor : processors) {
            if (processor.canProcess(event)) {
                processor.process(event);
            }
        }
    }
}
```

### Command System

Build command systems with dynamic command handlers:

```java
public interface SubCommand {
    String getAction();
    void execute(CommandSender sender, String[] args);
}

@IocBean
@IocMultiProvider(SubCommand.class)
public class HelpSubCommand implements SubCommand {
    @Override
    public String getAction() {
        return "help";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("Help information...");
    }
}

@IocBean
@IocMultiProvider(SubCommand.class)
public class ReloadSubCommand implements SubCommand {
    @Override
    public String getAction() {
        return "reload";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // Reload logic
    }
}

@IocBukkitCommandHandler("myplugin")
public class MainCommand extends RootCommand {
    private final Map<String, SubCommand> subCommandMap;

    public MainCommand(CommandExceptionHandler exceptionHandler,
                      @IocMulti(SubCommand.class) List<SubCommand> subCommands,
                      Messages messages,
                      TubingPermissionService permissionService) {
        super(exceptionHandler, subCommands, messages, permissionService);

        this.subCommandMap = new HashMap<>();
        for (SubCommand subCommand : subCommands) {
            subCommandMap.put(subCommand.getAction().toLowerCase(), subCommand);
        }
    }

    @Override
    protected boolean executeCmd(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /myplugin <action>");
            return true;
        }

        SubCommand subCommand = subCommandMap.get(args[0].toLowerCase());
        if (subCommand == null) {
            sender.sendMessage("Unknown action: " + args[0]);
            return true;
        }

        subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }
}
```

### Validation Pipeline

Build validation systems with multiple validators:

```java
public interface Validator<T> {
    ValidationResult validate(T object);
}

public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    public ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }

    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

    public boolean isValid() { return valid; }
    public String getErrorMessage() { return errorMessage; }
}

@IocBean
@IocMultiProvider(Validator.class)
public class NameValidator implements Validator<Home> {
    @Override
    public ValidationResult validate(Home home) {
        if (home.getName() == null || home.getName().isEmpty()) {
            return ValidationResult.failure("Home name cannot be empty");
        }
        if (!home.getName().matches("[a-zA-Z0-9_]+")) {
            return ValidationResult.failure("Home name contains invalid characters");
        }
        return ValidationResult.success();
    }
}

@IocBean
@IocMultiProvider(Validator.class)
public class LocationValidator implements Validator<Home> {
    @Override
    public ValidationResult validate(Home home) {
        if (home.getLocation() == null) {
            return ValidationResult.failure("Home location cannot be null");
        }
        if (home.getLocation().getWorld() == null) {
            return ValidationResult.failure("Home world does not exist");
        }
        return ValidationResult.success();
    }
}

@IocBean
public class ValidationService {
    private final List<Validator<Home>> validators;

    public ValidationService(@IocMulti(Validator.class)
                            List<Validator<Home>> validators) {
        this.validators = validators;
    }

    public ValidationResult validateHome(Home home) {
        for (Validator<Home> validator : validators) {
            ValidationResult result = validator.validate(home);
            if (!result.isValid()) {
                return result; // Fail fast
            }
        }
        return ValidationResult.success();
    }
}
```

## Combining with @IocBeanMultiProvider

The `@IocBean` annotation has a `multiproviderClass` attribute that provides a shorthand alternative to `@IocMultiProvider`:

### Using multiproviderClass

```java
@IocBean(multiproviderClass = EventHandler.class)
public class PlayerJoinHandler implements EventHandler {
    // Equivalent to @IocBean @IocMultiProvider(EventHandler.class)
}
```

This is syntactic sugar for the more common pattern:

```java
@IocBean
@IocMultiProvider(EventHandler.class)
public class PlayerJoinHandler implements EventHandler {
    // Same effect
}
```

### How It Works

The container checks for the `multiproviderClass` attribute during bean instantiation:

```java
// IocContainer.java (simplified)
Optional<Annotation> first = Arrays.stream(aClass.getAnnotations())
    .filter(a1 -> beanAnnotations.contains(a1.annotationType())).findFirst();
if (first.isPresent()) {
    Annotation annotation = first.get();
    Class multiproviderClass = (Class) annotation.annotationType()
        .getMethod("multiproviderClass").invoke(annotation);
    if (multiproviderClass != Object.class) {
        Object bean = createBean(scanResult, aClass, validBeans, providedBeans, multiProviders);
        beans.putIfAbsent(multiproviderClass, new ArrayList<>());
        List list = (List) beans.get(multiproviderClass);
        if (!list.contains(bean) && bean != null) {
            list.add(bean);
        }
        return bean;
    }
}
```

### When to Use Each

**Use `@IocMultiProvider` when:**
- You need to contribute to multiple interfaces
- The syntax is clearer for your use case
- You're using platform-specific annotations (`@IocBukkitListener`, etc.)

```java
@IocBean
@IocMultiProvider({EventHandler.class, Validator.class})
public class MultiPurposeHandler implements EventHandler, Validator { }
```

**Use `multiproviderClass` when:**
- You're only contributing to a single interface
- You prefer a more compact annotation
- You want to keep annotations on a single line

```java
@IocBean(multiproviderClass = EventHandler.class)
public class SimpleHandler implements EventHandler { }
```

Both approaches produce identical runtime behavior.

## Order of Implementations

The order of implementations in the injected list depends on discovery order and is generally not guaranteed. If order matters, you should sort the list explicitly.

### Discovery Order

Implementations are added to the list in the order they're discovered during classpath scanning:

```java
@IocBean
@IocMultiProvider(Handler.class)
public class HandlerA implements Handler { }  // Discovered first

@IocBean
@IocMultiProvider(Handler.class)
public class HandlerB implements Handler { }  // Discovered second

@IocBean
@IocMultiProvider(Handler.class)
public class HandlerC implements Handler { }  // Discovered third

// The list will contain [HandlerA, HandlerB, HandlerC]
// But this order is NOT guaranteed across different environments
```

### Explicit Ordering

If you need predictable order, add a priority/order method and sort explicitly:

```java
public interface Handler {
    void handle(Event event);
    int getOrder(); // Lower = higher priority
}

@IocBean
@IocMultiProvider(Handler.class)
public class HighPriorityHandler implements Handler {
    @Override
    public void handle(Event event) { }

    @Override
    public int getOrder() {
        return 100; // High priority
    }
}

@IocBean
@IocMultiProvider(Handler.class)
public class LowPriorityHandler implements Handler {
    @Override
    public void handle(Event event) { }

    @Override
    public int getOrder() {
        return 1000; // Low priority
    }
}

@IocBean
public class HandlerCoordinator {
    private final List<Handler> handlers;

    public HandlerCoordinator(@IocMulti(Handler.class) List<Handler> handlers) {
        // Sort by order
        this.handlers = handlers.stream()
            .sorted(Comparator.comparingInt(Handler::getOrder))
            .collect(Collectors.toList());
    }

    public void process(Event event) {
        for (Handler handler : handlers) {
            handler.handle(event);
        }
    }
}
```

### Alternative: Named Priority

Use an annotation-based approach:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HandlerPriority {
    int value() default 500;
}

@IocBean
@IocMultiProvider(Handler.class)
@HandlerPriority(100) // High priority
public class ImportantHandler implements Handler {
    @Override
    public void handle(Event event) { }
}

@IocBean
@IocMultiProvider(Handler.class)
@HandlerPriority(1000) // Low priority
public class OptionalHandler implements Handler {
    @Override
    public void handle(Event event) { }
}

@IocBean
public class HandlerCoordinator {
    private final List<Handler> handlers;

    public HandlerCoordinator(@IocMulti(Handler.class) List<Handler> handlers) {
        this.handlers = handlers.stream()
            .sorted((h1, h2) -> {
                int p1 = h1.getClass().isAnnotationPresent(HandlerPriority.class)
                    ? h1.getClass().getAnnotation(HandlerPriority.class).value()
                    : 500;
                int p2 = h2.getClass().isAnnotationPresent(HandlerPriority.class)
                    ? h2.getClass().getAnnotation(HandlerPriority.class).value()
                    : 500;
                return Integer.compare(p1, p2);
            })
            .collect(Collectors.toList());
    }
}
```

## Practical Example: Reward System

Complete example demonstrating multi-implementation in a reward system:

```java
// Core interfaces
public interface RewardHandler {
    String getRewardType();
    void giveReward(Player player, RewardData data);
}

public class RewardData {
    private final String type;
    private final int amount;
    private final Map<String, Object> metadata;

    public RewardData(String type, int amount, Map<String, Object> metadata) {
        this.type = type;
        this.amount = amount;
        this.metadata = metadata;
    }

    public String getType() { return type; }
    public int getAmount() { return amount; }
    public Map<String, Object> getMetadata() { return metadata; }
}

// Implementations
@IocBean
@IocMultiProvider(RewardHandler.class)
public class MoneyRewardHandler implements RewardHandler {
    private final EconomyService economy;

    public MoneyRewardHandler(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public String getRewardType() {
        return "money";
    }

    @Override
    public void giveReward(Player player, RewardData data) {
        economy.deposit(player, data.getAmount());
        player.sendMessage("You received $" + data.getAmount() + "!");
    }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ItemRewardHandler implements RewardHandler {

    @Override
    public String getRewardType() {
        return "item";
    }

    @Override
    public void giveReward(Player player, RewardData data) {
        String itemType = (String) data.getMetadata().get("item");
        ItemStack item = new ItemStack(Material.valueOf(itemType), data.getAmount());
        player.getInventory().addItem(item);
        player.sendMessage("You received " + data.getAmount() + "x " + itemType + "!");
    }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class ExperienceRewardHandler implements RewardHandler {

    @Override
    public String getRewardType() {
        return "experience";
    }

    @Override
    public void giveReward(Player player, RewardData data) {
        player.giveExp(data.getAmount());
        player.sendMessage("You received " + data.getAmount() + " experience!");
    }
}

@IocBean
@IocMultiProvider(RewardHandler.class)
public class PermissionRewardHandler implements RewardHandler {
    private final PermissionService permissions;

    public PermissionRewardHandler(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public String getRewardType() {
        return "permission";
    }

    @Override
    public void giveReward(Player player, RewardData data) {
        String permission = (String) data.getMetadata().get("permission");
        int durationDays = data.getAmount();
        permissions.grantTemporary(player, permission, durationDays);
        player.sendMessage("You received the " + permission + " permission for " + durationDays + " days!");
    }
}

// Service that uses all handlers
@IocBean
public class RewardService {
    private final Map<String, RewardHandler> handlerMap;
    private final MessageService messages;

    public RewardService(@IocMulti(RewardHandler.class) List<RewardHandler> handlers,
                        MessageService messages) {
        this.messages = messages;
        this.handlerMap = new HashMap<>();

        // Build lookup map
        for (RewardHandler handler : handlers) {
            handlerMap.put(handler.getRewardType(), handler);
        }
    }

    public void giveReward(Player player, RewardData reward) {
        RewardHandler handler = handlerMap.get(reward.getType());

        if (handler == null) {
            messages.sendError(player, "Unknown reward type: " + reward.getType());
            return;
        }

        try {
            handler.giveReward(player, reward);
        } catch (Exception e) {
            messages.sendError(player, "Failed to give reward: " + e.getMessage());
        }
    }

    public void giveMultipleRewards(Player player, List<RewardData> rewards) {
        for (RewardData reward : rewards) {
            giveReward(player, reward);
        }
    }

    public boolean isValidRewardType(String type) {
        return handlerMap.containsKey(type);
    }

    public Set<String> getAvailableRewardTypes() {
        return handlerMap.keySet();
    }
}

// Usage in a command
@IocBukkitCommandHandler("givereward")
public class GiveRewardCommand {
    private final RewardService rewardService;

    public GiveRewardCommand(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    public boolean onCommand(CommandSender sender, Command command,
                            String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /givereward <player> <type> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found");
            return true;
        }

        String rewardType = args[1];
        if (!rewardService.isValidRewardType(rewardType)) {
            sender.sendMessage("Invalid reward type. Available: " +
                rewardService.getAvailableRewardTypes());
            return true;
        }

        int amount = Integer.parseInt(args[2]);
        RewardData reward = new RewardData(rewardType, amount, new HashMap<>());

        rewardService.giveReward(target, reward);
        sender.sendMessage("Reward given to " + target.getName());

        return true;
    }
}
```

## Best Practices

### 1. Use Descriptive Interface Names

```java
// Good - clear purpose
public interface PaymentProcessor { }
public interface NotificationHandler { }
public interface ValidationRule { }

// Bad - too generic
public interface Handler { }
public interface Processor { }
public interface Manager { }
```

### 2. Document Multi-Implementation Expectations

```java
/**
 * Handler for processing reward types.
 *
 * Multiple implementations can be registered to support different reward types.
 * Each implementation should return a unique reward type identifier.
 *
 * Implementations are discovered via @IocMultiProvider(RewardHandler.class).
 */
public interface RewardHandler {
    String getRewardType();
    void giveReward(Player player, RewardData data);
}
```

### 3. Provide Default Implementations

```java
@IocBean
@IocMultiProvider(StorageProvider.class)
@ConditionalOnMissingBean
public class DefaultFileStorageProvider implements StorageProvider {
    // Used if no other implementations are found
}
```

### 4. Build Lookup Maps for Performance

```java
@IocBean
public class HandlerService {
    private final Map<String, Handler> handlerMap;

    public HandlerService(@IocMulti(Handler.class) List<Handler> handlers) {
        this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(Handler::getId, h -> h));
    }

    public void handle(String id, Object data) {
        Handler handler = handlerMap.get(id); // O(1) lookup
        if (handler != null) {
            handler.handle(data);
        }
    }
}
```

### 5. Validate Implementations on Startup

```java
@TubingConfiguration
public class ValidationConfiguration {

    @AfterIocLoad
    public static void validateHandlers(
            @IocMulti(RewardHandler.class) List<RewardHandler> handlers) {

        Set<String> types = new HashSet<>();
        for (RewardHandler handler : handlers) {
            String type = handler.getRewardType();
            if (types.contains(type)) {
                throw new IllegalStateException(
                    "Duplicate reward type: " + type);
            }
            types.add(type);
        }
    }
}
```

### 6. Handle Empty Lists Gracefully

```java
@IocBean
public class ProcessorService {
    private final List<Processor> processors;

    public ProcessorService(@IocMulti(Processor.class) List<Processor> processors) {
        this.processors = processors;

        if (processors.isEmpty()) {
            // Log warning or use default behavior
            getLogger().warning("No processors registered");
        }
    }
}
```

### 7. Consider Using Streams

```java
@IocBean
public class ValidationService {
    private final List<Validator> validators;

    public ValidationService(@IocMulti(Validator.class) List<Validator> validators) {
        this.validators = validators;
    }

    public boolean validateAll(Object obj) {
        return validators.stream()
            .allMatch(v -> v.validate(obj));
    }

    public Optional<String> getFirstError(Object obj) {
        return validators.stream()
            .map(v -> v.validate(obj))
            .filter(r -> !r.isValid())
            .map(ValidationResult::getErrorMessage)
            .findFirst();
    }
}
```

## Troubleshooting

### "Cannot retrieve bean with interface X. Too many implementations registered"

**Problem:** You're using `container.get()` or injecting without `@IocMulti` when multiple implementations exist.

**Solution:** Use `@IocMulti` to inject the list:

```java
// Before (error)
public Service(Handler handler) { }

// After (fixed)
public Service(@IocMulti(Handler.class) List<Handler> handlers) { }
```

### Empty List Injected

**Problem:** No implementations are being found.

**Checklist:**
1. Verify implementations have `@IocBean` annotation
2. Verify implementations have `@IocMultiProvider(Interface.class)` annotation
3. Verify implementations are in the scanned package
4. Check if implementations are conditionally disabled

```java
// Verify your implementations look like this:
@IocBean
@IocMultiProvider(YourInterface.class)
public class YourImplementation implements YourInterface { }
```

### Implementations Not Found After Reload

**Problem:** Multi-provider list is empty after plugin reload.

**Solution:** The container is recreated on reload. Ensure all implementations are properly annotated and scanned.

### Duplicate Entries in List

**Problem:** Same implementation appears multiple times.

**Cause:** The container checks for duplicates using `.contains()`, which relies on `.equals()`. If your implementations don't override `equals()`, duplicates may not be detected.

**Solution:** The container should prevent duplicates automatically. If you encounter this, ensure you're not manually registering beans.

## Next Steps

Now that you understand multi-implementation:

- Learn about [Bean Providers](Bean-Providers.md) for creating collections of beans
- Explore [Post-Initialization](Post-Initialization.md) to validate multi-provider lists on startup
- Read [Plugin APIs](../advanced/Plugin-APIs.md) for building extensible systems
- Check [Testing](../best-practices/Testing.md) for testing multi-implementation systems

---

**See also:**
- [Dependency Injection](Dependency-Injection.md) - Understanding injection patterns
- [IoC Container](IoC-Container.md) - How the container manages beans
- [Bean Lifecycle](Bean-Lifecycle.md) - When multi-provider beans are instantiated
