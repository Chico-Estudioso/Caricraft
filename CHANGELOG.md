# Changelog

## Unreleased
- **Desafío Dúo:**
  - Una noche de desafío obligatoria cada 3, que se puede adelantar desde la app; no se puede dormir.
  - Mobs vanilla equipados y reforzados por niveles (1–5), indicador en pantalla, Carins por mob y por sobrevivir, y 9 misiones de caza.
  - Preparación automática de MobsBeGone (`/caricraft desafio preparar`). Protocolo de red 2.

## 0.1.0 (2026-11-24)
- Kit inicial adaptado: el proyecto pasa a llamarse **Caricraft Mod**, la moneda **Carins** y el objetivo es COBBLEVERSE 1.8.0.
- M0: auditoría de COBBLEVERSE 1.8.0, de funcionalidades del modpack y de la API de Cobblemon 1.8.4; registro de riesgos.
- M1: arquitectura aprobada y ADR 001–005 (plataforma, autoridad del servidor, propiedad, modelo económico, persistencia y red).
- M1 base Fabric: proyecto Gradle 8.14.3 + Loom 1.11.8 con versiones fijadas, ítem CariPhone (receta y textura propias), tecla para abrir el teléfono, configuración `config/caricraft.json`, payloads de red tipados y CI de GitHub Actions.
- M3–M6 núcleo: dominio en Java puro con 46 tests. Incluye Carins con ledger e idempotencia, tienda (apps del grupo y temas personales), 26 misiones por defecto (individuales, cooperativas, diarias, semanales e hitos), vínculo de dúo con fusión y reparto, crónicas con notas, exploración de biomas y persistencia NBT versionada.
- M2/M4 servidor y CariPhone:
  - Persistencia en `data/caricraft.dat` y acciones del teléfono validadas con límite de frecuencia.
  - Seguimiento de distancia, biomas, bloques picados, fabricación y criaturas derrotadas.
  - Teléfono inicial al entrar y avisos emergentes.
  - Comandos `/carins` y `/caricraft`.
  - Interfaz completa del CariPhone: inicio, misiones, cartera, dúo, tienda, ajustes con 4 temas, crónicas, exploración, estadísticas y pantalla de app bloqueada.
  - Herramienta de QA visual solo para desarrollo.
- M8 Cobblemon:
  - Adaptador opcional para capturas, evoluciones y victorias (no cuenta PvP), con crónicas de primera captura, variocolor y legendarios.
  - Probado en un servidor COBBLEVERSE 1.8.0 real: 251 mods, Java 21.
  - Script `tools/cobbleverse_server_test.py`.
- M9 QA y balance:
  - 52 tests.
  - Partida real automatizada con dos clientes.
  - Informes `docs/QA_REPORT.md` y `docs/BALANCE_REPORT.md`.
  - Capturas en `docs/img/cariphone.png`.
