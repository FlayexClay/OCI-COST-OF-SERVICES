package oci.connection.demo.cli;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.cost.CostReporter;
import oci.connection.demo.dto.CostLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class CostReportDemo {
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println(" Reporte de costos OCI (mes en curso) - Aenza");
        System.out.println("===============================================");

        try {
            OciAuthProvider auth = new OciAuthProvider();

            try (CostReporter reporter = new CostReporter(auth)) {
                System.out.println("Tenancy : " + reporter.getTenantId());
                System.out.println("Consultando la Usage API...\n");

                List<CostLine> lines = reporter.monthToDateByService();

                if (lines.isEmpty()) {
                    System.out.println("Sin costos en el período (mes recién iniciado o sin consumo aún).");
                    return;
                }

                lines.sort((a, b) -> b.amount().compareTo(a.amount()));

                String currency = lines.stream()
                        .map(CostLine::currency)
                        .filter(c -> c != null && !c.isBlank())
                        .findFirst()
                        .orElse("");

                BigDecimal total = BigDecimal.ZERO;
                System.out.printf("%-38s %14s%n", "SERVICIO", "COSTO");
                System.out.println("-----------------------------------------------------");
                for (CostLine line : lines) {
                    System.out.printf("%-38s %14s%n",
                            line.service(),
                            line.amount().setScale(2, RoundingMode.HALF_UP));
                    total = total.add(line.amount());
                }
                System.out.println("-----------------------------------------------------");
                System.out.printf("%-38s %14s %s%n",
                        "TOTAL", total.setScale(2, RoundingMode.HALF_UP), currency);
            }

        } catch (BmcException e) {
            System.err.println("❌ Falló la consulta a la Usage API (HTTP " + e.getStatusCode() + ")");
            System.err.println("   " + e.getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                System.err.println("   Pista: tu usuario necesita permiso para leer costos. Política requerida:");
                System.err.println("          Allow group <tu-grupo> to read usage-report in tenancy");
            }
            System.exit(1);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
