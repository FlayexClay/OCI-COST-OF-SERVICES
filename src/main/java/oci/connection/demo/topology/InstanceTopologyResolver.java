package oci.connection.demo.topology;

import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.BootVolumeAttachment;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.VolumeAttachment;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.ListBootVolumeAttachmentsRequest;
import com.oracle.bmc.core.requests.ListVolumeAttachmentsRequest;
import com.oracle.bmc.core.responses.ListBootVolumeAttachmentsResponse;
import com.oracle.bmc.core.responses.ListVolumeAttachmentsResponse;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ResourceInfo;
import com.oracle.bmc.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve qué discos (boot y block volumes) están adjuntos a una instancia,
 * usando los attachments de la API de Compute.
 *
 * <p>Esta es la relación instancia↔disco que NO existe en los datos de costo y
 * que hay que reconstruir aparte.</p>
 *
 * <p>Requiere permiso de lectura sobre Compute (p. ej. instance-family y
 * volume-family).</p>
 */
public class InstanceTopologyResolver implements AutoCloseable{

    private final ComputeClient computeClient;

    public InstanceTopologyResolver(OciAuthProvider auth) {
        this.computeClient = ComputeClient.builder().build(auth.getProvider());
    }

    /**
     * Devuelve los OCIDs de los discos adjuntos a la instancia.
     *
     * @param instanceId         OCID de la instancia
     * @param compartmentId      compartimento de la instancia (obligatorio)
     * @param availabilityDomain AD de la instancia (necesario para boot volumes)
     */
    public InstanceDisks disksFor(String instanceId, String compartmentId, String availabilityDomain) {
        ensureRegion(instanceId);
        List<String> bootIds = new ArrayList<>();
        if (compartmentId != null && availabilityDomain != null) {
            ListBootVolumeAttachmentsResponse r = computeClient.listBootVolumeAttachments(
                    ListBootVolumeAttachmentsRequest.builder()
                            .availabilityDomain(availabilityDomain)
                            .compartmentId(compartmentId)
                            .instanceId(instanceId)
                            .build());
            for (BootVolumeAttachment a : r.getItems()) {
                if (a.getBootVolumeId() != null && !bootIds.contains(a.getBootVolumeId())) {
                    bootIds.add(a.getBootVolumeId());
                }
            }
        }

        List<String> blockIds = new ArrayList<>();
        if (compartmentId != null) {
            ListVolumeAttachmentsResponse vr = computeClient.listVolumeAttachments(
                    ListVolumeAttachmentsRequest.builder()
                            .compartmentId(compartmentId)
                            .instanceId(instanceId)
                            .build());
            for (VolumeAttachment a : vr.getItems()) {
                if (a.getVolumeId() != null && !blockIds.contains(a.getVolumeId())) {
                    blockIds.add(a.getVolumeId());
                }
            }
        }

        return new InstanceDisks(bootIds, blockIds);
    }

    /**
     * Consulta el Compute API por OCID para obtener nombre, compartimento y AD.
     * Devuelve null si la instancia no existe o no hay permiso.
     */
    public ResourceInfo resolveInstanceInfo(String instanceId) {
        ensureRegion(instanceId);
        try {
            Instance inst = computeClient.getInstance(
                    GetInstanceRequest.builder().instanceId(instanceId).build())
                    .getInstance();
            if (inst == null) return null;
            return new ResourceInfo(instanceId, inst.getDisplayName(), "Instance",
                    inst.getCompartmentId(), inst.getAvailabilityDomain());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        computeClient.close();
    }

    /** Extrae la región del OCID: ocid1.instance.oc1.sa-santiago-1.xxxx */
    private static String regionFromOcid(String ocid) {
        String[] parts = ocid.split("\\.");
        return parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
    }

    private void ensureRegion(String instanceId) {
        String regionId = regionFromOcid(instanceId);
        if (regionId != null) {
            computeClient.setRegion(Region.fromRegionId(regionId));
        }
    }

    /** OCIDs de discos adjuntos a una instancia, separados por tipo. */
    public record InstanceDisks(List<String> bootVolumeIds, List<String> blockVolumeIds) {
    }
}
