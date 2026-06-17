package oci.connection.demo.test;

import com.oracle.bmc.model.BmcException;
import oci.connection.demo.OciConnection;
import oci.connection.demo.dto.ConnectionResult;

public class ConnectionDemo {
    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" Demostración de conexión a OCI (Aenza)");
        System.out.println("==========================================");

        try (OciConnection conn = new OciConnection()) {

            System.out.println("Tenancy : " + conn.getTenantId());
            System.out.println("Región  : " + conn.getRegionId());
            System.out.println("Autenticando y consultando la identidad en OCI...\n");

            ConnectionResult r = conn.verify();

            System.out.println("✅ CONEXIÓN EXITOSA");
            System.out.println("------------------------------------------");
            System.out.println("Usuario autenticado : " + r.userName());
            System.out.println("Estado del usuario  : " + r.userState());
            System.out.println("Regiones suscritas  : " + r.subscribedRegions());
            System.out.println("------------------------------------------");

        } catch (BmcException e) {
            // Errores que devuelve el servicio OCI (problemas de credenciales/permisos).
            System.err.println("❌ FALLÓ LA LLAMADA A OCI (HTTP " + e.getStatusCode() + ")");
            System.err.println("   Mensaje: " + e.getMessage());
            switch (e.getStatusCode()) {
                case 401 -> System.err.println("   Pista: revisa fingerprint, key_file (llave privada) y user OCID en ~/.oci/config.");
                case 404 -> System.err.println("   Pista: revisa que el user OCID y el tenancy OCID sean correctos.");
                case 429 -> System.err.println("   Pista: demasiadas solicitudes; reintenta en unos segundos.");
                default  -> System.err.println("   Pista: verifica permisos (políticas IAM) y conectividad de red/VPN.");
            }
            System.exit(1);

        } catch (Exception e) {
            // Errores antes de llegar a OCI: config no encontrada, llave ilegible, sin red.
            System.err.println("❌ ERROR DE CONFIGURACIÓN O ENTORNO");
            System.err.println("   " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.err.println("   Pista: confirma que existe ~/.oci/config, que key_file apunta a la llave");
            System.err.println("          privada correcta y que tienes acceso de red a OCI (VPN si aplica).");
            System.exit(1);
        }
    }
}
