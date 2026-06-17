package oci.connection.demo.cost;

import com.oracle.bmc.usageapi.UsageapiClient;
import com.oracle.bmc.usageapi.model.RequestSummarizedUsagesDetails;
import com.oracle.bmc.usageapi.model.UsageSummary;
import com.oracle.bmc.usageapi.requests.RequestSummarizedUsagesRequest;
import com.oracle.bmc.usageapi.responses.RequestSummarizedUsagesResponse;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.CostComponent;
import oci.connection.demo.dto.CostLine;
import oci.connection.demo.dto.ResourceCostLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CostReporter implements AutoCloseable{

    private final OciAuthProvider auth;
    private final UsageapiClient usageClient;

    public CostReporter(OciAuthProvider auth) {
        this.auth = auth;
        this.usageClient = UsageapiClient.builder().build(auth.getProvider());
    }

    /** Costo del mes en curso (del día 1 hasta hoy) agrupado por servicio. */
    public List<CostLine> monthToDateByService() {
        Date[] w = monthToDateWindow();
        return byService(w[0], w[1]);
    }

    /** Costo del mes en curso desglosado por recurso y componente. */
    public List<ResourceCostLine> monthToDateByResourceComponent() {
        Date[] w = monthToDateWindow();
        return byResourceComponent(w[0], w[1]);
    }

    /** Ventana [inicio, fin) del mes en curso, alineada a medianoche UTC. */
    private Date[] monthToDateWindow() {
        LocalDate firstOfThisMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate firstOfNextMonth = firstOfThisMonth.plusMonths(1);
        // La Usage API exige fechas alineadas a medianoche UTC.
        return new Date[]{
                Date.from(firstOfThisMonth.atStartOfDay(ZoneOffset.UTC).toInstant()),
                Date.from(firstOfNextMonth.atStartOfDay(ZoneOffset.UTC).toInstant())
        };
    }

    /**
     * Costo agregado por servicio entre dos fechas (medianoche UTC).
     */
    public List<CostLine> byService(Date start, Date end) {
        RequestSummarizedUsagesDetails details = RequestSummarizedUsagesDetails.builder()
                .tenantId(auth.getTenantId())
                .timeUsageStarted(start)
                .timeUsageEnded(end)
                .granularity(RequestSummarizedUsagesDetails.Granularity.Monthly)
                .queryType(RequestSummarizedUsagesDetails.QueryType.Cost)
                .isAggregateByTime(true)
                .groupBy(List.of("service"))
                .build();

        RequestSummarizedUsagesResponse resp = usageClient.requestSummarizedUsages(
                RequestSummarizedUsagesRequest.builder()
                        .requestSummarizedUsagesDetails(details)
                        .build());

        List<CostLine> lines = new ArrayList<>();
        for (UsageSummary item : resp.getUsageAggregation().getItems()) {
            BigDecimal amount = item.getComputedAmount() != null
                    ? item.getComputedAmount()
                    : BigDecimal.ZERO;
            String service = item.getService() != null ? item.getService() : "(sin servicio)";
            lines.add(new CostLine(service, amount, item.getCurrency()));
        }
        return lines;
    }

    /**
     * Costo agregado por recurso y componente entre dos fechas.
     *
     * <p>Agrupa por resourceId, skuName y unit (3 de las 4 dimensiones que
     * permite la API) y clasifica cada línea con {@link CostComponent}.</p>
     */
    public List<ResourceCostLine> byResourceComponent(Date start, Date end) {
        RequestSummarizedUsagesDetails details = RequestSummarizedUsagesDetails.builder()
                .tenantId(auth.getTenantId())
                .timeUsageStarted(start)
                .timeUsageEnded(end)
                .granularity(RequestSummarizedUsagesDetails.Granularity.Monthly)
                .queryType(RequestSummarizedUsagesDetails.QueryType.Cost)
                .isAggregateByTime(true)
                .groupBy(List.of("resourceId", "skuName", "unit"))
                .build();

        RequestSummarizedUsagesResponse resp = usageClient.requestSummarizedUsages(
                RequestSummarizedUsagesRequest.builder()
                        .requestSummarizedUsagesDetails(details)
                        .build());

        List<ResourceCostLine> lines = new ArrayList<>();
        for (UsageSummary item : resp.getUsageAggregation().getItems()) {
            BigDecimal amount = item.getComputedAmount() != null
                    ? item.getComputedAmount()
                    : BigDecimal.ZERO;
            CostComponent component = CostComponent.classify(
                    item.getResourceId(), item.getSkuName(), item.getUnit());
            lines.add(new ResourceCostLine(
                    item.getResourceId(),
                    item.getResourceName(),
                    component,
                    item.getSkuName(),
                    amount,
                    item.getCurrency()));
        }
        return lines;
    }

    public String getTenantId() {
        return auth.getTenantId();
    }

    @Override
    public void close() {
        usageClient.close();
    }
}
