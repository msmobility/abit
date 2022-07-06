package abm.models;

import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.splitByType.SplitByType;
import abm.models.activityGeneration.splitByType.SplitStopType;
import abm.models.activityGeneration.time.DayOfWeekDiscretionaryAssignment;
import abm.models.activityGeneration.time.DayOfWeekMandatoryAssignment;
import abm.models.activityGeneration.time.TimeAssignment;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.TourModeChoice;

public interface ModelSetup {


    HabitualModeChoice getHabitualModeChoice();

    FrequencyGenerator getFrequencyGenerator();

    DestinationChoice getDestinationChoice();

    TourModeChoice getTourModeChoice();

    DayOfWeekMandatoryAssignment getDayOfWeekMandatoryAssignment();

    DayOfWeekDiscretionaryAssignment getDayOfWeekDiscretionaryAssignment();

    TimeAssignment getTimeAssignment();

    SplitByType getSplitByType();

    SplitStopType getStopSplitType();
}
