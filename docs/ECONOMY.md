# Carins

Moneda interna sin valor real, propia de Caricraft Mod. Independiente de CobbleDollars (ver ADR-004). La pestaña opcional Cobbleverse del Mercado usa CobbleDollars, no Carins (ver `COBBLEVERSE.md` y ADR-006). Su regalo misterioso puede dar Carins.

## Modelo
Saldo compartido por grupo (dúo o jugador sin vincular). Ledger con los últimos asientos (por defecto 100) visible en la app Cartera.

## Fuentes
- Recompensas de misiones (al reclamarlas).
- Descubrir un bioma nuevo con la app Exploración comprada (por defecto 5 Carins).
- Desafío Dúo: 2–6 Carins por mob derrotado durante la noche de desafío y 50 al sobrevivirla (`docs/CHALLENGE.md`).
- Racha diaria, eventos semanales y medallas (`docs/ROUTINE.md`), y jefes del desafío.
- Intereses de la **Hucha del dúo** (ver abajo).
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
| TP Dúo | App del grupo | 300 |
| CariCamp | App del grupo | 400 |
| CariBox | App del grupo | 400 |
| Ampliación de CariBox (×3) | Mejora del grupo | 250 cada una |
| Hucha | App del grupo | 300 |
| Centro rápido (×2) | Mejora del CobbleCentro | 300 cada una |
| Más expediciones (×2) | Mejora del CobbleCentro | 400 cada una |
| Radar variocolor (×2) | Mejora del CobbleCentro | 250 cada una |
| Entrenador personal | Uso (Cobbleverse) | 10 + nivel del Pokémon por cada nivel, 5 niveles al día |
| Viajes | Uso | 30 (campamento, CariCamp) / 50 (compañero con TP Dúo, con aceptación estilo /tpa) |
| Mercado | Objetos | 20–600 por oferta, stock diario |

Todos los precios y recompensas se pueden cambiar en `config/caricraft.json`.

## Hucha del dúo
App de pago (300) en la sección Día a día del CariPhone.
- Se meten y se sacan Carins del monedero cuando se quiera (10, 50, 100, 500 o todo).
- Cada día crece un **2 %** (`savings.dailyRate`) de lo que hubo en ella **todo el día**, es decir, de su saldo más bajo ese día. Meter justo antes de medianoche no da nada hasta el día siguiente, y sacar a media tarde baja lo que se gana. Como mucho **40 Carins al día** (`dailyCap`) y hasta **100.000** dentro (`maxBalance`).
- Los intereses se quedan dentro de la hucha (crecen solos) y avisan a los dos. Si nadie se conecta en varios días, se calculan al volver (hasta 30 días).
- Al formar un dúo se juntan las huchas; al separarse, quien se va se lleva la mitad.

## Reglas
Saldo no negativo; transacciones atómicas en el hilo del servidor; ledger; idempotencia de créditos; precios siempre leídos en el servidor; no se cobra una app ya comprada.
