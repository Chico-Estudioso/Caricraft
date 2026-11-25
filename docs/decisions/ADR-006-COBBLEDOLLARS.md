# ADR-006 Integración con CobbleDollars

Estado: **aceptada**.

## Contexto
ADR-004 dejó los Carins separados de CobbleDollars. Ahora se pide que, con la opción de Cobbleverse, se puedan vender Pokémon por «la moneda del juego» y haya una tienda cara de cosas de Cobbleverse.

## Decisión
- Los **Carins siguen siendo independientes**: no se convierten en CobbleDollars ni al revés, y las apps se siguen pagando con Carins.
- La pestaña **Cobbleverse** del Mercado (opcional, desactivada por defecto) usa **CobbleDollars**:
  - Tienda de Pokémon y objetos especiales.
  - Venta de Pokémon del equipo.
- El regalo misterioso puede dar CobbleDollars o Carins.
- Acceso a CobbleDollars **por reflexión**:
  - Interfaz `fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer`, que CobbleDollars añade a `Player`. Getter y setter de `BigInteger`.
  - Sin dependencia de compilación ni redistribución (licencia «All rights reserved»).
- El servidor es la autoridad:
  - Calcula precios con sus datos.
  - Cobra antes de entregar y devuelve si falla.
  - Si un pago falla, devuelve el Pokémon.
  - Aplica límites diarios por jugador.

## Alternativas descartadas
- **Compilar contra CobbleDollars:** obliga a descargar un JAR con todos los derechos reservados en la CI.
- **Usar sus comandos (`/cobbledollars`):** no permite comprobar el saldo de forma fiable ni deshacer.
- **Pagar la tienda en Carins:** mezcla la progresión del CariPhone con la economía del modpack, que es justo lo que se quiere aprovechar aquí.

## Consecuencias
- Si CobbleDollars cambia esa interfaz, la tienda y la venta se desactivan solas (aviso en el registro). `/caricraft cobbleverse prueba` lo detecta.
- Verificado con CobbleDollars 2.0.0+Beta-5.1 en un servidor COBBLEVERSE 1.8.0 real.
