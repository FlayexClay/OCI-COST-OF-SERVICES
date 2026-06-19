package oci.connection.demo.cli;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ComponentCost;
import oci.connection.demo.dto.ResourceNode;
import oci.connection.demo.report.ExcelReportWriter;
import oci.connection.demo.report.ResourceReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reporte de costos mes-a-la-fecha agrupado por recurso. Cubre todos los tipos OCI
 * con describidor (Compute, Block Storage, Autonomous DB) y el resto en modo genérico.
 *
 * <p>Sin argumentos → todos los recursos. Con argumento → filtra por nombre.</p>
 */
public class ResourceCostDemo {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        String nameFilter = args.length > 0 ? String.join(" ", args).trim() : null;

        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1);

        System.out.println("=================================================");
        if (nameFilter != null) {
            System.out.println(" Costo por recurso (filtro: \"" + nameFilter + "\") - Dolares");
        } else {
            System.out.println(" Costo por recurso (todos) - Dolares");
        }
        System.out.println("=================================================");
        System.out.printf("Periodo: %s \u2192 %s (mes a la fecha)%n%n",
                first.format(DATE_FMT), today.format(DATE_FMT));

        try {
            OciAuthProvider auth = new OciAuthProvider();

            try (ResourceReportService service = new ResourceReportService(auth)) {
                System.out.println("Consultando costos, recursos y attachments...\n");

                List<ResourceNode> nodes = (nameFilter != null)
                        ? service.monthToDateForName(nameFilter)
                        : service.monthToDateAll();

                if (nodes.isEmpty()) {
                    System.out.println(nameFilter != null
                            ? "No se encontro ningun recurso que coincida con \"" + nameFilter + "\"."
                            : "No se encontraron recursos con costo en el periodo.");
                    return;
                }

                for (ResourceNode node : nodes) {
                    printNode(node, "");
                }

                String excelFile = ExcelReportWriter.write(nodes, first, today, nameFilter);
                System.out.println("\nExcel generado: " + excelFile);
            }

        } catch (BmcException e) {
            System.err.println("Fallo una llamada a OCI (HTTP " + e.getStatusCode() + ")");
            System.err.println("   " + e.getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                System.err.println("   Pista: revisa permisos (usage-report, instance-family,");
                System.err.println("          volume-family, database-family).");
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printNode(ResourceNode node, String indent) {
        String name = (node.name() != null && !node.name().isBlank())
                ? node.name() : shortOcid(node.resourceId());
        String type = node.type() != null ? " \u2014 " + node.type() : "";
        System.out.println(indent + name + type);

        for (ComponentCost c : node.components()) {
            String qty = formatQty(c.quantity(), c.unit());
            System.out.printf("%s    %-24s%-12s%7.2f%n",
                    indent, c.label(), qty, c.cost().doubleValue());
        }

        for (ResourceNode child : node.children()) {
            printNode(child, indent + "    ");
        }

        String sep = indent + "    " + "-".repeat(43);
        System.out.println(sep);
        System.out.printf("%s    %-36s%7.2f %s%n",
                indent, "Total",
                node.totalCost().setScale(2, RoundingMode.HALF_UP).doubleValue(),
                node.currency());
        System.out.println();
    }

    private static String formatQty(Double quantity, String unit) {
        if (quantity == null) return "-";
        String num;
        long lv = Math.round(quantity);
        num = (quantity == lv) ? String.valueOf(lv) : String.format("%.1f", quantity);
        return unit != null ? num + " " + unit : num;
    }

    private static String shortOcid(String ocid) {
        if (ocid == null || ocid.isBlank()) return "(sin id)";
        int first = ocid.indexOf('.');
        int second = first >= 0 ? ocid.indexOf('.', first + 1) : -1;
        String type = second > 0 ? ocid.substring(0, second) : ocid;
        String tail = ocid.length() > 12 ? ocid.substring(ocid.length() - 12) : ocid;
        return type + "..." + tail;
    }
}