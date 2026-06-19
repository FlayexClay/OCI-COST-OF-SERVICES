package oci.connection.demo.dto;

import java.math.BigDecimal;

/** Componente de un recurso con cantidad provisionada y costo del período. */
public record ComponentCost(String label, Double quantity, String unit, BigDecimal cost) {
}