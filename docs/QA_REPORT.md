# Informe de QA (M9)

Fecha: 2026-11-24 (ampliado el 2026-11-25 con las apps nuevas) · Versión: 0.1.0 + Unreleased

## Resumen
| Área | Resultado |
|---|---|
| Tests automáticos (`./gradlew test`) | 52 tests, 0 fallos |
| Servidor dedicado Fabric + 2 clientes reales (Xvfb) | Guion completo OK |
| Persistencia tras reinicio del servidor | OK |
| Servidor COBBLEVERSE 1.8.0 de producción (251 mods, Java 21) | Arranca; integración con Cobblemon activa; 26 misiones |
| Cliente con el modpack completo | Sin probar (sin GPU) |
| Eventos de Cobblemon con jugador real | Sin probar |

## 1. Tests automáticos
`./gradlew test` ejecuta 52 tests JUnit:
- **Economía:** créditos, débitos, saldo nunca negativo, límite máximo, ledger acotado, idempotencia, transferencias. Concurrencia: 200 débitos y 100 créditos idempotentes en paralelo.
- **Tienda:** compra compartida del dúo, sin doble cobro, saldo insuficiente, "próximamente", artículos de serie, cosméticos personales, precios por configuración.
- **Misiones:**
  - Ciclo completo y reclamación idempotente (doble clic).
  - Abandonar.
  - Progreso individual por miembro.
  - Cooperativas con aporte obligatorio de ambos.
  - Requisitos, tags y marcas (`#minecraft:logs`, `shiny`), hitos automáticos.
  - Diarias y semanales con caducidad y repetición; las completadas siguen reclamables tras el cambio de ciclo.
  - Entregas parciales y multiplicador de recompensa.
- **Dúo:**
  - Grupo individual al entrar.
  - Fusión de saldo, apps, biomas y crónicas.
  - Validación de invitaciones (a uno mismo, duplicadas, dúo lleno), caducidad, rechazar y cancelar.
  - Desvinculación con confirmación de 10 s, reparto al 50 % y enfriamiento.
  - Cambio de nombre.
- **Crónicas y exploración:** notas saneadas (códigos `§`, control, longitud), límite de entradas, bonificación de bioma solo con la app y solo una vez.
- **Persistencia:** ida y vuelta NBT completa a bytes, misiones reclamadas tras recargar, datos vacíos y de esquema futuro.
- **Red:** códecs de acción, aviso y snapshot. Índice de acción desconocido → ignorado. Argumentos demasiado largos → rechazados.
- **Límite de frecuencia** por jugador.

## 2. Partida real: servidor dedicado + 2 clientes
Herramienta: `runServer` + `runQaClientA` + `runQaClientB` (ver `docs/TESTING.md`).

| Paso | Resultado |
|---|---|
| Dev1 entra y recibe el CariPhone | OK (aviso emergente) |
| Inicio en solitario y app bloqueada | OK |
| Aceptar 4 misiones y viajar ~1,2 km con `/tp` de 55 bloques | OK: "Paseo diario" y "Primeros pasos" completadas; biomas detectados |
| Comprar Crónicas, Exploración y Estadísticas | OK: saldo descontado, crónicas registran la compra |
| Nota en Crónicas | OK |
| Dev1 invita a Dev2 → aviso → Dev2 acepta | OK: dúo formado, apps compartidas, saldo fusionado |
| Misión cooperativa "Expedición en dúo" con aportes de ambos | OK: se ven los aportes por jugador |
| Entregar 64 troncos + 16 lingotes ("Provisiones") | OK: se retiran del inventario y se completa |
| Reclamar misiones (incluidas no completadas) | OK: pagan una vez; las incompletas se rechazan |
| Cartera, Dúo, Crónicas, Exploración, Estadísticas | OK con datos reales |
| Comprar y aplicar temas Océano y Atardecer | OK; Bob no recibe el tema de Alice |
| Desvincular → confirmación | OK (aviso de confirmación) |
| Reinicio del servidor | OK: `1 grupos, 2 perfiles` cargados |

Capturas en `docs/img/cariphone.png`.

## 3. Servidor COBBLEVERSE 1.8.0 real
Ver `docs/ENVIRONMENT_AUDIT.md`. Arranca con 251 mods, `Integración con Cobblemon 1.8.4+1.21.1 activa`, 26 misiones y `/caricraft reload` correcto. Hace falta **Java 21**.

## 4. Apps nuevas (2026-11-25)
Tests automáticos: **81** (63 + 18 nuevos: desafío, mercado, campamento, álbum, PNG, mejoras por niveles, red).

**Desafío Dúo** (`runQaChallenge`, servidor dedicado):

| Paso | Resultado |
|---|---|
| `/summon zombie` fuera del desafío | 0 zombis: el filtro bloquea |
| Adelantar desafío → anochecer | Empieza; aparece el indicador en pantalla |
| Invocar zombi, esqueleto, momia y araña | Aparecen equipados (nivel 1: cota de malla y espada de piedra) |
| Derrotar 2 mobs | +2 Carins cada uno, contador actualizado |
| Amanecer | +50 Carins; los mobs desaparecen (0); noche superada y próximo desafío en 3 noches |
| `/summon skeleton` después | 0 esqueletos |

**Mercado, CariCamp y CariLens** (`runQaNewApps` + `runQaNewAppsPartner`, 2 clientes):

| Paso | Resultado |
|---|---|
| Comprar la primera oferta del Mercado | Montura entregada, Carins descontados |
| Comprar CariCamp, CariLens y una ampliación de álbum | OK; el álbum pasa de 12 a 24 |
| Fijar campamento junto a una hoguera encendida | OK |
| Brújula desde 300 bloques | Distancia y dirección correctas hacia campamento y compañero |
| Viajar al campamento | Llega a 1 bloque de la hoguera |
| Viajar junto al compañero | Llega a su posición |
| Foto con pie | PNG de 61 KB subido en 3 trozos; aparece en el álbum, en las Crónicas y **en el álbum del compañero** |
| Viajar durante la noche de desafío | Rechazado con aviso |

**CariPhone ordenado y TP Dúo** (`runQaNewApps` + `runQaNewAppsPartner`, 2 clientes):

| Paso | Resultado |
|---|---|
| Inicio sin apps compradas | Secciones Día a día / Dúo / Aventura / Sistema; las apps sin comprar, atenuadas y con precio |
| Tienda tras comprar 4 apps | Contador 4/7; primero las que se pueden comprar |
| Inicio → CariCamp → TP Dúo → atrás | Vuelve a CariCamp |
| Esc desde CariCamp | Vuelve al inicio; Esc en el inicio cierra |
| Tienda → ficha de Estadísticas → Tienda → botón lateral del ratón ×2 | Ficha de Estadísticas y luego Tienda |
| Tarjeta del compañero | Cara, bioma (Taiga), distancia y punto cardinal; la brújula apunta bien según hacia dónde se mira |
| Petición «Ir» | El compañero ve el aviso en el inicio, un globo «1» en TP Dúo y la tarjeta con barra de tiempo; al aceptar, llega a 0 bloques |
| Petición «Traer» | Cuenta atrás visible en TP Dúo; el compañero llega a 0 bloques |
| Noche de desafío | CariCamp y TP Dúo bloquean el viaje con aviso rojo; aviso «¡Desafío en curso!» en el inicio |

**CariPhone personal** (`runQaChallenge -DqaRole=P`, supervivencia):

| Paso | Resultado |
|---|---|
| Entrar | 1 CariPhone propio (serie 1), con la descripción «De Dev1 · Personal e intransferible» |
| Tecla de tirar con el teléfono en la mano | No cae; aviso «no se puede tirar»; 0 en el suelo |
| Tirarlo desde el inventario | Vuelve al inventario; 0 en el suelo |
| Cofre: mayúsculas+clic y colocarlo a mano | 0 en el cofre; se queda en el cursor y vuelve al inventario |
| `/give` uno sin dueño y otro de otro jugador | Se retiran los dos; sigue el mismo (serie 1) |
| `/clear` | 0 al momento; a los 3 s, uno nuevo con la serie 2 |
| Morir sin `keepInventory` | La tierra cae y el teléfono no; al reaparecer lo tiene (serie 2) y hay 0 en el suelo |
| Abrir el teléfono después | OK |

**Servidor COBBLEVERSE 1.8.0 real** (251 mods, Java 21):
- Avisa al arrancar de que MobsBeGone bloquea los 10 mobs del desafío.
- `/caricraft desafio preparar` (consola) los quita: la lista pasa de 84 a 74 entradas y se guarda copia `.caricraft.bak`. Tras reiniciar, sin aviso.
- `/summon zombie` fuera del desafío no deja ningún zombi: el filtro convive con el de MobsBeGone.
- 35 misiones y 33 ofertas de Mercado cargadas.
- **CariPhone personal** (exportando las clases con `-Dmixin.debug.export=true`):
  - Los tres mixins se aplican sin conflictos en `ServerPlayer`, `Player` y `Slot`.
  - En esos mismos métodos conviven Lenient Death (conservar objetos al morir), Sophisticated Core (tirar desde la mochila), Interactic (fuerza al tirar) y Accessories/Trinkets (soltar accesorios al morir). Todos usan inyecciones compatibles; ninguno reemplaza los métodos.
  - En un mundo creado con una versión anterior aparece una vez el aviso `Missing data pack caricraft`, porque ya no hay receta. Es inofensivo.

## Errores encontrados y corregidos durante la QA
1. **Teléfono borroso (alto):** en 1.21 `Screen.render` vuelve a pintar el fondo desenfocado encima. Corregido en `PhoneScreen`.
2. **Pestañas de misiones cortadas (bajo):** se quitaron los contadores y se añadió un punto rojo si hay misiones para reclamar.
3. **Nombres de apps cortados (bajo):** las etiquetas se encogen para caber.
4. **Servidor montado a mano sin `athena`/`fusion` (medio):** documentado; el script de prueba los incluye.
5. **Tarjeta del Desafío (bajo):** el título se cortaba y los indicadores de nivel se montaban sobre el texto en negrita. Corregido.
6. **Mercado (bajo):** el texto de stock quedaba tapado por el botón Comprar. Corregido.
7. **Etiquetas del inicio pegadas (bajo):** con 4 columnas, «Misiones», «Cartera» y «Mercado» se tocaban. Ahora dejan margen y algunas apps usan un nombre corto.

## Pendiente (priorizado)
1. **Medio:** probar captura, evolución y combate con un cliente COBBLEVERSE completo.
2. **Medio:** comprobar en COBBLEVERSE si los mobs del desafío dañan a los Pokémon sacados, y que los sacos de dormir de Comforts también quedan bloqueados en la noche de desafío.
3. **Bajo:** el bloqueo de dormir con una cama real no lo cubre el guion automático (lógica cubierta por tests).
4. **Bajo:** bloques colocados por el jugador cuentan al volver a picarlos (afecta a "Leñador" y "Mineros de élite"). Posible mejora: ignorar bloques colocados por jugadores.
5. **Bajo:** fabricar y descomponer (lingotes ↔ bloque) cuenta para "Artesano" (40 Carins, una vez).
6. **Bajo:** fabricar y picar no los cubre el guion automático (sí los tests del núcleo).
