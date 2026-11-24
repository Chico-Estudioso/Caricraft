# Instrucciones — Caricraft Mod

- Responde y documenta en español. Usa inglés para identificadores, clases, métodos, paquetes y convenciones técnicas.
- Proyecto: mod Fabric para Minecraft 1.21.1 (COBBLEVERSE 1.8.0), con el CariPhone, apps desbloqueables, Carins, misiones, crónicas e integración Cobblemon opcional.
- Mod id `caricraft`, paquete `com.chicoestudioso.caricraft`, mappings oficiales de Mojang.
- Nunca trabajes directamente en `main`; usa ramas y PR. No uses `push --force`.
- No cambies versiones del modpack ni dependencias sin actualizar `docs/ENVIRONMENT_AUDIT.md`.
- No inventes APIs: verifica firmas en los JAR o en la documentación de la versión fijada.
- No redistribuyas assets, código o configuraciones de Cobbleverse ni de Cobblemon.
- El servidor es la autoridad sobre saldo, compras, misiones, recompensas y progreso. El cliente solo presenta UI y envía solicitudes.
- No añadas IA, servicios externos, telemetría ni monetización real.
- Separa `core` (Java puro, testeable), `server`, `network`, `client` y el adaptador opcional `integration/cobblemon`.
- Mantén tests para economía, misiones, vínculo, persistencia y red.
- Al terminar: `./gradlew build` (compila y ejecuta tests), documenta y abre PR.
