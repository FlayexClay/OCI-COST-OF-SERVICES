package oci.connection.demo.report;

import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.cost.CostReporter;
import oci.connection.demo.dto.CostComponent;
import oci.connection.demo.dto.DiskCost;
import oci.connection.demo.dto.InstanceCost;
import oci.connection.demo.dto.ResourceCostLine;
import oci.connection.demo.dto.ResourceInfo;
import oci.connection.demo.naming.ResourceNameResolver;
import oci.connection.demo.topology.InstanceTopologyResolver;
import oci.connection.demo.topology.InstanceTopologyResolver.InstanceDetail;
import oci.connection.demo.topology.InstanceTopologyResolver.InstanceDisks;
import oci.connection.demo.topology.InstanceTopologyResolver.VolumeDetail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstanceCostService implements AutoCloseable {
    private static final String INSTANCE_PREFIX = "ocid1.instance.";

    private final CostReporter costReporter;
    private final ResourceNameResolver nameResolver;
    private final InstanceTopologyResolver topologyResolver;

    public InstanceCostService(OciAuthProvider auth) {
        this.costReporter = new CostReporter(auth);
        this.nameResolver = new ResourceNameResolver(auth);
        this.topologyResolver = new InstanceTopologyResolver(auth);
    }

    /** Todas las instancias con costo en el mes en curso, de mayor a menor. */
    public List<InstanceCost> monthToDateAll() {
        Context c = load();
        Set<String> instanceIds = collectInstanceIds(c);
        List<InstanceCost> out = new ArrayList<>();
        for (String id : instanceIds) {
            ResourceInfo info = c.catalog().getOrDefault(id,
                    new ResourceInfo(id, null, "Instance", null, null));
            InstanceCost ic = build(info, c);
            if (ic.totalCost().signum() > 0) {
                out.add(ic);
            }
        }
        out.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
        return out;
    }

    /** Instancias cuyo nombre contiene el texto dado (sin distinguir mayúsculas). */
    public List<InstanceCost> monthToDateForName(String nameQuery) {
        Context c = load();
        String q = nameQuery == null ? "" : nameQuery.toLowerCase();
        Set<String> instanceIds = collectInstanceIds(c);
        List<InstanceCost> out = new ArrayList<>();
        for (String id : instanceIds) {
            ResourceInfo info = c.catalog().get(id);
            if (info == null) continue;
            String dn = info.displayName();
            if (dn == null || !dn.toLowerCase().contains(q)) continue;
            out.add(build(info, c));
        }
        out.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
        return out;
    }

    private Set<String> collectInstanceIds(Context c) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map.Entry<String, ResourceInfo> e : c.catalog().entrySet()) {
            if (isInstance(e.getValue())) ids.add(e.getKey());
        }
        for (String rid : c.total().keySet()) {
            if (rid != null && rid.startsWith(INSTANCE_PREFIX)) ids.add(rid);
        }
        return ids;
    }

    private InstanceCost build(ResourceInfo info, Context c) {
        String id = info.identifier();

        // describeInstance es fuente autoritativa: nombre, compartimento, AD, OCPUs, RAM.
        InstanceDetail detail = topologyResolver.describeInstance(id);
        Double ocpus = null;
        Double memoryGb = null;
        if (detail != null) {
            info = new ResourceInfo(id, detail.displayName(), "Instance",
                    detail.compartmentId(), detail.availabilityDomain());
            ocpus = detail.ocpus();
            memoryGb = detail.memoryGb();
        }

        BigDecimal proc = c.proc().getOrDefault(id, BigDecimal.ZERO);
        BigDecimal mem = c.mem().getOrDefault(id, BigDecimal.ZERO);
        // Standard shapes facturan en un solo SKU (OTHER), no se divide en OCPU/Memory.
        if (proc.signum() == 0 && mem.signum() == 0) {
            proc = c.total().getOrDefault(id, BigDecimal.ZERO);
        }

        InstanceDisks disks = topologyResolver.disksFor(
                id, info.compartmentId(), info.availabilityDomain());

        List<DiskCost> diskCosts = new ArrayList<>();
        for (String bootId : disks.bootVolumeIds()) {
            VolumeDetail vd = topologyResolver.describeBootVolume(bootId);
            String name = vd != null ? vd.name() : nameOf(bootId, c);
            Long sizeGb = vd != null ? vd.sizeGb() : null;
            diskCosts.add(new DiskCost(bootId, name, "boot", sizeGb,
                    c.total().getOrDefault(bootId, BigDecimal.ZERO)));
        }
        for (String blockId : disks.blockVolumeIds()) {
            VolumeDetail vd = topologyResolver.describeVolume(blockId);
            String name = vd != null ? vd.name() : nameOf(blockId, c);
            Long sizeGb = vd != null ? vd.sizeGb() : null;
            diskCosts.add(new DiskCost(blockId, name, "block", sizeGb,
                    c.total().getOrDefault(blockId, BigDecimal.ZERO)));
        }

        return new InstanceCost(id, info.displayName(), ocpus, memoryGb, proc, mem, diskCosts, c.currency());
    }

    private String nameOf(String ocid, Context c) {
        ResourceInfo info = c.catalog().get(ocid);
        return info != null ? info.displayName() : null;
    }

    private boolean isInstance(ResourceInfo info) {
        if (info.resourceType() != null && info.resourceType().equalsIgnoreCase("Instance")) {
            return true;
        }
        return info.identifier() != null && info.identifier().startsWith(INSTANCE_PREFIX);
    }

    private Context load() {
        List<ResourceCostLine> lines = costReporter.monthToDateByResourceComponent();
        Map<String, ResourceInfo> catalog = nameResolver.allResources();

        Map<String, BigDecimal> total = new HashMap<>();
        Map<String, BigDecimal> proc = new HashMap<>();
        Map<String, BigDecimal> mem = new HashMap<>();
        String currency = "";

        for (ResourceCostLine l : lines) {
            if (currency.isEmpty() && l.currency() != null && !l.currency().isBlank()) {
                currency = l.currency();
            }
            String rid = l.resourceId();
            if (rid == null) continue;
            if (l.resourceName() != null && !l.resourceName().isBlank()
                    && !catalog.containsKey(rid)) {
                catalog.put(rid, new ResourceInfo(rid, l.resourceName(), null, null, null));
            }
            total.merge(rid, l.amount(), BigDecimal::add);
            if (l.component() == CostComponent.PROCESSOR) {
                proc.merge(rid, l.amount(), BigDecimal::add);
            } else if (l.component() == CostComponent.MEMORY) {
                mem.merge(rid, l.amount(), BigDecimal::add);
            }
        }

        return new Context(catalog, total, proc, mem, currency);
    }

    @Override
    public void close() {
        costReporter.close();
        nameResolver.close();
        topologyResolver.close();
    }

    private record Context(Map<String, ResourceInfo> catalog,
                           Map<String, BigDecimal> total,
                           Map<String, BigDecimal> proc,
                           Map<String, BigDecimal> mem,
                           String currency) {}
}