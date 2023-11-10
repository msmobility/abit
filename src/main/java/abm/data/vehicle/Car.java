package abm.data.vehicle;

import abm.data.timeOfDay.CarAvailableTimeOfWeek;

public class Car implements Vehicle {


    private final int id; //household id + car id
    private final CarType carType;
    private int age;
    private CarAvailableTimeOfWeek availableTimeOfWeek;
    @Override
    public CarAvailableTimeOfWeek getCarAvailableTimeOfWeek(){
        return availableTimeOfWeek;
    }

    public Car(int id, CarType carType, int age) {
        this.id = id;
        this.carType = carType;
        this.age = age;
        this.availableTimeOfWeek = new CarAvailableTimeOfWeek();
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

    public CarAvailableTimeOfWeek getAvailableTimeOfWeek() {
        return availableTimeOfWeek;
    }
}
