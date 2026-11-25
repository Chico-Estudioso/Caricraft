# Desafío Dúo

Noches de desafío con mobs vanilla equipados. Es una app de serie del CariPhone (calavera roja).

## Reglas
- Cada `intervalNights` noches (por defecto **3**) hay un **desafío obligatorio**.
- El dúo puede **adelantarlo** a la noche actual desde la app. Cuenta como el obligatorio y reinicia la cuenta.
- Durante la noche de desafío:
  - **No se puede dormir** (evento `ALLOW_SLEEPING` de Fabric). Cubre las camas; los sacos de Comforts también, si usan el sueño vanilla (sin probar).
  - Aparecen los mobs de `challenge.mobs` (por defecto zombi, momia, ahogado, esqueleto, esqueleto glacial, araña, araña de cueva, bruja, slime y enderman) **equipados y reforzados**.
  - Cada mob derrotado da `killReward + nivel - 1` Carins (por defecto 2–6).
  - Los viajes de CariCamp están bloqueados.
- **Al amanecer**:
  - Si el dúo sigue conectado, gana `nightReward` (por defecto 50) y la noche cuenta como superada.
  - Los mobs del desafío desaparecen.
- Fuera del desafío **no aparecen** esos mobs: el mundo queda como siempre en COBBLEVERSE.

## Niveles
El nivel sube cada `nightsPerLevel` (2) noches superadas, hasta `maxLevel` (5).

| Nivel | Equipamiento | Refuerzos |
|---|---|---|
| 1 | Cota de malla, espada de piedra | +15 % vida, +10 % daño, +50 % alcance de detección, +3 % velocidad |
| 2 | Cota de malla, espada de hierro, arcos con Poder I | Arañas con Velocidad |
| 3 | Hierro, hacha de hierro, Poder II | +45 % vida, +30 % daño |
| 4 | Hierro con Protección I, Filo I, Poder III | Arañas con Fuerza |
| 5 | Hierro y diamante con Protección II, espada de diamante con Filo II, Poder IV + Retroceso | +75 % vida, +50 % daño |

La probabilidad de cada pieza de armadura es 25 % + 15 % por nivel. El equipamiento solo cae al morir un 4 % de las veces.

## Misiones de caza
`first_challenge`, `night_hunter`, `duo_survivors`, `duo_trophies`, `elite_hunters`, `daily_hunt`, `weekly_hunt`, `milestone_hunter` y `milestone_nights` (ver `default_missions.json`). Usan:
- `KILL_ENTITY` con el objetivo `challenge` (solo cuentan los mobs del desafío).
- `CHALLENGE_NIGHT` (sobrevivir hasta el amanecer).

Si `challenge.enabled` es `false`, la app y estas misiones desaparecen.

## Preparar un servidor COBBLEVERSE
COBBLEVERSE bloquea estos mobs con **MobsBeGone**, que los borra al entrar al mundo (`config/mobsbegone-blacklist.json`). Hay que sacarlos de esa lista **una vez**:

1. En el servidor, como operador (nivel 3) o desde la consola: `/caricraft desafio preparar`. Quita de la lista los mobs de `challenge.mobs` y guarda una copia en `mobsbegone-blacklist.json.caricraft.bak`.
2. Reiniciar el servidor.
3. Comprobar con `/caricraft desafio estado`. Si algo sigue bloqueado, el log y la app lo avisan.

Para hacerlo a mano, basta con borrar esas entradas del JSON.

## Cómo funciona por dentro
- `ChallengeService` (núcleo, con tests) lleva la cuenta de noches.
  - El índice de día cambia **al amanecer** (tick 23000), así que cada noche pertenece al día de la tarde anterior.
  - El estado solo avanza mientras algún miembro está conectado.
- El mixin `ServerLevelChallengeMixin` se engancha en `ServerLevel.addFreshEntity`, el mismo punto que usa MobsBeGone.
  - Si no hay un dúo en desafío a menos de `radius` bloques, impide la aparición. Esto cubre aparición natural, spawners, conversiones y comandos.
  - Si lo hay, etiqueta el mob (`caricraft_challenge`) y lo equipa.
- Al cargar chunks se retiran los mobs etiquetados que quedaron de noches pasadas.
- Comandos de administración: `/caricraft desafio iniciar|terminar` (nivel 2).
