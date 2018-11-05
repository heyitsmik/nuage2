package shared;

import java.io.Serializable;

import shared.*;

public class ServerConfig implements Serializable {

    private String SERVER_HOSTNAME;
	private int OPERATION_CAPACITY;
	private int PORT;

    public ServerConfig(String serverHostname, int operationCapacity, int port) {
        SERVER_HOSTNAME = serverHostname;
        OPERATION_CAPACITY = operationCapacity;
        PORT = port;
    }

    public String getServerHostname() {
        return SERVER_HOSTNAME;
    }

    public int getOperationCapacity() {
        return OPERATION_CAPACITY;
    }

    public int getPort() {
        return PORT;
    }

}
