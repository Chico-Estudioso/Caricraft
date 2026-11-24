# Auditoría del entorno (M0)

Fecha: 2026-11-24. Fuente principal: `modrinth.index.json` del archivo oficial `COBBLEVERSE 1.8.0.mrpack` (Modrinth, proyecto `cobbleverse`, versión `DxzQzMLR`, publicada el 2026-11-17).

Leyenda: **[H]** hecho comprobado · **[I]** inferencia · **[D]** duda abierta.

## Versiones base

| Componente | Versión | Estado |
|---|---|---|
| Modpack | COBBLEVERSE 1.8.0 | [H] `versionId` del índice |
| Minecraft | 1.21.1 | [H] `dependencies.minecraft` |
| Fabric Loader | 0.19.5 | [H] `dependencies.fabric-loader` |
| Fabric API | 0.116.18+1.21.1 | [H] `mods/fabric-api-0.116.18+1.21.1.jar` |
| Cobblemon | 1.8.4+1.21.1 | [H] `mods/Cobblemon-fabric-1.8.4+1.21.1.jar` |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 | [H] |
| Java | 21 | [H] `depends.java` de Cobblemon; Minecraft 1.21.1 exige Java 21 |

Nota sobre el aviso del kit ("Cobbleverse 1.8 ≠ Cobblemon 1.8"): en esta versión concreta **sí** coinciden. COBBLEVERSE 1.8.0 incluye Cobblemon 1.8.4. Aun así, las versiones se fijan por JAR comprobado y no por el nombre del modpack.

## Decisiones derivadas

- Caricraft Mod compila contra exactamente esas versiones (`gradle.properties`).
- Mappings: oficiales de Mojang (`loom.officialMojangMappings()`), los mismos que usa Cobblemon internamente.
- Cobblemon se usa solo como dependencia `modCompileOnly`; no se empaqueta.
- El mod es un JAR aparte: no se modifica ni redistribuye nada del modpack.

## Entorno cliente/servidor

- [H] COBBLEVERSE marca la mayoría de mods como `required` en ambos lados (cliente y servidor).
- [H] Caricraft Mod debe instalarse en **servidor y clientes** (tiene ítem, red y UI). `environment: "*"`.
- [I] Un servidor dedicado con COBBLEVERSE 1.8.0 + Caricraft Mod no necesita más dependencias que Fabric API.

## Mods relevantes del modpack (resumen)

Lista completa: 166 entradas en el índice. Los que afectan al diseño están analizados en `docs/MODPACK_FEATURE_AUDIT.md`:
CobbleDollars 2.0.0-Beta-5.1, cobblenav 2.4.2, Xaero's Minimap 26.6.0 y World Map 1.47.0, Waystones 21.1.46, Radical Cobblemon Trainers (rctmod 0.19.2, rctapi 0.16.2), CobbleverseBadges 1.3, Cobblemon Raid Dens 0.13.5, Comforts 9.0.5, Cobbreeding 2.4.1, Mega Showdown 1.3.3, CobbleCuisine 2.2.0, Sophisticated Backpacks/Storage, Tom's Storage, RoughlyEnoughItems, Ping Wheel, Trinkets/Accessories, owo-lib, Placeholder API.

## Persistencia y red (verificado en JAR de Minecraft 1.21.1 con mappings de Mojang)

- [H] `SavedData.save(CompoundTag, HolderLookup.Provider)` y `SavedData.Factory(Supplier, BiFunction<CompoundTag, HolderLookup.Provider, T>, DataFixTypes)`.
- [H] `DimensionDataStorage.computeIfAbsent(Factory, String)`.
- [H] `CustomPacketPayload.Type(ResourceLocation)` y `StreamCodec` para payloads.
- [H] Fabric API 0.116.18: `PayloadTypeRegistry.playC2S()/playS2C()` y `ServerPlayNetworking.PlayPayloadHandler`, cuyo javadoc garantiza ejecución **en el hilo del servidor**.
- [H] `ItemStack.onCraftedBy(Level, Player, int)` existe y se usa para el objetivo de fabricar.

## Prueba real con COBBLEVERSE 1.8.0 (servidor)

Se montó un servidor de producción (Fabric Server Launcher, sin entorno de desarrollo) con los 94 mods de servidor del índice, las carpetas `config/` y `datapacks/` del modpack y el JAR de Caricraft Mod. Script: `tools/cobbleverse_server_test.py`.

- [H] Arranca con **251 mods** (contando los anidados) y llega a `Done`.
- [H] `Caricraft Mod listo: … 26 misiones` e `Integración con Cobblemon 1.8.4+1.21.1 activa.`
- [H] `/caricraft reload` funciona desde consola.
- [H] **Java:** Cobblemon 1.8.4 exige exactamente Java 21. C2ME incluye un submódulo (`c2me-opts-natives-math`) que pide Java 25; con Java 21 se omite y el servidor arranca bien. Con Java 25, Cobblemon se niega a cargar. **El servidor debe usar Java 21.**
- [H] `athena` y `fusion` figuran como "solo cliente" en el índice, pero CobbleFurnies y Rechiseled los requieren también en el servidor. Un servidor montado a mano tiene que incluirlos.
- [H] La tecla por defecto del CariPhone (`O`) no está asignada en `config/defaultoptions/keybindings.txt` del modpack.
- [H] Cobblemon no arranca en el entorno de desarrollo de Loom (mappings de Mojang + reflexión de Kotlin: `ClassNotFoundException: net.minecraft.class_2960`). Las pruebas con Cobblemon se hacen en un servidor de producción.

## Dudas abiertas

- [D] Si el servidor real de Caricraft 2 añade mods fuera del modpack. Si es así, revisar conflictos de teclas y de red.
- [D] Si Cobbreeding dispara `HATCH_EGG_POST` de Cobblemon al eclosionar huevos (no se usa todavía).
- [D] No se ha probado un cliente con el modpack completo (requiere GPU); el cliente del CariPhone se probó con Minecraft + Fabric API.
