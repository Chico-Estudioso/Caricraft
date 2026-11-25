# Testing

## Automáticas (`./gradlew test`)
- Economía: saldo, débito insuficiente, idempotencia, ledger acotado.
- Tienda: compra, requisitos, app ya comprada, cosmético personal, artículos "próximamente".
- Misiones: ciclo de estados, contribución de ambos, reclamación idempotente, caducidad diaria/semanal, entregas.
- Grupos: invitación, caducidad, aceptación, fusión, desvinculación con confirmación, reparto y enfriamiento.
- Persistencia: ida y vuelta NBT del estado completo y migración de esquema.
- Red: validación de acciones (longitudes, acciones desconocidas).

## Integración
Carga Fabric en servidor dedicado (con y sin Cobblemon), persistencia tras reinicio, dos clientes, desconexión durante transacción, paquetes repetidos.

## Manual
GUI scale, jugador sin dúo, cambios de configuración, ausencia de Cobblemon y logs.

## QA visual automatizada (solo desarrollo)
El source set `devtools` contiene `QaAutomation`, un mod de desarrollo que **no se incluye en el JAR**. Juega un guion completo contra un servidor dedicado y guarda capturas del CariPhone en `run-qa-a/screenshots` y `run-qa-b/screenshots`.

1. Preparar `run/` con `eula=true`, `online-mode=false` y `ops.json` para `Dev1` y `Dev2` (UUID offline).
2. `./gradlew runServer`
3. En otra terminal: `./gradlew runQaClientA`. En una tercera: `./gradlew runQaClientB`.

En una máquina sin pantalla se ejecutan con `xvfb-run` y `LIBGL_ALWAYS_SOFTWARE=1`.

Guion A: inicio en solitario, app bloqueada, aceptar misiones, viajar con `/tp` en saltos de 55 bloques (cuenta distancia y biomas), comprar apps, escribir una nota, invitar a Dev2, misiones de dúo, entregar ítems, reclamar, recorrer todas las apps, comprar y aplicar temas, y pedir la desvinculación.
Guion B: recibir y aceptar la invitación y viajar.

### Guiones adicionales
- `./gradlew runQaChallenge`: Desafío Dúo. Comprueba que los mobs no aparecen fuera del desafío, que salen equipados durante él, las recompensas y que desaparecen al amanecer.
- `./gradlew runQaNewApps` + `./gradlew runQaNewAppsPartner`: Mercado, CariCamp y CariLens con dos jugadores. Cubre compra y entrega, fijar campamento, brújula, viaje al campamento y al compañero, foto, álbum del compañero y viajes bloqueados en noche de desafío.

Para que el guion de apps sea rápido, en `run/config/caricraft.json` conviene poner `camp.cooldownSeconds = 5` y `lens.uploadCooldownSeconds = 0`.
