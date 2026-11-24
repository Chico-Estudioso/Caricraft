# PROMPT 04 — Carins y tienda

Implementa economía persistente y autoritativa en servidor. Decide modelo según ADR. Moneda ficticia Carins.

Fuentes iniciales de prueba/admin y contratos para recompensas de misiones. Tienda con apps, mejoras, temas y cosméticos. Apps principales compartidas; cosméticos individuales si se aprobó.

Precios orientativos configurables: CariLens 300, Crónicas 250, Exploración 400, CariCamp 500, tema 150, expansión álbum 200.

Reglas: servidor valida precio y saldo; transacciones atómicas; no saldo negativo; ledger; idempotencia; proteger doble clic/reintentos/desconexiones; no cobrar app ya comprada. Tests de concurrencia, reinicio, saldo insuficiente y manipulación de paquetes.
