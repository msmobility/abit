package abm.models.activityGeneration.frequency;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.AgeGroupFine;
import abm.data.pop.EconomicStatus;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

public class SubtourGeneratorModel implements SubtourGenerator {

    private static final Logger logger = Logger.getLogger(SubtourGeneratorModel.class);

    private final DataSet dataSet;

    private Map<String, Double> workSubtourCoef;
    private Map<String, Double> eduSubtourCoef;

    public SubtourGeneratorModel(DataSet dataSet) {
        this.dataSet = dataSet;

        this.workSubtourCoef =
                new CoefficientsReader(dataSet, "yes",
                        Path.of(AbitResources.instance.getString("actgen.subtour.work"))).readCoefficients();

        this.eduSubtourCoef =
                new CoefficientsReader(dataSet, "yes",
                        Path.of(AbitResources.instance.getString("actgen.subtour.education"))).readCoefficients();

    }

    @Override
    public boolean hasSubtourInMandatoryActivity(Tour mandatoryTour) {


        Person person = mandatoryTour.getMainActivity().getPerson();
        Purpose purpose = mandatoryTour.getMainActivity().getPurpose();
        double utility = calculateUtility(purpose, person, mandatoryTour);


        HashMap<Boolean, Double> probabilities = new HashMap<>();
        probabilities.put(false, 0.);
        probabilities.put(true, utility);
        boolean hasSubtour = MitoUtil.select(probabilities, AbitUtils.getRandomObject());


        return hasSubtour;

    }

    private double calculateUtility(Purpose purpose, Person person, Tour mandatoryTour) {

        double utility = 0.;
        Activity mandatoryActivity = mandatoryTour.getMainActivity();

        if (purpose.equals(Purpose.WORK)) {

            utility += workSubtourCoef.get("ASC");

            if (mandatoryActivity.getDuration() > 6 * 60 && mandatoryActivity.getDuration() < 24 * 60) {
                utility += workSubtourCoef.get("actDuration_6_24");
            }

            if (mandatoryActivity.getStartTime_min() > 8 * 60 && mandatoryActivity.getStartTime_min() < 12 * 60) {
                utility += workSubtourCoef.get("actStart_8_12");
            }

            if (mandatoryActivity.getEndTime_min() > 12 * 60 && mandatoryActivity.getEndTime_min() < 24 * 60) {
                utility += workSubtourCoef.get("actEnd_12_24");
            }

            if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.FRIDAY)) {
                utility += workSubtourCoef.get("isTourOnFriday");
            } else if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.SATURDAY)) {
                utility += workSubtourCoef.get("isTourOnWeekend");
            } else if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
                utility += workSubtourCoef.get("isTourOnWeekend");
            }

            //todo change to half time
            if (person.getOccupation().equals(Occupation.EMPLOYED)) {
                utility += workSubtourCoef.get("isOccupation_halftime");
            } else if (person.getOccupation().equals(Occupation.STUDENT)) {
                utility += workSubtourCoef.get("isOccupation_student");
            }

            //todo add travel time from work location to pt: "isWorkplaceToPtStop>20min"

        } else if (purpose.equals(Purpose.EDUCATION)) {

            utility += eduSubtourCoef.get("ASC");

            if (mandatoryActivity.getStartTime_min() > 7 * 60 && mandatoryActivity.getStartTime_min() < 12 * 60) {
                utility += eduSubtourCoef.get("actStart_7_12");
            }

            if (mandatoryActivity.getEndTime_min() > 12 * 60 && mandatoryActivity.getEndTime_min() < 24 * 60) {
                utility += eduSubtourCoef.get("actEnd_12_24");
            }

            //todo add travel time from work location to pt: "isSchoolplaceToPtStop>20min♦"

            if (person.getAge() < 16) {
                utility += eduSubtourCoef.get("isAge<16");
            }

        }
        return utility;
    }
}
