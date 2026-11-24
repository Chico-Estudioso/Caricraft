# Informe de QA (M9)

Fecha: 2026-11-24 · Versión: 0.1.0

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

## Errores encontrados y corregidos durante la QA
1. **Teléfono borroso (alto):** en 1.21 `Screen.render` vuelve a pintar el fondo desenfocado encima. Corregido en `PhoneScreen`.
2. **Pestañas de misiones cortadas (bajo):** se quitaron los contadores y se añadió un punto rojo si hay misiones para reclamar.
3. **Nombres de apps cortados (bajo):** las etiquetas se encogen para caber.
4. **Servidor montado a mano sin `athena`/`fusion` (medio):** documentado; el script de prueba los incluye.

## Pendiente (priorizado)
1. **Medio:** probar captura, evolución y combate con un cliente COBBLEVERSE completo.
2. **Bajo:** bloques colocados por el jugador cuentan al volver a picarlos (afecta a "Leñador" y "Mineros de élite"). Posible mejora: ignorar bloques colocados por jugadores.
3. **Bajo:** fabricar y descomponer (lingotes ↔ bloque) cuenta para "Artesano" (40 Carins, una vez).
4. **Bajo:** fabricar y picar no los cubre el guion automático (sí los tests del núcleo).
