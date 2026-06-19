package oci.connection.demo.describe;

import oci.connection.demo.dto.ResourceDescription;

import java.util.List;

/** Registro de describidores: delega al primero que soporte el OCID dado. */
public class ResourceDescriberRegistry implements AutoCloseable {

    private final List<ResourceDescriber> describers;

    public ResourceDescriberRegistry(List<ResourceDescriber> describers) {
        this.describers = List.copyOf(describers);
    }

    /** Devuelve el describidor que soporta el OCID, o null si ninguno lo hace. */
    public ResourceDescriber finderFor(String ocid) {
        for (ResourceDescriber d : describers) {
            if (d.supports(ocid)) return d;
        }
        return null;
    }

    /** Describe el recurso usando el primer describidor que lo soporte. */
    public ResourceDescription describe(String ocid) {
        ResourceDescriber d = finderFor(ocid);
        return d != null ? d.describe(ocid) : null;
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (ResourceDescriber d : describers) {
            try { d.close(); } catch (Exception e) { if (first == null) first = e; }
        }
        if (first != null) throw first;
    }
}