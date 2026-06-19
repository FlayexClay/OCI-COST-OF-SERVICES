package oci.connection.demo.report;

import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.cost.CostReporter;
import oci.connection.demo.describe.AutonomousDbDescriber;
import oci.connection.demo.describe.InstanceDescriber;
import oci.connection.demo.describe.ResourceDescriber;
import oci.connection.demo.describe.ResourceDescriberRegistry;
import oci.connection.demo.describe.VolumeDescriber;
import oci.connection.demo.dto.ComponentCost;
import oci.connection.demo.dto.ComponentSpec;
import oci.connection.demo.dto.CostComponent;
import oci.connection.demo.dto.ResourceCostLine;
import oci.connection.demo.dto.ResourceDescription;
import oci.connection.demo.dto.ResourceInfo;
import oci.connection.demo.dto.ResourceNode;
import oci.connection.demo.naming.ResourceNameResolver;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Servicio de reporte híbrido: usa describidores para los tipos conocidos y cae en un
 * modo genérico (por SKU) para el resto. Cubre todos los recursos con costo, no solo
 * instancias.
 */
public class ResourceReportService implements AutoCloseable {

    private static final String INSTANCE_PREFIX = "ocid1.instance.";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CostReporter costReporter;
    private final ResourceNameResolver nameResolver;
    private final ResourceDescriberRegistry registry;

    public ResourceReportService(OciAuthProvider auth) {
        this.costReporter = new CostReporter(auth);
        this.nameResolver = new ResourceNameResolver(auth);
        this.registry = new ResourceDescriberRegistry(List.of(
                new InstanceDescriber(auth),
                new VolumeDescriber(auth),
                new AutonomousDbDescriber(auth)
        ));
    }

    /** Todos los recursos de nivel superior con costo mes a la fecha, de mayor a menor. */
    public List<ResourceNode> monthToDateAll() {
        return buildTree(null);
    }

    /** Recursos de nivel superior cuyo nombre contenga el filtro (insensible a mayúsculas). */
    public List<ResourceNode> monthToDateForName(String nameFilter) {
        return buildTree(nameFilter == null ? null : nameFilter.toLowerCase());
    }

    // -------------------------------------------------------------------------

    private List<ResourceNode> buildTree(String nameFilterLower) {
        List<ResourceCostLine> lines = costReporter.monthToDateByResourceComponent();
        CostData costs = buildCostData(lines);

        Map<String, ResourceInfo> catalog = nameResolver.allResources();
        // Suplementar catálogo con nombres de la Usage API
        for (ResourceCostLine l : lines) {
            if (l.resourceId() != null && l.resourceName() != null
                    && !l.resourceName().isBlank() && !catalog.containsKey(l.resourceId())) {
                catalog.put(l.resourceId(), new ResourceInfo(
                        l.resourceId(), l.resourceName(), null, null, null));
            }
        }

        // Candidatos iniciales: recursos con costo + recursos del catálogo soportados por algún describidor
        Set<String> allIds = new LinkedHashSet<>();
        costs.total().keySet().stream().filter(Objects::nonNull).forEach(allIds::add);
        catalog.keySet().stream().filter(id -> registry.finderFor(id) != null).forEach(allIds::add);

        // Describir todos (BFS para descubrir hijos de hijos)
        Map<String, ResourceDescription> descriptions = new HashMap<>();
        Set<String> claimed = new HashSet<>();
        Queue<String> toProcess = new ArrayDeque<>(allIds);
        Set<String> processed = new HashSet<>();

        while (!toProcess.isEmpty()) {
            String id = toProcess.poll();
            if (!processed.add(id)) continue;

            ResourceDescriber describer = registry.finderFor(id);
            if (describer != null) {
                ResourceDescription desc = describer.describe(id);
                if (desc != null) {
                    descriptions.put(id, desc);
                    for (String childId : desc.childIds()) {
                        claimed.add(childId);
                        allIds.add(childId);
                        if (!processed.contains(childId)) toProcess.add(childId);
                    }
                }
            }
        }

        // Nivel superior = candidatos no reclamados como hijos de otro
        List<ResourceNode> roots = new ArrayList<>();
        for (String id : allIds) {
            if (claimed.contains(id)) continue;
            ResourceNode node = buildNode(id, descriptions, costs, catalog);
            if (node.totalCost().signum() <= 0) continue;
            if (nameFilterLower != null) {
                String name = node.name() != null ? node.name().toLowerCase() : "";
                if (!name.contains(nameFilterLower)) continue;
            }
            roots.add(node);
        }
        roots.sort((a, b) -> b.totalCost().compareTo(a.totalCost()));
        return roots;
    }

    private ResourceNode buildNode(String id, Map<String, ResourceDescription> descriptions,
                                   CostData costs, Map<String, ResourceInfo> catalog) {
        ResourceDescription desc = descriptions.get(id);
        String name = resolvedName(id, desc, catalog);
        String type = desc != null ? desc.type() : null;

        List<ComponentCost> components;
        List<ResourceNode> children = new ArrayList<>();

        if (desc != null) {
            // Costos por componente
            BigDecimal p = costs.proc().getOrDefault(id, ZERO);
            BigDecimal m = costs.mem().getOrDefault(id, ZERO);
            BigDecimal t = costs.total().getOrDefault(id, ZERO);
            // Regla de shape estándar: si no hay desglose proc/mem, el total es el procesador
            if (id.startsWith(INSTANCE_PREFIX) && p.signum() == 0 && m.signum() == 0) {
                p = t;
            }
            BigDecimal storage = t.subtract(p).subtract(m);

            components = new ArrayList<>();
            for (ComponentSpec spec : desc.components()) {
                BigDecimal cost = switch (spec.label()) {
                    case "CPU" -> p;
                    case "RAM" -> m;
                    case "Almacenamiento" -> storage;
                    default -> ZERO;
                };
                components.add(new ComponentCost(spec.label(), spec.quantity(), spec.unit(), cost));
            }

            // Hijos recursivos
            for (String childId : desc.childIds()) {
                ResourceNode child = buildNode(childId, descriptions, costs, catalog);
                if (child.totalCost().signum() > 0) children.add(child);
            }
        } else {
            // Recurso sin describidor: componentes por SKU sin cantidad
            components = new ArrayList<>();
            Map<String, BigDecimal> skuMap = costs.bySku().getOrDefault(id, Map.of());
            for (Map.Entry<String, BigDecimal> e : skuMap.entrySet()) {
                if (e.getValue().signum() > 0)
                    components.add(new ComponentCost(e.getKey(), null, null, e.getValue()));
            }
        }

        return new ResourceNode(id, name, type, components, children, costs.currency());
    }

    private String resolvedName(String id, ResourceDescription desc, Map<String, ResourceInfo> catalog) {
        if (desc != null && desc.name() != null && !desc.name().isBlank()) return desc.name();
        ResourceInfo info = catalog.get(id);
        if (info != null && info.displayName() != null && !info.displayName().isBlank())
            return info.displayName();
        return null;
    }

    private CostData buildCostData(List<ResourceCostLine> lines) {
        Map<String, BigDecimal> total = new HashMap<>();
        Map<String, BigDecimal> proc  = new HashMap<>();
        Map<String, BigDecimal> mem   = new HashMap<>();
        Map<String, Map<String, BigDecimal>> bySku = new HashMap<>();
        String currency = "";

        for (ResourceCostLine l : lines) {
            if (currency.isEmpty() && l.currency() != null && !l.currency().isBlank())
                currency = l.currency();
            String rid = l.resourceId();
            if (rid == null) continue;

            total.merge(rid, l.amount(), BigDecimal::add);

            if (l.component() == CostComponent.PROCESSOR) {
                proc.merge(rid, l.amount(), BigDecimal::add);
            } else if (l.component() == CostComponent.MEMORY) {
                mem.merge(rid, l.amount(), BigDecimal::add);
            }

            if (l.skuName() != null) {
                bySku.computeIfAbsent(rid, k -> new HashMap<>())
                     .merge(l.skuName(), l.amount(), BigDecimal::add);
            }
        }

        return new CostData(total, proc, mem, bySku, currency);
    }

    @Override
    public void close() throws Exception {
        costReporter.close();
        nameResolver.close();
        registry.close();
    }

    private record CostData(Map<String, BigDecimal> total,
                            Map<String, BigDecimal> proc,
                            Map<String, BigDecimal> mem,
                            Map<String, Map<String, BigDecimal>> bySku,
                            String currency) {}
}