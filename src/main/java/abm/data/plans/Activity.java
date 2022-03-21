package abm.data.plans;


import abm.data.geo.Location;
import java.time.DayOfWeek;

public class Activity implements Comparable<Activity> {

    private Purpose purpose;
    private DayOfWeek dayOfWeek;
    private double startTime_s;
    private double endTime_s;

    private Location location;


    public Activity(Purpose purpose, double startTime_s, double endTime_s, Location location) {
        this.purpose = purpose;
        this.startTime_s = startTime_s;
        this.endTime_s = endTime_s;
        this.location = location;
    }

    public Activity(Purpose purpose){
        this.purpose = purpose;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public double getStartTime_s() {
        return startTime_s;
    }

    public double getEndTime_s() {
        return endTime_s;
    }

    public Location getLocation() {
        return location;
    }

    public void setStartTime_s(double startTime_s) {
        this.startTime_s = startTime_s;
    }

    public void setEndTime_s(double endTime_s) {
        this.endTime_s = endTime_s;
    }

    public double getDuration(){
        return  endTime_s - startTime_s;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public int compareTo(Activity activity) {
        if (activity.getStartTime_s() < this.getStartTime_s()){
            return 1;
        } else if (activity.getStartTime_s() == this.getStartTime_s()) {
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "Activity{" +
                "purpose=" + purpose +
                ", startTime_s=" + startTime_s +
                ", endTime_s=" + endTime_s +
                ", location=" + location +
                '}';
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }
}
