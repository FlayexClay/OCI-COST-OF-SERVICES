package oci.connection.demo.describe;

import oci.connection.demo.dto.ResourceDescription;

/** Describe un tipo de recurso OCI: nombre, tipo legible, componentes y recursos hijos. */
public interface ResourceDescriber extends AutoCloseable {

    boolean supports(String ocid);

    ResourceDescription describe(String ocid);

    @Override
    default void close() throws Exception {}
}