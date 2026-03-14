# Bukkit Messaging

Tubing provides a comprehensive messaging system for Bukkit plugins that handles localization, color codes, placeholder replacement, and message formatting. The `Messages` service simplifies sending messages to players and command senders while supporting multi-language patterns and custom message prefixes.

## Overview

The Tubing messaging system offers several key features:

- **Color Code Support**: Automatic translation of `&` color codes to Minecraft color codes
- **Message Prefixes**: Configurable prefixes for all plugin messages
- **Placeholder Support**: Integration with PlaceholderAPI for dynamic placeholders
- **Multi-line Messages**: Support for multi-line messages with `\n` separator
- **Broadcast Messages**: Send messages to all online players or filtered groups
- **Localization Ready**: Designed to work with configuration-based message systems
- **No-Prefix Messages**: Option to send messages without the plugin prefix

## The Messages Service

### Injecting the Messages Service

The `Messages` service is automatically registered as an `@IocBean` and can be injected into any of your classes:

```java
@IocBean
public class WelcomeService {

    private final Messages messages;

    public WelcomeService(Messages messages) {
        this.messages = messages;
    }

    public void welcomePlayer(Player player) {
        messages.send(player, "&aWelcome to the server, &e%player_name%&a!");
    }
}
```

### Basic Message Sending

Send a message to a command sender (player or console):

```java
messages.send(sender, "&aYou have been teleported!");
```

Send a message without the plugin prefix:

```java
messages.sendNoPrefix(sender, "&7This message has no prefix");
```

Send multiple lines:

```java
messages.send(sender, "&6=== Server Rules ===\n" +
    "&71. Be respectful\n" +
    "&72. No griefing\n" +
    "&73. Have fun!");
```

## Color Code Support

### Color Code Translation

The Messages service automatically translates `&` color codes to Minecraft's `§` color codes:

```java
// Input: "&aGreen &cRed &9Blue"
// Output: "§aGreen §cRed §9Blue"
messages.send(player, "&aGreen &cRed &9Blue");
```

### Supported Color Codes

Standard Minecraft color codes are supported:

- `&0` - Black
- `&1` - Dark Blue
- `&2` - Dark Green
- `&3` - Dark Aqua
- `&4` - Dark Red
- `&5` - Dark Purple
- `&6` - Gold
- `&7` - Gray
- `&8` - Dark Gray
- `&9` - Blue
- `&a` - Green
- `&b` - Aqua
- `&c` - Red
- `&d` - Light Purple
- `&e` - Yellow
- `&f` - White

Format codes:

- `&k` - Obfuscated
- `&l` - Bold
- `&m` - Strikethrough
- `&n` - Underline
- `&o` - Italic
- `&r` - Reset

### Escaping Ampersands

If you need to use a literal `&` character, use `&&`:

```java
// Input: "Use && to escape ampersands"
// Output: "Use & to escape ampersands"
messages.send(player, "Use && to escape ampersands");
```

### Manual Colorization

You can colorize strings without sending them:

```java
String colorized = messages.colorize("&aThis is green");
// Returns: "§aThis is green"
```

## Message Prefixes

### Configuring the Default Prefix

The default message prefix is configured in your `config.yml`:

```yaml
messages:
  prefix: "&8[&6MyPlugin&8]"
```

This prefix is automatically added to all messages sent via `messages.send()`:

```java
// With prefix: "&8[&6MyPlugin&8]"
messages.send(player, "&aWelcome!");
// Player sees: "[MyPlugin] Welcome!" (colorized)
```

### Disabling Prefix for Specific Messages

Use the `[NO_PREFIX]` marker at the start of a message to disable the prefix:

```java
messages.send(player, "[NO_PREFIX]&aThis message has no prefix");
```

Or use the dedicated method:

```java
messages.sendNoPrefix(player, "&aThis message has no prefix");
```

### Custom Prefix Provider

You can provide your own prefix implementation by creating a bean that implements `MessagePrefixProvider`:

```java
@IocBean
public class CustomPrefixProvider implements MessagePrefixProvider {

    @Override
    public String getPrefix() {
        // Dynamic prefix based on time, context, etc.
        LocalTime now = LocalTime.now();
        if (now.getHour() < 12) {
            return "&e[Morning]";
        } else {
            return "&6[Evening]";
        }
    }
}
```

The default `TubingMessagePrefixProvider` uses `@ConditionalOnMissingBean`, so your custom implementation will automatically replace it.

## Placeholder Replacement

### PlaceholderAPI Integration

The Messages service automatically integrates with PlaceholderAPI if it's installed on the server. All placeholders are resolved before messages are sent:

```java
// With PlaceholderAPI installed:
messages.send(player, "&aHello %player_name%! You have %player_level% levels.");
// Output: "Hello Steve! You have 30 levels."
```

### How It Works

The `PlaceholderService` is automatically injected into the Messages service:

```java
@IocBean
public class PlaceholderService {

    public PlaceholderService(@InjectTubingPlugin TubingPlugin tubingPlugin) {
        Plugin placeholderPlugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderPlugin != null) {
            usesPlaceholderAPI = true;
            tubingPlugin.getLogger().info("Hooked into PlaceholderAPI " +
                placeholderPlugin.getDescription().getVersion());
        }
    }
}
```

### Without PlaceholderAPI

If PlaceholderAPI is not installed, placeholders are left as-is in the message:

```java
// Without PlaceholderAPI:
messages.send(player, "Hello %player_name%!");
// Output: "Hello %player_name%!" (literal text)
```

### Manual Placeholder Parsing

You can manually parse placeholders and color codes:

```java
String parsed = messages.parse(player, "&aYour balance: &e$%vault_eco_balance%");
// Returns the colorized and placeholder-resolved string
```

## Broadcasting Messages

### Broadcast to All Players

Send a message to all online players:

```java
messages.broadcast("&6Server restart in 5 minutes!");
```

This sends the message with the configured prefix to all online players.

### Send to Player Collection

Send a message to a specific group of players:

```java
Collection<Player> teamMembers = getTeamMembers();
messages.send(teamMembers, "&aYour team has captured the flag!");
```

### Permission-Based Broadcasting

Send a message only to players with a specific permission:

```java
// Only players with "myplugin.admin" permission will see this
messages.sendGroupMessage("&cAdmin alert: Suspicious activity detected", "myplugin.admin");
```

Send to a single player with permission check:

```java
// Only sends if player has the permission
messages.send(player, "&aSecret admin message", "myplugin.admin.secrets");
```

### Global Broadcast

Send a global broadcast message (bypasses the Messages service formatting):

```java
messages.sendGlobalMessage("&6[ANNOUNCEMENT] Server event starting now!");
```

## Multi-Language Support Patterns

### Configuration-Based Messages

The recommended approach for multi-language support is to store messages in your configuration files and load them through the Messages service:

```yaml
# config.yml or messages.yml
messages:
  prefix: "&8[&6MyPlugin&8]"
  welcome: "&aWelcome to the server, &e%player_name%&a!"
  farewell: "&7Goodbye, &e%player_name%&7!"
  no-permission: "&cYou don't have permission to do that!"
  teleport-success: "&aYou have been teleported to &e%location%&a!"
  teleport-failed: "&cTeleport failed: &7%reason%"
```

Load and use these messages in your code:

```java
@IocBean
public class MessageService {

    private final Messages messages;
    private final ConfigurationLoader configurationLoader;

    public MessageService(Messages messages, ConfigurationLoader configurationLoader) {
        this.messages = messages;
        this.configurationLoader = configurationLoader;
    }

    public void sendWelcome(Player player) {
        String message = configurationLoader.getConfigStringValue("messages.welcome")
            .orElse("&aWelcome!");
        messages.send(player, message);
    }

    public void sendTeleportSuccess(Player player, String location) {
        String message = configurationLoader.getConfigStringValue("messages.teleport-success")
            .orElse("&aTeleported!")
            .replace("%location%", location);
        messages.send(player, message);
    }
}
```

### Language File Pattern

For more advanced multi-language support, create separate language files:

```
plugins/MyPlugin/
├── config.yml
├── lang/
│   ├── en_US.yml
│   ├── es_ES.yml
│   ├── fr_FR.yml
│   └── de_DE.yml
```

Example `en_US.yml`:

```yaml
welcome: "&aWelcome to the server!"
farewell: "&7Goodbye!"
no-permission: "&cYou don't have permission!"
```

Example `es_ES.yml`:

```yaml
welcome: "&a¡Bienvenido al servidor!"
farewell: "&7¡Adiós!"
no-permission: "&c¡No tienes permiso!"
```

Create a language manager:

```java
@IocBean
public class LanguageManager {

    private final Messages messages;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String defaultLanguage = "en_US";

    @PostConstruct
    public void loadLanguages() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // Load all language files
        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File file : langFiles) {
                String langCode = file.getName().replace(".yml", "");
                languages.put(langCode, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    public void sendMessage(Player player, String key, Object... replacements) {
        String lang = getPlayerLanguage(player); // Get from player data
        String message = getMessage(lang, key, replacements);
        messages.send(player, message);
    }

    private String getMessage(String lang, String key, Object... replacements) {
        YamlConfiguration config = languages.getOrDefault(lang,
            languages.get(defaultLanguage));

        String message = config.getString(key, "&cMessage not found: " + key);

        // Simple placeholder replacement
        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(replacements[i]));
        }

        return message;
    }

    private String getPlayerLanguage(Player player) {
        // Get player's preferred language from data storage
        // Default to server language
        return defaultLanguage;
    }
}
```

Usage:

```java
languageManager.sendMessage(player, "welcome");
languageManager.sendMessage(player, "teleport-success", "spawn");
```

### Integration with Command Exceptions

The Messages service integrates with Tubing's exception handling system. Store error messages in configuration:

```yaml
errors:
  invalid-player: "&cPlayer not found: &7%player%"
  insufficient-funds: "&cYou need &e$%amount%&c to do that!"
  command-usage: "&cUsage: &7%usage%"
```

The `DefaultCommandExceptionHandler` automatically loads messages from configuration:

```java
@IocBean
@ConditionalOnMissingBean
public class DefaultCommandExceptionHandler implements CommandExceptionHandler {

    private final ConfigurationLoader configurationLoader;
    private final Messages messages;

    @Override
    public void handle(CommandSender commandSender, Throwable commandException) {
        // Tries to load from config, falls back to exception message
        String message = configurationLoader.getConfigStringValue(commandException.getMessage())
            .orElse(commandException.getMessage());
        messages.send(commandSender, message);
    }
}
```

Throw exceptions with config keys:

```java
throw new CommandException("errors.invalid-player");
```

## Message Formatting Best Practices

### Consistent Color Scheme

Define a consistent color scheme for your plugin:

```yaml
colors:
  primary: "&6"      # Gold for main messages
  secondary: "&e"    # Yellow for highlights
  success: "&a"      # Green for success
  error: "&c"        # Red for errors
  info: "&7"         # Gray for information
  warning: "&e"      # Yellow for warnings
```

Load these in a constants class:

```java
@IocBean
public class MessageColors {

    @ConfigProperty("colors.primary")
    private String primary;

    @ConfigProperty("colors.secondary")
    private String secondary;

    @ConfigProperty("colors.success")
    private String success;

    @ConfigProperty("colors.error")
    private String error;

    public String getPrimary() { return primary; }
    public String getSecondary() { return secondary; }
    public String getSuccess() { return success; }
    public String getError() { return error; }
}
```

### Message Templates

Create reusable message templates:

```java
@IocBean
public class MessageTemplates {

    private final Messages messages;
    private final MessageColors colors;

    public void sendSuccess(CommandSender sender, String message) {
        messages.send(sender, colors.getSuccess() + message);
    }

    public void sendError(CommandSender sender, String message) {
        messages.send(sender, colors.getError() + message);
    }

    public void sendInfo(CommandSender sender, String message) {
        messages.send(sender, colors.getInfo() + message);
    }

    public void sendHeader(CommandSender sender, String title) {
        messages.send(sender, "\n" + colors.getPrimary() + "&m          " +
            "&r " + colors.getSecondary() + "&l" + title +
            " " + colors.getPrimary() + "&m          ");
    }

    public void sendFooter(CommandSender sender) {
        messages.send(sender, colors.getPrimary() + "&m                              \n");
    }
}
```

### Readable Multi-Line Messages

Use Java text blocks (Java 15+) for readable multi-line messages:

```java
String helpMessage = """
    &6=== MyPlugin Help ===
    &e/myplugin help &7- Show this help
    &e/myplugin reload &7- Reload configuration
    &e/myplugin info &7- Show plugin info
    &6=====================
    """;

messages.send(sender, helpMessage);
```

Or use string concatenation with clear formatting:

```java
messages.send(sender,
    "&6=== MyPlugin Help ===\n" +
    "&e/myplugin help &7- Show this help\n" +
    "&e/myplugin reload &7- Reload configuration\n" +
    "&e/myplugin info &7- Show plugin info\n" +
    "&6====================="
);
```

### Action Bar and Title Messages

While the Messages service handles chat messages, you may want to send action bar or title messages. Create a separate service:

```java
@IocBean
public class DisplayMessageService {

    private final Messages messages;

    public void sendActionBar(Player player, String message) {
        String parsed = messages.colorize(message);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(parsed));
    }

    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
            messages.colorize(title),
            messages.colorize(subtitle),
            fadeIn, stay, fadeOut
        );
    }
}
```

## Advanced Patterns

### Message Builder Pattern

Create a fluent builder for complex messages:

```java
@IocBean
public class MessageBuilder {

    private final Messages messages;
    private final StringBuilder content = new StringBuilder();
    private String prefix = null;

    public MessageBuilder(Messages messages) {
        this.messages = messages;
    }

    public MessageBuilder line(String text) {
        if (content.length() > 0) {
            content.append("\n");
        }
        content.append(text);
        return this;
    }

    public MessageBuilder noPrefix() {
        this.prefix = "[NO_PREFIX]";
        return this;
    }

    public void send(CommandSender sender) {
        String message = content.toString();
        if (prefix != null) {
            message = prefix + message;
        }
        messages.send(sender, message);
        content.setLength(0); // Reset for reuse
        prefix = null;
    }
}
```

Usage:

```java
messageBuilder
    .line("&6=== Player Stats ===")
    .line("&eKills: &f" + kills)
    .line("&eDeaths: &f" + deaths)
    .line("&eK/D Ratio: &f" + ratio)
    .send(player);
```

### Paginated Messages

Create a pagination helper for long message lists:

```java
@IocBean
public class PaginationService {

    private final Messages messages;
    private static final int LINES_PER_PAGE = 8;

    public void sendPaginatedList(CommandSender sender, String title,
                                   List<String> items, int page) {
        int totalPages = (int) Math.ceil((double) items.size() / LINES_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        int startIndex = (page - 1) * LINES_PER_PAGE;
        int endIndex = Math.min(startIndex + LINES_PER_PAGE, items.size());

        messages.send(sender, "&6&m          &r &e" + title + " &7(Page " +
            page + "/" + totalPages + ")&6&m          ");

        for (int i = startIndex; i < endIndex; i++) {
            messages.send(sender, items.get(i));
        }

        if (page < totalPages) {
            messages.send(sender, "&7Use &e/command " + (page + 1) +
                "&7 for the next page");
        }
    }
}
```

### Clickable Messages (Text Components)

For clickable messages with hover effects, extend the Messages service:

```java
@IocBean
public class InteractiveMessageService {

    private final Messages messages;

    public void sendClickableMessage(Player player, String message,
                                     String command, String hover) {
        TextComponent component = new TextComponent(
            TextComponent.fromLegacyText(messages.colorize(message))
        );
        component.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND, command
        ));
        component.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(messages.colorize(hover)).create()
        ));
        player.spigot().sendMessage(component);
    }
}
```

## Complete Example

Here's a complete example showing all messaging features:

```java
@IocBean
public class PlayerManager {

    private final Messages messages;
    private final ConfigurationLoader configurationLoader;

    public PlayerManager(Messages messages, ConfigurationLoader configurationLoader) {
        this.messages = messages;
        this.configurationLoader = configurationLoader;
    }

    public void welcomePlayer(Player player) {
        // Load message from config with fallback
        String welcomeMsg = configurationLoader
            .getConfigStringValue("messages.welcome")
            .orElse("&aWelcome, %player_name%!");

        messages.send(player, welcomeMsg);
    }

    public void showPlayerInfo(CommandSender sender, Player target) {
        messages.send(sender,
            "&6=== Player Info: &e" + target.getName() + " &6===\n" +
            "&7UUID: &f" + target.getUniqueId() + "\n" +
            "&7Health: &f" + target.getHealth() + "/" + target.getMaxHealth() + "\n" +
            "&7Location: &f" + formatLocation(target.getLocation()) + "\n" +
            "&7Game Mode: &f" + target.getGameMode()
        );
    }

    public void broadcastJoin(Player player) {
        String joinMsg = configurationLoader
            .getConfigStringValue("messages.player-join")
            .orElse("&e%player_name% &7joined the server")
            .replace("%player_name%", player.getName());

        messages.broadcast(joinMsg);
    }

    public void notifyAdmins(String alert) {
        messages.sendGroupMessage("&c[ADMIN] &7" + alert, "myplugin.admin");
    }

    private String formatLocation(Location loc) {
        return String.format("%s (%d, %d, %d)",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        );
    }
}
```

## API Reference

### Messages Class

| Method | Description |
|--------|-------------|
| `send(CommandSender, String)` | Send a message with prefix |
| `sendNoPrefix(CommandSender, String)` | Send a message without prefix |
| `send(CommandSender, List<String>)` | Send multiple message lines |
| `send(Collection<Player>, String)` | Send to multiple players |
| `send(Player, String, String)` | Send to player with permission check |
| `broadcast(String)` | Broadcast to all online players |
| `sendGlobalMessage(String)` | Global broadcast (bypasses formatting) |
| `sendGroupMessage(String, String)` | Broadcast to players with permission |
| `colorize(String)` | Translate color codes without sending |
| `parse(Player, String)` | Parse placeholders and colorize |

### MessagePrefixProvider Interface

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getPrefix()` | `String` | Returns the message prefix |

### PlaceholderService Class

| Method | Description |
|--------|-------------|
| `setPlaceholders(CommandSender, String)` | Replace PlaceholderAPI placeholders |
| `setPlaceholders(Player, String)` | Replace PlaceholderAPI placeholders for player |

## See Also

- [Commands](Commands.md) - Command handling and error messages
- [Subcommands](Subcommands.md) - Complex command hierarchies
- [Configuration Injection](../core/Configuration-Injection.md) - Loading messages from config
- [Bukkit Setup](Bukkit-Setup.md) - Plugin setup and initialization
