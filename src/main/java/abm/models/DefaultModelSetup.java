package abm.models;

import abm.data.DataSet;
import abm.data.plans.Purpose;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;
import abm.models.activityGeneration.splitByType.SimpleSplitByType;
import abm.models.activityGeneration.splitByType.SimpleSplitStopTypeModelWithAvailability;
import abm.models.activityGeneration.splitByType.SplitByType;
import abm.models.activityGeneration.splitByType.SplitStopType;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SimpleDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SimpleHabitualModeChoice;
import abm.models.modeChoice.SimpleTourModeChoice;
import abm.models.modeChoice.TourModeChoice;
import org.apache.commons.collections.map.HashedMap;

import java.util.Map;

public class DefaultModelSetup implements ModelSetup{

    private static Map<Purpose, FrequencyGenerator> frequencyGenerators;
    private static HabitualModeChoice habitualModeChoice;
    private static DestinationChoice destinationChoice;
    private static TourModeChoice tourModeChoice;
    private static DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment;
    private static DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment = new SimpleDayOfWeekDiscretionaryAssignment();
    private static TimeAssignment timeAssignment;
    private static SplitByType splitByType;
    private static SplitStopType stopSplitType;


    public DefaultModelSetup(DataSet dataSet) {



        stopSplitType = new SimpleSplitStopTypeModelWithAvailability();
        splitByType = new SimpleSplitByType();
        timeAssignment = new SimpleTimeAssignmentWithTimeAvailability();
        dayOfWeekMandatoryAssignment = new SimpleDayOfWeekMandatoryAssignment();
        destinationChoice = new SimpleDestinationChoice();
        tourModeChoice = new SimpleTourModeChoice();
        habitualModeChoice = new SimpleHabitualModeChoice();




        frequencyGenerators = new HashedMap();
        for (Purpose purpose : Purpose.getAllPurposes()){
            frequencyGenerators.put(purpose, new FrequencyGeneratorModel(dataSet, purpose));
        }

    }

    @Override
    public HabitualModeChoice getHabitualModeChoice() {
        return habitualModeChoice;
    }

    @Override
    public Map<Purpose, FrequencyGenerator> getFrequencyGenerator() {
        return frequencyGenerators;
    }

    @Override
    public DestinationChoice getDestinationChoice() {
        return destinationChoice;
    }

    @Override
    public TourModeChoice getTourModeChoice() {
        return tourModeChoice;
    }

    @Override
    public DayOfWeekMandatoryAssignment getDayOfWeekMandatoryAssignment() {
        return dayOfWeekMandatoryAssignment;
    }

    @Override
    public DayOfWeekDiscretionaryAssignment getDayOfWeekDiscretionaryAssignment() {
        return dayOfWeekDiscretionaryAssignment;
    }

    @Override
    public TimeAssignment getTimeAssignment() {
        return timeAssignment;
    }

    @Override
    public SplitByType getSplitByType() {
        return splitByType;
    }

    @Override
    public SplitStopType getStopSplitType() {
        return stopSplitType;
    }

}
