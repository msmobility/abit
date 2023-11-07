package abm.data.vehicle;

import abm.data.timeOfDay.CarAvailableTimeOfWeek;
import abm.data.timeOfDay.CarAvailableTimeOfWeekLinkedList;

public class Car implements Vehicle {


    private final int id; //household id + car id
    private final CarType carType;
    private int age;
    private CarAvailableTimeOfWeekLinkedList availableTimeOfWeek;

    public Car(int id, CarType carType, int age) {
        this.id = id;
        this.carType = carType;
        this.age = age;
        //Todo a testing switch here
        this.availableTimeOfWeek = new CarAvailableTimeOfWeekLinkedList();
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

    public void increaseAgeByOne(){
        age++;
    }

    public CarType getCarType() {
        return carType;
    }

    public CarAvailableTimeOfWeekLinkedList getAvailableTimeOfWeek() {
        return availableTimeOfWeek;
    }
}
