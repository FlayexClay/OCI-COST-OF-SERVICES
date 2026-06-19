package oci.connection.demo.describe;

import com.oracle.bmc.Region;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.BootVolumeAttachment;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceShapeConfig;
import com.oracle.bmc.core.model.VolumeAttachment;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.ListBootVolumeAttachmentsRequest;
import com.oracle.bmc.core.requests.ListVolumeAttachmentsRequest;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ComponentSpec;
import oci.connection.demo.dto.ResourceDescription;

import java.util.ArrayList;
import java.util.List;

/** Describe una instancia de Compute: OCPUs, RAM y volúmenes adjuntos. */
public class InstanceDescriber implements ResourceDescriber {

    private static final String PREFIX = "ocid1.instance.";
    private final ComputeClient computeClient;

    public InstanceDescriber(OciAuthProvider auth) {
        this.computeClient = ComputeClient.builder().build(auth.getProvider());
    }

    @Override
    public boolean supports(String ocid) {
        return ocid != null && ocid.startsWith(PREFIX);
    }

    @Override
    public ResourceDescription describe(String ocid) {
        setRegion(ocid);
        try {
            Instance inst = computeClient.getInstance(
                    GetInstanceRequest.builder().instanceId(ocid).build()).getInstance();
            if (inst == null) return null;

            InstanceShapeConfig sc = inst.getShapeConfig();
            Double ocpus = sc != null && sc.getOcpus() != null ? sc.getOcpus().doubleValue() : null;
            Double memGb = sc != null && sc.getMemoryInGBs() != null ? sc.getMemoryInGBs().doubleValue() : null;

            List<ComponentSpec> components = List.of(
                    new ComponentSpec("CPU", ocpus, "OCPU"),
                    new ComponentSpec("RAM", memGb, "GB")
            );

            List<String> childIds = new ArrayList<>();
            String compartmentId = inst.getCompartmentId();
            String ad = inst.getAvailabilityDomain();

            if (compartmentId != null && ad != null) {
                computeClient.listBootVolumeAttachments(
                                ListBootVolumeAttachmentsRequest.builder()
                                        .availabilityDomain(ad)
                                        .compartmentId(compartmentId)
                                        .instanceId(ocid)
                                        .build())
                        .getItems()
                        .forEach(a -> {
                            if (a.getBootVolumeId() != null && !childIds.contains(a.getBootVolumeId()))
                                childIds.add(a.getBootVolumeId());
                        });
            }

            if (compartmentId != null) {
                computeClient.listVolumeAttachments(
                                ListVolumeAttachmentsRequest.builder()
                                        .compartmentId(compartmentId)
                                        .instanceId(ocid)
                                        .build())
                        .getItems()
                        .forEach(a -> {
                            if (a.getVolumeId() != null && !childIds.contains(a.getVolumeId()))
                                childIds.add(a.getVolumeId());
                        });
            }

            return new ResourceDescription(inst.getDisplayName(), "Instancia", components, childIds);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        computeClient.close();
    }

    private void setRegion(String ocid) {
        String regionId = regionFromOcid(ocid);
        if (regionId != null) computeClient.setRegion(Region.fromRegionId(regionId));
    }

    static String regionFromOcid(String ocid) {
        if (ocid == null) return null;
        String[] parts = ocid.split("\\.");
        return parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
    }
}