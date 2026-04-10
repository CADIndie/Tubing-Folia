# Tubing Framework Documentation

Welcome to the complete documentation for the Tubing framework (version 7.5.6).

## Documentation Organization

This documentation is organized into 8 main sections:

### 1. Getting Started
New to Tubing? Start here to learn the basics and set up your first project.

- [Installation](getting-started/Installation.md) - Maven setup and dependencies
- [Quick Start](getting-started/Quick-Start.md) - Build your first Tubing plugin
- [Project Structure](getting-started/Project-Structure.md) - Organize your code
- [Migration Guide](getting-started/Migration-Guide.md) - Convert existing plugins to Tubing

### 2. Core Framework
Deep dive into the IoC container and configuration management.

- [IoC Container](core/IoC-Container.md) - Container fundamentals
- [Dependency Injection](core/Dependency-Injection.md) - DI patterns and principles
- [Bean Lifecycle](core/Bean-Lifecycle.md) - How beans are created and managed
- [Bean Registration](core/Bean-Registration.md) - Using @IocBean
- [Bean Providers](core/Bean-Providers.md) - Factory methods with @IocBeanProvider
- [Multi-Implementation](core/Multi-Implementation.md) - Managing multiple implementations
- [Conditional Beans](core/Conditional-Beans.md) - Conditional registration
- [Configuration Injection](core/Configuration-Injection.md) - Inject config values
- [Configuration Files](core/Configuration-Files.md) - Multiple configs and migrations
- [Configuration Objects](core/Configuration-Objects.md) - Complex object mapping
- [Configuration Transformers](core/Configuration-Transformers.md) - Custom type conversion
- [Post-Initialization](core/Post-Initialization.md) - Post-load hooks

### 3. Platform-Specific

#### Bukkit
- [Bukkit Setup](bukkit/Bukkit-Setup.md) - Getting started with Bukkit
- [Commands](bukkit/Commands.md) - Command handling
- [Subcommands](bukkit/Subcommands.md) - Complex command trees
- [Event Listeners](bukkit/Event-Listeners.md) - Event handling
- [Plugin Messages](bukkit/Plugin-Messages.md) - Plugin messaging channels
- [Permissions](bukkit/Permissions.md) - Permission handling
- [Messaging](bukkit/Messaging.md) - Localization and color codes

#### BungeeCord
- [BungeeCord Setup](bungee/Bungee-Setup.md) - Getting started with BungeeCord
- [Commands](bungee/Commands.md) - Proxy commands
- [Listeners](bungee/Listeners.md) - Proxy event handling

#### Velocity
- [Velocity Setup](velocity/Velocity-Setup.md) - Getting started with Velocity
- [Commands](velocity/Commands.md) - Modern proxy commands
- [Listeners](velocity/Listeners.md) - Modern proxy events

### 4. GUI Framework (Bukkit)
Build sophisticated inventory GUIs with the MVC pattern.

- [GUI Setup](gui/GUI-Setup.md) - Getting started with GUIs
- [GUI Controllers](gui/GUI-Controllers.md) - MVC pattern with @GuiController
- [GUI Building](gui/GUI-Building.md) - TubingGui builder patterns
- [GUI Actions](gui/GUI-Actions.md) - Action routing and parameters
- [GUI Templates](gui/GUI-Templates.md) - Freemarker template engine
- [GUI Async](gui/GUI-Async.md) - Async operations with loading screens
- [GUI History](gui/GUI-History.md) - Navigation and back button
- [Chat Input](gui/Chat-Input.md) - Capturing chat input from GUIs
- [Exception Handling](gui/Exception-Handling.md) - Graceful error handling

### 5. Advanced Topics
Extend Tubing with custom functionality.

- [Custom Annotations](advanced/Custom-Annotations.md) - Create custom bean annotations
- [Custom Configuration Provider](advanced/Custom-Configuration-Provider.md) - Custom config loading
- [Manual Bean Registration](advanced/Manual-Bean-Registration.md) - Runtime bean registration
- [Reflection Utilities](advanced/Reflection-Utilities.md) - ReflectionUtils reference

### 6. Best Practices
Design patterns and architectural guidance.

- [Project Architecture](best-practices/Project-Architecture.md) - Layered architecture and DDD
- [Testing](best-practices/Testing.md) - Unit and integration testing
- [Performance](best-practices/Performance.md) - Optimization strategies
- [Common Patterns](best-practices/Common-Patterns.md) - Design patterns with Tubing

### 7. Troubleshooting
Solutions to common problems.

- [Common Errors](troubleshooting/Common-Errors.md) - Error messages and solutions
- [Debugging](troubleshooting/Debugging.md) - Debugging techniques
- [FAQ](troubleshooting/FAQ.md) - Frequently asked questions

### 8. Reference
Quick reference documentation.

- [Annotations](reference/Annotations.md) - Complete annotation reference
- [Core Classes](reference/Core-Classes.md) - Key class reference
- [Configuration Options](reference/Configuration-Options.md) - Configuration reference

## Quick Links

### For Beginners
1. [Installation](getting-started/Installation.md) - Set up your project
2. [Quick Start](getting-started/Quick-Start.md) - Build your first plugin
3. [IoC Container](core/IoC-Container.md) - Understand the container
4. [Dependency Injection](core/Dependency-Injection.md) - Learn DI patterns

### For Existing Users
- [Migration Guide](getting-started/Migration-Guide.md) - Convert your plugin
- [Best Practices](best-practices/Project-Architecture.md) - Architecture guidance
- [FAQ](troubleshooting/FAQ.md) - Common questions answered

### For Advanced Users
- [Custom Annotations](advanced/Custom-Annotations.md) - Extend the framework
- [Performance](best-practices/Performance.md) - Optimize your plugin
- [Reference](reference/Annotations.md) - API reference

## Documentation Stats

- **Total Pages**: 52
- **Sections**: 8
- **Code Examples**: 500+
- **Version**: 7.5.6

## Contributing

Found an error or want to improve the documentation? Please submit an issue or pull request to the [Tubing repository](https://github.com/garagepoort/Tubing).

## License

This documentation is part of the Tubing project and is available under the same license as the framework.

---

**Get Started:** [Installation Guide](getting-started/Installation.md)
