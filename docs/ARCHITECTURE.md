# Arquitectura

Propuesta pendiente de validar en M0.

- `common`: modelos, IDs, reglas y contratos.
- `server`: persistencia, economía, misiones, vínculo, validación y recompensas.
- `client`: teléfono, pantallas, navegación y presentación.
- `integration/cobblemon`: adaptador opcional.
- `network`: mensajes tipados/versionados.
- `config`: ajustes del servidor.

Servidor autoritativo: cliente solicita abrir app, aceptar misión, comprar o reclamar; servidor comprueba permisos, saldo, requisitos, propiedad, cooldown e idempotencia.

Persistencia versionada: perfil del dúo, perfiles personales, estado de misiones, ledger, crónicas y schema version. Usar el mecanismo apropiado de Fabric/Minecraft 1.21.1 tras verificar API.

Catálogo de apps: ID estable, nombre traducible, descripción, precio, propiedad dúo/individual, requisitos, versión, estado y pantalla.

Economía: operaciones atómicas, sin saldo negativo, ledger, idempotencia y validación en servidor.

UI desacoplada de reglas. Seguridad en paquetes, frecuencia, permisos, tamaño y estado.
