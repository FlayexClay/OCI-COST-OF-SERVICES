package oci.connection.demo.dto;

public record ResourceInfo(String identifier, String displayName, String resourceType, String compartmentId, String availabilityDomain) {
}
