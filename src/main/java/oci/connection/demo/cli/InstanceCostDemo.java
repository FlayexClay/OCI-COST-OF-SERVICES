package oci.connection.demo.cli;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.DiskCost;
import oci.connection.demo.dto.InstanceCost;
import oci.connection.demo.report.InstanceCostService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Punto de entrada: costo por instancia, incluyendo procesador, memoria y sus
 * discos (boot y block volumes) con el costo de cada uno.
 *
 * <p>Dos modos en un solo main:</p>
 * <ul>
 *   <li>Sin argumentos → reporte completo (todas las instancias con costo).</li>
 *   <li>Con un nombre → solo las instancias cuyo nombre lo contenga.</li>
 * </ul>
 *
 * <p>Ejemplos:
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.aenza.oci.cli.InstanceCostDemo
 *   mvn compile exec:java -Dexec.mainClass=com.enza.oci.cli.InstanceCostDemo -Dexec.args="prod"
 * </pre></p>
 */
public class InstanceCostDemo {

    public static void main(String[] args) {
        String nameFilter = args.length > 0 ? String.join(" ", args).trim() : null;

        System.out.println("=================================================");
        if (nameFilter != null) {
            System.out.println(" Costo por instancia (filtro: \"" + nameFilter + "\") - Dolares");
        } else {
            System.out.println(" Costo por instancia (todas) - Dolares");
        }
        System.out.println("=================================================");

        try {
            OciAuthProvider auth = new OciAuthProvider();

            try (InstanceCostService service = new InstanceCostService(auth)) {
                System.out.println("Consultando costos, recursos y attachments...\n");

                List<InstanceCost> instances = (nameFilter != null)
                        ? service.monthToDateForName(nameFilter)
                        : service.monthToDateAll();

                if (instances.isEmpty()) {
                    System.out.println(nameFilter != null
                            ? "No se encontró ninguna instancia que coincida con \"" + nameFilter + "\"."
                            : "No se encontraron instancias con costo en el período.");
                    return;
                }

                for (InstanceCost ic : instances) {
                    print(ic);
                }
            }

        } catch (BmcException e) {
            System.err.println("❌ Falló una llamada a OCI (HTTP " + e.getStatusCode() + ")");
            System.err.println("   " + e.getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                System.err.println("   Pista: revisa permisos. Necesitas leer costos (usage-report),");
                System.err.println("          inspeccionar recursos y leer Compute (instance-family, volume-family).");
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static void print(InstanceCost ic) {
        String name = (ic.name() != null && !ic.name().isBlank())
                ? ic.name() : "(instancia eliminada)";

        System.out.println("Instancia: " + name);
        System.out.printf("  %-22s %12s%n", "Procesador (OCPU)", money(ic.processorCost()));
        System.out.printf("  %-22s %12s%n", "Memoria (RAM)", money(ic.memoryCost()));

        if (ic.disks().isEmpty()) {
            System.out.println("  Discos: (ninguno encontrado)");
        } else {
            System.out.println("  Discos:");
            for (DiskCost d : ic.disks()) {
                String dn = (d.name() != null && !d.name().isBlank())
                        ? d.name() : shortOcid(d.volumeId());
                System.out.printf("    %-30s %12s%n", dn + " (" + d.kind() + ")", money(d.cost()));
            }
        }
        System.out.println("  --------------------------------------------");
        System.out.printf("  %-22s %12s %s%n", "Total instancia", money(ic.totalCost()), ic.currency());
        System.out.println();
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

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