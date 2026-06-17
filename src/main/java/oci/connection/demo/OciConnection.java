package oci.connection.demo;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.identity.requests.GetUserRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.responses.GetUserResponse;
import com.oracle.bmc.identity.responses.ListRegionSubscriptionsResponse;
import oci.connection.demo.dto.ConnectionResult;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class OciConnection  implements AutoCloseable {

    private final ConfigFileAuthenticationDetailsProvider provider;
    private final IdentityClient identityClient;

    public OciConnection() throws IOException {
        this(ConfigFileReader.parseDefault());
    }

    public OciConnection(String configPath, String profile) throws IOException{
        this(ConfigFileReader.parse(configPath, profile));
    }

    public OciConnection(ConfigFileReader.ConfigFile configFile){
        this.provider = new ConfigFileAuthenticationDetailsProvider(configFile);
        this.identityClient = IdentityClient.builder().build(provider);
    }

    public ConnectionResult verify() {
        GetUserResponse userResp = identityClient.getUser(
                GetUserRequest.builder()
                        .userId(provider.getUserId())
                        .build()
        );

        ListRegionSubscriptionsResponse regionsResp = identityClient.listRegionSubscriptions(
                ListRegionSubscriptionsRequest.builder()
                        .tenancyId(provider.getTenantId())
                        .build()
        );

        List<RegionSubscription> subs = regionsResp.getItems();
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
        return provider.getTenantId();
    }

    public String getRegionId() {
     return provider.getRegion() != null ? provider.getRegion().getRegionId() : "n/d";
    }

    @Override
    public void close() throws Exception {

    }
}
