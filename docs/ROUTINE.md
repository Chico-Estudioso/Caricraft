# Racha diaria, eventos semanales y Vitrina

Tres cosas para jugar un poco cada día y tener algo que enseñar. Todo lo decide el servidor: qué cuenta, cuándo se paga y cuánto. Los Carins van al monedero del dúo. Se configuran en el bloque `routine` de `config/caricraft.json`.

## Racha diaria
- **Un día cuenta cuando el dúo reclama al menos una misión** (cualquiera: diaria, de historia, semanal…). Da igual cuál de los dos la reclame.
- Cada día de racha paga Carins, más cuanto más larga es la semana de racha: **15, 20, 25, 30, 40, 50 y 100** el séptimo. Luego vuelve a empezar el ciclo (`streakRewardMultiplier`).
- **Bonus de dúo:** si los dos jugáis ese día (basta con entrar), el dúo cobra además la mitad del día. Se paga en cuanto se cumplen las dos cosas, en el orden que sea.
- **Protector de racha:** un día perdido por semana (de lunes a domingo) no la rompe. Dos seguidos sí, y se vuelve a empezar desde 1.
- Los días cambian a las 00:00 de `timeZone` (por defecto Europe/Madrid), como las misiones diarias.
- En el **Inicio** del CariPhone hay una tarjeta con los días de racha, el récord, los siete puntos de la semana de racha, lo que paga hoy y si falta el compañero para el bonus de dúo.
- Cada 7 días de racha queda apuntado en las Crónicas.

## Eventos semanales
Cada semana (de lunes a domingo) hay un evento para todo el servidor, en rotación. Tiene una **ventaja** y una **meta del dúo**:

| Evento | Ventaja (×1,5 por defecto) | Meta |
|---|---|---|
| Semana minera | Misiones de picar pagan más Carins | 1.500 bloques picados |
| Semana del desafío | Más experiencia del desafío | 150 mobs del desafío |
| Semana exploradora | Misiones de viajar y explorar; biomas nuevos ×2 | 25 km recorridos |
| Semana Pokémon (con Cobblemon) | Misiones Pokémon y caza del día | 40 capturas |
| Semana cazadora | Misiones de derrotar mobs | 250 mobs |
| Semana artesana | Misiones de fabricar | 600 objetos fabricados |

- La meta cuenta solo lo de esa semana. Paga **100 Carins a la mitad** y **300 al completarla** (`weeklyRewardMultiplier`).
- Los eventos que necesitan algo que no está (el desafío desactivado o Cobblemon sin instalar) se saltan.
- Tarjeta en el **Inicio** con el evento, su ventaja, la barra con la marca de la mitad y cuánto queda de semana.
- `weeklyBonus` cambia la ventaja (1,5 = un 50 % más).

## Vitrina
App gratuita del CariPhone (sección Dúo) con las **medallas** del dúo y los **trofeos** de los jefes del desafío.

- **16 medallas**, cada una con bronce, plata y oro, que pagan **25, 75 y 200 Carins** (`medalRewardMultiplier`). Se miden con las estadísticas del grupo, así que **cuenta lo que ya habíais hecho** (al instalar la actualización puede llegar más de una de golpe).

| Medalla | Bronce / plata / oro |
|---|---|
| Misiones | 10 / 50 / 200 reclamadas |
| Racha | récord de 7 / 30 / 100 días |
| Eventos | 1 / 5 / 20 eventos semanales completados |
| Noches | 5 / 25 / 100 noches de desafío superadas |
| Cazamobs | 100 / 1.000 / 5.000 mobs del desafío |
| Nivel | nivel del desafío 5 / 15 / 30 |
| Jefes | 1 / 3 / 6 jefes del desafío |
| Explorador | 10 / 30 / 60 biomas |
| Viajero | 10 / 100 / 500 km |
| Minero | 1.000 / 10.000 / 50.000 bloques |
| Cazador | 250 / 2.500 / 10.000 mobs |
| Artesano | 500 / 5.000 / 20.000 objetos |
| Fotógrafo | 10 / 50 / 150 fotos de CariLens |
| Entrenador (Cobblemon) | 50 / 250 / 1.000 capturas |
| Variocolor (Cobblemon) | 1 / 5 / 15 variocolores |
| Combates (Cobblemon) | 50 / 250 / 1.000 victorias |

- Las de desafío solo salen si está activado, y las de Cobblemon solo con Cobblemon.
- Al conseguir un tramo llega un aviso a los dos y queda en las Crónicas.
- Al formar un dúo se juntan las medallas y la racha (se queda la mejor); al separarse, cada uno se lleva una copia.

## Configuración (`routine`)
| Clave | Por defecto | Qué hace |
|---|---|---|
| `streak` | `true` | Racha diaria |
| `streakRewardMultiplier` | 1.0 | Multiplica los Carins de la racha |
| `weeklyEvents` | `true` | Eventos semanales |
| `weeklyRewardMultiplier` | 1.0 | Multiplica los premios de la meta |
| `weeklyBonus` | 1.5 | Fuerza de la ventaja del evento (1 a 5) |
| `medals` | `true` | Vitrina de medallas |
| `medalRewardMultiplier` | 1.0 | Multiplica los Carins de las medallas |

## Comandos (nivel 2)
- `/caricraft rutina`: racha, evento de la semana, medallas y jefes del grupo.
- `/caricraft rutina evento <cantidad>`: suma progreso a la meta del evento (para probar o compensar).
- `/caricraft rutina racha <días>`: fija la racha del grupo.
