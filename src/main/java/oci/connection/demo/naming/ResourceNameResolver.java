package oci.connection.demo.naming;

import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.resourcesearch.ResourceSearchClient;
import com.oracle.bmc.resourcesearch.model.ResourceSummary;
import com.oracle.bmc.resourcesearch.model.SearchDetails;
import com.oracle.bmc.resourcesearch.model.StructuredSearchDetails;
import com.oracle.bmc.resourcesearch.requests.SearchResourcesRequest;
import com.oracle.bmc.resourcesearch.responses.SearchResourcesResponse;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ResourceInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceNameResolver implements AutoCloseable {

    private final OciAuthProvider auth;
    private final ResourceSearchClient searchClient;

    public ResourceNameResolver(OciAuthProvider auth) {
        this.auth = auth;
        this.searchClient = ResourceSearchClient.builder().build(auth.getProvider());
    }

    /** Catálogo OCID -> metadatos, recorriendo TODAS las regiones suscritas. */
    public Map<String, ResourceInfo> allResources() {
        Map<String, ResourceInfo> out = new HashMap<>();
        for (String regionId : subscribedRegions()) {
            try {
                searchClient.setRegion(regionId);   // Resource Search es regional
                collectRegion(out);
            } catch (Exception e) {
                // Una región sin permiso o no reconocida no debe romper el resto.
                System.err.println("(Aviso: no se pudo consultar la región " + regionId
                        + ": " + e.getMessage() + ")");
            }
        }
        return out;
    }

    private void collectRegion(Map<String, ResourceInfo> out) {
        String page = null;
        do {
            SearchResourcesRequest req = SearchResourcesRequest.builder()
                    .searchDetails(StructuredSearchDetails.builder()
                            .query("query all resources")
                            .matchingContextType(SearchDetails.MatchingContextType.None)
                            .build())
                    .limit(1000)
                    .page(page)
                    .build();

            SearchResourcesResponse resp = searchClient.searchResources(req);
            for (ResourceSummary r : resp.getResourceSummaryCollection().getItems()) {
                if (r.getIdentifier() == null) continue;
                out.putIfAbsent(r.getIdentifier(), new ResourceInfo(
                        r.getIdentifier(), r.getDisplayName(), r.getResourceType(),
                        r.getCompartmentId(), r.getAvailabilityDomain()));
            }
            page = resp.getOpcNextPage();
        } while (page != null);
    }

    private List<String> subscribedRegions() {
        try (IdentityClient identity = IdentityClient.builder().build(auth.getProvider())) {
            List<String> regions = new ArrayList<>();
            identity.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder()
                            .tenancyId(auth.getTenantId()).build())
                    .getItems().forEach(s -> regions.add(s.getRegionName()));
            return regions;
        }
    }

    public Map<String, String> allResourceNames() {
        Map<String, String> names = new HashMap<>();
        for (ResourceInfo i : allResources().values()) {
            if (i.displayName() != null) names.put(i.identifier(), i.displayName());
        }
        return names;
    }

    @Override
    public void close() {
        searchClient.close();
    }
}