# Testing

## Automáticas (`./gradlew test`)
- Economía: saldo, débito insuficiente, idempotencia, ledger acotado.
- Tienda: compra, requisitos, app ya comprada, cosmético personal, artículos "próximamente".
- Misiones: ciclo de estados, contribución de ambos, reclamación idempotente, caducidad diaria/semanal, entregas.
- Grupos: invitación, caducidad, aceptación, fusión, desvinculación con confirmación, reparto y enfriamiento.
- Persistencia: ida y vuelta NBT del estado completo y migración de esquema.
- Red: validación de acciones (longitudes, acciones desconocidas).

## Integración
Carga Fabric en servidor dedicado (con y sin Cobblemon), persistencia tras reinicio, dos clientes, desconexión durante transacción, paquetes repetidos.

## Manual
GUI scale, jugador sin dúo, cambios de configuración, ausencia de Cobblemon y logs.
