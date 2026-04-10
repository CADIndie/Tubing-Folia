# Installation

This guide walks you through adding Tubing to your Minecraft plugin project using Maven.

## Prerequisites

- Java 8 or higher
- Maven or Gradle build system
- Basic understanding of Maven dependencies (this guide uses Maven)

## Maven Setup

### 1. Add the Repository

Add the StaffPlusPlus Maven repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>staffplusplus-repo</id>
        <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
    </repository>
</repositories>
```

### 2. Add Tubing Dependencies

The dependency you need depends on your platform:

#### Bukkit

```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### BungeeCord

```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bungee</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### Velocity

```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-velocity</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### Bukkit with GUI Module

If you want to use the GUI framework (Bukkit only), add the GUI dependency:

```xml
<dependency>
    <groupId>be.garagepoort.mcioc</groupId>
    <artifactId>tubing-bukkit-gui</artifactId>
    <version>7.5.6</version>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Note:** The exclusions prevent transitive dependencies from being included. Tubing will be shaded into your plugin.

### 3. Configure Maven Shade Plugin

You **must** relocate the Tubing package to avoid conflicts with other plugins using Tubing. Add the Maven Shade Plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <relocations>
                            <relocation>
                                <pattern>be.garagepoort.mcioc.</pattern>
                                <shadedPattern>com.yourplugin.tubing.</shadedPattern>
                            </relocation>
                        </relocations>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Important:** Replace `com.yourplugin.tubing` with your own package namespace.

## Complete Example pom.xml

Here's a complete example for a Bukkit plugin with the GUI module:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>myplugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <!-- Spigot repository -->
        <repository>
            <id>spigot-repo</id>
            <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
        </repository>
        <!-- Tubing repository -->
        <repository>
            <id>staffplusplus-repo</id>
            <url>https://nexus.staffplusplus.org/repository/staffplusplus/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Spigot API -->
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.20.1-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

        <!-- Tubing Bukkit -->
        <dependency>
            <groupId>be.garagepoort.mcioc</groupId>
            <artifactId>tubing-bukkit</artifactId>
            <version>7.5.6</version>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <!-- Tubing GUI (optional) -->
        <dependency>
            <groupId>be.garagepoort.mcioc</groupId>
            <artifactId>tubing-bukkit-gui</artifactId>
            <version>7.5.6</version>
            <exclusions>
                <exclusion>
                    <groupId>*</groupId>
                    <artifactId>*</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Shade Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <relocations>
                                <relocation>
                                    <pattern>be.garagepoort.mcioc.</pattern>
                                    <shadedPattern>com.example.myplugin.tubing.</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Gradle Setup (Alternative)

If you're using Gradle instead of Maven:

```groovy
repositories {
    maven {
        url 'https://nexus.staffplusplus.org/repository/staffplusplus/'
    }
}

dependencies {
    implementation('be.garagepoort.mcioc:tubing-bukkit:7.5.6') {
        transitive = false
    }
}

shadowJar {
    relocate 'be.garagepoort.mcioc', 'com.yourplugin.tubing'
}
```

## Version Compatibility

| Tubing Version | Minecraft Version | Java Version |
|----------------|-------------------|--------------|
| 7.5.6          | 1.8 - 1.20.x      | Java 8+      |
| 7.x.x          | 1.8 - 1.20.x      | Java 8+      |

Tubing is designed to be compatible with a wide range of Minecraft versions. The platform-specific dependencies (Bukkit, BungeeCord, Velocity) handle version differences.

## Verifying Installation

After adding the dependencies and configuring shading, compile your project:

```bash
mvn clean package
```

If successful, you should see the shaded JAR in your `target/` directory. The Tubing classes should be relocated to your custom package.

You can verify the relocation by:
1. Opening the JAR file with a ZIP viewer
2. Checking that `be/garagepoort/mcioc` is NOT present
3. Confirming that your relocated package (e.g., `com/example/myplugin/tubing`) contains the Tubing classes

## Common Issues

### Missing Dependencies

**Problem:** `ClassNotFoundException` or `NoClassDefFoundError` at runtime.

**Solution:** Ensure all exclusions are in place and that you're using the Maven Shade Plugin correctly. Tubing must be shaded into your plugin JAR.

### Relocation Not Working

**Problem:** Conflicts with other plugins using Tubing.

**Solution:** Double-check that your `<relocation>` configuration uses the correct `<pattern>` (must end with a dot: `be.garagepoort.mcioc.`) and a unique `<shadedPattern>`.

### Version Mismatch

**Problem:** Features documented here don't work.

**Solution:** Verify you're using the correct version. Check the version in your `pom.xml` matches the documentation version (7.5.6).

## Next Steps

Now that Tubing is installed, you're ready to create your first Tubing plugin:

**Next:** [Quick Start Guide](Quick-Start.md)

---

**See also:**
- [Project Structure](Project-Structure.md) - Organizing your Tubing project
- [Migration Guide](Migration-Guide.md) - Converting an existing plugin to Tubing
