package MMS;

public class Request {
    private final int variableId;
    private final int sizeB;
    private int pagesAllocated;
    private int firstVirtualPage;

    public Request(int variableId, int sizeB) {
        this.variableId = variableId;
        this.sizeB = sizeB;
    }

    public int getVariableId() {
        return variableId;
    }

    public int getSizeB() {
        return sizeB;
    }

    public int getPagesAllocated(){
        return pagesAllocated;
    }

    public void setPagesAllocated(int pagesNeeded){
        pagesAllocated = pagesNeeded;
    }

    public int getFirstVirtualPage() {
        return firstVirtualPage;
    }

    public void setFirstVirtualPage(int firstVirtualPage){
        this.firstVirtualPage = firstVirtualPage;
    }

}