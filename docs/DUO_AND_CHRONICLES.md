# Vínculo y crónicas

## Vínculo
- Invitación explícita desde la app Dúo (nombre del jugador conectado) o `/caricraft duo invite <jugador>`.
- La invitación caduca a los 5 minutos; máximo 3 invitaciones pendientes por jugador.
- Se puede aceptar, rechazar o cancelar.
- Un jugador pertenece a un solo grupo; un grupo tiene como máximo 2 miembros en el MVP.
- Se identifica por UUID; el nombre solo se guarda para mostrarlo y se actualiza al conectarse.
- Desvincular exige confirmación (dos pulsaciones en 10 s). Política de reparto en ADR-004. Enfriamiento de 24 h.

## Crónicas
Diario compartido del grupo (máximo 300 entradas; las más antiguas se archivan descartándose):
- Formación del dúo y desvinculaciones.
- Misiones completadas.
- Compras de apps.
- Biomas descubiertos.
- Capturas especiales de Cobblemon (primera captura, shiny, legendario) y evoluciones.
- Notas escritas por los jugadores (máximo 120 caracteres).

Las coordenadas exactas solo se guardan si `chronicleRecordCoordinates` está activo (por defecto, no).

Solo los miembros del grupo ven sus crónicas; los operadores pueden consultarlas por comando.
