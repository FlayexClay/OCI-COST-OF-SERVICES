package oci.connection.demo.dto;

import java.util.List;

/** Descripción de un recurso OCI: nombre, tipo legible, componentes provisionados y OCIDs hijos. */
public record ResourceDescription(String name, String type,
                                  List<ComponentSpec> components,
                                  List<String> childIds) {
}