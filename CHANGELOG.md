# Changelog

## Unreleased
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
