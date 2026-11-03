DO $$
DECLARE
    duplicate_company_ids TEXT;
BEGIN
    SELECT string_agg(company_id::TEXT, ', ' ORDER BY company_id)
      INTO duplicate_company_ids
      FROM (
          SELECT company_id
            FROM company_admin
           GROUP BY company_id
          HAVING COUNT(*) > 1
      ) duplicates;

    IF duplicate_company_ids IS NOT NULL THEN
        RAISE EXCEPTION
            'V24 aborted: duplicate company_admin rows for company_id(s): %',
            duplicate_company_ids;
    END IF;
END
$$;

ALTER TABLE company_admin
    ADD CONSTRAINT uk_company_admin_company UNIQUE (company_id);
