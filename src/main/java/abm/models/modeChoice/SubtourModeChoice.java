package abm.models.modeChoice;

import abm.data.plans.Activity;

public interface SubtourModeChoice {

    void chooseSubtourMode(Activity subtourActivity, Activity mainActivity);
}
