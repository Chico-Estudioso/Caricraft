package com.chicoestudioso.caricraft.core;

/** Fuente de tiempo (epoch en milisegundos). Inyectable para tests. */
@FunctionalInterface
public interface Clock {
    Clock SYSTEM = System::currentTimeMillis;

    long now();
}
