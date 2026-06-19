package oci.connection.demo.describe;

import com.oracle.bmc.Region;
import com.oracle.bmc.database.DatabaseClient;
import com.oracle.bmc.database.model.AutonomousDatabase;
import com.oracle.bmc.database.requests.GetAutonomousDatabaseRequest;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ComponentSpec;
import oci.connection.demo.dto.ResourceDescription;

import java.util.ArrayList;
import java.util.List;

/** Describe una Autonomous Database: CPU (OCPU o ECPU) y almacenamiento en TB. */
public class AutonomousDbDescriber implements ResourceDescriber {

    private static final String PREFIX = "ocid1.autonomousdatabase.";
    private final DatabaseClient databaseClient;

    public AutonomousDbDescriber(OciAuthProvider auth) {
        this.databaseClient = DatabaseClient.builder().build(auth.getProvider());
    }

    @Override
    public boolean supports(String ocid) {
        return ocid != null && ocid.startsWith(PREFIX);
    }

    @Override
    public ResourceDescription describe(String ocid) {
        setRegion(ocid);
        try {
            AutonomousDatabase adb = databaseClient.getAutonomousDatabase(
                    GetAutonomousDatabaseRequest.builder().autonomousDatabaseId(ocid).build())
                    .getAutonomousDatabase();
            if (adb == null) return null;

            Double cpuCount;
            String cpuUnit;
            // OCPU-based vs ECPU-based Serverless
            if (adb.getCpuCoreCount() != null && adb.getCpuCoreCount() > 0) {
                cpuCount = adb.getCpuCoreCount().doubleValue();
                cpuUnit  = "OCPU";
            } else if (adb.getComputeCount() != null) {
                cpuCount = adb.getComputeCount().doubleValue();
                cpuUnit  = "ECPU";
            } else {
                cpuCount = null;
                cpuUnit  = "OCPU";
            }

            Double storageTb = adb.getDataStorageSizeInTBs() != null
                    ? adb.getDataStorageSizeInTBs().doubleValue() : null;

            List<ComponentSpec> components = new ArrayList<>();
            components.add(new ComponentSpec("CPU", cpuCount, cpuUnit));
            components.add(new ComponentSpec("Almacenamiento", storageTb, "TB"));

            return new ResourceDescription(adb.getDisplayName(), "Autonomous Database",
                    components, List.of());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        databaseClient.close();
    }

    private void setRegion(String ocid) {
        String regionId = InstanceDescriber.regionFromOcid(ocid);
        if (regionId != null) databaseClient.setRegion(Region.fromRegionId(regionId));
    }
}