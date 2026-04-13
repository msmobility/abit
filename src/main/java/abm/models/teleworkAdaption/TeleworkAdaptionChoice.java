package abm.models.teleworkAdaption;

import abm.data.pop.Household;

public interface TeleworkAdaptionChoice {

    /**
     * Compute telework decision for a household.
     * Returns a HouseholdTeleworkDecision containing
     * telework flags for male and female earners.
     */
    void chooseTelework(Household household);

}