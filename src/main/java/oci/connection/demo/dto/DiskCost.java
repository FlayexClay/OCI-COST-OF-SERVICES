package oci.connection.demo.dto;

import java.math.BigDecimal;

public record DiskCost(String volumeId, String name, String kind, BigDecimal cost) {
}
