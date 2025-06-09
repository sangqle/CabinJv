---
id: installation
title: Installation
---

# Installation Guide for CabinJ
CabinJ is a lightweight HTTP server framework built with Java NIO. This guide will help you set up CabinJ in your Java project.

## Prerequisites
- Java Development Kit (JDK) 17 or higher
- A build tool like Maven or Gradle
- An IDE (like IntelliJ IDEA or Eclipse) for Java development
- Basic knowledge of Java programming


## Install CabinJ
You can include CabinJ in your project using Maven or Gradle.

### Using jar File

Download the latest CabinJ jar file from the [releases page](https://github.com/CabinJV/CabinJv/releases), and add it to your project's classpath.

```gradle
// In your build.gradle file
implementation(files('libs/cabin-v12.0.jar'))
```

### Building from Source
If you prefer to build CabinJ from source, clone the repository and run the build command.

```bash
git clone https://github.com/CabinJV/CabinJv/tree/master
cd CabinJv
./gradlew build
```
This will generate the CabinJ jar file in the `build/libs` directory. And you can add it to your project as shown above.