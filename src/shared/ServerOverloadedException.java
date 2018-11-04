package shared;

public class ServerOverloadedException extends Exception {
    public ServerOverloadedException() { super(); }
    public ServerOverloadedException(String message) { super(message); }
}