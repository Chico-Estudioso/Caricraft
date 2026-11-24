# ADR-002 Autoridad del servidor

Estado: **aceptada**.

El servidor valida y persiste moneda, compras, misiones, recompensas, vínculo y datos compartidos. El cliente solo presenta la UI a partir de snapshots y solicita acciones tipadas. Toda mutación ocurre en el hilo del servidor.
