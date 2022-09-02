package abm.models.modeChoice;

import abm.data.plans.Activity;
import abm.data.plans.Mode;

public class SimpleSubtourModeChoice implements SubtourModeChoice{
    @Override
    public void chooseSubtourMode(Activity subtourActivity, Activity mainActivity) {
        mainActivity.getSubtour().getInboundLeg().setLegMode(Mode.WALK);
        mainActivity.getSubtour().getOutboundLeg().setLegMode(Mode.WALK);
    }
}
