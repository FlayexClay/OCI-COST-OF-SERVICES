package oci.connection.demo.dto;

import java.math.BigDecimal;

public record CostLine(String service, BigDecimal amount, String currency) {
}
