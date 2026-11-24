# Roadmap

## M0 Investigación
**Estado:** ✅ Hecho (`docs/ENVIRONMENT_AUDIT.md`, `MODPACK_FEATURE_AUDIT.md`, `COBBLEMON_API_AUDIT.md`, `RISK_REGISTER.md`).
Auditar COBBLEVERSE 1.8.0: Minecraft, Loader, Fabric API, Java, Cobblemon, mods que solapan, APIs, licencias y entorno cliente/servidor. Salida: auditoría y arquitectura aprobadas.

## M1 Base Fabric
**Estado:** ✅ Hecho.
Gradle/Loom, inicializadores, registros, networking, configuración, ítem CariPhone y carga cliente/servidor.

## M2 Teléfono
**Estado:** ✅ Hecho (CariPhone con 9 apps y 4 temas).
UI, navegación, iconos, catálogo, estados bloqueado/desbloqueado y snapshots del servidor.

## M3 Economía
**Estado:** ✅ Hecho (Carins).
Carins, ledger, persistencia, transacciones atómicas, recompensas y protección anti-duplicación.

## M4 Tienda y apps
**Estado:** ✅ Hecho.
Compra compartida de apps, mejoras y cosméticos personales.

## M5 Misiones
**Estado:** ✅ Hecho (26 misiones por defecto, ampliables por JSON).
Individuales, cooperativas, objetivos, progreso, reclamación idempotente y recompensas.

## M6 Vínculo y crónicas
**Estado:** ✅ Hecho.
Invitación/aceptación, grupo (dúo), privacidad, desvinculación y diario compartido.

## M7 Aplicaciones de contenido
**Estado:** 🟡 Parcial: Exploración y Estadísticas hechas; CariLens y CariCamp aplazadas (duplicarían Pokédex y Comforts, ver auditoría).
Exploración, álbum (CariLens), CariCamp y colecciones, solo si no duplican el modpack.

## M8 Cobblemon
**Estado:** 🟡 Hecho y probado en servidor COBBLEVERSE real; falta probar eventos con un jugador real.
Adaptador opcional para eventos/API de Cobblemon 1.8.4, sin acoplar el core.

## M9 QA y balance
**Estado:** ✅ Hecho (`docs/QA_REPORT.md`, `docs/BALANCE_REPORT.md`).
Dos clientes, servidor dedicado, reinicios, desconexiones, seguridad, economía y compatibilidad.

## M10 Release
**Estado:** 🟡 Versión 0.1.0 lista para instalar (JAR en `build/libs` y como artefacto del CI). Falta publicar una release si se quiere.
JAR, instalación, documentación, licencias, changelog, matriz de compatibilidad y mantenimiento.
