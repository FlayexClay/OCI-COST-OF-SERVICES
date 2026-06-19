package oci.connection.demo.dto;

/** Cantidad provisionada de un componente de un recurso (sin costo). */
public record ComponentSpec(String label, Double quantity, String unit) {
}