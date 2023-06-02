package abm.models.activityGeneration.time;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Purpose;
import abm.data.pop.EconomicStatus;
import abm.data.timeOfDay.AvailableTimeOfWeek;
import de.tum.bgu.msm.util.MitoUtil;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

public class DayOfWeekDiscretionaryAssignmentModel implements DayOfWeekDiscretionaryAssignment {

    private final DataSet dataSet;
    private final Map<DayOfWeek, Double> dayProbabilities;

    public DayOfWeekDiscretionaryAssignmentModel(DataSet dataSet) {
        this.dataSet = dataSet;
        dayProbabilities = new HashMap<>();
    }

    @Override
    public void assignDayOfWeek(Activity activity) {

        Map<DayOfWeek, Double> dayProbabilities = new HashMap<>();

        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {

            AvailableTimeOfWeek availableTime = activity.getPerson().getPlan().getAvailableTimeOfDay();

            double availableTimeSlots = availableTime.getForThisDayOfWeek(dayOfWeek)
                    .getInternalMap().entrySet().stream()
                    .filter(x -> x.getValue().equals(true)).count();

            if (activity.getPurpose().equals(Purpose.SHOPPING)) {
                availableTimeSlots = 0.0;
            }

            dayProbabilities.put(dayOfWeek, (double) availableTimeSlots);
        }
        DayOfWeek select = MitoUtil.select(dayProbabilities);
        activity.setDayOfWeek(select);
    }
}
