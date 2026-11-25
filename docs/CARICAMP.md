# CariCamp

App de pago del dúo (400 Carins, hoguera naranja). Da un campamento propio del dúo y el viaje de vuelta a él. Los viajes junto al compañero están en la app [TP Dúo](TPDUO.md).

## Funciones
- **Campamento:** junto a una **hoguera encendida** (a 3 bloques como mucho), «Fijar campamento aquí» la guarda como campamento del dúo. Solo hay uno; volver a fijarlo lo mueve.
- **Brújula:** la app muestra una flecha y la distancia (con punto cardinal) al campamento. La flecha tiene en cuenta hacia dónde mira el jugador. Si el campamento está en otra dimensión, se indica.
- **Viaje al campamento:** `camp.campTravelCost` (30 Carins).
  - No necesita aceptación, pero hay una cuenta atrás de `camp.warmupSeconds` (3 s) sin moverse, que se ve en la app.
  - Se cobra al llegar. Después hay `camp.cooldownSeconds` (5 min) de espera, compartida con TP Dúo.
- **Durante la noche de desafío no se puede viajar** (`camp.blockDuringChallenge`).
- **Reglas de la hoguera:** el viaje busca un hueco seguro (suelo firme y dos bloques de aire) junto a ella.
  - Si se ha **apagado**, no se puede viajar hasta volver a encenderla (no se cobra).
  - Si se ha **roto**, el campamento se borra.
- La app tiene un acceso directo a TP Dúo (o a su ficha de compra si aún no se tiene).
- La posición del campamento solo se envía a los miembros del propio dúo.

## Comandos
| Comando | Qué hace |
|---|---|
| `/tpduo campamento` | Viaja al campamento |

`/caricraft viaje campamento` funciona igual.

## En qué se diferencia de Waystones
Waystones es una red de piedras fijas repartidas por el mundo, con viaje inmediato y pagado con experiencia. CariCamp da **un único campamento propio del dúo**, que se mueve con la hoguera, se paga con Carins, tiene cuenta atrás y está bloqueado en noche de desafío. Para viajar junto al compañero, ver [TP Dúo](TPDUO.md#en-qué-se-diferencia-de-waystones).
