# PROMPT 05 — Misiones

Implementa misiones individuales, cooperativas, hitos y opcionalmente diarias/semanales. Modelo declarativo con ID, requisitos, objetivos, progreso, estado, recompensa y repetición.

Ciclo disponible→aceptada→progreso→completada→reclamada; soporta bloqueada/abandonada/caducada.

Objetivos base no dependientes de Cobblemon: explorar bioma, visitar lugar, recorrer distancia, craftear/entregar ítem. Objetivos Cobblemon solo con eventos fiables auditados.

Progreso validado en servidor. Reclamación idempotente conectada al servicio económico. Tests de duplicación, desconexión, contribución de ambos y reinicios.
