# Carins

Moneda interna sin valor real, propia de Caricraft Mod. Independiente de CobbleDollars (ver ADR-004).

## Modelo
Saldo compartido por grupo (dúo o jugador sin vincular). Ledger con los últimos asientos (por defecto 100) visible en la app Cartera.

## Fuentes
- Recompensas de misiones (al reclamarlas).
- Descubrir un bioma nuevo con la app Exploración comprada (por defecto 5 Carins).
- Desafío Dúo: 2–6 Carins por mob derrotado durante la noche de desafío y 50 al sobrevivirla (`docs/CHALLENGE.md`).
- Comandos de administración (`/carins give|take|set`).

## Sumideros
Apps del grupo, cosméticos personales y el **Mercado** (objetos por Carins con stock diario, ver `docs/MARKET.md`).

| Artículo | Tipo | Precio por defecto |
|---|---|---|
| Crónicas | App del grupo | 250 |
| Exploración | App del grupo | 400 |
| Estadísticas | App del grupo | 200 |
| Tema Atardecer / Océano / Bosque | Cosmético personal | 150 |
| CariLens | App del grupo | 300 |
| Ampliación de álbum (×4) | Mejora del grupo | 200 cada una |
| CariCamp | App del grupo | 500 |
| Viajes de CariCamp | Uso | 30 (campamento) / 50 (compañero) |
| Mercado | Objetos | 20–600 por oferta, stock diario |

Todos los precios y recompensas se pueden cambiar en `config/caricraft.json`.

## Reglas
Saldo no negativo; transacciones atómicas en el hilo del servidor; ledger; idempotencia de créditos; precios siempre leídos en el servidor; no se cobra una app ya comprada.
