# ADR-004 Modelo económico de Carins

Estado: **aceptada**.

## Decisión
- Todo jugador pertenece siempre a un **grupo** (`Party`). Un jugador sin vincular es un grupo de un miembro; un dúo es un grupo de dos (`maxPartySize = 2`, ampliable en el futuro).
- Cada grupo tiene **un saldo compartido** de Carins y un ledger.
- Todas las recompensas (misiones individuales y cooperativas, descubrimientos) van al saldo del grupo.
- **Vincular:** el jugador que acepta deja su grupo individual; su saldo, apps, biomas descubiertos y crónicas se fusionan en el grupo de quien invitó (con asientos en el ledger).
- **Desvincular:** quien se va crea un grupo nuevo y recibe la mitad del saldo (redondeo hacia abajo); los dos conservan apps, biomas y una copia de las crónicas. Hay un enfriamiento configurable (por defecto 24 h) antes de poder vincularse de nuevo.
- Saldo nunca negativo. Créditos con clave de idempotencia.

## Alternativas descartadas
- Saldo individual + fondo común: más pantallas, más transferencias y más estados de error para un servidor de dos personas.

## Relación con CobbleDollars
Los Carins no se convierten en CobbleDollars ni al revés (ver `MODPACK_FEATURE_AUDIT.md`). La pestaña opcional Cobbleverse del Mercado sí usa CobbleDollars para su tienda y para vender Pokémon (ADR-006).
