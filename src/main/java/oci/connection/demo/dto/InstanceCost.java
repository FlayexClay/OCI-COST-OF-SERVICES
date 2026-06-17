package oci.connection.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record InstanceCost(String instanceId, String name, BigDecimal processorCost, BigDecimal memoryCost,
                           List<DiskCost> disks, String currency) {

    public BigDecimal totalCost() {
        BigDecimal diskTotal = disks.stream()
                .map(DiskCost::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return processorCost.add(memoryCost).add(diskTotal);
    }
}
