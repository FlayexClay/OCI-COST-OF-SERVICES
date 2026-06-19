package oci.connection.demo.dto;

import java.math.BigDecimal;
import java.util.List;

/** Nodo del árbol de costos: recurso con sus componentes y recursos hijos. */
public record ResourceNode(String resourceId, String name, String type,
                           List<ComponentCost> components,
                           List<ResourceNode> children,
                           String currency) {

    public BigDecimal totalCost() {
        BigDecimal own = components.stream()
                .map(ComponentCost::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal childTotal = children.stream()
                .map(ResourceNode::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return own.add(childTotal);
    }
}