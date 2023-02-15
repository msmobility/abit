package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import abm.utils.LogitTools;
import de.tum.bgu.msm.data.MitoHousehold;
import de.tum.bgu.msm.data.MitoPerson;
import de.tum.bgu.msm.data.MitoZone;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.util.MitoUtil;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.*;


public class NestedLogitModeChoiceModel implements TourModeChoice{


    private final DataSet dataSet;
    private Map<Purpose,Map<Mode,Map<String, Double>>> purposeModeCoefficients;

    private static final LogitTools<abm.data.plans.Mode> logitTools = new LogitTools<>(abm.data.plans.Mode.class);
    private Map<Purpose, List<Tuple<EnumSet<abm.data.plans.Mode>, Double>>> nests = null;

    public NestedLogitModeChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.purposeModeCoefficients = new HashMap<>();

        //the following loop will read the coefficient file Mode.getModes().size() times, which is acceptable?
        Map<Purpose,Map<Mode,Map<String, Double>>> purposeModeCoefficients = new HashMap<>();
        Map<Purpose, List<Tuple<EnumSet<abm.data.plans.Mode>, Double>>> nests = new HashMap<>();
        for (Purpose purpose: Purpose.getAllPurposes()) {
            Map<Mode, Map<String, Double>> modeCoefficients = new HashMap<>();
            List<Tuple<EnumSet<abm.data.plans.Mode>, Double>> nestsByPurpose = new ArrayList<>();
            Path pathToFilePurpose = Path.of(AbitResources.instance.getString("tour.mode.coef") + "_" + purpose + ".csv");
            for (Mode mode : Mode.getModes()) {
                String columnName = mode.toString().toLowerCase() + "_" + purpose.toString().toLowerCase(); //todo review this
                Map<String, Double> coefficients = new CoefficientsReader(dataSet, columnName, pathToFilePurpose).readCoefficients();
                modeCoefficients.put(mode, coefficients);
                if (mode == Mode.CAR_DRIVER){
                    nestsByPurpose.add(new Tuple<>(EnumSet.of(Mode.CAR_DRIVER,Mode.CAR_PASSENGER), coefficients.get("nestingCoefficient")));
                } else if (mode == Mode.TRAIN){
                    nestsByPurpose.add(new Tuple<>(EnumSet.of(Mode.TRAIN, Mode.TRAM_METRO, Mode.BUS), coefficients.get("nestingCoefficient")));
                } else if (mode == Mode.BIKE){
                    nestsByPurpose.add(new Tuple<>(EnumSet.of(Mode.WALK,Mode.BIKE), coefficients.get("nestingCoefficient")));
                }
            }
            purposeModeCoefficients.put(purpose, modeCoefficients);
        }
        this.purposeModeCoefficients = purposeModeCoefficients;
        this.nests = nests;

    }

    @Override
    public void chooseMode(Person person, Tour tour) {
        //This is only used for the simple mode choice that does not have coefficients by mode in separate files by purpose
    }

    @Override
    public void chooseMode(Person person, Tour tour, Purpose purpose) {

    }

    @Override
    public Mode chooseMode(Person person, Tour tour, Purpose purpose, Boolean carAvailable) {

        EnumMap<Mode, Double> utilities = new EnumMap<Mode, Double>(Mode.class);
        for (Mode mode : Mode.getModes()){
            if (mode == Mode.CAR_DRIVER && carAvailable) {
                utilities.put(mode, calculateUtilityForThisMode(person, tour, purpose, mode));
            } else {
                utilities.put(mode,Double.NEGATIVE_INFINITY);
            }
        }

        if(utilities == null) return null;
        EnumMap<Mode, Double>  probabilities = logitTools.getProbabilities(utilities, nests.get(purpose));

        final Mode selected = MitoUtil.select(probabilities, AbitUtils.getRandomObject().nextDouble());

        tour.getLegs().values().forEach(leg -> leg.setLegMode(selected));

        return selected;

    }

    private double calculateUtilityForThisMode(Person person, Tour tour, Purpose purpose, Mode mode) {
    // Intercept
        double utility = purposeModeCoefficients.get(purpose).get(mode).get("INTERCEPT");

        // Sex
        if (person.getGender().equals(Gender.FEMALE)) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.female",0.);
        }

        // Age
        if (person.getAge() < 19) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.age_gr_1",0.);
        } else if (person.getAge() < 30) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.age_gr_2",0.);
        } else if (person.getAge() < 50) {
            utility += 0;
        } else if (person.getAge() < 70) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.age_gr_45",0.);
        } else {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.age_gr_6",0.);
        }

        // Mobility restriction
        if (person.getAttributes().getAttribute("mobilityRestricted").equals("yes")) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("p.mobilityRestricted",0.);
        }

        // Household size
        utility += person.getHousehold().getPersons().size() * purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.size",0.);

        // Household Children
        int numChildren = (int) person.getHousehold().getPersons().stream().filter(p -> p.getAge() <= 18).count();
        if (numChildren > 0) {
            utility +=  purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.children",0.);
        }

        // Household BBSR Region Type
        RegioStaR7 regionType = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId()).getRegioStaR7Type();
        if (regionType.equals(RegioStaR7.URBAN_METROPOLIS) || regionType.equals(RegioStaR7.URBAN_REGIOPOLIS)) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.regionType_12",0.);
        } else if (regionType.equals(RegioStaR7.URBAN_MEDIUM_SIZED_CITY) || regionType.equals(RegioStaR7.URBAN_PROVINCIAL)) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.regionType_34",0.);
        } else if (regionType.equals(RegioStaR7.RURAL_CENTRAL_CITY) || regionType.equals(RegioStaR7.RURAL_URBAN_AREA)) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.regionType_56",0.);
        } else {
            utility += 0;
        }

        // Urban or rural
        RegioStaR2 regionType2 = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId()).getRegioStaR2Type();
        if (regionType2.equals(RegioStaR2.URBAN)) {
            utility += purposeModeCoefficients.get(purpose).get(mode).getOrDefault("hh.urban",0.);
        } else{
            utility += 0;
        }

        //todo. Change the calculator with data from the tour and person
        EnumMap<Mode, Double> generalizedCosts = calculateGeneralizedCosts(purpose, household, person,
                originZone, destinationZone, travelTimes, travelDistanceAuto, travelDistanceNMT, peakHour_s);

        //todo. Calculate tours of the person to get utilities of mobility related attributes (missing)
            double gc = generalizedCosts.get(mode);
        utility += gc * purposeModeCoefficients.get(purpose).get(mode).get("gc");


        return utility;
    }

    //todo. Remove the pointers to msm and replace by abit specific objects
    public EnumMap<Mode, Double> calculateGeneralizedCosts(Purpose purpose, MitoHousehold household, MitoPerson person, MitoZone originZone,
                                                                               MitoZone destinationZone, TravelTimes travelTimes,
                                                                               double travelDistanceAuto, double travelDistanceNMT, double peakHour_s) {

        double timeAutoD = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "car");
        double timeAutoP = timeAutoD;
        double timeBus = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "bus");
        double timeTrain = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "train");
        double timeTramMetro = travelTimes.getTravelTime(originZone, destinationZone, peakHour_s, "tramMetro");

        int monthlyIncome_EUR = household.getMonthlyIncome_EUR();

        double gcAutoD;
        double gcAutoP;
        double gcBus;
        double gcTrain;
        double gcTramMetro;
        double gcWalk = travelDistanceNMT;
        double gcBicycle = travelDistanceNMT;

        //todo. Add VOT table with the coefficients and rename
        if (monthlyIncome_EUR <= 1500) {
            gcAutoD = timeAutoD + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_DRIVER).get("vot_under_1500_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_PASSENGER).get("vot_under_1500_eur_min");
            gcBus = timeBus + (travelDistanceAuto * 0.12) / coef.get(Mode.BUS).get("vot_under_1500_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAIN).get("vot_under_1500_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAM_METRO).get("vot_under_1500_eur_min");
        } else if (monthlyIncome_EUR <= 5600) {
            gcAutoD = timeAutoD + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_DRIVER).get("vot_1500_to_5600_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_PASSENGER).get("vot_1500_to_5600_eur_min");
            gcBus = timeBus + (travelDistanceAuto * 0.12) / coef.get(Mode.BUS).get("vot_1500_to_5600_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAIN).get("vot_1500_to_5600_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAM_METRO).get("vot_1500_to_5600_eur_min");
        } else {
            gcAutoD = timeAutoD + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_DRIVER).get("vot_above_5600_eur_min");
            gcAutoP = timeAutoP + (travelDistanceAuto * 0.7) / coef.get(Mode.CAR_PASSENGER).get("vot_above_5600_eur_min");
            gcBus = timeBus + (travelDistanceAuto * 0.12) / coef.get(Mode.BUS).get("vot_above_5600_eur_min");
            gcTrain = timeTrain + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAIN).get("vot_above_5600_eur_min");
            gcTramMetro = timeTramMetro + (travelDistanceAuto * 0.12) / coef.get(Mode.TRAM_METRO).get("vot_above_5600_eur_min");
        }

        EnumMap<Mode, Double> generalizedCosts = new EnumMap<Mode, Double>(Mode.class);
        generalizedCosts.put(Mode.CAR_DRIVER, gcAutoD);
        generalizedCosts.put(Mode.CAR_PASSENGER, gcAutoP);
        generalizedCosts.put(Mode.BIKE, gcBicycle);
        generalizedCosts.put(Mode.BUS, gcBus);
        generalizedCosts.put(Mode.TRAIN, gcTrain);
        generalizedCosts.put(Mode.TRAM_METRO, gcTramMetro);
        generalizedCosts.put(Mode.WALK, gcWalk);
        return generalizedCosts;

    }
}
