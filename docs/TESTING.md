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
- `./gradlew runQaChallenge -DqaRole=I`: cómo se ve el ítem del CariPhone en primera y tercera persona y en el inventario.
- `./gradlew runQaChallenge -DqaRole=V`: aspecto de la pestaña Cobbleverse con datos de ejemplo. En desarrollo no hay Cobblemon.
- En un servidor COBBLEVERSE real: `/caricraft cobbleverse prueba` (desde la consola) comprueba Cobblemon y CobbleDollars con un jugador ficticio.
- **Con Cobblemon real** (modelos 3D, compras, ventas e intercambio prodigioso): `./gradlew build remapQaJar` y después `python3 tools/cobblemon_qa.py <servidor_cobbleverse> <carpeta_de_trabajo> W`.
  - `<servidor_cobbleverse>` es el que prepara `tools/cobbleverse_server_test.py`; de ahí se copian Cobblemon, CobbleDollars, Fabric API, Kotlin y Architectury.
  - Monta un servidor mínimo (mundo plano, puerto 25566) y un cliente de producción en español con el JAR de QA (`remapQaJar`, nunca se publica), y juega el rol W: pestaña Cobbleverse del Mercado, regalo, compra, venta, las cuatro pestañas del CobbleCentro y un intercambio prodigioso.
  - Imprime el registro `[QA]` y deja las capturas en `<carpeta_de_trabajo>/client/screenshots`. Sin pantalla usa `xvfb-run`.
  - Otros roles con el mismo comando:
    - `L`: cajas, tragaperras, mejoras de Pokémon y fusiones del CobbleCentro.
    - `G`: cofres del desafío (emboscada por oleadas, conquista y botín; Pokémon guardián que reaparece y se retira si te vas), limpieza de los mobs del desafío al amanecer (también en zonas descargadas), barra de experiencia y los dos regalos diarios.
    - `D`: bloque del dúo. Centro Pokémon de bolsillo (cobro, espera), caza del día (capturas simuladas con `/caricraft cobbleverse captura`), Pokédex del dúo (vitrina variocolor, legendario, metas), expedición (salida, recogida antes de tiempo rechazada, vuelta y botín), aviso de variocolor con brújula en la barra de acción y, copiando también RCT del servidor, la opción `levelCapSpawns` (que solo este rol enciende).
- `./gradlew runQaChallenge -DqaRole=S` (con `runServer`): escuadrón con botín. Comprueba la aparición, el aviso y la app, el cofre bloqueado, la derrota (Carins y botín) y que al amanecer el cofre abierto se queda y el no ganado desaparece.
- `./gradlew runQaChallenge -DqaRole=T` (con `runServer`): consejo en el chat, notificación con el teléfono cerrado, barra amarilla antes de la noche y roja durante el desafío, interruptor de consejos y aviso al amanecer.
- `./gradlew runQaChallenge -DqaRole=P` (con `runServer`): CariPhone personal. Comprueba que:
  - Hay uno vinculado al entrar.
  - La tecla de tirar y tirarlo desde el inventario no lo sueltan.
  - No entra en un cofre.
  - Un `/give` extra y uno ajeno se retiran.
  - Tras `/clear` vuelve con la serie siguiente.
  - Al morir no cae y vuelve al reaparecer.
- `./gradlew runQaNewApps` + `./gradlew runQaNewAppsPartner`: inicio por secciones, navegación con historial, Mercado, CariCamp, TP Dúo, CariLens y CariBox con dos jugadores. Cubre:
  - Compra y entrega.
  - Fijar campamento, brújula y viaje al campamento.
  - Ir/Traer con TP Dúo y avisos en el inicio.
  - Foto y álbum del compañero.
  - Viajes bloqueados en noche de desafío.

Para que el guion de apps sea rápido, en `run/config/caricraft.json` conviene poner `camp.cooldownSeconds = 5` y `lens.uploadCooldownSeconds = 0`.
