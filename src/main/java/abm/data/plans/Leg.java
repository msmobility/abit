package abm.data.plans;

public class Leg {

    private Activity previousActivity;
    private Activity nextActivity;
    private Mode legMode;

    public Leg(Activity previousActivity, Activity nextActivity) {
        this.previousActivity = previousActivity;
        this.nextActivity = nextActivity;
        this.legMode = Mode.UNKNOWN;
    }

    public Activity getPreviousActivity() {
        return previousActivity;
    }

    public void setPreviousActivity(Activity previousActivity) {
        this.previousActivity = previousActivity;
    }

    public Activity getNextActivity() {
        return nextActivity;
    }

    public void setNextActivity(Activity nextActivity) {
        this.nextActivity = nextActivity;
    }

    public Mode getLegMode() {
        return legMode;
    }

    public void setLegMode(Mode legMode) {
        this.legMode = legMode;
    }
}
