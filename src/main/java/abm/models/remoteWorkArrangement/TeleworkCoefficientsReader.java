package abm.models.remoteWorkArrangement;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads either:
 *   4-alternative: variable,NoOneTelework,OnlyMaleTelework,OnlyFemaleTelework,BothTelework
 *   2-alternative: variable,noTelework,telework
 *
 * Auto-detects format from the header. Use readCoefficients() for both cases.
 * Use isTwoAlternative() to check which format was found.
 * For 2-alt files, the "telework" column is mapped to ONLY_MALE or ONLY_FEMALE
 * depending on which method calls this reader — mapping is done in the model.
 */
public class TeleworkCoefficientsReader {

    private final Path path;
    private boolean twoAlternative = false;

    public TeleworkCoefficientsReader(Path path) {
        this.path = path;
    }

    public boolean isTwoAlternative() {
        return twoAlternative;
    }

    /**
     * Reads coefficients from either a 4-alternative or 2-alternative CSV file.
     * <p>
     * For 4-alternative files the map keys are all four {@link TeleworkAlternative} values.
     * For 2-alternative files:
     * <ul>
     *   <li>NO_ONE  ← "noTelework" column</li>
     *   <li>ONLY_MALE and ONLY_FEMALE both ← "telework" column
     *       (the model picks whichever is appropriate for the person's gender)</li>
     * </ul>
     */
    public Map<String, EnumMap<TeleworkAlternative, Double>> readCoefficients() {
        Map<String, EnumMap<TeleworkAlternative, Double>> out = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

            String header = nextNonEmpty(br);
            if (header == null) {
                throw new IllegalArgumentException("Telework coefficient file is empty: " + path);
            }

            String[] columns = splitCsv(header);

            if (isFourAlternativeHeader(columns)) {
                twoAlternative = false;
                readFourAlternative(br, out);
            } else if (isTwoAlternativeHeader(columns)) {
                twoAlternative = true;
                readTwoAlternative(br, out);
            } else {
                throw new IllegalArgumentException(
                        "Unrecognised header in telework coefficient file: " + header
                                + "\nExpected either:"
                                + "\n  variable,NoOneTelework,OnlyMaleTelework,OnlyFemaleTelework,BothTelework"
                                + "\n  variable,noTelework,telework"
                                + "\nIn file: " + path);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading telework coefficients: " + path, e);
        }

        return out;
    }

    // -----------------------------------------------------------------------
    // Header detection
    // -----------------------------------------------------------------------

    private static boolean isFourAlternativeHeader(String[] cols) {
        return cols.length >= 5
                && cols[0].trim().equalsIgnoreCase("variable")
                && cols[1].trim().equalsIgnoreCase("NoOneTelework")
                && cols[2].trim().equalsIgnoreCase("OnlyMaleTelework")
                && cols[3].trim().equalsIgnoreCase("OnlyFemaleTelework")
                && cols[4].trim().equalsIgnoreCase("BothTelework");
    }

    private static boolean isTwoAlternativeHeader(String[] cols) {
        return cols.length >= 3
                && cols[0].trim().equalsIgnoreCase("variable")
                && cols[1].trim().equalsIgnoreCase("noTelework")
                && cols[2].trim().equalsIgnoreCase("telework");
    }

    // -----------------------------------------------------------------------
    // Format-specific readers
    // -----------------------------------------------------------------------

    private void readFourAlternative(BufferedReader br,
                                     Map<String, EnumMap<TeleworkAlternative, Double>> out)
            throws IOException {

        String line;
        while ((line = br.readLine()) != null) {
            line = trimComment(line);
            if (line.isBlank()) continue;

            String[] parts = splitCsv(line);
            if (parts.length < 5) continue;

            String variable = parts[0].trim();
            EnumMap<TeleworkAlternative, Double> row = new EnumMap<>(TeleworkAlternative.class);
            row.put(TeleworkAlternative.NO_ONE,      parse(parts[1]));
            row.put(TeleworkAlternative.ONLY_MALE,   parse(parts[2]));
            row.put(TeleworkAlternative.ONLY_FEMALE, parse(parts[3]));
            row.put(TeleworkAlternative.BOTH,        parse(parts[4]));
            out.put(variable, row);
        }
    }

    /**
     * 2-alternative format: variable, noTelework, telework
     * <p>
     * Maps:
     *   NO_ONE      ← noTelework column
     *   ONLY_MALE   ← telework column  (model selects the right one by gender)
     *   ONLY_FEMALE ← telework column
     * BOTH is left absent (not used by 2-alt models).
     */
    private void readTwoAlternative(BufferedReader br,
                                    Map<String, EnumMap<TeleworkAlternative, Double>> out)
            throws IOException {

        String line;
        while ((line = br.readLine()) != null) {
            line = trimComment(line);
            if (line.isBlank()) continue;

            String[] parts = splitCsv(line);
            if (parts.length < 3) continue;

            String variable   = parts[0].trim();
            double noTelework = parse(parts[1]);
            double telework   = parse(parts[2]);

            EnumMap<TeleworkAlternative, Double> row = new EnumMap<>(TeleworkAlternative.class);
            row.put(TeleworkAlternative.NO_ONE,      noTelework);
            row.put(TeleworkAlternative.ONLY_MALE,   telework);   // model picks by gender
            row.put(TeleworkAlternative.ONLY_FEMALE, telework);   // model picks by gender
            // BOTH intentionally omitted — 2-alt models never use it
            out.put(variable, row);
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------
    private static String nextNonEmpty(BufferedReader br) throws IOException {
        String line;
        boolean firstLine = true;
        while ((line = br.readLine()) != null) {
            if (firstLine) {
                line = stripBom(line);
                firstLine = false;
            }
            line = trimComment(line);
            if (!line.isBlank()) return line;
        }
        return null;
    }
    private static String trimComment(String s) {
        int i = s.indexOf('#');
        return (i >= 0 ? s.substring(0, i) : s).trim();
    }

    private static String[] splitCsv(String s) {
        return s.split(",", -1);
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
    private static String stripBom(String s) {
        if (s != null && s.startsWith("\uFEFF")) {
            return s.substring(1);
        }
        return s;
    }
}