package MMS;

import java.util.List;

public class Request {
    private final int variableId;
    private final int sizeB;
    private int pagesAllocated;
    private List<Integer> pagesAllocatedList;

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

    public List<Integer> getPagesAllocatedList(){
        return pagesAllocatedList;
    }

    public void setPagesAllocatedList(List<Integer> freePages){
        pagesAllocatedList = freePages;
    }

}