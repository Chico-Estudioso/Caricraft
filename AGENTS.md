# AGENTS.md

- El servidor manda: el cliente no decide saldos, compras ni la finalización de misiones.
- Datos compartidos en el grupo (dúo); datos personales (cosméticos, tema) en el perfil del jugador.
- El teléfono es interfaz; no almacena reglas de negocio.
- Apps registradas mediante un catálogo extensible (`AppCatalog`).
- El core funciona sin Cobblemon; la integración es opcional y se activa solo si el mod está cargado.
- Evitar escaneos de mundo por tick y trabajo pesado en el hilo principal.
- Validar paquetes, permisos, frecuencia y estados.
- No duplicar funciones ya incluidas en COBBLEVERSE 1.8.0 (ver `docs/MODPACK_FEATURE_AUDIT.md`).
- Java 21 y APIs de Minecraft 1.21.1 / Fabric API 0.116.18 verificadas.
- Compilación reproducible (Gradle Wrapper), tests, migraciones de esquema y pruebas en servidor dedicado.
- Rama por tarea y PR.
