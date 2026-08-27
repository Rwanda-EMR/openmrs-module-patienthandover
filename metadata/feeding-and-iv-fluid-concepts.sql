-- OpenMRS metadata for the Feeding and IV Fluid Chart
-- Compatible with the schema used by patienthandover_dev.
-- Import this file once before importing the HTML Form Entry definitions.

SET NAMES utf8mb4;
START TRANSACTION;

SET @numeric_datatype := (
    SELECT concept_datatype_id FROM concept_datatype
    WHERE uuid = '8d4a4488-c2cc-11de-8d13-0010c6dffd0f'
);
SET @question_class := (
    SELECT concept_class_id FROM concept_class
    WHERE uuid = '8d491e50-c2cc-11de-8d13-0010c6dffd0f'
);
SET @metadata_creator := (
    SELECT creator FROM concept ORDER BY concept_id LIMIT 1
);

-- Feeding or IV fluid quantity (Numeric, mL)
INSERT INTO concept
    (retired, datatype_id, class_id, is_set, creator, date_created, version, uuid)
SELECT 0, @numeric_datatype, @question_class, 0, @metadata_creator, NOW(), '1.0',
       '7e455d5e-8852-4bfd-8f7d-5f9d43b33e01'
WHERE NOT EXISTS (
    SELECT 1 FROM concept WHERE uuid = '7e455d5e-8852-4bfd-8f7d-5f9d43b33e01'
);

SET @quantity_concept := (
    SELECT concept_id FROM concept
    WHERE uuid = '7e455d5e-8852-4bfd-8f7d-5f9d43b33e01'
);

INSERT INTO concept_numeric
    (concept_id, low_absolute, units, allow_decimal, display_precision)
SELECT @quantity_concept, 0, 'mL', 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM concept_numeric WHERE concept_id = @quantity_concept
);

INSERT INTO concept_name
    (concept_id, name, locale, locale_preferred, creator, date_created,
     concept_name_type, voided, uuid)
SELECT @quantity_concept, 'Feeding or IV fluid quantity', 'en', 1,
       @metadata_creator, NOW(), 'FULLY_SPECIFIED', 0,
       '3167af51-0c2d-4656-8b21-02781ea68701'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = '3167af51-0c2d-4656-8b21-02781ea68701')
UNION ALL
SELECT @quantity_concept, 'Fluid quantity', 'en', 0,
       @metadata_creator, NOW(), 'SHORT', 0,
       '3167af51-0c2d-4656-8b21-02781ea68702'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = '3167af51-0c2d-4656-8b21-02781ea68702')
UNION ALL
SELECT @quantity_concept, 'Quantité d’alimentation ou de liquide IV', 'fr', 1,
       @metadata_creator, NOW(), 'FULLY_SPECIFIED', 0,
       '3167af51-0c2d-4656-8b21-02781ea68703'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = '3167af51-0c2d-4656-8b21-02781ea68703')
UNION ALL
SELECT @quantity_concept, 'Quantité de liquide', 'fr', 0,
       @metadata_creator, NOW(), 'SHORT', 0,
       '3167af51-0c2d-4656-8b21-02781ea68704'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = '3167af51-0c2d-4656-8b21-02781ea68704');

INSERT INTO concept_description
    (concept_id, description, locale, creator, date_created, uuid)
SELECT @quantity_concept,
       'Volume of enteral feeding or intravenous fluid administered during this chart entry, measured in millilitres.',
       'en', @metadata_creator, NOW(), 'a4c6b4ee-5c7f-4cf0-9efd-e7c958435701'
WHERE NOT EXISTS (SELECT 1 FROM concept_description WHERE uuid = 'a4c6b4ee-5c7f-4cf0-9efd-e7c958435701')
UNION ALL
SELECT @quantity_concept,
       'Volume d’alimentation entérale ou de liquide intraveineux administré lors de cette saisie, mesuré en millilitres.',
       'fr', @metadata_creator, NOW(), 'a4c6b4ee-5c7f-4cf0-9efd-e7c958435702'
WHERE NOT EXISTS (SELECT 1 FROM concept_description WHERE uuid = 'a4c6b4ee-5c7f-4cf0-9efd-e7c958435702');

-- Gastric residual volume (Numeric, mL)
INSERT INTO concept
    (retired, datatype_id, class_id, is_set, creator, date_created, version, uuid)
SELECT 0, @numeric_datatype, @question_class, 0, @metadata_creator, NOW(), '1.0',
       '39f4316a-0011-4372-b895-956b8aa14202'
WHERE NOT EXISTS (
    SELECT 1 FROM concept WHERE uuid = '39f4316a-0011-4372-b895-956b8aa14202'
);

SET @residual_concept := (
    SELECT concept_id FROM concept
    WHERE uuid = '39f4316a-0011-4372-b895-956b8aa14202'
);

INSERT INTO concept_numeric
    (concept_id, low_absolute, units, allow_decimal, display_precision)
SELECT @residual_concept, 0, 'mL', 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM concept_numeric WHERE concept_id = @residual_concept
);

INSERT INTO concept_name
    (concept_id, name, locale, locale_preferred, creator, date_created,
     concept_name_type, voided, uuid)
SELECT @residual_concept, 'Gastric residual volume', 'en', 1,
       @metadata_creator, NOW(), 'FULLY_SPECIFIED', 0,
       'b4b48230-8e68-46e0-a335-e476dcf8df01'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = 'b4b48230-8e68-46e0-a335-e476dcf8df01')
UNION ALL
SELECT @residual_concept, 'Gastric residual', 'en', 0,
       @metadata_creator, NOW(), 'SHORT', 0,
       'b4b48230-8e68-46e0-a335-e476dcf8df02'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = 'b4b48230-8e68-46e0-a335-e476dcf8df02')
UNION ALL
SELECT @residual_concept, 'Volume résiduel gastrique', 'fr', 1,
       @metadata_creator, NOW(), 'FULLY_SPECIFIED', 0,
       'b4b48230-8e68-46e0-a335-e476dcf8df03'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = 'b4b48230-8e68-46e0-a335-e476dcf8df03')
UNION ALL
SELECT @residual_concept, 'Résidu gastrique', 'fr', 0,
       @metadata_creator, NOW(), 'SHORT', 0,
       'b4b48230-8e68-46e0-a335-e476dcf8df04'
WHERE NOT EXISTS (SELECT 1 FROM concept_name WHERE uuid = 'b4b48230-8e68-46e0-a335-e476dcf8df04');

INSERT INTO concept_description
    (concept_id, description, locale, creator, date_created, uuid)
SELECT @residual_concept,
       'Volume remaining in the stomach when gastric residual is assessed, measured in millilitres.',
       'en', @metadata_creator, NOW(), 'd430507c-9f43-41d5-b760-fdbb34331401'
WHERE NOT EXISTS (SELECT 1 FROM concept_description WHERE uuid = 'd430507c-9f43-41d5-b760-fdbb34331401')
UNION ALL
SELECT @residual_concept,
       'Volume restant dans l’estomac lors de l’évaluation du résidu gastrique, mesuré en millilitres.',
       'fr', @metadata_creator, NOW(), 'd430507c-9f43-41d5-b760-fdbb34331402'
WHERE NOT EXISTS (SELECT 1 FROM concept_description WHERE uuid = 'd430507c-9f43-41d5-b760-fdbb34331402');

COMMIT;

SELECT concept_id, uuid FROM concept
WHERE uuid IN (
    '7e455d5e-8852-4bfd-8f7d-5f9d43b33e01',
    '39f4316a-0011-4372-b895-956b8aa14202'
)
ORDER BY uuid;
