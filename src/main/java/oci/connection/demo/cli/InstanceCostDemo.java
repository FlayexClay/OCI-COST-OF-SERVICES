package oci.connection.demo.cli;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.DiskCost;
import oci.connection.demo.dto.InstanceCost;
import oci.connection.demo.report.InstanceCostService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class InstanceCostDemo {

    private static final String ROW  = "    %-24s%-12s%7.2f%n";
    private static final String SEP  = "    " + "-".repeat(43);
    private static final String TOT  = "    %-36s%7.2f %s%n";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        String nameFilter = args.length > 0 ? String.join(" ", args).trim() : null;

        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1);

        System.out.println("=================================================");
        if (nameFilter != null) {
            System.out.println(" Costo por instancia (filtro: \"" + nameFilter + "\") - Dolares");
        } else {
            System.out.println(" Costo por instancia (todas) - Dolares");
        }
        System.out.println("=================================================");
        System.out.printf("Periodo: %s \u2192 %s (mes a la fecha)%n%n",
                first.format(DATE_FMT), today.format(DATE_FMT));

        try {
            OciAuthProvider auth = new OciAuthProvider();

            try (InstanceCostService service = new InstanceCostService(auth)) {
                System.out.println("Consultando costos, recursos y attachments...\n");

                List<InstanceCost> instances = (nameFilter != null)
                        ? service.monthToDateForName(nameFilter)
                        : service.monthToDateAll();

                if (instances.isEmpty()) {
                    System.out.println(nameFilter != null
                            ? "No se encontro ninguna instancia que coincida con \"" + nameFilter + "\"."
                            : "No se encontraron instancias con costo en el periodo.");
                    return;
                }

                for (InstanceCost ic : instances) {
                    print(ic);
                }
            }

        } catch (BmcException e) {
            System.err.println("Fallo una llamada a OCI (HTTP " + e.getStatusCode() + ")");
            System.err.println("   " + e.getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                System.err.println("   Pista: revisa permisos. Necesitas leer costos (usage-report),");
                System.err.println("          inspeccionar recursos y leer Compute (instance-family, volume-family).");
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void print(InstanceCost ic) {
        String name = (ic.name() != null && !ic.name().isBlank()) ? ic.name() : "(instancia eliminada)";
        System.out.println(name + " \u2014 Instancia");

        // CPU
        String cpuQty = ic.ocpus() != null ? fmtDouble(ic.ocpus()) + " OCPU" : "-";
        System.out.printf(ROW, "CPU", cpuQty, ic.processorCost().doubleValue());

        // RAM
        String ramQty = ic.memoryGb() != null ? fmtDouble(ic.memoryGb()) + " GB" : "-";
        System.out.printf(ROW, "RAM", ramQty, ic.memoryCost().doubleValue());

        // Discos
        for (DiskCost d : ic.disks()) {
            String dn = (d.name() != null && !d.name().isBlank()) ? d.name() : shortOcid(d.volumeId());
            String label = dn + " (" + d.kind() + ")";
            String sizeQty = d.sizeGb() != null ? d.sizeGb() + " GB" : "-";
            System.out.printf(ROW, label, sizeQty, d.cost().doubleValue());
        }

        System.out.println(SEP);
        System.out.printf(TOT, "Total", ic.totalCost().setScale(2, RoundingMode.HALF_UP).doubleValue(),
                ic.currency());
        System.out.println();
    }

    /** Formato sin decimal si es entero, con un decimal si no lo es. */
    private static String fmtDouble(double v) {
        long lv = Math.round(v);
        return (v == lv) ? String.valueOf(lv) : String.format("%.1f", v);
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