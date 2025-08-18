package abm.data.vehicle;

import abm.data.timeOfDay.CarAvailableTimeOfWeek;
import abm.data.timeOfDay.CarBlockedTimeOfWeekLinkedList;

public class STCar implements Vehicle {


    private final int id; //household id + car id
    private final CarType carType;
    private EmissionClass emissionClass;
    private int age;
    private CarBlockedTimeOfWeekLinkedList availableTimeOfWeek;


    public STCar(int id, CarType carType, EmissionClass emissionClass, int age) {
        this.id = id;
        this.carType = carType;
        this.emissionClass = emissionClass;
        this.age = age;
        //Todo a testing switch here
        this.availableTimeOfWeek = new CarBlockedTimeOfWeekLinkedList();
    }


    @Override
    public int getId() {
        return id;
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
    }

    public CarType getEngineType() {
        return this.carType;
    }

    @Override
    public int getAge() {
        return age;
    }

    @Override
    public CarAvailableTimeOfWeek getCarAvailableTimeOfWeek() {
        return null;
    }

    public void increaseAgeByOne(){
        age++;
    }

    public CarType getCarType() {
        return carType;
    }

    public CarBlockedTimeOfWeekLinkedList getBlockedTimeOfWeek() {
        return availableTimeOfWeek;
    }

    public EmissionClass getEmissionClass() {
        return emissionClass;
    }

    public void setEmissionClass(EmissionClass emissionClass) {
        this.emissionClass = emissionClass;
    }
}
