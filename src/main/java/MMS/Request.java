package MMS;

public class Request {
    private final int variableId;
    private final int startAddress;
    private final int sizeB;
    private final int timestamp;

    public Request(int variableId, int startAddress, int sizeB, int timestamp) {
        this.variableId = variableId;
        this.startAddress = startAddress;
        this.sizeB = sizeB;
        this.timestamp = timestamp;
    }

    public int getVariableId() {
        return variableId;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getRequestSizeB() {
        return sizeB;
    }

}