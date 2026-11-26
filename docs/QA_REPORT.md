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

**Cobbleverse** (servidor COBBLEVERSE 1.8.0 real, con `cobbleverse.enabled`, `/caricraft cobbleverse prueba` desde la consola):

| Comprobación | Resultado |
|---|---|
| Carga | «Cobbleverse: 24 ofertas y 16 premios»; Mercado de Carins con 46 ofertas (33 vanilla + 13 de Cobblemon); «CobbleDollars detectado» |
| Pokémon de tienda | `cobblemon:eevee` nv. 15, variocolor, IV 186/186 y Velocidad Extrema (sin avisos de movimientos desconocidos); se vendería por ₽31.240 |
| Quitar y devolver al equipo | 0 → 1 |
| CobbleDollars | 0 → +1000 → −1000 = 0; un cobro sin saldo se rechaza |
| Interfaz (`runQaChallenge -DqaRole=V`, datos de ejemplo) | Pestañas Mercado/Cobbleverse con punto rojo si hay regalo; saldo, regalo, ofertas con distintivos y movimientos, «Agotado hoy», «Faltan ₽…», venta con «¿Seguro?» y «Legendario» |

**Escuadrones con botín** (`runQaChallenge -DqaRole=S`):

| Paso | Resultado |
|---|---|
| Noche de desafío (13700) | Aparece un escuadrón de 3 (capitán «Capitán: Disecado») a 24–40 bloques con un cofre. Llega el aviso «a 25 m al Sur» y se ve la columna de llamas |
| App Desafío | «Botines de esta noche»: brújula, distancia y «Quedan 3 guardias» |
| Abrir el cofre con guardias vivos | No se abre; aviso «quedan 3 guardias» |
| Derrotar al escuadrón | «¡Escuadrón derrotado!», +45 Carins (nivel 1) y el cofre se abre con 8–12 objetos del botín de nivel 1 |
| Amanecer | El cofre abierto se queda |
| Otra noche sin derrotarlo, hasta el amanecer | El cofre desaparece, 0 guardias y 0 botines en el teléfono |

**Avisos, consejos y barra del desafío** (`runQaChallenge -DqaRole=T`):

| Paso | Resultado |
|---|---|
| Entrar | Llega un consejo «[CariPhone] …» por el chat; `/caricraft consejo` muestra otro |
| Adelantar el desafío y poner la hora a 11800 | Barra amarilla «El Desafío Dúo empieza en 1:00» |
| Anochece (13000) | Notificación con sonido «¡Empieza la noche de desafío!» y barra roja «Nivel 1 · Oleada 1/3 · Amanece en 8:20»; la tarjeta queda debajo de la barra |
| Medianoche (18000) | «Nivel 2 · Oleada 2/3 · Amanece en 4:10» |
| Ajustes → Consejos | Pasa a «Desactivados» y se guarda en el servidor |
| Amanece | Notificación «¡Habéis sobrevivido!…» y la barra desaparece |

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

**CobbleCentro e intercambio prodigioso con Cobblemon real** (`tools/cobblemon_qa.py`, rol W; cliente de producción en español con Cobblemon 1.8.4 y CobbleDollars):

| Paso | Resultado |
|---|---|
| Abrir el CobbleCentro desde el Mercado | Se abre; Inicio con los 6 Pokémon del equipo en 3D (los variocolor con el borde dorado) |
| Tienda | Tarjetas horizontales con el objeto en grande o el Pokémon en 3D, precio y «Comprar» |
| Vender | Tarjetas con el modelo, nivel, IV y «Vender · ₽…»; el segundo toque confirma |
| Prodigioso: elegir Pikachu Nv. 24 e intercambiar | Llega un Porygon Nv. 24 (luego un Pansage Nv. 23) de «un entrenador lejano»: en la bolsa solo estaba el Pikachu del propio jugador, que no se puede recibir |
| Animación | Poké Ball agitándose, destello y el Pokémon recibido girando; el aviso de texto no se adelanta a la animación |
| Otro intercambio sin quedar | «Ya has hecho hoy todos los intercambios prodigiosos» |

**Cajas, tragaperras, mejoras y fusiones con Cobblemon real** (`tools/cobblemon_qa.py`, rol L, dos pasadas):

| Paso | Resultado |
|---|---|
| Catálogo | 3 cajas (8, 8 y 6 premios), apuestas ₽1.000/5.000/20.000, retorno 91,5 %, 3 mejoras y 5 fusiones |
| Caja Maestra (₽90.000) | Ruleta y premio: una Master Ball (épico) en la primera pasada y ₽87.432 (común) en la segunda; el saldo baja y sube lo justo |
| Caja Poké (₽8.000) | 7 Poké Balls; 3 caramelos de exp. L |
| 16 tiradas | Pagos según la tabla: pierde, «recuperas ₽500/700» con dos iguales y ₽8.000 con tres Super Balls (×8) |
| Subida de nivel | Eevee Nv. 15 → 20 y Nv. 20 → 25 |
| Entrenamiento de IV | El IV más bajo a 31 (PS; Defensa especial): IV totales 112 → 139 y 139 → 161 |
| Amuleto variocolor (8 %) | Falló las dos veces: se cobran ₽25.000 y el Pokémon sigue igual, con animación de suspense y «No ha habido suerte» |
| Fusión 4 Poké → 2 Super Balls | Inventario 12 → 8 Poké Balls y 0 → 2 Super Balls |
| Fusión 8 Ultra → Master Ball (10 %) | Falló: se gastan 8 Ultra Balls y ₽20.000 |
| Fusión sin ingredientes | Rechazada: «Te faltan 3 × Cápsula habilidad en el inventario» |

**Cofres del desafío, limpieza al amanecer y regalos con Cobblemon real** (`tools/cobblemon_qa.py`, rol G, mundo plano):

| Paso | Resultado |
|---|---|
| `/caricraft desafio cofre emboscada` (nivel 6) | Cofre a 110–129 bloques, aviso con distancia y dirección, columna de luz y tarjeta con brújula en la app |
| Acercarse a menos de 10 bloques | Emboscada: oleada 1/2 con barra morada; mobs con casco y su equipo de nivel (p. ej. esqueleto con espada de diamante) a pleno día sin quemarse |
| Matar las oleadas | Sale la oleada 2/2 tras la pausa; al acabarla, «¡Cofre del desafío conquistado!» (+60 Carins) |
| Abrir el cofre | Peto o casco de hierro **encantado**, diamantes, esmeraldas, oro, manzana de oro, frascos de experiencia y, de Cobblemon, caramelos M y piedras trueno/fuego |
| `/caricraft desafio cofre guardian` | Golbat o Pupitar Nv. 24 que brilla junto al cofre al acercarse a 16 bloques, con barra «Guardián: …»; el cofre dice que hay que vencerlo o capturarlo |
| Matarlo sin combate | Vuelve a salir (no cuenta como vencido) |
| Noche de desafío con 7 mobs; irse a 850 bloques, amanecer y volver | **0 mobs** del desafío: los de la zona descargada se retiran al cargarla, sin kill ni botín |
| Regalo misterioso tres veces | Dos regalos y el tercero «ya abierto»; la tarjeta dice «quedan 0/2» |
| `/caricraft cobbleverse prueba` | Todo OK, también crear un Pokémon guardián salvaje, leer que no combate y que no tiene dueño |

Sin probar en partida: vencer al guardián en un combate de Cobblemon de verdad o capturarlo (no hay forma automática de iniciar el combate; se usa `PokemonEntity.isBattling` y se busca el Pokémon en el equipo y el PC).

**Bloque Cobbleverse del dúo con Cobblemon real** (`tools/cobblemon_qa.py`, rol D, mundo plano, 2026-11-26):

| Paso | Resultado |
|---|---|
| `/caricraft cobbleverse herir` y abrir Inicio | Tarjeta «Centro Pokémon de bolsillo» con «Curar · ₽1.500» |
| Curar | «¡Equipo curado! (₽1.500)»: ₽50.000 → ₽48.500; la tarjeta pasa a «Recargando: 14 min» |
| Curar otra vez | Rechazado: «Tu equipo ya está como nuevo» (sin cobrar) |
| Caza del día | 3 objetivos: 1 × Makuhita, 3 de tipo Eléctrico, 7 cualesquiera |
| Capturar el Makuhita (`/caricraft cobbleverse captura`) | ✔ y ₽1.200 + 15 Carins al momento |
| Completar los otros dos | Caza completa: ₽48.500 → ₽53.000 y 57 Carins (1.200 + 1.000 + 800 + 1.500; 15 + 12 + 10 + 20). Jugando solo, sin bonus de dúo y semanal 1/4 |
| Capturar Gible y Magikarp variocolor, Mewtwo y Dratini | Vitrina variocolor con modelos 3D y quién lo capturó; Mewtwo en Legendarios; capturas raras Mewtwo (ratio 3), Gible y Dratini (45); metas «Vuestro primer variocolor» y «Vuestro primer legendario» cumplidas: ₽53.000 → ₽62.000 |
| Mandar a Geodude Nv. 18 a la Cueva 4 h | Sale del equipo (6 → 5), tarjeta «Vuelve en 3 h 59 min»; «Recoger» antes de tiempo rechazado |
| `/caricraft cobbleverse expediciones` y recoger | Vuelve al equipo con ₽380, 2 carbón, 3 hierro en bruto y 2 fragmentos de amatista (≈ ₽95 por hora: pasivo y pequeño) |
| `/caricraft cobbleverse variocolor gible` | Aviso «¡Gible variocolor salvaje! A 40 bloques al Oeste de Dev1» y brújula «✦ Gible variocolor · 40 m →» en la barra de acción (mirando al sur, el oeste queda a la derecha); tarjeta en Inicio con distancia, flecha y minutos |
| Ir hasta él | «¡Gible variocolor está aquí!» |
| Expedición con los textos nuevos (segunda pasada) | Destinos «Bosque», «Cueva» y «Costa» con los tipos en varias líneas; Geodude vuelve con ₽409 y objetos |
| Que desaparezca | El aviso se quita solo |
| Opción `levelCapSpawns` encendida, con RCT y su datapack de COBBLEVERSE | Level cap 25 al empezar (serie de Kanto). Con un Charizard Nv. 70 en el equipo y un minuto de apariciones: 109 sin cambios, 8 bajadas al cap y 16 canceladas; el CobbleCentro muestra «Level cap 25» |

Por defecto `levelCapSpawns` está desactivado y los niveles de los salvajes son los de Cobbleverse; la regla tiene tests (`LevelCapRuleTest`).

**Bloque normal con cliente y servidor de producción** (`tools/cobblemon_qa.py`, rol R, mundo plano, 2026-11-26):

| Paso | Resultado |
|---|---|
| Inicio del CariPhone sin nada hecho | Tarjeta de racha a 0 («Reclama una misión hoy para empezar la racha: +15 C») y evento de la semana: «Semana artesana», misiones de fabricar ×1,5, meta 600 objetos |
| Reclamar la misión diaria «Paseo» | «Racha del dúo: 1 día (+15 Carins)» además de los 25 de la misión; la tarjeta pasa a «Hoy cumplido» con el primer punto en naranja |
| `/caricraft rutina evento 300` | «Evento «Semana artesana»: ¡a mitad de la meta! +100 Carins» |
| Completar la meta | +300 Carins; medallas «Eventos» y «Artesano» de bronce (+25 cada una); la de «Nivel» (nivel 6 del desafío) ya había salido al entrar |
| Vitrina | 3/48 medallas, cada una con su progreso al siguiente tramo; las de bronce en color bronce |
| Noche de desafío a nivel 6 y `/caricraft desafio jefe` | «¡El Rey Zombi (jefe de nivel 5) ha aparecido a 20 bloques al Suroeste!», barra morada «El Rey Zombi · jefe de nivel 5» bajo la de la noche, tarjeta en la app Desafío |
| Segunda pasada tras los arreglos | Jefe con 144/144 de vida, más grande y brillando; tarjeta del evento sin solapes («✔ mitad: 100 C · ✔ meta: 300 C»), «Racha: 1 día» y los trofeos primero en la Vitrina |
| Derrotarlo (`/damage … by @s`) | «¡El Rey Zombi derrotado (nivel 5)!»: +250 Carins, **Trofeo del desafío** en el inventario, botín en el suelo (diamantes, esmeraldas, manzana de oro y frascos de experiencia), medalla «Jefes» de bronce y el trofeo en la Vitrina |

## Errores encontrados y corregidos durante la QA
1. **Teléfono borroso (alto):** en 1.21 `Screen.render` vuelve a pintar el fondo desenfocado encima. Corregido en `PhoneScreen`.
2. **Pestañas de misiones cortadas (bajo):** se quitaron los contadores y se añadió un punto rojo si hay misiones para reclamar.
3. **Nombres de apps cortados (bajo):** las etiquetas se encogen para caber.
4. **Servidor montado a mano sin `athena`/`fusion` (medio):** documentado; el script de prueba los incluye.
5. **Tarjeta del Desafío (bajo):** el título se cortaba y los indicadores de nivel se montaban sobre el texto en negrita. Corregido.
6. **Mercado (bajo):** el texto de stock quedaba tapado por el botón Comprar. Corregido.
7. **CobbleCentro: barra lateral congelada (medio):** los recortes anidados de las tarjetas dejaban activo el recorte de pantalla de un fotograma a otro, y fuera del contenido se veía siempre el primer fotograma (la pestaña activa no cambiaba). Corregido con recortes anidados de `GuiGraphics`.
8. **Cliente de QA en inglés (bajo):** el índice de recursos de Loom se llama `1.21.1-17`; con `17` no se cargaba ningún idioma salvo el inglés del JAR. Corregido en `tools/cobblemon_qa.py`.
9. **Suerte (bajo):** porcentajes como «7,0 %», cantidades sin separador de miles en los avisos («₽1000») y botón «¡Genial!» también al fallar. Corregido («7 %», «₽1.000», «Vale»).
10. **Capas de animación (bajo):** con la animación encima se podía pulsar lo de debajo. Ahora la capa absorbe los clics salvo su botón.
11. **Cofres del desafío en mundo plano (medio):** la comprobación de «por encima del nivel del mar» descartaba todos los sitios en mundos planos. Se quitó (los líquidos ya se comprueban). El servidor registra ahora por qué no encuentra sitio.
12. **Cofres lejanos (medio):** mirar el cofre cada segundo habría cargado su chunk aunque no hubiera nadie cerca. Ahora solo se mira si la zona ya está cargada.
13. **Etiquetas del inicio pegadas (bajo):** con 4 columnas, «Misiones», «Cartera» y «Mercado» se tocaban. Ahora dejan margen y algunas apps usan un nombre corto.
14. **Teléfono sin datos con Cobbleverse (alto):** `PokemonSpecies.getByName("cobblemon:pidgey")` lanza una excepción (espera el nombre sin `cobblemon:`), y eso tumbaba el snapshot entero del CariPhone. Corregido: los nombres se normalizan, y si una parte de Cobbleverse falla se oculta solo esa parte (o la pestaña), nunca el teléfono.
15. **Textos cortados en el CobbleCentro (bajo):** la descripción de la curación, los objetivos largos de la caza, las etiquetas de la Pokédex del dúo y los tipos de los destinos. Ahora se reparten en dos líneas o se encogen.
16. **Jefe con el doble de vida (medio):** el refuerzo de vida por nivel del equipo del desafío se sumaba a la del jefe (317 en vez de 144 el primero). Ahora la vida final es la de la tabla.
17. **Tarjeta del evento (bajo):** el progreso y los premios se pisaban; «1 días». Corregido, y los trofeos van antes que las medallas en la Vitrina.
18. **Jefe zombi bebé o araña con jinete (bajo):** se crean sin la aparición aleatoria de Minecraft; el equipo lo pone el desafío.

## Pendiente (priorizado)
1. **Medio:** probar captura, evolución y combate con un cliente COBBLEVERSE completo.
2. **Medio:** comprobar en COBBLEVERSE si los mobs del desafío dañan a los Pokémon sacados, y que los sacos de dormir de Comforts también quedan bloqueados en la noche de desafío.
3. **Bajo:** el bloqueo de dormir con una cama real no lo cubre el guion automático (lógica cubierta por tests).
4. **Bajo:** bloques colocados por el jugador cuentan al volver a picarlos (afecta a "Leñador" y "Mineros de élite"). Posible mejora: ignorar bloques colocados por jugadores.
5. **Bajo:** fabricar y descomponer (lingotes ↔ bloque) cuenta para "Artesano" (40 Carins, una vez).
6. **Bajo:** fabricar y picar no los cubre el guion automático (sí los tests del núcleo).
