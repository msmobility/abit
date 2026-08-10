package abm.models.teleworkAdaption;

import abm.data.pop.Household;

public interface RemoteWorkAllowance {

    /**
     * Compute telework decision for a household.
     * Returns a HouseholdTeleworkDecision containing
     * telework flags for male and female earners.
     */
    void assignRemoteWorkAllowance(Household household);

}