-- target: tenant
-- Theme values may now be "#RRGGBB" (solid) or "#RRGGBB,#RRGGBB" (gradient
-- primary,accent). Widen the column so gradients fit; CompanyService
-- validates the format.
ALTER TABLE company_theme_settings
    MODIFY COLUMN theme_color VARCHAR(24) NOT NULL;
