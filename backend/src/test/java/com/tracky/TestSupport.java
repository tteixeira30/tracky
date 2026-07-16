package com.tracky;

import java.lang.reflect.Field;

/** Utilitários partilhados pelos testes. */
public final class TestSupport {

    private TestSupport() {}

    /**
     * Define o {@code id} (gerado pela BD, sem setter) de uma entidade em testes
     * unitários, onde não há persistência que o atribua.
     */
    public static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível definir o id de teste", e);
        }
    }
}
