# CariCamp

App de pago del dúo (500 Carins, hoguera naranja).

## Funciones
- **Campamento:** junto a una **hoguera encendida** (a 3 bloques como mucho), "Fijar campamento aquí" la guarda como campamento del dúo. Solo hay uno; volver a fijarlo lo mueve.
- **Brújula:** la app muestra una flecha y la distancia (con punto cardinal) al campamento y a cada compañero conectado. La flecha tiene en cuenta hacia dónde mira el jugador. Si el destino está en otra dimensión, se indica.
- **Viajes:**
  - Al campamento: `camp.campTravelCost` (30 Carins).
  - Junto al compañero: `camp.partnerTravelCost` (50 Carins).
  - Espera entre viajes de `camp.cooldownSeconds` (5 min) por jugador.
  - **Bloqueados durante la noche de desafío** (`camp.blockDuringChallenge`).
- **Reglas de la hoguera:** el viaje al campamento busca un hueco seguro (suelo firme y dos bloques de aire) junto a ella.
  - Si se ha **apagado**, no se puede viajar hasta volver a encenderla (no se cobra).
  - Si se ha **roto**, el campamento se borra.
- Las posiciones solo se envían a los miembros del propio dúo.

## Por qué no duplica Waystones
Waystones conecta piedras repartidas por el mundo y cuesta experiencia. CariCamp da un único punto propio del dúo y el viaje directo al compañero, pagados con Carins.
