package shared;

import java.io.Serializable;

import shared.*;

public class ServerConfig implements Serializable {

    private String SERVER_HOSTNAME;
	private int OPERATION_CAPACITY;
	private float MALICIOUS_RATE;
	private int PORT;

    public ServerConfig(String serverHostname, int operationCapacity, float maliciousRate, int port) {
        SERVER_HOSTNAME = serverHostname;
        OPERATION_CAPACITY = operationCapacity;
        MALICIOUS_RATE = maliciousRate;
        PORT = port;
    }

    public String getServerHostname() {
        return SERVER_HOSTNAME;
    }

    public int getOperationCapacity() {
        return OPERATION_CAPACITY;
    }

    public float getMaliciousRate() {
        return MALICIOUS_RATE;
    }

    public int getPort() {
        return PORT;
    }

}
