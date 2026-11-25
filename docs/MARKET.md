# Mercado

App de serie del CariPhone (cofre morado). Su función es dar uso continuo a los Carins.

## Funcionamiento
- Cada día (UTC) salen `market.offersPerDay` ofertas (por defecto **8**), sorteadas por peso de entre todas las posibles. Son iguales para todo el servidor y cambian a medianoche UTC.
- Cada oferta tiene un **stock diario por dúo** (1–3 compras).
- El servidor cobra, entrega el objeto (si no cabe, cae a los pies) y lo anota en la Cartera. Si la entrega falla, devuelve los Carins.

## Ofertas por defecto
33 objetos **vanilla** útiles en COBBLEVERSE. Varios no se pueden conseguir de otra forma porque el modpack elimina sus mobs (cuero, plumas, tinta, panales, perlas de ender, varas de blaze…).

| Rango | Ejemplos | Precio |
|---|---|---|
| Común | Carbón ×16, hierro ×8, cobre ×16, redstone ×16, lapislázuli ×16, huesos ×16 | 20–35 |
| Medio | Diamante, esmeraldas ×4, perlas de ender ×4, varas de blaze ×2, frascos de experiencia ×8, etiqueta, montura | 40–80 |
| Raro | Caparazones de shulker ×2, corazón del mar, fragmento de netherita, tótem, manzana de Notch | 300–600 |

## Objetos de Cobblemon (desactivados)
Con `cobbleverse.enabled` el Mercado tiene una segunda pestaña, **Cobbleverse** (regalo misterioso, tienda y venta de Pokémon en CobbleDollars; ver `COBBLEVERSE.md`), y además incluye las ofertas de Cobblemon de aquí abajo.

`market.includeCobblemonItems` (por defecto `false`) añade 13 ofertas de Cobblemon: Poké/Super/Ultra Balls, caramelos raros y de experiencia, pociones, revivir, piedras evolutivas y cápsula de habilidad. Están desactivadas para no competir con las tiendas de CobbleDollars hasta decidirlo. Solo se activan si Cobblemon está cargado.

## Personalizar
`config/caricraft_market.json` sustituye la lista por defecto (mismo formato que `caricraft/default_market.json` dentro del JAR):

```json
{ "offers": [
  { "id": "iron", "item": "minecraft:iron_ingot", "count": 8, "price": 30, "dailyStock": 3, "weight": 10, "source": "vanilla" }
] }
```

`/caricraft reload` aplica los cambios. Las ofertas de objetos que no existen en el servidor se ignoran.
