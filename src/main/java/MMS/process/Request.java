package MMS.process;

public class Request {
    private final int variableId;
    private final int sizeB;
    private int pagesUsedNum;
    private int[] pagesUsedList;

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

    public int getPagesUsedNum(){
        return pagesUsedNum;
    }

    public void setPagesUsedNum(int pagesNeeded){
        pagesUsedNum = pagesNeeded;
    }

    public int[] getPagesUsedList(){
        return pagesUsedList;
    }

    public void setPagesUsedList(int[] freePages){
        pagesUsedList = freePages;
    }

}