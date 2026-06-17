package oci.connection.demo.auth;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

import java.io.IOException;

public class OciAuthProvider {

    private final ConfigFileAuthenticationDetailsProvider provider;

    public OciAuthProvider () throws IOException {
       this(ConfigFileReader.parseDefault());
    }

    public OciAuthProvider(String configPath, String profile) throws IOException{
        this(ConfigFileReader.parse(configPath, profile));
    }

    public OciAuthProvider(ConfigFileReader.ConfigFile configFile) {
        this.provider = new ConfigFileAuthenticationDetailsProvider(configFile);
    }

    public ConfigFileAuthenticationDetailsProvider getProvider() {
        return provider;
    }

    public String getTenantId() {
        return provider.getTenantId();
    }

    public String getRegionId() {
        return provider.getRegion() != null ? provider.getRegion().getRegionId() : "n/d";
    }


}
