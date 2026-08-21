package abm.models.remoteWorkArrangement;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.Zone;
import abm.data.plans.Mode;
import abm.data.pop.EmploymentStatus;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

public class RemoteWorkAllowanceMultinomialLogitModel implements RemoteWorkAllowance {

    private static final Logger logger = Logger.getLogger(RemoteWorkAllowanceMultinomialLogitModel.class);

    private final DataSet dataSet;



    private final Map<String, EnumMap<TeleworkAlternative, Double>> coefDualEarner = new HashMap<>();
    private final Map<String, EnumMap<TeleworkAlternative, Double>> coefSingleEarnerMale = new HashMap<>();
    private final Map<String, EnumMap<TeleworkAlternative, Double>> coefSingleEarnerFemale = new HashMap<>();
    private final Map<String, EnumMap<TeleworkAlternative, Double>> coefSinglePerson = new HashMap<>();

    public RemoteWorkAllowanceMultinomialLogitModel(DataSet dataSet) {
        this.dataSet = dataSet;

        Path coefFileDual = Path.of(AbitResources.instance.getString("telework.coef.dual"));
        Path coefFileSingleMale = Path.of(AbitResources.instance.getString("telework.coef.single.male"));
        Path coefFileSingleFemale = Path.of(AbitResources.instance.getString("telework.coef.single.female"));
        Path coefFileSinglePerson = Path.of(AbitResources.instance.getString("telework.coef.single.person"));


        // Dual → always 4 alternatives
        coefDualEarner.putAll(new TeleworkCoefficientsReader(coefFileDual).readCoefficients());

        // Single-earner male → always 2 alternatives
        coefSingleEarnerMale.putAll(new TeleworkCoefficientsReader(coefFileSingleMale).readCoefficients());

        // Single-earner female → always 2 alternatives
        coefSingleEarnerFemale.putAll(new TeleworkCoefficientsReader(coefFileSingleFemale).readCoefficients());

        // Single-person → detect format
        TeleworkCoefficientsReader readerSingle = new TeleworkCoefficientsReader(coefFileSinglePerson);
        coefSinglePerson.putAll(readerSingle.readCoefficients());

    }

    @Override
    public void assignRemoteWorkAllowance(Household household) {

        if(household.getHouseholdType() == HouseholdType.PARTNERED_DUAL_EARNER) {
            Optional<Person> employedMan = household.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.MALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst();
            Optional<Person> employedWoman = household.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.FEMALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst();
            if (employedMan.isEmpty() || employedWoman.isEmpty()) {
                logger.warn("Household " + household.getId() + " is classified as PARTNERED_DUAL_EARNER but is missing an expected employed married man/woman - skipping remote-work assignment.");
                household.getPersons().forEach(p -> p.setRemoteWork(false));
                return;
            }
            assignRemoteWorkArrangementForPartneredDualEarnerHousehold(household, employedMan.get(), employedWoman.get());

        } else if (household.getHouseholdType() == HouseholdType.PARTNERED_SINGLE_EARNER_MALE){
            Optional<Person> employedMan = household.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.MALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst();
            if (employedMan.isEmpty()) {
                logger.warn("Household " + household.getId() + " is classified as PARTNERED_SINGLE_EARNER_MALE but is missing the expected employed married man - skipping remote-work assignment.");
                household.getPersons().forEach(p -> p.setRemoteWork(false));
                return;
            }
            adaptTeleworkForPartneredSingleEarnerHouseholdMale(household, employedMan.get());

        } else if (household.getHouseholdType() == HouseholdType.PARTNERED_SINGLE_EARNER_FEMALE) {
            Optional<Person> employedWoman = household.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.FEMALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst();
            if (employedWoman.isEmpty()) {
                logger.warn("Household " + household.getId() + " is classified as PARTNERED_SINGLE_EARNER_FEMALE but is missing the expected employed married woman - skipping remote-work assignment.");
                household.getPersons().forEach(p -> p.setRemoteWork(false));
                return;
            }
            adaptTeleworkForPartneredSingleEarnerHouseholdFemale(household, employedWoman.get());

        } else if (household.getHouseholdType() == HouseholdType.SINGLE_WORKER){
            adaptTeleworkForSinglePersonHousehold(household);

        }
    }

    private void assignRemoteWorkArrangementForPartneredDualEarnerHousehold(Household hh, Person male, Person female) {

        TeleworkAlternative chosen =
                computeChoice(hh, male, female, coefDualEarner, HouseholdType.PARTNERED_DUAL_EARNER);

        if (chosen.equals(TeleworkAlternative.NO_ONE)){
            male.setRemoteWork(false);
            female.setRemoteWork(false);
        } else if (chosen.equals(TeleworkAlternative.ONLY_MALE)) {
            male.setRemoteWork(true);
            female.setRemoteWork(false);
        } else if (chosen.equals(TeleworkAlternative.ONLY_FEMALE)) {
            male.setRemoteWork(false);
            female.setRemoteWork(true);
        } else if (chosen.equals(TeleworkAlternative.BOTH)) {
            male.setRemoteWork(true);
            female.setRemoteWork(true);
        }
    }
    public void adaptTeleworkForPartneredSingleEarnerHouseholdMale(Household hh, Person male) {

        TeleworkAlternative chosen =
                computeChoice(hh, male, null, coefSingleEarnerMale, HouseholdType.PARTNERED_SINGLE_EARNER_MALE);

        male.setRemoteWork(chosen == TeleworkAlternative.ONLY_MALE);
    }
    public void adaptTeleworkForPartneredSingleEarnerHouseholdFemale(Household hh, Person female) {

        TeleworkAlternative chosen =
                computeChoice(hh, null, female, coefSingleEarnerFemale, HouseholdType.PARTNERED_SINGLE_EARNER_FEMALE);

        female.setRemoteWork(chosen == TeleworkAlternative.ONLY_FEMALE);
    }
    public void adaptTeleworkForSinglePersonHousehold(Household hh) {

        if (hh == null || hh.getPersons().isEmpty()) {
            return;
        }
        // The only person in the household
        Person p = hh.getPersons().get(0);

        // If not working → auto NO telework
        if (p.getOccupation() != (Occupation.EMPLOYED)) {
            p.setRemoteWork(false);
            return;
        }
        Person male   = (p.getGender() == Gender.MALE) ? p : null;
        Person female = (p.getGender() == Gender.FEMALE) ? p : null;

        TeleworkAlternative chosen =
                computeChoice(hh, male, female, coefSinglePerson, HouseholdType.SINGLE_WORKER);

        p.setRemoteWork(chosen == TeleworkAlternative.ONLY_MALE || chosen == TeleworkAlternative.ONLY_FEMALE);
    }

    // --------------------------------------------------------------
    // Regressor Logic
    // --------------------------------------------------------------

    private double computeRegressorDualEarner(String var, Person male, Person female, Household hh) {

        return switch (var) {

            // ASC
            case "asc" -> 1.0;

            // Household characteristics
            case "hh_youngest_child_age_no_child" -> indicator(noChild(hh));
            case "hh_youngest_child_age_0_3"      -> indicator(youngestChildInRange(hh, 0, 3));
            case "hh_youngest_child_age_3_6"      -> indicator(youngestChildInRange(hh, 3, 6));
            case "hh_youngest_child_age_6_10"      -> indicator(youngestChildInRange(hh, 6, 10));
            case "hh_youngest_child_age_10_18"      -> indicator(youngestChildInRange(hh, 10, 18));

            case "hh_rural", "hh_urban"           -> resolveUrbanRuralDegree(var, hh);

            case "hh_cars_lt_2"         -> indicator(countCars(hh) < 2);
            case "hh_cars_ge_2"         -> indicator(countCars(hh) >= 2);

            case "hh_net_income_lt_4000" -> indicator(householdIncome(hh) < 4000);
            case "hh_net_income_ge_4000" -> indicator(householdIncome(hh) >= 4000);

            // Education
            case "p_in_education_no_male"   -> indicator(male.getSchool() == null);
            case "p_in_education_yes_male"  -> indicator(male.getSchool() != null);
            case "p_in_education_no_female" -> indicator(female.getSchool() == null);
            case "p_in_education_yes_female"-> indicator(female.getSchool() != null);

            // Commute male
            case "p_commute_0_male"     -> commuteInRange(male, 0, 0);
            case "p_commute_0_20_male"  -> commuteInRange(male, 0, 20);
            case "p_commute_20_40_male" -> commuteInRange(male, 20, 40);
            case "p_commute_40_80_male" -> commuteInRange(male, 40, 80);
            case "p_commute_ge_80_male" -> commuteAbove(male, 80);

            // Commute female
            case "p_commute_0_female"     -> commuteInRange(female, 0, 0);
            case "p_commute_0_20_female"  -> commuteInRange(female, 0, 20);
            case "p_commute_20_40_female" -> commuteInRange(female, 20, 40);
            case "p_commute_40_80_female" -> commuteInRange(female, 40, 80);
            case "p_commute_ge_80_female" -> commuteAbove(female, 80);

            // Employment status
            case "p_emp_status_other_male"   -> indicator(male.getEmploymentStatus() != EmploymentStatus.FULLTIME_EMPLOYED);
            case "p_emp_status_full_time_male" -> indicator(male.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED);

            case "p_emp_status_other_female"   -> indicator(female.getEmploymentStatus() != EmploymentStatus.FULLTIME_EMPLOYED);
            case "p_emp_status_full_time_female" -> indicator(female.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED);

            // Job sectors male — in computeRegressorDualEarner
            case "j_emp_sector_other_male"              -> male.getJob() == null ? 0.0 : indicator(isOther(male.getJob().getType()));
            case "j_emp_sector_science_humanities_male" -> male.getJob() == null ? 0.0 : indicator(isScienceHumanities(male.getJob().getType()));
            case "j_emp_sector_service_admin_male"      -> male.getJob() == null ? 0.0 : indicator(isServiceAdministration(male.getJob().getType()));
            case "j_emp_sector_transport_logistic_male" -> male.getJob() == null ? 0.0 : indicator(isTransportLogistic(male.getJob().getType()));
            case "j_emp_sector_essential_services_male" -> male.getJob() == null ? 0.0 : indicator(isEssentialServices(male.getJob().getType()));

            // Job sectors female — in computeRegressorDualEarner
            case "j_emp_sector_other_female"              -> female.getJob() == null ? 0.0 : indicator(isOther(female.getJob().getType()));
            case "j_emp_sector_science_humanities_female" -> female.getJob() == null ? 0.0 : indicator(isScienceHumanities(female.getJob().getType()));
            case "j_emp_sector_service_admin_female"      -> female.getJob() == null ? 0.0 : indicator(isServiceAdministration(female.getJob().getType()));
            case "j_emp_sector_transport_logistic_female" -> female.getJob() == null ? 0.0 : indicator(isTransportLogistic(female.getJob().getType()));
            case "j_emp_sector_essential_services_female" -> female.getJob() == null ? 0.0 : indicator(isEssentialServices(female.getJob().getType()));
            // Income
            case "p_gross_income_lt_3000_male"   -> indicator(male.getMonthlyIncome_eur() < 3000);
            case "p_gross_income_3000_6000_male" -> indicator(male.getMonthlyIncome_eur() >= 3000 && male.getMonthlyIncome_eur() < 6000);
            case "p_gross_income_ge_6000_male"   -> indicator(male.getMonthlyIncome_eur() >= 6000);

            case "p_gross_income_lt_3000_female"   -> indicator(female.getMonthlyIncome_eur() < 3000);
            case "p_gross_income_3000_6000_female" -> indicator(female.getMonthlyIncome_eur() >= 3000 && female.getMonthlyIncome_eur() < 6000);
            case "p_gross_income_ge_6000_female"   -> indicator(female.getMonthlyIncome_eur() >= 6000);

//            // Education
//            case "edu_no_uni_male"   -> indicator(!male.hasUniversityDegree());
//            case "edu_uni_male"      -> indicator(male.hasUniversityDegree());
//            case "edu_no_uni_female" -> indicator(!female.hasUniversityDegree());
//            case "edu_uni_female"    -> indicator(female.hasUniversityDegree());

//            // Immigration
//            case "immigration_background_no_male"   -> indicator(!male.hasImmigrationBackground());
//            case "immigration_background_yes_male"  -> indicator(male.hasImmigrationBackground());
//            case "immigration_background_no_female" -> indicator(!female.hasImmigrationBackground());
//            case "immigration_background_yes_female"-> indicator(female.hasImmigrationBackground());

            default -> 0.0;
        };
    }
    private double computeRegressorSingleEarnerMale(String var, Person male, Household hh) {

        if (male == null || male.getJob() == null) return 0.0;

        return switch (var) {

            // ASC
            case "asc" -> 1.0;

            // Household characteristics
            case "hh_youngest_child_age_no_child" -> indicator(noChild(hh));
            case "hh_youngest_child_age_0_3"      -> indicator(youngestChildInRange(hh, 0, 3));
            case "hh_youngest_child_age_3_6"      -> indicator(youngestChildInRange(hh, 3, 6));
            case "hh_youngest_child_age_6_10"      -> indicator(youngestChildInRange(hh, 6, 10));
            case "hh_youngest_child_age_10_18"      -> indicator(youngestChildInRange(hh, 10, 18));

            case "hh_net_income_lt_4000" -> indicator(householdIncome(hh) < 4000);
            case "hh_net_income_ge_4000" -> indicator(householdIncome(hh) >= 4000);

            // Employment status
            case "p_emp_status_other_male"   -> indicator(male.getEmploymentStatus() != EmploymentStatus.FULLTIME_EMPLOYED);
            case "p_emp_status_full_time_male" -> indicator(male.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED);

            // Job sectors male
            case "j_emp_sector_other_male"                -> indicator(isOther(male.getJob().getType()));
            case "j_emp_sector_science_humanities_male"   -> indicator(isScienceHumanities(male.getJob().getType()));
            case "j_emp_sector_service_admin_male"        -> indicator(isServiceAdministration(male.getJob().getType()));
            case "j_emp_sector_transport_logistic_male"   -> indicator(isTransportLogistic(male.getJob().getType()));
            case "j_emp_sector_essential_services_male"   -> indicator(isEssentialServices(male.getJob().getType()));

//            // Education
//            case "edu_no_uni_male" -> indicator(!male.hasUniversityDegree());
//            case "edu_uni_male"    -> indicator(male.hasUniversityDegree());

            default -> 0.0;
        };
    }
    private double computeRegressorSingleEarnerFemale(String var, Person female, Household hh) {

        if (female == null || female.getJob() == null) return 0.0;

        return switch (var) {

            // ASC
            case "asc" -> 1.0;

            // Household characteristics
            case "hh_youngest_child_age_no_child" -> indicator(noChild(hh));
            case "hh_youngest_child_age_0_3"      -> indicator(youngestChildInRange(hh, 0, 3));
            case "hh_youngest_child_age_3_6"      -> indicator(youngestChildInRange(hh, 3, 6));
            case "hh_youngest_child_age_6_10"      -> indicator(youngestChildInRange(hh, 6, 10));
            case "hh_youngest_child_age_10_18"      -> indicator(youngestChildInRange(hh, 10, 18));

            case "hh_net_income_lt_4000" -> indicator(householdIncome(hh) < 4000);
            case "hh_net_income_ge_4000" -> indicator(householdIncome(hh) >= 4000);

            // Commute female
            case "p_commute_0_female"     -> commuteInRange(female, 0, 0);
            case "p_commute_0_20_female"  -> commuteInRange(female, 0, 20);
            case "p_commute_20_40_female" -> commuteInRange(female, 20, 40);
            case "p_commute_40_80_female" -> commuteInRange(female, 40, 80);
            case "p_commute_ge_80_female" -> commuteAbove(female, 80);

            // Employment status
            case "p_emp_status_other_female"   -> indicator(female.getEmploymentStatus() != EmploymentStatus.FULLTIME_EMPLOYED);
            case "p_emp_status_full_time_female" -> indicator(female.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED);

            // Job sectors female
            case "j_emp_sector_other_female"                -> indicator(isOther(female.getJob().getType()));
            case "j_emp_sector_science_humanities_female"   -> indicator(isScienceHumanities(female.getJob().getType()));
            case "j_emp_sector_service_admin_female"        -> indicator(isServiceAdministration(female.getJob().getType()));
            case "j_emp_sector_transport_logistic_female"   -> indicator(isTransportLogistic(female.getJob().getType()));
            case "j_emp_sector_essential_services_female"   -> indicator(isEssentialServices(female.getJob().getType()));

//            // Education
//            case "edu_no_uni_female" -> indicator(!female.hasUniversityDegree());
//            case "edu_uni_female"    -> indicator(female.hasUniversityDegree());

            default -> 0.0;
        };
    }
    private double computeRegressorSinglePerson(String var, Person person, Household hh) {
        if (person == null) return 0.0;

        return switch (var) {

            // ASC
            case "asc" -> 1.0;

            // Household characteristics
            case "hh_cars_lt_2"          -> indicator(countCars(hh) < 2);
            case "hh_cars_ge_2"          -> indicator(countCars(hh) >= 2);
            case "hh_net_income_lt_4000" -> indicator(householdIncome(hh) < 4000);
            case "hh_net_income_ge_4000" -> indicator(householdIncome(hh) >= 4000);

            // Commute
            case "p_commute_0"     -> commuteInRange(person, 0, 0);
            case "p_commute_0_20"  -> commuteInRange(person, 0, 20);
            case "p_commute_20_40" -> commuteInRange(person, 20, 40);
            case "p_commute_40_80" -> commuteInRange(person, 40, 80);
            case "p_commute_ge_80" -> commuteAbove(person, 80);

            // Employment status
            case "p_emp_status_other"     -> indicator(person.getEmploymentStatus() != EmploymentStatus.FULLTIME_EMPLOYED);
            case "p_emp_status_full_time" -> indicator(person.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED);

            // Job sectors — guard against null job
            case "j_emp_sector_other"                -> person.getJob() == null ? 0.0 : indicator(isOther(person.getJob().getType()));
            case "j_emp_sector_science_humanities"   -> person.getJob() == null ? 0.0 : indicator(isScienceHumanities(person.getJob().getType()));
            case "j_emp_sector_service_admin"        -> person.getJob() == null ? 0.0 : indicator(isServiceAdministration(person.getJob().getType()));
            case "j_emp_sector_transport_logistic"   -> person.getJob() == null ? 0.0 : indicator(isTransportLogistic(person.getJob().getType()));
            case "j_emp_sector_essential_services"   -> person.getJob() == null ? 0.0 : indicator(isEssentialServices(person.getJob().getType()));

            // Gender
            case "gender_male"   -> indicator(person.getGender().equals(Gender.MALE));
            case "gender_female" -> indicator(person.getGender().equals(Gender.FEMALE));

            default -> 0.0;
        };
    }

    // --------------------------------------------------------------
    // Helper Functions
    // --------------------------------------------------------------

    private boolean isScienceHumanities(String jobType) {
        if (jobType == null) return false;
        jobType = jobType.trim();
        return jobType.equals("Finc");
    }

    private boolean isServiceAdministration(String jobType) {
        if (jobType == null) return false;
        jobType = jobType.trim();
        return jobType.equals("Serv") || jobType.equals("Admn");
    }

    private boolean isTransportLogistic(String jobType) {
        if (jobType == null) return false;
        jobType = jobType.trim();
        return jobType.equals("Trns") || jobType.equals("Retl");
    }

    private boolean isEssentialServices(String jobType) {
        if (jobType == null) return false;
        jobType = jobType.trim();
        return jobType.equals("Rlst");
    }

    private boolean isOther(String jobType) {
        if (jobType == null) return false;
        jobType = jobType.trim();
        return jobType.equals("Agri") || jobType.equals("Cons") || jobType.equals("Mnft") || jobType.equals("Util");
    }

    private double resolveUrbanRuralDegree(String var, Household hh) {
        try {
            Zone z = dataSet.getZones().get(hh.getLocation().getZoneId());
            RegioStaR2 type = z.getRegioStaR2Type();
            int typeCode = z.getRegioStaR2Type().code(); // 1 = urban, 2 = rural

            return switch (var) {
                case "hh_urban" -> indicator(typeCode == 1);  // urban
                case "hh_rural" -> indicator(typeCode == 2);  // rural
                default -> 0.0;
            };

        } catch (Exception e) {
            return 0.0;
        }
    }

    private double householdIncome(Household hh) {
        if (hh == null) return -1;
        return hh.getPersons().stream()
                .mapToDouble(p -> Math.max(0, p.getMonthlyIncome_eur()))
                .sum();
    }

    private int countCars(Household hh) {
        if (hh == null) return 0;
        if (hh.getVehicles() != null && !hh.getVehicles().isEmpty())
            return hh.getVehicles().size();
        return hh.getNumberOfCars();
    }

    private double commuteInRange(Person p, int lo, int hi) {
        double t = getCommuteMinutes(p);
        if (t < 0) return 0;
        if (lo == hi) return indicator(t == lo);
        return indicator(t >= lo && t < hi);
    }

    private double commuteAbove(Person p, int thr) {
        double t = getCommuteMinutes(p);
        if (t < 0) return 0;
        return indicator(t >= thr);
    }

    private double getCommuteMinutes(Person p) {
        if (p == null
                || p.getJob() == null
                || p.getJob().getLocation() == null
                || p.getHousehold() == null
                || p.getHousehold().getLocation() == null)
            return -1;

        try {
            double t = dataSet.getTravelTimes().getTravelTimeInMinutes(
                    p.getHousehold().getLocation(),
                    p.getJob().getLocation(),
                    Mode.CAR_DRIVER,
                    p.getJob().getStartTime_min());
            return Math.round(t);
        } catch (Exception e) {
            return -1;
        }
    }

    private double indicator(boolean b) { return b ? 1.0 : 0.0; }

    private boolean youngestChildInRange(Household hh, int lo, int hi) {
        Integer y = youngestChildAge(hh);
        return y != null && y >= lo && y < hi;
    }

    private boolean noChild(Household hh) {
        return youngestChildAge(hh) == null;
    }

    private Integer youngestChildAge(Household hh) {
        return hh.getPersons().stream()
                .filter(p -> p.getAge() < 18)
                .mapToInt(Person::getAge)
                .min()
                .stream().boxed().findFirst().orElse(null);
    }

    private TeleworkAlternative computeChoice(
            Household hh,
            Person male,
            Person female,
            Map<String, EnumMap<TeleworkAlternative, Double>> coefTable,
            HouseholdType type) {

        List<TeleworkAlternative> alternatives;

        switch (type) {

            case PARTNERED_DUAL_EARNER -> {
                // 4 alternatives
                alternatives = List.of(TeleworkAlternative.values());
            }

            case PARTNERED_SINGLE_EARNER_MALE -> {
                alternatives = List.of(
                        TeleworkAlternative.NO_ONE,
                        TeleworkAlternative.ONLY_MALE
                );
            }

            case PARTNERED_SINGLE_EARNER_FEMALE -> {
                alternatives = List.of(
                        TeleworkAlternative.NO_ONE,
                        TeleworkAlternative.ONLY_FEMALE
                );
            }

            case SINGLE_WORKER -> {
                alternatives = List.of(
                        TeleworkAlternative.NO_ONE,
                        male != null ? TeleworkAlternative.ONLY_MALE : TeleworkAlternative.ONLY_FEMALE
                );
            }

            default -> alternatives = List.of(TeleworkAlternative.values());
        }

        EnumMap<TeleworkAlternative, Double> utilities =
                new EnumMap<>(TeleworkAlternative.class);

        for (TeleworkAlternative alt : alternatives) {
            utilities.put(alt,
                    computeUtility(hh, male, female, alt, coefTable, type));
        }

        EnumMap<TeleworkAlternative, Double> expU =
                new EnumMap<>(TeleworkAlternative.class);

        for (var e : utilities.entrySet()) {
            double v = Math.exp(e.getValue());
            expU.put(e.getKey(), (Double.isNaN(v) || Double.isInfinite(v)) ? 0.0 : v);
        }

        double sumExp = expU.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        EnumMap<TeleworkAlternative, Double> probs =
                new EnumMap<>(TeleworkAlternative.class);

        for (TeleworkAlternative alt : alternatives) {
            probs.put(alt, sumExp > 0 ? expU.get(alt) / sumExp : 0.0);
        }

        if (sumExp <= 0.0) {
            return utilities.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(TeleworkAlternative.NO_ONE);
        }

        return MitoUtil.select(probs, AbitUtils.getRandomObject());
    }
    private double computeUtility(Household hh, Person male, Person female, TeleworkAlternative alt, Map<String, EnumMap<TeleworkAlternative, Double>> coefTable, HouseholdType type) {

        double u = 0;

        for (var entry : coefTable.entrySet()) {
            String var = entry.getKey().trim().toLowerCase();
            Double beta = entry.getValue().get(alt);
            if (beta == null) continue;

            double x = 0;

            // route computation to the correct regressor system
            switch (type) {
                case PARTNERED_DUAL_EARNER ->
                        x = computeRegressorDualEarner(var, male, female, hh);

                case PARTNERED_SINGLE_EARNER_MALE ->
                        x = computeRegressorSingleEarnerMale(var, male, hh);

                case PARTNERED_SINGLE_EARNER_FEMALE ->
                        x = computeRegressorSingleEarnerFemale(var, female, hh);

                case SINGLE_WORKER ->
                        x = computeRegressorSinglePerson(var, (male != null ? male : female), hh);

                default -> x = 0;
            }

            if (!Double.isNaN(x) && x != 0.0) {
                u += beta * x;
            }
        }

        return u;
    }
    public Map<TeleworkAlternative, Double> getUtilities(Household hh, Person male, Person female) {

        EnumMap<TeleworkAlternative, Double> utilities =
                new EnumMap<>(TeleworkAlternative.class);

        for (TeleworkAlternative alt : TeleworkAlternative.values()) {
            double u = computeUtility(
                    hh, male, female,
                    alt,
                    coefDualEarner,
                    HouseholdType.PARTNERED_DUAL_EARNER
            );
            utilities.put(alt, u);
        }

        return utilities;
    }

    public Map<TeleworkAlternative, Double> getProbabilities(Household hh, Person male, Person female) {

        Map<TeleworkAlternative, Double> utilities =
                getUtilities(hh, male, female);

        EnumMap<TeleworkAlternative, Double> expU =
                new EnumMap<>(TeleworkAlternative.class);

        for (var e : utilities.entrySet()) {
            double val = Math.exp(e.getValue());
            expU.put(e.getKey(),
                    (Double.isNaN(val) || Double.isInfinite(val)) ? 0.0 : val);
        }

        double sum = expU.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        EnumMap<TeleworkAlternative, Double> probs =
                new EnumMap<>(TeleworkAlternative.class);

        for (TeleworkAlternative alt : TeleworkAlternative.values()) {
            probs.put(alt, sum > 0 ? expU.get(alt) / sum : 0.0);
        }

        return probs;
    }

    // --------------------------------------------------------------
// Debug / Inspection
// --------------------------------------------------------------

    public void debugHousehold(Household hh) {
        HouseholdType type = hh.getHouseholdType();
        Person male = hh.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.MALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst().orElse(null);
        Person female = hh.getPersons().stream().filter(p -> (p.getRelationship() == Relationship.married && p.getGender() == Gender.FEMALE && p.getOccupation() == Occupation.EMPLOYED)).findFirst().orElse(null);

        System.out.println("=== HH " + hh.getId() + " | type=" + type + " ===");
        System.out.printf("  male   : %s  income=%.0f  commute=%.1f%n",
                male   == null ? "NONE" : male.getId(),
                male   != null ? male.getMonthlyIncome_eur()   : -1.0,
                getCommuteMinutes(male));
        System.out.printf("  female : %s  income=%.0f  commute=%.1f%n",
                female == null ? "NONE" : female.getId(),
                female != null ? female.getMonthlyIncome_eur() : -1.0,
                getCommuteMinutes(female));

        if (type == HouseholdType.NONE) {
            System.out.println("  → skipped (unclassified household)\n");
            return;
        }

        // Pick the right coef table and person(s) for regressor printing
        Map<String, EnumMap<TeleworkAlternative, Double>> coefTable;
        Person regressorMale   = male;
        Person regressorFemale = female;

        switch (type) {
            case PARTNERED_DUAL_EARNER         -> coefTable = coefDualEarner;
            case PARTNERED_SINGLE_EARNER_MALE  -> { coefTable = coefSingleEarnerMale;   regressorFemale = null; }
            case PARTNERED_SINGLE_EARNER_FEMALE-> { coefTable = coefSingleEarnerFemale; regressorMale   = null; }
            case SINGLE_WORKER                 -> {
                coefTable = coefSinglePerson;
                regressorMale   = (hh.getPersons().get(0).getGender() == Gender.MALE)   ? hh.getPersons().get(0) : null;
                regressorFemale = (hh.getPersons().get(0).getGender() == Gender.FEMALE) ? hh.getPersons().get(0) : null;
            }
            default                            -> coefTable = coefDualEarner;
        }

        // Determine active alternatives for this type
        List<TeleworkAlternative> alternatives = getAlternativesForType(type, regressorMale);

        System.out.println("  --- regressors (non-zero only) ---");
        for (String var : coefTable.keySet()) {
            String varLower = var.trim().toLowerCase();
            double x = computeRegressorForType(varLower, regressorMale, regressorFemale, hh, type);
            if (x != 0.0 && !Double.isNaN(x)) {
                System.out.printf("    %-45s x=%.1f%n", var, x);
            }
        }

        System.out.println("  --- utilities / probabilities ---");
        EnumMap<TeleworkAlternative, Double> utilities = new EnumMap<>(TeleworkAlternative.class);
        for (TeleworkAlternative alt : alternatives) {
            double u = computeUtility(hh, regressorMale, regressorFemale, alt, coefTable, type);
            utilities.put(alt, u);
        }

        EnumMap<TeleworkAlternative, Double> expU = new EnumMap<>(TeleworkAlternative.class);
        for (var e : utilities.entrySet()) {
            double v = Math.exp(e.getValue());
            expU.put(e.getKey(), (Double.isNaN(v) || Double.isInfinite(v)) ? 0.0 : v);
        }
        double sumExp = expU.values().stream().mapToDouble(Double::doubleValue).sum();

        for (TeleworkAlternative alt : alternatives) {
            double prob = sumExp > 0 ? expU.get(alt) / sumExp : 0.0;
            System.out.printf("    %-16s U=%8.4f  P=%.4f%n", alt, utilities.get(alt), prob);
        }
        System.out.println();
    }

    public void debugHouseholds(DataSet ds, List<Integer> hhIds) {
        for (int id : hhIds) {
            Household hh = ds.getHouseholds().get(id);
            if (hh == null)
                System.out.println("=== HH " + id + " : NOT FOUND ===\n");
            else
                debugHousehold(hh);
        }
    }

    /** Helper: returns the same alternative list that computeChoice would use. */
    private List<TeleworkAlternative> getAlternativesForType(HouseholdType type, Person male) {
        return switch (type) {
            case PARTNERED_DUAL_EARNER          -> List.of(TeleworkAlternative.values());
            case PARTNERED_SINGLE_EARNER_MALE   -> List.of(TeleworkAlternative.NO_ONE, TeleworkAlternative.ONLY_MALE);
            case PARTNERED_SINGLE_EARNER_FEMALE -> List.of(TeleworkAlternative.NO_ONE, TeleworkAlternative.ONLY_FEMALE);
            case SINGLE_WORKER                  -> List.of(TeleworkAlternative.NO_ONE,
                    male != null ? TeleworkAlternative.ONLY_MALE : TeleworkAlternative.ONLY_FEMALE);
            default                             -> List.of(TeleworkAlternative.values());
        };
    }

    /** Helper: routes to the correct regressor method for debug printing. */
    private double computeRegressorForType(String var, Person male, Person female,
                                           Household hh, HouseholdType type) {
        return switch (type) {
            case PARTNERED_DUAL_EARNER          -> computeRegressorDualEarner(var, male, female, hh);
            case PARTNERED_SINGLE_EARNER_MALE   -> computeRegressorSingleEarnerMale(var, male, hh);
            case PARTNERED_SINGLE_EARNER_FEMALE -> computeRegressorSingleEarnerFemale(var, female, hh);
            case SINGLE_WORKER                  -> computeRegressorSinglePerson(var, male != null ? male : female, hh);
            default                             -> 0.0;
        };
    }

}