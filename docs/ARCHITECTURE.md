# Arquitectura

Estado: aprobada para el MVP (M1). Versiones en `docs/ENVIRONMENT_AUDIT.md`.

## Módulos (paquete `com.chicoestudioso.caricraft`)

| Paquete | Lado | Responsabilidad |
|---|---|---|
| `core` | común, **Java puro** | Modelo y reglas: grupos, Carins/ledger, catálogo de apps, tienda, misiones, crónicas, exploración. Sin clases de Minecraft → tests JUnit rápidos. |
| `config` | común | `config/caricraft.json` (precios, recompensas, límites). |
| `item` | común | Registro del ítem CariPhone. |
| `network` | común | Payloads tipados y DTO del snapshot. |
| `server` | servidor | `SavedData`, códec NBT, manejador de acciones, constructor de snapshots, seguimiento de progreso, comandos `/carins` y `/caricraft`. |
| `mixin` | común (efecto en servidor) | `ItemStack.onCraftedBy` para el objetivo de fabricar. |
| `integration.cobblemon` | servidor | Adaptador opcional; se carga solo si Cobblemon está presente. |
| `client` (source set `client`) | cliente | Pantalla del CariPhone, temas, caché del snapshot, tecla. |

## Flujo

```
Cliente (CariPhone)                       Servidor
  abrir teléfono ──REQUEST_SNAPSHOT──▶  PhoneActionHandler ─▶ SnapshotBuilder
  pulsar "Comprar" ─BUY_APP(id)──────▶  valida (rate limit, longitud, estado)
                                         └▶ CaricraftCore.shop.buy(...)
                                             └▶ EconomyService.debit(...) + ledger
  ◀── phone_notify("Compra realizada") ─┤
  ◀── phone_snapshot(JSON) ─────────────┘ (también al otro miembro conectado)
```

## Núcleo (`core`)

- `CaricraftState`: raíz persistente: grupos (`Party`), perfiles (`PlayerProfile`), índice jugador→grupo, `schemaVersion` (2: TP Dúo separado de CariCamp).
- `Party`: id, miembros, saldo, ledger acotado, claves de idempotencia, apps compradas, estados de misión, crónicas, biomas descubiertos, estadísticas.
- `PartyService`: invitar, aceptar, rechazar, cancelar, desvincular (con confirmación y enfriamiento). Fusión y reparto según ADR-004.
- `EconomyService`: `credit/debit` atómicos, saldo no negativo, idempotencia, ledger.
- `AppCatalog` + `ShopService`: definición de apps/cosméticos con precio, propiedad y requisitos; compra validada.
- `MissionCatalog` + `MissionService`: definiciones declarativas (JSON) y máquina de estados.
- `ProgressEvent`: evento neutral (tipo, objetivo, cantidad, jugador) que alimentan Minecraft y Cobblemon.
- `Clock`: abstracción de tiempo para tests y caducidad diaria/semanal.

## Concurrencia

Todas las mutaciones ocurren en el hilo del servidor (Fabric lo garantiza para los payloads). Los servicios no usan hilos propios.

## Seguridad

Enum de acciones, argumentos con longitud máxima, comprobación de pertenencia al grupo, límite de 20 acciones por segundo y jugador, y comandos de administración con nivel de permiso 2.

## Rendimiento

Bioma y distancia se muestrean cada 40 ticks por jugador. Sin escaneos de chunks. Snapshots solo bajo demanda o tras acciones.
