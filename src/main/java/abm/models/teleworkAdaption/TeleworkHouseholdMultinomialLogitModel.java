package abm.models.teleworkAdaption;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Telework multinomial logit model — DUAL-EARNER ONLY.
 *
 * Applies ONLY to households with >= 2 employed persons.
 * Households with 0 or 1 worker: no telework for anyone.
 *
 * Four alternatives:
 *   NO_ONE, ONLY_MALE, ONLY_FEMALE, BOTH
 *
 * Telework is evaluated ONCE per household and cached.
 */
public class TeleworkHouseholdMultinomialLogitModel implements TeleworkAdaptionChoice {

    private final DataSet dataSet;

    /** Cache: householdId → telework decision */
    private final Map<Integer, HouseholdTeleworkDecision> hhDecisionCache = new HashMap<>();

    /** Variable → Alternative → β */
    private final Map<String, EnumMap<TeleworkAlternative, Double>> coef4 = new HashMap<>();

    /** Result container */
    public static final class HouseholdTeleworkDecision {
        private final boolean maleTeleworks;
        private final boolean femaleTeleworks;

        public HouseholdTeleworkDecision(boolean maleTeleworks, boolean femaleTeleworks) {
            this.maleTeleworks = maleTeleworks;
            this.femaleTeleworks = femaleTeleworks;
        }

        public boolean isMaleTeleworks()   { return maleTeleworks; }
        public boolean isFemaleTeleworks() { return femaleTeleworks; }
    }

    public TeleworkHouseholdMultinomialLogitModel(DataSet dataSet) {
        this.dataSet = dataSet;

        Path coefFile = Path.of(AbitResources.instance.getString("telework.coef"));
        TeleworkCoefficientsReader reader = new TeleworkCoefficientsReader(coefFile);
        coef4.putAll(reader.readFourAlternativeCoefficients());
    }

    /* ================================================================
     * PERSON ENTRY POINT
     * ================================================================ */
    @Override
    public boolean canTelework(Person person) {

        // Non-workers never telework
        if (!isWorking(person)) {
            return false;
        }

        Household hh = person.getHousehold();
        int hhId = hh.getId();

        // Use cached household decision if available
        if (hhDecisionCache.containsKey(hhId)) {
            return extractResultForPerson(person, hhDecisionCache.get(hhId), hh);
        }

        // Compute, cache, and return
        HouseholdTeleworkDecision decision = evaluateHousehold(hh);
        hhDecisionCache.put(hhId, decision);
        return extractResultForPerson(person, decision, hh);
    }

    /* ================================================================
     * HOUSEHOLD LOGIC — DUAL-EARNER ONLY
     * ================================================================ */
    private HouseholdTeleworkDecision evaluateHousehold(Household hh) {

        Person male   = findWorkingMale(hh);
        Person female = findWorkingFemale(hh);

        // Require exactly one identified male worker and one identified female worker
        if (male == null || female == null) {
            return new HouseholdTeleworkDecision(false, false);
        }

        return decideDualEarner(male, female);
    }

    /* ================================================================
     * EXTRACT RESULT FOR PERSON
     * Only the person who filled the male/female slot in the model
     * gets the corresponding result. Any other worker in the household
     * (e.g. a third adult) is treated as no-telework.
     * ================================================================ */
    private boolean extractResultForPerson(Person p,
                                           HouseholdTeleworkDecision d,
                                           Household hh) {
        if (!isWorking(p)) return false;

        Person male   = findWorkingMale(hh);
        Person female = findWorkingFemale(hh);

        if (p.equals(male))   return d.isMaleTeleworks();
        if (p.equals(female)) return d.isFemaleTeleworks();

        // Person was not part of the dual-earner pair → no telework
        return false;
    }

    /* ================================================================
     * DUAL-EARNER (4-alternative MNL)
     * Alternatives: NO_ONE, ONLY_MALE, ONLY_FEMALE, BOTH
     *
     * Uses AbitUtils.getRandomObject() — the shared ABIT Random instance —
     * so draws are reproducible and consistent with all other ABIT models.
     *
     * MitoUtil.select(Map, Random) draws by:
     *   selectedWeight = random.nextDouble() * sum(values)
     * so we pass the RAW exp(U) values (not normalized), which is
     * equivalent to passing normalized probabilities but avoids the
     * floating-point edge case where normalized values sum to slightly
     * less than 1.0 and the last alternative is never reachable.
     * ================================================================ */
    public HouseholdTeleworkDecision decideDualEarner(Person male, Person female) {

        // 1) Compute utilities U_i = sum(beta_j * x_j) for each alternative
        EnumMap<TeleworkAlternative, Double> utilities = new EnumMap<>(TeleworkAlternative.class);
        for (TeleworkAlternative alt : TeleworkAlternative.values()) {
            utilities.put(alt, utility(male, female, alt));
        }

        // 2) Exponentiate to get exp(U_i) — used directly as weights for MitoUtil.select.
        //    Passing raw exp(U) is numerically safer than normalizing first because
        //    MitoUtil.select(Map, Random) recomputes the sum internally, so there is
        //    no risk of the normalized values summing to < 1.0 due to floating point.
        EnumMap<TeleworkAlternative, Double> expU = new EnumMap<>(TeleworkAlternative.class);
        double denom = 0.0;
        for (TeleworkAlternative alt : TeleworkAlternative.values()) {
            double v = Math.exp(utilities.get(alt));
            // Guard against NaN/Inf from extreme utility values
            v = (Double.isNaN(v) || Double.isInfinite(v)) ? 0.0 : v;
            expU.put(alt, v);
            denom += v;
        }

        // 3) Fallback: if all exp(U) are zero (e.g. all utilities are -Inf),
        //    fall back to the alternative with the highest raw utility rather
        //    than always defaulting to NO_ONE, which would bias results.
        TeleworkAlternative chosen;
        if (denom <= 0.0) {
            chosen = utilities.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(TeleworkAlternative.NO_ONE);
        } else {
            // 4) Probabilistic draw using the shared ABIT random stream.
            //    MitoUtil.select computes: selectedWeight = random.nextDouble() * sum(expU)
            //    then walks entries until cumulative sum exceeds selectedWeight.
            chosen = MitoUtil.select(expU, AbitUtils.getRandomObject());
        }

        // 5) Map chosen alternative to household decision
        return switch (chosen) {
            case NO_ONE      -> new HouseholdTeleworkDecision(false, false);
            case ONLY_MALE   -> new HouseholdTeleworkDecision(true,  false);
            case ONLY_FEMALE -> new HouseholdTeleworkDecision(false, true);
            case BOTH        -> new HouseholdTeleworkDecision(true,  true);
        };
    }

    /* ================================================================
     * UTILITY — iterates over all loaded coefficients
     * ================================================================ */
    private double utility(Person male, Person female, TeleworkAlternative alt) {
        double u = 0.0;

        for (var entry : coef4.entrySet()) {
            final String var     = entry.getKey().trim().toLowerCase();
            final Double betaObj = entry.getValue().get(alt);
            if (betaObj == null) continue;

            final double beta = betaObj;
            final double x    = computeRegressor(var, male, female);
            if (x != 0.0 && !Double.isNaN(x)) {
                u += beta * x;
            }
        }
        return u;
    }

    /* ================================================================
     * REGRESSOR DISPATCHER
     * Supported variables (dataset-available only):
     *   hh_child_age_0_3, hh_child_age_3_6, hh_child_age_gt_6
     *   hh_cars_2plus, hh_net_income_gt_4000
     *   male_indiv_gross_income_3000_6000, female_indiv_gross_income_3000_6000
     *   male_indiv_gross_income_gt_6000,   female_indiv_gross_income_gt_6000
     * All other variables return x = 0.
     * ================================================================ */
    private double computeRegressor(String var, Person male, Person female) {

        if (var.startsWith("hh_")) {
            return computeHouseholdRegressor(var, householdOf(male, female));
        }

        if ("male_indiv_gross_income_3000_6000".equals(var)) {
            return (male != null && inRange(male.getMonthlyIncome_eur(), 3000, 6000)) ? 1.0 : 0.0;
        }
        if ("female_indiv_gross_income_3000_6000".equals(var)) {
            return (female != null && inRange(female.getMonthlyIncome_eur(), 3000, 6000)) ? 1.0 : 0.0;
        }
        if ("male_indiv_gross_income_gt_6000".equals(var)) {
            return (male != null && male.getMonthlyIncome_eur() > 6000) ? 1.0 : 0.0;
        }
        if ("female_indiv_gross_income_gt_6000".equals(var)) {
            return (female != null && female.getMonthlyIncome_eur() > 6000) ? 1.0 : 0.0;
        }

        return 0.0;
    }

    /* ================================================================
     * HOUSEHOLD REGRESSORS
     * ================================================================ */
    private double computeHouseholdRegressor(String var, Household hh) {
        if (hh == null) return 0.0;

        return switch (var) {
            case "hh_child_age_0_3"    -> youngestChildInRange(hh, 0, 3)  ? 1.0 : 0.0;
            case "hh_child_age_3_6"    -> youngestChildInRange(hh, 3, 6)  ? 1.0 : 0.0;
            case "hh_child_age_gt_6"   -> youngestChildAbove(hh, 6)       ? 1.0 : 0.0;
            case "hh_cars_2plus"       -> {
                int cars = (hh.getVehicles() != null && !hh.getVehicles().isEmpty())
                        ? hh.getVehicles().size()
                        : hh.getNumberOfCars();
                yield cars >= 2 ? 1.0 : 0.0;
            }
            case "hh_net_income_gt_4000" -> {
                double inc = 0.0;
                for (Person p : hh.getPersons()) inc += Math.max(0, p.getMonthlyIncome_eur());
                yield inc > 4000.0 ? 1.0 : 0.0;
            }
            default -> 0.0;
        };
    }

    /* ================================================================
     * CHILD AGE HELPERS
     * ================================================================ */
    private boolean youngestChildInRange(Household hh, int min, int max) {
        Integer y = youngestChildAge(hh);
        return y != null && y >= min && y <= max;
    }

    private boolean youngestChildAbove(Household hh, int age) {
        Integer y = youngestChildAge(hh);
        return y != null && y > age;
    }

    private Integer youngestChildAge(Household hh) {
        if (hh == null) return null;
        Integer youngest = null;
        for (Person p : hh.getPersons()) {
            if (p.getAge() < 18) {
                if (youngest == null || p.getAge() < youngest) {
                    youngest = p.getAge();
                }
            }
        }
        return youngest;
    }

    /* ================================================================
     * GENERAL HELPERS
     * ================================================================ */
    private Household householdOf(Person male, Person female) {
        return male != null ? male.getHousehold()
                : (female != null ? female.getHousehold() : null);
    }

    private boolean inRange(double v, int lo, int hi) {
        return v >= lo && v <= hi;
    }

    private boolean isWorking(Person p) {
        return p.getOccupation() == Occupation.EMPLOYED;
    }

    private boolean isMale(Person p) {
        return p.getGender() == Gender.MALE;
    }

    private Person findWorkingMale(Household hh) {
        for (Person p : hh.getPersons()) {
            if (isWorking(p) && isMale(p)) return p;
        }
        return null;
    }

    private Person findWorkingFemale(Household hh) {
        for (Person p : hh.getPersons()) {
            if (isWorking(p) && !isMale(p)) return p;
        }
        return null;
    }

    /* ================================================================
     * PUBLIC HELPERS FOR RUNNER
     * ================================================================ */

    /** Read-only view of loaded coefficients (variable → {alt → beta}). */
    public Map<String, EnumMap<TeleworkAlternative, Double>> getCoefficientsView() {
        return java.util.Collections.unmodifiableMap(coef4);
    }


    /**
     * Returns the raw underlying value the regressor reads before thresholding to 0/1.
     * Useful for diagnostics — lets you verify the threshold logic is correct.
     *
     * Examples:
     *   hh_cars_2plus          → actual number of cars
     *   hh_net_income_gt_4000  → actual summed net income (eur)
     *   hh_child_age_*         → age of youngest child (-1 if none)
     *   male/female_indiv_*    → actual monthly income of that person (-1 if null)
     */
    public double computeRawValueFor(String variableName, Person male, Person female) {
        String var = variableName == null ? "" : variableName.trim().toLowerCase();
        Household hh = householdOf(male, female);

        return switch (var) {
            case "hh_child_age_0_3",
                 "hh_child_age_3_6",
                 "hh_child_age_gt_6" -> {
                Integer y = youngestChildAge(hh);
                yield (y != null) ? y : -1;
            }
            case "hh_cars_2plus" -> {
                if (hh == null) yield -1;
                yield (hh.getVehicles() != null && !hh.getVehicles().isEmpty())
                        ? hh.getVehicles().size()
                        : hh.getNumberOfCars();
            }
            case "hh_net_income_gt_4000" -> {
                if (hh == null) yield -1;
                double inc = 0.0;
                for (Person p : hh.getPersons()) inc += Math.max(0, p.getMonthlyIncome_eur());
                yield inc;
            }
            case "male_indiv_gross_income_3000_6000",
                 "male_indiv_gross_income_gt_6000"   -> male   != null ? male.getMonthlyIncome_eur()   : -1;
            case "female_indiv_gross_income_3000_6000",
                 "female_indiv_gross_income_gt_6000" -> female != null ? female.getMonthlyIncome_eur() : -1;
            default -> -1;
        };
    }

    /**
     * Compute the regressor x for one variable name using the same logic
     * as the model. Pass null for the slot that is absent.
     */
    public double computeRegressorDatasetOnlyFor(String variableName, Person male, Person female) {
        String var = variableName == null ? "" : variableName.trim().toLowerCase();
        return computeRegressor(var, male, female);
    }
}