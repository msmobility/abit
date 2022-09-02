package abm.models.activityGeneration.frequency;

import abm.data.plans.Activity;

public interface SubtourGenerator {

    boolean hasSubtourInMandatoryActivity(Activity mandatoryActivity);

}
