package oci.connection.demo.cli;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.cost.CostReporter;
import oci.connection.demo.dto.CostComponent;
import oci.connection.demo.dto.ResourceCostLine;
import oci.connection.demo.naming.ResourceNameResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Punto de entrada: costo del mes en curso desglosado por componente
 * (procesador, memoria, boot volume, block volume) y por recurso.
 *
 * <p>Ejecútalo con:
 * {@code mvn compile exec:java -Dexec.mainClass=com.aenza.oci.cli.ResourceCostReportDemo}</p>
 */
public class ResourceCostReportDemo {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Costos OCI por recurso y componente - Aenza");
        System.out.println("=================================================");

        try {
            OciAuthProvider auth = new OciAuthProvider();

            try (CostReporter reporter = new CostReporter(auth)) {
                System.out.println("Tenancy : " + reporter.getTenantId());
                System.out.println("Consultando la Usage API...\n");

                List<ResourceCostLine> lines = reporter.monthToDateByResourceComponent();

                if (lines.isEmpty()) {
                    System.out.println("Sin costos en el período (mes recién iniciado o sin consumo aún).");
                    return;
                }

                String currency = lines.stream()
                        .map(ResourceCostLine::currency)
                        .filter(c -> c != null && !c.isBlank())
                        .findFirst()
                        .orElse("");

                // Mapa OCID -> nombre legible (no bloquea el reporte si falla).
                Map<String, String> names = loadNamesSafe(auth);

                printComponentTotals(lines, currency);
                printResourceDetail(lines, names);
            }

        } catch (BmcException e) {
            System.err.println("❌ Falló la consulta a la Usage API (HTTP " + e.getStatusCode() + ")");
            System.err.println("   " + e.getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                System.err.println("   Pista: necesitas la política  Allow group <tu-grupo> to read usage-report in tenancy");
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    /** Carga el mapa de nombres; si no hay permisos, devuelve vacío y avisa. */
    private static Map<String, String> loadNamesSafe(OciAuthProvider auth) {
        try (ResourceNameResolver resolver = new ResourceNameResolver(auth)) {
            return resolver.allResourceNames();
        } catch (Exception e) {
            System.out.println("(Aviso: no se pudieron resolver nombres de recursos: "
                    + e.getMessage() + ". Se mostrarán OCIDs.)\n");
            return Map.of();
        }
    }

    /** Suma los costos por componente y los imprime en orden fijo. */
    private static void printComponentTotals(List<ResourceCostLine> lines, String currency) {
        Map<CostComponent, BigDecimal> totals = new EnumMap<>(CostComponent.class);
        for (ResourceCostLine l : lines) {
            totals.merge(l.component(), l.amount(), BigDecimal::add);
        }

        System.out.println("TOTALES POR COMPONENTE");
        System.out.println("-------------------------------------------------");
        BigDecimal grand = BigDecimal.ZERO;
        for (CostComponent c : CostComponent.values()) {
            BigDecimal amt = totals.getOrDefault(c, BigDecimal.ZERO);
            if (amt.signum() == 0) {
                continue;
            }
            System.out.printf("%-22s %14s%n", c.label(), amt.setScale(2, RoundingMode.HALF_UP));
            grand = grand.add(amt);
        }
        System.out.println("-------------------------------------------------");
        System.out.printf("%-22s %14s %s%n", "TOTAL", grand.setScale(2, RoundingMode.HALF_UP), currency);
    }

    /** Agrupa por recurso y lista los componentes de cada uno, con su nombre. */
    private static void printResourceDetail(List<ResourceCostLine> lines, Map<String, String> names) {
        Map<String, List<ResourceCostLine>> byResource = lines.stream()
                .collect(Collectors.groupingBy(
                        l -> l.resourceId() == null ? "(sin id)" : l.resourceId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        System.out.println("\nDETALLE POR RECURSO");
        System.out.println("-------------------------------------------------");
        for (Map.Entry<String, List<ResourceCostLine>> e : byResource.entrySet()) {
            List<ResourceCostLine> rl = e.getValue();
            System.out.println(displayName(e.getKey(), rl.get(0).resourceName(), names));
            for (ResourceCostLine l : rl) {
                System.out.printf("   %-18s %14s   (%s)%n",
                        l.component().label(),
                        l.amount().setScale(2, RoundingMode.HALF_UP),
                        l.skuName() != null ? l.skuName() : "");
            }
        }
    }

    /**
     * Decide qué mostrar como encabezado del recurso, en orden de preferencia:
     * nombre del propio dato de costo, luego el del mapa de Resource Search,
     * y como último recurso el OCID acortado.
     */
    private static String displayName(String ocid, String costName, Map<String, String> names) {
        if (costName != null && !costName.isBlank()) {
            return costName;
        }
        String resolved = names.get(ocid);
        if (resolved != null && !resolved.isBlank()) {
            return resolved + "   [" + shortOcid(ocid) + "]";
        }
        return shortOcid(ocid);
    }

    /** Acorta un OCID largo para mostrarlo legible: "ocid1.instance...XXXXXXXX". */
    private static String shortOcid(String ocid) {
        if (ocid == null || ocid.isBlank()) {
            return "(sin id)";
        }
        int firstDot = ocid.indexOf('.');
        int secondDot = firstDot >= 0 ? ocid.indexOf('.', firstDot + 1) : -1;
        String type = secondDot > 0 ? ocid.substring(0, secondDot) : ocid;
        String tail = ocid.length() > 12 ? ocid.substring(ocid.length() - 12) : ocid;
        return type + "..." + tail;
    }
}