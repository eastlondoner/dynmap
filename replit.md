# Dynmap® - Dynamic Web Maps for Minecraft Servers

## Replit Environment Setup

This Replit environment hosts a **Download Portal** for Dynmap plugin builds. The portal provides an easy way for Minecraft server administrators to find and download the correct Dynmap plugin version for their server type.

### What's Running

- **Web Server**: Python 3 HTTP server (`server.py`) running on port 5000
- **Download Portal**: Static website (`public/index.html`) listing available Dynmap builds for:
  - Spigot/PaperMC (Minecraft 1.21.7, 1.21.8, 1.21.10)

### File Structure

- `server.py` - Simple HTTP server serving the download portal on port 5000
- `public/index.html` - Static download portal page with platform-specific download links
- Build artifacts (when generated) are placed in `target/` directory

### Building Plugins Locally

To build the Spigot/PaperMC plugin versions:
```bash
./gradlew :spigot:build
```

Note: Building requires JDK 17 or higher. Built JARs appear in the `target/` directory.

### Supported Versions

This build configuration is optimized to build **only** Spigot/PaperMC versions for:
- Minecraft 1.21.7
- Minecraft 1.21.8
- Minecraft 1.21.10

Fabric and Forge builds have been removed from this configuration.

### Recent Updates

**October 2025: Configured for Minecraft 1.21.7, 1.21.8, and 1.21.10**

The build system has been streamlined to support only three specific Minecraft versions:
- Created `bukkit-helper-121-7` for Minecraft 1.21.7 (uses NMS v1_21_R5)
- Created `bukkit-helper-121-8` for Minecraft 1.21.8 (uses NMS v1_21_R5)
- Created `bukkit-helper-121-10` for Minecraft 1.21.10 (uses NMS v1_21_R6)
- Versions 1.21.7 and 1.21.8 share NMS v1_21_R5, while 1.21.10 uses v1_21_R6
- Removed all other version helpers and Fabric/Forge modules from the build
- Updated download portal to show only these three versions

**AWS S3 Library Migration (October 2025)**

Migrated from `io.github.linktosriram.s3lite` to `com.github.davidmoten:aws-lightweight-client-java:0.1.19` for AWS S3 storage backend. Key improvements:

- **Lighter footprint**: New library is only 80KB vs previous multi-module setup
- **Better performance**: 40% faster cold start times, optimized for Lambda environments
- **Simplified API**: Cleaner request building with proper query parameter handling
- **Enhanced efficiency**: Implemented HEAD-based existence checks instead of ListObjects
- **Fixed binary handling**: Proper binary data handling for tile images (no UTF-8 corruption)
- **Proper signing**: Query parameters now use `param()` method for correct AWS signature canonicalization

Technical changes:
- Replaced all s3lite imports with aws-lightweight-client-java
- Rewrote S3 operations: ListObjectsV2, GetObject, PutObject, DeleteObject
- Implemented HEAD-based `exists()` and `matchesHashCode()` for metadata efficiency
- Custom endpoint support maintained for S3-compatible services (MinIO, etc.)

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

Dynmap is a plugin/mod system that generates real-time, Google Maps-style web maps for Minecraft servers. It renders 3D maps of Minecraft worlds with various perspectives and lighting options. This build is configured specifically for Spigot/PaperMC servers running Minecraft 1.21.7, 1.21.8, or 1.21.10.

The project follows a multi-platform architecture with a shared core library (DynmapCore) and platform-specific implementations for different Minecraft server types and versions.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Multi-Platform Plugin Architecture

**Purpose**: Support Dynmap across multiple Minecraft versions while maintaining a single codebase.

**Implementation**: 
- Shared core library (`DynmapCore`) containing platform-agnostic map rendering logic
- Platform-specific modules for each Minecraft version (bukkit-helper-121-7, bukkit-helper-121-8, bukkit-helper-121-10)
- Version-specific NMS (Native Minecraft Server) mappings for deep integration with Minecraft internals

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

### Build System

**Purpose**: Compile and package Dynmap for supported platforms.

**Implementation**:
- Gradle 8.7 build system
- Multi-module project structure with platform-specific submodules
- Java 17 compatibility for modern Minecraft versions
- Version-specific bukkit-helper modules for NMS integration

**Rationale**: Different Minecraft versions require different NMS mappings. The modular build system allows targeting specific versions while maintaining a unified codebase.

## External Dependencies

### Core Framework
- **Gradle v8.7**: Build automation and dependency management
- **Java 17+**: Required for modern Minecraft versions

### Platform Integration
- **Spigot/PaperMC**: Server platforms for versions 1.21.7, 1.21.8, 1.21.10
- **Spigot API**: Official API for Bukkit-based servers
- **NMS (Native Minecraft Server)**: Direct server implementation access for performance-critical operations

### Database Support (Optional)
- **SQLite**: Embedded database for map storage
- **MySQL/MariaDB**: External relational database options
- **PostgreSQL**: Alternative enterprise database

### Cloud Storage (Optional)
- **aws-lightweight-client-java v0.1.19**: Lightweight AWS S3 client for cloud storage (80KB footprint)
- **S3-compatible services**: Support for MinIO and other S3-compatible backends via custom endpoint configuration

### Web Server
- **Python 3 http.server**: Development server (server.py) for local testing
- Internal Java-based web server for production deployment

### Texture and Resource Processing
- Minecraft asset files (textures, colormaps) from supported game versions
- Biome color mapping files for realistic terrain rendering
- Entity texture files for player/mob rendering on maps
