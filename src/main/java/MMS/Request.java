package MMS;

public class Request {
    private final int variableId;
    private final int startAddress;
    private final int sizeB;
    private final long timestamp;

    public Request(int variableId, int startAddress, int sizeB) {
        this.variableId = variableId;
        this.startAddress = startAddress;
        this.sizeB = sizeB;
        this.timestamp = System.nanoTime();
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

    public long getTimestamp() {
        return timestamp;
    }

}