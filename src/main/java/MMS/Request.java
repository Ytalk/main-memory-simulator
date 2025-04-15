package MMS;

public class Request {
    private final int variableId;
    private final int firstVirtualPage;
    private final int sizeB;
    private final int pagesAllocated;
    private final long timestamp;

    public Request(int variableId, int firstVirtualPage, int pagesAllocated, int sizeB) {
        this.variableId = variableId;
        this.firstVirtualPage = firstVirtualPage;
        this.sizeB = sizeB;
        this.pagesAllocated = pagesAllocated;
        this.timestamp = System.nanoTime();
    }

    public int getVariableId() {
        return variableId;
    }

    public int getFirstVirtualPage() {
        return firstVirtualPage;
    }

    public int getRequestSizeB() {
        return sizeB;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getPagesAllocated(){
        return pagesAllocated;
    }

}