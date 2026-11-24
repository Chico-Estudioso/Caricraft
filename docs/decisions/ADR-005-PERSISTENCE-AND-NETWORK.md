# ADR-005 Persistencia y red

Estado: **aceptada**.

## Persistencia
- Un único `SavedData` (`caricraft`) en el almacenamiento del Overworld con todo el estado (`CaricraftState`).
- Serialización NBT explícita con `schemaVersion`; las migraciones se aplican al cargar.
- El modelo de dominio es Java puro (sin clases de Minecraft) para poder probarlo con JUnit.

## Red
- C2S `caricraft:phone_action`: enum `PhoneAction` + hasta dos argumentos de texto con longitud máxima. Límite de frecuencia por jugador.
- S2C `caricraft:phone_snapshot`: snapshot completo de lo que muestra el teléfono, serializado como JSON (Gson) con `protocolVersion`.
- S2C `caricraft:phone_notify`: clave de traducción + argumentos para avisos (compra hecha, saldo insuficiente…).
- El servidor envía snapshot tras cada acción al solicitante y a los demás miembros conectados del grupo.
