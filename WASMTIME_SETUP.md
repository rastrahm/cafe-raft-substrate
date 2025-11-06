# Cómo obtener Wasmtime-Java para WasmtimeWasmRuntime

## Opción 1: JitPack (si tienes acceso a internet)

Si tu red permite acceso a `https://jitpack.io`, añade en `raft-application-spring/build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // WASM runtime (Wasmtime-Java via JitPack)
    implementation("com.github.kawamuray.wasmtime:wasmtime-java:0.11.0")
}
```

Luego ejecuta:
```bash
./gradlew :raft-application-spring:build
```

## Opción 2: Descargar JAR manualmente

1. **Descargar desde JitPack**:
   - URL: `https://jitpack.io/com/github/kawamuray/wasmtime/wasmtime-java/0.11.0/wasmtime-java-0.11.0.jar`
   - O desde GitHub releases si está disponible

2. **Colocar el JAR en el proyecto**:
   ```bash
   mkdir -p raft-application-spring/libs
   # Copia el JAR descargado a:
   # raft-application-spring/libs/wasmtime-java-0.11.0.jar
   ```

3. **Añadir al classpath en `build.gradle.kts`**:
   ```kotlin
   dependencies {
       // WASM runtime (JAR local)
       implementation(files("libs/wasmtime-java-0.11.0.jar"))
   }
   ```

## Opción 3: Instalar en repositorio Maven local

Si descargaste el JAR manualmente:

```bash
mvn install:install-file \
  -Dfile=wasmtime-java-0.11.0.jar \
  -DgroupId=com.github.kawamuray.wasmtime \
  -DartifactId=wasmtime-java \
  -Dversion=0.11.0 \
  -Dpackaging=jar
```

Luego en `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.kawamuray.wasmtime:wasmtime-java:0.11.0")
}
```

## Verificar que funciona

Una vez añadido, `WasmtimeWasmRuntime` debería:
- Precompilar módulos WASM correctamente
- `WasmEngine` podrá ejecutar contratos WASM

## Nota sobre dependencias nativas

Wasmtime-Java puede requerir librerías nativas (`.so` en Linux, `.dylib` en macOS, `.dll` en Windows) que se descargan automáticamente en tiempo de ejecución. Asegúrate de tener acceso a internet en tiempo de ejecución o descarga las librerías nativas manualmente.

## Referencias

- Repositorio: https://github.com/kawamuray/wasmtime-java
- JitPack: https://jitpack.io/#kawamuray/wasmtime-java

