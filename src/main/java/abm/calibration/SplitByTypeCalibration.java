package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.models.activityGeneration.splitByType.SplitByTypeModel;
import abm.properties.AbitResources;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SplitByTypeCalibration implements ModelComponent{
    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(SplitByTypeModel.class);
    private static final int MAX_ITERATION = 2_000_000;
    private static final double TERMINATION_THRESHOLD = 0.005;
    double stepSize = 10;
    String inputFolder = AbitResources.instance.getString("act.split.type.output");
    DataSet dataSet;
    Map<DiscretionaryActivityType, Double> objectiveSplitByType = new HashMap<>();
    Map<DiscretionaryActivityType, Double> simulatedSplitByType = new HashMap<>();
    Map<DiscretionaryActivityType, Double> calibrationFactors = new HashMap<>();

    private SplitByTypeModel splitByTypeCalibration;

    public SplitByTypeCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }
    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated
        boolean calibrateSplitByType = Boolean.parseBoolean(AbitResources.instance.getString("act.split.type.calibration"));
        splitByTypeCalibration = new SplitByTypeModel(dataSet, calibrateSplitByType);

        //Todo: initialize all the data containers that might be needed for calibration

        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.ON_MANDATORY_TOUR, 0.0);
        for (DiscretionaryActivityType activityType : DiscretionaryActivityType.getDiscretionaryOntoDiscretionaryTypes()) {
            objectiveSplitByType.putIfAbsent(activityType, 0.0);
            simulatedSplitByType.putIfAbsent(activityType, 0.0);
        }
    }

    @Override
    public void load() {
        //Todo: read objective values
        readObjectiveValues();
        //Todo: consider having the result summarization in the statistics writer
        summarizeSimulatedResult();
    }

    @Override
    public void run() {

        //Todo: loop through the calibration process until criteria are met



        //Todo: obtain the updated coefficients + calibration factors


        //Todo: print the coefficients table to input folder

    }

    private void readObjectiveValues() {
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.ON_MANDATORY_TOUR, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.ACCOMPANY_ON_ACCOMPANY, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.SHOP_ON_ACCOMPANY, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.SHOP_ON_SHOP, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.OTHER_ON_ACCOMPANY, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.OTHER_ON_SHOP, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.OTHER_ON_OTHER, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.RECREATION_ON_ACCOMPANY, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.RECREATION_ON_SHOP, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.RECREATION_ON_OTHER, 0.00);
        objectiveSplitByType.putIfAbsent(DiscretionaryActivityType.RECREATION_ON_RECREATION, 0.00);

    }

    private void summarizeSimulatedResult() {

/*        long numActOnMandTour = dataSet.getHouseholds().values().stream()
                .filter(Household::getSimulated)
                .flatMap(household -> household.getPersons().stream())
                .flatMap(person -> person.getPlan().getTours().values().stream())
                .flatMap(tour -> tour.getActivities().values().stream())
                .filter(act -> act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.ON_MANDATORY_TOUR))
                .count();*/
        int numActOnMandTour = 0;
        int numAccompanyPrimary = 0;
        int numShopPrimary = 0;
        int numOtherPrimary = 0;
        int numRecreationPrimary = 0;
        int numAccompanyOnAccompany = 0;
        int numShopOnAccompany = 0;
        int numShopOnShop = 0;
        int numOtherOnAccompany = 0;
        int numOtherOnShop = 0;
        int numOtherOnOther = 0;
        int numRecreationOnAccompany = 0;
        int numRecreationOnShop = 0;
        int numRecreationOnOther = 0;
        int numRecreationOnRecreation = 0;
        int numDiscretionaryAct = 0;
        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                for (Person person : household.getPersons()){

                    for (Tour tour : person.getPlan().getTours().values()){
                        for (Activity act: tour.getActivities().values()) {
                            if(act.getDiscretionaryActivityType() != null) {
                                numDiscretionaryAct++;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.ON_MANDATORY_TOUR)) {
                                numActOnMandTour+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.ACCOMPANY_PRIMARY)) {
                                numAccompanyPrimary++;}
                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.SHOP_PRIMARY)) {
                                numShopPrimary++;}
                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.OTHER_PRIMARY)) {
                                numOtherPrimary++;}
                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.RECREATION_PRIMARY)) {
                                numRecreationPrimary++;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.ACCOMPANY_ON_ACCOMPANY)) {
                                numAccompanyOnAccompany+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.SHOP_ON_ACCOMPANY)) {
                                numShopOnAccompany++;
                            }

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.SHOP_ON_SHOP)) {
                                numShopOnShop+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.OTHER_ON_ACCOMPANY)) {
                                numOtherOnAccompany+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.OTHER_ON_SHOP)) {
                                numOtherOnShop+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.OTHER_ON_OTHER)) {
                                numOtherOnOther+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.RECREATION_ON_ACCOMPANY)) {
                                numRecreationOnAccompany+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.RECREATION_ON_SHOP)) {
                                numRecreationOnShop+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.RECREATION_ON_OTHER)) {
                                numRecreationOnOther+=1;}

                            if(act.getDiscretionaryActivityType().equals(DiscretionaryActivityType.RECREATION_ON_RECREATION)) {
                                numRecreationOnRecreation+=1;}


                        }
                    }
                }
            }
        }

        simulatedSplitByType.replace(DiscretionaryActivityType.ON_MANDATORY_TOUR, (double) numActOnMandTour/numDiscretionaryAct );
        simulatedSplitByType.replace(DiscretionaryActivityType.ACCOMPANY_ON_ACCOMPANY, (double) numAccompanyOnAccompany/(numAccompanyPrimary+numAccompanyOnAccompany) );
        simulatedSplitByType.replace(DiscretionaryActivityType.SHOP_ON_ACCOMPANY, (double) numShopOnAccompany/(numShopOnAccompany+numShopOnShop+numShopPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.SHOP_ON_SHOP, (double)  numShopOnShop/(numShopOnAccompany+numShopOnShop+numShopPrimary));
        simulatedSplitByType.replace(DiscretionaryActivityType.OTHER_ON_ACCOMPANY, (double) numOtherOnAccompany/(numOtherOnAccompany+numOtherOnShop+numOtherOnOther+numOtherPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.OTHER_ON_SHOP, (double) numOtherOnShop/(numOtherOnAccompany+numOtherOnShop+numOtherOnOther+numOtherPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.OTHER_ON_OTHER, (double) numOtherOnOther/(numOtherOnAccompany+numOtherOnShop+numOtherOnOther+numOtherPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.RECREATION_ON_ACCOMPANY, (double) numRecreationOnAccompany/(numRecreationOnAccompany+numRecreationOnShop+numRecreationOnOther+numRecreationOnRecreation+numRecreationPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.RECREATION_ON_SHOP, (double) numRecreationOnShop/(numRecreationOnAccompany+numRecreationOnShop+numRecreationOnOther+numRecreationOnRecreation+numRecreationPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.RECREATION_ON_OTHER, (double) numRecreationOnOther/(numRecreationOnAccompany+numRecreationOnShop+numRecreationOnOther+numRecreationOnRecreation+numRecreationPrimary) );
        simulatedSplitByType.replace(DiscretionaryActivityType.RECREATION_ON_ACCOMPANY, (double) numRecreationOnRecreation/(numRecreationOnAccompany+numRecreationOnShop+numRecreationOnOther+numRecreationOnRecreation+numRecreationPrimary) );

    }

    private void printFinalCoefficientsTable(Map<Mode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {

    }
}
