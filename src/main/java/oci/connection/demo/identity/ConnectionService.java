package oci.connection.demo.identity;

import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.identity.requests.GetUserRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.responses.GetUserResponse;
import com.oracle.bmc.identity.responses.ListRegionSubscriptionsResponse;
import oci.connection.demo.auth.OciAuthProvider;
import oci.connection.demo.dto.ConnectionResult;

import java.util.List;
import java.util.stream.Collectors;

public class ConnectionService implements AutoCloseable{

    private final OciAuthProvider auth;
    private final IdentityClient identityClient;

    public ConnectionService(OciAuthProvider auth) {
        this.auth = auth;
        this.identityClient = IdentityClient.builder().build(auth.getProvider());
    }

    public ConnectionResult verify() {
        GetUserResponse userResp = identityClient.getUser(
                GetUserRequest.builder()
                        .userId(auth.getProvider().getUserId())
                        .build()
        );

        ListRegionSubscriptionsResponse regionResp = identityClient.listRegionSubscriptions(
                ListRegionSubscriptionsRequest.builder()
                        .tenancyId(auth.getTenantId())
                        .build()
        );

        List<RegionSubscription> subs = regionResp.getItems();
        String regiones = subs.stream()
                .map(RegionSubscription::getRegionName)
                .collect(Collectors.joining(", "));

        return new ConnectionResult(
                userResp.getUser().getName(),
                String.valueOf(userResp.getUser().getLifecycleState()),
                regiones
        );
    }

    public String getTenantId() {
        return auth.getTenantId();
    }

    public String getRegionId() {
        return auth.getRegionId();
    }


    @Override
    public void close() throws Exception {
        identityClient.close();
    }

}
