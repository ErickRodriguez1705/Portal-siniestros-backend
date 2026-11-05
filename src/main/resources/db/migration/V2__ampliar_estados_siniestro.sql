-- Las instalaciones anteriores tenian una restriccion con solo dos estados.
-- En una base nueva, Hibernate creara la tabla despues de esta migracion.
DO $$
BEGIN
    IF to_regclass('public.siniestros') IS NOT NULL THEN
        ALTER TABLE public.siniestros DROP CONSTRAINT IF EXISTS siniestros_estado_check;
        ALTER TABLE public.siniestros ADD CONSTRAINT siniestros_estado_check
            CHECK (estado IN ('PENDIENTE_DETALLE', 'REPORTADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO'));
    END IF;
END
$$;
