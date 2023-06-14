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

            utility += workSubtourCoef.get("(Intercept)");

            if (mandatoryActivity.getDuration() > 6 * 60 || mandatoryActivity.getDuration() < 24 * 60) {
                utility += workSubtourCoef.get("act.duration_hr_6_24");
            }

            if (mandatoryActivity.getStartTime_min() > 8 * 60 || mandatoryActivity.getStartTime_min() < 12 * 60) {
                utility += workSubtourCoef.get("act.start_hr_8_12");
            }

            if (mandatoryActivity.getEndTime_min() > 12 * 60 || mandatoryActivity.getEndTime_min() < 24 * 60) {
                utility += workSubtourCoef.get("act.end_hr_12_24");
            }

            if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.FRIDAY)) {
                utility += workSubtourCoef.get("tour.friday");
            } else if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.SATURDAY)) {
                utility += workSubtourCoef.get("tour.weekend");
            } else if (mandatoryActivity.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
                utility += workSubtourCoef.get("tour.weekend");
            }

            //todo check why tour mode is zero
            //if (mandatoryTour.getTourMode().equals(Mode.CAR_DRIVER)) {
            //    utility += workSubtourCoef.get("tour.mode_carD");
            //}

            //todo change to half time
            if (person.getOccupation().equals(Occupation.EMPLOYED)) {
                utility += workSubtourCoef.get("p.occupationStatus_Halftime");
            } else if (person.getOccupation().equals(Occupation.STUDENT)) {
                utility += workSubtourCoef.get("p.occupationStatus_Student");
            }

            //todo add travel time from work location to pt: "walkTime2PtFromWorkLocation_from20min"

            if (AgeGroupFine.assignAgeGroupFine(person.getAge()).equals(AgeGroupFine.from0to18)) {
                utility += workSubtourCoef.get("p.age_gr_fine_1");
            }

            //todo add number of works per week: "numDaysWork"

            if (person.getHousehold().getEconomicStatus().equals(EconomicStatus.from1601to2400)) {
                utility += workSubtourCoef.get("hh.econStatus_3");
            } else if (person.getHousehold().getEconomicStatus().equals(EconomicStatus.from2401)) {
                utility += workSubtourCoef.get("hh.econStatus_4");
            }


            int hhSize = person.getHousehold().getPersons().size();
            switch (hhSize) {
                case 1:
                    break;
                case 2:
                    utility += workSubtourCoef.get("hh.size_2");
                    break;
                case 3:
                    utility += workSubtourCoef.get("hh.size_3");
                    break;
                case 4:
                    utility += workSubtourCoef.get("hh.size_4");
                    break;
                default:
                    utility += workSubtourCoef.get("hh.size_5");
                    break;
            }


        } else if (purpose.equals(Purpose.EDUCATION)) {

            utility += eduSubtourCoef.get("(Intercept)");

            if (mandatoryActivity.getDuration() > 4 * 60 || mandatoryActivity.getDuration() < 24 * 60) {
                utility += eduSubtourCoef.get("act.duration_hr_4_24");
            }

            if (mandatoryActivity.getEndTime_min() > 14 * 60 || mandatoryActivity.getEndTime_min() < 24 * 60) {
                utility += eduSubtourCoef.get("act.end_hr_14_24");
            }

            //todo check why tour mode is zero
            //if (mandatoryTour.getTourMode().equals(Mode.CAR_DRIVER)) {
            //    utility += eduSubtourCoef.get("tour.mode_carD");
            //}

            //todo add travel time from work location to pt: "walkTime2PtFromWorkLocation_from10min"

            if (person.getAge() <= 16) {
                utility += eduSubtourCoef.get("p.age_0_16");
            }

            if (person.getHousehold().getEconomicStatus().equals(EconomicStatus.from2401)) {
                utility += eduSubtourCoef.get("hh.econStatus_4");
            }

            int hhSize = person.getHousehold().getPersons().size();
            switch (hhSize) {
                case 1:
                    break;
                case 2:
                    utility += eduSubtourCoef.get("hh.size_2");
                    break;
                case 3:
                    utility += eduSubtourCoef.get("hh.size_3");
                    break;
                case 4:
                    utility += eduSubtourCoef.get("hh.size_4");
                    break;
                default:
                    utility += eduSubtourCoef.get("hh.size_5");
                    break;
            }
        }
        return utility;
    }
}
