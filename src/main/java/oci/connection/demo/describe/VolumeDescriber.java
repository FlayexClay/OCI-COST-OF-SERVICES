package oci.connection.demo.describe;

import com.oracle.bmc.Region;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.requests.GetBootVolumeRequest;
import com.oracle.bmc.core.requests.GetVolumeRequest;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ComponentSpec;
import oci.connection.demo.dto.ResourceDescription;

import java.util.List;

/** Describe boot volumes y block volumes: tamaño en GB. */
public class VolumeDescriber implements ResourceDescriber {

    private static final String BOOT_PREFIX  = "ocid1.bootvolume.";
    private static final String BLOCK_PREFIX = "ocid1.volume.";
    private final BlockstorageClient blockStorageClient;

    public VolumeDescriber(OciAuthProvider auth) {
        this.blockStorageClient = BlockstorageClient.builder().build(auth.getProvider());
    }

    @Override
    public boolean supports(String ocid) {
        return ocid != null && (ocid.startsWith(BOOT_PREFIX) || ocid.startsWith(BLOCK_PREFIX));
    }

    @Override
    public ResourceDescription describe(String ocid) {
        setRegion(ocid);
        boolean isBoot = ocid.startsWith(BOOT_PREFIX);
        try {
            String name;
            Long sizeGb;
            String type;

            if (isBoot) {
                var bv = blockStorageClient.getBootVolume(
                        GetBootVolumeRequest.builder().bootVolumeId(ocid).build()).getBootVolume();
                if (bv == null) return null;
                name   = bv.getDisplayName();
                sizeGb = bv.getSizeInGBs();
                type   = "Boot volume";
            } else {
                var v = blockStorageClient.getVolume(
                        GetVolumeRequest.builder().volumeId(ocid).build()).getVolume();
                if (v == null) return null;
                name   = v.getDisplayName();
                sizeGb = v.getSizeInGBs();
                type   = "Block volume";
            }

            List<ComponentSpec> components = List.of(
                    new ComponentSpec("Almacenamiento", sizeGb != null ? sizeGb.doubleValue() : null, "GB")
            );
            return new ResourceDescription(name, type, components, List.of());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        blockStorageClient.close();
    }

    private void setRegion(String ocid) {
        String regionId = InstanceDescriber.regionFromOcid(ocid);
        if (regionId != null) blockStorageClient.setRegion(Region.fromRegionId(regionId));
    }
}