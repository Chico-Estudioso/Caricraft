# Informe de balance (M9)

Valores por defecto (`missionRewardMultiplier = 1.0`) con Cobblemon.

## Ingresos
| Fuente | Carins |
|---|---|
| Historias individuales (10) | 670 por jugador → **1.340** el dúo |
| Historias cooperativas (7) | **1.550** |
| Hitos (4) | **1.500** a largo plazo (25 biomas, 50 km, 100 capturas, primer variocolor) |
| Diarias (3 por jugador) | hasta **170/día** el dúo |
| Semanales (2) | **350/semana** |
| Exploración | 5 por bioma nuevo con la app (COBBLEVERSE incluye Terralith: ~100+ biomas → ~500) |

## Gastos
| Artículo | Carins |
|---|---|
| Crónicas + Exploración + Estadísticas | 850 (una vez, para el dúo) |
| 3 temas × 2 jugadores | 900 |
| CariLens + CariCamp (cuando existan) | 800 |

## Ritmo estimado
- **Primera app (250):** primera sesión (~30–60 min). "Primeros pasos", "Leñador", "Artesano" y las primeras capturas bastan.
- **Las tres apps (850):** 2–3 sesiones de juego en dúo.
- **Todo el catálogo actual (1.750):** ~1 semana de juego normal.

## Riesgos
- **Inflación a largo plazo:** cuando se compra todo, diarias y semanales siguen dando ~1.500 Carins/semana sin nada en qué gastarlos. Opciones:
  1. Añadir sumideros (CariLens, CariCamp, más temas, cosméticos).
  2. Bajar `missionRewardMultiplier`.
  3. Desactivar diarias y semanales cuando no haya nada que comprar.
- **Exploits conocidos (bajo impacto, una sola vez):** volver a picar bloques colocados y fabricar/descomponer. Ver `QA_REPORT.md`.
- **Exploits cerrados:** PvP no cuenta, teletransportes no suman distancia, vincular/desvincular no genera Carins (reparto al 50 % y 24 h de espera), los biomas solo pagan la primera vez por grupo, las entregas retiran los ítems y las reclamaciones son idempotentes.

## Ajuste
Todo se configura en `config/caricraft.json` (`prices`, `missionRewardMultiplier`, `biomeDiscoveryReward`) y en `config/caricraft_missions.json` (recompensas por misión).
