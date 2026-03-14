# Bukkit Plugin Messages

Tubing provides automatic registration and dependency injection for Bukkit plugin message listeners, making cross-server communication seamless. The `@IocBukkitMessageListener` annotation simplifies plugin messaging for BungeeCord, Velocity, and custom plugin channels.

## Overview

Plugin messaging (also called plugin channels) allows plugins on Bukkit servers to communicate with:
- **Proxy servers** (BungeeCord, Velocity, Waterfall)
- **Other plugins** on the same server
- **Custom backend services**

Tubing automatically registers plugin message listeners and injects their dependencies, eliminating boilerplate code.

**Key benefits:**
- **Automatic Registration**: Listeners are discovered and registered automatically
- **Dependency Injection**: Message handlers receive dependencies via constructor injection
- **Conditional Loading**: Register listeners based on configuration properties
- **Type Safety**: Strong typing for message handlers
- **Priority Control**: Control listener registration order
- **Multi-Provider Support**: Multiple handlers for the same channel

## Basic Plugin Message Listener

### Simple Message Listener

Create a message listener by implementing `PluginMessageListener` and using the `@IocBukkitMessageListener` annotation:

```java
package com.example.myplugin.messaging;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitMessageListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

@IocBukkitMessageListener(channel = "BungeeCord")
public class BungeeMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
             DataInputStream in = new DataInputStream(stream)) {

            String subchannel = in.readUTF();

            if (subchannel.equals("PlayerCount")) {
                String server = in.readUTF();
                int playerCount = in.readInt();
                player.sendMessage("Server " + server + " has " + playerCount + " players");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

**What happens:**
1. Tubing discovers the `@IocBukkitMessageListener` annotation during startup
2. The listener is instantiated with dependency injection
3. Tubing registers it with Bukkit: `getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", listener)`
4. Messages on the "BungeeCord" channel are routed to this listener

## Annotation Attributes

The `@IocBukkitMessageListener` annotation supports several attributes:

```java
@IocBukkitMessageListener(
    channel = "myplugin:custom",
    conditionalOnProperty = "features.cross-server.enabled",
    priority = true,
    multiproviderClass = MessageHandler.class
)
```

### channel (required)

The plugin channel to listen on.

**Common channels:**
- `"BungeeCord"` - Standard BungeeCord channel
- `"bungeecord:main"` - Modern namespaced BungeeCord channel
- `"velocity:main"` - Velocity proxy channel
- `"myplugin:custom"` - Custom plugin channel (namespaced format recommended)

```java
@IocBukkitMessageListener(channel = "myplugin:sync")
public class DataSyncListener implements PluginMessageListener {
    // Listens on the "myplugin:sync" channel
}
```

**Best practice:** Use namespaced channels (plugin:subchannel) to avoid conflicts with other plugins.

### conditionalOnProperty

Only register the listener if a configuration property is true.

```java
@IocBukkitMessageListener(
    channel = "BungeeCord",
    conditionalOnProperty = "features.bungeecord.enabled"
)
public class BungeeListener implements PluginMessageListener {
    // Only registered if features.bungeecord.enabled = true in config
}
```

**config.yml:**
```yaml
features:
  bungeecord:
    enabled: true
```

**Use cases:**
- Enable/disable cross-server features
- Load different listeners based on environment (dev/prod)
- Feature flags for gradual rollouts

### priority

Control listener instantiation order. Priority listeners are instantiated before normal listeners.

```java
@IocBukkitMessageListener(channel = "BungeeCord", priority = true)
public class CriticalMessageListener implements PluginMessageListener {
    // Instantiated first during plugin startup
}
```

**Default:** `false` (normal priority)

**When to use:**
- The listener initializes infrastructure needed by other beans
- The listener must register before other message handlers
- The listener performs critical setup

**Note:** This controls bean instantiation order, not message processing order. All listeners on the same channel receive messages in Bukkit's event dispatch order.

### multiproviderClass

Register the listener as a multi-provider implementation of an interface.

```java
public interface MessageHandler {
    void handle(Player player, String data);
}

@IocBukkitMessageListener(
    channel = "myplugin:events",
    multiproviderClass = MessageHandler.class
)
public class EventMessageListener implements PluginMessageListener, MessageHandler {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String data = new String(message);
        handle(player, data);
    }

    @Override
    public void handle(Player player, String data) {
        // Process event message
    }
}
```

This makes the listener available via `@IocMulti(MessageHandler.class)`:

```java
@IocBean
public class MessageDispatcher {
    private final List<MessageHandler> handlers;

    public MessageDispatcher(@IocMulti(MessageHandler.class) List<MessageHandler> handlers) {
        this.handlers = handlers;
    }
}
```

## Receiving Plugin Messages

### BungeeCord Channel

The standard BungeeCord channel uses subchannels to organize message types.

```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class BungeeMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
             DataInputStream in = new DataInputStream(stream)) {

            String subchannel = in.readUTF();

            switch (subchannel) {
                case "PlayerCount":
                    handlePlayerCount(in);
                    break;
                case "PlayerList":
                    handlePlayerList(in);
                    break;
                case "GetServer":
                    handleGetServer(in, player);
                    break;
                default:
                    // Unknown subchannel
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePlayerCount(DataInputStream in) throws IOException {
        String server = in.readUTF();
        int playerCount = in.readInt();
        // Process player count
    }

    private void handlePlayerList(DataInputStream in) throws IOException {
        String server = in.readUTF();
        String[] players = in.readUTF().split(", ");
        // Process player list
    }

    private void handleGetServer(DataInputStream in, Player player) throws IOException {
        String server = in.readUTF();
        player.sendMessage("You are on server: " + server);
    }
}
```

### Custom Channel

For custom plugin-to-plugin communication, use your own channel:

```java
@IocBukkitMessageListener(channel = "myplugin:sync")
public class DataSyncListener implements PluginMessageListener {

    private final DataService dataService;

    public DataSyncListener(DataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
             DataInputStream in = new DataInputStream(stream)) {

            String action = in.readUTF();

            if (action.equals("UPDATE")) {
                String key = in.readUTF();
                String value = in.readUTF();
                dataService.update(key, value);
            } else if (action.equals("DELETE")) {
                String key = in.readUTF();
                dataService.delete(key);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### JSON Messages

For complex data structures, consider using JSON:

```java
@IocBukkitMessageListener(channel = "myplugin:json")
public class JsonMessageListener implements PluginMessageListener {

    private final Gson gson = new Gson();
    private final PlayerService playerService;

    public JsonMessageListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String json = new String(message, StandardCharsets.UTF_8);

        try {
            PlayerDataMessage data = gson.fromJson(json, PlayerDataMessage.class);
            playerService.updatePlayerData(data.getPlayerId(), data.getData());
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    private static class PlayerDataMessage {
        private UUID playerId;
        private Map<String, Object> data;

        // Getters
        public UUID getPlayerId() { return playerId; }
        public Map<String, Object> getData() { return data; }
    }
}
```

## Sending Plugin Messages

To send plugin messages, you need to register the outgoing channel and send the message via a player connection.

### Registering Outgoing Channel

Register outgoing channels in your plugin's `enable()` method:

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        // Register outgoing channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:sync");
    }
}
```

### Sending to BungeeCord

Create a service to handle sending messages:

```java
package com.example.myplugin.messaging;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@IocBean
public class BungeeMessenger {

    private final TubingBukkitPlugin plugin;

    public BungeeMessenger(TubingBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Connect a player to another server
     */
    public void connectPlayer(Player player, String serverName) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get player count on a server
     */
    public void getPlayerCount(Player player, String serverName) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("PlayerCount");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get player list on a server
     */
    public void getPlayerList(Player player, String serverName) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("PlayerList");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Forward a message to all servers
     */
    public void forwardToAll(Player player, String subchannel, byte[] data) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(subchannel);

            out.writeShort(data.length);
            out.write(data);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Forward a message to a specific server
     */
    public void forwardToServer(Player player, String serverName, String subchannel, byte[] data) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("Forward");
            out.writeUTF(serverName);
            out.writeUTF(subchannel);

            out.writeShort(data.length);
            out.write(data);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### Sending Custom Messages

For custom channels, create a similar service:

```java
@IocBean
public class CustomMessageSender {

    private final TubingBukkitPlugin plugin;

    public CustomMessageSender(TubingBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncData(Player player, String key, String value) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("UPDATE");
            out.writeUTF(key);
            out.writeUTF(value);

            player.sendPluginMessage(plugin, "myplugin:sync", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteData(Player player, String key) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("DELETE");
            out.writeUTF(key);

            player.sendPluginMessage(plugin, "myplugin:sync", stream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### Sending Without a Player

Plugin messages require a player connection. If you need to send messages without a specific player context, use any online player:

```java
@IocBean
public class AsyncMessageSender {

    private final TubingBukkitPlugin plugin;

    public AsyncMessageSender(TubingBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendMessage(String channel, byte[] data) {
        Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();

        if (onlinePlayers.isEmpty()) {
            plugin.getLogger().warning("Cannot send plugin message: no players online");
            return;
        }

        // Use first available player
        Player player = onlinePlayers.iterator().next();
        player.sendPluginMessage(plugin, channel, data);
    }
}
```

**Note:** This is a limitation of Bukkit's plugin messaging system - messages must be sent through a player connection, even if the message is not player-specific.

## Dependency Injection in Message Listeners

Message listeners are beans and support full dependency injection.

### Constructor Injection

Inject dependencies through the constructor:

```java
@IocBukkitMessageListener(channel = "myplugin:events")
public class EventMessageListener implements PluginMessageListener {

    private final PlayerService playerService;
    private final DataService dataService;
    private final Logger logger;

    public EventMessageListener(PlayerService playerService,
                                DataService dataService,
                                Logger logger) {
        this.playerService = playerService;
        this.dataService = dataService;
        this.logger = logger;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        logger.info("Received message on channel: " + channel);
        // Use injected services
    }
}
```

### Configuration Injection

Inject configuration properties:

```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class ConfigurableListener implements PluginMessageListener {

    @ConfigProperty("messaging.debug")
    private boolean debug;

    @ConfigProperty("messaging.timeout")
    private int timeout;

    private final MessageProcessor processor;

    public ConfigurableListener(MessageProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (debug) {
            // Log debug information
        }
        processor.process(message, timeout);
    }
}
```

### Multi-Implementation Dependencies

Inject lists of implementations:

```java
public interface MessageTransformer {
    byte[] transform(byte[] message);
}

@IocBukkitMessageListener(channel = "myplugin:secure")
public class SecureMessageListener implements PluginMessageListener {

    private final List<MessageTransformer> transformers;

    public SecureMessageListener(@IocMulti(MessageTransformer.class)
                                 List<MessageTransformer> transformers) {
        this.transformers = transformers;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Apply all transformers in sequence
        byte[] transformed = message;
        for (MessageTransformer transformer : transformers) {
            transformed = transformer.transform(transformed);
        }
        // Process transformed message
    }
}
```

## BungeeCord Integration

### Setting Up BungeeCord

1. **Enable BungeeCord mode in spigot.yml:**

```yaml
settings:
  bungeecord: true
```

2. **Register channels in your plugin:**

```java
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getLogger().info("BungeeCord plugin messaging enabled");
    }
}
```

3. **Create listener:**

```java
@IocBukkitMessageListener(
    channel = "BungeeCord",
    conditionalOnProperty = "bungeecord.enabled"
)
public class BungeeCordListener implements PluginMessageListener {
    // Listener implementation
}
```

### Common BungeeCord Subchannels

| Subchannel | Direction | Description |
|------------|-----------|-------------|
| `Connect` | Server → Proxy | Connect player to another server |
| `ConnectOther` | Server → Proxy | Connect a different player to server |
| `IP` | Server → Proxy → Server | Get player's IP address |
| `PlayerCount` | Server → Proxy → Server | Get player count on a server |
| `PlayerList` | Server → Proxy → Server | Get list of players on a server |
| `GetServers` | Server → Proxy → Server | Get list of all servers |
| `GetServer` | Server → Proxy → Server | Get the server a player is on |
| `UUID` | Server → Proxy → Server | Get a player's UUID |
| `UUIDOther` | Server → Proxy → Server | Get another player's UUID |
| `ServerIP` | Server → Proxy → Server | Get a server's IP address |
| `KickPlayer` | Server → Proxy | Kick a player from the proxy |
| `Forward` | Server → Proxy → Server(s) | Forward custom data to other servers |
| `ForwardToPlayer` | Server → Proxy → Server | Forward custom data to a specific player |

### Complete BungeeCord Example

```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class BungeeCordIntegration implements PluginMessageListener {

    private final TubingBukkitPlugin plugin;
    private final PlayerDataService playerData;

    // Store pending requests for callback handling
    private final Map<String, Consumer<String>> pendingRequests = new ConcurrentHashMap<>();

    public BungeeCordIntegration(TubingBukkitPlugin plugin, PlayerDataService playerData) {
        this.plugin = plugin;
        this.playerData = playerData;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
             DataInputStream in = new DataInputStream(stream)) {

            String subchannel = in.readUTF();

            switch (subchannel) {
                case "PlayerCount":
                    handlePlayerCount(in, player);
                    break;
                case "GetServer":
                    handleGetServer(in, player);
                    break;
                case "Forward":
                    handleForward(in, player);
                    break;
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Error processing BungeeCord message: " + e.getMessage());
        }
    }

    private void handlePlayerCount(DataInputStream in, Player player) throws IOException {
        String server = in.readUTF();
        int count = in.readInt();
        playerData.updateServerCount(server, count);
    }

    private void handleGetServer(DataInputStream in, Player player) throws IOException {
        String serverName = in.readUTF();
        playerData.setCurrentServer(player.getUniqueId(), serverName);
    }

    private void handleForward(DataInputStream in, Player player) throws IOException {
        short dataLength = in.readShort();
        byte[] data = new byte[dataLength];
        in.readFully(data);

        // Process forwarded data
        try (ByteArrayInputStream dataStream = new ByteArrayInputStream(data);
             DataInputStream dataIn = new DataInputStream(dataStream)) {

            String action = dataIn.readUTF();
            handleCustomAction(action, dataIn, player);
        }
    }

    private void handleCustomAction(String action, DataInputStream in, Player player) throws IOException {
        if (action.equals("SYNC_DATA")) {
            String key = in.readUTF();
            String value = in.readUTF();
            playerData.sync(key, value);
        }
    }

    // Helper method to send messages
    public void requestPlayerCount(Player player, String serverName, Consumer<Integer> callback) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("PlayerCount");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().severe("Error sending PlayerCount request: " + e.getMessage());
        }
    }
}
```

## Velocity Integration

Velocity uses a different messaging protocol from BungeeCord.

### Setting Up Velocity

1. **Configure Velocity forwarding:**

In `velocity.toml`:
```toml
[servers]
try = ["lobby", "survival", "creative"]

[player-info-forwarding]
mode = "modern"  # or "legacy" for BungeeCord compatibility
```

2. **Configure Paper/Spigot:**

In `paper.yml` or `config/paper-global.yml`:
```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "your-forwarding-secret-here"
```

### Velocity Modern Forwarding

Velocity's modern forwarding uses the `velocity:main` channel:

```java
@IocBukkitMessageListener(
    channel = "velocity:main",
    conditionalOnProperty = "velocity.enabled"
)
public class VelocityMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Velocity uses a different message format
        // Typically you'll use Velocity's built-in methods rather than custom messaging
    }
}
```

### Velocity Custom Channels

For custom messaging with Velocity:

```java
// Register in plugin
public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:custom");
        getServer().getMessenger().registerIncomingPluginChannel(this, "myplugin:custom",
            getIocContainer().get(CustomVelocityListener.class));
    }
}

// Listener (note: no @IocBukkitMessageListener needed if manually registered)
@IocBean
public class CustomVelocityListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Handle custom Velocity messages
    }
}
```

**Note:** Velocity's messaging system is more limited than BungeeCord's. For most cross-server features, consider using a messaging service like Redis pub/sub instead.

## Best Practices

### 1. Use Namespaced Channels

Avoid channel name conflicts by using namespaced format:

**Bad:**
```java
@IocBukkitMessageListener(channel = "sync")
```

**Good:**
```java
@IocBukkitMessageListener(channel = "myplugin:sync")
```

### 2. Validate Message Format

Always validate incoming messages to prevent crashes:

```java
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!channel.equals("myplugin:data")) {
        return;
    }

    if (message.length == 0) {
        plugin.getLogger().warning("Received empty plugin message");
        return;
    }

    try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
         DataInputStream in = new DataInputStream(stream)) {

        String action = in.readUTF();

        // Validate action
        if (!isValidAction(action)) {
            plugin.getLogger().warning("Invalid action: " + action);
            return;
        }

        processAction(action, in, player);

    } catch (IOException e) {
        plugin.getLogger().severe("Error reading plugin message: " + e.getMessage());
    }
}

private boolean isValidAction(String action) {
    return action.equals("UPDATE") || action.equals("DELETE") || action.equals("QUERY");
}
```

### 3. Handle Errors Gracefully

Wrap message processing in try-catch blocks:

```java
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    try {
        processMessage(channel, player, message);
    } catch (Exception e) {
        plugin.getLogger().severe("Error processing plugin message from " +
                                 player.getName() + ": " + e.getMessage());
        if (debug) {
            e.printStackTrace();
        }
    }
}
```

### 4. Use Separate Listeners for Different Channels

Don't handle multiple unrelated channels in one listener:

**Bad:**
```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class MultiChannelListener implements PluginMessageListener {
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            // ...
        } else if (channel.equals("myplugin:sync")) {
            // ...
        }
    }
}
```

**Good:**
```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class BungeeCordListener implements PluginMessageListener {
    // Handle BungeeCord messages
}

@IocBukkitMessageListener(channel = "myplugin:sync")
public class SyncListener implements PluginMessageListener {
    // Handle sync messages
}
```

### 5. Abstract Message Creation

Create reusable message builders:

```java
@IocBean
public class MessageBuilder {

    public byte[] buildUpdateMessage(String key, String value) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("UPDATE");
            out.writeUTF(key);
            out.writeUTF(value);

            return stream.toByteArray();
        }
    }

    public byte[] buildDeleteMessage(String key) throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("DELETE");
            out.writeUTF(key);

            return stream.toByteArray();
        }
    }
}
```

### 6. Use Configuration for Channel Names

Make channels configurable for flexibility:

```java
@IocBukkitMessageListener(channel = "myplugin:sync")
public class ConfigurableListener implements PluginMessageListener {

    @ConfigProperty("messaging.channels.sync")
    private String channelName;

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Use channelName for validation
    }
}
```

**config.yml:**
```yaml
messaging:
  channels:
    sync: "myplugin:sync"
    events: "myplugin:events"
```

### 7. Implement Timeouts for Requests

When expecting responses, implement timeouts:

```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class TimedRequestListener implements PluginMessageListener {

    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final TubingBukkitPlugin plugin;

    public TimedRequestListener(TubingBukkitPlugin plugin) {
        this.plugin = plugin;

        // Cleanup expired requests every 30 seconds
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            this::cleanupExpiredRequests, 600L, 600L);
    }

    public void requestWithTimeout(Player player, String subchannel,
                                   Consumer<String> callback, long timeoutMs) {
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingRequest(callback,
                           System.currentTimeMillis() + timeoutMs));

        // Send request with requestId
    }

    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private static class PendingRequest {
        final Consumer<String> callback;
        final long expiresAt;

        PendingRequest(Consumer<String> callback, long expiresAt) {
            this.callback = callback;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Handle responses and invoke callbacks
    }
}
```

### 8. Consider Alternative Technologies

For complex cross-server communication, consider:

**Redis Pub/Sub:**
- Better performance for high-frequency messaging
- No player connection required
- Built-in support for patterns and channels

**Database-based messaging:**
- Persistent messages
- Works without proxy
- Good for asynchronous communication

**Direct socket communication:**
- Full control over protocol
- No proxy dependency
- Best for real-time features

Plugin messaging is best for:
- Simple server transfers
- Player-context data
- Integration with existing BungeeCord infrastructure

### 9. Document Your Protocol

Document your custom message format:

```java
/**
 * Custom message protocol for myplugin:sync channel
 *
 * Message format:
 * - String: action ("UPDATE", "DELETE", "QUERY")
 * - String: key
 * - String: value (for UPDATE only)
 * - Long: timestamp
 *
 * Example UPDATE message:
 * - "UPDATE"
 * - "player:123e4567-e89b-12d3-a456-426614174000"
 * - "{\"level\":50,\"coins\":1000}"
 * - 1615824000000
 */
@IocBukkitMessageListener(channel = "myplugin:sync")
public class DocumentedListener implements PluginMessageListener {
    // Implementation
}
```

### 10. Test Message Handling

Write tests for message processing logic:

```java
@Test
public void testMessageParsing() {
    SyncListener listener = new SyncListener(mockDataService);

    byte[] message = createTestMessage("UPDATE", "key1", "value1");
    Player mockPlayer = mock(Player.class);

    listener.onPluginMessageReceived("myplugin:sync", mockPlayer, message);

    verify(mockDataService).update("key1", "value1");
}

private byte[] createTestMessage(String action, String key, String value) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(stream)) {
        out.writeUTF(action);
        out.writeUTF(key);
        out.writeUTF(value);
        return stream.toByteArray();
    }
}
```

## Complete Example

Here's a complete example combining all concepts:

**MyPlugin.java:**
```java
package com.example.myplugin;

import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;

public class MyPlugin extends TubingBukkitPlugin {

    @Override
    protected void enable() {
        // Register outgoing channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "myplugin:sync");

        getLogger().info("Plugin messaging enabled");
    }

    @Override
    protected void disable() {
        getLogger().info("Plugin disabled");
    }
}
```

**BungeeCordService.java:**
```java
package com.example.myplugin.messaging;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.configuration.ConfigProperty;
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@IocBean
public class BungeeCordService {

    private final TubingBukkitPlugin plugin;

    @ConfigProperty("bungeecord.enabled")
    private boolean enabled;

    public BungeeCordService(TubingBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendToServer(Player player, String serverName) {
        if (!enabled) {
            player.sendMessage("BungeeCord integration is disabled");
            return;
        }

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().severe("Error sending player to server: " + e.getMessage());
        }
    }

    public void getPlayerCount(Player player, String serverName) {
        if (!enabled) return;

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            out.writeUTF("PlayerCount");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());

        } catch (IOException e) {
            plugin.getLogger().severe("Error requesting player count: " + e.getMessage());
        }
    }
}
```

**BungeeCordListener.java:**
```java
package com.example.myplugin.messaging;

import be.garagepoort.mcioc.tubingbukkit.annotations.IocBukkitMessageListener;
import com.example.myplugin.services.ServerDataService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

@IocBukkitMessageListener(
    channel = "BungeeCord",
    conditionalOnProperty = "bungeecord.enabled"
)
public class BungeeCordListener implements PluginMessageListener {

    private final ServerDataService serverData;

    public BungeeCordListener(ServerDataService serverData) {
        this.serverData = serverData;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(message);
             DataInputStream in = new DataInputStream(stream)) {

            String subchannel = in.readUTF();

            switch (subchannel) {
                case "PlayerCount":
                    handlePlayerCount(in);
                    break;
                case "GetServer":
                    handleGetServer(in, player);
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePlayerCount(DataInputStream in) throws IOException {
        String server = in.readUTF();
        int count = in.readInt();
        serverData.updatePlayerCount(server, count);
    }

    private void handleGetServer(DataInputStream in, Player player) throws IOException {
        String server = in.readUTF();
        serverData.setPlayerServer(player.getUniqueId(), server);
    }
}
```

**ServerDataService.java:**
```java
package com.example.myplugin.services;

import be.garagepoort.mcioc.IocBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@IocBean
public class ServerDataService {

    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerServers = new ConcurrentHashMap<>();

    public void updatePlayerCount(String server, int count) {
        playerCounts.put(server, count);
    }

    public int getPlayerCount(String server) {
        return playerCounts.getOrDefault(server, 0);
    }

    public void setPlayerServer(UUID playerId, String server) {
        playerServers.put(playerId, server);
    }

    public String getPlayerServer(UUID playerId) {
        return playerServers.get(playerId);
    }
}
```

**config.yml:**
```yaml
bungeecord:
  enabled: true

messaging:
  debug: false
  channels:
    sync: "myplugin:sync"
```

## Troubleshooting

### Listener Not Registered

**Problem:** Message listener doesn't receive messages.

**Solutions:**
1. Check that the channel is specified correctly
2. Verify the class implements `PluginMessageListener`
3. Check `conditionalOnProperty` if used
4. Ensure the plugin is loading properly
5. Verify BungeeCord mode is enabled (for BungeeCord channels)

### Messages Not Sending

**Problem:** `sendPluginMessage()` doesn't work.

**Solutions:**
1. Register the outgoing channel in `enable()`
2. Ensure a player is online when sending
3. Verify the channel name matches exactly
4. Check server logs for errors
5. Confirm BungeeCord/Velocity is configured correctly

### IOException When Reading Messages

**Problem:** `IOException` when reading message data.

**Solutions:**
1. Validate message format matches expected structure
2. Check for buffer underflow (reading more data than sent)
3. Add try-catch around all `DataInputStream` operations
4. Log message length for debugging: `plugin.getLogger().info("Message length: " + message.length)`

### Circular Dependency with Plugin

**Problem:** Can't inject `TubingBukkitPlugin` into listener.

**Solution:** Use field injection instead of constructor injection:

```java
@IocBukkitMessageListener(channel = "BungeeCord")
public class MyListener implements PluginMessageListener {

    @InjectTubingPlugin
    private TubingBukkitPlugin plugin;

    // Don't inject plugin in constructor
    public MyListener(OtherService service) {
        // ...
    }
}
```

## Next Steps

Now that you understand plugin messaging with Tubing:

- [Event Listeners](Event-Listeners.md) - Handle Bukkit events with automatic registration
- [Commands](Commands.md) - Create command handlers with dependency injection
- [Bukkit Setup](Bukkit-Setup.md) - Learn about plugin lifecycle and initialization
- [Configuration Injection](../core/Configuration-Injection.md) - Inject configuration into listeners

---

**See also:**
- [Dependency Injection](../core/Dependency-Injection.md) - Master dependency injection patterns
- [Multi-Implementation](../core/Multi-Implementation.md) - Work with multiple message handlers
- [Conditional Beans](../core/Conditional-Beans.md) - Conditional listener registration
