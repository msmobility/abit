package abm.data.pop;

public enum  DisabilityMuc{

    WITHOUT(0),
    WITH(1);

    private final int disabilityCode;

    private DisabilityMuc(int disabilityCode) {
        this.disabilityCode = disabilityCode;
    }

    public int getDisabilityCode() {
        return this.disabilityCode;
    }

}
