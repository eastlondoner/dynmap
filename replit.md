# Dynmap® - Dynamic Web Maps for Minecraft Servers

## Replit Environment Setup

This Replit environment hosts a **Download Portal** for Dynmap plugin builds. The portal provides an easy way for Minecraft server administrators to find and download the correct Dynmap plugin version for their server type.

### What's Running

- **Web Server**: Python 3 HTTP server (`server.py`) running on port 5000
- **Download Portal**: Static website (`public/index.html`) listing available Dynmap builds for:
  - Spigot/PaperMC (Minecraft 1.10.2 - 1.21.4)
  - Fabric (Minecraft 1.14.4 - 1.21.6)
  - Forge (Minecraft 1.12.2 - 1.21.6)

### File Structure

- `server.py` - Simple HTTP server serving the download portal on port 5000
- `public/index.html` - Static download portal page with platform-specific download links
- Build artifacts (when generated) are placed in `target/` directory

### Building Plugins Locally

To build the latest plugin versions:
```bash
./gradlew :spigot:build :fabric-1.21.6:build :forge-1.21.6:build
```

Note: Building requires JDK 21 for latest versions. Built JARs appear in the `target/` directory.

### Recent Updates

**JAXB Fix for AWS S3 Storage (October 2025)**

Fixed a critical issue where Dynmap would throw `jakarta.xml.bind.JAXBException` when using the AWS S3 storage backend on Java 17/21. The complete fix includes:

- Added JAXB API: `jakarta.xml.bind-api:4.0.2`
- Added JAXB runtime: `org.glassfish.jaxb:jaxb-runtime:4.0.5`
- Added required transitive dependencies:
  - `jakarta.activation-api:2.1.3`
  - `com.sun.istack:istack-commons-runtime:4.1.2`
  - `org.glassfish.jaxb:txw2:4.0.5`
- Configured proper package relocation to `org.dynmap.shaded.*` to avoid classpath conflicts
- Enabled `mergeServiceFiles()` to preserve META-INF/services for ServiceLoader discovery

This ensures S3 tile read/write operations and zoom-out processing work correctly on modern Java versions.

## Overview

Dynmap is a plugin/mod system that generates real-time, Google Maps-style web maps for Minecraft servers. It renders 3D maps of Minecraft worlds with various perspectives and lighting options, supporting multiple server platforms including Spigot, Paper, and Fabric across different Minecraft versions (1.14.4 through 1.21.x).

The project follows a multi-platform architecture with a shared core library (DynmapCore) and platform-specific implementations for different Minecraft server types and versions.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Multi-Platform Plugin Architecture

**Purpose**: Support Dynmap across numerous Minecraft versions and server platforms while maintaining a single codebase.

**Implementation**: 
- Shared core library (`DynmapCore`) containing platform-agnostic map rendering logic
- Platform-specific modules for each Minecraft version (fabric-1.14.4, fabric-1.15.2, fabric-1.16.4, etc., and forge versions)
- Version-specific mixins for deep integration with Minecraft internals

**Rationale**: This architecture allows code reuse while accommodating breaking changes between Minecraft versions. Each platform module can hook into version-specific APIs without affecting the core rendering engine.

### Map Rendering System

**Purpose**: Generate map tiles from Minecraft world data with various perspectives and quality levels.

**Implementation**:
- Configurable rendering templates (vlowres, lowres, hires) with different pixel-per-block ratios
- Patch-based geometry system for complex block shapes (torches, plants, etc.)
- Multiple perspective modes (isometric views at different angles)
- Shader system supporting biome coloring, lighting effects, and texture packs

**Design Decisions**:
- Template-based configuration allows users to choose performance vs. quality tradeoffs
- Patch definitions separate geometry from rendering logic for maintainability
- Version-specific texture mappings handle Minecraft's evolving block system

### Storage Backend Abstraction

**Purpose**: Support multiple storage solutions for map tiles without changing core logic.

**Implementation**: Pluggable storage backends including:
- File tree (default) - standard directory structure
- SQLite - single database file
- MySQL/MariaDB - enterprise database support
- PostgreSQL - alternative relational database
- AWS S3 - cloud storage for web hosting

**Rationale**: Different server operators have different infrastructure needs. File tree works for simple setups, while databases handle high-traffic servers better. S3 integration enables CDN-backed map hosting.

**Tradeoffs**: Each backend has performance characteristics - file tree is simple but slower for large maps, SQLite can grow very large, databases require additional infrastructure.

### Web Interface Delivery

**Purpose**: Serve rendered maps to web browsers.

**Implementation**:
- Internal web server component for standalone operation
- Static file generation for external web servers
- Real-time player position updates via WebSocket-like protocol
- Web chat integration

**Design Choice**: Dual-mode operation (internal vs. external server) provides flexibility - internal server simplifies deployment, external server allows CDN integration and better performance at scale.

### Mixin-Based Platform Integration

**Purpose**: Deep integration with Minecraft internals without modifying game code.

**Implementation**:
- Fabric Mixin framework for bytecode manipulation
- Version-specific mixins targeting chunk loading, player management, and world events
- Accessor mixins for reading protected/private game data

**Rationale**: Mixins allow Dynmap to hook into Minecraft's internal events (chunk generation, player movement) without conflicts or version-specific bytecode patches. This is cleaner than reflection and more maintainable than ASM manipulation.

### Build System

**Purpose**: Compile and package Dynmap for all supported platforms.

**Implementation**:
- Gradle 8.7 for modern versions
- Separate "oldgradle" directory for legacy Forge 1.12.2 (requires JDK 8)
- Multi-module project structure with platform-specific submodules
- Version-specific Java compatibility (JDK 8 for old versions, up to JDK 21 for latest)

**Rationale**: Different Minecraft versions require different Java versions and build tools. The split build system accommodates these requirements while maintaining a unified codebase.

## External Dependencies

### Core Framework
- **Gradle v8.7**: Build automation and dependency management
- **Java 8-21**: Version-specific JDK requirements based on Minecraft version

### Platform Integration
- **Fabric Loader**: Mod loading framework for Fabric-based versions
- **Fabric API**: Standard API layer for Fabric mods
- **Fabric Mixin**: Bytecode manipulation framework for deep game integration

### Minecraft Compatibility
- **Spigot/PaperMC**: Server platforms for versions ≤1.21.4
- **Fabric**: Client/server mod platform for versions 1.14.4 through 1.21.x
- **Forge**: Server platform for legacy versions (1.12.2, 1.14.4, 1.15.2, 1.16.5)

### Database Support (Optional)
- **SQLite**: Embedded database for map storage
- **MySQL/MariaDB**: External relational database options
- **PostgreSQL**: Alternative enterprise database

### Cloud Storage (Optional)
- **AWS S3**: Cloud object storage for map tile hosting
- **S3-compatible services**: Alternative cloud storage backends

### Web Server
- **Python 3 http.server**: Development server (server.py) for local testing
- Internal Java-based web server for production deployment

### Texture and Resource Processing
- Minecraft asset files (textures, colormaps) from multiple game versions
- Biome color mapping files for realistic terrain rendering
- Entity texture files for player/mob rendering on maps