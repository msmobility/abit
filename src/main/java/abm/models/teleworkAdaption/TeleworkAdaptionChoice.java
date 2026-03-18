package abm.models.teleworkAdaption;

import abm.data.pop.Person;

public interface TeleworkAdaptionChoice {
    boolean canTelework(Person person);
}