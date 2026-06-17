package oci.connection.demo.dto;

import java.math.BigDecimal;

/**
 * Una línea de costo desglosada por recurso y componente.
 *
 * @param resourceId   OCID del recurso (instancia, boot volume, block volume...)
 * @param resourceName nombre del recurso si OCI lo provee (suele venir vacío)
 * @param component    componente clasificado (procesador, memoria, boot, block)
 * @param skuName      nombre del SKU facturado
 * @param amount       monto del costo
 * @param currency     moneda
 */
public record ResourceCostLine(String resourceId,
                               String resourceName,
                               CostComponent component,
                               String skuName,
                               BigDecimal amount,
                               String currency) {
}
