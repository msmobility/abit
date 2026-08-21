package abm.models.remoteWorkArrangement;

import abm.data.DataSet;
import abm.data.plans.Purpose;
import abm.data.pop.EconomicStatus;
import abm.data.pop.Person;
import abm.models.activityGeneration.time.DayOfWeekMandatoryAssignment;
import abm.properties.AbitResources;
import de.tum.bgu.msm.util.MitoUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DayOfWeekRemoteWorkAssignmentModel implements DayOfWeekMandatoryAssignment {

    private final DataSet dataSet;
    private final Map<EconomicStatus, Map<DayOfWeek, Double>> dayProbabilitiesRemoteWork;

    public DayOfWeekRemoteWorkAssignmentModel(DataSet dataSet) {
        this.dataSet = dataSet;
        dayProbabilitiesRemoteWork = new HashMap<>();
        readDayOfWeekDistributionForMainActs();
    }

    private void readDayOfWeekDistributionForMainActs() {

        int econStatusIndex;
        int dayOfWeekIndex;
        int probabilityIndex;

        try (BufferedReader br = new BufferedReader(new FileReader(AbitResources.instance.getString("day.of.week.distribution.remote.work")))) {
            String[] firstLine = br.readLine().split(",");

            econStatusIndex = MitoUtil.findPositionInArray("econStatus", firstLine);
            dayOfWeekIndex = MitoUtil.findPositionInArray("dayOfWeek", firstLine);
            probabilityIndex = MitoUtil.findPositionInArray("probabilities", firstLine);

            String line;
            while((line = br.readLine())!= null){

                EconomicStatus econStatus = EconomicStatus.of(Integer.parseInt(line.split(",")[econStatusIndex]));
                DayOfWeek timeOfDay = DayOfWeek.valueOf(line.split(",")[dayOfWeekIndex].toUpperCase());
                double probability = Double.parseDouble(line.split(",")[probabilityIndex]);

                dayProbabilitiesRemoteWork.putIfAbsent(econStatus, new HashMap<>());
                dayProbabilitiesRemoteWork.get(econStatus).putIfAbsent(timeOfDay, probability);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public DayOfWeek[] assignDaysOfWeek(int numberOfDaysOfWeek, Purpose purpose, Person person) {

        Map<DayOfWeek, Double> dayProbabilities = new HashMap<>(getBaseDistribution(person));

        DayOfWeek[] daysOfWeek = new DayOfWeek[numberOfDaysOfWeek];

        for (int i = 0; i < numberOfDaysOfWeek; i++) {
            final DayOfWeek select = MitoUtil.select(dayProbabilities, person.getRandom());
            daysOfWeek[i] = select;
            dayProbabilities.remove(select);
        }
        return daysOfWeek;
    }

    public DayOfWeek[] assignDaysOfWeek(DayOfWeek[] workingDayOfWeeks, int numberOfDaysOfWeek, Purpose purpose, Person person) {

        Set<DayOfWeek> workingDays = new HashSet<>(Arrays.asList(workingDayOfWeeks));
        Map<DayOfWeek, Double> dayProbabilities = getBaseDistribution(person).entrySet().stream()
                .filter(entry -> workingDays.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        int daysToAssign = Math.min(numberOfDaysOfWeek, dayProbabilities.size());
        DayOfWeek[] daysOfWeekRemoteWork = new DayOfWeek[daysToAssign];

        for (int i = 0; i < daysToAssign; i++) {
            final DayOfWeek select = MitoUtil.select(dayProbabilities, person.getRandom());
            daysOfWeekRemoteWork[i] = select;
            dayProbabilities.remove(select);
        }
        return daysOfWeekRemoteWork;
    }

    /**
     * Looks up the raw day-of-week distribution for this person's household economic status,
     * failing fast if the input CSV doesn't cover it rather than letting callers hit an NPE
     * from copying/streaming a null map.
     */
    private Map<DayOfWeek, Double> getBaseDistribution(Person person) {
        EconomicStatus economicStatus = person.getHousehold().getEconomicStatus();
        Map<DayOfWeek, Double> baseDistribution = dayProbabilitiesRemoteWork.get(economicStatus);
        if (baseDistribution == null) {
            throw new IllegalStateException("No remote-work day-of-week distribution configured for economic status " + economicStatus);
        }
        return baseDistribution;
    }
}
