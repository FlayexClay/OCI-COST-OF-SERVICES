package oci.connection.demo.dto;

/**
 * Componente de costo de un recurso de OCI.
 *
 * <p>OCI no expone "procesador", "memoria", "boot volume" o "block volume"
 * como dimensiones de costo nativas, así que las inferimos:
 * los volúmenes se distinguen por el TIPO de OCID (boot vs block) y, en
 * compute flexible, OCPU y memoria llegan como SKUs distintos.</p>
 */
public enum CostComponent {
    PROCESSOR("Procesador (OCPU)"),
    MEMORY("Memoria"),
    BOOT_VOLUME("Boot volume"),
    BLOCK_VOLUME("Block volume"),
    OTHER("Otro");

    private final String label;

    CostComponent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Clasifica una línea de costo a partir del OCID del recurso, el nombre del
     * SKU y la unidad de medida.
     *
     * <p>El orden importa: los volúmenes se detectan primero por su OCID, antes
     * de mirar el SKU, porque boot y block comparten los mismos SKUs.</p>
     */
    public static CostComponent classify(String resourceId, String skuName, String unit) {
        String rid = resourceId == null ? "" : resourceId.toLowerCase();
        String sku = skuName == null ? "" : skuName.toLowerCase();
        String u = unit == null ? "" : unit.toLowerCase();

        // 1) Volúmenes: se distinguen por el tipo de OCID, no por el SKU.
        if (rid.contains("ocid1.bootvolume")) {
            return BOOT_VOLUME;
        }
        if (rid.contains("ocid1.volume")) {
            return BLOCK_VOLUME;
        }

        // 2) Compute flexible: OCPU y memoria son SKUs separados.
        if (sku.contains("ocpu") || u.contains("ocpu")) {
            return PROCESSOR;
        }
        if (sku.contains("memory") || sku.contains("memoria")) {
            return MEMORY;
        }

        return OTHER;
    }
}

