package abm.data.vehicle;

import abm.data.timeOfDay.AvailableTimeOfWeek;

import java.util.SortedMap;

public class Car implements Vehicle {


    private final int id; //household id + car id
    private final CarType carType;
    private int age;
    private AvailableTimeOfWeek availableTimeOfWeek;

    public Car(int id, CarType carType, int age) {
        this.id = id;
        this.carType = carType;
        this.age = age;
        this.availableTimeOfWeek = new AvailableTimeOfWeek();
    }


    @Override
    public int getId() {
        return id;
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
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

    public AvailableTimeOfWeek getAvailableTimeOfWeek() {
        return availableTimeOfWeek;
    }
}
