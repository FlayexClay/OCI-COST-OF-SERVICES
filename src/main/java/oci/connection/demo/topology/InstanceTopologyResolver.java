package oci.connection.demo.topology;

import com.oracle.bmc.Region;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.BootVolumeAttachment;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceShapeConfig;
import com.oracle.bmc.core.model.VolumeAttachment;
import com.oracle.bmc.core.requests.GetBootVolumeRequest;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.GetVolumeRequest;
import com.oracle.bmc.core.requests.ListBootVolumeAttachmentsRequest;
import com.oracle.bmc.core.requests.ListVolumeAttachmentsRequest;
import com.oracle.bmc.core.responses.ListBootVolumeAttachmentsResponse;
import com.oracle.bmc.core.responses.ListVolumeAttachmentsResponse;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ResourceInfo;

import java.util.ArrayList;
import java.util.List;

public class InstanceTopologyResolver implements AutoCloseable {

    private final ComputeClient computeClient;
    private final BlockstorageClient blockStorageClient;

    public InstanceTopologyResolver(OciAuthProvider auth) {
        this.computeClient = ComputeClient.builder().build(auth.getProvider());
        this.blockStorageClient = BlockstorageClient.builder().build(auth.getProvider());
    }

    /**
     * Devuelve los OCIDs de los discos adjuntos a la instancia.
     */
    public InstanceDisks disksFor(String instanceId, String compartmentId, String availabilityDomain) {
        ensureComputeRegion(instanceId);
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
     * Consulta el Compute API para obtener nombre, compartimento, AD, OCPUs y RAM.
     * Devuelve null si la instancia no existe o no hay permiso.
     */
    public InstanceDetail describeInstance(String instanceId) {
        ensureComputeRegion(instanceId);
        try {
            Instance inst = computeClient.getInstance(
                    GetInstanceRequest.builder().instanceId(instanceId).build())
                    .getInstance();
            if (inst == null) return null;
            InstanceShapeConfig sc = inst.getShapeConfig();
            Double ocpus = sc != null && sc.getOcpus() != null ? sc.getOcpus().doubleValue() : null;
            Double memoryGb = sc != null && sc.getMemoryInGBs() != null ? sc.getMemoryInGBs().doubleValue() : null;
            return new InstanceDetail(inst.getDisplayName(), inst.getCompartmentId(),
                    inst.getAvailabilityDomain(), ocpus, memoryGb);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Consulta el Block Storage API para obtener nombre y tamaño de un boot volume.
     * Devuelve null si no existe o no hay permiso.
     */
    public VolumeDetail describeBootVolume(String bootVolumeId) {
        ensureBlockStorageRegion(bootVolumeId);
        try {
            var bv = blockStorageClient.getBootVolume(
                    GetBootVolumeRequest.builder().bootVolumeId(bootVolumeId).build())
                    .getBootVolume();
            if (bv == null) return null;
            return new VolumeDetail(bv.getDisplayName(), bv.getSizeInGBs());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Consulta el Block Storage API para obtener nombre y tamaño de un block volume.
     * Devuelve null si no existe o no hay permiso.
     */
    public VolumeDetail describeVolume(String volumeId) {
        ensureBlockStorageRegion(volumeId);
        try {
            var v = blockStorageClient.getVolume(
                    GetVolumeRequest.builder().volumeId(volumeId).build())
                    .getVolume();
            if (v == null) return null;
            return new VolumeDetail(v.getDisplayName(), v.getSizeInGBs());
        } catch (Exception e) {
            return null;
        }
    }

    /** Consulta el Compute API por OCID para obtener nombre, compartimento y AD. */
    public ResourceInfo resolveInstanceInfo(String instanceId) {
        InstanceDetail d = describeInstance(instanceId);
        if (d == null) return null;
        return new ResourceInfo(instanceId, d.displayName(), "Instance",
                d.compartmentId(), d.availabilityDomain());
    }

    @Override
    public void close() {
        computeClient.close();
        blockStorageClient.close();
    }

    private static String regionFromOcid(String ocid) {
        String[] parts = ocid.split("\\.");
        return parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
    }

    private void ensureComputeRegion(String ocid) {
        String regionId = regionFromOcid(ocid);
        if (regionId != null) {
            computeClient.setRegion(Region.fromRegionId(regionId));
        }
    }

    private void ensureBlockStorageRegion(String ocid) {
        String regionId = regionFromOcid(ocid);
        if (regionId != null) {
            blockStorageClient.setRegion(Region.fromRegionId(regionId));
        }
    }

    /** OCIDs de discos adjuntos a una instancia, separados por tipo. */
    public record InstanceDisks(List<String> bootVolumeIds, List<String> blockVolumeIds) {}

    /** Datos de una instancia obtenidos del Compute API. */
    public record InstanceDetail(String displayName, String compartmentId,
                                 String availabilityDomain, Double ocpus, Double memoryGb) {}

    /** Nombre y tamaño de un volumen obtenidos del Block Storage API. */
    public record VolumeDetail(String name, Long sizeGb) {}
}